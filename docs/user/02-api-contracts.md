# 用户系统鉴权设计（A档）- API 契约

> 版本：v1.0
> 日期：2026-04-09

## 1. 通用约定

## 1.1 Content-Type

- 请求与响应统一使用 `application/json`。

## 1.2 响应结构

### 成功

```json
{
  "success": true,
  "data": {}
}
```

### 失败

```json
{
  "success": false,
  "error": {
    "code": "AUTH_TOKEN_INVALID",
    "message": "token invalid"
  }
}
```

## 1.3 认证头

受保护接口需要请求头：

```
Authorization: Bearer <access_token>
```

---

## 2. 用户服务接口

## 2.1 注册

- **Method**: `POST`
- **Path**: `/api/user/register`
- **Auth**: 否

### 请求体

```json
{
  "username": "alice01",
  "password": "P@ssw0rd!",
  "nickname": "Alice"
}
```

### 字段约束

- `username`: 4-32，字母/数字/下划线
- `password`: 8-64
- `nickname`: 1-32，可选（为空时后端可回退为 `username`）

### 成功响应（201）

```json
{
  "success": true,
  "data": {
    "id": 1001,
    "username": "alice01",
    "nickname": "Alice",
    "role": "normal"
  }
}
```

### 失败响应

- `400 REQ_INVALID_PARAM`
- `409 USER_USERNAME_EXISTS`

---

## 2.2 登录

- **Method**: `POST`
- **Path**: `/api/user/login`
- **Auth**: 否

### 请求体

```json
{
  "username": "alice01",
  "password": "P@ssw0rd!"
}
```

### 成功响应（200）

```json
{
  "success": true,
  "data": {
    "tokenType": "Bearer",
    "accessToken": "<jwt>",
    "expiresIn": 7200,
    "user": {
      "id": 1001,
      "username": "alice01",
      "nickname": "Alice",
      "role": "normal"
    }
  }
}
```

### 失败响应

- `400 REQ_INVALID_PARAM`
- `401 AUTH_BAD_CREDENTIALS`

> 说明：用户名不存在与密码错误统一返回 `AUTH_BAD_CREDENTIALS`。

---

## 2.3 退出登录

- **Method**: `POST`
- **Path**: `/api/user/logout`
- **Auth**: 是

### 请求头

```
Authorization: Bearer <jwt>
```

### 成功响应（200）

```json
{
  "success": true,
  "message": "logged out"
}
```

### 失败响应

- `401 AUTH_TOKEN_MISSING`
- `401 AUTH_TOKEN_INVALID`
- `401 AUTH_TOKEN_EXPIRED`

> 幂等要求：重复调用 logout 仍返回成功。

---

## 2.4 获取当前登录用户

- **Method**: `GET`
- **Path**: `/api/user/me`
- **Auth**: 是

### 成功响应（200）

```json
{
  "success": true,
  "data": {
    "id": 1001,
    "username": "alice01",
    "nickname": "Alice",
    "role": "normal"
  }
}
```

### 失败响应

- `401 AUTH_TOKEN_MISSING`
- `401 AUTH_TOKEN_INVALID`
- `401 AUTH_TOKEN_EXPIRED`

---

## 3. 订单服务（抢单）鉴权契约

> 本文不规定订单服务具体路由，只规定鉴权语义。

1. 抢单接口必须要求 `Bearer token`。
2. 请求先经过 order 的 Auth Layer，再进入业务 controller。
3. 订单服务只以 Auth Layer 从 token 解析出的 `userId` 作为下单身份。
4. 若请求体包含 `userId`，必须忽略或校验一致性后仍以 token 为准。
5. 鉴权失败统一返回 401；权限不足返回 403。

---

## 4.1 Auth Layer 错误映射

| 场景 | HTTP | code |
|---|---|---|
| 缺少 `Authorization` 头 | 401 | `AUTH_TOKEN_MISSING` |
| token 格式或签名非法 | 401 | `AUTH_TOKEN_INVALID` |
| token 已过期 | 401 | `AUTH_TOKEN_EXPIRED` |
| token 命中黑名单（已退出） | 401 | `AUTH_TOKEN_INVALID`（或 `AUTH_TOKEN_REVOKED`） |
| 角色不满足接口要求 | 403 | `AUTH_FORBIDDEN` |

## 4.2 业务错误映射

| 场景 | HTTP | code |
|---|---|---|
| 参数缺失或格式错误 | 400 | `REQ_INVALID_PARAM` |
| 登录凭证错误 | 401 | `AUTH_BAD_CREDENTIALS` |
| 用户名冲突 | 409 | `USER_USERNAME_EXISTS` |

---

## 5. DTO 约束

1. `User` 实体对象禁止直接作为登录/注册响应。
2. 任意对外响应禁止出现 `password` 字段。
3. 对外只返回必要字段：`id/username/nickname/role`。
