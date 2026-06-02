package com.lubover.singularity.stock.listener;

import com.lubover.singularity.stock.service.StockService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 库存变更MQ消费者
 * 实现削峰填谷的异步落库功能
 * 
 * 消息格式: productId|quantity|changeType|orderId|messageId
 */
@Component
@RocketMQMessageListener(
        topic = "${rocketmq.consumer.stock.topic:stock-topic}",
        consumerGroup = "${rocketmq.consumer.stock.group:stock-consumer-group}",
        consumeMode = org.apache.rocketmq.spring.annotation.ConsumeMode.ORDERLY)
public class StockConsumer implements RocketMQListener<String> {

    private static final Logger logger = LoggerFactory.getLogger(StockConsumer.class);
    
    private final StockService stockService;

    public StockConsumer(StockService stockService) {
        this.stockService = stockService;
    }

    @Override
    public void onMessage(String message) {
        logger.info("收到库存变更消息: {}", message);

        try {
            // 解析消息（简化处理，实际应使用JSON库）
            // 消息格式: productId|quantity|changeType|orderId|messageId
            String[] parts = message.split("\\|");
            if (parts.length != 5) {
                logger.warn("消息格式错误: {}", message);
                return;
            }

            String messageId = parts[4];
            String productId = parts[0];
            Long quantity = Long.parseLong(parts[1]);
            Integer changeType = Integer.parseInt(parts[2]);
            String orderId = parts[3];

            logger.info("解析库存变更消息 - 商品:{}, 数量:{}, 类型:{}, 订单:{}, 消息ID:{}",
                    productId, quantity, changeType, orderId, messageId);

            boolean result = false;
            if (changeType == 1) {
                // 扣库存
                result = stockService.deductStock(productId, quantity, orderId, messageId);
                logger.info("扣库存结果: productId={}, quantity={}, orderId={}, success={}", 
                        productId, quantity, orderId, result);
            } else if (changeType == 2) {
                // 还库存
                result = stockService.returnStock(productId, quantity, orderId, messageId);
                logger.info("还库存结果: productId={}, quantity={}, orderId={}, success={}", 
                        productId, quantity, orderId, result);
            } else {
                logger.warn("不支持的变更类型: message={}, changeType={}", message, changeType);
                return;
            }

            if (result) {
                logger.info("库存变更成功处理: messageId={}, productId={}, changeType={}", 
                        messageId, productId, changeType);
            } else {
                logger.warn("库存变更处理失败: messageId={}, productId={}, changeType={}", 
                        messageId, productId, changeType);
            }
        } catch (NumberFormatException e) {
            // 数字字段解析失败属于不可重试错误，直接记录并丢弃
            logger.warn("忽略非法库存消息(数字字段解析失败): {}, reason={}", message, e.getMessage());
        } catch (IllegalArgumentException e) {
            // 非法参数属于不可重试错误，直接记录并丢弃
            logger.warn("忽略非法库存消息: {}, reason={}", message, e.getMessage());
        } catch (Exception e) {
            logger.error("处理库存变更消息异常: {}", message, e);
            // 抛出异常让MQ触发重试，避免消息被误判为消费成功
            throw new RuntimeException("处理库存变更消息失败", e);
        }
    }
}
