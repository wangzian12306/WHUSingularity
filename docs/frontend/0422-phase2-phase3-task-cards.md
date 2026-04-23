# Phase 2 + Phase 3 前端实现计划

> 日期：2026-04-22
> 状态：Task 4、5、6、7、8 已完成
> 前置：Phase 1 已完成（全局布局、Admin 用户管理、Stock/Order API 客户端）
> 后端状态：order list / stock 全部接口已就绪

---

## Task 4: 秒杀主页

### 背景
`/` 路由目前是 Placeholder，这是核心业务页面：展示可抢购的商品列表（stock），每个商品显示实时库存、抢单按钮，点击后调用 `POST /api/order/snag` 抢单，并轮询订单结果。页面需要倒计时（如果秒杀有开始时间）、库存轮询（3s 间隔）、抢单防重复（按钮 loading 态）。

### 最终目标
用户登录后访问 `/`，看到商品列表，能点击抢单，得到成功/失败的明确反馈。

### 分步计划（有序，每步独立可验收）

**Step 1: 创建秒杀主页组件骨架** ✅
- 产出物: 新建 `src/pages/Home.tsx`
- 内容: 页面结构分为两部分——商品列表区（Card 网格）、抢单结果反馈区（Alert/Notification）。使用 `useEffect` 每 3s 轮询 `stockApi.list()` 刷新库存
- 验收: 访问 `/` 页面正常渲染，3s 后库存数字自动更新

**Step 2: 实现抢单按钮与防重复提交** ✅
- 产出物: 修改 `src/pages/Home.tsx`
- 内容: 每个商品 Card 上有抢单按钮，点击后：
  1. 按钮置为 loading 态，禁用点击
  2. 调用 `orderApi.snag({ userId: String(user.id) })`
  3. 成功：显示订单号 + 状态；失败：显示错误原因（库存不足/重复抢单）
  4. 无论成败，2s 后按钮恢复可点击
- 验收: 快速连续点击同一商品按钮，只发出一次请求；错误提示可读

**Step 3: 轮询抢单结果** ✅
- 产出物: 修改 `src/pages/Home.tsx`
- 内容: 抢单成功后，每 2s 轮询 `orderApi.list({ actorId: user.id, page: 0, size: 1 })` 查看最新订单状态，直到状态变为 `PAID` 或 `CANCELLED`，停止轮询并展示最终结果
- 验收: 抢单后页面自动刷新订单状态，最终显示"抢单成功"或"抢单失败"

**Step 4: 路由替换** ✅
- 产出物: 修改 `src/App.tsx`
- 内容: 将 `/` 的 `Placeholder` 替换为 `Home` 组件
- 验收: 登录后访问 `/` 显示秒杀主页

### 非目标
- 不做 WebSocket 实时推送（用轮询先满足 MVP）
- 不做秒杀倒计时（当前无开始/结束时间概念，后续扩展）
- 不做支付流程（抢单成功后直接 PAID）
- 不引入新依赖

### 参考
- API 契约: `docs/frontend/03-frontend-api-contracts.md §3-4`
- 技术栈: `docs/frontend/02-frontend-tech-stack.md §3.1`（轮询方案）
- 现有模式: `src/pages/admin/AdminUserList.tsx`（Table + Modal）

### 自动化验收命令
- 运行环境: Node.js（pnpm）+ `./dev-run.sh` 拉起全部后端
- 执行命令格式: `cd singularity-front && pnpm dev`

[Step1: 访问 `/`，页面显示商品列表，3s 后库存数字变化]
[Step2: 点击抢单按钮，loading 态 2s，显示结果；快速连击只发一次请求]
[Step3: 抢单后 2-6s 内，订单状态从 CREATED 变为 PAID/CANCELLED，页面更新]
[Step4: 登录后 `/` 不再显示 Placeholder]

### 成功条件
- 商品列表正确展示后端返回数据
- 抢单按钮有 loading 态和防重复
- 抢单结果有明确反馈
- diff 范围在 `pages/Home.tsx`、`App.tsx`

### 错误处理约定
- 如某步失败：先分析原因，给出修复方案，等确认后再修
- 如连续两次失败：停下来，列出可能原因，不要继续盲目重试
- 如遇到环境/依赖问题：报告具体报错，不要自行修改环境配置

---

## Task 5: 用户中心

### 背景
当前 AppLayout 顶栏只有用户名和退出按钮，没有用户中心入口。用户需要查看个人信息、充值余额、查看自己的订单列表。`/user` 路由尚未注册。

### 最终目标
`/user` 页面展示当前用户信息、余额充值入口、我的订单列表（分页）。

### 分步计划（有序，每步独立可验收）

**Step 1: AppLayout 添加用户中心入口** ✅
- 产出物: 修改 `src/components/AppLayout.tsx`
- 内容: 顶栏用户名旁添加"用户中心"链接，点击跳转 `/user`
- 验收: 登录后顶栏显示"用户中心"，点击跳转 `/user`（此时页面为空或 Placeholder）

**Step 2: 创建用户中心页面骨架** ✅
- 产出物: 新建 `src/pages/UserCenter.tsx`
- 内容: 页面分三区块：
  - 用户信息卡片：用户名、昵称、角色
  - 余额卡片：当前余额 + 充值按钮（弹出 Modal，输入金额，调用 `userApi.recharge`）
  - 订单列表：Table，调用 `orderApi.list({ actorId: user.id })`
- 验收: 页面正常渲染，信息正确，充值 Modal 可弹出

**Step 3: 实现订单列表分页** ✅
- 产出物: 修改 `src/pages/UserCenter.tsx`
- 内容: Ant Design Table 自带分页，切换页码时调用 `orderApi.list` 并传入对应 `page`/`size`
- 验收: 订单超过 10 条时分页器出现，切换页码正确加载数据

**Step 4: 注册路由** ✅
- 产出物: 修改 `src/App.tsx`
- 内容: 在 ProtectedRoute 下添加 `/user` 路由
- 验收: 访问 `/user` 正常显示用户中心页面

### 非目标
- 不做订单详情页（列表展示足够）
- 不做充值记录查询
- 不编辑个人信息（已有 Admin 用户管理页处理）
- 不引入新依赖

### 参考
- API 契约: `docs/frontend/03-frontend-api-contracts.md §2.4-2.5、§3.2`
- 现有模式: `src/pages/admin/AdminUserList.tsx`（Table + 分页）

### 自动化验收命令
- 运行环境: Node.js（pnpm）+ `./dev-run.sh` 拉起全部后端
- 执行命令格式: `cd singularity-front && pnpm dev`

[Step1: 登录后顶栏出现"用户中心"，点击跳转 `/user`]
[Step2: `/user` 页面显示用户信息、余额、充值 Modal、订单列表]
[Step3: 订单超过 10 条时分页工作，切换页码刷新数据]
[Step4: 直接访问 `/user` 页面正常渲染]

### 成功条件
- 用户中心入口可见且可点击
- 余额展示正确，充值成功后余额更新
- 订单列表分页正常
- diff 范围在 `components/AppLayout.tsx`、`pages/UserCenter.tsx`、`App.tsx`

### 错误处理约定
- 如某步失败：先分析原因，给出修复方案，等确认后再修
- 如连续两次失败：停下来，列出可能原因，不要继续盲目重试
- 如遇到环境/依赖问题：报告具体报错，不要自行修改环境配置

---

## Task 6: Admin 库存管理

### 背景
Admin 管理页目前只有用户管理，需要补充库存管理页面：查看所有商品库存、初始化库存、查看变更日志。stock API 客户端已在 Phase 1 完成。

### 最终目标
`/admin/stock` 页面展示库存列表 Table，支持初始化库存（Modal 表单），支持查看变更日志。

### 分步计划（有序，每步独立可验收）

**Step 1: 创建 AdminStockList 页面** ✅
- 产出物: 新建 `src/pages/admin/AdminStockList.tsx`
- 内容: Ant Design Table 展示 `stockApi.list()` 结果，列：productId、availableQuantity、reservedQuantity、totalQuantity
- 验收: admin 账号登录，访问 `/admin/stock`，显示库存列表

**Step 2: 实现库存初始化 Modal** ✅
- 产出物: 修改 `src/pages/admin/AdminStockList.tsx`
- 内容: 页面顶部添加"初始化库存"按钮，点击弹出 Modal（Form：productId、totalQuantity），提交调用 `stockApi.init()`，成功后刷新列表
- 验收: 输入新商品 ID 和库存数量，提交后列表出现新记录；重复初始化同一商品报 `STOCK_ALREADY_EXISTS`

**Step 3: 实现变更日志查看** ✅
- 产出物: 修改 `src/pages/admin/AdminStockList.tsx`
- 内容: 每行库存添加"变更日志"操作按钮，点击弹出 Drawer/Modal，调用 `stockApi.getChangeLog({ productId })` 展示该商品的库存变更记录
- 验收: 点击"变更日志"，弹出面板显示该商品的全部变更记录

**Step 4: 添加路由和导航入口** ✅
- 产出物: 修改 `src/App.tsx`、`src/components/AppLayout.tsx`
- 内容: AppLayout 管理入口改为下拉菜单或分组导航（用户管理 / 库存管理 / 订单管理），注册 `/admin/stock` 路由
- 验收: admin 用户在顶栏管理入口可看到库存管理，点击跳转 `/admin/stock`

### 非目标
- 不做库存手动调整（已有 init + MQ 驱动扣减）
- 不做批量初始化
- 不引入新依赖

### 参考
- API 契约: `docs/frontend/03-frontend-api-contracts.md §4`
- 现有模式: `src/pages/admin/AdminUserList.tsx`

### 自动化验收命令
- 运行环境: Node.js（pnpm）+ `./dev-run.sh` 拉起全部后端
- 执行命令格式: `cd singularity-front && pnpm dev`

[Step1: admin 访问 `/admin/stock`，显示库存列表]
[Step2: 初始化新商品库存，列表刷新出现新记录]
[Step3: 点击变更日志，显示对应商品的变更记录]
[Step4: 管理入口导航正常，路由可访问]

### 成功条件
- 库存列表正确展示
- 初始化功能可用，错误提示正确
- 变更日志按商品过滤展示
- diff 范围在 `pages/admin/AdminStockList.tsx`、`App.tsx`、`AppLayout.tsx`

### 错误处理约定
- 如某步失败：先分析原因，给出修复方案，等确认后再修
- 如连续两次失败：停下来，列出可能原因，不要继续盲目重试
- 如遇到环境/依赖问题：报告具体报错，不要自行修改环境配置

---

## Task 7: Admin 订单管理

### 背景
Admin 需要查看系统中全部订单，支持按用户和状态筛选。Order list API 已支持 `actorId` 和 `status` 过滤 + 分页。

### 最终目标
`/admin/orders` 页面展示全部订单列表 Table，支持按用户 ID 和状态筛选，分页展示。

### 分步计划（有序，每步独立可验收）

**Step 1: 创建 AdminOrderList 页面** ✅
- 产出物: 新建 `src/pages/admin/AdminOrderList.tsx`
- 内容: Ant Design Table 展示 `orderApi.list({ page: 0, size: 10 })` 结果（admin 查看全部订单，不传 actorId），列：orderId、userId、slotId、status、createTime
- 验收: admin 访问 `/admin/orders`，显示全部订单列表

**Step 2: 实现筛选条件** ✅
- 产出物: 修改 `src/pages/admin/AdminOrderList.tsx`
- 内容: 表格上方添加筛选表单：
  - userId Input（按用户过滤）
  - status Select（CREATED / PAID / CANCELLED）
  - 查询按钮（调用 `orderApi.list` 带参数）
  - 重置按钮（清空条件，查全部）
- 验收: 输入 userId 后只显示该用户订单；选择 status 后只显示对应状态订单；组合筛选同时生效

**Step 3: 添加路由和导航入口** ✅
- 产出物: 修改 `src/App.tsx`、`src/components/AppLayout.tsx`
- 内容: 在管理导航中添加"订单管理"入口，注册 `/admin/orders` 路由
- 验收: admin 用户可在管理入口看到订单管理并访问

### 非目标
- 不做订单状态手动修改（由后端 MQ 驱动）
- 不做订单详情页
- 不引入新依赖

### 参考
- API 契约: `docs/frontend/03-frontend-api-contracts.md §3.2`
- 现有模式: `src/pages/admin/AdminUserList.tsx`

### 自动化验收命令
- 运行环境: Node.js（pnpm）+ `./dev-run.sh` 拉起全部后端
- 执行命令格式: `cd singularity-front && pnpm dev`

[Step1: admin 访问 `/admin/orders`，显示全部订单，分页正常]
[Step2: 按 userId 和 status 筛选，结果正确]
[Step3: 管理导航入口可见，路由可访问]

### 成功条件
- 订单列表正确展示全部订单
- 筛选条件生效，组合筛选正确
- 分页正常工作
- diff 范围在 `pages/admin/AdminOrderList.tsx`、`App.tsx`、`AppLayout.tsx`

### 错误处理约定
- 如某步失败：先分析原因，给出修复方案，等确认后再修
- 如连续两次失败：停下来，列出可能原因，不要继续盲目重试
- 如遇到环境/依赖问题：报告具体报错，不要自行修改环境配置

---

## Task 8: WebMCP 集成

### 背景
WebMCP 允许浏览器内 AI Agent 直接调用页面暴露的 JavaScript tools，而非模拟点击。这是增强能力，不影响现有 UI。

### 最终目标
基础页面稳定后，在关键业务操作（抢单、查库存、查订单）上注册 WebMCP tools，使支持 WebMCP 的浏览器 AI 可直接操作。

### 分步计划（有序，每步独立可验收）

**Step 1: 引入 WebMCP polyfill** ✅
- 产出物: 修改 `singularity-front/package.json`
- 内容: 安装 `@mcp-b/webmcp-polyfill`，在 `src/main.tsx` 中初始化 polyfill
- 验收: `pnpm install` 成功，应用正常启动，控制台无 polyfill 报错

**Step 2: 注册业务 tools** ✅
- 产出物: 新建 `src/webmcp/tools.ts`
- 内容: 注册以下 tools：
  - `listStock`: 调用 `stockApi.list()`
  - `snagOrder`: 调用 `orderApi.snag({ userId })`
  - `listOrders`: 调用 `orderApi.list(params)`
  - `getUserInfo`: 返回当前登录用户信息
- 验收: Chrome 146+（开启 WebMCP flag）中，开发者工具可看到已注册的 tools

**Step 3: 在 Home 页面挂载 tool 注册** ✅
- 产出物: 修改 `src/pages/Home.tsx`
- 内容: 页面挂载时注册抢单和查库存 tools，卸载时注销
- 验收: 访问 `/` 后，浏览器 AI 可通过 WebMCP 执行抢单和查库存

### 非目标
- 不影响常规用户 UI（WebMCP 是增量能力）
- 不向后兼容做过多工作（polyfill 已处理）
- 不注册管理操作（先聚焦用户侧核心流程）

### 参考
- 技术栈文档: `docs/frontend/02-frontend-tech-stack.md §7`
- WebMCP Spec: https://webmachinelearning.github.io/webmcp
- Polyfill: https://docs.mcp-b.ai/

### 自动化验收命令
- 运行环境: Node.js（pnpm）+ Chrome 146+（开启 WebMCP flag）
- 执行命令格式: `cd singularity-front && pnpm dev`

[Step1: `pnpm install` 成功，`pnpm dev` 正常启动]
[Step2: Chrome 开发者工具 Application > WebMCP 面板可见已注册 tools]
[Step3: 通过浏览器 AI 执行 listStock 和 snagOrder]

### 成功条件
- polyfill 加载无报错
- 关键业务 tools 注册成功
- 常规 UI 不受影响
- diff 范围在 `package.json`、`main.tsx`、新增 `webmcp/tools.ts`、`pages/Home.tsx`

### 错误处理约定
- 如某步失败：先分析原因，给出修复方案，等确认后再修
- 如连续两次失败：停下来，列出可能原因，不要继续盲目重试
- 如遇到环境/依赖问题：报告具体报错，不要自行修改环境配置

---

## 实施顺序建议

1. **Task 4 秒杀主页** → 核心业务流程，优先验证端到端
2. **Task 5 用户中心** → 与 Task 4 无依赖，可并行
3. **Task 6 Admin 库存管理** → 依赖 Task 4/5 完成后，管理页导航重构
4. **Task 7 Admin 订单管理** → 与 Task 6 类似，可紧跟其后
5. **Task 8 WebMCP 集成** → 基础页面全部稳定后再做

> 类型修复已提前完成（`Order.userId` / `status: string` / `SnapOrderResponse` 拼写）。

---

## 测试验收汇总（2026-04-22）

### Task 4 — 秒杀主页 `/`

- 商品列表展示、3s 轮询、抢单按钮 loading + 防重复、订单状态轮询均通过验收。
- **已知后端限制**：订单状态始终为 `CREATED`（处理中），不会变为成功/失败。根因见 `docs/startup.md` §已知后端限制。

### Task 5 — 用户中心 `/user`

- 用户中心入口、信息/余额展示、充值 Modal、订单列表分页均通过验收。
- 修复了分页 `page` 参数偏移（前端原传 1 起始，后端需 0 起始）。

### Task 6 — Admin 库存管理 `/admin/stock`

- 库存列表、初始化库存、变更日志均通过验收。
- 已知限制已解除：抢单消息现在携带 `productId` 并由 `order-topic` 驱动库存异步落库。

### Task 7 — Admin 订单管理 `/admin/orders`

- 全部订单列表、分页、userId / status 筛选、组合筛选 + 重置均通过验收。
- 修复了分页 `page` 参数偏移（同 Task 5）。

### Task 8 — WebMCP 集成

- polyfill 加载无报错，4 个 tools 全部注册成功。
- `listStock` tool 已通过端到端调用验证（返回库存列表数据）。
- 自动化测试脚本：`singularity-front/test/webmcp.test.mjs`。
- 运行命令：`cd singularity-front && npx tsx test/webmcp.test.mjs`。
