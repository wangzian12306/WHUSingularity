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

public class ProductDetailCacheInterceptor implements PipelineInterceptor<ProductView> {

    private final ProductCacheService cacheService;

    public ProductDetailCacheInterceptor(ProductCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public void handle(ExecutionContext<ProductView> context) {
        String productId = productId(context);
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

        context.next();

        ExecutionResult<ProductView> result = context.getResult();
        if (result != null && result.isSuccess()) {
            cacheService.putDetail(productId, result.getData());
        }
    }

    private String productId(ExecutionContext<ProductView> context) {
        return String.valueOf(context.getOperation().getMetadata().get(ProductReadOperations.META_PRODUCT_ID));
    }
}
