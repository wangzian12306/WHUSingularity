package com.lubover.singularity.product.read;

import com.lubover.singularity.pipeline.ExecutionContext;
import com.lubover.singularity.pipeline.ExecutionResult;
import com.lubover.singularity.pipeline.PipelineInterceptor;
import com.lubover.singularity.product.cache.ProductCacheService;
import com.lubover.singularity.product.cache.ProductCacheService.CacheState;
import com.lubover.singularity.product.cache.ProductCacheService.ListCacheResult;
import com.lubover.singularity.product.dto.PageResponse;
import com.lubover.singularity.product.dto.ProductView;
import com.lubover.singularity.product.exception.BusinessException;
import com.lubover.singularity.product.exception.ErrorCode;

import java.util.List;
import java.util.UUID;

public class ProductListStampedeInterceptor implements PipelineInterceptor<PageResponse<ProductView>> {

    private static final long CACHE_LOCK_WAIT_MS = 50L;

    private final ProductCacheService cacheService;

    public ProductListStampedeInterceptor(ProductCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public void handle(ExecutionContext<PageResponse<ProductView>> context) {
        String queryHash = queryHash(context);
        String lockToken = UUID.randomUUID().toString();
        int waitCount = 0;

        while (true) {
            if (cacheService.tryLockList(queryHash, lockToken)) {
                try {
                    context.next();
                    context.putMeta(ProductReadMeta.SOURCE, ProductReadMeta.SOURCE_DB);
                    return;
                } finally {
                    cacheService.unlockList(queryHash, lockToken);
                }
            }

            waitCount++;
            context.putMeta(ProductReadMeta.LOCK_WAIT_COUNT, waitCount);
            ListCacheResult cacheResult = cacheService.getList(queryHash);
            if (cacheResult.getState() == CacheState.HIT_VALUE) {
                context.putMeta(ProductReadMeta.SOURCE, ProductReadMeta.SOURCE_LOCAL_OR_REDIS);
                context.setResult(ExecutionResult.success(cacheResult.getValue()));
                return;
            }
            if (cacheResult.getState() == CacheState.HIT_NULL) {
                context.putMeta(ProductReadMeta.SOURCE, ProductReadMeta.SOURCE_LOCAL_OR_REDIS);
                context.setResult(ExecutionResult.success(emptyPage(context)));
                return;
            }

            sleepBriefly();
        }
    }

    private String queryHash(ExecutionContext<PageResponse<ProductView>> context) {
        return String.valueOf(context.getOperation().getMetadata().get(ProductReadOperations.META_QUERY_HASH));
    }

    private PageResponse<ProductView> emptyPage(ExecutionContext<PageResponse<ProductView>> context) {
        int pageNo = (Integer) context.getOperation().getMetadata().get(ProductReadOperations.META_PAGE_NO);
        int pageSize = (Integer) context.getOperation().getMetadata().get(ProductReadOperations.META_PAGE_SIZE);
        return PageResponse.of(List.of(), 0, pageNo, pageSize);
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
