package com.lubover.singularity.pipeline.interceptor;

import com.lubover.singularity.pipeline.ExecutionContext;
import com.lubover.singularity.pipeline.ExecutionResult;
import com.lubover.singularity.pipeline.PipelineInterceptor;
import com.lubover.singularity.pipeline.read.CacheLookup;
import com.lubover.singularity.pipeline.read.CacheLookupState;
import com.lubover.singularity.pipeline.read.CacheNullHitHandler;
import com.lubover.singularity.pipeline.read.ReadCache;
import com.lubover.singularity.pipeline.read.ReadLockManager;
import com.lubover.singularity.pipeline.read.ReadMeta;

import java.util.UUID;

public class CacheStampedeGuardInterceptor<T> implements PipelineInterceptor<T> {

    private final ReadLockManager lockManager;
    private final ReadCache<T> cache;
    private final CacheNullHitHandler<T> nullHitHandler;
    private final int maxWaitAttempts;
    private final long waitMillis;

    public CacheStampedeGuardInterceptor(
            ReadLockManager lockManager,
            ReadCache<T> cache,
            CacheNullHitHandler<T> nullHitHandler,
            int maxWaitAttempts,
            long waitMillis) {
        if (maxWaitAttempts < 0) {
            throw new IllegalArgumentException("maxWaitAttempts must not be negative");
        }
        if (waitMillis < 0) {
            throw new IllegalArgumentException("waitMillis must not be negative");
        }
        this.lockManager = lockManager;
        this.cache = cache;
        this.nullHitHandler = nullHitHandler;
        this.maxWaitAttempts = maxWaitAttempts;
        this.waitMillis = waitMillis;
    }

    @Override
    public void handle(ExecutionContext<T> context) {
        String token = UUID.randomUUID().toString();
        if (lockManager.tryLock(context, token)) {
            try {
                context.next();
                context.putMeta(ReadMeta.SOURCE, ReadMeta.SOURCE_DB);
                return;
            } finally {
                lockManager.unlock(context, token);
            }
        }

        for (int attempt = 1; attempt <= maxWaitAttempts; attempt++) {
            context.putMeta(ReadMeta.LOCK_WAIT_COUNT, attempt);
            CacheLookup<T> lookup = cache.get(context);
            if (lookup.getState() == CacheLookupState.HIT_VALUE) {
                context.putMeta(ReadMeta.SOURCE, ReadMeta.SOURCE_CACHE);
                context.putMeta(ReadMeta.CACHE_STATE, lookup.getState().name());
                context.setResult(ExecutionResult.success(lookup.getValue()));
                return;
            }
            if (lookup.getState() == CacheLookupState.HIT_NULL) {
                context.putMeta(ReadMeta.SOURCE, ReadMeta.SOURCE_CACHE);
                context.putMeta(ReadMeta.CACHE_STATE, lookup.getState().name());
                nullHitHandler.handle(context);
                return;
            }
            sleep();
        }

        context.setResult(ExecutionResult.failure("READ_LOCK_TIMEOUT", "read cache lock wait timeout"));
    }

    private void sleep() {
        if (waitMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(waitMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("read cache lock wait interrupted", e);
        }
    }
}
