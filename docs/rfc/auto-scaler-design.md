# singularity-scaler: 服务实例自动伸缩设计与实现计划

## 背景

当前系统中各微服务的实例数量在 `docker-compose.backend.yml` 中预定义，无法根据运行时负载动态调整。虽然已有 PowerShell 伸缩脚本（`deploy/scale-instance.ps1`）支持手动启停容器，但仍需人工判断何时触发。

目标：实现一个独立的 Scaler 服务，持续监控各服务资源指标（CPU / JVM 堆内存 / QPS），当资源利用率超过/低于阈值时自动启停容器实例。

## 最终目标

一个 Spring Boot 微服务（`singularity-scaler`），周期性采集各服务 Prometheus 指标（QPS、CPU 使用率、JVM 堆内存使用率），基于资源利用率百分比 + 滑动窗口平滑决策，自动启停 Docker 容器实例，并提供 REST API 查看状态和手动触发。

## 可行性分析

### 所有服务均为无状态

| 服务 | 共享状态 | 伸缩影响 |
|---|---|---|
| User | Redis (blacklist/cache) | 零影响 |
| Order | Redis buckets + RocketMQ | 零影响（slot 分的是 Redis key，不是实例） |
| Stock | Redis + MySQL + RocketMQ | consumer 自动 rebalance |
| Product | Redis + MySQL (Caffeine 冷启动后 Cache-Aside 自动回源) | 新实例短暂缓存未命中 |
| Merchant | MySQL / H2 | 零影响 |
| Gateway | 纯路由 | 零影响 |

结论：实例增减对业务无副作用，无需改动 singularity-core 框架。

### 基础设施已就绪

- **Nacos**：新实例启动后自动注册，Gateway 和 OpenFeign 自动发现并负载均衡
- **RocketMQ**：consumer group 内新实例自动 rebalance
- **Docker 伸缩脚本**：已有容器启停逻辑和端口分配策略，可移植为 Java 实现

### 现有短板：指标暴露

- 仅 Gateway 有 `spring-boot-starter-actuator`
- 所有服务均无 Micrometer / Prometheus 依赖

补齐 actuator + prometheus 后，Spring Boot 自动采集 `http.server.requests`（QPS）、`process.cpu.usage`（CPU）、`jvm.memory.used`（内存）等指标，无需写自定义 MeterBinder。

## 架构设计

```
┌─────────────────────────────────────────────────┐
│              singularity-scaler :9090            │
│                                                 │
│  ┌───────────┐  ┌──────────┐  ┌─────────────┐  │
│  │  Nacos    │  │ Metrics  │  │   Docker    │  │
│  │ Discovery │  │ Scraper  │  │  Executor   │  │
│  │ (实例发现) │  │ (指标采集)│  │ (容器启停)  │  │
│  └─────┬─────┘  └────┬─────┘  └──────┬──────┘  │
│        │             │               │          │
│        ▼             ▼               ▼          │
│  ┌─────────────────────────────────────────┐    │
│  │          ScalingService (编排)          │    │
│  │  冷却检查 → 实例计数 → 采集 → 评估 → 执行 │    │
│  └─────────────────────────────────────────┘    │
│        │                                        │
│        ▼                                        │
│  ┌──────────────┐  ┌────────────────────┐       │
│  │ PolicyConfig │  │ CooldownManager    │       │
│  │ (阈值策略)   │  │ (防抖动)           │       │
│  └──────────────┘  └────────────────────┘       │
│                                                 │
│  REST API: /api/scaler/status | /api/scaler/scale│
└─────────────────────────────────────────────────┘
         │                    │
         ▼                    ▼
    Nacos :8848          Docker Engine
  (服务注册发现)       (容器生命周期管理)
```

### 轮询循环（每 15s）

1. 遍历所有配置的服务
2. 检查冷却状态（上次操作后 120s 内不重复操作同一服务）
3. 通过 Nacos 获取该服务的所有健康实例
4. 遍历所有健康实例，HTTP GET `/actuator/prometheus`，采集 CPU 使用率、JVM 堆内存使用率、QPS
5. 计算集群平均资源利用率，记录到滑动窗口（最近 10 个周期）
6. 将当前指标与策略阈值比较：扩容即时触发（CPU 或内存超阈值），缩容需连续 3 个周期低于阈值
7. 超过上限 → `docker run` 新实例；低于下限 → `docker rm` 最高序号实例
8. 记录操作日志 + 重置冷却计时

### 指标采集方式

不部署 Prometheus Server。直接 HTTP 抓取各服务所有健康实例的 `/actuator/prometheus` 端点，在 Scaler 内解析并取集群平均。

主要指标：
- `process_cpu_usage` — 进程 CPU 使用率（0.0~1.0）
- `jvm_memory_used_bytes` / `jvm_memory_max_bytes`（area=heap） — JVM 堆内存使用率
- `http_server_requests_seconds_count` — 计算两次采集间的 rate 作为 QPS

### 容器启停

通过 `ProcessBuilder` 调用 `docker run` / `docker rm` 命令（跨平台）。容器命名、网络、卷挂载、环境变量与现有 docker-compose 一致。端口分配逻辑从 PS 脚本移植（order 用奇数步进，其余顺序递增）。

## 分步计划

> **状态：全部已完成**（2026-04-28）

### Step 1: 现有服务添加 Actuator + Prometheus 依赖 ✅

为 6 个服务的 `pom.xml` 添加依赖，为 6 个服务的 `application.yml` 添加 management 端点配置。零 Java 代码改动。

- **产出物**:
  - `singularity-order/pom.xml` — 加 actuator + micrometer-registry-prometheus
  - `singularity-user/pom.xml` — 同上
  - `singularity-stock/pom.xml` — 同上
  - `singularity-product/pom.xml` — 同上
  - `singularity-merchant/pom.xml` — 同上
  - `singularity-gateway/pom.xml` — 加 micrometer-registry-prometheus（已有 actuator）
  - 6 个 `application.yml` — 追加 `management:` 配置块（gateway 合并到已有块）
- **验收**:
  ```bash
  mvn clean package -DskipTests
  # 构建通过后，启动任一服务，确认 prometheus 端点返回数据：
  curl -s http://localhost:8081/actuator/prometheus | grep http_server_requests_seconds_count
  ```

### Step 2: 创建 singularity-scaler 模块骨架 ✅

新建模块目录结构、pom.xml、application.yml、bootstrap.yml、ScalerApplication.java，注册到父 pom modules。

- **产出物**:
  - `singularity-scaler/pom.xml` — 依赖：web, actuator, nacos-discovery, nacos-config, bootstrap, lombok
  - `singularity-scaler/src/main/resources/application.yml` — 端口 9090 + scaler 配置
  - `singularity-scaler/src/main/resources/bootstrap.yml` — Nacos 连接
  - `singularity-scaler/src/main/java/.../ScalerApplication.java` — `@SpringBootApplication @EnableDiscoveryClient`
  - `pom.xml`（父） — `<modules>` 加 `<module>singularity-scaler</module>`
- **验收**:
  ```bash
  mvn clean package -DskipTests
  ```

### Step 3: 实现 Nacos 服务发现 + Prometheus 指标采集 ✅

实现 NacosServiceDiscovery（获取健康实例列表和地址）、PrometheusTextParser（解析 Prometheus 文本格式）、MetricsScraper（HTTP 抓取 + 内存存储计算 rate）。

- **产出物**:
  - `NacosServiceDiscovery.java` — 封装 `NamingService.selectInstances()`
  - `PrometheusTextParser.java` — 按 metric name + label filter 提取值
  - `MetricsScraper.java` — 从 Nacos 取一个实例地址 → HTTP GET /actuator/prometheus → 计算指标 rate
- **验收**:
  ```bash
  # 启动 scaler，日志中应能看到 Nacos 发现的各服务实例
  # 可通过临时 endpoint 或日志确认指标值被正确解析（此步无 REST API，用日志验证）
  ```

### Step 4: 实现 Docker 容器管理（端口分配 + 启停执行） ✅

实现 DockerContainerInspector（解析 `docker ps` 输出）、PortAllocator（端口扫描分配）、DockerCommandExecutor（ProcessBuilder 执行 docker run/rm）。端口分配逻辑从现有 PS 脚本移植。

- **产出物**:
  - `DockerContainerInspector.java` — `docker ps` 解析容器名和端口
  - `PortAllocator.java` — 按服务配置的端口范围 + 步进分配可用端口
  - `DockerCommandExecutor.java` — 构造并执行 `docker run` / `docker rm` 命令
- **验收**:
  ```bash
  # 手动测试：构造一个 startInstance 调用（可通过单元测试或临时 main），确认容器启动并注册到 Nacos
  docker ps | grep singularity-order
  ```

### Step 5: 实现策略评估 + 冷却 + 编排 + 定时任务 ✅

实现 ScalerProperties（配置绑定）、PolicyEvaluator（阈值比较）、CooldownManager（防抖动）、ScalingService（编排全流程）、ScalingScheduler（@Scheduled 轮询）。

- **产出物**:
  - `ScalerProperties.java` — @ConfigurationProperties("scaler")
  - `model/ServiceState.java` — 实例数、QPS、冷却状态
  - `model/ScaleResult.java` — 伸缩操作结果
  - `PolicyEvaluator.java` — 比较指标与阈值，返回 SCALE_UP / SCALE_DOWN / NONE
  - `CooldownManager.java` — ConcurrentHashMap 记录每个服务最后操作时间
  - `ScalingService.java` — 编排：冷却检查 → 实例计数 → 采集 → 评估 → 执行
  - `ScalingScheduler.java` — @Scheduled 轮询入口
- **验收**:
  ```bash
  # 启动 scaler，观察日志是否正常输出轮询信息
  # 无 Prometheus 端点访问时不应触发伸缩，日志显示 "metric below threshold" 或 "cooldown active"
  ```

### Step 6: 实现 REST API + Nacos 策略配置 ✅

实现 ScalerController（status + scale 端点），创建 Nacos 配置 `singularity-scaler.yaml`。

- **产出物**:
  - `ScalerController.java` — `GET /api/scaler/status`、`POST /api/scaler/scale`
  - Nacos 配置 `singularity-scaler.yaml`（Data ID, Group: DEFAULT_GROUP）
- **验收**:
  ```bash
  curl http://localhost:9090/api/scaler/status | jq .
  # 应返回各服务的实例数、当前 QPS、冷却状态
  curl -X POST http://localhost:9090/api/scaler/scale \
    -H "Content-Type: application/json" \
    -d '{"service":"singularity-order","action":"SCALE_UP"}'
  docker ps | grep singularity-order   # 确认新容器启动
  ```

### Step 7: 部署集成与文档更新 ✅

将 scaler 加入 docker-compose，更新 Nacos 文档和项目文档。

- **产出物**:
  - `deploy/docker-compose.backend.yml` — 添加 scaler 服务定义
  - `docs/nacos/README.md` — 添加 `singularity-scaler.yaml` 配置模板
  - `CLAUDE.md` — 更新 Architecture 目录树、启动顺序、Nacos 配置列表
  - `docs/ARCHITECTURE.md` — 架构图加入 scaler
- **验收**:
  ```bash
  # docker-compose 构建验证（不实际启动，仅配置检查）
  docker compose -f deploy/docker-compose.backend.yml config > /dev/null
  ```

## 非目标

- 不改动 `singularity-core` 框架代码（所有服务无状态，Slot 模型不受实例数影响）
- 不实现 Prometheus Server / Grafana 可视化（直连 actuator 端点足够）
- 不实现优雅停机（`docker rm -f` 强制移除，Nacos 心跳超时自动注销）
- 不重构现有 PowerShell 脚本（Docker 命令在 Java 中重新实现）
- 不为现有服务添加自定义 MeterBinder / HealthIndicator（auto-config 指标已够用）
- 不实现 merchant 和 gateway 的自动伸缩（仅管理 order/user/stock/product 四个核心服务）

## 参考

- `deploy/scale-instance.ps1` — 统一入口脚本，参数格式和 JSON 输出规范
- `deploy/start-next-order-instance.ps1` — Docker run 命令构造、环境变量、端口分配的参考实现
- `deploy/scaling-scripts.md` — 设计约束（并发控制、dry-run、审计日志）
- `deploy/docker-compose.backend.yml` — 容器命名、网络、卷挂载、环境变量的基准配置
- `singularity-gateway/src/main/resources/application.yml` — management 端点配置的现有模板
- `docs/task_card_template.md` — 本文档结构参考

## 自动化验收命令

- 运行环境: Docker Desktop + Java 21 + Maven
- 执行命令格式: 直接在项目根目录执行

```bash
# Step 1 验收：全量构建 + 指标端点验证
mvn clean package -DskipTests
curl -s http://localhost:8081/actuator/prometheus | grep http_server_requests_seconds_count

# Step 2 验收：模块构建通过
mvn clean package -DskipTests

# Step 3-5 验收：启动 scaler 观察日志（无自动化断言，人工确认日志输出正常）

# Step 6 验收：REST API + 手动扩容
curl -s http://localhost:9090/api/scaler/status | jq .
curl -X POST http://localhost:9090/api/scaler/scale \
  -H "Content-Type: application/json" \
  -d '{"service":"singularity-order","action":"SCALE_UP"}'
sleep 30  # 等待容器启动 + Nacos 注册
docker ps --format "{{.Names}}" | grep singularity-order

# Step 7 验收：docker-compose 配置检查
docker compose -f deploy/docker-compose.backend.yml config > /dev/null && echo "OK"
```

## 成功条件

- `mvn clean package -DskipTests` 通过（exit code 0）
- 各服务 `/actuator/prometheus` 端点返回指标数据
- `GET /api/scaler/status` 返回各服务状态
- `POST /api/scaler/scale` 手动扩容成功，`docker ps` 可见新容器
- Nacos 控制台可见新注册的实例
- diff 范围仅在预期文件内（pom.xml、application.yml、新增 scaler 模块、部署配置）

## 错误处理约定

- 如某步失败：先分析原因，给出修复方案，等确认后再修
- 如连续两次失败：停下来，列出可能原因，不要继续盲目重试
- 如遇到环境/依赖问题：报告具体报错，不要自行修改环境配置

---

## 实现问题与修复记录

以下为实际开发测试过程中遇到的问题及修复方案，供后续维护参考。

### 问题 1：Bean 名称冲突 — `NacosServiceDiscovery`

**现象**：Spring Boot 启动时报 `BeanDefinitionOverrideException`：
```
Cannot register bean definition for bean 'nacosServiceDiscovery'
since there is already [...] bound
```
**根因**：类名 `NacosServiceDiscovery` 产生的 bean 名与 Spring Cloud Alibaba 的 `com.alibaba.cloud.nacos.discovery.NacosDiscoveryAutoConfiguration` 中的 bean 冲突。
**修复**：将类重命名为 `InstanceDiscovery`，更新所有引用（`MetricsScraper`、`ScalingService`）。
**文件**：`singularity-scaler/src/main/java/.../discovery/InstanceDiscovery.java`

### 问题 2：scaler 容器内缺少 docker CLI

**现象**：`DockerCommandExecutor.execute()` 抛出 `IOException: Cannot run program "docker": error=2, No such file or directory`
**根因**：`maven:3.9.9-eclipse-temurin-21` 基础镜像不含 Docker CLI，scaler 服务虽然挂载了宿主机的 `/var/run/docker.sock`，但容器内没有 `docker` 命令。
**修复**：在 `docker-compose.backend.yml` 的 scaler 服务 `command` 中加入自动安装逻辑：
```yaml
command: sh -c "which docker || (apt-get update -qq && apt-get install -y -qq docker.io) && java -jar ..."
```
**文件**：`deploy/docker-compose.backend.yml`

### 问题 3：新容器无法访问 jar 文件

**现象**：`docker run` 启动的新实例报错 `Unable to access jarfile singularity-user/target/singularity-user-1.0-SNAPSHOT.jar`
**根因**：`DockerCommandExecutor` 构造 `-v` 挂载时使用的 `repoRoot` 是容器内的 `/workspace`，但 `docker run` 是在宿主机上执行的，volume mount 需要宿主机侧路径。
**修复**：在构造函数中增加三层探测逻辑：
1. 优先读取环境变量 `SCALER_REPO_ROOT`
2. 否则通过 `docker inspect` 查询自身容器的 `/workspace` mount source（获取宿主机侧路径）
3. 否则按当前工作目录回退推算
**文件**：`singularity-scaler/src/main/java/.../docker/DockerCommandExecutor.java`

### 问题 4：order 服务 Flyway checksum mismatch

**现象**：order 容器启动失败，日志报 `FlywayValidateException: Migration checksum mismatch for migration version 1`
**根因**：`singularity-order/src/main/resources/db/migration/V1__Create_Order_Tables.sql` 在数据库已应用后被修改，数据库中记录的 checksum（`141246234`）与本地文件重新计算的值（`-1048902616`）不一致。
**修复**：在 MySQL 中 drop 并重建 `singularity_order` 数据库，让 Flyway 重新执行迁移。开发环境数据不珍贵，此方案最简单。
**操作**：
```sql
DROP DATABASE IF EXISTS singularity_order;
CREATE DATABASE singularity_order DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 问题 5：product 服务连接失败 — `Unknown database 'singularity_product'`

**现象**：product 容器启动失败，日志报 `SQLSyntaxErrorException: Unknown database 'singularity_product'`
**根因**：MySQL 的 `mysql_data` Docker volume 是之前运行残留的，`docker-entrypoint-initdb.d` 中的 `01-init.sql` 仅在 volume 首次初始化时执行，后续修改 init 脚本不会重新运行。因此 `singularity_product` 数据库从未被创建。
**修复**：手动在 MySQL 中创建数据库：
```sql
CREATE DATABASE IF NOT EXISTS singularity_product DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
```
**注意**：`docker-compose.backend.yml` 中 product-0 已正确配置了 `SPRING_DATASOURCE_URL` 环境变量，无需修改。

---

在开始实施之前，请先：
1. 用你自己的话复述：目标是什么、边界是什么
2. 列出你认为的风险点或歧义
3. 给出最小改动方案（只写思路，不写代码）
4. 等我确认后再实施
