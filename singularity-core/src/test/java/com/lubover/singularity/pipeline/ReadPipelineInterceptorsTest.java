package com.lubover.singularity.pipeline;

import com.lubover.singularity.pipeline.impl.DefaultPipelineExecutor;
import com.lubover.singularity.pipeline.interceptor.CacheStampedeGuardInterceptor;
import com.lubover.singularity.pipeline.interceptor.ReadDegradeInterceptor;
import com.lubover.singularity.pipeline.interceptor.ReadRateLimitInterceptor;
import com.lubover.singularity.pipeline.interceptor.ReadRoutingInterceptor;
import com.lubover.singularity.pipeline.interceptor.ReadThroughCacheInterceptor;
import com.lubover.singularity.pipeline.read.CacheLookup;
import com.lubover.singularity.pipeline.read.HashReadShardPolicy;
import com.lubover.singularity.pipeline.read.ReadCache;
import com.lubover.singularity.pipeline.read.ReadLockManager;
import com.lubover.singularity.pipeline.read.ReadMeta;
import com.lubover.singularity.pipeline.read.ReadSlot;
import com.lubover.singularity.pipeline.read.StaticReadRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadPipelineInterceptorsTest {

    private final PipelineExecutor executor = new DefaultPipelineExecutor();

    @Test
    void readThroughCache_shouldShortCircuitOnCacheHit() {
        AtomicInteger handlerCalls = new AtomicInteger();
        ReadCache<String> cache = new StubReadCache(CacheLookup.value("cached"));

        ExecutionResult<String> result = executor.execute(
                DefaultOperation.read("product:detail:p-1"),
                List.of(new ReadThroughCacheInterceptor<>(cache, context ->
                        context.setResult(ExecutionResult.failure("NOT_FOUND", "not found")))),
                context -> {
                    handlerCalls.incrementAndGet();
                    context.setResult(ExecutionResult.success("db"));
                });

        assertTrue(result.isSuccess());
        assertEquals("cached", result.getData());
        assertEquals(0, handlerCalls.get());
        assertEquals(ReadMeta.SOURCE_CACHE, result.getMeta().get(ReadMeta.SOURCE));
    }

    @Test
    void stampedeGuard_shouldWaitAndReuseCacheWhenLockIsHeld() {
        AtomicInteger cacheReads = new AtomicInteger();
        ReadCache<String> cache = new ReadCache<>() {
            @Override
            public CacheLookup<String> get(Operation operation) {
                return cacheReads.incrementAndGet() == 1
                        ? CacheLookup.miss()
                        : CacheLookup.value("filled-by-other-request");
            }

            @Override
            public void put(Operation operation, String value) {
            }
        };
        ReadLockManager lockManager = new ReadLockManager() {
            @Override
            public boolean tryLock(Operation operation, String token) {
                return false;
            }

            @Override
            public void unlock(Operation operation, String token) {
            }
        };

        ExecutionResult<String> result = executor.execute(
                DefaultOperation.read("product:detail:p-1"),
                List.of(new CacheStampedeGuardInterceptor<>(lockManager, cache, context ->
                        context.setResult(ExecutionResult.failure("NOT_FOUND", "not found")), 3, 0)),
                context -> context.setResult(ExecutionResult.success("db")));

        assertTrue(result.isSuccess());
        assertEquals("filled-by-other-request", result.getData());
        assertEquals(2, cacheReads.get());
        assertEquals(ReadMeta.SOURCE_CACHE, result.getMeta().get(ReadMeta.SOURCE));
    }

    @Test
    void readRateLimit_shouldThrottleAfterWindowQuota() {
        ReadRateLimitInterceptor<String> limiter = new ReadRateLimitInterceptor<>(1, 60_000);
        Operation operation = DefaultOperation.read("product:detail:p-1");

        ExecutionResult<String> first = executor.execute(
                operation,
                List.of(limiter),
                context -> context.setResult(ExecutionResult.success("ok")));
        ExecutionResult<String> second = executor.execute(
                operation,
                List.of(limiter),
                context -> context.setResult(ExecutionResult.success("ok")));

        assertTrue(first.isSuccess());
        assertFalse(second.isSuccess());
        assertEquals("THROTTLED", second.getCode());
    }

    @Test
    void readDegrade_shouldReturnFallbackWhenHandlerFails() {
        ExecutionResult<String> result = executor.execute(
                DefaultOperation.read("product:detail:p-1"),
                List.of(new ReadDegradeInterceptor<>((context, cause) ->
                        context.setResult(ExecutionResult.success("fallback")))),
                context -> {
                    throw new IllegalStateException("db down");
                });

        assertTrue(result.isSuccess());
        assertEquals("fallback", result.getData());
        assertEquals(Boolean.TRUE, result.getMeta().get(ReadMeta.DEGRADED));
        assertEquals(ReadMeta.SOURCE_FALLBACK, result.getMeta().get(ReadMeta.SOURCE));
    }

    @Test
    void readRouting_shouldWriteStableSlotIdToMeta() {
        StaticReadRegistry registry = new StaticReadRegistry(List.of(new ReadSlot("r0"), new ReadSlot("r1")));
        HashReadShardPolicy policy = new HashReadShardPolicy();

        ExecutionResult<String> first = executor.execute(
                DefaultOperation.read("product:detail:p-1"),
                List.of(new ReadRoutingInterceptor<>(registry, policy)),
                context -> context.setResult(ExecutionResult.success("ok")));
        ExecutionResult<String> second = executor.execute(
                DefaultOperation.read("product:detail:p-1"),
                List.of(new ReadRoutingInterceptor<>(registry, policy)),
                context -> context.setResult(ExecutionResult.success("ok")));

        assertTrue(first.isSuccess());
        assertEquals(first.getMeta().get(ReadMeta.READ_SLOT_ID), second.getMeta().get(ReadMeta.READ_SLOT_ID));
    }

    private static class StubReadCache implements ReadCache<String> {
        private final CacheLookup<String> lookup;

        private StubReadCache(CacheLookup<String> lookup) {
            this.lookup = lookup;
        }

        @Override
        public CacheLookup<String> get(Operation operation) {
            return lookup;
        }

        @Override
        public void put(Operation operation, String value) {
        }
    }
}
