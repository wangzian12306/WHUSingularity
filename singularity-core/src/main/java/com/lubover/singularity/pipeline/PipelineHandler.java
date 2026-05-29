package com.lubover.singularity.pipeline;

@FunctionalInterface
public interface PipelineHandler<T> {

    void handle(ExecutionContext<T> context);
}
