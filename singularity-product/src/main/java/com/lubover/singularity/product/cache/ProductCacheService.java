package com.lubover.singularity.product.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.lubover.singularity.product.dto.PageResponse;
import com.lubover.singularity.product.dto.ProductView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

/**
 * Cache-aside component backed by local Caffeine and Redis.
 */
@Component
public class ProductCacheService {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheService.class);

    static final String NULL_PLACEHOLDER = "__NULL__";
    static final String PREFIX_DETAIL = "product:detail:";
    static final String PREFIX_LIST = "product:list:";
    static final String PREFIX_DETAIL_LOCK = "product:lock:detail:";
    static final String PREFIX_LIST_LOCK = "product:lock:list:";

    private static final Duration REDIS_DETAIL_TTL = Duration.ofMinutes(30);
    private static final Duration REDIS_LIST_TTL = Duration.ofMinutes(10);
    private static final Duration NULL_TTL = Duration.ofSeconds(60);
    private static final Duration CACHE_LOCK_TTL = Duration.ofSeconds(10);

    private static final String UNLOCK_LUA = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """;
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(UNLOCK_LUA, Long.class);

    private final Cache<String, String> localCache;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public ProductCacheService(
            @Qualifier("productLocalCache") Cache<String, String> localCache,
            @Qualifier("productRedisTemplate") RedisTemplate<String, String> redisTemplate,
            @Qualifier("cacheObjectMapper") ObjectMapper objectMapper) {
        this.localCache = localCache;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public DetailCacheResult getDetail(String productId) {
        String key = PREFIX_DETAIL + productId;

        String raw = localCache.getIfPresent(key);
        if (raw != null) {
            log.debug("cache hit [local] key={}", key);
            return NULL_PLACEHOLDER.equals(raw)
                    ? DetailCacheResult.nullHit()
                    : DetailCacheResult.value(deserialize(raw, ProductView.class));
        }

        raw = redisTemplate.opsForValue().get(key);
        if (raw != null) {
            log.debug("cache hit [redis] key={}", key);
            localCache.put(key, raw);
            return NULL_PLACEHOLDER.equals(raw)
                    ? DetailCacheResult.nullHit()
                    : DetailCacheResult.value(deserialize(raw, ProductView.class));
        }

        log.info("cache miss [detail] key={}", key);
        return DetailCacheResult.miss();
    }

    public void putDetail(String productId, ProductView view) {
        String key = PREFIX_DETAIL + productId;
        String raw = view != null ? serialize(view) : NULL_PLACEHOLDER;
        Duration ttl = view != null ? REDIS_DETAIL_TTL : NULL_TTL;
        localCache.put(key, raw);
        redisTemplate.opsForValue().set(key, raw, ttl);
        log.debug("cache put key={} ttl={}", key, ttl);
    }

    public void evictDetail(String productId) {
        String key = PREFIX_DETAIL + productId;
        localCache.invalidate(key);
        redisTemplate.delete(key);
        log.debug("cache evict key={}", key);
    }

    public void evictLocalDetail(String productId) {
        String key = PREFIX_DETAIL + productId;
        localCache.invalidate(key);
        log.debug("local cache evict key={}", key);
    }

    public boolean tryLockDetail(String productId, String token) {
        return tryLock(PREFIX_DETAIL_LOCK + productId, token);
    }

    public void unlockDetail(String productId, String token) {
        unlock(PREFIX_DETAIL_LOCK + productId, token);
    }

    public ListCacheResult getList(String queryHash) {
        String key = PREFIX_LIST + queryHash;

        String raw = localCache.getIfPresent(key);
        if (raw != null) {
            log.debug("cache hit [local] key={}", key);
            return NULL_PLACEHOLDER.equals(raw)
                    ? ListCacheResult.nullHit()
                    : ListCacheResult.value(deserializePageResponse(raw));
        }

        raw = redisTemplate.opsForValue().get(key);
        if (raw != null) {
            log.debug("cache hit [redis] key={}", key);
            localCache.put(key, raw);
            return NULL_PLACEHOLDER.equals(raw)
                    ? ListCacheResult.nullHit()
                    : ListCacheResult.value(deserializePageResponse(raw));
        }

        log.info("cache miss [list] key={}", key);
        return ListCacheResult.miss();
    }

    public void putList(String queryHash, PageResponse<ProductView> page) {
        String key = PREFIX_LIST + queryHash;
        String raw = page != null ? serialize(page) : NULL_PLACEHOLDER;
        Duration ttl = page != null ? REDIS_LIST_TTL : NULL_TTL;
        localCache.put(key, raw);
        redisTemplate.opsForValue().set(key, raw, ttl);
        log.debug("cache put key={} ttl={}", key, ttl);
    }

    public void evictAllLists() {
        localCache.asMap().keySet().removeIf(k -> k.startsWith(PREFIX_LIST));
        try {
            var keys = redisTemplate.keys(PREFIX_LIST + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("cache evict {} list keys", keys.size());
            }
        } catch (Exception e) {
            log.warn("evictAllLists redis error: {}", e.getMessage());
        }
    }

    public void evictLocalLists() {
        localCache.asMap().keySet().removeIf(k -> k.startsWith(PREFIX_LIST));
        log.debug("local cache evict list keys");
    }

    public boolean tryLockList(String queryHash, String token) {
        return tryLock(PREFIX_LIST_LOCK + queryHash, token);
    }

    public void unlockList(String queryHash, String token) {
        unlock(PREFIX_LIST_LOCK + queryHash, token);
    }

    public static String buildListHash(Integer status, String category, String keyword, int pageNo, int pageSize) {
        String raw = String.format("s=%s&c=%s&kw=%s&p=%d&ps=%d",
                status, category, keyword, pageNo, pageSize);
        return String.valueOf(raw.hashCode() & 0x7FFFFFFF);
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Cache serialize error", e);
        }
    }

    private <T> T deserialize(String raw, Class<T> type) {
        try {
            return objectMapper.readValue(raw, type);
        } catch (Exception e) {
            log.warn("Cache deserialize error, treating as miss: {}", e.getMessage());
            return null;
        }
    }

    private PageResponse<ProductView> deserializePageResponse(String raw) {
        try {
            return objectMapper.readValue(raw, new TypeReference<PageResponse<ProductView>>() {});
        } catch (Exception e) {
            log.warn("Cache deserialize PageResponse error, treating as miss: {}", e.getMessage());
            return null;
        }
    }

    private boolean tryLock(String lockKey, String token) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(lockKey, token, CACHE_LOCK_TTL);
        return Boolean.TRUE.equals(ok);
    }

    private void unlock(String lockKey, String token) {
        try {
            redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), token);
        } catch (Exception e) {
            log.warn("unlock cache lock failed: key={} err={}", lockKey, e.getMessage());
        }
    }

    public enum CacheState {
        HIT_VALUE,
        HIT_NULL,
        MISS
    }

    public static class ListCacheResult {
        private final CacheState state;
        private final PageResponse<ProductView> value;

        private ListCacheResult(CacheState state, PageResponse<ProductView> value) {
            this.state = state;
            this.value = value;
        }

        public static ListCacheResult value(PageResponse<ProductView> value) {
            return new ListCacheResult(CacheState.HIT_VALUE, value);
        }

        public static ListCacheResult nullHit() {
            return new ListCacheResult(CacheState.HIT_NULL, null);
        }

        public static ListCacheResult miss() {
            return new ListCacheResult(CacheState.MISS, null);
        }

        public CacheState getState() {
            return state;
        }

        public PageResponse<ProductView> getValue() {
            return value;
        }
    }

    public static class DetailCacheResult {
        private final CacheState state;
        private final ProductView value;

        private DetailCacheResult(CacheState state, ProductView value) {
            this.state = state;
            this.value = value;
        }

        public static DetailCacheResult value(ProductView value) {
            return new DetailCacheResult(CacheState.HIT_VALUE, value);
        }

        public static DetailCacheResult nullHit() {
            return new DetailCacheResult(CacheState.HIT_NULL, null);
        }

        public static DetailCacheResult miss() {
            return new DetailCacheResult(CacheState.MISS, null);
        }

        public CacheState getState() {
            return state;
        }

        public ProductView getValue() {
            return value;
        }
    }
}
