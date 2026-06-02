package com.lubover.singularity.stock.listener;

import com.lubover.singularity.stock.event.OrderMessage;
import com.lubover.singularity.stock.service.StockService;
import com.fasterxml.jackson.core.JsonProcessingException;
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
        } catch (JsonProcessingException e) {
            // 兼容旧协议: order-topic 曾发送纯 orderId 字符串，而非 JSON。
            // 对这类历史消息直接忽略，避免持续重试刷日志。
            if (isLegacyOrderIdPayload(payload)) {
                logger.warn("忽略旧版order-topic消息(纯orderId): orderId={}, messageId={}", payload, messageId);
                return;
            }
            logger.warn("忽略非JSON的order-topic消息: payload={}, messageId={}", payload, messageId);
        } catch (IllegalArgumentException e) {
            logger.warn("忽略非法order-topic消息: payload={}, reason={}", payload, e.getMessage());
        } catch (Exception e) {
            logger.error("处理order-topic消息异常: payload={}, messageId={}", payload, messageId, e);
            throw new RuntimeException("处理order-topic消息失败", e);
        }
    }

    private boolean isLegacyOrderIdPayload(String payload) {
        if (payload == null) {
            return false;
        }
        String trimmed = payload.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return false;
        }
        return trimmed.matches("^[0-9a-fA-F-]{16,64}$");
    }
}
