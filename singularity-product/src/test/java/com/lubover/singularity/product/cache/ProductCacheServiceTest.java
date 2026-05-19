package com.lubover.singularity.product.cache;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductCacheServiceTest {

    @Test
    void tryLockDetail_shouldUseRedisSetIfAbsent() {
        Fixture fixture = newFixture();
        when(fixture.valueOperations.setIfAbsent(eq("product:lock:detail:p-1"), eq("token-1"), any(Duration.class)))
                .thenReturn(true);

        assertTrue(fixture.cacheService.tryLockDetail("p-1", "token-1"));
    }

    @Test
    void unlockDetail_shouldCallLuaRelease() {
        Fixture fixture = newFixture();
        when(fixture.redisTemplate.execute(any(DefaultRedisScript.class), eq(java.util.Collections.singletonList("product:lock:detail:p-1")), eq("token-1")))
                .thenReturn(1L);

        fixture.cacheService.unlockDetail("p-1", "token-1");

        verify(fixture.redisTemplate).execute(any(DefaultRedisScript.class), eq(java.util.Collections.singletonList("product:lock:detail:p-1")), eq("token-1"));
    }

    @Test
    void tryLockList_shouldReturnFalseWhenLockExists() {
        Fixture fixture = newFixture();
        when(fixture.valueOperations.setIfAbsent(eq("product:lock:list:hash-1"), eq("token-2"), any(Duration.class)))
                .thenReturn(false);

        assertFalse(fixture.cacheService.tryLockList("hash-1", "token-2"));
    }

    private Fixture newFixture() {
        @SuppressWarnings("unchecked")
        Cache<String, String> localCache = Mockito.mock(Cache.class);
        @SuppressWarnings("unchecked")
        RedisTemplate<String, String> redisTemplate = Mockito.mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        return new Fixture(new ProductCacheService(localCache, redisTemplate, new com.fasterxml.jackson.databind.ObjectMapper()), redisTemplate, valueOperations);
    }

    private static class Fixture {
        private final ProductCacheService cacheService;
        private final RedisTemplate<String, String> redisTemplate;
        private final ValueOperations<String, String> valueOperations;

        private Fixture(ProductCacheService cacheService,
                RedisTemplate<String, String> redisTemplate,
                ValueOperations<String, String> valueOperations) {
            this.cacheService = cacheService;
            this.redisTemplate = redisTemplate;
            this.valueOperations = valueOperations;
        }
    }
}
