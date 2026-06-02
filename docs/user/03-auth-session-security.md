# 用户系统鉴权设计（A档）- 会话与安全

> 版本：v1.0
> 日期：2026-04-09

## 1. 安全基线

A档目标是“最小可用 + 不踩明显安全坑”，基线如下：

1. 密码只存哈希，不存明文。
2. 登录后签发 access token（JWT）。
3. 使用薄 Auth Layer 统一承载鉴权，不在业务代码分散校验。
4. 退出后当前 token 立即失效（Redis 黑名单）。
5. 所有业务服务只信任 token 身份。

## 2. 密码策略

## 2.1 存储

- 算法：`BCrypt`（Spring Security `BCryptPasswordEncoder`）。
- 数据库存储：`password_hash`（或沿用 `password` 字段但仅保存哈希值）。
- 禁止：明文、可逆加密、自定义弱哈希。

## 2.2 校验

- 登录使用 `matches(rawPassword, hashedPassword)`。
- 认证失败统一错误：`AUTH_BAD_CREDENTIALS`。

## 2.3 返回约束

- 任何接口禁止返回密码或哈希值。

## 3. Token 设计（JWT）

## 3.1 Header / Algorithm

- A档推荐：`HS256`（单机/小规模微服务最简）
- 共享密钥统一配置在服务环境变量或配置中心，禁止硬编码入仓库。

## 3.2 Claim 约定

| Claim | 含义 |
|---|---|
| `sub` | 用户ID（字符串） |
| `role` | 角色（`normal` / `admin`） |
| `jti` | token 唯一标识（用于退出失效） |
| `iat` | 签发时间 |
| `exp` | 过期时间 |

## 3.3 生命周期

- `access token`：2小时（7200秒）。
- A档不引入 refresh token；过期后重新登录。

## 4. 退出失效机制（黑名单）

## 4.1 写入规则

- key：`auth:blacklist:{jti}`
- value：`1`
- TTL：`exp - now`（秒）

## 4.2 校验规则

请求进入受保护接口时：

1. 校验 token 签名、格式、过期。
2. 提取 `jti` 查 Redis 黑名单。
3. 命中则返回 `401 AUTH_TOKEN_INVALID`（或按项目约定用 `AUTH_TOKEN_REVOKED`）。

## 4.3 幂等

- 重复 logout 不报错，统一成功返回。

## 5. 鉴权与授权规则

## 5.1 Auth Layer 处理顺序（请求进入受保护接口）

1. 读取并校验 `Authorization: Bearer ...`。
2. 解析 JWT，校验签名与 `exp`。
3. 读取 `jti`，检查 Redis 黑名单。
4. 将 `userId(sub)`、`role` 注入请求上下文。
5. 根据接口要求执行角色授权。

## 5.2 鉴权（Authentication）

- 无 token：`401 AUTH_TOKEN_MISSING`
- 非法 token：`401 AUTH_TOKEN_INVALID`
- 过期 token：`401 AUTH_TOKEN_EXPIRED`

## 5.3 授权（Authorization）

- 已认证但 role 不满足：`403 AUTH_FORBIDDEN`
- `admin` 接口（商品管理/库存管理）必须做 role 判断。

## 6. 服务内实现要求

## 6.1 user 服务

1. 注册时哈希密码。
2. 登录时签发 token。
3. 提供 logout（黑名单写入）。
4. 提供 me（当前用户信息）。
5. 对受保护接口挂载 Auth Layer（可复用组件）。

## 6.2 order 服务

1. 所有抢单入口必须先经过 Auth Layer。
2. 身份来源仅 Auth Layer 注入的 token claim，不信任请求体 userId。
3. 将 `userId` 传入后续抢单流程（Redis/MQ链路）。
4. 若后续接入网关，当前 Auth Layer 保留为兜底或灰度。

## 7. 配置项建议

| 配置项 | 示例 | 说明 |
|---|---|---|
| `auth.jwt.secret` | `<env-secret>` | JWT 密钥（环境变量注入） |
| `auth.jwt.expire-seconds` | `7200` | token 过期秒数 |
| `auth.blacklist.prefix` | `auth:blacklist:` | 黑名单前缀 |

## 8. 观测与排障建议（A档最小）

1. 记录登录成功/失败计数。
2. 记录鉴权失败原因分布（missing/invalid/expired）。
3. 记录 logout 黑名单写入失败次数。
4. 日志禁止输出明文密码与完整 token。
