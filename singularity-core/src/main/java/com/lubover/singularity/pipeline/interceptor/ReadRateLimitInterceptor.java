package com.lubover.singularity.pipeline.interceptor;

import com.lubover.singularity.pipeline.ExecutionContext;
import com.lubover.singularity.pipeline.ExecutionResult;
import com.lubover.singularity.pipeline.PipelineInterceptor;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ReadRateLimitInterceptor<T> implements PipelineInterceptor<T> {

    private final int maxRequestsPerWindow;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public ReadRateLimitInterceptor(int maxRequestsPerWindow, long windowMillis) {
        this(maxRequestsPerWindow, windowMillis, Clock.systemUTC());
    }

    ReadRateLimitInterceptor(int maxRequestsPerWindow, long windowMillis, Clock clock) {
        if (maxRequestsPerWindow <= 0) {
            throw new IllegalArgumentException("maxRequestsPerWindow must be positive");
        }
        if (windowMillis <= 0) {
            throw new IllegalArgumentException("windowMillis must be positive");
        }
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    @Override
    public void handle(ExecutionContext<T> context) {
        String key = context.getOperation().getKey();
        long now = clock.millis();
        WindowCounter counter = counters.compute(key, (ignored, existing) -> {
            if (existing == null || now >= existing.windowStartMillis + windowMillis) {
                return new WindowCounter(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        if (counter.count.get() > maxRequestsPerWindow) {
            context.setResult(ExecutionResult.failure("THROTTLED", "read request throttled"));
            return;
        }

        context.next();
    }

    private static class WindowCounter {
        private final long windowStartMillis;
        private final AtomicInteger count;

        private WindowCounter(long windowStartMillis, AtomicInteger count) {
            this.windowStartMillis = windowStartMillis;
            this.count = count;
        }
    }
}
