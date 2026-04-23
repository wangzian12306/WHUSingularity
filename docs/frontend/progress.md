# 前端开发进度

> 更新日期：2026-04-22（Phase 2/3 任务卡已制定）
>
> 详细任务卡见 [`0422-phase2-phase3-task-cards.md`](0422-phase2-phase3-task-cards.md)

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
| 秒杀主页 `/` | 商品 Card 网格、3s 库存轮询、抢单按钮防重复、订单结果轮询展示 | `pages/Home.tsx` |
| 用户中心 `/user` | 用户信息卡片、余额充值 Modal、订单列表分页 Table | `pages/UserCenter.tsx` |
| Admin 库存管理 `/admin/stock` | 库存列表 Table、初始化 Modal、变更日志 Modal | `pages/admin/AdminStockList.tsx` |
| Admin 订单管理 `/admin/orders` | 全部订单列表 Table、userId + status 筛选、分页 | `pages/admin/AdminOrderList.tsx` |
| WHU 主题 | Ant Design ConfigProvider 配色 | `App.tsx` |
| WebMCP 集成 | `@mcp-b/webmcp-polyfill` 初始化，4 个业务 tools 注册 | `webmcp/tools.ts`, `main.tsx`, `Home.tsx` |
| Vite 代理 | `/api/user` → 8090, `/api/order` → 8081, `/api/stock` → 8082 | `vite.config.ts` |

### 1.2 待实现

无

---

## 2. 后端阻塞分析

后端所需 REST Controller 已全部就绪：

- **Order Service**：`GET /api/order/list` ✅（支持 `actorId`/`status` 筛选 + 分页）
- **Stock Service**：`GET /api/stock/{productId}` ✅、`GET /api/stock/list` ✅、`POST /api/stock/init` ✅、`GET /api/stock/change-log` ✅

---

## 3. 任务规划

### Phase 1 — 无后端阻塞 ✅ 已完成

| # | 任务 | 状态 |
|---|---|---|
| 1 | **全局布局**（导航栏 + 页面容器） | ✅ |
| 2 | **Admin 用户管理页** `/admin/users` | ✅ |
| 3 | **Stock + Order API 客户端** `api/stock.ts` / `api/order.ts` | ✅ |

### Phase 2 — 后端接口已就绪，可继续前端页面开发

| # | 任务 | 状态 |
|---|---|---|
| 4 | **秒杀主页** `/` | 已完成 |
| 5 | **用户中心** `/user` | 已完成 |

### Phase 3 — 管理 + 增强（低优先级，后端接口已就绪）

| # | 任务 | 状态 |
|---|---|---|
| 6 | **Admin 库存管理** `/admin/stock` | 已完成 |
| 7 | **Admin 订单管理** `/admin/orders` | 已完成 |
| 8 | **WebMCP 集成** | 已完成 |

---

## 4. 测试验收结果（2026-04-22）

### Task 4 — 秒杀主页 `/`

| 验收项 | 结果 | 备注 |
|---|---|---|
| 商品列表正确展示 | 通过 | 3s 轮询刷新正常 |
| 抢单按钮 loading 态 + 防重复 | 通过 | 快速连击只发一次请求 |
| 抢单结果反馈 | 通过 | 成功/失败提示明确 |
| 订单状态轮询 | 通过 | 2s 轮询正常触发 |
| **已知限制** | — | 订单状态始终为 `CREATED`（后端缺状态更新机制），见 `docs/startup.md` |

### Task 5 — 用户中心 `/user`

| 验收项 | 结果 | 备注 |
|---|---|---|
| 用户中心入口可见 | 通过 | AppLayout 顶栏已添加 |
| 用户信息/余额展示 | 通过 | 数据正确 |
| 充值 Modal | 通过 | 金额输入 + API 调用正常 |
| 订单列表分页 | 通过 | 已修复 0/1 起始页码偏移 |

### Task 6 — Admin 库存管理 `/admin/stock`

| 验收项 | 结果 | 备注 |
|---|---|---|
| 库存列表展示 | 通过 | 四列数据正确 |
| 初始化库存 | 通过 | 新记录刷新正常，重复初始化报 `STOCK_ALREADY_EXISTS` |
| 变更日志 | 通过 | 按 `productId` 过滤展示 |
| **已知限制** | — | 无（抢单后库存已通过 `order-topic` 异步扣减并落库） |

### Task 7 — Admin 订单管理 `/admin/orders`

| 验收项 | 结果 | 备注 |
|---|---|---|
| 全部订单列表 | 通过 | 不分用户展示全部订单 |
| 分页 | 通过 | 已修复 0/1 起始页码偏移 |
| userId 筛选 | 通过 | 输入后结果正确过滤 |
| status 筛选 | 通过 | 下拉选择后结果正确过滤 |
| 组合筛选 + 重置 | 通过 | 同时生效，重置恢复全部 |

### Task 8 — WebMCP 集成

| 验收项 | 结果 | 备注 |
|---|---|---|
| polyfill 加载 | 通过 | 无报错 |
| 4 个 tools 注册 | 通过 | `listOrders`、`getUserInfo`、`listStock`、`snagOrder` |
| 业务 tool 调用 | 通过 | `listStock` 端到端调用成功并返回数据 |
| 常规 UI 不受影响 | 通过 | — |
