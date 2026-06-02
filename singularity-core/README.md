# Singularity Core Usage Guide

`singularity-core` now contains two compatible execution models:

- The original allocation model for high-concurrency write paths such as order seckill.
- The pipeline model for reusable read/write governance, especially high-concurrency read paths such as product detail and product list queries.

## Allocation Model

The original API remains unchanged:

```text
Actor -> Allocator -> Registry -> ShardPolicy -> Interceptor chain -> handler -> Result
```

Typical usage:

- `Registry` provides available `Slot` instances.
- `ShardPolicy` routes an `Actor` to a `Slot`.
- `Interceptor` adds cross-cutting behavior.
- The business handler performs the final write operation.

This model is still used by `singularity-order` and is not affected by the read pipeline changes.

## Pipeline Model

The pipeline API is centered on `Operation`:

```text
Operation -> PipelineExecutor -> PipelineInterceptor chain -> PipelineHandler -> ExecutionResult<T>
```

Core types:

- `Operation`, `OperationType`, `DefaultOperation`
- `PipelineExecutor`, `PipelineHandler<T>`, `PipelineInterceptor<T>`
- `ExecutionContext<T>`, `ExecutionResult<T>`
- Default implementation: `impl.DefaultPipelineExecutor`

`ExecutionResult<T>` carries structured metadata through `meta`, including latency, read source, lock wait count, read slot id, and degradation markers.

## Read Governance Components

Reusable read-path components live in `com.lubover.singularity.pipeline.read` and `com.lubover.singularity.pipeline.interceptor`.

Read abstractions:

- `ReadCache<T>`: bridges local/remote cache implementations.
- `CacheLookup<T>` and `CacheLookupState`: represent value hit, null hit, or miss.
- `ReadLockManager`: bridges distributed lock implementations.
- `ReadRegistry`, `ReadSlot`, `ReadShardPolicy`: provide logical read-slot routing.
- `ReadMeta`: standard metadata keys and source values.

Built-in interceptors:

- `MetricsTraceInterceptor`: records `latencyMs`.
- `ReadThroughCacheInterceptor`: local/remote cache read-through and cache backfill.
- `CacheStampedeGuardInterceptor`: single-flight cache miss protection.
- `ReadRateLimitInterceptor`: fixed-window per-key read throttling.
- `ReadDegradeInterceptor`: fallback on read-path runtime failures.
- `ReadRoutingInterceptor`: stable read-slot routing by operation key.

## Product Landing

`singularity-product` uses the pipeline model for product reads:

```text
ProductServiceImpl
  -> MetricsTraceInterceptor
  -> ReadThroughCacheInterceptor
  -> CacheStampedeGuardInterceptor
  -> ProductMapper DB fallback
```

Product-specific adapters are intentionally thin:

- `ProductDetailReadCache`
- `ProductListReadCache`
- `ProductReadLockManager`

This keeps Caffeine/Redis details in the product module while moving the reusable governance logic into core.

## Verification

```powershell
mvn -pl singularity-core test
mvn -pl singularity-product -am test
```

Latest local verification:

- `mvn -pl singularity-core test`: 8 tests passed.
- `mvn -pl singularity-product -am test`: 18 tests passed across core and product reactor.
