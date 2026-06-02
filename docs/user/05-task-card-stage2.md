# 任务卡 v2（阶段2：流程工程化）

## 背景
`singularity-user` 当前已有基础注册/登录接口，但仍是明文密码校验、登录直接返回 `User` 实体（含 `password` 风险）、缺少 `logout/me`、缺少统一 Auth Layer 与 token 黑名单机制。
`docs/user/` 已给出 A 档契约与验收矩阵（S01-S12），本阶段目标是把“能跑”升级为“可验收、可联调、可演进”的流程化实现，且每步可独立验证并可停顿复盘。

## 最终目标
在不引入新依赖的前提下，让 `singularity-user` 达到文档 A 档要求：**注册/登录/退出/me 完整闭环 + JWT 会话 + Redis 黑名单失效 + 统一鉴权语义 + 响应脱敏**。

## 进度更新（截至 2026-04-09）

### 阶段状态

- [x] Step 1 完成：契约收口与响应模型落地
- [x] Step 2 完成：BCrypt + JWT 会话签发
- [x] Step 3 完成：Auth Layer + logout/me + 黑名单失效
- [ ] 跨模块总验收完成（待 order 服务侧联调）

### 已完成结果

1. `register/login/logout/me` 已可用，且主鉴权链路响应脱敏。
2. 密码改为 BCrypt 哈希存储，登录校验使用 `matches`。
3. 已落地 JWT 签发/解析、黑名单 TTL 写入与鉴权校验。
4. 已补充 admin 受保护端点与 `AUTH_FORBIDDEN` 语义校验。
5. `singularity-user` 自动化测试已覆盖正向与负向路径，总计 30 用例通过。

### 仍需推进

1. S10 需在 order 服务接入鉴权入口后完成端到端验证。
2. user 历史 CRUD 接口需继续统一 DTO，收敛实体直出风险。
3. 构建治理仍有缺口：多个模块 `spring-boot-maven-plugin` 未显式声明版本，当前仅告警但会影响后续 Maven 兼容性。
4. 测试环境治理仍有缺口：Mockito 动态 agent 自附加在新 JDK 版本下将逐步收紧，需按官方方式补充测试 JVM 参数或 agent 配置。

### 最终回归与冒烟结论（2026-04-09）

1. 回归：仓库根目录执行 `mvn clean package` 成功。
2. 冒烟：
  - `singularity-eureka` 启动成功；
  - `singularity-user` 启动成功并完成 Eureka 注册；
  - 未带 token 访问 `/api/user/me`、`/api/user/admin/ping` 返回 401，符合预期；
  - `singularity-order` 启动成功并完成 Eureka 注册（8081）。
3. 回归期间修复两处阻断：
  - `auth.blacklist.prefix` YAML 值补充引号，修复配置解析失败；
  - `JwtProvider` 显式标注运行时构造器注入，修复启动时构造器选择失败。
4. 最终补齐 `singularity-order` 运行时装配：新增 `Registry` 与 `ShardPolicy` Bean，消除历史启动阻断。

## 分步计划（有序，每步独立可验收）

### Step 1: 契约收口与响应模型落地（先做“接口语义正确”）
- 产出物:
  - `singularity-user/src/main/java/com/lubover/singularity/user/controller/UserController.java`
  - 新增 DTO/响应类（建议放在 `.../dto` 或 `.../model`，按现有风格最小化）
  - `singularity-user/src/main/java/com/lubover/singularity/user/service/UserService.java`
  - `singularity-user/src/main/java/com/lubover/singularity/user/service/impl/UserServiceImpl.java`
- 验收:
  - `register/login` 不再返回 `User` 实体中的 `password`
  - 错误码/HTTP 语义与文档一致（至少覆盖：`REQ_INVALID_PARAM`、`USER_USERNAME_EXISTS`、`AUTH_BAD_CREDENTIALS`）
  - `login` 响应结构满足：`tokenType/accessToken/expiresIn/user`

### Step 2: 凭证安全与会话签发（先“能发对 token”）
- 产出物:
  - `UserServiceImpl`：BCrypt 哈希存储 + `matches` 校验
  - 新增 JWT 组件（例如 `auth` 包下的 token provider/util）
  - `application.yml`：新增 `auth.jwt.secret`、`auth.jwt.expire-seconds`、`auth.blacklist.prefix`
- 验收:
  - 新注册用户数据库中存储的是哈希（非明文）
  - 登录成功返回可解析 JWT，包含 `sub/role/jti/iat/exp`
  - 错误凭证统一 `401 AUTH_BAD_CREDENTIALS`

### Step 3: Auth Layer + logout/me + 黑名单失效（完成“会话闭环”）
- 产出物:
  - 新增鉴权过滤器/拦截器（Auth Layer）
  - 新增上下文注入能力（将 `userId/role` 注入请求上下文）
  - `UserController`：新增 `POST /logout`、`GET /me`
  - Redis 黑名单读写逻辑（`auth:blacklist:{jti}`，TTL=`exp-now`）
- 验收:
  - 无 token / 非法 token / 过期 token 返回 401 且 code 正确
  - logout 后同 token 访问受保护接口立刻 401
  - 重复 logout 200 幂等
  - `/me` 返回公开字段且不含密码

> 每步完成后暂停，等你确认再进入下一步。
> 阶段 2 最重要练习点：在设计层面提前暴露问题，避免后续代码返工。

## 非目标
- 不改动 `singularity-order` 业务实现（本阶段只保证 user 侧契约可供 order 对接）
- 不跨步骤顺手优化（如缓存重构、全局异常体系大改、数据库结构重命名）
- 不引入新依赖（JWT、BCrypt 优先用 Spring Boot 现有可用依赖体系；若确需新增先确认）

## 参考
- `docs/user/01-overview-and-boundary.md`
- `docs/user/02-api-contracts.md`
- `docs/user/03-auth-session-security.md`
- `docs/user/04-scenarios-and-acceptance.md`
- 现状代码：
  - `singularity-user/src/main/java/com/lubover/singularity/user/controller/UserController.java`
  - `singularity-user/src/main/java/com/lubover/singularity/user/service/impl/UserServiceImpl.java`
  - `singularity-user/src/main/resources/application.yml`

## 自动化验收命令
- 运行环境: 当前项目为 Maven/Java（模板中的 conda 流程不适用本仓库）
- 执行命令格式: `mvn -pl singularity-user -am ...`

每步完成后可执行（建议）：
- Step1:
  - `mvn -pl singularity-user -am compile`
  - （手工接口检查）`register/login` 响应字段与错误码是否匹配契约
- Step2:
  - `mvn -pl singularity-user -am compile`
  - （手工）注册后查库确认密码为哈希；登录返回 JWT claim 满足约定
- Step3:
  - `mvn -pl singularity-user -am compile`
  - （手工）执行 S06/S08/S09/S11 的接口回归

## 成功条件
- 各步骤验收通过（命令退出码 0 + 契约行为符合文档）
- diff 范围聚焦 `singularity-user`（及必要配置文件）
- 核心场景覆盖达到 A 档：注册、登录、logout、me、黑名单失效与错误语义统一

当前判定：

1. `singularity-user` 单模块成功条件已达成。
2. 跨模块成功条件（特别是 S10）待 order 联调完成后闭环。

## 错误处理约定
- 某步失败：先给原因分析与修复方案，等你确认后再修
- 连续两次失败：暂停并列出可能根因，不盲目重试
- 环境/依赖问题：只报告具体报错，不擅自改环境配置
