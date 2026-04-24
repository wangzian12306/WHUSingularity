# WHUSingularity Architecture

WHUSingularity is a Spring Cloud based microservice system for a seckill/order-snatching scenario.
The important runtime boundary is the collaboration between `singularity-user`, `singularity-order`, and `singularity-stock`, with Nacos, Redis, RocketMQ, and MySQL as shared infrastructure.

`singularity-core` is not an independently deployed service. It is a shared library used mainly by `singularity-order` to implement slot-based high-concurrency allocation.

## Runtime Architecture

```mermaid
flowchart LR
    client[React Frontend\nVite + TypeScript + Ant Design]

    subgraph services[Microservices]
        user[singularity-user\n:8090\nRegister / Login / JWT / Balance]
        order[singularity-order\n:8081\nSnag Order / Order Query\nuses singularity-core]
        stock[singularity-stock\n:8082\nStock Query / Warmup / Async Deduction]
    end

    subgraph infra[Infrastructure]
        nacos[Nacos\nService Registry + Config Center]
        redis[(Redis)]
        rocketmq[RocketMQ\nNameServer + Broker]
        userdb[(MySQL\nuser DB)]
        orderdb[(MySQL\norder DB)]
        stockdb[(MySQL\nstock DB)]
    end

    client -->|/api/user| user
    client -->|/api/order| order
    client -->|/api/stock| stock

    user -->|register / discover / config| nacos
    order -->|register / discover / config| nacos
    stock -->|register / discover / config| nacos

    order -->|OpenFeign via Nacos\nfetch user info for cross-service checks| user

    user -->|JWT blacklist\nuser cache| redis
    order -->|slot stock bucket\ntransaction state / order shadow record| redis
    stock -->|slot preheat\nRedis bucket initialization| redis

    order -->|transactional half message\npublish order-topic| rocketmq
    rocketmq -->|consume order-topic\npersist order| order
    rocketmq -->|consume order-topic\ndeduct physical stock| stock
    rocketmq -->|optional stock-topic\nstock change events| stock

    user --> userdb
    order --> orderdb
    stock --> stockdb
```

## Service Responsibilities

- `singularity-user`
  Handles registration, login, JWT issuance, token blacklist, user profile, and balance operations.
- `singularity-order`
  Owns the seckill entrypoint. It uses `singularity-core` to route an actor to a slot, performs Redis-based stock deduction inside a RocketMQ transactional flow, and exposes order query APIs.
- `singularity-stock`
  Owns stock persistence and stock change logs. It also preheats Redis stock buckets and asynchronously updates physical stock after order messages are committed.

## Key Interaction Patterns

- Synchronous interaction
  `singularity-order` calls `singularity-user` through OpenFeign, with service discovery handled by Nacos.
- Asynchronous interaction
  `singularity-order` publishes transactional messages to RocketMQ. Both `singularity-order` and `singularity-stock` consume `order-topic`:
  `singularity-order` persists the order record, while `singularity-stock` deducts the actual stock record.
- Shared fast-path state
  Redis is part of the hot path. Order seckill traffic does not directly compete on MySQL row updates first; it hits Redis bucket stock first, which is critical for high concurrency.
- Persistent state
  Each service keeps its own database boundary: user data in user DB, order data in order DB, stock data in stock DB.

## Seckill Order Flow

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant O as singularity-order
    participant U as singularity-user
    participant R as Redis
    participant MQ as RocketMQ
    participant ODB as order DB
    participant S as singularity-stock
    participant SDB as stock DB

    FE->>O: POST /api/order/snag
    O->>U: OpenFeign call for user-related validation/discovery
    U-->>O: User data
    O->>O: singularity-core allocate(actor -> slot)
    O->>MQ: Send half message to order-topic
    MQ->>O: executeLocalTransaction()
    O->>R: Lua: decrement Redis bucket stock
    O->>R: Write order shadow record for tx check
    alt local transaction succeeds
        O-->>MQ: COMMIT_MESSAGE
        MQ-->>O: Deliver order-topic
        O->>ODB: Insert order record
        MQ-->>S: Deliver order-topic
        S->>SDB: Deduct physical stock and write change log
        O-->>FE: success + orderId
    else local transaction fails
        O-->>MQ: ROLLBACK_MESSAGE
        O-->>FE: failure
    end
```

## Why This Architecture Matters

- The system separates user, order, and stock concerns into independent services instead of coupling them into one monolith.
- The hottest business path is centered in `singularity-order`, but it offloads durable propagation to RocketMQ and uses Redis to absorb concurrency pressure.
- `singularity-stock` is eventually consistent with the Redis fast path, which is a deliberate design tradeoff for seckill throughput.
- Nacos is the control plane for both service registration and centralized runtime configuration.

## Current Architectural Notes

- `singularity-core` is a framework module embedded into `singularity-order`, not a standalone service.
- The repo documentation mentions a possible product service concept, but the current runnable microservice set is `user`, `order`, and `stock`.
- Order status progression is still limited in the current implementation: the order consumer persists orders with status `CREATED`, and there is not yet a full downstream state machine for payment/cancel/final success transitions.
