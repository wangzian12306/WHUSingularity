package com.lubover.singularity.product.cache;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    void tryLockDetail_shouldUseShardPrefix() {
        Fixture fixture = newFixture();
        when(fixture.valueOperations.setIfAbsent(eq("product:s2:lock:detail:p-1"), eq("token-1"), any(Duration.class)))
                .thenReturn(true);

        assertTrue(fixture.cacheService.tryLockDetail("product:s2:", "p-1", "token-1"));
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

    @Test
    void getDetail_shouldUseShardPrefixForLocalAndRedisKeys() {
        Fixture fixture = newFixture();

        fixture.cacheService.getDetail("product:s1:", "p-1");

        verify(fixture.localCache).getIfPresent("product:s1:detail:p-1");
        verify(fixture.valueOperations).get("product:s1:detail:p-1");
    }

    @Test
    void putList_shouldUseShardPrefixForLocalAndRedisKeys() {
        Fixture fixture = newFixture();

        fixture.cacheService.putList("product:s3:", "hash-1", null);

        verify(fixture.localCache).put("product:s3:list:hash-1", ProductCacheService.NULL_PLACEHOLDER);
        verify(fixture.valueOperations).set(eq("product:s3:list:hash-1"),
                eq(ProductCacheService.NULL_PLACEHOLDER), any(Duration.class));
    }

    @Test
    void evictDetail_shouldDeleteLegacyAndShardKeys() {
        Fixture fixture = newFixture();

        fixture.cacheService.evictDetail("p-1");

        verify(fixture.localCache).invalidate("product:detail:p-1");
        verify(fixture.localCache).invalidate("product:s0:detail:p-1");
        verify(fixture.localCache).invalidate("product:s1:detail:p-1");
        verify(fixture.localCache).invalidate("product:s2:detail:p-1");
        verify(fixture.localCache).invalidate("product:s3:detail:p-1");
        verify(fixture.redisTemplate).delete(eq(List.of(
                "product:detail:p-1",
                "product:s0:detail:p-1",
                "product:s1:detail:p-1",
                "product:s2:detail:p-1",
                "product:s3:detail:p-1")));
    }

    @Test
    void evictAllLists_shouldDeleteLegacyAndShardListKeys() {
        Fixture fixture = newFixture();
        when(fixture.redisTemplate.keys("product:list:*")).thenReturn(Set.of("product:list:legacy"));
        when(fixture.redisTemplate.keys("product:s0:list:*")).thenReturn(Set.of("product:s0:list:a"));
        when(fixture.redisTemplate.keys("product:s1:list:*")).thenReturn(Set.of("product:s1:list:b"));
        when(fixture.redisTemplate.keys("product:s2:list:*")).thenReturn(Set.of());
        when(fixture.redisTemplate.keys("product:s3:list:*")).thenReturn(Set.of("product:s3:list:c"));

        fixture.cacheService.evictAllLists();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> keysCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(fixture.redisTemplate).delete(keysCaptor.capture());
        assertTrue(keysCaptor.getValue().containsAll(List.of(
                "product:list:legacy",
                "product:s0:list:a",
                "product:s1:list:b",
                "product:s3:list:c")));
    }

    private Fixture newFixture() {
        @SuppressWarnings("unchecked")
        Cache<String, String> localCache = Mockito.mock(Cache.class);
        @SuppressWarnings("unchecked")
        RedisTemplate<String, String> redisTemplate = Mockito.mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        when(localCache.asMap()).thenReturn(new ConcurrentHashMap<>());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        return new Fixture(new ProductCacheService(localCache, redisTemplate, new com.fasterxml.jackson.databind.ObjectMapper()),
                localCache, redisTemplate, valueOperations);
    }

    private static class Fixture {
        private final ProductCacheService cacheService;
        private final Cache<String, String> localCache;
        private final RedisTemplate<String, String> redisTemplate;
        private final ValueOperations<String, String> valueOperations;

        private Fixture(ProductCacheService cacheService,
                Cache<String, String> localCache,
                RedisTemplate<String, String> redisTemplate,
                ValueOperations<String, String> valueOperations) {
            this.cacheService = cacheService;
            this.localCache = localCache;
            this.redisTemplate = redisTemplate;
            this.valueOperations = valueOperations;
        }
    }
}
