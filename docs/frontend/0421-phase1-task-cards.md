# Phase 1 前端实现计划

> 日期：2026-04-21
> 状态：待确认
> 前置：登录/注册页 + 认证基础设施已完成
> 阻塞：无（本阶段所有任务不依赖 order/stock 后端）

---

## Task 1: 全局布局

### 背景
当前 App.tsx 中各页面直接渲染，无导航结构。登录后用户无法在页面间跳转，后续新页面（秒杀主页、用户中心、管理页）都需要统一的顶栏和容器。这是所有后续页面的骨架，优先实现可避免重复调整。

### 最终目标
登录后所有页面共享顶部导航栏（用户名、用户中心/管理页入口、退出按钮），公开页面（登录/注册）不显示导航栏。

### 分步计划（有序，每步独立可验收）

**Step 1: 创建 AppLayout 组件**
- 产出物: `src/components/AppLayout.tsx`
- 验收: 组件渲染 Ant Design `Layout` + `Header`，`Header` 中显示用户名和退出按钮；从 `useAuth()` 获取 user，提供 `<Outlet />` 嵌套子路由

**Step 2: 重构 App.tsx 路由结构**
- 产出物: 修改 `src/App.tsx`
- 验收: 登录/注册为独立路由（不套 AppLayout）；`/`、`/user`、`/admin/*` 嵌套在 AppLayout 下；移除 Placeholder，保留 `/` 路由占位

**Step 3: 登录/注册页视觉效果调整**
- 产出物: 微调 `src/pages/Login.tsx`、`src/pages/Register.tsx`
- 验收: 登录/注册页全屏居中，不显示 AppLayout 导航栏，视觉与当前一致

### 非目标
- 不做侧边栏（当前页面少，顶栏足矣）
- 不做响应式布局（个人本地开发）
- 不引入新依赖

### 参考
- Ant Design Layout: https://ant.design/components/layout
- AuthContext: `src/contexts/AuthContext.tsx`（提供 `user`, `logout`）
- API 契约: `docs/frontend/03-frontend-api-contracts.md §2.3`（logout 接口）

### 自动化验收命令
- 运行环境: Node.js（pnpm）
- 执行命令格式: `cd singularity-front && pnpm dev`

[Step1: 访问 /login 登录后，跳转 / 页面顶部出现导航栏，显示用户名和退出按钮]
[Step2: 点击退出，清除 token，跳转回 /login，导航栏消失]
[Step3: 访问 /login 和 /register，页面无导航栏，全屏居中卡片]

### 成功条件
- 登录后导航栏可见，包含用户名和退出按钮
- 退出后跳转 /login，导航栏消失
- `/login`、`/register` 页面视觉效果不变
- diff 范围仅涉及 `App.tsx`、新增 `AppLayout.tsx`、`Login.tsx`/`Register.tsx` 微调

### 错误处理约定
- 如某步失败：先分析原因，给出修复方案，等确认后再修
- 如连续两次失败：停下来，列出可能原因，不要继续盲目重试
- 如遇到环境/依赖问题：报告具体报错，不要自行修改环境配置

---

## Task 2: Admin 用户管理页

### 背景
User 服务的 8 个 REST 接口已全部就绪（register/login/logout/me/list/update/delete/recharge），可以独立于 order 和 stock 服务进行前端 CRUD 开发。该页面是管理页的 Table CRUD 范式验证，后续管理页（库存/订单）可复用相同模式。

### Admin 提权说明
注册接口 role 硬编码为 `normal`，无法通过注册成为管理员。第一个 admin 需要手动初始化：

1. 注册一个普通账号
2. 直接修改数据库将该用户的 `role` 字段改为 `admin`：
   ```sql
   UPDATE user SET role = 'admin' WHERE username = '<your_username>';
   ```
3. 重新登录，前端读取 `user.role === 'admin'` 显示管理入口

后续其他用户的提权/降级通过本页面的编辑 Modal 完成。

### 最终目标
`/admin/users` 页面展示用户列表 Table，支持查看、编辑（昵称/角色/余额）、删除，带分页和搜索。

### 分步计划（有序，每步独立可验收）

**Step 1: 补全 user API 客户端**
- 产出物: 修改 `src/api/user.ts`，补充 `list`、`update`、`remove`、`recharge` 方法；修改 `src/api/types.ts` 补充 `UserDetail`（含 balance/createTime）和相关请求/响应类型
- 验收: TypeScript 编译无错误，API 方法签名与契约一致

**Step 2: 创建 AdminUserList 页面**
- 产出物: `src/pages/admin/AdminUserList.tsx`
- 验收: 页面渲染 Ant Design Table，调用 `userApi.list()` 展示用户列表（用户名、昵称、角色、余额、操作列）

**Step 3: 实现编辑和删除功能**
- 产出物: 修改 `src/pages/admin/AdminUserList.tsx`，新增 Modal 表单用于编辑，Popconfirm 用于删除确认
- 验收: 点击编辑弹出 Modal，修改后调用 `userApi.update()` 刷新列表；点击删除弹出确认，确认后调用 `userApi.remove()` 刷新列表

**Step 4: 添加路由和权限入口**
- 产出物: 修改 `App.tsx` 添加 `/admin/users` 路由；修改 `AppLayout.tsx` 在导航栏添加管理页入口（admin 角色可见）
- 验收: admin 用户在导航栏看到管理入口，点击跳转 `/admin/users`；normal 用户看不到入口，手动访问被拦截

### 非目标
- 不做充值功能（充值入口在用户中心，管理页仅编辑余额字段）
- 不做用户搜索/高级筛选（先跑通基础 CRUD）
- 不加 RBAC 中间件（前端简单判断 `user.role === 'admin'`）

### 参考
- API 契约: `docs/frontend/03-frontend-api-contracts.md §2.5-2.8`
- Ant Design Table: https://ant.design/components/table
- Ant Design Modal/Form: https://ant.design/components/modal

### 自动化验收命令
- 运行环境: Node.js（pnpm）+ singularity-user 服务
- 执行命令格式: `cd singularity-front && pnpm dev`

[Step1: `pnpm tsc --noEmit` 通过，无类型错误]
[Step2: 用 admin 账号登录，访问 /admin/users，显示用户列表]
[Step3: 编辑某用户的昵称/角色/余额，保存后列表刷新；删除某用户，确认后列表刷新]
[Step4: normal 角色用户看不到管理入口；admin 角色看到并可访问]

### 成功条件
- 用户列表正确展示后端返回数据
- 编辑/删除操作成功后列表自动刷新
- 路由权限按角色区分
- diff 范围在 `api/`、`pages/admin/`、`App.tsx`、`AppLayout.tsx`

### 错误处理约定
- 如某步失败：先分析原因，给出修复方案，等确认后再修
- 如连续两次失败：停下来，列出可能原因，不要继续盲目重试
- 如遇到环境/依赖问题：报告具体报错，不要自行修改环境配置

---

## Task 3: Stock + Order API 客户端

### 背景
秒杀主页（Phase 2）依赖 order 和 stock 接口。虽然后端 Controller 尚未实现，但 API 契约已在 `03-frontend-api-contracts.md` 中定义完整。提前按契约写好类型和请求方法，后端就绪后可直接联调，不浪费时间。

### 最终目标
`api/stock.ts` 和 `api/order.ts` 提供完整的类型安全的 API 方法，与后端契约一一对应，TypeScript 编译无错误。

### 分步计划（有序，每步独立可验收）

**Step 1: 补充 Stock 相关类型和方法**
- 产出物: 修改 `src/api/types.ts` 新增 `Stock`、`StockChangeLog` 类型；新建 `src/api/stock.ts` 导出 `stockApi`（`getStock`、`list`、`init`、`getChangeLog`）
- 验收: 类型定义与契约 §4 一致，`pnpm tsc --noEmit` 通过

**Step 2: 补充 Order 相关类型和方法**
- 产出物: 修改 `src/api/types.ts` 新增 `Order`、`SnagOrderRequest`、`OrderListResponse` 类型；新建 `src/api/order.ts` 导出 `orderApi`（`snag`、`list`）
- 验收: 类型定义与契约 §3 一致，`pnpm tsc --noEmit` 通过

### 非目标
- 不写 mock 或拦截器（后端未就绪时调用会 404，预期行为）
- 不实现调用方的页面逻辑（Phase 2 再做）
- 不新增依赖

### 参考
- API 契约: `docs/frontend/03-frontend-api-contracts.md §3-4`
- 已有模式: `src/api/user.ts`、`src/api/types.ts`、`src/api/client.ts`

### 自动化验收命令
- 运行环境: Node.js（pnpm）
- 执行命令格式: `cd singularity-front && pnpm tsc --noEmit`

[Step1: 编译通过，stock.ts 导出 4 个方法]
[Step2: 编译通过，order.ts 导出 2 个方法，types.ts 新增全部类型]

### 成功条件
- `pnpm tsc --noEmit` 零错误
- API 方法签名与契约一致（method/path/参数/返回类型）
- diff 范围仅 `api/` 目录

### 错误处理约定
- 如某步失败：先分析原因，给出修复方案，等确认后再修
- 如连续两次失败：停下来，列出可能原因，不要继续盲目重试
- 如遇到环境/依赖问题：报告具体报错，不要自行修改环境配置
