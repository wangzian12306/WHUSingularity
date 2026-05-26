package com.lubover.singularity.pipeline.interceptor;

import com.lubover.singularity.pipeline.ExecutionContext;
import com.lubover.singularity.pipeline.ExecutionResult;
import com.lubover.singularity.pipeline.PipelineInterceptor;
import com.lubover.singularity.pipeline.read.CacheLookup;
import com.lubover.singularity.pipeline.read.CacheLookupState;
import com.lubover.singularity.pipeline.read.CacheNullHitHandler;
import com.lubover.singularity.pipeline.read.ReadCache;
import com.lubover.singularity.pipeline.read.ReadMeta;

public class ReadThroughCacheInterceptor<T> implements PipelineInterceptor<T> {

    private final ReadCache<T> cache;
    private final CacheNullHitHandler<T> nullHitHandler;

    public ReadThroughCacheInterceptor(ReadCache<T> cache, CacheNullHitHandler<T> nullHitHandler) {
        this.cache = cache;
        this.nullHitHandler = nullHitHandler;
    }

    @Override
    public void handle(ExecutionContext<T> context) {
        CacheLookup<T> lookup = cache.get(context);
        context.putMeta(ReadMeta.CACHE_STATE, lookup.getState().name());
        if (lookup.getState() == CacheLookupState.HIT_VALUE) {
            context.putMeta(ReadMeta.SOURCE, ReadMeta.SOURCE_CACHE);
            context.setResult(ExecutionResult.success(lookup.getValue()));
            return;
        }
        if (lookup.getState() == CacheLookupState.HIT_NULL) {
            context.putMeta(ReadMeta.SOURCE, ReadMeta.SOURCE_CACHE);
            nullHitHandler.handle(context);
            return;
        }

        context.next();

        ExecutionResult<T> result = context.getResult();
        if (result != null && result.isSuccess()) {
            cache.put(context, result.getData());
        }
    }
}
