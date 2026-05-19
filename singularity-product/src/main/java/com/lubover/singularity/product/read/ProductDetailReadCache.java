package com.lubover.singularity.product.read;

import com.lubover.singularity.pipeline.Operation;
import com.lubover.singularity.pipeline.read.CacheLookup;
import com.lubover.singularity.pipeline.read.ReadCache;
import com.lubover.singularity.product.cache.ProductCacheService;
import com.lubover.singularity.product.cache.ProductCacheService.CacheState;
import com.lubover.singularity.product.cache.ProductCacheService.DetailCacheResult;
import com.lubover.singularity.product.dto.ProductView;

public class ProductDetailReadCache implements ReadCache<ProductView> {

    private final ProductCacheService cacheService;

    public ProductDetailReadCache(ProductCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public CacheLookup<ProductView> get(Operation operation) {
        DetailCacheResult result = cacheService.getDetail(productId(operation));
        if (result.getState() == CacheState.HIT_VALUE) {
            return CacheLookup.value(result.getValue());
        }
        if (result.getState() == CacheState.HIT_NULL) {
            return CacheLookup.nullHit();
        }
        return CacheLookup.miss();
    }

    @Override
    public void put(Operation operation, ProductView value) {
        cacheService.putDetail(productId(operation), value);
    }

    private String productId(Operation operation) {
        return String.valueOf(operation.getMetadata().get(ProductReadOperations.META_PRODUCT_ID));
    }
}
