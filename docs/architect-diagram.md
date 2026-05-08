# WHUSingularity — Architecture Diagram

Microservice collaboration and data flow diagrams for the WHUSingularity high-concurrency flash-sale system.

---

## 1. System Overview — Component Diagram

```mermaid
graph TB
    subgraph Client["Client Layer"]
        FE["singularity-front<br/>React 19 + TypeScript<br/>Vite + Ant Design<br/>:3000"]
    end

    subgraph Infra["Infrastructure"]
        NACOS["Nacos<br/>Service Registry<br/>+ Config Center<br/>:8848"]
        REDIS["Redis<br/>:6379"]
        MYSQL["MySQL<br/>:3306"]
        MQ["RocketMQ<br/>NameServer :9876<br/>Broker :10911"]
        GATEWAY["singularity-gateway<br/>:8080<br/>Spring Cloud Gateway"]
    end

    subgraph Services["Microservices"]
        USER["singularity-user<br/>:8090<br/>DB: singularity_user"]
        ORDER["singularity-order<br/>:8081<br/>DB: singularity_order"]
        STOCK["singularity-stock<br/>:8082<br/>DB: singularity_stock"]
        PRODUCT["singularity-product<br/>:8087<br/>DB: singularity_product"]
        MERCHANT["singularity-merchant<br/>:8091<br/>DB: singularity_merchant"]
        SCALER["singularity-scaler<br/>:9090<br/>Auto-scaler / Metrics"]
    end

    FE -->|"HTTP REST"| GATEWAY
    GATEWAY -->|"/api/user"| USER
    GATEWAY -->|"/api/order"| ORDER
    GATEWAY -->|"/api/stock"| STOCK
    GATEWAY -->|"/api/product"| PRODUCT
    GATEWAY -->|"/api/merchant"| MERCHANT

    USER -->|"Register / Discover"| NACOS
    ORDER -->|"Register / Discover"| NACOS
    STOCK -->|"Register / Discover"| NACOS
    PRODUCT -->|"Register / Discover"| NACOS
    MERCHANT -->|"Register / Discover"| NACOS
    GATEWAY -->|"Register / Discover"| NACOS
    SCALER -->|"Register / Discover"| NACOS

    USER -->|"JWT blacklist<br/>User cache"| REDIS
    ORDER -->|"Stock counters<br/>Order hash"| REDIS
    STOCK -->|"Slot warmup"| REDIS
    PRODUCT -->|"Cache-Aside"| REDIS
    MERCHANT -->|"JWT blacklist"| REDIS

    USER -->|"User data"| MYSQL
    ORDER -->|"Order records"| MYSQL
    STOCK -->|"Stock + change logs"| MYSQL
    PRODUCT -->|"Product catalog"| MYSQL
    MERCHANT -->|"Merchant + products"| MYSQL

    ORDER -->|"Publish: order-topic<br/>(Transaction Message)"| MQ
    MQ -->|"Consume: order-topic<br/>order-consumer-group"| ORDER
    MQ -->|"Consume: order-topic<br/>stock-order-consumer-group"| STOCK
    MQ -->|"Consume: stock-topic<br/>stock-consumer-group"| STOCK

    ORDER -->|"OpenFeign<br/>GET /api/user/{id}<br/>POST /api/user/{id}/deduct"| USER
    SCALER -->|"docker run / rm"| Infra
```

---

## 2. Flash-Sale Order Flow — Sequence Diagram

The core high-concurrency path: user grabs an order (snag), distributed transaction commits, and both Order and Stock services consume the message.

```mermaid
sequenceDiagram
    actor User
    participant FE as Frontend
    participant ORDER as Order Service
    participant Core as singularity-core<br/>(DefaultAllocator)
    participant REDIS as Redis
    participant MQ as RocketMQ Broker
    participant ORDER_C as OrderConsumerService<br/>(Order Service)
    participant STOCK_C as OrderTopicConsumer<br/>(Stock Service)
    participant MYSQL_O as MySQL<br/>(singularity_order)
    participant MYSQL_S as MySQL<br/>(singularity_stock)

    User->>FE: Click "立即抢单"
    FE->>ORDER: POST /api/order/snag {userId}

    ORDER->>Core: allocator.allocate(actor)
    Note over Core: 1. SlotRegistry.getSlotList()<br/>   (filters sold-out slots via Caffeine cache)<br/>2. HashShardPolicy.selectSlot()<br/>   actor.id.hashCode() % slots.size()<br/>3. Build interceptor chain<br/>4. LoggingInterceptor (pre-log)

    Core->>MQ: sendMessageInTransaction(order-topic, OrderMessage)<br/>[HALF MESSAGE — not yet visible to consumers]

    MQ->>Core: executeLocalTransaction(msg, localTx)
    Core->>REDIS: Lua Script (atomic)<br/>① CHECK stock_counter > 0<br/>② DECR stock_counter<br/>③ HSET order:{orderId} {...fields}

    alt Stock available (counter > 0)
        REDIS-->>Core: remaining stock count
        Core->>MQ: LocalTransactionState.COMMIT_MESSAGE
        Note over Core: If remaining == 0:<br/>SlotRegistry.markEmpty(slotId)<br/>(Caffeine cache — slot excluded from future allocations)
        Core-->>ORDER: Result(success=true, orderId)
        ORDER-->>FE: { success: true, data: orderId }
        FE-->>User: Order grabbed! Polling status...
    else Stock exhausted
        REDIS-->>Core: -1 (counter was 0, INCR rolled back)
        Core->>MQ: LocalTransactionState.ROLLBACK_MESSAGE
        Core-->>ORDER: Result(success=false, "stock exhausted")
        ORDER-->>FE: { success: false, message: "..." }
        FE-->>User: Sorry, sold out
    end

    MQ--)ORDER_C: Deliver order-topic message<br/>(group: order-consumer-group, CONCURRENTLY)
    ORDER_C->>MYSQL_O: INSERT INTO orders<br/>(orderId, userId, slotId, productId, status=CREATED)
    Note over ORDER_C: DuplicateKeyException handled<br/>gracefully (idempotent)

    MQ--)STOCK_C: Deliver order-topic message<br/>(group: stock-order-consumer-group, ORDERLY)
    STOCK_C->>MYSQL_S: deductStock(productId, qty=1, orderId, messageId)<br/>① Check StockChangeLog for messageId (idempotency)<br/>② Optimistic lock: UPDATE stock SET reserved=reserved+1,<br/>   version=version+1 WHERE id=? AND version=?<br/>③ INSERT StockChangeLog (status=done)
    Note over STOCK_C: Up to 3 retries on version conflict
```

---

## 3. Payment Flow — Sequence Diagram

After an order is created, the user pays, triggering a synchronous cross-service call via OpenFeign.

```mermaid
sequenceDiagram
    actor User
    participant FE as Frontend
    participant ORDER as Order Service
    participant USER as User Service<br/>(via OpenFeign)
    participant MYSQL_O as MySQL<br/>(singularity_order)
    participant MYSQL_U as MySQL<br/>(singularity_user)

    User->>FE: Click "Pay"
    FE->>ORDER: POST /api/order/{orderId}/pay {userId}

    ORDER->>MYSQL_O: SELECT order WHERE orderId=?
    MYSQL_O-->>ORDER: Order (status=CREATED)

    ORDER->>ORDER: Validate:<br/>order.userId == userId<br/>order.status == CREATED

    ORDER->>USER: POST /api/user/{userId}/deduct<br/>{ "amount": productPrice }<br/>[Feign + Nacos service discovery]

    USER->>MYSQL_U: BEGIN TRANSACTION<br/>SELECT balance WHERE id=userId FOR UPDATE<br/>Validate balance >= amount<br/>UPDATE user SET balance = balance - amount<br/>COMMIT

    alt Balance sufficient
        MYSQL_U-->>USER: OK
        USER-->>ORDER: { success: true }
        ORDER->>MYSQL_O: UPDATE order SET status=PAID
        ORDER-->>FE: { success: true, data: order }
        FE-->>User: Payment successful
    else Insufficient balance
        MYSQL_U-->>USER: Error
        USER-->>ORDER: { success: false, message: "Insufficient balance" }
        ORDER-->>FE: { success: false, message: "..." }
        FE-->>User: Payment failed
    end
```

---

## 4. singularity-core Internal Architecture — Class Diagram

The custom high-concurrency allocation framework at the heart of the order service.

```mermaid
classDiagram
    class Allocator {
        <<interface>>
        +allocate(actor: Actor) Result
    }

    class Actor {
        <<interface>>
        +getId() String
        +getMetadata() Map~String, Object~
    }

    class Slot {
        <<interface>>
        +getId() String
        +getMetadata() Map~String, ?~
    }

    class Registry {
        <<interface>>
        +getSlotList() List~Slot~
    }

    class ShardPolicy {
        <<interface>>
        +selectSlot(actor: Actor, slots: List~Slot~) Optional~Slot~
    }

    class Interceptor {
        <<interface, FunctionalInterface>>
        +handle(context: Context)
    }

    class Context {
        <<interface>>
        +getActor() Actor
        +getSlot() Slot
        +getResult() Result
        +setResult(result: Result)
        +get(key: String) Object
        +put(key: String, value: Object)
        +next()
    }

    class Result {
        +isSuccess() boolean
        +getMessage() String
    }

    class DefaultAllocator {
        -registry: Registry
        -shardPolicy: ShardPolicy
        -interceptors: List~Interceptor~
        -handler: Interceptor
        +allocate(actor: Actor) Result
    }

    class DefaultContext {
        -actor: Actor
        -slot: Slot
        -chain: List~Interceptor~
        -index: int
        -store: Map~String, Object~
        -result: Result
        +next()
    }

    class StockSlot {
        -id: String
        -redisStockKey: String
        -productId: String
        +getMetadata() Map
    }

    class SlotRegistry {
        -slots: List~StockSlot~
        -emptyCache: Cache~String, Boolean~ (Caffeine)
        +getSlotList() List~Slot~
        +markEmpty(slotId: String)
    }

    class HashShardPolicy {
        +selectSlot(actor, slots) Optional~Slot~
    }

    class LoggingInterceptor {
        +handle(context: Context)
    }

    Allocator <|.. DefaultAllocator
    Context <|.. DefaultContext
    Slot <|.. StockSlot
    Registry <|.. SlotRegistry
    ShardPolicy <|.. HashShardPolicy
    Interceptor <|.. LoggingInterceptor

    DefaultAllocator --> Registry : uses
    DefaultAllocator --> ShardPolicy : uses
    DefaultAllocator --> Interceptor : chains
    DefaultAllocator --> DefaultContext : creates
    DefaultContext --> Actor : holds
    DefaultContext --> Slot : holds
    DefaultContext --> Interceptor : executes chain
```

---

## 5. RocketMQ Topic & Consumer Group Map

```mermaid
graph LR
    subgraph Producers
        OP["OrderServiceImpl<br/>(singularity-order)"]
    end

    subgraph Topics
        OT["order-topic"]
        ST["stock-topic"]
    end

    subgraph Consumers
        OC["OrderConsumerService<br/>group: order-consumer-group<br/>mode: CONCURRENTLY<br/>(singularity-order)"]
        SC["OrderTopicConsumer<br/>group: stock-order-consumer-group<br/>mode: ORDERLY<br/>(singularity-stock)"]
        STC["StockConsumer<br/>group: stock-consumer-group<br/>mode: ORDERLY<br/>(singularity-stock)"]
    end

    subgraph Actions
        OA["INSERT Order → MySQL<br/>status = CREATED<br/>(idempotent via DuplicateKeyException)"]
        SA["deductStock()<br/>UPDATE stock.reserved + 1<br/>INSERT StockChangeLog<br/>(idempotent via messageId)"]
        STA["deductStock() / returnStock()<br/>via pipe-delimited payload<br/>changeType=1 or 2"]
    end

    OP -->|"Transaction Message<br/>COMMIT on Lua success"| OT
    OT --> OC --> OA
    OT --> SC --> SA
    ST --> STC --> STA

    style OT fill:#f9f,stroke:#333
    style ST fill:#f9f,stroke:#333
```

---

## 6. Authentication Flow — JWT + Redis Blacklist

```mermaid
sequenceDiagram
    participant Client
    participant Filter as AuthFilter
    participant Redis as Redis<br/>(token blacklist)
    participant Handler as Request Handler

    Client->>Filter: HTTP Request<br/>Authorization: Bearer <JWT>

    Filter->>Filter: Parse & validate JWT signature<br/>(HMAC-SHA256, secret from config)
    Filter->>Filter: Check token expiry (exp claim)

    Filter->>Redis: EXISTS blacklist:{jti}
    Redis-->>Filter: 0 (not blacklisted)

    Filter->>Filter: Set request attributes:<br/>userId, role, jti, exp

    Filter->>Handler: Forward request

    Note over Client, Handler: Logout Flow
    Client->>Handler: POST /api/user/logout
    Handler->>Redis: SET blacklist:{jti} "" EX {remaining_ttl}
    Redis-->>Handler: OK
    Handler-->>Client: { success: true }
```

---

## 7. Data Model — Entity Relationship

```mermaid
erDiagram
    USER {
        bigint id PK
        varchar username UK
        varchar password
        varchar nickname
        varchar role
        decimal balance
        datetime createTime
        datetime updateTime
    }

    ORDER {
        varchar orderId PK
        varchar userId FK
        varchar slotId
        varchar productId
        varchar status
        datetime createTime
        datetime updateTime
    }

    STOCK {
        bigint id PK
        varchar productId UK
        int availableQuantity
        int reservedQuantity
        int totalQuantity
        int version
        datetime createTime
        datetime updateTime
    }

    STOCK_CHANGE_LOG {
        bigint id PK
        varchar messageId UK
        varchar productId FK
        int changeQuantity
        int changeType
        varchar orderId
        int status
        varchar remark
        datetime createTime
        datetime updateTime
    }

    MERCHANT {
        bigint id PK
        varchar username UK
        varchar password
        varchar shopName
        varchar contactName
        varchar contactPhone
        varchar address
        varchar status
        datetime createTime
        datetime updateTime
    }

    PRODUCT {
        bigint id PK
        bigint merchantId FK
        varchar productName
        text description
        decimal price
        decimal originalPrice
        varchar status
        int salesCount
        datetime createTime
        datetime updateTime
    }

    PRODUCT_INVENTORY {
        bigint id PK
        bigint productId FK
        int quantity
        datetime createTime
        datetime updateTime
    }

    USER ||--o{ ORDER : "places"
    STOCK ||--o{ STOCK_CHANGE_LOG : "has"
    MERCHANT ||--o{ PRODUCT : "owns"
    PRODUCT ||--|| PRODUCT_INVENTORY : "has"
```
