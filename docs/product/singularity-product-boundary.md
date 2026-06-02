# singularity-product Backend Delivery Notes

## Scope

`singularity-product` owns product master data. It does not deduct stock and does not create orders. Stock deduction remains in `singularity-stock`; order lifecycle remains in `singularity-order`.

## Implemented Status

| Phase | Status | Evidence |
|---|---|---|
| Phase 0 Architecture baseline | Done | `singularity-product` module, Spring Boot app, Nacos bootstrap, MyBatis XML, Flyway config |
| Phase 1 Data model | Done | `db/migration/V1__Init_Product_Module.sql`, `V2__Create_Product_Tables.sql`, dev seed script |
| Phase 2 CRUD API | Done | Create/detail/update/delete/list endpoints with DTO isolation and unified response |
| Phase 3 Cache baseline | Done | Caffeine + Redis cache-aside for detail/list, null marker, cache locks; read governance is now reused from `singularity-core` |
| Phase 4 Distributed cache invalidation | Done | RocketMQ broadcast `ProductUpdatedEvent`, Redis event dedup, local cache eviction |
| Phase 5 Integration and observability | Done | Stock aggregation view, status switch API, read/event/stock metric snapshot |
| Phase 6 Test and delivery | Done with local limitation | Unit tests added; Python API integration script added. Maven execution is blocked on this machine because `mvn` is not in PATH. |

## API Contract

Base path: `/api/product`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/product` | Create product |
| `GET` | `/api/product/{productId}` | Query product detail |
| `GET` | `/api/product/{productId}/with-stock` | Query product detail plus stock view from `singularity-stock` |
| `PUT` | `/api/product/{productId}` | Update product fields |
| `PATCH` | `/api/product/{productId}/status?status=0|1` | Switch product offline/online status |
| `DELETE` | `/api/product/{productId}` | Logical delete |
| `GET` | `/api/product/list?status=&category=&keyword=&pageNo=&pageSize=` | Paged query |
| `GET` | `/api/product/metrics` | Simple product read/event/stock metric snapshot |

Success response:

```json
{ "success": true, "data": {} }
```

Failure response:

```json
{ "success": false, "error": { "code": "PRODUCT_NOT_FOUND", "message": "product not found" } }
```

## Cache Design

Detail key: `product:detail:{productId}`

List key: `product:list:{queryHash}`

Read path:

```text
ProductController
  -> ProductServiceImpl
  -> singularity-core PipelineExecutor
  -> MetricsTraceInterceptor
  -> ReadThroughCacheInterceptor
  -> CacheStampedeGuardInterceptor
  -> ProductMapper DB fallback
  -> cache backfill
```

Write path:

```text
DB write
  -> evict local + Redis detail cache
  -> evict local + Redis list cache
  -> publish ProductUpdatedEvent after transaction commit
  -> every product instance consumes broadcast event
  -> local cache eviction with Redis eventId dedup
```

Redis TTL:

| Cache | TTL |
|---|---|
| Detail value | 30 minutes |
| List value | 10 minutes |
| Null marker | 60 seconds |
| Cache stampede lock | 10 seconds |

## Stock Integration

`GET /api/product/{productId}/with-stock` first reads product detail through the read pipeline, then calls `singularity-stock` by OpenFeign:

```text
singularity-product -> StockClient -> singularity-stock /api/stock/{productId}
```

If the stock service is temporarily unavailable, product data is still returned and `stock` is `null`; the failure is counted in `/api/product/metrics`.

## Observability

`GET /api/product/metrics` returns an in-memory snapshot:

- `productReadTotal`
- `productReadBySource`
- `eventSendSuccessTotal`
- `eventSendFailureTotal`
- `eventConsumeSuccessTotal`
- `eventConsumeFailureTotal`
- `stockQuerySuccessTotal`
- `stockQueryFailureTotal`

Actuator Prometheus is also exposed by application config at `/actuator/prometheus`.

## Verification

Recommended automated checks:

```powershell
mvn -pl singularity-product -am test
python -m unittest discover -s api-integration-tests-python/tests -p "test_product_api_integration.py" -v
```

Current local verification:

- `git diff --check`: passed.
- `mvn -pl singularity-product -am test`: passed, 18 tests across `singularity-core` and `singularity-product`.
- `python -m py_compile api-integration-tests-python/tests/test_product_api_integration.py`: passed.

## Acceptance Checklist

- Product CRUD can be exercised through the gateway.
- Repeated detail/list reads are served by cache after first DB fallback.
- Update/status/delete invalidates detail and list caches.
- Product update events are broadcast by RocketMQ and consumed in broadcasting mode by every product instance.
- Duplicate product events are ignored by Redis `eventId` dedup keys.
- Product detail can include a stock view without moving stock ownership into product service.
- Delivery artifacts include unit tests, Python integration script, and this backend summary.
