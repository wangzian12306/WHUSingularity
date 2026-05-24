package com.lubover.singularity.pipeline.read;

import com.lubover.singularity.pipeline.ExecutionContext;

@FunctionalInterface
public interface CacheNullHitHandler<T> {

    void handle(ExecutionContext<T> context);
}
