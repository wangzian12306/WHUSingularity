package com.lubover.singularity.product.read;

import com.lubover.singularity.pipeline.ExecutionContext;
import com.lubover.singularity.pipeline.ExecutionResult;
import com.lubover.singularity.pipeline.PipelineInterceptor;
import com.lubover.singularity.product.cache.ProductCacheService;
import com.lubover.singularity.product.cache.ProductCacheService.CacheState;
import com.lubover.singularity.product.cache.ProductCacheService.ListCacheResult;
import com.lubover.singularity.product.dto.PageResponse;
import com.lubover.singularity.product.dto.ProductView;

public class ProductListCacheInterceptor implements PipelineInterceptor<PageResponse<ProductView>> {

    private final ProductCacheService cacheService;

    public ProductListCacheInterceptor(ProductCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public void handle(ExecutionContext<PageResponse<ProductView>> context) {
        String queryHash = queryHash(context);
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

        context.next();

        ExecutionResult<PageResponse<ProductView>> result = context.getResult();
        if (result != null && result.isSuccess()) {
            PageResponse<ProductView> page = result.getData();
            if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
                cacheService.putList(queryHash, null);
            } else {
                cacheService.putList(queryHash, page);
            }
        }
    }

    private String queryHash(ExecutionContext<PageResponse<ProductView>> context) {
        return String.valueOf(context.getOperation().getMetadata().get(ProductReadOperations.META_QUERY_HASH));
    }

    private PageResponse<ProductView> emptyPage(ExecutionContext<PageResponse<ProductView>> context) {
        int pageNo = (Integer) context.getOperation().getMetadata().get(ProductReadOperations.META_PAGE_NO);
        int pageSize = (Integer) context.getOperation().getMetadata().get(ProductReadOperations.META_PAGE_SIZE);
        return PageResponse.of(java.util.List.of(), 0, pageNo, pageSize);
    }
}
