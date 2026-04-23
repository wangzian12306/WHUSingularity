# WHUSingularity

高并发抢单系统框架 + Spring Cloud 微服务实践

## 项目结构

```
WHUSingularity/
├── singularity-core/      # 核心高并发框架
├── singularity-order/     # 订单服务 - 高并发抢单 (端口: 8081)
├── singularity-user/      # 用户服务 - 登录注册 (端口: 8090)
├── singularity-stock/     # 库存服务 - 库存管理 (端口: 8082)
└── docs/                  # 项目文档
    ├── database/          # 数据库初始化脚本
    └── nacos/            # Nacos 配置指南
```

## 技术栈

- **框架**: Spring Boot 4.0.3 + Spring Cloud 2025.0.0
- **服务发现/配置中心**: Alibaba Nacos 2.x
- **服务调用**: OpenFeign
- **消息队列**: RocketMQ
- **缓存**: Redis + Caffeine（本地缓存）
- **数据库**: MySQL
- **ORM**: MyBatis
- **数据库迁移**: Flyway

## 环境要求

1. **JDK**: 21+
2. **MySQL**: 8.0+
3. **Redis**: 6.0+
4. **RocketMQ**: 4.9+
5. **Nacos**: 2.4+

## 用户服务 API

### 注册

```http
POST /api/user/register
Content-Type: application/json

{
  "username": "test",
  "password": "123456",
  "nickname": "测试用户"
}
```

### 登录

```http
POST /api/user/login
Content-Type: application/json

{
  "username": "test",
  "password": "123456"
}
```

### 查询用户

```http
GET /api/user/{id}
```

---

## 订单服务功能

### 抢单接口

订单服务提供高并发抢单功能，使用 RocketMQ 事务消息保证：

1. Redis 原子减库存
2. 订单数据写入 MySQL（通过消息队列异步落库）
3. 库存售罄时自动标记 Slot 为空，避免无效请求

### 消息消费

订单服务监听 `order-topic`，直接消费订单 JSON 消息落库到 MySQL，保证幂等性。

---

## Spring Cloud Nacos 接入要点

### 1. 服务注册与发现

- 启动类添加 `@EnableDiscoveryClient`
- 创建 `bootstrap.yml` 配置 Nacos 服务地址
- 所有服务自动注册到 Nacos

### 2. 配置中心

- 业务配置（Redis、RocketMQ、Slot 等）从 Nacos 动态获取
- 支持配置热更新，无需重启服务
- 配置优先级：Nacos > application.yml

### 3. 服务间调用

- 使用 `@FeignClient` 注解声明客户端
- 启动类添加 `@EnableFeignClients`
- 注入 FeignClient 直接调用其他服务

---

## 数据库迁移

项目使用 Flyway 管理数据库版本：

- **order 服务**：自动执行 `db/migration/V1__Create_Order_Tables.sql`
- **stock 服务**：自动执行 `db/migration/V1__Create_Stock_Tables.sql`

首次启动时 Flyway 会自动创建表结构。

---

## 配置说明

### 环境变量

以下环境变量可在启动时配置：

```bash
# 数据库用户名和密码
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=your_password

# JWT 密钥（生产环境必须修改）
export JWT_SECRET=your_secret_key
```

### 本地开发配置

本地开发时可直接使用默认配置（root/root），无需设置环境变量。

---

## 项目特点

1. **高并发抢单框架**：基于 Actor-Slot 模型，支持分槽路由和库存隔离
2. **分布式事务**：使用 RocketMQ 事务消息保证订单创建的一致性
3. **多级缓存**：Redis + Caffeine 双层缓存，减少数据库压力
4. **服务治理**：基于 Nacos 的服务注册/发现和配置管理
5. **数据库版本管理**：Flyway 自动化数据库迁移

---

## 文档

- [Nacos 配置指南](docs/nacos/README.md)
- [数据库初始化脚本](docs/database/)

---

## 开发者

Lubover - 武汉大学学生项目

---

## License

MIT License
