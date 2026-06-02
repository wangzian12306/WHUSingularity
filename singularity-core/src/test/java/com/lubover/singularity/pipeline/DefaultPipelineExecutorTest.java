package com.lubover.singularity.pipeline;

import com.lubover.singularity.pipeline.impl.DefaultPipelineExecutor;
import com.lubover.singularity.pipeline.interceptor.MetricsTraceInterceptor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultPipelineExecutorTest {

    private final PipelineExecutor executor = new DefaultPipelineExecutor();

    @Test
    void execute_shouldRunInterceptorsAroundHandlerInOrder() {
        List<String> events = new ArrayList<>();
        PipelineInterceptor<String> first = context -> {
            events.add("first-before");
            context.next();
            events.add("first-after");
        };
        PipelineInterceptor<String> second = context -> {
            events.add("second-before");
            context.next();
            events.add("second-after");
        };

        ExecutionResult<String> result = executor.execute(
                DefaultOperation.read("product:detail:p-1"),
                List.of(first, second),
                context -> {
                    events.add("handler");
                    context.setResult(ExecutionResult.success("ok"));
                });

        assertTrue(result.isSuccess());
        assertEquals("ok", result.getData());
        assertEquals(List.of("first-before", "second-before", "handler", "second-after", "first-after"), events);
    }

    @Test
    void execute_shouldAllowInterceptorToShortCircuitHandler() {
        PipelineInterceptor<String> shortCircuit = context ->
                context.setResult(ExecutionResult.failure("THROTTLED", "too many requests"));

        ExecutionResult<String> result = executor.execute(
                DefaultOperation.read("product:detail:p-1"),
                List.of(shortCircuit),
                context -> context.setResult(ExecutionResult.success("should-not-run")));

        assertFalse(result.isSuccess());
        assertEquals("THROTTLED", result.getCode());
    }

    @Test
    void execute_shouldExposeMetaFromInterceptors() {
        ExecutionResult<String> result = executor.execute(
                DefaultOperation.read("product:detail:p-1"),
                List.of(new MetricsTraceInterceptor<>()),
                context -> {
                    context.putMeta("source", "DB");
                    context.setResult(ExecutionResult.success("value"));
                });

        assertTrue(result.isSuccess());
        assertEquals("DB", result.getMeta().get("source"));
        assertNotNull(result.getMeta().get(MetricsTraceInterceptor.META_LATENCY_MS));
    }
}
