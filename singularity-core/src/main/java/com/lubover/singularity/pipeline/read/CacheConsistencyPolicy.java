package com.lubover.singularity.pipeline.read;

import com.lubover.singularity.pipeline.ExecutionContext;

public interface CacheConsistencyPolicy<T> {

    CacheConsistencyPolicy<Object> NOOP = new CacheConsistencyPolicy<>() {
    };

    @SuppressWarnings("unchecked")
    static <T> CacheConsistencyPolicy<T> noop() {
        return (CacheConsistencyPolicy<T>) NOOP;
    }

    default void beforeCacheRead(ExecutionContext<T> context) {
    }

    default boolean shouldUseCache(ExecutionContext<T> context, CacheLookup<T> lookup) {
        return true;
    }

    default boolean shouldWriteCache(ExecutionContext<T> context, T value) {
        return true;
    }

    default void afterCacheWrite(ExecutionContext<T> context, T value) {
    }

    default void afterCacheWriteSkipped(ExecutionContext<T> context, T value) {
    }
}
