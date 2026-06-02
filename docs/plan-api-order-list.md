# Plan: 实现 /api/order/list 订单列表查询接口

## 背景

前端 `docs/frontend/progress.md` 将 Phase 2/3 的多个页面（秒杀主页、用户中心、Admin 订单管理）标记为"阻塞于后端"，因为后端缺少 `GET /api/order/list` 接口。

当前状态：
- 前端已预留 API 契约（`api/order.ts`、`api/types.ts`）
- 后端 `OrderController` 仅有 `POST /snag`、`GET /{orderId}`、`PUT /{orderId}/status`，无列表查询
- 后端 `OrderMapper` 仅有 `insert`、`selectByOrderId`、`updateStatus`，无列表 SQL
- 数据库表 `order` 已有 `idx_user_id` 和 `idx_status` 索引，具备查询条件

## 最终目标

后端暴露 `GET /api/order/list` 分页查询接口，前端类型对齐，解除 Phase 2/3 页面开发阻塞。

## 分步计划

### Step 1: 后端 Mapper 层 — 列表查询 SQL
- **做什么**: 在 `OrderMapper.java` 新增 `selectList` 和 `countList` 方法签名；在 `OrderMapper.xml` 编写带动态 WHERE 的分页 SQL 和计数 SQL
- **产出物**:
  - `singularity-order/src/main/java/com/lubover/singularity/order/mapper/OrderMapper.java`
  - `singularity-order/src/main/resources/mapper/OrderMapper.xml`
- **验收**: `mvn -pl singularity-order clean compile` 编译通过，无 MyBatis 绑定错误

### Step 2: 后端 Controller 层 — REST 端点
- **做什么**: 在 `OrderController` 新增 `GET /api/order/list`，接收 `actorId`、`status`、`page`、`size` 参数，调用 Mapper，包装为前端期望的分页响应结构
- **产出物**: `singularity-order/src/main/java/com/lubover/singularity/order/controller/OrderController.java`
- **验收**:
  - `mvn -pl singularity-order clean package -DskipTests` 构建通过
  - 本地启动 order 服务后，`curl "http://localhost:8081/api/order/list?page=0&size=5"` 返回 `{"success":true,"data":{"content":[],"totalElements":0,"totalPages":0,"page":0,"size":5}}`

## 非目标

- 不动前端页面组件（Login.tsx、Register.tsx 等）
- 不修改现有 `snagOrder`、`getOrderById`、`updateOrderStatus` 逻辑
- 不引入 PageHelper 或 Spring Data JPA 等新依赖
- 不修改数据库表结构（已有索引足够）
- 不跨步骤顺手优化周边代码

## 参考

- 前端契约: `singularity-front/src/api/types.ts`（`OrderListParams`、`OrderListResponse`）
- 前端调用: `singularity-front/src/api/order.ts`
- 后端现有 Controller 风格: `singularity-order/.../OrderController.java`（`success`/`failure` Map 封装）
- 后端现有 Mapper 风格: `singularity-order/.../OrderMapper.xml`（MyBatis XML + `<resultMap>`）
- 数据库表结构: `singularity-order/src/main/resources/db/migration/V1__Create_Order_Tables.sql`

## 自动化验收命令

**Step 1 验收**:
```bash
mvn -pl singularity-order clean compile
```

**Step 2 验收**:
```bash
mvn -pl singularity-order clean package -DskipTests
# 服务启动后:
curl -s "http://localhost:8081/api/order/list?page=0&size=5" | jq
```

## 成功条件

- 所有步骤验收命令通过（exit code 0）
- diff 范围限定在 `singularity-order/src/main/java/.../OrderMapper.java`、`OrderController.java`、`mapper/OrderMapper.xml`
- 接口返回结构与前端 `OrderListResponse` 一致

## 错误处理约定

- 如某步失败：先分析原因，给出修复方案，等确认后再修
- 如连续两次失败：停下来，列出可能原因，不要继续盲目重试
- 如遇到环境/依赖问题：报告具体报错，不要自行修改环境配置（如 Maven、JDK、Node 版本）
