package com.lubover.singularity.pipeline.interceptor;

import com.lubover.singularity.pipeline.ExecutionContext;
import com.lubover.singularity.pipeline.ExecutionResult;
import com.lubover.singularity.pipeline.PipelineInterceptor;
import com.lubover.singularity.pipeline.read.CacheConsistencyPolicy;
import com.lubover.singularity.pipeline.read.CacheLookup;
import com.lubover.singularity.pipeline.read.CacheLookupState;
import com.lubover.singularity.pipeline.read.CacheNullHitHandler;
import com.lubover.singularity.pipeline.read.ReadCache;
import com.lubover.singularity.pipeline.read.ReadMeta;

public class ReadThroughCacheInterceptor<T> implements PipelineInterceptor<T> {

    private final ReadCache<T> cache;
    private final CacheNullHitHandler<T> nullHitHandler;
    private final CacheConsistencyPolicy<T> consistencyPolicy;

    public ReadThroughCacheInterceptor(ReadCache<T> cache, CacheNullHitHandler<T> nullHitHandler) {
        this(cache, nullHitHandler, CacheConsistencyPolicy.noop());
    }

    public ReadThroughCacheInterceptor(
            ReadCache<T> cache,
            CacheNullHitHandler<T> nullHitHandler,
            CacheConsistencyPolicy<T> consistencyPolicy) {
        this.cache = cache;
        this.nullHitHandler = nullHitHandler;
        this.consistencyPolicy = consistencyPolicy == null
                ? CacheConsistencyPolicy.noop()
                : consistencyPolicy;
    }

    @Override
    public void handle(ExecutionContext<T> context) {
        consistencyPolicy.beforeCacheRead(context);
        CacheLookup<T> lookup = cache.get(context);
        lookup.getMeta().forEach(context::putMeta);
        context.putMeta(ReadMeta.CACHE_STATE, lookup.getState().name());
        if (lookup.getState() != CacheLookupState.MISS && !consistencyPolicy.shouldUseCache(context, lookup)) {
            context.putMeta(ReadMeta.CACHE_CONSISTENCY, ReadMeta.CACHE_CONSISTENCY_REJECTED);
            lookup = CacheLookup.miss();
        }
        if (lookup.getState() == CacheLookupState.HIT_VALUE) {
            context.putMeta(ReadMeta.SOURCE, ReadMeta.SOURCE_CACHE);
            context.putMeta(ReadMeta.CACHE_CONSISTENCY, ReadMeta.CACHE_CONSISTENCY_ACCEPTED);
            context.setResult(ExecutionResult.success(lookup.getValue()));
            return;
        }
        if (lookup.getState() == CacheLookupState.HIT_NULL) {
            context.putMeta(ReadMeta.SOURCE, ReadMeta.SOURCE_CACHE);
            context.putMeta(ReadMeta.CACHE_CONSISTENCY, ReadMeta.CACHE_CONSISTENCY_ACCEPTED);
            nullHitHandler.handle(context);
            return;
        }

        context.next();

        ExecutionResult<T> result = context.getResult();
        if (result != null && result.isSuccess()) {
            T data = result.getData();
            if (consistencyPolicy.shouldWriteCache(context, data)) {
                cache.put(context, data);
                consistencyPolicy.afterCacheWrite(context, data);
            } else {
                context.putMeta(ReadMeta.CACHE_WRITE_SKIPPED, true);
                consistencyPolicy.afterCacheWriteSkipped(context, data);
            }
        }
    }
}
