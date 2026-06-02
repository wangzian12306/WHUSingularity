# 当前系统最薄弱环节为 order 服务 — 证据说明

**项目**：WHUSingularity  
**结论**：在 Gateway Fraud Ramp 秒杀压测路径下，**`singularity-order` 是当前链路中最应优先扩容的薄弱点**；scaler 已据此自动扩缩容，观测数据与架构设计一致。  
**关联报告**：[gateway-fraud-ramp-test-report-2026-05-24.md](./gateway-fraud-ramp-test-report-2026-05-24.md)  
**报告日期**：2026-05-24  

---

## 1. 结论摘要（给老师的一页话）

| 问题 | 答案 |
|------|------|
| 最薄弱的一环是谁？ | **`singularity-order`**（可水平扩容，且 scaler 已监控） |
| 依据是什么？ | ① 压测时 order 聚合 CPU 持续 ≥70% 并触发扩容；② 5 副本顶满后 CPU 仍 ~100%、尾延迟随 RPS 上升；③ 下游 Redis/MQ/stock 未饱和；④ CPU 热点在 order 内 `FraudDetectionInterceptor` |
| 为什么不是 stock / Redis？ | 同步抢单路径不经过 stock HTTP；Redis 压测 CPU ~18%，延迟 med ~3ms |
| 为什么不是 gateway？ | gateway 单实例 CPU 也很高（148～170%），但 **compose 固定单实例、scaler 不扩**；在「可扩缩容的薄弱点」语境下，**order 是已验证且已接入 scaler 的主瓶颈** |

---

## 2. 压测路径与观测范围

### 2.1 同步热路径（本次证据均基于此）

```
k6
 → singularity-gateway:8080
 → /api/order/snag  { "userId" }          # 无 productId，走 allocate 链
 → singularity-order（Nacos LB 多副本）
      → FraudDetectionInterceptor（SHA-256 × hash-rounds）
      → allocate + handler
      → Redis Lua 扣桶库存 + RocketMQ 半消息
 → （异步，不在同步延迟内）order-topic → stock MySQL 落库
```

**脚本**：`tests/order-stress-test/k6-snag-gateway-fraud-ramp.js`  
**编排**：`tests/order-stress-test/run-k6-order-gateway-fraud-ramp.ps1`  
**环境**：order 副本由 `singularity-scaler-0` 在 **1～5** 间调度；`hash-rounds=8`；Redis 桶预热约 1M（`tests/order-stress-test/refill-stock-buckets.ps1`）。

### 2.2 为何此路径能代表「order 是瓶颈」

该路径刻意走 **allocate + 风控**，是 order 服务 **CPU 最重** 的业务分支；若此路径下 order 先饱和，则更能说明 order 是薄弱点，而非下游中间件。

---

## 3. 证据链

### 证据 A：Scaler 仅因 order CPU 升高而扩容

Scaler 配置（`singularity-scaler/src/main/resources/application.yml`）对 order 的扩容阈值为 **docker CPU ≥ 0.70** 或 **CPU 暴增（surge）**；stock / product / user 虽在监控列表中，但 **本轮压测未触发其扩容**。

#### 轮次 B（500 → 1000 → 1500 RPS × 120s）

| 时间 (UTC) | 观测 | 动作 |
|------------|------|------|
| 02:45:23 | order docker CPU **86%**，3 副本 | **3 → 4**（创建 order-6） |
| 02:48:08 | CPU 42% → **100%** 暴增 | **4 → 5**（创建 order-7） |
| 02:50:38 | QPS ≈ **1500**，5 副本，CPU **74%** | 维持 5 副本 |
| 02:51:22 | 压测结束 QPS 回落 | **5 → 4**（删除 order-7） |

**推论**：负载上升 → **order CPU 超阈** → scaler 扩 order；负载下降 → 缩 order。**因果链直接指向 order 为被扩容对象。**

#### 轮次 C（1500 → 2000 → 2500 RPS × 120s）

| 时间 (UTC+8) | 观测 | 动作 |
|--------------|------|------|
| 04:09:31 | QPS ≈ 1156，CPU 1% → **85%** | **4 → 5** |
| 04:12:01 | QPS ≈ **1997**，5 副本，CPU ≈ **100%** | 无法继续扩（`max-instances=5`） |
| 04:13:46 | QPS ≈ **2503**，CPU ≈ **100%** | 仍满负载 |
| 04:12:16 起 | JVM 指标倾向缩容 | **`scale-down blocked`**（docker CPU ≥ 0.35） |

**推论**：

1. 2000～2500 RPS 时，**5 个 order 副本仍长期 ~100% CPU** → 说明瓶颈在 order 处理能力，而非「副本数不够但 CPU 很低」。
2. `max-instances=5` 到顶后系统 **无法通过继续扩 order 消化更高 RPS**，与「order 是主瓶颈」一致。
3. 缩容保护（`docker-cpu-scale-down-block-threshold: 0.35`）在压测全程 **block 删副本**，侧面证明 order 的 docker CPU 持续处于高位。

---

### 证据 B：k6 端到端延迟随 RPS 上升而恶化（order 饱和特征）

修复序列化 bug 后，业务成功率 100%，HTTP 失败 0%，故延迟上升 **不是** 错误重试或超时风暴导致，而是 **处理能力接近上限** 的典型表现。

| 轮次 | RPS 阶梯 | 总请求 | 业务成功 | med | p95 | p99 |
|------|----------|--------|----------|-----|-----|-----|
| B | 500,1000,1500 | 360,002 | 100% | 2.88 ms | 6.43 ms | **15.77 ms** |
| C | 1500,2000,2500 | 720,004 | 100% | 2.84 ms | 12.08 ms | **49.06 ms** |

**对比**：

- 中位数 med 几乎不变（~2.8 ms）→ 常态路径仍快。
- **p99 从 15.77 ms 升至 49.06 ms**（约 3.1×）→ 高 RPS 下尾部请求排队，符合 **order 线程/CPU 排队**，而非 Redis/MQ 整体变慢（见证据 C）。

**推论**：在 gateway + Redis 均未报障的前提下，尾延迟恶化与 **order 5 副本 CPU 打满** 时间窗重合，指向 order 为延迟敏感路径上的约束点。

---

### 证据 C：`docker stats` 横向对比 — 下游组件未饱和

**方法**：`tests/order-stress-test/probe-gateway-fraud-bottleneck.ps1` — 2500 RPS 压测同时每 3s 采样容器 CPU。

| 组件 | 空闲 CPU | 压测中 CPU（典型） | 是否像瓶颈 |
|------|----------|-------------------|------------|
| **singularity-order-3** | 15% | **95～112%** | ✅ 单副本打满 |
| **singularity-order-5** | 3% | **99～104%** | ✅ 单副本打满 |
| singularity-gateway-0 | 0.27% | **148～170%** | ⚠️ 次瓶颈（见 §5） |
| singularity-redis | — | **~18%** | ❌ 远未饱和 |
| singularity-rmq-broker | ~2% | 未显著升高 | ❌ 远未饱和 |

**未列入采样的组件**（同步路径不经过 / 无 HTTP 压力）：

| 组件 | 压测路径中的角色 | 为何不是「最薄弱」 |
|------|------------------|-------------------|
| **singularity-stock** | 仅消费 MQ 异步扣 MySQL | 不在同步抢单链；CPU 平稳、消费速率有上限，属削峰落库 |
| **singularity-product** | 未调用 | 无流量 |
| **singularity-user** | 未调用 | 无流量 |
| **singularity-merchant** | 未调用 | 无流量 |
| **MySQL** | order/stock 异步写 | 同步路径以 Redis 为准；压测未现 DB 连接池打满 |

**推论**：在 **2500 RPS** 下，与抢单同步相关的中间件 **Redis ~18%**、**RMQ 无显著 CPU**；而 **order 多副本均 ~100%**。排除法支持 **order 为同步链最窄处**。

---

### 证据 D：order 副本流量均衡 — 瓶颈在单实例算力而非「流量没打到」

轮次 B 各副本 `local tx ok` 日志统计（Redis Lua 成功次数）：

| 副本 | local tx ok | 说明 |
|------|-------------|------|
| order-3 | 80,961 | 全程在线 |
| order-4 | 80,960 | 全程在线 |
| order-5 | 80,960 | 全程在线 |
| order-6 | 75,125 | 02:45 扩容，晚约 36s 接流 |
| order-7（已删） | ~42,000（估） | 仅存活约 3 分钟 |

**推论**：

1. Gateway → Nacos → 多 order 副本 **分流正常**。
2. 每个在线副本都接近 **单核 CPU 上限**，不是某一副本空转。
3. 扩容 order **有效分摊 QPS**（从 3 副本 CPU 86% → 扩到 5 仍高负载，说明需求侧 RPS 更高），符合「扩 order 是对的」。

---

### 证据 E：order 内部 — `FraudDetectionInterceptor` 是 CPU 主因

#### E.1 代码与设计意图

`FraudDetectionInterceptor` 文档明确写明：hash-rounds 越高，**CPU 开销越大**，且为高并发下 **scaler 扩容的合理触发源**。

```java
// singularity-order/.../FraudDetectionInterceptor.java（文档注释摘要）
// hashRounds=8  → ~1.0ms/req, 2000 RPS → CPU ~200%（强烈扩容信号）
// 典型演示：1500 RPS 打 1 实例 → CPU 冲上 70% → scaler 扩 2→3
```

每条 `/api/order/snag`（仅 userId）请求在进 allocate 前 **必过** 该拦截器：多轮 SHA-256 特征哈希 + 滑动窗口计数 + 槽位跳变检测 — **纯 CPU 计算，无 IO 等待**。

#### E.2 日志间接分段耗时（2500 RPS 探测）

| 阶段 | 耗时（估） | 类型 |
|------|------------|------|
| FraudDetection（SHA-256 × 8 轮） | **~1 ms/req** | CPU 密集 |
| handler + MQ + Redis（`LoggingInterceptor cost=`） | ~1.5 ms avg | CPU + 少量 IO |
| k6 端到端 med | ~2.8 ms | 含 gateway + 网络 |

**推论**：在 order 进程内，**Fraud 段占单请求 CPU 时间的大头**；Redis/MQ 段耗时不长且下游 CPU 低，进一步说明 **瓶颈在 order 进程内计算，而非 Redis/MQ 服务**。

#### E.3 旁证：轮次 A 故障也在 order 内

轮次 A 业务成功率 0% 的根因是 `OrderServiceImpl` 内 **MQ 半消息体序列化失败**（105,166 条日志），修复后成功率 **0% → 100%**。  
说明：**同步路径的成功与否首先由 order 决定**，与 stock 无关。

---

### 证据 F：架构与 scaler 能力 — 只有 order 被设计成「可扩的最窄环」

| 服务 | compose 能否 scale | scaler 是否监控/扩容 | 压测中是否触发 |
|------|-------------------|---------------------|----------------|
| **singularity-order** | ✅ 无固定 `container_name` | ✅ `max-instances: 5` | ✅ 多次扩容 |
| singularity-gateway | ❌ 固定 `singularity-gateway-0` + `8080:8080` | ❌ 未纳入 | — |
| singularity-stock | 可 scale | 在列表中 | ❌ 未触发 |
| singularity-product | 可 scale | 在列表中 | ❌ 未触发 |
| singularity-user | 可 scale | 在列表中 | ❌ 未触发 |
| singularity-redis | 单实例 | 无 | — |

**推论**：在「**在最薄弱处扩容**」的工程语境下，**order 既是观测上最窄的同步环，又是当前唯一被 scaler 验证有效的扩容目标**。

---

## 4. 排除项汇总（「不是最薄弱」的详细理由）

### 4.1 stock

- 同步抢单：**Redis Lua 在 order 内扣库存**，不调用 stock HTTP。
- 异步：`OrderTopicConsumer` 消费 `order-topic` 写 MySQL；压测中表现为 **匀速消费、CPU 相对平稳**（MQ 削峰设计）。
- **72 万笔成功后 stock 仍在扣 MySQL** 是预期行为，不代表 stock 同步路径成为瓶颈。

### 4.2 Redis

- 2500 RPS 下 CPU **~18%**；单次 Lua（`DECR` + `HSET`）极轻。
- 单实例 Redis 理论上可支撑远高于当前 RPS；**未观测到延迟尖刺或 CPU 打满**。

### 4.3 RocketMQ

- Broker CPU 压测中 **未显著升高**；半消息 + 事务提交非同步路径主延迟来源。
- 异步消费积压影响 **最终一致性**，不阻塞 k6 侧 `success: true` 返回。

### 4.4 product / user / merchant

- 本压测 **零 HTTP 调用**；秒杀同步链不经过这些服务。

### 4.5 MySQL（同步视角）

- 抢单成功与否由 **Redis 库存** 决定；订单/库存 MySQL 写入在 MQ 之后。
- 压测未出现同步超时与 DB 相关错误栈。

---

## 5. 次瓶颈说明：gateway（不推翻 order 为主结论）

| 指标 | gateway | order（单副本） |
|------|---------|----------------|
| 2500 RPS CPU | **148～170%** | **95～112%** |
| 可扩容 | ❌ compose 单实例 | ✅ 已扩至 5 |
| scaler | 未监控 | 已监控并扩容 |

**说明**：

1. gateway CPU 甚至高于 **单个** order 副本，是因为 **只有 1 个 gateway 承接全部入口流量**。
2. 在 **5 个 order 副本合计** 仍 ~100% CPU 且 **无法继续扩容** 时，系统整体吞吐仍受 **order 集群算力** 约束；gateway 1.5～1.7 核尚未单独成为 **first failure**（HTTP 0% 失败、p99 49ms 仍可接受）。
3. 若 RPS 继续升高，**gateway 可能先于 order 报错** — 这是后续实验方向，**不改变当前「应优先扩 order」的结论**。

---

## 6. 与「按最薄弱处扩容」的对应关系

| 观测信号 | 数据表明 | 应扩谁 | 系统现状 |
|----------|----------|--------|----------|
| order docker CPU ≥ 70% 或 surge | 轮次 B/C 多次触发 | **order** | ✅ scaler 已执行 3→4→5 |
| 5 副本仍 ~100% CPU | 轮次 C 2000～2500 RPS | **order**（需提高 max-instances） | ⚠️ 顶到 max=5 |
| p99 随 RPS 升、med 稳定 | 49ms @ 2500 RPS | **order** 排队 | ✅ 与 CPU 饱和一致 |
| Redis/MQ CPU 低 | ~18% / 无尖刺 | 不扩 | ✅ |
| stock 未触发 scaler | 无同步压力 | 不扩 | ✅ |
| gateway CPU 高但不可扩 | 148～170% | gateway（需改 compose） | ❌ 未实现 |

---

## 7. 复现与原始数据来源

```powershell
# 1. 预热 Redis 桶
tests/order-stress-test/refill-stock-buckets.ps1

# 2. 阶梯压测（示例：与轮次 C 相同）
tests/order-stress-test/run-k6-order-gateway-fraud-ramp.ps1 -RpsRamp "1500,2000,2500" -StepSec 120

# 3. 瓶颈探测（gateway / order / redis / rmq CPU 采样）
tests/order-stress-test/probe-gateway-fraud-bottleneck.ps1 -Rps 2500 -DurationSec 30

# 4. scaler 决策日志
docker logs singularity-scaler-0 --since 30m | Select-String "singularity-order|scale|blocked"

# 5. order 副本负载（local tx 成功次数）
docker logs singularity-singularity-order-3 --since 30m 2>&1 | Select-String "local tx ok" | Measure-Object
```

|  artifacts | 路径 |
|------------|------|
| k6 汇总 | `tests/order-stress-test/k6-out/summary-gateway-fraud-ramp.json` |
| 瓶颈采样 | `tests/order-stress-test/k6-out/bottleneck-docker-stats.txt` |
| 压测完整报告 | [gateway-fraud-ramp-test-report-2026-05-24.md](./gateway-fraud-ramp-test-report-2026-05-24.md) |
| Scaler 配置 | `singularity-scaler/src/main/resources/application.yml` |
| 风控拦截器 | `singularity-order/.../FraudDetectionInterceptor.java` |

---

## 8. 最终陈述（可直接用于答辩）

> 在 Gateway + allocate + FraudDetection 秒杀压测中，我们通过对 **k6 延迟**、**docker stats 横向 CPU**、**scaler 扩容日志** 与 **order 内部分段耗时** 的交叉验证，得出结论：**当前最薄弱且应优先水平扩容的环节是 `singularity-order`**。  
>  
> 证据包括：（1）负载上升时 **仅 order CPU 反复超过 70% 阈值并触发 scaler 扩容**；（2）**5 副本到顶后 CPU 仍 ~100%**，p99 延迟随 RPS 恶化而 Redis/MQ 未饱和；（3）瓶颈位于 order 内 **FraudDetectionInterceptor 的 CPU 密集计算**；（4）stock、product、user 不在同步热路径，Redis ~18% CPU，**排除为首要瓶颈**。  
>  
> Gateway 单实例 CPU 亦高，但 compose 未纳入 scaler；在可扩缩容前提下，**扩 order 与全部观测证据一致，符合「在最薄弱处扩容」的要求**。

---

## 9. 后续可补充证据（可选）

1. 合入 `OrderSnagMetrics` + `order_snag_fraud_detection_seconds` Timer，用 Prometheus 直接证明 Fraud 段占比。  
2. 提高 `max-instances`（如 8）观察 CPU 下降与 p99 改善，验证「扩 order 有效」。  
3. gateway-lb + 可 scale gateway，对比 RPS 继续升高时 gateway 与 order 谁先失败。
