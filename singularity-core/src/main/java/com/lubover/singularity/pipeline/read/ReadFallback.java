package com.lubover.singularity.pipeline.read;

import com.lubover.singularity.pipeline.ExecutionContext;

@FunctionalInterface
public interface ReadFallback<T> {

    void fallback(ExecutionContext<T> context, Exception cause);
}
