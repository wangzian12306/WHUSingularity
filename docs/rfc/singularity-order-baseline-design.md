# singularity-order-baseline 设计方案

## 背景

当前 `singularity-order` 的写链路优化点主要集中在以下几类能力上：

- `singularity-core` 的 `Allocator / Registry / ShardPolicy / Interceptor` 抽象
- 将一个商品的库存拆成多个 slot，对 actor 做分流
- `SlotRegistry` 的本地缓存与售罄标记缓存
- 多实例并行部署，由网关和服务发现分摊流量
- Redis 前置预扣库存，RocketMQ 异步落库

这套方案适合展示“优化后的系统上限”，但如果没有一个足够朴素、边界清晰的 baseline，就很难回答下面几个问题：

- 单机单 key 的极限吞吐是多少
- slot 拆分到底带来了多少收益
- 本地缓存和多实例扩展分别贡献了多少收益
- 在同一台机器、同一套中间件上，优化前后的 p50 / p95 / p99 差异到底有多大

因此，需要单独实现一个 `singularity-order-baseline` 服务，作为写链路基线版本。

这个 baseline 的目标不是替代现有 `singularity-order`，而是作为压测和论文/汇报中的对照组。

## 目标

实现一个新的基准服务 `singularity-order-baseline`，满足以下约束：

- 单机版：只部署一个实例，不接入自动扩缩容，不依赖 Nacos 做服务发现
- 无缓存：不使用 `singularity-core`，不使用本地 Caffeine，不维护 slot 列表缓存，不做售罄本地短路
- 单点 Redis key：同一个压测商品只对应一个 Redis 库存 key，不做 slot 分桶
- 保持尽量接近当前业务语义：依然是 Redis 前置拦截高并发写请求，订单异步落库，库存异步落库
- 对上层接口尽量兼容：保留 `/api/order/*` 这组接口，便于前端、脚本或网关切换目标服务
- 便于压测：配置简单、依赖少、行为稳定、容易重置测试数据

## 非目标

以下内容不在 baseline 的目标内：

- 不追求生产可用的高一致性分布式事务
- 不追求多商品、多商户、多租户下的通用能力
- 不接入 `singularity-core`
- 不支持横向扩展
- 不实现 slot 预热、slot 失效、slot 分流
- 不引入任何本地热点缓存优化

## 推荐方案

推荐将 `singularity-order-baseline` 实现为一个独立 Maven 模块，使用基础 Spring Boot 技术栈：

- Spring Boot Web
- Spring Boot Data Redis
- MyBatis + MySQL
- Flyway
- RocketMQ Spring Boot Starter
- Actuator + Prometheus

刻意不引入以下依赖：

- `singularity-core`
- Spring Cloud Alibaba Nacos Discovery
- Spring Cloud Alibaba Nacos Config
- OpenFeign
- Spring Cloud LoadBalancer
- Caffeine

## 为什么推荐“单 key + 普通 MQ 异步落库”

这里推荐的 baseline，不是“同步写 MySQL 的单体订单服务”，而是：

- 请求线程只做参数校验、单 Redis key 扣减、发送 MQ
- 订单写库由本服务自己的 consumer 异步完成
- 库存数据库扣减继续由现有 `singularity-stock` 的 MQ consumer 异步完成

这样做有三个好处：

1. 对照更公平

当前优化版本质上也是“Redis 前置 + MQ 异步落库”。如果 baseline 直接改成“请求线程同步写订单表、同步写库存表”，那么测试结果会同时混入数据库事务开销，最后很难说清楚性能差异究竟来自：

- `singularity-core` 的 slot 分流
- 多实例部署
- 还是“同步 DB”与“异步 MQ”之间的架构差别

2. 改动更小

`singularity-stock` 现在已经能够消费 `OrderMessage` 并按 `productId` 扣减库存表。baseline 只要继续发同格式消息，就能复用这一侧能力。

3. baseline 的瓶颈更纯粹

这种方案下，baseline 的主要竞争点会集中在：

- 单个 Redis 热 key
- 单实例 Tomcat/JVM
- 单服务内部串行处理能力

这正是它作为 baseline 应该承担的角色。

## 为什么不推荐继续使用事务消息

当前优化版使用 RocketMQ 事务消息，是为了让“Redis 扣减 + MQ 投递”具备更好的事务语义。

但对 baseline 而言，如果继续保留这套机制，会引入两个问题：

- 为了事务回查，通常还要保留额外状态（Redis 标记或 DB 事务记录），这会让“单点 Redis key”不再纯粹
- 实现复杂度会重新接近当前 `singularity-order`，baseline 会失去“朴素、好懂、易实现”的价值

因此，baseline 推荐使用“普通 MQ + 发送失败补偿回滚 Redis”的方式。

这套方式不是最强一致，但足够适合压测对照。

## 总体架构

```text
Client / Gateway
       |
       v
singularity-order-baseline  (单实例 Spring Boot)
       |
       |-- 1. Redis Lua: 对单个库存 key 做原子扣减
       |
       |-- 2. RocketMQ Producer: 发送 OrderMessage
       |
       |-- 3. 若 MQ 发送失败，则 Redis INCR 补偿
       |
       |-- 4. 返回 orderId 给调用方
       |
       |-- 5. 本服务 Consumer 异步写 order 表
       |
       '-- 6. singularity-stock Consumer 异步扣减 stock 表
```

和当前 `singularity-order` 的关系可以概括为：

| 维度 | 当前 `singularity-order` | `singularity-order-baseline` |
|---|---|---|
| 框架核心 | `singularity-core` | 无 |
| 请求分流 | slot + shard policy | 无 |
| Redis 写热点 | 多 slot key | 单商品单 key |
| 本地缓存 | 有 | 无 |
| 部署方式 | 多实例可扩展 | 单实例 |
| 配置来源 | Nacos | `application.yml` |
| 服务调用 | Feign + Nacos | 直接 HTTP URL |
| 订单落库 | MQ consumer | MQ consumer |
| 库存 DB 扣减 | stock service MQ consumer | 复用 stock service MQ consumer |

## 基线定义

为了保证这个服务真的能作为 baseline，建议把“baseline”定义清楚：

### 1. 单商品压测模式

推荐 baseline 默认只服务一个压测商品。

例如：

- `productId = 1001`
- `redisStockKey = baseline:stock:1001`
- `slotId = baseline`

这样能保证所有请求都打到同一个 Redis 热 key 上。

如果后续要支持多个商品，也应遵守同一条原则：

- 每个商品只有一个库存 key
- 不允许把单个商品拆成多个 bucket/slot

### 2. 无本地状态优化

服务内不能维护以下状态：

- 本地可用 slot 列表
- 售罄 key 的本地缓存
- 热门用户/商品的本地缓存
- 任何“提前短路 Redis”的热点判断

也就是说，每次抢单都必须真正访问 Redis。

### 3. 单实例约束

部署时只启动一个 baseline 实例。

为了避免误用，建议：

- 不注册到 Nacos
- 不放入现有自动扩缩容范围
- 容器命名明确，例如 `singularity-order-baseline-0`

## 模块结构建议

建议新增模块：`singularity-order-baseline`

建议目录结构如下：

```text
singularity-order-baseline/
  pom.xml
  src/main/java/com/lubover/singularity/orderbaseline/
    BaselineOrderApplication.java
    controller/
      OrderController.java
    service/
      OrderService.java
      impl/
        OrderServiceImpl.java
        RedisStockGate.java
    mq/
      OrderMessage.java
      OrderCreatedConsumer.java
    client/
      UserHttpClient.java
    mapper/
      OrderMapper.java
    entity/
      Order.java
    dto/
      ApiResponse.java
  src/main/resources/
    application.yml
    mapper/OrderMapper.xml
    db/migration/V1__Create_Order_Tables.sql
```

## 依赖建议

`pom.xml` 建议只保留这些依赖：

- `spring-boot-starter-web`
- `spring-boot-starter-validation`
- `spring-boot-starter-data-redis`
- `mybatis-spring-boot-starter`
- `mysql-connector-j`
- `flyway-core`
- `flyway-mysql`
- `rocketmq-spring-boot-starter`
- `spring-boot-starter-actuator`
- `micrometer-registry-prometheus`

明确不要引入：

- `com.lubover:singularity-core`
- `spring-cloud-starter-openfeign`
- `spring-cloud-starter-loadbalancer`
- `spring-cloud-starter-bootstrap`
- `spring-cloud-starter-alibaba-nacos-*`
- `caffeine`

## 接口设计

为了方便对照压测，建议尽量复用现有 `singularity-order` 的接口。

### 1. 抢单接口

`POST /api/order/snag`

请求体建议：

```json
{
  "userId": "1",
  "productId": "1001"
}
```

返回成功：

```json
{
  "success": true,
  "data": {
    "orderId": "uuid"
  }
}
```

返回失败：

```json
{
  "success": false,
  "message": "sold out"
}
```

实现建议：

- `userId` 必填
- `productId` 必填
- 若服务处于“单商品基线模式”，则 `productId` 必须与配置中的 benchmark product 相等，否则直接失败

### 2. 查询订单

`GET /api/order/{orderId}`

与当前服务保持一致。

### 3. 更新订单状态

`PUT /api/order/{orderId}/status`

与当前服务保持一致。

### 4. 支付订单

`POST /api/order/{orderId}/pay`

保留该接口，但实现方式改为：

- 用 MyBatis 查本地订单表
- 通过 `RestClient` 调用用户服务的 HTTP 地址，例如 `http://localhost:8090/api/user/{id}/deduct`
- 成功后更新状态为 `PAID`

### 5. 分页查询

`GET /api/order/list`

与当前服务保持一致。

## 核心写链路设计

### 抢单主流程

`OrderServiceImpl.snagOrder(userId, productId)` 推荐按下面的步骤实现：

1. 校验参数
2. 根据 `productId` 解析唯一 Redis 库存 key
3. 执行 Lua 脚本，对 Redis key 原子扣减
4. 如果 Redis 返回库存不足，直接失败返回
5. 生成 `orderId`
6. 构造 `OrderMessage`
7. 发送普通 RocketMQ 消息
8. 如果发送失败，对 Redis 做一次 `INCRBY 1` 补偿
9. 发送成功则直接返回 `orderId`

推荐伪代码如下：

```java
public Result snagOrder(String userId, String productId) {
    validate(userId, productId);

    String stockKey = stockKeyResolver.resolve(productId);
    Long remaining = redisStockGate.tryDecrement(stockKey, 1L);
    if (remaining == null || remaining < 0) {
        return Result.fail("sold out");
    }

    String orderId = UUID.randomUUID().toString();
    OrderMessage message = new OrderMessage(orderId, userId, productId, "baseline", LocalDateTime.now());

    try {
        rocketMQTemplate.convertAndSend(orderTopic, message);
        return Result.success(orderId);
    } catch (Exception ex) {
        redisStockGate.compensate(stockKey, 1L);
        return Result.fail("mq send failed");
    }
}
```

### Redis 原子扣减

不要使用“先 GET 再 DECR”的两步逻辑，必须使用 Lua 或单条原子命令。

推荐 Lua：

```lua
local current = redis.call('GET', KEYS[1])
if not current then
    return -2
end

local stock = tonumber(current)
local amount = tonumber(ARGV[1])

if stock < amount then
    return -1
end

return redis.call('DECRBY', KEYS[1], amount)
```

返回值约定：

- `>= 0`：扣减成功，值为扣减后的剩余库存
- `-1`：库存不足
- `-2`：key 不存在，视作未预热或配置错误

### 为什么必须保留 Lua

虽然 baseline 要朴素，但不能写成错误实现。

如果使用：

- `GET stock`
- 判断 `> 0`
- 再 `DECR`

那么在高并发下会产生明显超卖，这会让 baseline 失去可信度。

baseline 可以“慢”，但不能“错”。

## MQ 设计

### 消息体

建议直接复用当前订单消息结构，确保和 `singularity-stock` 兼容：

- `orderId`
- `productId`
- `userId`
- `slotId`
- `createTime`

其中：

- `slotId` 固定写为 `baseline`

这样做的好处是：

- baseline 自己的 consumer 可以直接复用现在的落库逻辑
- `singularity-stock` 的 `OrderTopicConsumer` 只关心 `orderId/productId`，可以继续工作

### topic 建议

推荐两种模式二选一：

1. 隔离压测环境模式

- baseline 环境里只有 baseline order 服务在发消息
- 可以继续复用现有 `order-topic`

2. 更干净的独立 topic 模式

- baseline 使用 `baseline-order-topic`
- baseline order consumer 和 stock consumer 都订阅这个 topic

如果你打算让 baseline 和优化版在同一套 RocketMQ 环境中反复切换，推荐用独立 topic，避免消息串流。

## 订单落库设计

### 设计原则

- 请求线程不写订单表
- 由 MQ consumer 异步写库
- `order_id` 主键保证天然幂等

### consumer 行为

`OrderCreatedConsumer` 收到消息后：

1. 校验消息字段完整性
2. 构造 `Order` 实体
3. 插入 `order` 表
4. 如果主键冲突，则忽略重复消息

这部分几乎可以直接复用当前 `singularity-order` 中的 `OrderConsumerService`。

### 为什么推荐继续异步落单

原因很简单：

- 当前优化版就是异步落单
- baseline 继续异步落单，才能把差异尽量收敛到“单 key vs 多 slot key、单机 vs 多实例、无缓存 vs 有缓存”

## 数据模型设计

建议 baseline 使用独立数据库：`singularity_order_baseline`

原因：

- 不污染当前 `singularity_order`
- 压测前可以直接 truncate 或 drop/recreate
- 避免优化版和 baseline 共用同一张订单表

### 订单表建议

为了减少 DTO / Mapper 改动，建议继续使用当前订单表结构：

```sql
CREATE TABLE IF NOT EXISTS `order` (
  `order_id` VARCHAR(64) NOT NULL PRIMARY KEY,
  `user_id` VARCHAR(64) NOT NULL,
  `slot_id` VARCHAR(64) NOT NULL,
  `product_id` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'CREATED',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_product_id` (`product_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_create_time` (`create_time`)
);
```

说明：

- `slot_id` 虽然在 baseline 中没有真实含义，但保留它能显著降低兼容成本
- `slot_id` 固定写成 `baseline`

## 配置设计

`application.yml` 建议采用纯本地静态配置。

示例：

```yaml
server:
  port: 8085

spring:
  application:
    name: singularity-order-baseline
  datasource:
    url: jdbc:mysql://localhost:3306/singularity_order_baseline?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
  data:
    redis:
      host: localhost
      port: 6379
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

mybatis:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true

rocketmq:
  name-server: localhost:9876

baseline:
  benchmark:
    product-id: 1001
    redis-stock-key: baseline:stock:1001
    slot-id: baseline
  mq:
    topic: baseline-order-topic
    consumer-group: baseline-order-consumer-group
  user-service:
    base-url: http://localhost:8090

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

## 关键实现细节

### 1. `productId` 的处理

如果 baseline 主要用于单商品秒杀压测，推荐服务在启动时就固定一个商品：

- 请求体里即便带 `productId`
- 服务也只接受与 `baseline.benchmark.product-id` 相同的值

这样可以防止压测脚本误把流量分散到多个 key 上。

### 2. 不做重复下单限制

当前 `singularity-order` 并没有明显的“同用户同商品只能下一单”约束。

因此 baseline 为了保持可比性，也不建议额外引入：

- Redis 去重 set
- DB 唯一索引 `(user_id, product_id)`
- 本地幂等缓存

否则会引入新的额外开销或新行为，破坏对照。

### 3. MQ 发送失败补偿

baseline 的核心补偿逻辑只有一条：

- Redis 扣减成功
- 但 MQ 发送失败
- 则立刻对 Redis 做 `INCRBY 1`

这不是严格事务，但实现简单，符合 baseline 定位。

### 4. 服务进程崩溃窗口

存在一个已知窗口：

- Redis 已扣减
- 服务在发送 MQ 前崩溃

这会造成 Redis 与下游 DB 短暂不一致。

对于 baseline，有两种处理策略：

1. 压测模式接受该风险

- 只在受控环境跑压测
- 每轮压测前重置 Redis 和 DB
- 不把 baseline 当生产系统

2. 增强模式引入本地 outbox 表

- 请求线程先把待发送事件写入本地 DB
- 后台线程轮询 outbox 发 MQ

但这样会额外引入一次 DB 写入，压测基线会变“更重”。

基于“baseline 要尽量朴素”的原则，推荐先采用第 1 种。

### 5. `slotId` 固定值

建议统一写死：

- `slotId = baseline`

这样有三个好处：

- 兼容现有订单表结构
- 兼容现有消息结构
- 查询结果里仍然保留 `slotId` 字段，前端和脚本不用改太多

## 与现有系统的集成方式

### 方案 A：完全独立压测入口

压测脚本直接请求：

- `http://localhost:8085/api/order/snag`

优点：

- 不动网关
- 不影响当前线上式演示链路

缺点：

- 如果前端要切换，需要单独改 base URL

### 方案 B：通过网关切换路由

在测试环境里把 `/api/order/**` 临时指向 baseline 服务。

优点：

- 前端和脚本完全无感

缺点：

- 需要改 gateway 配置
- 容易和原 `singularity-order` 混淆

对于第一次实现，推荐先走方案 A。

## 实现步骤建议

### Step 1：创建模块骨架

新增 `singularity-order-baseline` 模块，并在根 `pom.xml` 中注册。

产出物：

- `singularity-order-baseline/pom.xml`
- `BaselineOrderApplication.java`
- `application.yml`

### Step 2：迁移订单表和 Mapper

从现有 `singularity-order` 复制最小必要内容：

- `Order.java`
- `OrderMapper.java`
- `OrderMapper.xml`
- `V1__Create_Order_Tables.sql`

只做极少修改：

- 包名调整
- 数据库名改为 `singularity_order_baseline`

### Step 3：实现单 key Redis 库存门闸

新增 `RedisStockGate`，只负责两件事：

- `tryDecrement(stockKey, quantity)`
- `compensate(stockKey, quantity)`

不要把业务逻辑、MQ、Mapper 混进去。

### Step 4：实现抢单服务

新增 `OrderServiceImpl`，内部只编排：

- 参数校验
- 调 Redis
- 发 MQ
- 返回结果

### Step 5：实现订单 MQ consumer

新增 `OrderCreatedConsumer`，消费消息并写订单表。

要求：

- 幂等
- 日志清晰
- 不做额外本地缓存

### Step 6：实现支付接口

新增 `UserHttpClient`，用 Spring `RestClient` 调用户服务。

不要接入 Feign 和 Nacos。

### Step 7：补齐监控与压测准备

加入：

- `/actuator/prometheus`
- 必要日志
- 压测前的 Redis/DB 重置文档

## 验收方式

### 基础功能验收

1. 构建模块

```bash
mvn -pl singularity-order-baseline package
```

2. 预热 Redis 库存 key

```bash
redis-cli SET baseline:stock:1001 1000
```

3. 发起一次抢单

```bash
curl -X POST http://localhost:8085/api/order/snag \
  -H "Content-Type: application/json" \
  -d '{"userId":"1","productId":"1001"}'
```

4. 校验结果

- 接口返回 `success=true`
- Redis 库存从 `1000` 变成 `999`
- baseline 订单表新增一条订单
- `singularity-stock` 对应商品库存被异步扣减

### 压测对照验收

压测时建议保证以下条件一致：

- 同一台机器
- 同一个 Redis
- 同一个 MySQL
- 同一个 RocketMQ
- 同一个商品
- 相同初始库存
- 相同并发参数
- 相同压测时长

比较指标建议至少包括：

- 总吞吐量（TPS/QPS）
- 成功请求数
- 失败请求数
- p50 / p95 / p99 延迟
- Redis CPU / ops
- JVM CPU / heap

## 测试数据重置建议

为了保证 baseline 和优化版的测试可重复，建议每轮压测前执行统一 reset：

1. 清空 baseline 订单库
2. 重置 stock 库中目标商品库存
3. 重置 Redis 库存 key
4. 清理 RocketMQ 残留消息或使用新 topic

建议压测生命周期如下：

1. reset 数据
2. 预热 JVM 1 到 2 分钟
3. 正式压测
4. 导出指标
5. 校验一致性

## 风险与取舍

### 风险 1：Redis 与 MQ 之间存在崩溃窗口

这是 baseline 方案的最大一致性风险。

结论：

- 对 benchmark 环境可接受
- 若未来要做更严格实验，可升级到 outbox 变体

### 风险 2：如果仍复用 `order-topic`，可能与现有服务串消息

结论：

- 独立环境可接受
- 混合环境下应使用独立 topic

### 风险 3：如果允许多个商品并发压测，会悄悄变成“多 key”测试

结论：

- baseline 默认必须固定单商品
- 服务端要校验 `productId`

## 可选增强

以下增强不是第一版必须实现，但未来可以按需补：

### 1. Outbox 增强版

为了解决“Redis 扣减成功但进程崩溃，MQ 尚未发送”的问题，可以加本地 `order_outbox` 表。

但这会增加一次 DB 写入，弱化 baseline 的“轻量抢单入口”特征。

### 2. 管理接口

可增加仅用于测试的管理接口，例如：

- `POST /api/order/admin/reset-stock`
- `GET /api/order/admin/health-summary`

但这些接口不要参与正式压测。

### 3. 自定义业务指标

如果需要更细粒度对照，可以增加 Micrometer 指标：

- `baseline_order_snag_total`
- `baseline_order_snag_success_total`
- `baseline_order_sold_out_total`
- `baseline_order_mq_send_fail_total`

第一版不是必须，因为 Actuator 默认 HTTP 指标已经足够做第一轮对照。

## 最终建议

综合当前项目结构和 baseline 的目标，最推荐的实现方式是：

- 新增独立模块 `singularity-order-baseline`
- 只用基础 Spring Boot，不接入 Spring Cloud 和 `singularity-core`
- 默认固定一个 benchmark 商品
- 所有抢单请求竞争同一个 Redis 库存 key
- 请求线程只做“Redis 原子扣减 + 普通 MQ 发送 + 失败补偿”
- 订单仍异步落库
- 库存 DB 仍复用现有 `singularity-stock` 的 MQ 消费逻辑
- 对外尽量保持 `/api/order/*` 接口兼容

这样做能够得到一个足够朴素、足够稳定、且与现有优化版具备明确可比性的 baseline。

它不是最强一致、也不是最通用的方案，但非常适合你当前这个项目的核心目标：

- 给 `singularity-core` 的写链路优化提供一个清晰可信的对照组。
