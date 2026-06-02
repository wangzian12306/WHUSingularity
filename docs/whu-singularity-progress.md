# WHUSingularity 近期进度与说明

> 汇总本仓库近期实现、压测结论与运维注意点。**与远程分支差异、具体 diff** 请以本机 `git status`、`git log gitee/main..HEAD` 为准；下文不按「未提交/已提交」做实时追踪。

## 1. 已落地（相对先前讨论）

### 1.1 Stock：`deductStock` 单条 SQL 条件扣减

- **实现**：`StockMapper.deductAvailableAndReserve` — 一条 `UPDATE` 在 `available_quantity >= quantity` 时原子执行：扣可用、加预占、`version + 1`（已移除「先 SELECT `version` 再 `UPDATE ... AND version = ?`」及 3 次重试路径）。
- **效果**：高并发消费下不再产生 **`版本冲突，重试后仍失败`**；历史表里该类 `remark` 为旧代码遗留，**以 `create_time` 在部署新 JAR 之后** 的统计为准。
- **部署**：`mvn -pl singularity-stock -am package -DskipTests` 后 `docker compose ... up -d --no-deps --force-recreate singularity-stock`。

### 1.2 压测与可观测性

- **`k6-snag-docker-internal-business.js`** + compose 服务 **`k6-order-load-business`** + `tests/order-stress-test/run-k6-order-load-business.cmd|.ps1`：除 HTTP 200 外校验 **`success === true`**，指标 **`snag_business_success`**。
- **Redis 无桶 / 业务全失败**：须 `refill-stock-buckets`；桶卖光且 `markEmpty` 后需 **重启 order** 才能再抢。
- **RocketMQ**：`mqadmin topicStatus` / `consumerProgress`（如 `stock-order-consumer-group`）对照 `order-topic` 生产与消费；积压大时 **MySQL 压力高属预期**。

## 2. 主要改动面索引（按模块）

便于 code review；清单可能与当前提交历史部分重叠，**细目以 `git diff` 为准**。

| 模块 | 要点 |
|------|------|
| **`singularity-scaler`** | `ScalingService`、`DockerContainerInspector`、`PolicyEvaluator`、`ScalerProperties`、`MetricsScraper`、`DockerCommandExecutor`；删除 `PortAllocator`；`application.yml` 扩容相关项。 |
| **`deploy/`** | `docker-compose.backend.yml`（多 order LB、k6、order 环境变量等）、`nginx-order-lb-main.conf`、`order-lb.conf`、`rebuild-stack.ps1`、`mysql/patch-stock-bucket-products.sql` 等。 |
| **`singularity-stock`** | **`StockServiceImpl` / `StockMapper`（单条条件扣减）**；种子 SQL `V001__seed_stock_dev.sql` 等。 |
| **`tests/order-stress-test/`** | k6 脚本、`refill-stock-buckets.*`、压测编排 `.ps1/.sh/.cmd`；compose 挂载该目录到 k6 `/scripts`。 |
| **其他** | 如 `api-integration-tests-python`、`singularity-front`、`docs/startup.md`、`dev-run.sh` 等小改。 |

## 3. 压测与架构结论（持续有效）

- **`/api/order/snag`**：业务失败仍常返回 **HTTP 200 + `success:false`**，不能仅用 k6 的 `http_req_failed` 代表成交。
- **Order vs Stock**：抢单以 **Redis/Lua** 为准；Stock 通过 **MQ 异步** 写 MySQL，`order-topic` 堆积会导致 **Stock 与 DB 持续高压**，属正常现象。
- **MySQL 顶满**：短时高峰压测 + 海量异步扣库时 **CPU/IO 高** 常见；常态业务若长期如此需容量、参数或削峰方案。

## 4. 后续可选事项

1. **Scaler**：`ScalingScheduler` 与配置项 **`scaler.interval-seconds`** 对齐、扩容冷却与突发策略等（按产品需求排期）。
2. **Stock / MQ**：若需进一步减轻 MySQL，可考虑消费批量写、表分区、或规格与 `innodb_buffer_pool` 等调优（改动前需单独评估）。

---

*文档曾用名：`local-changes-since-pull.md`。若在其他笔记中引用旧文件名，请改指向本文档。*
