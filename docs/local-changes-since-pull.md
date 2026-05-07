# 自远程同步以来的本地变更记录（工作区快照）

> 文档目的：便于在推送/合并前核对改动；**Stock 消费侧乐观锁优化**计划在代码上传后再做，见文末「待办」。

## 1. 仓库状态说明

- 截至编写时，本地 `main` 曾显示相对 `gitee/main` **超前约 35 个提交**（以你机器上 `git log gitee/main..HEAD --oneline` 为准）。
- 下文 **§2** 列出的是当前 **仍未 `git add` 提交** 的变更：`Changes not staged` 与 `Untracked files`（与当时 `git status` 一致）。若你已部分提交，请以最新 `git status` 为准并自行增删本节。

## 2. 未提交变更清单（按模块）

### 2.1 `singularity-scaler`

| 路径 | 类型 | 说明（概要） |
|------|------|--------------|
| `ScalerProperties.java` | 修改 | 扩容相关配置项扩展（与 `application.yml` 对齐，如 grace、surge、间隔等） |
| `DockerContainerInspector.java` | 修改 | Docker 采样、`docker stats`、compose 下容器列表等，供扩容决策 |
| `DockerCommandExecutor.java` | 修改 | 命令执行层精简或调整 |
| `MetricsScraper.java` | 修改 | 指标抓取补充 |
| `ScalingService.java` | 修改 | 扩容编排主流程（Docker CPU、surge、JVM 等与策略联动） |
| `PolicyEvaluator.java` | 修改 | 扩缩容策略判定 |
| `application.yml` | 修改 | Scaler 运行时默认配置 |
| `PortAllocator.java` | **删除** | 端口分配类移除（逻辑迁移或不再需要，以 diff 为准） |

### 2.2 `deploy/`

| 路径 | 类型 | 说明（概要） |
|------|------|--------------|
| `docker-compose.backend.yml` | 修改 | 栈定义更新：含多 LB、k6 服务、`singularity-order` 环境（如 `SPRING_APPLICATION_JSON` slot）、Scaler 依赖等 |
| `nginx/order-lb.conf` | 修改 | Order LB upstream 等 |
| `nginx/nginx-order-lb-main.conf` | **新增** | 独立 nginx 主配置（多 LB 场景） |
| `rebuild-stack.ps1` | 修改 | 重建/扩缩相关脚本参数 |
| `refill-stock-buckets.ps1` / `.sh` | **新增** | Redis `stock:bucket-*` 灌桶 |
| `mysql/patch-stock-bucket-products.sql` | **新增** | MySQL 侧商品/库存补丁（若使用） |
| `run-k6-order-load-business.cmd` / `.ps1` | **新增** | 运行「带业务成功率」的 k6：`k6-order-load-business` |

### 2.3 `singularity-stock`

| 路径 | 类型 | 说明（概要） |
|------|------|--------------|
| `db/seed/dev/V001__seed_stock_dev.sql` | 修改 | 开发种子数据（如 `PROD_001` / `PROD_002` 库存量级等） |

> **未改**：`StockServiceImpl` / `StockMapper` 等业务代码在本快照中仍为「读 `version` + 乐观锁重试」；高并发下易出现 `stock_change_log.remark = 版本冲突，重试后仍失败`。

### 2.4 `tests/order-stress-test/`（与 compose 挂载相关）

Compose 将 `./tests/order-stress-test` 挂到 k6 容器的 `/scripts`。其中包含（至少）：

- `k6-snag-docker-internal-business.js`：**业务成功率**（`success === true`）、指标 `snag_business_success`、可选 `STRICT_BUSINESS_MIN_RATE`。
- 其余 `k6-snag-*.js` 与 `k6-out/` 等仍以仓库内实际文件为准。

若推送时遗漏该目录，`k6-order-load-business` 会找不到脚本。

### 2.5 其他

| 路径 | 说明（概要） |
|------|--------------|
| `api-integration-tests-python/` | 集成测试/README 小改 |
| `singularity-front/nginx.conf`, `vite.config.ts` | 前端构建/反代相关 |
| `docs/startup.md` | 启动文档更新 |
| `dev-run.sh` | 开发启动脚本 |

## 3. 压测与排查结论（便于日后对照）

- **仅看 HTTP 200** 不能代表抢单业务成功：`/api/order/snag` 失败时常仍返回 **200 + `success:false`**。
- **业务成功率** 应使用 `k6-snag-docker-internal-business.js` / `run-k6-order-load-business.cmd`，或自行解析响应 JSON。
- **RocketMQ `order-topic`**：成功提交的订单才会增加各队列 Max Offset；可用 `mqadmin topicStatus` / `consumerProgress` 核对。
- **Stock 慢 / 扣库存失败**：在 MySQL 库存充足时，失败多为 **`版本冲突，重试后仍失败`**（多并发对同一 `product_id` 行做读-改-写 + `version` 条件更新）。**单纯多开 Stock 实例**能缓解 **MQ 积压**，但可能 **加重** 行上冲突，需配合扣库 SQL/事务模型改进。

## 4. 待办（上传 / 合并后）

1. **Stock 改进（建议优先）**：将 `deductStock` 热路径改为 **单条条件 `UPDATE`**（`available_quantity >= quantity` + 扣减 + 必要时同事务内更新 `reserved_quantity`），减少「读 version 再更新」窗口；再视情况调整重试与消费并行度。
2. 可选：**Scaler** `ScalingScheduler` 与 `scaler.interval-seconds` 绑定、扩容冷却与突发策略等（见 scaler 相关 issue/需求）。

---

*生成说明：本文档依据工作区 `git status` 与对话中的排障结论整理；推送前请再执行一次 `git status` / `git diff` 核对路径与意图。*
