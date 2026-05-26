package com.lubover.singularity.product.read;

import com.lubover.singularity.pipeline.ExecutionContext;
import com.lubover.singularity.pipeline.read.CacheConsistencyPolicy;
import com.lubover.singularity.pipeline.read.CacheLookup;
import com.lubover.singularity.pipeline.read.ReadMeta;
import com.lubover.singularity.product.cache.ProductCacheService;
import com.lubover.singularity.product.dto.ProductView;

public class ProductDetailCacheConsistencyPolicy implements CacheConsistencyPolicy<ProductView> {

    private final ProductCacheService cacheService;

    public ProductDetailCacheConsistencyPolicy(ProductCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public void beforeCacheRead(ExecutionContext<ProductView> context) {
        Long dirtyVersion = cacheService.detailDirtyVersion(productId(context));
        if (dirtyVersion != null) {
            context.putMeta(ReadMeta.DIRTY_VERSION, dirtyVersion);
        }
    }

    @Override
    public boolean shouldUseCache(ExecutionContext<ProductView> context, CacheLookup<ProductView> lookup) {
        Long dirtyVersion = dirtyVersion(context);
        if (dirtyVersion == null) {
            return true;
        }
        Long cacheVersion = cacheVersion(lookup);
        return cacheVersion != null && cacheVersion >= dirtyVersion;
    }

    @Override
    public boolean shouldWriteCache(ExecutionContext<ProductView> context, ProductView value) {
        Long dirtyVersion = dirtyVersion(context);
        if (dirtyVersion == null || value == null) {
            return true;
        }
        Long valueVersion = value.getVersion();
        return valueVersion != null && valueVersion >= dirtyVersion;
    }

    private String productId(ExecutionContext<?> context) {
        return String.valueOf(context.getOperation().getMetadata().get(ProductReadOperations.META_PRODUCT_ID));
    }

    private Long dirtyVersion(ExecutionContext<?> context) {
        Object value = context.getMeta().get(ReadMeta.DIRTY_VERSION);
        return value instanceof Number number ? number.longValue() : null;
    }

    private Long cacheVersion(CacheLookup<?> lookup) {
        Object value = lookup.getMeta().get(ReadMeta.CACHE_VERSION);
        return value instanceof Number number ? number.longValue() : null;
    }
}
