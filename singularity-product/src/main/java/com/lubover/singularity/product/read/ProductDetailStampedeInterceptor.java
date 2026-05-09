package com.lubover.singularity.product.read;

import com.lubover.singularity.pipeline.ExecutionContext;
import com.lubover.singularity.pipeline.ExecutionResult;
import com.lubover.singularity.pipeline.PipelineInterceptor;
import com.lubover.singularity.product.cache.ProductCacheService;
import com.lubover.singularity.product.cache.ProductCacheService.CacheState;
import com.lubover.singularity.product.cache.ProductCacheService.DetailCacheResult;
import com.lubover.singularity.product.dto.ProductView;
import com.lubover.singularity.product.exception.BusinessException;
import com.lubover.singularity.product.exception.ErrorCode;

import java.util.UUID;

public class ProductDetailStampedeInterceptor implements PipelineInterceptor<ProductView> {

    private static final long CACHE_LOCK_WAIT_MS = 50L;

    private final ProductCacheService cacheService;

    public ProductDetailStampedeInterceptor(ProductCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public void handle(ExecutionContext<ProductView> context) {
        String productId = productId(context);
        String lockToken = UUID.randomUUID().toString();
        int waitCount = 0;

        while (true) {
            if (cacheService.tryLockDetail(productId, lockToken)) {
                try {
                    context.next();
                    context.putMeta(ProductReadMeta.SOURCE, ProductReadMeta.SOURCE_DB);
                    return;
                } finally {
                    cacheService.unlockDetail(productId, lockToken);
                }
            }

            waitCount++;
            context.putMeta(ProductReadMeta.LOCK_WAIT_COUNT, waitCount);
            DetailCacheResult cacheResult = cacheService.getDetail(productId);
            if (cacheResult.getState() == CacheState.HIT_VALUE) {
                context.putMeta(ProductReadMeta.SOURCE, ProductReadMeta.SOURCE_LOCAL_OR_REDIS);
                context.setResult(ExecutionResult.success(cacheResult.getValue()));
                return;
            }
            if (cacheResult.getState() == CacheState.HIT_NULL) {
                context.putMeta(ProductReadMeta.SOURCE, ProductReadMeta.SOURCE_LOCAL_OR_REDIS);
                throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
            }

            sleepBriefly();
        }
    }

    private String productId(ExecutionContext<ProductView> context) {
        return String.valueOf(context.getOperation().getMetadata().get(ProductReadOperations.META_PRODUCT_ID));
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(CACHE_LOCK_WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "cache lock wait interrupted");
        }
    }
}
