package com.lubover.singularity.product.read;

import com.lubover.singularity.pipeline.Operation;
import com.lubover.singularity.pipeline.read.CacheLookup;
import com.lubover.singularity.pipeline.read.ReadCache;
import com.lubover.singularity.product.cache.ProductCacheService;
import com.lubover.singularity.product.cache.ProductCacheService.CacheState;
import com.lubover.singularity.product.cache.ProductCacheService.ListCacheResult;
import com.lubover.singularity.product.dto.PageResponse;
import com.lubover.singularity.product.dto.ProductView;

public class ProductListReadCache implements ReadCache<PageResponse<ProductView>> {

    private final ProductCacheService cacheService;

    public ProductListReadCache(ProductCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public CacheLookup<PageResponse<ProductView>> get(Operation operation) {
        ListCacheResult result = cacheService.getList(queryHash(operation));
        if (result.getState() == CacheState.HIT_VALUE) {
            return CacheLookup.value(result.getValue());
        }
        if (result.getState() == CacheState.HIT_NULL) {
            return CacheLookup.nullHit();
        }
        return CacheLookup.miss();
    }

    @Override
    public void put(Operation operation, PageResponse<ProductView> value) {
        if (value == null || value.getRecords() == null || value.getRecords().isEmpty()) {
            cacheService.putList(queryHash(operation), null);
            return;
        }
        cacheService.putList(queryHash(operation), value);
    }

    private String queryHash(Operation operation) {
        return String.valueOf(operation.getMetadata().get(ProductReadOperations.META_QUERY_HASH));
    }
}
