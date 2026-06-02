package com.lubover.singularity.pipeline;

import java.util.List;

public interface PipelineExecutor {

    <T> ExecutionResult<T> execute(
            Operation operation,
            List<PipelineInterceptor<T>> interceptors,
            PipelineHandler<T> handler);
}
