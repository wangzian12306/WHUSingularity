## 快速启动

### 1. 启动基础服务

#### 1.1 启动 Nacos

```bash
# 下载 Nacos 2.x（https://github.com/alibaba/nacos/releases）
cd nacos/bin

# Linux/Mac
sh startup.sh -m standalone

# Windows
startup.cmd -m standalone
```

访问 Nacos 控制台：http://localhost:8848/nacos（用户名/密码：nacos/nacos）

#### 1.2 配置 Nacos（必须）

在 Nacos 控制台创建以下配置文件（详见 `docs/nacos/README.md`）：

- `singularity-order.yaml`
- `singularity-user.yaml`
- `singularity-stock.yaml`

#### 1.3 启动 MySQL

创建数据库：

```sql
-- 订单服务数据库（表结构由 Flyway 自动创建）
CREATE DATABASE singularity_order DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 用户服务数据库
CREATE DATABASE singularity_user DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- 执行用户服务初始化脚本：singularity-user/src/main/resources/schema.sql

-- 库存服务数据库（表结构由 Flyway 自动创建）
CREATE DATABASE singularity_stock DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

#### 1.4 启动 Redis

```bash
redis-server
```

#### 1.5 启动 RocketMQ

```bash
# 启动 NameServer
sh bin/mqnamesrv

# 启动 Broker
sh bin/mqbroker -n localhost:9876
```

### 2. 启动微服务

```bash
# 1. 编译整个项目
mvn clean package -DskipTests

# 2. 启动服务（按顺序）
# 启动用户服务
java -jar singularity-user/target/singularity-user-1.0-SNAPSHOT.jar

# 启动库存服务
java -jar singularity-stock/target/singularity-stock-1.0-SNAPSHOT.jar

# 启动订单服务
java -jar singularity-order/target/singularity-order-1.0-SNAPSHOT.jar
```

### 3. 验证服务注册

访问 Nacos 控制台 → 服务管理 → 服务列表，确认以下服务已注册：

- `singularity-order`
- `singularity-user`
- `singularity-stock`

---

### 4. 初始化库存（Redis 预热）

秒杀扣减走的是 **Redis bucket 库存**，而非数据库 stock 表。启动后必须预热：

```bash
# bucket-1
curl -X POST http://localhost:8082/api/stock/slots/preheat \
  -H "Content-Type: application/json" \
  -d '{"slotId":"bucket-1","quantity":100,"overwrite":true}'

# bucket-2
curl -X POST http://localhost:8082/api/stock/slots/preheat \
  -H "Content-Type: application/json" \
  -d '{"slotId":"bucket-2","quantity":100,"overwrite":true}'
```

---

### 5. 测试账户与数据

#### 4.1 预置测试账户

启动后可直接使用以下账户登录（没有的话自己创建，建议如下）：

| 角色 | 用户名 | 密码 | 用途 |
|---|---|---|---|
| 普通用户 | `user` | `12345678` | 测试秒杀、用户中心 |
| 管理员 | `admin` | `admin123456` | 测试管理后台 |

> Admin 账户通过 `/register` 注册后，需在数据库手动改 role：
> ```sql
> UPDATE singularity_user.user SET role = 'admin' WHERE username = 'admin';
> ```

#### 4.2 预置测试商品（库存）

```sql
USE singularity_stock;
INSERT INTO stock (product_id, available_quantity, reserved_quantity, total_quantity) VALUES
('PROD_001', 100, 0, 100),
('PROD_002', 50, 0, 50),
('PROD_003', 10, 0, 10),
('PROD_004', 30, 0, 30);
```

> 注意：`singularity-order.yaml` 的 slot 配置需包含 `product-id`，并与库存表中的 `product_id` 一一对应。

#### 4.3 前端开发环境

```bash
cd singularity-front
pnpm install
pnpm dev
```

Vite 代理已配置（`vite.config.ts`）：
- `/api/user` → `localhost:8090`
- `/api/order` → `localhost:8081`
- `/api/stock` → `localhost:8082`

---

## 已知后端限制

### 1. 订单状态始终为 "CREATED"（处理中）

**现象**：抢单成功后，订单状态一直显示"处理中"，不会变为"成功"或"失败"。

**根因**：`OrderConsumerService` 消费 `order-topic` 消息并将订单落库时，硬编码 `status = "CREATED"`（`singularity-order/.../consumer/OrderConsumerService.java:77`）。当前架构缺少将订单状态推进为 `PAID` 或 `CANCELLED` 的后续机制（没有状态机或二次 MQ 消费来更新订单）。

**影响范围**：前端订单列表、抢单状态轮询均显示"处理中"。

### 2. 订单状态类型与 API 契约不一致

API 契约（`docs/frontend/03-frontend-api-contracts.md`）规定 `status` 为 number（0=处理中, 1=成功, 2=失败），但后端实际返回字符串 `"CREATED"`。前端已做兼容处理：将 `"CREATED"` 视为处理中。
