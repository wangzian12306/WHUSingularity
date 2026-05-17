# singularity-scaler 资源利用率驱动伸缩 — 实施计划

> 方案：B（滑动窗口 + 多实例平均 + 扩缩分离策略）

---

## 1. 背景与问题

当前 `singularity-scaler` 的伸缩决策只基于单一指标 `http_server_requests_seconds_count`（QPS），且使用硬编码绝对阈值（100/20）。

**已知缺陷**：
- 不感知服务器资源健康状态。CPU 100% 但 QPS 未到 100 时不会扩容；QPS 到了 100 但 CPU 仅 10% 时盲目扩容。
- 硬编码数值无法适应不同规模并发。几万并发场景下单机 QPS 阈值失去意义。
- 单实例采样。`MetricsScraper` 只抓 Nacos 健康实例列表中的第一个，不能代表集群平均负载。
- 单周期判断，无历史平滑。负载毛刺容易导致"扩完秒缩"的震荡。

---

## 2. 目标

将伸缩决策从"固定 QPS 阈值"迁移到"资源利用率百分比（CPU / JVM 内存）+ 集群平均负载 + 滑动窗口平滑"。

---

## 3. 架构改动概览

```
singularity-scaler
├── metrics
│   ├── MetricsScraper          ← 增强：采集 CPU / 内存，遍历全部实例取平均
│   ├── PrometheusTextParser    ← 不变（已能解析所有指标行）
│   └── MetricHistory           ← 新增：每个服务最近 N 个周期的 CPU / 内存快照滑动窗口
├── policy
│   ├── PolicyEvaluator         ← 重写：基于资源利用率百分比 + 历史窗口判断
│   └── CooldownManager         ← 不变（120s 冷却保持）
├── orchestration
│   ├── ScalingService          ← 微调：注入 MetricHistory，编排时查询历史
│   └── ScalingScheduler        ← 不变（15s 轮询周期保持）
├── model
│   ├── ServiceState            ← 增强：新增 avgCpu / avgMemory 字段
│   └── ServiceMetricSnapshot   ← 新增：单个周期内的资源指标快照
└── controller
    └── ScalerController        ← 微调：/status 返回资源利用率详情
```

---

## 4. 核心组件设计

### 4.1 MetricsScraper 增强

**变更点**：
1. 从 prometheus 响应中额外提取：
   - `process_cpu_usage`（0~1，进程 CPU 使用率）
   - `jvm_memory_used_bytes` / `jvm_memory_max_bytes`（JVM 堆内存）
2. 遍历 Nacos **全部健康实例**，分别采集后计算算术平均值，作为该服务的集群平均负载。

**方法签名变更**：
```java
public class ResourceMetrics {
    double qps;
    double cpuUsage;      // 0.0 ~ 1.0
    double memoryUsage;   // 0.0 ~ 1.0
}

public ResourceMetrics scrape(String serviceName)
```

**实例采样策略**：
- 若某实例 `/actuator/prometheus` 不可达，跳过该实例，用剩余可达实例平均。
- 若全部实例不可达，返回全 0 且记录 warn，本次轮询不触发伸缩。

### 4.2 MetricHistory（新增）

**核心原则：滑动窗口按"服务"聚合，不是按"单个实例"。**

每个被监控的服务（order / user / stock / product）在 `MetricHistory` 中拥有独立的环形队列，队列里存储的是该服务在最近 N 个轮询周期内的**集群平均指标**。

```java
@Component
public class MetricHistory {
    // serviceName -> RingBuffer<ResourceMetrics>
    private final Map<String, ArrayDeque<ResourceMetrics>> histories = new ConcurrentHashMap<>();

    public void record(String serviceName, ResourceMetrics metrics);
    public List<ResourceMetrics> recent(String serviceName, int n);
    public boolean allRecentBelow(String serviceName, int n, Predicate<ResourceMetrics> predicate);
}
```

**为什么按服务维度而不是实例维度？**

- 伸缩的单元是"服务"。新实例注册到 Nacos 后，Gateway 和 OpenFeign 自动对所有实例做负载均衡，因此决策应基于"整个集群的平均健康度"。
- 若某个实例 CPU 100% 但其他实例空闲，平均后可能只有 30%，此时不应盲目扩容（可能是单实例异常或负载不均），而应依赖告警。扩容解决的是"整个服务集群容量不足"。
- 若按实例独立窗口 + 独立决策，会出现 order-0 触发扩容、order-1 触发缩容的冲突局面。

**容量选择**：10 × 15s = 150s，覆盖略长于冷却期（120s），保证缩容判断有足够历史。

### 4.3 PolicyEvaluator 重写

**决策逻辑**：

| 动作 | 条件 | 说明 |
|---|---|---|
| SCALE_UP | `avgCpu >= 0.70` **或** `avgMemory >= 0.80` | 任一资源紧张即扩容，快速响应。不检查历史，单周期触发。 |
| SCALE_DOWN | `avgCpu <= 0.20` **且** `avgMemory <= 0.30` **且** 连续 3 个周期满足 | 防止毛刺，必须连续低负载才缩容。 |
| NONE | 其他情况 | 保持现状。 |

**边界约束**（保持原有）：
- `currentInstances < maxInstances`（默认 5）才能扩容
- `currentInstances > minInstances`（默认 1）才能缩容

### 4.4 ServiceState 增强

`GET /api/scaler/status` 返回体新增字段：

```json
{
  "serviceName": "singularity-order",
  "instanceCount": 2,
  "currentQps": 145.3,
  "avgCpuUsage": 0.82,
  "avgMemoryUsage": 0.45,
  "cooldownActive": false,
  "lastActionTime": 1714291200000
}
```

---

## 5. 服务隔离与独立伸缩

四个核心服务（order / user / stock / product）在监控和伸缩上是**完全隔离**的：

| 维度 | 隔离方式 |
|---|---|
| **服务发现** | `InstanceDiscovery` 按 `serviceName` 从 Nacos 分别查询，互不影响。 |
| **指标采集** | `MetricsScraper.scrape("singularity-order")` 只抓 order 的实例，不会混入 user 的数据。 |
| **滑动窗口** | `MetricHistory` 以 `serviceName` 为 key，四个服务各有一个独立的 `ArrayDeque<ResourceMetrics>`。 |
| **策略评估** | `PolicyEvaluator` 对 order 的决策只基于 order 自己的平均指标和历史窗口。 |
| **容器执行** | `docker run` / `docker rm` 的容器名包含服务名（如 `singularity-order-1`），操作范围精确到单个服务。 |

**示例**：
- order 服务 CPU 飙到 80% → 触发 order 扩容，user/stock/product 不受任何影响。
- user 服务连续 3 个周期低负载 → 触发 user 缩容，order 即使正在高负载也保持现状。

---

## 6. 数据流

```
ScalingScheduler (@Scheduled 15s)
  │
  ▼
ScalingService.evaluateAndScale(serviceConfig)
  │
  ├──► CooldownManager.isCooldownActive() ? return
  │
  ├──► InstanceDiscovery.getHealthyInstances() → 获取全部实例
  │
  ├──► MetricsScraper.scrape(serviceName)
  │       ├── 对每个实例 HTTP GET /actuator/prometheus
  │       ├── PrometheusTextParser.parse()
  │       ├── 提取 http_server_requests_seconds_count (QPS)
  │       ├── 提取 process_cpu_usage (CPU)
  │       ├── 提取 jvm_memory_used/max (内存)
  │       └── 取平均 → ResourceMetrics
  │
  ├──► MetricHistory.record(serviceName, metrics)
  │
  ├──► PolicyEvaluator.evaluate(metrics, history, currentInstances, min, max)
  │       ├── 扩容：cpu>=0.70 || memory>=0.80 ? SCALE_UP
  │       └── 缩容：连续3周期 cpu<=0.20 && memory<=0.30 ? SCALE_DOWN
  │
  └──► DockerCommandExecutor.startInstance() / removeInstance()
```

---

## 6. 配置变更

`application.yml` 中 `scaler.services[*]` 移除 QPS 相关字段，新增资源阈值字段：

```yaml
scaler:
  cooldown-seconds: 120
  history-size: 10
  services:
    - name: singularity-order
      # ... 原有端口、镜像、env 不变 ...
      cpu-scale-up-threshold: 0.70
      memory-scale-up-threshold: 0.80
      cpu-scale-down-threshold: 0.20
      memory-scale-down-threshold: 0.30
      scale-down-consecutive-periods: 3
```

旧字段 `qps-scale-up-threshold`、`qps-scale-down-threshold` 删除。

---

## 7. 实施步骤

| 步骤 | 内容 | 涉及文件 |
|---|---|---|
| 1 | 新增 `ResourceMetrics`、`ServiceMetricSnapshot` 模型 | `model/` |
| 2 | 增强 `PrometheusTextParser`（如需要）或直接用现有 `parse()` + 新提取方法 | `metrics/PrometheusTextParser.java` |
| 3 | 重写 `MetricsScraper.scrape()`：多实例采集 + CPU/内存解析 + 平均 | `metrics/MetricsScraper.java` |
| 4 | 新增 `MetricHistory` 滑动窗口组件 | `metrics/MetricHistory.java` |
| 5 | 重写 `PolicyEvaluator.evaluate()`：资源百分比 + 历史窗口判断 | `policy/PolicyEvaluator.java` |
| 6 | 增强 `ServiceState` 和 `ScalerController.status()` | `model/ServiceState.java`, `controller/ScalerController.java` |
| 7 | 调整 `ScalingService`：注入 MetricHistory，更新编排流程 | `orchestration/ScalingService.java` |
| 8 | 更新 `application.yml` 配置 | `resources/application.yml` |
| 9 | 全量构建验证 | `mvn clean package -DskipTests` |
| 10 | 本地集成测试 | 手动扩缩 + 压测观察 |

---

## 8. 测试计划

### 8.1 单元测试
- `PrometheusTextParser` 能正确从 prometheus 文本中提取 `process_cpu_usage` 和 `jvm_memory_*`。
- `MetricHistory` 的环形队列、recent()、allRecentBelow() 行为正确。
- `PolicyEvaluator` 对各种边界条件（恰好阈值、连续周期不足、实例数边界）返回预期 `ScaleAction`。

### 8.2 集成测试
1. 启动 scaler + order 服务，访问 `GET /api/scaler/status`，确认 `avgCpuUsage`、`avgMemoryUsage` 有值且在合理范围（0~1）。
2. 对 order 服务施压（wrk / ab / 连续抢单），观察 scaler 日志：
   - CPU 持续 ≥ 70% 后应在 15s 内触发扩容。
   - `docker ps` 确认新容器 `singularity-order-1` 出现。
   - Nacos 控制台确认新实例注册。
3. 停止压测，观察 scaler 日志：
   - 连续 3 个周期（约 45s）CPU 和内存都低于缩容阈值后，触发缩容。
   - 冷却期内（120s）即使负载再次飙升也不重复扩容。

### 8.3 异常测试
- 某实例 actuator 端口不可达时，scraper 用剩余实例平均，不抛异常。
- 全部实例不可达时，本次轮询跳过，服务不触发伸缩。

---

## 9. 风险与回滚

| 风险 | 缓解措施 |
|---|---|
| 新指标导致误扩容/误缩容 | 阈值先保守设置（CPU 70% 扩、20% 缩），线上观察后再微调。 |
| 环形队列内存泄漏 | 容量固定（10），key 是有限服务名（4 个），无泄漏风险。 |
| actuator 端点无 CPU/内存指标 | Spring Boot Actuator + Micrometer 默认已暴露 `process_cpu_usage` 和 `jvm_memory_*`，无需改业务服务。 |

**回滚**：直接回退 `singularity-scaler` 到上一个版本镜像即可，不影响其他微服务。

---

## 10. 非目标（明确排除）

- 不改 `singularity-core` 框架。
- 不改动 order/user/stock/product 的业务代码或 pom.xml（actuator 指标已在 Step 1 补齐）。
- 不部署 Prometheus Server / Grafana。
- 不实现基于自定义 MeterBinder 的指标（标准 actuator 指标已够用）。
- 不对 gateway / merchant 做自动伸缩（保持原有范围）。
