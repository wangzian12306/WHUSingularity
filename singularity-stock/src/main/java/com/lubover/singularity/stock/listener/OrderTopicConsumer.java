package com.lubover.singularity.stock.listener;

import com.lubover.singularity.stock.event.OrderMessage;
import com.lubover.singularity.stock.service.StockService;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
        topic = "${rocketmq.consumer.order.topic:order-topic}",
        consumerGroup = "${rocketmq.consumer.order.group:stock-order-consumer-group}",
        consumeMode = ConsumeMode.ORDERLY)
public class OrderTopicConsumer implements RocketMQListener<MessageExt> {

    private static final Logger logger = LoggerFactory.getLogger(OrderTopicConsumer.class);

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final StockService stockService;

    public OrderTopicConsumer(com.fasterxml.jackson.databind.ObjectMapper objectMapper, StockService stockService) {
        this.objectMapper = objectMapper;
        this.stockService = stockService;
    }

    @Override
    public void onMessage(MessageExt messageExt) {
        String payload = new String(messageExt.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        String messageId = messageExt.getMsgId();
        try {
            OrderMessage message = objectMapper.readValue(payload, OrderMessage.class);
            if (message.getOrderId() == null || message.getProductId() == null) {
                logger.warn("忽略非法order-topic消息: payload={}", payload);
                return;
            }

            boolean result = stockService.deductStock(message.getProductId(), 1L, message.getOrderId(), messageId);
            if (result) {
                logger.info("order-topic扣库存成功: orderId={}, productId={}, messageId={}",
                        message.getOrderId(), message.getProductId(), messageId);
            } else {
                logger.warn("order-topic扣库存失败: orderId={}, productId={}, messageId={}",
                        message.getOrderId(), message.getProductId(), messageId);
            }
        } catch (IllegalArgumentException e) {
            logger.warn("忽略非法order-topic消息: payload={}, reason={}", payload, e.getMessage());
        } catch (Exception e) {
            logger.error("处理order-topic消息异常: payload={}, messageId={}", payload, messageId, e);
            throw new RuntimeException("处理order-topic消息失败", e);
        }
    }
}
