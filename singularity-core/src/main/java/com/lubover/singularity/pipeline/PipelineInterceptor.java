package com.lubover.singularity.pipeline;

@FunctionalInterface
public interface PipelineInterceptor<T> {

    void handle(ExecutionContext<T> context);
}
