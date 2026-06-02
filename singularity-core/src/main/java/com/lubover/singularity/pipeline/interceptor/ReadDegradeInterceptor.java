package com.lubover.singularity.pipeline.interceptor;

import com.lubover.singularity.pipeline.ExecutionContext;
import com.lubover.singularity.pipeline.PipelineInterceptor;
import com.lubover.singularity.pipeline.read.ReadFallback;
import com.lubover.singularity.pipeline.read.ReadMeta;

public class ReadDegradeInterceptor<T> implements PipelineInterceptor<T> {

    private final ReadFallback<T> fallback;

    public ReadDegradeInterceptor(ReadFallback<T> fallback) {
        this.fallback = fallback;
    }

    @Override
    public void handle(ExecutionContext<T> context) {
        try {
            context.next();
        } catch (RuntimeException e) {
            context.putMeta(ReadMeta.DEGRADED, true);
            context.putMeta(ReadMeta.SOURCE, ReadMeta.SOURCE_FALLBACK);
            fallback.fallback(context, e);
        }
    }
}
