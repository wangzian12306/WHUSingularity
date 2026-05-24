package com.lubover.singularity.product.event;

import com.lubover.singularity.product.observability.ProductObservabilityService;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class ProductEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProductEventPublisher.class);
    private final RocketMQTemplate rocketMQTemplate;
    private final String topic;
    private final ProductObservabilityService observabilityService;

    public ProductEventPublisher(
            RocketMQTemplate rocketMQTemplate,
            @Value("${rocketmq.producer.product.topic:product-cache-topic}") String topic,
            ProductObservabilityService observabilityService) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.topic = topic;
        this.observabilityService = observabilityService;
    }

    public void publishAfterCommit(ProductUpdatedEvent event) {
        if (event == null) {
            return;
        }

        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            publishNow(event);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishNow(event);
            }
        });
    }

    private void publishNow(ProductUpdatedEvent event) {
        try {
            rocketMQTemplate.convertAndSend(topic, event);
            observabilityService.recordEventSend(true);
            log.info("product event sent: eventId={} action={} productId={}",
                    event.getEventId(), event.getAction(), event.getProductId());
        } catch (Exception e) {
            observabilityService.recordEventSend(false);
            log.error("product event send failed: eventId={} action={} productId={} err={}",
                    event.getEventId(), event.getAction(), event.getProductId(), e.getMessage());
            throw e;
        }
    }
}
