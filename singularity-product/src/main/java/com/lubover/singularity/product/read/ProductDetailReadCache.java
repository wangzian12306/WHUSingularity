package com.lubover.singularity.product.read;

import com.lubover.singularity.pipeline.ExecutionContext;
import com.lubover.singularity.pipeline.Operation;
import com.lubover.singularity.pipeline.read.CacheLookup;
import com.lubover.singularity.pipeline.read.ReadCache;
import com.lubover.singularity.pipeline.read.ReadMeta;
import com.lubover.singularity.product.cache.ProductCacheService;
import com.lubover.singularity.product.cache.ProductCacheService.CacheState;
import com.lubover.singularity.product.cache.ProductCacheService.DetailCacheResult;
import com.lubover.singularity.product.dto.ProductView;

import java.util.Map;

public class ProductDetailReadCache implements ReadCache<ProductView> {

    private final ProductCacheService cacheService;

    public ProductDetailReadCache(ProductCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public CacheLookup<ProductView> get(Operation operation) {
        DetailCacheResult result = cacheService.getDetail(productId(operation));
        return toLookup(result);
    }

    @Override
    public CacheLookup<ProductView> get(ExecutionContext<ProductView> context) {
        DetailCacheResult result = cacheService.getDetail(
                ProductReadSlotSupport.redisKeyPrefix(context),
                productId(context.getOperation()));
        return toLookup(result);
    }

    private CacheLookup<ProductView> toLookup(DetailCacheResult result) {
        if (result.getState() == CacheState.HIT_VALUE) {
            return CacheLookup.value(result.getValue()).withMeta(versionMeta(result.getVersion()));
        }
        if (result.getState() == CacheState.HIT_NULL) {
            return CacheLookup.<ProductView>nullHit().withMeta(versionMeta(result.getVersion()));
        }
        return CacheLookup.miss();
    }

    @Override
    public void put(Operation operation, ProductView value) {
        cacheService.putDetail(productId(operation), value);
    }

    @Override
    public void put(ExecutionContext<ProductView> context, ProductView value) {
        cacheService.putDetail(
                ProductReadSlotSupport.redisKeyPrefix(context),
                productId(context.getOperation()),
                value);
    }

    private String productId(Operation operation) {
        return String.valueOf(operation.getMetadata().get(ProductReadOperations.META_PRODUCT_ID));
    }

    private Map<String, Object> versionMeta(Long version) {
        return version == null ? Map.of() : Map.of(ReadMeta.CACHE_VERSION, version);
    }
}
