# 前端开发进度

> 更新日期：2026-04-21（Phase 1 完成）

## 1. 当前进展

### 1.1 已完成

| 模块 | 内容 | 关键文件 |
|---|---|---|
| 项目脚手架 | React + TS + Vite + Ant Design + React Router v6 | `package.json`, `vite.config.ts` |
| Axios 客户端 | 请求/响应拦截器、token 注入、401 自动跳转 | `api/client.ts` |
| 类型定义 | User、Login/Register 请求响应、ApiResponse、UserDetail、Stock、Order 等 | `api/types.ts` |
| User API 客户端 | login、register、logout、me、list、update、remove、recharge | `api/user.ts` |
| Stock API 客户端 | getStock、list、init、getChangeLog | `api/stock.ts` |
| Order API 客户端 | snag、list | `api/order.ts` |
| 认证上下文 | AuthContext + 登录态管理 | `contexts/AuthContext.tsx` |
| 路由守卫 | ProtectedRoute，未登录重定向 /login；AdminRoute，非 admin 重定向 / | `components/AuthGuard.tsx`, `components/AdminGuard.tsx` |
| 全局布局 | AppLayout 顶栏（用户名、管理入口、退出）、嵌套路由 | `components/AppLayout.tsx`, `App.tsx` |
| 登录页 | 表单校验、错误提示、登录后跳转 | `pages/Login.tsx` |
| 注册页 | 表单校验（用户名 4-32、密码 8-64）、注册后跳转登录 | `pages/Register.tsx` |
| Admin 用户管理 | 用户列表 Table + 编辑 Modal + 删除 Popconfirm | `pages/admin/AdminUserList.tsx` |
| WHU 主题 | Ant Design ConfigProvider 配色 | `App.tsx` |
| Vite 代理 | `/api/user` → 8090, `/api/order` → 8081, `/api/stock` → 8082 | `vite.config.ts` |

### 1.2 待实现

| 模块 | 内容 | 依赖后端 |
|---|---|---|
| 秒杀主页 `/` | 商品列表、库存轮询、抢单按钮、倒计时、结果反馈 | **是** — order/stock Controller |
| 用户中心 `/user` | 个人信息、余额充值、我的订单、退出登录 | 部分（用户 OK，订单依赖 order 服务） |
| Admin - 库存管理 `/admin/stock` | 库存列表/初始化/变更日志 | **是** — stock Controller |
| Admin - 订单管理 `/admin/orders` | 订单列表/筛选 | **是** — order Controller |

---

## 2. 后端阻塞分析

前端秒杀主页和用户中心的核心功能依赖后端尚未暴露的 REST Controller（Service 层已有实现）：

- **Order Service**：`POST /api/order/snag`、`GET /api/order/list`
- **Stock Service**：`GET /api/stock/{productId}`、`GET /api/stock/list`、`POST /api/stock/init`、`GET /api/stock/change-log`

---

## 3. 任务规划

### Phase 1 — 无后端阻塞 ✅ 已完成

| # | 任务 | 状态 |
|---|---|---|
| 1 | **全局布局**（导航栏 + 页面容器） | ✅ |
| 2 | **Admin 用户管理页** `/admin/users` | ✅ |
| 3 | **Stock + Order API 客户端** `api/stock.ts` / `api/order.ts` | ✅ |

### Phase 2 — 需后端 Controller 就绪

| # | 任务 | 理由 |
|---|---|---|
| 4 | **秒杀主页** `/` | 核心业务页面：商品列表、库存轮询（3s）、抢单按钮防重复、倒计时、结果反馈。依赖 order + stock 接口 |
| 5 | **用户中心** `/user` | 个人信息展示（已可用）+ 余额充值（已可用）+ 我的订单列表（依赖 order 服务） |

### Phase 3 — 管理 + 增强（低优先级）

| # | 任务 | 理由 |
|---|---|---|
| 6 | **Admin 库存管理** `/admin/stock` | 库存列表、初始化、变更日志 |
| 7 | **Admin 订单管理** `/admin/orders` | 全部订单查看、筛选 |
| 8 | **WebMCP 集成** | 增强能力，基础页面稳定后接入（详见 [02-frontend-tech-stack.md §7](02-frontend-tech-stack.md)） |
