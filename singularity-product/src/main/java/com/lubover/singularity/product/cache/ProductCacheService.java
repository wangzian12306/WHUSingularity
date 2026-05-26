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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cache-aside component backed by local Caffeine and Redis.
 */
@Component
public class ProductCacheService {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheService.class);

    static final String NULL_PLACEHOLDER = "__NULL__";
    static final String DEFAULT_KEY_PREFIX = "product:";
    static final List<String> SHARD_KEY_PREFIXES = List.of(
            "product:s0:",
            "product:s1:",
            "product:s2:",
            "product:s3:");
    static final String DETAIL_SUFFIX = "detail:";
    static final String LIST_SUFFIX = "list:";
    static final String DETAIL_LOCK_SUFFIX = "lock:detail:";
    static final String LIST_LOCK_SUFFIX = "lock:list:";

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
        return getDetail(DEFAULT_KEY_PREFIX, productId);
    }

    public DetailCacheResult getDetail(String keyPrefix, String productId) {
        String key = detailKey(keyPrefix, productId);

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
        putDetail(DEFAULT_KEY_PREFIX, productId, view);
    }

    public void putDetail(String keyPrefix, String productId, ProductView view) {
        String key = detailKey(keyPrefix, productId);
        String raw = view != null ? serialize(view) : NULL_PLACEHOLDER;
        Duration ttl = view != null ? REDIS_DETAIL_TTL : NULL_TTL;
        localCache.put(key, raw);
        redisTemplate.opsForValue().set(key, raw, ttl);
        log.debug("cache put key={} ttl={}", key, ttl);
    }

    public void evictDetail(String productId) {
        List<String> keys = allDetailKeys(productId);
        keys.forEach(localCache::invalidate);
        redisTemplate.delete(keys);
        log.debug("cache evict detail keys={}", keys);
    }

    public void evictLocalDetail(String productId) {
        List<String> keys = allDetailKeys(productId);
        keys.forEach(localCache::invalidate);
        log.debug("local cache evict detail keys={}", keys);
    }

    public boolean tryLockDetail(String productId, String token) {
        return tryLockDetail(DEFAULT_KEY_PREFIX, productId, token);
    }

    public boolean tryLockDetail(String keyPrefix, String productId, String token) {
        return tryLock(detailLockKey(keyPrefix, productId), token);
    }

    public void unlockDetail(String productId, String token) {
        unlockDetail(DEFAULT_KEY_PREFIX, productId, token);
    }

    public void unlockDetail(String keyPrefix, String productId, String token) {
        unlock(detailLockKey(keyPrefix, productId), token);
    }

    public ListCacheResult getList(String queryHash) {
        return getList(DEFAULT_KEY_PREFIX, queryHash);
    }

    public ListCacheResult getList(String keyPrefix, String queryHash) {
        String key = listKey(keyPrefix, queryHash);

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
        putList(DEFAULT_KEY_PREFIX, queryHash, page);
    }

    public void putList(String keyPrefix, String queryHash, PageResponse<ProductView> page) {
        String key = listKey(keyPrefix, queryHash);
        String raw = page != null ? serialize(page) : NULL_PLACEHOLDER;
        Duration ttl = page != null ? REDIS_LIST_TTL : NULL_TTL;
        localCache.put(key, raw);
        redisTemplate.opsForValue().set(key, raw, ttl);
        log.debug("cache put key={} ttl={}", key, ttl);
    }

    public void evictAllLists() {
        List<String> prefixes = allListPrefixes();
        localCache.asMap().keySet().removeIf(k -> prefixes.stream().anyMatch(k::startsWith));
        try {
            List<String> keysToDelete = new ArrayList<>();
            for (String prefix : prefixes) {
                var keys = redisTemplate.keys(prefix + "*");
                if (keys != null && !keys.isEmpty()) {
                    keysToDelete.addAll(keys);
                }
            }
            if (!keysToDelete.isEmpty()) {
                redisTemplate.delete(keysToDelete);
                log.debug("cache evict {} list keys", keysToDelete.size());
            }
        } catch (Exception e) {
            log.warn("evictAllLists redis error: {}", e.getMessage());
        }
    }

    public void evictLocalLists() {
        List<String> prefixes = allListPrefixes();
        localCache.asMap().keySet().removeIf(k -> prefixes.stream().anyMatch(k::startsWith));
        log.debug("local cache evict list keys");
    }

    public boolean tryLockList(String queryHash, String token) {
        return tryLockList(DEFAULT_KEY_PREFIX, queryHash, token);
    }

    public boolean tryLockList(String keyPrefix, String queryHash, String token) {
        return tryLock(listLockKey(keyPrefix, queryHash), token);
    }

    public void unlockList(String queryHash, String token) {
        unlockList(DEFAULT_KEY_PREFIX, queryHash, token);
    }

    public void unlockList(String keyPrefix, String queryHash, String token) {
        unlock(listLockKey(keyPrefix, queryHash), token);
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

    private String detailKey(String keyPrefix, String productId) {
        return normalizePrefix(keyPrefix) + DETAIL_SUFFIX + productId;
    }

    private String listKey(String keyPrefix, String queryHash) {
        return normalizePrefix(keyPrefix) + LIST_SUFFIX + queryHash;
    }

    private String detailLockKey(String keyPrefix, String productId) {
        return normalizePrefix(keyPrefix) + DETAIL_LOCK_SUFFIX + productId;
    }

    private String listLockKey(String keyPrefix, String queryHash) {
        return normalizePrefix(keyPrefix) + LIST_LOCK_SUFFIX + queryHash;
    }

    private String normalizePrefix(String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            return DEFAULT_KEY_PREFIX;
        }
        return keyPrefix.endsWith(":") ? keyPrefix : keyPrefix + ":";
    }

    private List<String> allDetailKeys(String productId) {
        List<String> keys = new ArrayList<>();
        keys.add(detailKey(DEFAULT_KEY_PREFIX, productId));
        SHARD_KEY_PREFIXES.stream()
                .map(prefix -> detailKey(prefix, productId))
                .forEach(keys::add);
        return keys;
    }

    private List<String> allListPrefixes() {
        List<String> prefixes = new ArrayList<>();
        prefixes.add(DEFAULT_KEY_PREFIX + LIST_SUFFIX);
        SHARD_KEY_PREFIXES.stream()
                .map(prefix -> normalizePrefix(prefix) + LIST_SUFFIX)
                .forEach(prefixes::add);
        return prefixes;
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
