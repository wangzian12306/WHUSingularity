问题本质

当前 snagOrder 的问题不是"实现得不好"，而是业务逻辑太薄，导致框架的结构性优势无处施展：

当前 snagOrder 的计算剖面：
CPU: < 0.1ms（字符串拼接 + HashMap 存取）
Redis: ~0.3ms（单 key Lua：GET + DECRBY）
MQ: ~5ms（事务消息同步等待）

    CPU/IO 比 ≈ 0.02  ← 纯 I/O 密集型

这造成两个后果：

1. Redis 分 key 优势不显著：单 key 操作的绝对时间太短（0.3ms），即使不分片，Redis 单 key 也能撑住 3-5 万 QPS。baseline 和 order 的差距只是一个"还没触到的天花板"
2. Scaler 扩容无意义：加实例只是增加了等待 Redis/MQ 的线程数，不增加计算吞吐——IO 密集型服务的水平扩展解决不了远端资源瓶颈

要让框架有价值，必须把业务逻辑从 I/O 密集型改造成 CPU 密集型。 即：让单请求的计算量显著大于 I/O 等待量，使水平扩展真正提升总吞吐。

---

场景设计：「智能限购秒杀」Smart Restricted-Quantity Seckill

业务背景：一次限量奢侈品首发，涉及多 SKU、用户分级限购、实时风控、动态捆绑折扣。

完整请求剖面（单次 snagOrder）

┌─────────────────────────────────────────────────────────────────┐
│ Interceptor Chain │
├─────────────────────────────────────────────────────────────────┤
│ 1. RateLimitInterceptor CPU: 0.1ms Redis: 0.2ms │
│ └─ Sliding window per-slot 限流（Redis INCR + EXPIRE） │
│ │
│ 2. UserProfileInterceptor CPU: 3-8ms Redis: 0.5ms │
│ └─ 多维度用户资质计算： │
│ · 会员等级解析（消费总额 → 分段函数 → 等级枚举） │
│ · 历史行为评分（Redis 取近 30 天购买记录 → 本地加权计算） │
│ · 设备指纹风险分（header 熵值 + 请求频率分析） │
│ │
│ 3. InventoryRuleInterceptor CPU: 2-5ms Redis: 0.3ms │
│ └─ 库存规则匹配： │
│ · SKU 组合约束检查（A 款 + B 款不可同时购买） │
│ · 用户限购额度 vs slot 剩余库存的分配策略 │
│ │
│ 4. PricingInterceptor CPU: 3-6ms Redis: 0.2ms │
│ └─ 动态定价引擎： │
│ · 捆绑折扣计算（DP/贪心求解最优折扣组合） │
│ · 实时优惠券匹配（用户持有券 ∩ 商品适用券） │
│ · 最终价格 = base_price - bundle_discount - coupon │
│ │
│ 5. Business Handler (终端) CPU: 1-2ms Redis: 1-2ms │
│ └─ 原子库存扣减（复杂 Lua）+ MQ 事务消息 │
│ Lua: 多 key 原子操作（stock + user_limit + campaign_counter）│
├─────────────────────────────────────────────────────────────────┤
│ 合计： CPU: 9-22ms Redis: 2-3ms │
│ MQ: ~5ms │
│ CPU/IO 比 ≈ 3-5 ← CPU 密集型 │
└─────────────────────────────────────────────────────────────────┘

---

当前订单风控拦截器实现：FraudDetectionInterceptor

`singularity-order` 当前已经接入 `FraudDetectionInterceptor`，它是 `singularity-core` 分配链路中的一个 `Interceptor`，执行位置在：

Registry 获取可用 slot → ShardPolicy 选择 slot → FraudDetectionInterceptor 风控识别 → handler 执行 Redis 扣库存与 RocketMQ 事务消息

它只覆盖走 `Allocator.allocate(actor)` 的抢单入口。也就是说，标准 `snagOrder(actor)` 会经过风控；如果业务方法绕过 allocator、直接按 productId 选择 slot 并执行事务，则不会触发该拦截器。

### 风控记录方式

拦截器按 `actorId` 维护一个本地行为窗口，底层使用 Caffeine：

- key：`actorId`
- value：`BehaviorWindow`
- 过期策略：访问后 `windowSeconds * 2` 秒过期
- 最大容量：50000 个 actor 行为窗口

每个 `BehaviorWindow` 记录四类状态：

- `windowStartMs`：当前行为窗口的开始时间
- `requestCount`：该 actor 在当前窗口内已经发起的抢单次数
- `lastSlotId`：该 actor 上一次命中的 slot
- `slotSwitchCount`：该 actor 在窗口内发生 slot 切换的次数

每次请求进入风控后，系统会先取出当前 `actorId` 对应的窗口；如果不存在，则创建一个新的窗口。随后执行一次 `recordAndScore(now, slotId, featureVector)`：

1. 如果当前时间已经超过窗口长度，则重置窗口开始时间、请求次数、slot 切换次数和上一次 slot。
2. 请求次数 `requestCount + 1`。
3. 如果 `lastSlotId` 不为空且不同于当前 `slotId`，说明该 actor 在短时间内切换了库存槽位，`slotSwitchCount + 1`。
4. 更新 `lastSlotId = 当前 slotId`。
5. 计算风险分。

这个记录方式的重点是“以用户维度观察短时间行为”，而不是以订单维度或商品维度单点判断。它能识别的不是某一次请求是否异常，而是同一 actor 在极短时间内是否呈现出脚本化模式。

### 行为指纹生成

风控还会为每次请求生成一个行为特征向量。输入特征包括：

- `id:actorId`：身份特征
- `tw:actorId:timeBucket`：时间窗口行为特征
- `sp:actorId:slotId`：槽位偏好特征
- `cs:actorId:slotId:timeBucket`：跨槽位跳变特征

其中 `timeBucket = nowMs / (windowSeconds * 1000)`，也就是按照配置的行为窗口长度分桶。每个特征会进行多轮 SHA-256：

```text
seed = "singularity-fraud-v2"
input = feature
repeat hashRounds times:
    input = SHA256(seed + input)
featureValue = firstByte(input) / 255.0
```

最终得到一个 4 维向量。当前实时拦截评分主要依赖频率和槽位跳变，特征向量用于保留行为指纹与制造可横向扩展的 CPU 型业务负载；后续如果需要增强识别能力，可以把这些向量接入更复杂的模型或审计系统。

### 风险识别规则

当前实时风险分由两部分组成：

```text
freqScore   = min(1.0, requestCount / maxRequestsPerWindow)
switchScore = requestCount > 1
              ? min(1.0, slotSwitchCount / (requestCount - 1))
              : 0.0
riskScore   = 0.55 * freqScore + 0.45 * switchScore
```

这表示系统主要识别两类脚本行为：

- 高频请求：同一 actor 在短窗口内请求次数接近或超过阈值，说明它不像人工点击。
- 槽位跳变：同一 actor 在短窗口内频繁命中不同 slot，说明它可能在遍历库存桶或使用脚本重试。

风险等级按阈值划分：

- `LOW`：`riskScore < 0.5`
- `MEDIUM`：`0.5 <= riskScore < riskThreshold`
- `HIGH`：`riskScore >= riskThreshold`

默认配置下，`riskThreshold = 0.85`，`windowSeconds = 5`，`maxRequestsPerWindow = 100`，`hashRounds = 8`。

### 拦截与放行流程

风控识别完成后，会把结果写入 `Context`：

- `fraud.riskScore`
- `fraud.riskLevel`

如果 `riskScore >= riskThreshold`，拦截器直接设置失败结果：

```text
风控拦截：行为异常，抢单被拒绝
```

并且不再调用 `context.next()`。这意味着请求不会进入后续 handler，也不会执行 Redis 原子减库存、RocketMQ 半消息发送、订单消息投递和 MySQL 异步落库。

如果 `riskScore < riskThreshold`，拦截器调用 `context.next()` 放行。后续订单 handler 会从 `Context` 读取 `fraud.riskScore`，写入 `OrderMessage.riskScore`，随 `order-topic` 消息一起传播，便于后续消费端、日志或审计链路保留本次抢单的风险信息。

### 设计价值

这套做法有三个直接价值：

1. 在热路径前置拦截明显异常的抢单行为，避免恶意请求继续消耗 Redis、MQ 和数据库链路资源。
2. 用本地窗口记录用户短时间行为，识别脚本高频请求和跨 slot 遍历，而不是只做单请求静态校验。
3. 通过多轮特征哈希引入真实 CPU 计算，使订单服务在高并发下能够形成可被 scaler 捕捉的 CPU 型负载；扩容新实例后，风控计算可以随实例数横向分摊。

---

关键：CPU 密集型操作的具体设计

UserProfileInterceptor — 多维度资质评分（纯 CPU）

// 伪代码
int calculateQualificationScore(UserProfile profile, SlotMetadata meta) {
// 1. 会员等级分（查表 + 分段函数）
int tierScore = switch (profile.membershipLevel()) {
case DIAMOND -> 100;
case GOLD -> profile.totalSpent() > 50000 ? 85 : 70;
case SILVER -> profile.totalSpent() > 10000 ? 55 : 40;
default -> profile.purchaseCount() > 5 ? 20 : 0;
};

      // 2. 行为风险分（本地计算，涉及多维度加权）
      double requestEntropy = shannonEntropy(profile.recentHeaders());  // CPU: 字符串处理
      double frequencyScore = 1.0 / (1.0 + Math.exp(-profile.avgRequestInterval()));
      int riskPenalty = (int) (requestEntropy * 30 + frequencyScore * 20);

      // 3. 历史履约率（Redis 批量取最近 N 笔订单 → 本地聚合计算）
      List<OrderSummary> history = fetchRecentOrders(profile.userId());  // Redis pipeline
      double fulfillRate = history.stream()
              .filter(o -> o.status() == PAID)
              .count() / (double) Math.max(history.size(), 1);

      return Math.max(0, tierScore - riskPenalty + (int)(fulfillRate * 30));

}

PricingInterceptor — 最优折扣组合求解（纯 CPU）

// 伪代码：0/1 背包变种 → 贪心 + DP 混合
DiscountPlan computeOptimalDiscount(List<Coupon> coupons, List<Bundle> bundles,
List<String> cartSkus, BigDecimal baseTotal) {
// 1. 过滤适用券（O(n) 条件匹配）
List<Coupon> applicable = coupons.stream()
.filter(c -> c.isApplicable(cartSkus, baseTotal))
.toList();

      // 2. 捆绑折扣组合枚举（O(2^m)，但 m ≤ 5，可控）
      List<BundlePlan> bundlePlans = enumerateBundleCombinations(bundles, cartSkus);

      // 3. 对每种 bundle plan，贪心匹配最优 coupon → 取最大值
      return bundlePlans.stream()
              .map(bp -> bp.applyCoupons(applicable))  // 每种组合独立计算
              .max(Comparator.comparing(DiscountPlan::totalDiscount));

}

---

定量对比：新场景下的吞吐天花板

假设单实例配置：Tomcat 200 线程，Redis 连接池 50，RocketMQ producer 20 并发。

┌──────────────────────┬───────────────────────────┬──────────────────────────────┐
│ 指标 │ Baseline（单 Redis key） │ Normal Order（N=10 buckets） │
├──────────────────────┼───────────────────────────┼──────────────────────────────┤
│ 单请求 CPU │ 0.1ms │ 15ms │
├──────────────────────┼───────────────────────────┼──────────────────────────────┤
│ 单请求 Redis │ 0.3ms │ 2.5ms │
├──────────────────────┼───────────────────────────┼──────────────────────────────┤
│ 单请求 MQ │ 0ms (async) │ 5ms │
├──────────────────────┼───────────────────────────┼──────────────────────────────┤
│ CPU 瓶颈（单实例） │ 200/0.0001 = 2,000,000 │ 200/0.015 = 13,333 req/s │
├──────────────────────┼───────────────────────────┼──────────────────────────────┤
│ Redis 瓶颈（单实例） │ 50/0.0003 = 166,667 req/s │ 50/0.0025 = 20,000 req/s │
├──────────────────────┼───────────────────────────┼──────────────────────────────┤
│ 实际天花板（单实例） │ min(2M, 166K) = 166K │ min(13.3K, 20K, MQ) = 13K │
└──────────────────────┴───────────────────────────┴──────────────────────────────┘

单实例 baseline 仍然更高——因为它的 Redis 操作太简单（0.3ms），单 key 天花板远未触及。

3 实例的情况：

┌────────────┬──────────────────────────────────────┬────────────────────────────────┐
│ 指标 │ 3×Baseline │ 3×Normal Order（N=10 buckets） │
├────────────┼──────────────────────────────────────┼────────────────────────────────┤
│ CPU 瓶颈 │ 3 × 2M = 6M req/s │ 3 × 13.3K = 40K req/s │
├────────────┼──────────────────────────────────────┼────────────────────────────────┤
│ Redis 瓶颈 │ 1 key × 166K = 166K req/s ← 硬天花板 │ 10 keys × 20K = 200K req/s │
├────────────┼──────────────────────────────────────┼────────────────────────────────┤
│ 实际天花板 │ 166K req/s（Redis 单 key 瓶颈） │ 40K req/s（CPU 瓶颈） │
└────────────┴──────────────────────────────────────┴────────────────────────────────┘

等等——3×baseline 的 Redis 天花板是 166K，而 3×normal 的 CPU 天花板是 40K。Baseline 还是赢了？

问题出在哪？ Baseline 的 Redis 操作（0.3ms）比 Normal 的 CPU 操作（15ms）快 50 倍。即使 Normal 有分片，CPU 计算本身成了新的瓶颈。

修正：关键不在绝对吞吐量，而在可扩展性曲线的斜率。

吞吐量 vs 实例数：

Baseline:
1 实例: 166K (Redis 单 key 天花板以下)
3 实例: 166K (Redis 单 key 天花板 — 加实例不再增长！)
10 实例: 166K (仍然卡在同一个 Redis key)

    曲线: ▁▁▁▁▁▁▁▁▁▁ (水平线，加实例无收益)

Normal Order:
1 实例: 13K (CPU 瓶颈)
3 实例: 40K (3× CPU = 3× 吞吐)
10 实例: 133K (10× CPU = 10× 吞吐，直到触及 Redis 天花板 200K)

    曲线: ▁▃▅▇█ (线性增长，直到 Redis 天花板)

这才是核心故事：

- Baseline 在低并发下吞吐更高（没有事务消息和 CPU 开销），但它不可水平扩展——Redis 单 key 是固定的串行化点
- Normal 在单实例下吞吐较低，但线性可扩展——每加一个实例，CPU 能力翻倍，吞吐翻倍，直到触及 Redis 分片天花板
- 两条曲线必然会交叉。交叉点取决于 N（bucket 数）、CPU 复杂度、Redis 单 key 上限

          吞吐
           ^
           |         Baseline ─ ─ ─ ─ ─ ─ ─ (天花板)
           |       /
           |      /
           |     /  Normal Order (线性增长)
           |    /
           |   /
           |  /  ← 交叉点（scaler 价值所在）
           | /
           |/
           +----------------------> 实例数
              1    3    5    10

Scaler 的价值就在这个交叉点之后。 当负载上升，scaler 检测到 CPU 利用率 > 阈值 → 启动新实例 → 注册新 slot → 吞吐线性增长。Baseline 的 scaler
即使启动更多实例也毫无收益——所有实例都在 Redis 单 key 上排队。

---

要让交叉点尽早出现，需要调三个参数

┌──────────────────┬──────────────────────────────────────────┬──────────────────────────────────────┐
│ 参数 │ 作用 │ 对交叉点的影响 │
├──────────────────┼──────────────────────────────────────────┼──────────────────────────────────────┤
│ Bucket 数 N │ 提高 Normal 的 Redis 天花板 │ N 越大，Normal 的线性增长空间越大 │
├──────────────────┼──────────────────────────────────────────┼──────────────────────────────────────┤
│ CPU 计算复杂度 │ 让 CPU 成为 Normal 的主导瓶颈（而非 MQ） │ CPU 占比越高，加实例的收益越纯粹 │
├──────────────────┼──────────────────────────────────────────┼──────────────────────────────────────┤
│ Redis Lua 复杂度 │ 压低 baseline 单 key 的实际天花板 │ 单 key Lua 越重，baseline 天花板越低 │
└──────────────────┴──────────────────────────────────────────┴──────────────────────────────────────┘

其中最关键的是 Redis Lua 复杂度——当前 baseline 的 Lua 太简单（GET + DECRBY，0.3ms），单 key 天花板非常高（15 万+ QPS），交叉点发生在实例数很大时。让 Redis
操作变重（多 key 原子操作、HSET 写入、counter 级联更新），baseline 的单 key 天花板就会急剧下降。

新场景中的 Lua 脚本（多 key 原子扣减 + 用户限购检查 + 活动计数器更新）单次 ~1.5ms，单 key 天花板 ~3 万 QPS。这意味着：

- 3×baseline 天花板 = 3 万（Redis 单 key 瓶颈）
- 3×normal = 3 × CPU 天花板 = 4 万（CPU 瓶颈，10 buckets）
- 交叉点在 2-3 个实例时就出现了

---

新场景如何体现框架每一项能力

┌────────────────────┬──────────────────────────────────────┬──────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ 框架能力 │ 旧场景（snagOrder） │ 新场景（智能限购秒杀） │
├────────────────────┼──────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ ShardPolicy │ Hash 路由，但每个 slot │ 不同 slot 可以承载不同 SKU 组合、不同的 discount 规则集。路由策略有了语义 │
│ │ 做的事完全一样 │ │
├────────────────────┼──────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Interceptor 链 │ 只有 LoggingInterceptor（纯观测） │ 4 个拦截器各自执行独立且有 CPU 开销的业务逻辑，链的 around 语义真正发挥作用 │
├────────────────────┼──────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Slot 元数据 │ {redisStockKey, productId} 两个字段 │ {redisStockKey, productId, skuConfig, discountRules, tierLimits, riskThreshold} —— slot │
│ │ │ 是完整的业务配置单元 │
├────────────────────┼──────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Registry.markEmpty │ 仅标记库存耗尽 │ 可以标记"库存耗尽"或"风控熔断"——slot 级别的断路 │
├────────────────────┼──────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Dynamic slot 注册 │ 技术上支持但无实际使用 │ Scaler 启动新实例 → 动态注册 slot → 库存重分配。slot 是水平扩展的原语 │
├────────────────────┼──────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Redis key 分片 │ 1 个 key 做 DECR │ 每个 slot 持有多把 Redis key（stock + user_counter + campaign_counter），Lua 原子操作跨 key │
├────────────────────┼──────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Scaler │ CPU 使用率始终 < 5%，无触发理由 │ CPU 密集 → CPU 使用率是真实的扩容信号 → scaler 有决策依据 │
└────────────────────┴──────────────────────────────────────┴──────────────────────────────────────────────────────────────────────────────────────────────────────┘

---

建议的实现路径

第一步（纯配置，即刻见效）：给 slot 加元数据

singularity.order.slots: - id: "slot-1"
redis-key: "stock:slot-1"
product-id: "PROD_001"
sku-ids: "SKU_A,SKU_B"
discount-rules: "bundle_3x_10off,new_user_5off"
tier-limits: "DIAMOND:5,GOLD:3,SILVER:2,NORMAL:1"
risk-threshold: "0.7"

第二步（新增 Interceptor）：按优先级实现

- UserProfileInterceptor — 最体现 CPU 计算
- RateLimitInterceptor — 最体现 Redis per-slot 操作
- PricingInterceptor — 最体现复杂业务逻辑
- InventoryRuleInterceptor — 最体现 slot 间差异化

第三步（改造 Lua 脚本）：从单 key DECR 变为多 key 原子操作，把用户限购校验和活动计数更新放进 Lua

第四步（验证 scaler 闭环）：压测 → CPU 升高 → scaler 检测 → 启动新实例 → 注册新 slot → 吞吐线性增长

---

一句话总结

当前 snagOrder 是 I/O 密集型（计算几乎为零，等待几乎全部），把框架的"分片计算 + 水平扩展"基因完全浪费了。新场景把 CPU/IO 比从 0.02 拉到
3-5，使每个实例的计算能力成为瓶颈而非摆设，Redis 分 key 和 Scaler 扩容才第一次有了存在的意义。
