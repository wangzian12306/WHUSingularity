package com.lubover.singularity.product.event;

import com.lubover.singularity.product.cache.ProductCacheService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RocketMQMessageListener(
        topic = "${rocketmq.consumer.product.topic:product-cache-topic}",
        consumerGroup = "${rocketmq.consumer.product.group:product-cache-consumer-group}",
        maxReconsumeTimes = 3)
public class ProductEventConsumer implements RocketMQListener<ProductUpdatedEvent> {

    private static final Logger log = LoggerFactory.getLogger(ProductEventConsumer.class);
    private static final Duration DEDUP_TTL = Duration.ofMinutes(10);

    private final ProductCacheService cacheService;
    private final StringRedisTemplate redisTemplate;

    public ProductEventConsumer(ProductCacheService cacheService, StringRedisTemplate redisTemplate) {
        this.cacheService = cacheService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void onMessage(ProductUpdatedEvent event) {
        if (event == null || event.getEventId() == null || event.getProductId() == null) {
            log.warn("ignore invalid product event: {}", event);
            return;
        }

        String dedupKey = "product:event:dedup:" + event.getEventId();
        Boolean first = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_TTL);
        if (!Boolean.TRUE.equals(first)) {
            log.info("duplicate product event ignored: eventId={}", event.getEventId());
            return;
        }

        try {
            cacheService.evictLocalDetail(event.getProductId());
            cacheService.evictLocalLists();
            log.info("product event consumed: eventId={} action={} productId={}",
                    event.getEventId(), event.getAction(), event.getProductId());
        } catch (Exception e) {
            log.error("product event consume failed: eventId={} action={} productId={} err={}",
                    event.getEventId(), event.getAction(), event.getProductId(), e.getMessage());
            throw e;
        }
    }
}
