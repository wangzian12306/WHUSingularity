# 前端 API 契约

> 版本：v1.2
> 日期：2026-05-07
> 说明：前端需要消费的所有接口定义。标注"已就绪"的接口后端已实现；标注"待实现"的接口后端需按此契约开发。如果后端有调整，或者实际实现和文档有冲突，在这个文档的最后单开一节标注和说明

## 1. 通用约定

### 1.1 Content-Type

请求与响应统一使用 `application/json`。

### 1.2 响应结构

**成功**

```json
{
  "success": true,
  "data": {}
}
```

**失败**

```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "错误描述"
  }
}
```

### 1.3 认证头

受保护接口需要请求头：

```
Authorization: Bearer <access_token>
```

### 1.4 错误码总表

| HTTP | code | 含义 |
|---|---|---|
| 400 | `REQ_INVALID_PARAM` | 参数缺失或格式错误 |
| 401 | `AUTH_BAD_CREDENTIALS` | 登录凭证错误 |
| 401 | `AUTH_TOKEN_MISSING` | 缺少 Authorization 头 |
| 401 | `AUTH_TOKEN_INVALID` | token 无效或已失效 |
| 401 | `AUTH_TOKEN_EXPIRED` | token 已过期 |
| 403 | `AUTH_FORBIDDEN` | 权限不足 |
| 409 | `USER_USERNAME_EXISTS` | 用户名已存在 |

### 1.5 服务端口

| 服务 | 端口 | API 前缀 |
|---|---|---|
| singularity-user | 8090 | `/api/user` |
| singularity-order | 8081 | `/api/order` |
| singularity-stock | 8082 | `/api/stock` |
| singularity-product | 8087 | `/api/product` |
| singularity-merchant | 8091 | `/api/merchant` |
| singularity-gateway | 8080 | `/api/*` |
| singularity-scaler | 9090 | `/api/scaler` |

---

## 2. 用户服务

### 2.1 注册

- **Method**: `POST`
- **Path**: `/api/user/register`
- **Auth**: 否
- **状态**: 已就绪

**请求体**

```json
{
  "username": "alice01",
  "password": "P@ssw0rd!",
  "nickname": "Alice"
}
```

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| username | string | 是 | 4-32 字符，字母/数字/下划线 |
| password | string | 是 | 8-64 字符 |
| nickname | string | 否 | 1-32 字符 |

**成功响应 201**

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

**失败响应**

- `400` `REQ_INVALID_PARAM`
- `409` `USER_USERNAME_EXISTS`

---

### 2.2 登录

- **Method**: `POST`
- **Path**: `/api/user/login`
- **Auth**: 否
- **状态**: 已就绪

**请求体**

```json
{
  "username": "alice01",
  "password": "P@ssw0rd!"
}
```

**成功响应 200**

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

| 字段 | 类型 | 说明 |
|---|---|---|
| tokenType | string | 固定 `"Bearer"` |
| accessToken | string | JWT token |
| expiresIn | number | 过期时间（秒） |
| user | object | 当前登录用户信息 |

**失败响应**

- `400` `REQ_INVALID_PARAM`
- `401` `AUTH_BAD_CREDENTIALS`

> 用户名不存在与密码错误统一返回 `AUTH_BAD_CREDENTIALS`。

---

### 2.3 退出登录

- **Method**: `POST`
- **Path**: `/api/user/logout`
- **Auth**: 是
- **状态**: 已就绪

**请求头**

```
Authorization: Bearer <jwt>
```

**成功响应 200**

```json
{
  "success": true,
  "message": "logged out"
}
```

**失败响应**

- `401` `AUTH_TOKEN_MISSING` / `AUTH_TOKEN_INVALID` / `AUTH_TOKEN_EXPIRED`

> 幂等：重复调用仍返回成功。

---

### 2.4 获取当前用户

- **Method**: `GET`
- **Path**: `/api/user/me`
- **Auth**: 是
- **状态**: 已就绪

**成功响应 200**

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

**失败响应**

- `401` `AUTH_TOKEN_MISSING` / `AUTH_TOKEN_INVALID` / `AUTH_TOKEN_EXPIRED`

---

### 2.5 充值余额

- **Method**: `POST`
- **Path**: `/api/user/{id}/recharge`
- **Auth**: 否（当前）
- **状态**: 已就绪

**路径参数**

| 参数 | 类型 | 说明 |
|---|---|---|
| id | Long | 用户 ID |

**请求体**

```json
{
  "amount": 100.50
}
```

**成功响应 200**

```json
{
  "success": true,
  "message": "Recharge successful"
}
```

**失败响应**

- `400` `REQ_INVALID_PARAM`（金额必须为正数 / 用户不存在）

---

### 2.6 用户列表（管理）

- **Method**: `GET`
- **Path**: `/api/user/list`
- **Auth**: 否（当前）
- **状态**: 已就绪

**成功响应 200**

```json
{
  "success": true,
  "data": [
    {
      "id": 1001,
      "username": "alice01",
      "nickname": "Alice",
      "role": "normal",
      "balance": 100.50,
      "createTime": "2026-04-20T10:00:00",
      "updateTime": "2026-04-20T10:00:00"
    }
  ]
}
```

> 注意：当前接口返回完整实体，包含密码哈希。前端使用时应忽略该字段，后续后端应改为脱敏 DTO。

---

### 2.7 编辑用户（管理）

- **Method**: `PUT`
- **Path**: `/api/user/{id}`
- **Auth**: 否（当前）
- **状态**: 已就绪

**请求体**

```json
{
  "nickname": "New Nickname",
  "role": "admin",
  "balance": 200.00
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| nickname | string | 否 | 新昵称 |
| role | string | 否 | `normal` 或 `admin` |
| balance | number | 否 | 余额 |
| password | string | 否 | 新密码 |

---

### 2.8 删除用户（管理）

- **Method**: `DELETE`
- **Path**: `/api/user/{id}`
- **Auth**: 否（当前）
- **状态**: 已就绪

**成功响应 200**

```json
{
  "success": true,
  "message": "User deleted successfully"
}
```

---

## 3. 订单服务

### 3.1 抢单

- **Method**: `POST`
- **Path**: `/api/order/snag`
- **Auth**: 是（前端传 userId）
- **状态**: **已就绪**

**请求体**

```json
{
  "userId": "1001"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| userId | string | 是 | 用户 ID |

**成功响应 200**

```json
{
  "success": true,
  "data": {
    "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| orderId | string | 订单 ID（UUID） |

> **注意**：后端实际只返回 `orderId`，不返回 `status` 字段。前端应从订单列表轮询状态。

**失败响应**

> 后端实际返回 `{ "success": false, "message": "<string>" }`，没有 `error.code` 结构。

| HTTP | 含义 |
|---|---|
| 400 | userId 缺失 |
| 401 | 未认证 |
| 409 | 库存不足 |
| 409 | 重复抢单 |

---

### 3.2 查询订单列表

- **Method**: `GET`
- **Path**: `/api/order/list`
- **Auth**: 是
- **状态**: 已就绪

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| actorId | string | 否 | 按用户 ID 过滤（普通用户只能查自己的），后端内部映射为 `userId` |
| status | string | 否 | 按订单状态过滤（字符串，如 `"CREATED"`） |
| page | number | 否 | 页码，默认 0（从 0 开始） |
| size | number | 否 | 每页条数，默认 10 |

**成功响应 200**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "userId": "1001",
        "productId": "PROD_001",
        "slotId": "bucket-1",
        "status": "CREATED",
        "createTime": "2026-04-20T12:00:00",
        "updateTime": "2026-04-20T12:00:00"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "page": 0,
    "size": 10
  }
}
```

**订单状态**

> 后端实际返回字符串类型，非数字。当前仅有 `CREATED`。

| status | 含义 |
|---|---|
| `CREATED` | 已创建（当前唯一状态，后端硬编码） |
| `PAID` | 已支付（预留，后端尚未实现状态流转） |
| `CANCELLED` | 已取消（预留，后端尚未实现状态流转） |

---

## 4. 库存服务

### 4.1 查询商品库存

- **Method**: `GET`
- **Path**: `/api/stock/{productId}`
- **Auth**: 否
- **状态**: 已就绪

**路径参数**

| 参数 | 类型 | 说明 |
|---|---|---|
| productId | string | 商品 ID |

**成功响应 200**

```json
{
  "success": true,
  "data": {
    "productId": "PROD_001",
    "availableQuantity": 50,
    "reservedQuantity": 10,
    "totalQuantity": 100
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| productId | string | 商品 ID |
| availableQuantity | number | 可用库存（用户可见的剩余数量） |
| reservedQuantity | number | 已占用库存 |
| totalQuantity | number | 总库存 |

**失败响应**

- `404` `STOCK_NOT_FOUND` — 商品不存在

---

### 4.2 库存列表

- **Method**: `GET`
- **Path**: `/api/stock/list`
- **Auth**: 否
- **状态**: 已就绪

**成功响应 200**

```json
{
  "success": true,
  "data": [
    {
      "productId": "PROD_001",
      "availableQuantity": 50,
      "reservedQuantity": 10,
      "totalQuantity": 100
    },
    {
      "productId": "PROD_002",
      "availableQuantity": 0,
      "reservedQuantity": 5,
      "totalQuantity": 5
    }
  ]
}
```

---

### 4.3 初始化库存（管理）

- **Method**: `POST`
- **Path**: `/api/stock/init`
- **Auth**: 是（Admin）
- **状态**: 已就绪

**请求体**

```json
{
  "productId": "PROD_001",
  "totalQuantity": 100
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| productId | string | 是 | 商品 ID |
| totalQuantity | number | 是 | 初始库存总量 |

**成功响应 200**

```json
{
  "success": true,
  "message": "Stock initialized"
}
```

**失败响应**

| HTTP | code | 含义 |
|---|---|---|
| 400 | `REQ_INVALID_PARAM` | 数量必须为正数 |
| 409 | `STOCK_ALREADY_EXISTS` | 商品库存已存在 |

---

### 4.4 库存变更日志（管理）

- **Method**: `GET`
- **Path**: `/api/stock/change-log`
- **Auth**: 是（Admin）
- **状态**: 已就绪

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| productId | string | 否 | 按商品过滤 |
| status | number | 否 | 按处理状态过滤 |

**成功响应 200**

```json
{
  "success": true,
  "data": [
    {
      "messageId": "msg-001",
      "productId": "PROD_001",
      "changeQuantity": 1,
      "changeType": 1,
      "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "status": 1,
      "remark": "成功",
      "createTime": "2026-04-20T12:00:00"
    }
  ]
}
```

**changeType**

| 值 | 含义 |
|---|---|
| 1 | 扣库存 |
| 2 | 还库存 |
| 3 | 销售 |

**log status**

| 值 | 含义 |
|---|---|
| 0 | 待处理 |
| 1 | 已处理 |
| 2 | 处理失败 |

---

## 5. 商户服务

> 注：商户 API 客户端已存在于 `src/api/merchant.ts`，当前前端页面未直接消费，供后续商户端页面扩展使用。

### 5.1 商户注册

- **Method**: `POST`
- **Path**: `/api/merchant/register`
- **Auth**: 否
- **状态**: 已就绪

**请求体**

```json
{
  "username": "merchant01",
  "password": "P@ssw0rd!",
  "shopName": "My Shop",
  "contactName": "John",
  "contactPhone": "13800138000",
  "address": "Wuhan",
  "description": "A nice shop"
}
```

**成功响应 201**

```json
{
  "success": true,
  "data": {
    "id": 1,
    "username": "merchant01",
    "shopName": "My Shop",
    "status": 1
  }
}
```

### 5.2 商户登录

- **Method**: `POST`
- **Path**: `/api/merchant/login`
- **Auth**: 否
- **状态**: 已就绪

**成功响应 200**

```json
{
  "success": true,
  "data": {
    "tokenType": "Bearer",
    "accessToken": "<jwt>",
    "expiresIn": 7200,
    "merchant": { ... }
  }
}
```

### 5.3 商户信息查询

- **Method**: `GET`
- **Path**: `/api/merchant/profile`
- **Auth**: 是（Merchant JWT）
- **状态**: 已就绪

### 5.4 商户信息更新

- **Method**: `PUT`
- **Path**: `/api/merchant/profile`
- **Auth**: 是（Merchant JWT）
- **状态**: 已就绪

---

## 6. 接口状态汇总

| 接口 | 状态 |
|---|---|
| `POST /api/user/register` | 已就绪 |
| `POST /api/user/login` | 已就绪 |
| `POST /api/user/logout` | 已就绪 |
| `GET /api/user/me` | 已就绪 |
| `POST /api/user/{id}/recharge` | 已就绪 |
| `GET /api/user/list` | 已就绪 |
| `PUT /api/user/{id}` | 已就绪 |
| `DELETE /api/user/{id}` | 已就绪 |
| `POST /api/order/snag` | 已就绪 |
| `GET /api/order/list` | 已就绪 |
| `GET /api/stock/{productId}` | 已就绪 |
| `GET /api/stock/list` | 已就绪 |
| `POST /api/stock/init` | 已就绪 |
| `GET /api/stock/change-log` | 已就绪 |
| `POST /api/merchant/register` | 已就绪 |
| `POST /api/merchant/login` | 已就绪 |
| `GET /api/merchant/profile` | 已就绪 |
| `PUT /api/merchant/profile` | 已就绪 |

---

## 7. 与后端实现不一致之处

> 本节记录前端契约与后端实际实现的差异，供前端兼容参考。

### 7.1 响应结构

- **契约约定**：失败响应为 `{ "success": false, "error": { "code": "...", "message": "..." } }`
- **后端实际**：失败响应为 `{ "success": false, "message": "..." }`（无 `error` 嵌套，无错误码）

### 7.2 订单状态类型

- **契约约定**：`status` 为 `number`（0/1/2）
- **后端实际**：`status` 为 `string`（`CREATED`/`PAID`/`CANCELLED`），当前仅有 `CREATED`

### 7.3 抢单响应

- **契约约定**：返回 `{ "orderId": "...", "status": 0 }`
- **后端实际**：仅返回 `{ "orderId": "..." }`，不含 `status` 字段

### 7.4 订单列表字段名

- **契约约定**：用户 ID 字段为 `actorId`
- **后端实际**：字段名为 `userId`
- **补充**：订单实体额外包含 `productId` 和 `updateTime` 字段

### 7.5 分页约定

- **契约约定**：`page` 从 1 开始，默认 20 条/页
- **后端实际**：`page` 从 0 开始，默认 10 条/页
