package com.lubover.singularity.orderbaseline.mq;

import com.lubover.singularity.orderbaseline.dto.OrderMessage;
import com.lubover.singularity.orderbaseline.entity.Order;
import com.lubover.singularity.orderbaseline.mapper.OrderMapper;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RocketMQMessageListener(
        topic = "${baseline.mq.topic:baseline-order-topic}",
        consumerGroup = "${baseline.mq.consumer-group:baseline-order-consumer-group}")
public class OrderCreatedConsumer implements RocketMQListener<OrderMessage> {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedConsumer.class);

    private final OrderMapper orderMapper;

    public OrderCreatedConsumer(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public void onMessage(OrderMessage message) {
        try {
            if (message == null || message.getOrderId() == null || message.getUserId() == null
                    || message.getProductId() == null) {
                log.error("Invalid order message: {}", message);
                return;
            }

            String orderId = message.getOrderId();
            log.info("Received order message: orderId={} productId={} userId={}",
                    orderId, message.getProductId(), message.getUserId());

            Order order = new Order();
            order.setOrderId(orderId);
            order.setUserId(message.getUserId());
            order.setProductId(message.getProductId());
            order.setSlotId(message.getSlotId() != null ? message.getSlotId() : "baseline");
            order.setStatus("CREATED");
            order.setCreateTime(message.getCreateTime() != null ? message.getCreateTime() : LocalDateTime.now());
            order.setUpdateTime(LocalDateTime.now());

            try {
                int rows = orderMapper.insert(order);
                if (rows > 0) {
                    log.info("Order persisted: orderId={}", orderId);
                } else {
                    log.warn("Order insert returned 0 rows: orderId={}", orderId);
                }
            } catch (DuplicateKeyException e) {
                log.info("Duplicate order message ignored: orderId={}", orderId);
            }
        } catch (Exception e) {
            log.error("Failed to process order message: {}", message, e);
            throw new RuntimeException("Order processing failed", e);
        }
    }
}
