package com.lubover.singularity.product.read;

import com.lubover.singularity.pipeline.ExecutionContext;
import com.lubover.singularity.pipeline.read.CacheConsistencyPolicy;
import com.lubover.singularity.pipeline.read.CacheLookup;
import com.lubover.singularity.pipeline.read.ReadMeta;
import com.lubover.singularity.product.cache.ProductCacheService;
import com.lubover.singularity.product.dto.PageResponse;
import com.lubover.singularity.product.dto.ProductView;

public class ProductListCacheConsistencyPolicy implements CacheConsistencyPolicy<PageResponse<ProductView>> {

    private final ProductCacheService cacheService;

    public ProductListCacheConsistencyPolicy(ProductCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public void beforeCacheRead(ExecutionContext<PageResponse<ProductView>> context) {
        Long dirtyVersion = cacheService.listDirtyVersion();
        if (dirtyVersion != null) {
            context.putMeta(ReadMeta.DIRTY_VERSION, dirtyVersion);
        }
    }

    @Override
    public boolean shouldUseCache(
            ExecutionContext<PageResponse<ProductView>> context,
            CacheLookup<PageResponse<ProductView>> lookup) {
        Long dirtyVersion = dirtyVersion(context);
        if (dirtyVersion == null) {
            return true;
        }
        Long cacheVersion = cacheVersion(lookup);
        return cacheVersion != null && cacheVersion >= dirtyVersion;
    }

    @Override
    public boolean shouldWriteCache(ExecutionContext<PageResponse<ProductView>> context, PageResponse<ProductView> value) {
        return true;
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
