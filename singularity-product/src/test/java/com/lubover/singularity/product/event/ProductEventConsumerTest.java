package com.lubover.singularity.product.event;

import com.lubover.singularity.product.cache.ProductCacheService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductEventConsumerTest {

    @Test
    void onMessage_firstTime_shouldEvictLocalCache() {
        ProductCacheService cacheService = Mockito.mock(ProductCacheService.class);
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(any(), any(), any(java.time.Duration.class))).thenReturn(true);

        ProductEventConsumer consumer = new ProductEventConsumer(cacheService, redisTemplate);
        ProductUpdatedEvent event = new ProductUpdatedEvent();
        event.setEventId("evt-1");
        event.setProductId("p-1");
        event.setAction(ProductUpdatedEvent.Action.UPDATED);
        event.setEventTime(LocalDateTime.now());

        consumer.onMessage(event);

        verify(cacheService).evictLocalDetail("p-1");
        verify(cacheService).evictLocalLists();
    }

    @Test
    void onMessage_duplicate_shouldSkipEviction() {
        ProductCacheService cacheService = Mockito.mock(ProductCacheService.class);
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(any(), any(), any(java.time.Duration.class))).thenReturn(false);

        ProductEventConsumer consumer = new ProductEventConsumer(cacheService, redisTemplate);
        ProductUpdatedEvent event = new ProductUpdatedEvent();
        event.setEventId("evt-2");
        event.setProductId("p-2");
        event.setAction(ProductUpdatedEvent.Action.UPDATED);
        event.setEventTime(LocalDateTime.now());

        consumer.onMessage(event);

        verify(cacheService, never()).evictLocalDetail(eq("p-2"));
        verify(cacheService, never()).evictLocalLists();
    }
}
