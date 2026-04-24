package com.lubover.singularity.order.service.impl;

import com.lubover.singularity.api.*;
import com.lubover.singularity.api.impl.DefaultAllocator;
import com.lubover.singularity.order.dto.OrderMessage;
import com.lubover.singularity.order.entity.Order;
import com.lubover.singularity.order.feign.UserClient;
import com.lubover.singularity.order.mapper.OrderMapper;
import com.lubover.singularity.order.registry.SlotRegistry;
import com.lubover.singularity.order.service.OrderService;
import com.lubover.singularity.order.tx.OrderLocalTransaction;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    private static final BigDecimal PRODUCT_PRICE = BigDecimal.valueOf(99);

    private final Allocator allocator;
    private final OrderMapper orderMapper;
    private final UserClient userClient;

    @Autowired
    public OrderServiceImpl(
            Registry registry,
            ShardPolicy shardPolicy,
            List<Interceptor> interceptors,
            RocketMQTemplate rocketMQTemplate,
            StringRedisTemplate redisTemplate,
            SlotRegistry slotRegistry,
            OrderMapper orderMapper,
            UserClient userClient) {
        this.allocator = new DefaultAllocator(
                registry,
                shardPolicy,
                interceptors != null ? interceptors : Collections.emptyList(),
                handler(rocketMQTemplate, redisTemplate, slotRegistry));
        this.orderMapper = orderMapper;
        this.userClient = userClient;
    }

    @Override
    public Result snagOrder(Actor actor) {
        return allocator.allocate(actor);
    }

    @Override
    public Result payOrder(String orderId, String userId) {
        Order order = orderMapper.selectByOrderId(orderId);
        if (order == null) {
            return new Result(false, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            return new Result(false, "无权操作此订单");
        }
        if (!"CREATED".equals(order.getStatus())) {
            return new Result(false, "订单状态不支持支付");
        }

        try {
            Long uid = Long.parseLong(userId);
            Map<String, BigDecimal> body = new HashMap<>();
            body.put("amount", PRODUCT_PRICE);
            Map<String, Object> deductResult = userClient.deductBalance(uid, body);
            if (!Boolean.TRUE.equals(deductResult.get("success"))) {
                String msg = deductResult.get("message") != null
                        ? String.valueOf(deductResult.get("message"))
                        : "余额不足";
                return new Result(false, msg);
            }
        } catch (NumberFormatException e) {
            return new Result(false, "userId 格式非法");
        } catch (Exception e) {
            return new Result(false, "扣款失败: " + e.getMessage());
        }

        orderMapper.updateStatus(orderId, "PAID");
        return new Result(true, orderId);
    }

    private Interceptor handler(RocketMQTemplate rocketMQTemplate,
            StringRedisTemplate redisTemplate,
            SlotRegistry slotRegistry) {
        return context -> {
            Actor actor = context.getCurrActor();
            Slot slot = context.getCurrSlot();
            String orderId = UUID.randomUUID().toString();
            String redisStockKey = (String) slot.getMetadata().get("redisStockKey");
            String productId = (String) slot.getMetadata().get("productId");
            if (productId == null || productId.isBlank()) {
                context.setResult(new Result(false, "slot productId missing for slot: " + slot.getId()));
                return;
            }
            LocalDateTime createTime = LocalDateTime.now();

            // Step 1: 构造本地事务对象（Redis 减库存 + 写 order），作为 arg 传入
            // RocketMQ 在半消息确认后会回调 OrderTransactionListener.executeLocalTransaction，
            // 后者直接委托给该对象执行，不再包含任何业务逻辑
            OrderLocalTransaction localTx = new OrderLocalTransaction(
                    orderId, actor.getId(), slot.getId(),
                    productId, redisStockKey, redisTemplate, slotRegistry, createTime);

            OrderMessage orderMessage = new OrderMessage();
            orderMessage.setOrderId(orderId);
            orderMessage.setProductId(productId);
            orderMessage.setUserId(actor.getId());
            orderMessage.setSlotId(slot.getId());
            orderMessage.setCreateTime(createTime);

            // Step 2: 发送 RocketMQ 半消息，触发本地事务
            // sendMessageInTransaction 同步等待 executeLocalTransaction 执行完毕再返回
            Message<OrderMessage> msg = MessageBuilder.withPayload(orderMessage)
                    .setHeader("orderId", orderId)
                    .build();

            TransactionSendResult sendResult = rocketMQTemplate.sendMessageInTransaction(
                    "order-topic", msg, localTx);

            // Step 3: 本地事务（Redis 减库存 + 写 order）与半消息提交均已完成
            // checkLocalTransaction 接口供 broker 在超时时回查 Redis order 是否存在
            if (sendResult.getLocalTransactionState() == LocalTransactionState.COMMIT_MESSAGE) {
                context.setResult(new Result(true, orderId));
            } else {
                context.setResult(new Result(false, "stock insufficient or tx failed for slot: " + slot.getId()));
            }
        };
    }
}
