package com.lubover.singularity.product.read;

import com.lubover.singularity.pipeline.Operation;
import com.lubover.singularity.pipeline.read.ReadLockManager;
import com.lubover.singularity.product.cache.ProductCacheService;

public class ProductReadLockManager implements ReadLockManager {

    private final ProductCacheService cacheService;

    public ProductReadLockManager(ProductCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public boolean tryLock(Operation operation, String token) {
        if (operation.getKey().startsWith("product:list:")) {
            return cacheService.tryLockList(queryHash(operation), token);
        }
        return cacheService.tryLockDetail(productId(operation), token);
    }

    @Override
    public void unlock(Operation operation, String token) {
        if (operation.getKey().startsWith("product:list:")) {
            cacheService.unlockList(queryHash(operation), token);
            return;
        }
        cacheService.unlockDetail(productId(operation), token);
    }

    private String productId(Operation operation) {
        return String.valueOf(operation.getMetadata().get(ProductReadOperations.META_PRODUCT_ID));
    }

    private String queryHash(Operation operation) {
        return String.valueOf(operation.getMetadata().get(ProductReadOperations.META_QUERY_HASH));
    }
}
