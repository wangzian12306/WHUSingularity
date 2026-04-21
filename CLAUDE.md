# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

WHUSingularity — 高并发抢单（秒杀）系统框架 + Spring Cloud 微服务实践。核心是 `singularity-core` 自定义的高并发资源分配框架，上层业务为电商秒杀场景。

## Tech Stack

- Java 21, Spring Boot 4.0.3, Spring Cloud 2025.0.0
- Netflix Eureka (服务注册), OpenFeign (服务间调用)
- MyBatis 3.0.4, MySQL, Redis, RocketMQ 2.3.1
- Flyway (stock 服务数据库迁移)

## Build & Run

```bash
mvn clean package              # 构建全部
mvn -pl singularity-user package  # 构建单个模块
mvn -pl singularity-user test     # 测试单个模块
mvn test                         # 测试全部
```

**启动顺序**: eureka (8761) → user (8090) → order (8081) → stock (8082)

**基础设施依赖**: MySQL 3306, Redis 6379, RocketMQ NameServer 9876 + Broker 10911

**注意**: `singularity-stock` 有独立 pom.xml 但尚未加入父 pom 的 modules 声明。

## Architecture

```
singularity-core/    — 核心框架：Allocator/Actor/Slot/Interceptor/ShardPolicy/Registry
singularity-eureka/  — 服务注册中心
singularity-user/    — 用户服务：注册/登录/JWT认证/余额管理 (8090)
singularity-order/   — 订单服务：高并发抢单，依赖 core 框架 (8081)
singularity-stock/   — 库存服务：库存管理，MQ 驱动，Flyway 迁移 (8082)
```

### Core Framework (singularity-core)

`Allocator.allocate(Actor)` 的内部流程：
1. Registry 获取可用 slot 列表
2. ShardPolicy 根据 metadata 做 Actor→Slot 分流
3. 执行 Interceptor 链（around 语义，transactional）
4. 调用业务 handler

业务 handler 内使用 RocketMQ half message + Redis 原子减库存的分布式事务模式。

### Service Communication

- **同步**: OpenFeign（如 Order 调用 User 验证用户）
- **异步**: RocketMQ（如 Stock 消费库存更新消息）
- **认证**: JWT + Redis token blacklist，无状态跨服务

### API Pattern

统一 JSON 响应格式 `success/data/error`，错误码体系（如 `AUTH_TOKEN_INVALID`、`USER_USERNAME_EXISTS`）。公共端点：register、login；其余需 `Authorization: Bearer <JWT>`。

## Workflow

- **直接在 main 分支上开发**，不创建 feature 分支。团队通过 PR 协作，main 上开发即可。

## Documentation

`docs/` 目录包含各服务的设计文档、API 契约、场景验收和进度记录，是理解业务细节的主要参考。

### 文档更新约定

完成一个 feature 或阶段性进展后，自动更新相关文档：
- `docs/frontend/progress.md` — 更新已完成/待实现状态
- `docs/frontend/0421-phase1-task-cards.md` 等任务卡 — 标记已完成步骤和状态
- 不要创建新的文档文件，只更新已有文档
