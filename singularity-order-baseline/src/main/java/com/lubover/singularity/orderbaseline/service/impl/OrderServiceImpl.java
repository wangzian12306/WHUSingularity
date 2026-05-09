package com.lubover.singularity.orderbaseline.service.impl;

import com.lubover.singularity.orderbaseline.client.UserHttpClient;
import com.lubover.singularity.orderbaseline.dto.OrderMessage;
import com.lubover.singularity.orderbaseline.dto.Result;
import com.lubover.singularity.orderbaseline.entity.Order;
import com.lubover.singularity.orderbaseline.mapper.OrderMapper;
import com.lubover.singularity.orderbaseline.service.OrderService;
import com.lubover.singularity.orderbaseline.service.RedisStockGate;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final RedisStockGate redisStockGate;
    private final RocketMQTemplate rocketMQTemplate;
    private final OrderMapper orderMapper;
    private final UserHttpClient userHttpClient;

    private final String productId;
    private final String stockKey;
    private final String slotId;
    private final String topic;

    public OrderServiceImpl(
            RedisStockGate redisStockGate,
            RocketMQTemplate rocketMQTemplate,
            OrderMapper orderMapper,
            UserHttpClient userHttpClient,
            @Value("${baseline.benchmark.product-id:1001}") String productId,
            @Value("${baseline.benchmark.redis-stock-key:baseline:stock:1001}") String stockKey,
            @Value("${baseline.benchmark.slot-id:baseline}") String slotId,
            @Value("${baseline.mq.topic:baseline-order-topic}") String topic) {
        this.redisStockGate = redisStockGate;
        this.rocketMQTemplate = rocketMQTemplate;
        this.orderMapper = orderMapper;
        this.userHttpClient = userHttpClient;
        this.productId = productId;
        this.stockKey = stockKey;
        this.slotId = slotId;
        this.topic = topic;
    }

    @Override
    public Result snagOrder(String userId, String productId) {
        if (userId == null || userId.isBlank()) {
            return Result.fail("userId is required");
        }
        if (productId == null || productId.isBlank()) {
            return Result.fail("productId is required");
        }
        if (!this.productId.equals(productId)) {
            return Result.fail("unsupported productId: " + productId + ", only " + this.productId + " is supported");
        }

        Long remaining = redisStockGate.tryDecrement(stockKey, 1L);
        if (remaining == null || remaining < 0) {
            if (remaining != null && remaining == -1L) {
                log.debug("Stock sold out: key={} productId={}", stockKey, productId);
                return Result.fail("sold out");
            }
            log.error("Stock key not found or Redis error: key={}", stockKey);
            return Result.fail("stock not initialized");
        }

        String orderId = UUID.randomUUID().toString();
        OrderMessage message = new OrderMessage(orderId, userId, productId, slotId, LocalDateTime.now());

        try {
            rocketMQTemplate.convertAndSend(topic, message);
            log.debug("MQ message sent: orderId={} productId={}", orderId, productId);
            return Result.success(orderId);
        } catch (Exception ex) {
            log.error("MQ send failed, compensating Redis: orderId={} key={}", orderId, stockKey, ex);
            redisStockGate.compensate(stockKey, 1L);
            return Result.fail("internal error");
        }
    }

    @Override
    public Result payOrder(String orderId, String userId) {
        if (orderId == null || orderId.isBlank()) {
            return Result.fail("orderId is required");
        }
        if (userId == null || userId.isBlank()) {
            return Result.fail("userId is required");
        }

        Order order = orderMapper.selectByOrderId(orderId);
        if (order == null) {
            return Result.fail("order not found");
        }
        if (!order.getUserId().equals(userId)) {
            return Result.fail("not your order");
        }
        if (!"CREATED".equals(order.getStatus())) {
            return Result.fail("order status not payable: " + order.getStatus());
        }

        try {
            var deductResult = userHttpClient.deductBalance(Long.parseLong(userId), 99);
            if (!Boolean.TRUE.equals(deductResult.get("success"))) {
                String msg = deductResult.get("message") != null
                        ? String.valueOf(deductResult.get("message"))
                        : "balance insufficient";
                return Result.fail(msg);
            }
        } catch (NumberFormatException e) {
            return Result.fail("invalid userId format");
        } catch (Exception e) {
            return Result.fail("deduct balance failed: " + e.getMessage());
        }

        orderMapper.updateStatus(orderId, "PAID");
        return Result.success(orderId);
    }
}
