# singularity-scaler 监控与自动伸缩实现说明

> 本文档描述 `singularity-scaler` 服务当前的监控采集与自动伸缩实现细节，对应代码状态为 2026-05-09。

---

## 1. 架构概览

`singularity-scaler`（端口 9090）是一个独立的 Spring Boot 微服务，不依赖外部 Prometheus Server 或 Grafana。它通过 Nacos 发现各业务服务的健康实例，直接 HTTP 抓取各实例的 `/actuator/prometheus` 端点，在本地解析指标并做集群平均，最后基于资源利用率百分比 + 滑动窗口平滑决策，自动启停 Docker 容器实例。

```
┌─────────────────────────────────────────────┐
│         singularity-scaler :9090             │
│                                              │
│  ┌─────────────┐  ┌──────────────────────┐  │
│  │ Nacos       │  │ MetricsScraper       │  │
│  │ Discovery   │  │ (HTTP 采集 + 解析)    │  │
│  └──────┬──────┘  └──────────┬───────────┘  │
│         │                    │              │
│         ▼                    ▼              │
│  ┌──────────────────────────────────────┐  │
│  │      ScalingService (编排)           │  │
│  │  冷却检查 → 实例计数 → 采集 → 评估 → 执行 │  │
│  └──────────────────────────────────────┘  │
│         │                                   │
│         ▼                                   │
│  ┌──────────────────────────────────────┐  │
│  │  DockerCommandExecutor               │  │
│  │  (docker run / docker rm -f)         │  │
│  └──────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
```

---

## 2. 指标暴露

所有业务服务（order、user、stock、product、merchant、gateway）均通过 Spring Boot Actuator + Micrometer 暴露 Prometheus 指标：

**依赖（pom.xml）**
- `spring-boot-starter-actuator`
- `micrometer-registry-prometheus`

**端点配置（application.yml）**
```yaml
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

默认可用的关键指标：
- `http_server_requests_seconds_count` — HTTP 请求总次数
- `process_cpu_usage` — 进程 CPU 使用率（0.0 ~ 1.0）
- `jvm_memory_used_bytes{area="heap"}` — JVM 堆已用内存
- `jvm_memory_max_bytes{area="heap"}` — JVM 堆最大内存

---

## 3. 指标采集与解析

### 3.1 采集入口

`singularity-scaler/src/main/java/com/lubover/singularity/scaler/metrics/MetricsScraper.java`

`scrape(String serviceName)` 的完整流程：

1. 通过 `InstanceDiscovery.getHealthyInstances(serviceName)` 从 Nacos 获取该服务的**全部健康实例**；
2. 对每个实例发起 `GET http://<ip>:<port>/actuator/prometheus`；
3. `PrometheusTextParser.parse(String text)` 解析 Prometheus text format，返回 `Map<metricName, Map<key, value>>`；
4. 计算三项集群平均指标：
   - **QPS**：取 `http_server_requests_seconds_count` 的总和，与前一次 `MetricSnapshot` 比较算 rate（`delta / seconds`）；
   - **CPU**：取 `process_cpu_usage`；
   - **内存**：取 `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}`；
5. 所有成功采集的实例做**算术平均**，返回 `ResourceMetrics(qps, cpuUsage, memoryUsage)`。

若某个实例不可达（超时或状态码非 200），则跳过该实例，用剩余可达实例平均；若全部不可达，返回全 0 并记 warn。

### 3.2 解析器

`singularity-scaler/src/main/java/com/lubover/singularity/scaler/metrics/PrometheusTextParser.java`

- 使用正则 `^(?!#\s*)(\w+)(\{([^}]*)\})?\s+([\d.eE+-]+)$` 匹配指标行；
- `extractRate()`：对同名指标的所有时间线求和；
- `extractByLabelFilter()`：按 label key=value 过滤后求和。

---

## 4. 历史平滑窗口

`singularity-scaler/src/main/java/com/lubover/singularity/scaler/metrics/MetricHistory.java`

- 以 `serviceName` 为 key，每个被监控服务拥有一个独立的 `ArrayDeque<ResourceMetrics>`；
- 默认容量 `history-size = 10`，对应 10 × 15s = 150s 的历史数据，略长于冷却期（120s）；
- 提供 `allRecentBelow(serviceName, n, predicate)` 方法，用于缩容时判断连续 N 个周期是否都低于阈值。

**为什么按服务维度聚合而不是实例维度？**
伸缩的单元是服务。新实例注册到 Nacos 后，Gateway 和 OpenFeign 自动对所有实例做负载均衡，因此决策应基于整个集群的平均健康度，避免单实例异常导致误判。

---

## 5. 伸缩策略

`singularity-scaler/src/main/java/com/lubover/singularity/scaler/policy/PolicyEvaluator.java`

| 动作 | 条件 | 说明 |
|---|---|---|
| **SCALE_UP** | 单周期 `avgCpu >= cpuScaleUpThreshold` **或** `avgMemory >= memoryScaleUpThreshold`，且 `currentInstances < maxInstances` | 任一资源紧张即扩容，快速响应，不检查历史 |
| **SCALE_DOWN** | 连续 `scaleDownConsecutivePeriods` 个周期（默认 3）`avgCpu <= cpuScaleDownThreshold` **且** `avgMemory <= memoryScaleDownThreshold`，且 `currentInstances > minInstances` | 防止毛刺，必须连续低负载才缩容 |
| **NONE** | 其他情况 | 保持现状 |

---

## 6. 冷却与调度

### 6.1 冷却管理

`singularity-scaler/src/main/java/com/lubover/singularity/scaler/policy/CooldownManager.java`

- `ConcurrentHashMap<String, Long>` 记录每个服务最后一次伸缩操作的毫秒时间戳；
- `isCooldownActive(serviceName, cooldownSeconds)`：距上次操作不足 `cooldownSeconds`（默认 120s）时返回 true，跳过本次评估。

### 6.2 定时轮询

`singularity-scaler/src/main/java/com/lubover/singularity/scaler/orchestration/ScalingScheduler.java`

```java
@Scheduled(fixedRate = 15000)
public void poll() { ... }
```

每 15 秒遍历 `scaler.services` 中配置的所有服务，串行调用 `ScalingService.evaluateAndScale(config)`，单个服务异常不影响其他服务。

---

## 7. 容器执行

### 7.1 容器启停

`singularity-scaler/src/main/java/com/lubover/singularity/scaler/docker/DockerCommandExecutor.java`

通过 `ProcessBuilder` 调用宿主机 `docker` 命令：

- **扩容** (`startInstance`)：
  ```
  docker run -d --name <service>-<index> --restart unless-stopped \
    --network deploy_default -p <port>:<port> \
    -v <repoRoot>:/workspace -v deploy_maven_repo:/root/.m2 \
    -w /workspace <image> \
    sh -c "java -jar <module>/target/<module>-1.0-SNAPSHOT.jar"
  ```
- **缩容** (`removeInstance`)：
  ```
  docker rm -f <containerName>
  ```

**仓库根目录探测（三层回退）**：
1. 环境变量 `SCALER_REPO_ROOT`
2. 通过 `docker inspect` 查询自身容器的 `/workspace` mount source
3. 按当前工作目录回退推算

### 7.2 容器发现

`singularity-scaler/src/main/java/com/lubover/singularity/scaler/docker/DockerContainerInspector.java`

- 解析 `docker ps --format "{{.Names}}\t{{.Ports}}"`；
- `getContainerNamesForService(serviceName)`：按 `serviceName-\d+` 匹配；
- `getMaxIndex(serviceName)`：返回匹配容器的最大序号，用于新实例命名和缩容时移除最高序号实例。

### 7.3 端口分配

`singularity-scaler/src/main/java/com/lubover/singularity/scaler/docker/PortAllocator.java`

- 扫描所有运行中容器的 host port；
- 计算 `basePort + portStep * index`，跳过已被占用的端口；
- 分配首个可用端口。例如 order 的 `basePort=8081, portStep=2`，实例序号为 0/1/2 时对应端口 8081/8083/8085。

---

## 8. REST API

`singularity-scaler/src/main/java/com/lubover/singularity/scaler/controller/ScalerController.java`

| 方法 | 端点 | 说明 |
|---|---|---|
| GET | `/api/scaler/status` | 返回所有服务状态列表：`ServiceState`（实例数、平均 QPS、CPU、内存、冷却状态、上次操作时间） |
| POST | `/api/scaler/scale` | 手动触发伸缩，Body 为 `{"service":"singularity-order","action":"SCALE_UP"}` |

---

## 9. 配置摘要

`singularity-scaler/src/main/resources/application.yml`

```yaml
scaler:
  interval-seconds: 15
  cooldown-seconds: 120
  history-size: 10
  services:
    - name: singularity-order
      base-port: 8081
      port-step: 2
      min-instances: 1
      max-instances: 5
      cpu-scale-up-threshold: 0.70
      memory-scale-up-threshold: 0.80
      cpu-scale-down-threshold: 0.20
      memory-scale-down-threshold: 0.30
      scale-down-consecutive-periods: 3
      image: maven:3.9.9-eclipse-temurin-21
      env:
        SPRING_CLOUD_NACOS_SERVER_ADDR: nacos:8848
        ...
```

当前监控与伸缩的服务：order、user、stock、product（merchant 和 gateway 未纳入自动伸缩范围）。

---

## 10. 完整数据流

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

## 11. 已知边界与限制

- 不部署 Prometheus Server / Grafana，指标仅用于 scaler 本地决策；
- 缩容使用 `docker rm -f`，非优雅停机，依赖 Nacos 心跳超时自动注销；
- 端口分配基于宿主机 `docker ps`，若宿主机有其他进程占用同端口可能冲突；
- scaler 容器内必须能访问宿主机的 `docker` CLI（`docker-compose.backend.yml` 中已配置自动安装逻辑）。
