package com.lubover.singularity.order.consumer;

import com.lubover.singularity.order.dto.OrderMessage;
import com.lubover.singularity.order.entity.Order;
import com.lubover.singularity.order.mapper.OrderMapper;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RocketMQMessageListener(
        topic = "${rocketmq.consumer.order.topic:order-topic}",
        consumerGroup = "${rocketmq.consumer.order.group:order-consumer-group}")
public class OrderConsumerService implements RocketMQListener<OrderMessage> {

    private static final Logger log = LoggerFactory.getLogger(OrderConsumerService.class);

    @Autowired
    private OrderMapper orderMapper;

    @Override
    public void onMessage(OrderMessage message) {
        try {
            if (message == null || message.getOrderId() == null || message.getUserId() == null
                    || message.getSlotId() == null || message.getProductId() == null) {
                log.error("Invalid order message: {}", message);
                return;
            }

            String orderId = message.getOrderId();
            log.info("Received order message: orderId={}, productId={}, slotId={}",
                    orderId, message.getProductId(), message.getSlotId());

            Order order = new Order();
            order.setOrderId(orderId);
            order.setUserId(message.getUserId());
            order.setProductId(message.getProductId());
            order.setSlotId(message.getSlotId());
            order.setStatus("CREATED");
            order.setCreateTime(message.getCreateTime() != null ? message.getCreateTime() : LocalDateTime.now());
            order.setUpdateTime(LocalDateTime.now());

            try {
                int rows = orderMapper.insert(order);
                if (rows > 0) {
                    log.info("Order persisted to database: orderId={}, userId={}, productId={}, slotId={}",
                            orderId, message.getUserId(), message.getProductId(), message.getSlotId());
                } else {
                    log.warn("Order insert failed (0 rows affected): orderId={}", orderId);
                }
            } catch (DuplicateKeyException e) {
                log.info("Order already exists in database (duplicate message ignored): orderId={}", orderId);
            }

        } catch (Exception e) {
            log.error("Failed to process order message: {}", message, e);
            throw new RuntimeException("Order processing failed", e);
        }
    }
}
