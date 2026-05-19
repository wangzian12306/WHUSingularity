package com.lubover.singularity.pipeline.impl;

import com.lubover.singularity.pipeline.ExecutionResult;
import com.lubover.singularity.pipeline.Operation;
import com.lubover.singularity.pipeline.PipelineExecutor;
import com.lubover.singularity.pipeline.PipelineHandler;
import com.lubover.singularity.pipeline.PipelineInterceptor;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class DefaultPipelineExecutor implements PipelineExecutor {

    @Override
    public <T> ExecutionResult<T> execute(
            Operation operation,
            List<PipelineInterceptor<T>> interceptors,
            PipelineHandler<T> handler) {
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(handler, "handler must not be null");

        List<PipelineInterceptor<T>> chain = interceptors == null
                ? Collections.emptyList()
                : List.copyOf(interceptors);
        DefaultExecutionContext<T> context = new DefaultExecutionContext<>(operation, chain, handler);
        context.next();

        ExecutionResult<T> result = context.getResult();
        if (result == null) {
            result = ExecutionResult.failure("NO_RESULT", "no result produced by pipeline");
        }
        return result.withMeta(context.getMeta());
    }
}
