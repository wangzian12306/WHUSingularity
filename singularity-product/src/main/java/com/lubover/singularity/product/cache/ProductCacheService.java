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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
    static final String DETAIL_DIRTY_PREFIX = "product:dirty:detail:";
    static final String LIST_DIRTY_KEY = "product:dirty:list";

    private static final Duration REDIS_DETAIL_TTL = Duration.ofMinutes(30);
    private static final Duration REDIS_LIST_TTL = Duration.ofMinutes(10);
    private static final Duration NULL_TTL = Duration.ofSeconds(60);
    private static final Duration CACHE_LOCK_TTL = Duration.ofSeconds(10);
    private static final Duration DIRTY_TTL = Duration.ofHours(1);

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
                    : detailValue(raw);
        }

        raw = redisTemplate.opsForValue().get(key);
        if (raw != null) {
            log.debug("cache hit [redis] key={}", key);
            localCache.put(key, raw);
            return NULL_PLACEHOLDER.equals(raw)
                    ? DetailCacheResult.nullHit()
                    : detailValue(raw);
        }

        log.info("cache miss [detail] key={}", key);
        return DetailCacheResult.miss();
    }

    public void putDetail(String productId, ProductView view) {
        putDetail(DEFAULT_KEY_PREFIX, productId, view);
    }

    public void putDetail(String keyPrefix, String productId, ProductView view) {
        String key = detailKey(keyPrefix, productId);
        String raw = view != null
                ? serialize(CacheEnvelope.of(versionOf(view), updateTimeOf(view), view))
                : serialize(CacheEnvelope.of(detailDirtyVersion(productId), null, null));
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
                    : listValue(raw);
        }

        raw = redisTemplate.opsForValue().get(key);
        if (raw != null) {
            log.debug("cache hit [redis] key={}", key);
            localCache.put(key, raw);
            return NULL_PLACEHOLDER.equals(raw)
                    ? ListCacheResult.nullHit()
                    : listValue(raw);
        }

        log.info("cache miss [list] key={}", key);
        return ListCacheResult.miss();
    }

    public void putList(String queryHash, PageResponse<ProductView> page) {
        putList(DEFAULT_KEY_PREFIX, queryHash, page);
    }

    public void putList(String keyPrefix, String queryHash, PageResponse<ProductView> page) {
        String key = listKey(keyPrefix, queryHash);
        String raw = page != null
                ? serialize(CacheEnvelope.of(pageVersion(page), pageUpdateTime(page), page))
                : serialize(CacheEnvelope.of(listDirtyVersion(), null, null));
        Duration ttl = page != null ? REDIS_LIST_TTL : NULL_TTL;
        localCache.put(key, raw);
        redisTemplate.opsForValue().set(key, raw, ttl);
        log.debug("cache put key={} ttl={}", key, ttl);
    }

    public void markDetailDirty(String productId, Long version) {
        Long dirtyVersion = version == null ? 0L : version;
        redisTemplate.opsForValue().set(detailDirtyKey(productId), String.valueOf(dirtyVersion), DIRTY_TTL);
        log.debug("detail dirty marked productId={} version={}", productId, dirtyVersion);
    }

    public void markListDirty(Long version) {
        Long dirtyVersion = System.currentTimeMillis();
        redisTemplate.opsForValue().set(LIST_DIRTY_KEY, String.valueOf(dirtyVersion), DIRTY_TTL);
        log.debug("list dirty marked version={}", dirtyVersion);
    }

    public Long detailDirtyVersion(String productId) {
        return parseLong(redisTemplate.opsForValue().get(detailDirtyKey(productId)));
    }

    public Long listDirtyVersion() {
        return parseLong(redisTemplate.opsForValue().get(LIST_DIRTY_KEY));
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

    private DetailCacheResult detailValue(String raw) {
        CacheEnvelope<ProductView> envelope = deserializeEnvelope(raw, ProductView.class);
        if (envelope != null) {
            if (envelope.getData() == null) {
                return DetailCacheResult.nullHit(envelope.getVersion());
            }
            return DetailCacheResult.value(envelope.getData(), envelope.getVersion());
        }
        return DetailCacheResult.value(deserialize(raw, ProductView.class), null);
    }

    private ListCacheResult listValue(String raw) {
        CacheEnvelope<PageResponse<ProductView>> envelope = deserializePageEnvelope(raw);
        if (envelope != null) {
            if (envelope.getData() == null) {
                return ListCacheResult.nullHit(envelope.getVersion());
            }
            return ListCacheResult.value(envelope.getData(), envelope.getVersion());
        }
        return ListCacheResult.value(deserializePageResponse(raw), null);
    }

    private <T> CacheEnvelope<T> deserializeEnvelope(String raw, Class<T> type) {
        try {
            var node = objectMapper.readTree(raw);
            if (node == null || !node.has("data") || !node.has("version")) {
                return null;
            }
            CacheEnvelope<T> envelope = new CacheEnvelope<>();
            envelope.setVersion(node.get("version").isNull() ? null : node.get("version").asLong());
            envelope.setLastModified(node.has("lastModified") && !node.get("lastModified").isNull()
                    ? node.get("lastModified").asText()
                    : null);
            envelope.setData(node.get("data").isNull() ? null : objectMapper.treeToValue(node.get("data"), type));
            return envelope;
        } catch (Exception e) {
            return null;
        }
    }

    private CacheEnvelope<PageResponse<ProductView>> deserializePageEnvelope(String raw) {
        try {
            var node = objectMapper.readTree(raw);
            if (node == null || !node.has("data") || !node.has("version")) {
                return null;
            }
            CacheEnvelope<PageResponse<ProductView>> envelope = new CacheEnvelope<>();
            envelope.setVersion(node.get("version").isNull() ? null : node.get("version").asLong());
            envelope.setLastModified(node.has("lastModified") && !node.get("lastModified").isNull()
                    ? node.get("lastModified").asText()
                    : null);
            envelope.setData(node.get("data").isNull()
                    ? null
                    : objectMapper.readValue(objectMapper.treeAsTokens(node.get("data")),
                            new TypeReference<PageResponse<ProductView>>() {}));
            return envelope;
        } catch (Exception e) {
            return null;
        }
    }

    private Long versionOf(ProductView view) {
        return view == null ? null : view.getVersion();
    }

    private String updateTimeOf(ProductView view) {
        return view == null || view.getUpdateTime() == null ? null : view.getUpdateTime().toString();
    }

    private Long pageVersion(PageResponse<ProductView> page) {
        return page == null ? null : System.currentTimeMillis();
    }

    private String pageUpdateTime(PageResponse<ProductView> page) {
        if (page == null || page.getRecords() == null) {
            return null;
        }
        return page.getRecords().stream()
                .filter(Objects::nonNull)
                .map(ProductView::getUpdateTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .map(LocalDateTime::toString)
                .orElse(null);
    }

    private String detailDirtyKey(String productId) {
        return DETAIL_DIRTY_PREFIX + productId;
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
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
        private final Long version;

        private ListCacheResult(CacheState state, PageResponse<ProductView> value, Long version) {
            this.state = state;
            this.value = value;
            this.version = version;
        }

        public static ListCacheResult value(PageResponse<ProductView> value) {
            return value(value, null);
        }

        public static ListCacheResult value(PageResponse<ProductView> value, Long version) {
            return new ListCacheResult(CacheState.HIT_VALUE, value, version);
        }

        public static ListCacheResult nullHit() {
            return nullHit(null);
        }

        public static ListCacheResult nullHit(Long version) {
            return new ListCacheResult(CacheState.HIT_NULL, null, version);
        }

        public static ListCacheResult miss() {
            return new ListCacheResult(CacheState.MISS, null, null);
        }

        public CacheState getState() {
            return state;
        }

        public PageResponse<ProductView> getValue() {
            return value;
        }

        public Long getVersion() {
            return version;
        }
    }

    public static class DetailCacheResult {
        private final CacheState state;
        private final ProductView value;
        private final Long version;

        private DetailCacheResult(CacheState state, ProductView value, Long version) {
            this.state = state;
            this.value = value;
            this.version = version;
        }

        public static DetailCacheResult value(ProductView value) {
            return value(value, null);
        }

        public static DetailCacheResult value(ProductView value, Long version) {
            return new DetailCacheResult(CacheState.HIT_VALUE, value, version);
        }

        public static DetailCacheResult nullHit() {
            return nullHit(null);
        }

        public static DetailCacheResult nullHit(Long version) {
            return new DetailCacheResult(CacheState.HIT_NULL, null, version);
        }

        public static DetailCacheResult miss() {
            return new DetailCacheResult(CacheState.MISS, null, null);
        }

        public CacheState getState() {
            return state;
        }

        public ProductView getValue() {
            return value;
        }

        public Long getVersion() {
            return version;
        }
    }

    public static class CacheEnvelope<T> {
        private Long version;
        private String lastModified;
        private T data;

        public static <T> CacheEnvelope<T> of(Long version, String lastModified, T data) {
            CacheEnvelope<T> envelope = new CacheEnvelope<>();
            envelope.setVersion(version);
            envelope.setLastModified(lastModified);
            envelope.setData(data);
            return envelope;
        }

        public Long getVersion() {
            return version;
        }

        public void setVersion(Long version) {
            this.version = version;
        }

        public String getLastModified() {
            return lastModified;
        }

        public void setLastModified(String lastModified) {
            this.lastModified = lastModified;
        }

        public T getData() {
            return data;
        }

        public void setData(T data) {
            this.data = data;
        }
    }
}
