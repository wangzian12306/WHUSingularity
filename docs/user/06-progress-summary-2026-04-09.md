# 用户模块阶段进展总结（2026-04-09）

## 1. 目标与范围

本阶段目标是让 `singularity-user` 达到 A 档鉴权闭环：

1. 注册与登录契约收口，响应脱敏。
2. 密码安全存储与认证。
3. JWT 会话签发、鉴权校验、退出失效。
4. 错误语义统一（400/401/403/409）。

范围说明：

1. 本阶段主要落在 `singularity-user`。
2. `singularity-order` 仅保留对接约束，不在本阶段内完成启动与鉴权接入。

## 2. 本阶段已完成事项

### 2.1 契约与响应模型

1. `register/login` 已改为统一响应结构（`success/data/error`）。
2. 用户对外模型已脱敏，仅返回 `id/username/nickname/role`。
3. 认证与参数错误码已统一映射到业务约定。

### 2.2 安全与会话

1. 密码存储由明文改为 BCrypt 哈希。
2. 登录认证使用 `PasswordEncoder.matches`。
3. JWT 已实现签发与校验，包含 `sub/role/jti/iat/exp`。
4. 配置项已落地：
   - `auth.jwt.secret`
   - `auth.jwt.expire-seconds`
   - `auth.blacklist.prefix`

### 2.3 鉴权入口与会话闭环

1. 已落地 `AuthFilter`：
   - Bearer 头校验
   - token 解析/验签
   - 过期语义处理
   - 黑名单校验
   - 请求上下文注入（`userId/role/jti/exp`）
2. 已实现 `POST /api/user/logout`：写入黑名单并支持幂等。
3. 已实现 `GET /api/user/me`：返回当前登录用户公开信息。
4. 已实现 admin 受保护端点示例 `GET /api/user/admin/ping`，用于 `403 AUTH_FORBIDDEN` 验收。

## 3. 测试与验收状态

## 3.1 自动化测试

当前 `singularity-user` 已建立测试基线并覆盖正向与负向路径：

1. Controller 契约测试：注册/登录响应、错误码、脱敏检查。
2. Service 安全测试：哈希存储、错误凭证、参数校验、并发冲突映射。
3. JWT 测试：claim 完整性、篡改无效、过期失效。
4. 鉴权流测试：missing/invalid/expired/blacklist/forbidden。

结果：`mvn -pl singularity-user test` 通过（30 tests, 0 failures）。

最终回归（仓库级）：`mvn clean package` 通过（root + core + eureka + user + order 构建成功）。

## 3.3 当前执行状态（标准化重构计划）

1. Step 1（契约冻结与回归护栏加固）已完成。
2. Step 2（JWT 标准化替换）已完成：由自定义 JWT 组装/验签切换为 Spring Security JOSE 编解码组件，并保持现有错误语义不变。
3. Step 3（鉴权链标准化替换）已完成一轮收口：
   - 引入统一鉴权上下文对象 `AuthRequestContext`，减少分散 request attribute 读取。
   - Filter 错误码改为复用 `ErrorCode`，避免字面量漂移。
   - 对 `sub/role/jti/exp` 做上下文构建时校验，异常输入统一收敛为 401 `AUTH_TOKEN_INVALID`。
4. 已补充并通过对应回归：`mvn -pl singularity-user test`（30 tests, 0 failures）。

## 3.4 最终回归与冒烟（2026-04-09）

1. 回归结果：`mvn clean package` 全仓通过。
2. 冒烟结果：
   - `singularity-eureka` 启动成功（8761）。
   - `singularity-user` 启动成功（8090）并成功注册到 Eureka。
   - HTTP 快速探测：`GET /`（8761）返回 200；`GET /api/user/me` 与 `GET /api/user/admin/ping`（无 token）均返回 401，符合鉴权预期。
   - `singularity-order` 启动成功（8081）并成功注册到 Eureka；`GET /`（8081）返回 404（无根路径映射，容器可用）。
3. 本轮回归中发现并修复：
   - `application.yml` 中 `auth.blacklist.prefix` 的 YAML 语法问题（已改为带引号字符串）。
   - `JwtProvider` 在存在测试构造函数时的 Spring 构造器注入歧义（已显式标注运行时构造器注入）。
   - `singularity-order` 的 `Registry` 运行时装配缺口（已补齐 `Registry`/`ShardPolicy` Bean）。

## 3.5 本轮复核补充（2026-04-09）

1. 已再次执行 `mvn -pl singularity-user test`，结果通过（30 tests, 0 failures）。
2. 当前测试链路存在两类工程治理告警（不影响本轮通过结论）：
   - 多模块 `spring-boot-maven-plugin` 未显式声明版本（Maven model warning）。
   - Mockito 仍采用动态 agent 自附加方式，未来 JDK 版本可能默认收紧。

## 3.2 场景矩阵 S01-S12

1. S01-S09、S11、S12 在 user 模块范围内已完成验证。
2. S10（冒充他人下单）属于跨服务场景，需 order 鉴权入口接入后做端到端验证。

## 4. 与其他模块的交互关系

### 4.1 与 eureka

1. user 作为服务注册到 eureka。
2. 服务发现配置在 `application.yml` 生效。

### 4.2 与 order

1. order 当前通过 Feign 调用 user 的 `GET /api/user/{id}`。
2. order 当前已恢复可启动状态，运行时 `Registry/ShardPolicy` 装配已补齐。
3. 跨模块 token 透传与 S10 仍待在 order 抢单入口补齐鉴权语义后完成端到端验收。

### 4.3 与 Redis/MySQL

1. MySQL 用于用户主数据与密码哈希。
2. Redis 用于会话黑名单（`auth:blacklist:{jti}`，TTL=`exp-now`）。

## 5. 当前风险与差距

1. user 部分历史 CRUD 接口仍可能存在实体直出风险，需进一步 DTO 化。
2. 跨服务鉴权一致性（order 侧）尚未完成，导致全链路验收缺口。
3. 生产环境密钥管理仍需结合部署体系（环境变量/配置中心）完成加固。
4. 构建配置治理未完全收口：`spring-boot-maven-plugin` 版本声明缺失目前为 warning，后续 Maven 版本可能升级为阻断。
5. 测试运行治理未完全收口：Mockito 动态 agent 告警提示后续 JDK 兼容风险，需提前固化测试运行参数。

## 6. 下一步建议

## 6.1 本模块（user）

1. 完成历史接口统一脱敏改造，彻底避免 `password` 出口。
2. 为鉴权/黑名单路径补充更多边界测试（例如 Redis 异常策略）。
3. 增加登录/鉴权关键指标埋点与日志规范。

## 6.2 跨模块（user + order）

1. order 启动阻断已修复（`Registry/ShardPolicy` 运行时装配已补齐）。
2. 在 order 接入与 user 对齐的 Auth Layer 语义（401/403、身份注入）。
3. 完成 S10 端到端联调与验收闭环。

## 6.3 工程治理（构建与测试）

1. 在父/子 POM 中显式收口 `spring-boot-maven-plugin` 版本，消除跨模块模型告警。
2. 按 Mockito 官方建议为测试运行补充 agent 配置或 JVM 参数，避免未来 JDK 默认策略变更导致测试不稳定。

## 7. 当前结论

1. `singularity-user` 已完成 A 档核心闭环能力，可单模块稳定运行与验收。
2. 全项目当前已可完成三服务启动与基础冒烟，最终业务安全闭环仍取决于 order 侧鉴权入口接入与 S10 联调。
