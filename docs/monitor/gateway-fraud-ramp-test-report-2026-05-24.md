# Gateway Fraud Ramp 压测报告

**项目**：WHUSingularity  
**路径**：k6 → `singularity-gateway:8080` → `/api/order/snag`（body 仅 `{ userId }`）→ allocate + `FraudDetectionInterceptor` + Redis Lua + RocketMQ  
**脚本**：`tests/order-stress-test/k6-snag-gateway-fraud-ramp.js`  
**编排**：`tests/order-stress-test/run-k6-order-gateway-fraud-ramp.ps1`  
**报告日期**：2026-05-24  

---

## 1. 测试环境

| 项 | 配置 |
|----|------|
| 入口 | Gateway `8080` → Nacos LB → order 多副本 |
| order 副本 | 压测过程中由 scaler 在 **3～5** 之间自动调整 |
| scaler | `singularity-scaler-0`，15s 调度，`max-instances=5` |
| 风控 | `FraudDetectionInterceptor`，默认 `hash-rounds=8` |
| 库存 | Redis 预热约 1M/桶（`tests/order-stress-test/refill-stock-buckets.ps1`） |

**说明**：第一次压测曾因 `OrderMessage` 序列化 bug 导致业务成功率 0%，修复后（`ObjectMapper` 注册 `JavaTimeModule`）后续测试均为 100% 业务成功。

---

## 2. 压测轮次汇总

| 轮次 | 时间 (UTC+8) | RPS 阶梯 | 每档时长 | 迭代数 | HTTP 失败 | 业务成功 | p99 延迟 | 备注 |
|------|--------------|----------|----------|--------|-----------|----------|----------|------|
| A | ~10:25 | 500,1000,1500 | 120s | 357,619 | 1.08% | **0%** | 21.42s | 序列化失败，见 §3 |
| B | ~10:45 | 500,1000,1500 | 120s | 360,002 | 0% | **100%** | 15.77ms | 修复后；scaler 4→5→4 |
| C | ~12:09 | 1500,2000,2500 | 120s | 720,004 | 0% | **100%** | 49.06ms | 高负载；scaler 4→5，缩容被 block |

---

## 3. 轮次 A：业务成功率 0%（已修复）

### 3.1 k6 摘要

- `snag_business_success`: **0%**（353,751 次 HTTP 200，但 JSON `success: false`）
- `http_req_duration` p99: **21.42s**（约 1% 超时拉高尾延迟）
- `dropped_iterations`: 2,383

### 3.2 根因

`OrderServiceImpl` 使用未注册 `JavaTimeModule` 的 `ObjectMapper`，`OrderMessage.createTime`（`LocalDateTime`）序列化失败：

```
InvalidDefinitionException: Java 8 date/time type `java.time.LocalDateTime` not supported by default
→ 返回 { "success": false, "message": "序列化失败" }
```

order-3 日志中同类错误 **105,166** 条。

### 3.3 修复

```java
private static final ObjectMapper OBJECT_MAPPER =
    new ObjectMapper().registerModule(new JavaTimeModule());
```

修复后探测：`{"success":true,"data":{"orderId":"..."}}`。

---

## 4. 轮次 B：500 → 1000 → 1500 RPS（修复后）

### 4.1 k6 结果

| 指标 | 值 |
|------|-----|
| 总请求 | 360,002（≈ 1000 RPS × 360s） |
| HTTP 失败 | 0% |
| 业务成功 | 100% |
| med / p95 / p99 | 2.88 / 6.43 / **15.77 ms** |
| max VU | 81 |

三档均打满，无 `dropped_iterations`。

### 4.2 Scaler 伸缩（UTC）

| 时间 | 事件 |
|------|------|
| 02:45:23 | docker CPU **86%**，3 副本 → **扩到 4**（创建 order-6） |
| 02:47:52 | QPS ≈ **1001**，4 副本 |
| 02:48:08 | docker CPU 42%→**100%** 暴增 → **扩到 5**（创建 order-7） |
| 02:50:38 | QPS ≈ **1500**，5 副本，docker CPU **74%** |
| 02:50:52 | JVM 指标触发缩容 debounce **1/3** |
| 02:51:22 | 压测结束，QPS 回落 → **5→4**（删除 order-7） |

**问题**：1500 RPS 压测期间 JVM 指标偏低，scaler 已开始缩容 debounce，存在压测中途删副本风险。

### 4.3 副本流量（local tx ok 统计）

| 副本 | local tx ok | 说明 |
|------|-------------|------|
| order-3 | 80,961 | 全程在线 |
| order-4 | 80,960 | 全程在线 |
| order-5 | 80,960 | 全程在线 |
| order-6 | 75,125 | 02:45 扩容，晚约 36s 接流量 |
| order-7（已删） | ~42,000（估） | 02:48–02:51 约 3 分钟 |

Gateway → Nacos 分流正常；order-6 **有接流量**，并非空跑。

---

## 5. 轮次 C：1500 → 2000 → 2500 RPS（高负载）

### 5.1 k6 结果

| 指标 | 值 |
|------|-----|
| 总请求 | 720,004（≈ 2000 RPS × 360s） |
| HTTP 失败 | 0% |
| 业务成功 | 100% |
| med / p95 / p99 | 2.84 / 12.08 / **49.06 ms** |
| max VU | 331 |

三档均打满，无丢迭代。

### 5.2 Scaler 伸缩（UTC，+8h = 北京时间）

| 时间 | 事件 |
|------|------|
| 04:09:18 | k6 启动（1500 RPS） |
| 04:09:31 | QPS ≈ 1156，docker CPU 1%→**85%** → **4→5**（order-7） |
| 04:09:54 | order-7 开始接 k6 流量 |
| 04:11:18 | 2000 RPS 档开始 |
| 04:12:01 | QPS ≈ **1997**，5 副本，docker CPU ≈ **100%** |
| 04:12:16 起 | JVM 想缩容 → **`scale-down blocked`**（docker CPU ≥ 0.35） |
| 04:13:18 | 2500 RPS 档开始 |
| 04:13:46 | QPS ≈ **2503**，docker CPU ≈ **100%**，仍 blocked |
| 04:15:18 | k6 结束 |
| 04:15:31 | QPS 降至 537，CPU 11.8% → debounce 1/3（scaler 随后被 stop） |

**本次仅一次扩容（4→5）**；`max-instances=5` 已到顶，2500 RPS 时无法再加副本。  
**docker CPU 缩容保护生效**：压测全程未删副本。

---

## 6. 瓶颈探测（2500 RPS × 25s）

**方法**：k6 打满 2500 RPS 同时每 3s 采样 `docker stats`。

### 6.1 空闲 → 压测中 CPU（%）

| 组件 | 空闲 | 压测中（典型） |
|------|------|----------------|
| **singularity-gateway-0** | 0.27 | **148～170** |
| singularity-order-3 | 15 | **95～112** |
| singularity-order-5 | 3 | **99～104** |
| singularity-redis | — | **~18** |
| singularity-rmq-broker | ~2（空闲） | 未显著升高 |

### 6.2 order 内部耗时（日志间接）

| 阶段 | 耗时（估） | 说明 |
|------|------------|------|
| FraudDetection（SHA-256 × 8 轮） | ~1 ms/req | 文档校准；CPU 密集 |
| handler + MQ + Redis（LoggingInterceptor `cost=`） | ~1.5 ms avg | 日志统计 |
| k6 端到端 med | ~2.8 ms | Gateway + 网络 + order |

---

## 7. 瓶颈结论（扩缩容依据）

### 7.1 链路薄弱环节排序

1. **order 服务（主瓶颈）**  
   - 5 副本、2500 RPS 时聚合 docker CPU 仍 **98～100%**  
   - 根因：**`FraudDetectionInterceptor` CPU 密集计算** + 事务路径  
   - **扩容对象**：`singularity-order`（当前 scaler 已做）

2. **gateway 单实例（次瓶颈）**  
   - 2500 RPS 时 **148～170% CPU**（1.5～1.7 核）  
   - compose 仅 `singularity-gateway-0`，**scaler 不监控、不扩**  
   - 再提 RPS 时可能 **gateway 先于 order 崩溃**

3. **Redis / RocketMQ**  
   - 压测中 CPU 低、延迟 med ~3ms → **不是瓶颈**

### 7.2 对老师「按最薄弱处扩容」的回应

| 观测信号 | 应扩谁 | 当前 scaler |
|----------|--------|-------------|
| order docker CPU ≥ 70% 或 CPU 暴增 | **order** | ✅ 已实现 |
| order docker CPU ≥ 35% 且 JVM 想缩容 | **禁止缩 order** | ✅ `docker-cpu-scale-down-block-threshold: 0.35` |
| gateway CPU ≥ 70% | **gateway** | ❌ 未纳入 |
| 分阶段 Timer（Fraud / handler / tx） | **order（精确到组件）** | ❌ main 未合入 `OrderSnagMetrics` |

---

## 8. Scaler 配置要点（本轮相关）

```yaml
scaler:
  docker-cpu-scale-down-block-threshold: 0.35
  services:
    - name: singularity-order
      min-instances: 1
      max-instances: 5
      cpu-scale-up-threshold: 0.70
      cpu-scale-down-threshold: 0.20   # JVM，仅缩容参考
      docker-cpu-scale-down-block-threshold: 0.35
```

- **扩容依据**：docker cgroup CPU（≥70% 或 surge）  
- **缩容依据**：JVM Prometheus + 历史窗口；**且** docker CPU < 35% 才允许缩  

---

## 9. 复现命令

```powershell
# 压测（示例：1500,2000,2500 阶梯）
tests/order-stress-test/refill-stock-buckets.ps1
tests/order-stress-test/run-k6-order-gateway-fraud-ramp.ps1 -RpsRamp "1500,2000,2500" -StepSec 120

# 瓶颈探测（各组件 CPU 采样）
tests/order-stress-test/probe-gateway-fraud-bottleneck.ps1 -Rps 2500 -DurationSec 30

# 查看 scaler 日志
docker logs singularity-scaler-0 --since 30m | Select-String "singularity-order|scale|blocked"
```

**k6 汇总文件**（容器内写入）：`tests/order-stress-test/k6-out/summary-gateway-fraud-ramp.json`

---

## 10. 后续建议

1. **合入分阶段指标**：`OrderSnagMetrics` + `order_snag_fraud_detection_seconds`，用数据证明 Fraud 段占 order CPU 大头。  
2. **gateway 多副本 + scaler 监控**：验证高 RPS 下是否 gateway 先饱和。  
3. **提高 `max-instances`**（如 8）或固定 `min-instances=3`，观察 2500+ RPS 拐点。  
4. **压测前**可选 `docker stop singularity-scaler-0`，纯观察固定副本；**演示自动扩缩容**时保持 scaler 运行。

---

## 11. 相关变更与文件

| 文件 | 说明 |
|------|------|
| `singularity-order/.../OrderServiceImpl.java` | `JavaTimeModule` 修复序列化 |
| `singularity-scaler/.../ScalingService.java` | docker CPU 缩容保护 |
| `tests/order-stress-test/probe-gateway-fraud-bottleneck.ps1` | 瓶颈探测脚本 |
| `tests/order-stress-test/run-k6-order-gateway-fraud-ramp.ps1` | 压测入口 |
| `tests/order-stress-test/k6-out/stats-samples.txt` | 2500 RPS docker stats 原始采样 |
