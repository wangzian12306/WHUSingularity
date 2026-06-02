# 用户系统鉴权设计（A档）- 场景与验收

> 版本：v1.0
> 日期：2026-04-09

## 1. 场景目标

本文件用于覆盖“注册、登录、抢单、退出”全链路下的常见与边界场景，作为联调与验收清单。

## 2. 核心场景流程

## 2.1 新用户注册后抢单

1. 用户调用 `register` 成功。
2. 用户调用 `login` 获得 token。
3. 前端携带 token 调用订单服务抢单接口。
4. 请求先进入 order 的 Auth Layer，完成 token/黑名单校验并注入身份。
5. 鉴权通过后进入抢单业务。

**期望**：
- 账号创建成功；
- 登录返回 token；
- 抢单请求进入业务层（非鉴权失败）。

## 2.2 已登录用户抢单

1. 使用有效 token 直接调用抢单。
2. Auth Layer 解析 `sub/role` 并注入上下文。

**期望**：
- 不需要额外用户查询即可完成身份识别；
- 请求可正常进入库存/MQ链路。

## 2.3 登录后退出

1. 用户调用 `logout`。
2. token `jti` 进入黑名单。
3. 使用同一 token 再次请求抢单。

**期望**：
- logout 成功；
- 旧 token 再次访问返回 401。

---

## 3. 场景测试矩阵（常见 + 边界）

| ID | 场景 | 前置条件 | 操作 | 期望 |
|---|---|---|---|---|
| S01 | 注册成功 | 用户名未被占用 | 调 `register` | 201，返回 `id/username/nickname/role` |
| S02 | 重复注册 | 用户名已存在 | 同用户名再次 `register` | 409 `USER_USERNAME_EXISTS` |
| S03 | 并发重复注册 | 两个并发请求同用户名 | 并发调 `register` | 一成功一失败（409） |
| S04 | 登录成功 | 账号已存在，密码正确 | 调 `login` | 200，返回 token 与用户公开信息 |
| S05 | 登录失败-密码错误 | 账号已存在 | 错误密码调 `login` | 401 `AUTH_BAD_CREDENTIALS` |
| S06 | 未登录抢单 | 无 token | 调抢单接口 | 401 `AUTH_TOKEN_MISSING` |
| S07 | 篡改 token 抢单 | token 被改动 | 调抢单接口 | 401 `AUTH_TOKEN_INVALID` |
| S08 | 过期 token 抢单 | token 已超时 | 调抢单接口 | 401 `AUTH_TOKEN_EXPIRED` |
| S09 | 退出后重放 | 先 logout | 使用旧 token 调抢单 | 401（黑名单生效） |
| S10 | 冒充他人下单 | 请求体伪造 userId | 带本人 token 调抢单 | 订单身份以 token `sub` 为准 |
| S11 | 重复退出 | 同一 token 已退出一次 | 再次调 logout | 200，幂等成功 |
| S12 | 权限不足访问管理接口 | normal 用户 token | 调 admin 接口 | 403 `AUTH_FORBIDDEN` |

---

## 4. 当前验收进展（截至 2026-04-09）

当前在 `singularity-user` 模块内已完成自动化验收与回归，结果如下：

| ID | 状态 | 说明 |
|---|---|---|
| S01 | 已通过 | register 成功返回 201 且字段脱敏 |
| S02 | 已通过 | 重复用户名返回 409 `USER_USERNAME_EXISTS` |
| S03 | 已通过（模块内） | 已覆盖数据库唯一约束冲突映射，确保并发冲突返回 409 |
| S04 | 已通过 | login 返回 `Bearer` + JWT + 公开用户信息 |
| S05 | 已通过 | 错误密码返回 401 `AUTH_BAD_CREDENTIALS` |
| S06 | 已通过 | 无 token 访问受保护接口返回 401 `AUTH_TOKEN_MISSING` |
| S07 | 已通过 | 非法/篡改 token 返回 401 `AUTH_TOKEN_INVALID` |
| S08 | 已通过 | 过期 token 返回 401 `AUTH_TOKEN_EXPIRED` |
| S09 | 已通过 | logout 后重放受保护接口返回 401（黑名单生效） |
| S10 | 待跨模块联调 | 需 order 服务启动并接入鉴权入口后完成端到端验证 |
| S11 | 已通过 | 重复 logout 仍返回 200 幂等成功 |
| S12 | 已通过 | normal 访问 admin 端点返回 403 `AUTH_FORBIDDEN` |

补充说明：

1. 当前 `singularity-user` 全量测试 24 个用例均通过，包含正向与负向路径。
2. S10 依赖 order 抢单入口鉴权接入，属于跨服务验收，不在 user 单模块内闭环。

---

## 5. 验收标准（Definition of Done）

以下条件同时满足视为 A档完成：

1. 注册、登录、退出、me 接口可用。
2. 用户密码仅哈希存储，无明文。
3. 登录返回 token，且返回体不含密码。
4. user/order 两侧均接入薄 Auth Layer（过滤器/拦截器），鉴权不散落在业务代码。
5. 订单抢单接口身份只取 Auth Layer 注入身份（源自 token）。
6. logout 后旧 token 立即失效。
7. 错误码与 HTTP 状态码符合契约文档。
8. 场景矩阵 S01-S12 通过。

## 6. 回归重点

每次改动以下任一项时，需要至少回归 S04/S06/S09/S10：

1. 登录签发逻辑
2. token 验证逻辑
3. logout 失效逻辑
4. 订单抢单鉴权入口
