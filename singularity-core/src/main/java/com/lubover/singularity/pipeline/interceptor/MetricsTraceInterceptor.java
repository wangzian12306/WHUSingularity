package com.lubover.singularity.pipeline.interceptor;

import com.lubover.singularity.pipeline.ExecutionContext;
import com.lubover.singularity.pipeline.PipelineInterceptor;

public class MetricsTraceInterceptor<T> implements PipelineInterceptor<T> {

    public static final String META_LATENCY_MS = "latencyMs";

    @Override
    public void handle(ExecutionContext<T> context) {
        long start = System.currentTimeMillis();
        try {
            context.next();
        } finally {
            context.putMeta(META_LATENCY_MS, System.currentTimeMillis() - start);
        }
    }
}
