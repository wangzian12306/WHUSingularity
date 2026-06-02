# 前端技术栈

> 版本：v1.1
> 日期：2026-05-07

## 1. 基础技术栈

| 类别 | 选型 | 说明 |
|---|---|---|
| 框架 | React 19 + TypeScript | 项目既定规划 |
| 构建 | Vite | 快速 HMR，开箱即用的 TS 支持 |
| 路由 | React Router v6 | SPA 路由，支持路由守卫 |
| 请求 | axios | 拦截器处理 token 注入和 401 跳转 |
| UI 组件库 | Ant Design | 中后台场景成熟，表单/表格开箱即用 |
| 状态管理 | React Context + hooks | 页面少、逻辑简单，无需引入 Redux/Zustand |
| 包管理 | pnpm | 快速、节省磁盘空间 |

## 2. 多后端服务代理

前端需同时请求 user(8090)、order(8081)、stock(8082) 三个微服务，需要解决 CORS 和路由分发。当前已部署 Spring Cloud Gateway（:8080）作为统一入口。

### 2.1 开发环境（Vite Proxy）

通过 Vite 的 `server.proxy` 按 API 前缀转发到不同后端端口：

```ts
// vite.config.ts
export default defineConfig({
  server: {
    proxy: {
      '/api/user':  'http://localhost:8090',
      '/api/order': 'http://localhost:8081',
      '/api/stock': 'http://localhost:8082',
    },
  },
});
```

> 注：`/api/product` 与 `/api/merchant` 当前无前端页面直接消费，故未配置代理；如需调用可通过 Gateway（:8080）或单独追加代理。

### 2.2 生产环境（Spring Cloud Gateway）

系统已引入 `singularity-gateway`（:8080）作为统一 API 入口，基于 Nacos 服务发现自动路由：

```
/api/user    → lb://singularity-user
/api/order   → lb://singularity-order
/api/stock   → lb://singularity-stock
/api/product → lb://singularity-product
```

前端生产环境只需指向 Gateway 地址即可。

## 3. 实时数据

秒杀场景有几个准实时需求：
- 库存数字刷新（大量用户同时看到库存变化）
- 抢单结果反馈（异步 MQ 落库，用户需知道订单最终状态）

### 3.1 当前方案：短轮询

基于 axios 的定时轮询，实现简单，先满足 MVP：

- 库存：页面挂载后每 3s 轮询 `GET /api/stock/{productId}`
- 订单结果：抢单后每 2s 轮询订单状态，成功或失败后停止

### 3.2 后续升级：WebSocket

当并发量和实时性要求提高时，升级为 WebSocket：

```ts
const ws = new WebSocket('ws://localhost:8081/ws/stock');
ws.onmessage = (event) => {
  const { productId, availableQuantity } = JSON.parse(event.data);
  updateStockDisplay(productId, availableQuantity);
};
```

> 后端需新增 WebSocket 端点，前端暂不引入额外库，使用原生 `WebSocket` API。

### 3.3 工程规范

- 项目目录：`singularity-front`
- 语言：TypeScript strict 模式
- 样式：CSS Modules（组件级隔离，不引入额外预处理器）
- 代码风格：ESLint + Prettier
- 接口类型：按后端 API 契约手动维护 TypeScript 类型

## 4. 设计规范

### 4.1 色彩体系

基于武汉大学校园色彩，通过 Ant Design 的 `ConfigProvider` `theme.token` 统一注入。

| 角色 | 名称 | 色值 | 用途 |
|---|---|---|---|
| **主色 Primary** | 珞珈蓝 | `#002554` | 按钮、链接、选中态、品牌标识 |
| **主色辅助** | 珞珈绿 | `#115740` | 成功状态、次要强调、辅助品牌 |
| 辅助 | 珞樱粉 | `#f8a3bc` | 提示、轻量装饰 |
| 辅助 | 东湖蓝 | `#41b6e6` | 信息提示、图标点缀 |
| 辅助 | 秋桂黄 | `#ffa300` | 警告、倒计时高亮 |
| 辅助 | 春藤紫 | `#33058d` | 特殊标记 |
| 辅助 | 黉瓦绿 | `#00797c` | 辅助成功色、徽章 |
| 辅助 | 霜叶红 | `#e10800` | 错误、危险操作 |
| 中性 | 晨雾灰 | `#c1c6c8` | 边框、分割线、禁用态 |

```tsx
// ConfigProvider theme 配置示例
const theme = {
  token: {
    colorPrimary: '#002554',
    colorSuccess: '#115740',
    colorError: '#e10800',
    colorWarning: '#ffa300',
    colorInfo: '#41b6e6',
  },
}
```

## 5. 测试

| 类别 | 选型 | 说明 | 状态 |
|---|---|---|---|
| 单元测试 | Vitest | Vite 原生集成，零配置启动 | 未引入 |
| 组件测试 | React Testing Library | 测试用户行为而非实现细节 | 未引入 |
| E2E 测试 | Playwright | 抢单并发场景需端到端验证 | 未引入 |
| WebMCP 测试 | tsx + 自定义脚本 | 验证 tools 注册与端到端调用 | 已落地 `test/webmcp.test.mjs` |

> 除 WebMCP 测试脚本外，其余测试框架尚未引入，计划在功能稳定后补充。

## 6. 全局机制

- **Token 管理**：登录后将 `accessToken` 和 `expiresIn` 存入 localStorage
- **请求拦截**：axios request interceptor 自动附加 `Authorization: Bearer <token>`
- **401 处理**：axios response interceptor 捕获 401，清除 token 并跳转 `/login`
- **路由守卫**：受保护路由检查 localStorage 中的 token，无 token 则重定向 `/login`

## 7. WebMCP（增强能力）

### 7.1 什么是 WebMCP

WebMCP 是 W3C Web Machine Learning Community Group 提出的浏览器标准草案（2025.08 首次发布）。核心 API `navigator.modelContext.registerTool()` 允许网页暴露结构化的 JavaScript "tools"（带自然语言描述和 schema），供浏览器内 AI Agent 直接调用，而非通过模拟点击/滚动操作页面。

Chrome 146+ 已开启 Early Preview（需手动启用 flag）。

### 7.2 与本项目的关系

WebMCP 是**增量能力，不影响基础架构选型**。原因：

- 纯前端 JS API，不需要后端配合，天然兼容 React
- 常规页面交互（登录、抢单、用户中心）的人用 UI 不变
- Agent 调用的 tool 与用户点击按钮走的是同一套前端业务逻辑

### 7.3 落地状态

WebMCP 已集成完毕：

1. ✅ 引入 `@mcp-b/webmcp-polyfill`（`singularity-front/package.json`）
2. ✅ 在 `src/main.tsx` 中初始化 polyfill
3. ✅ 在 `src/webmcp/tools.ts` 注册 4 个业务 tools：
   - `listStock` — 调用 `stockApi.list()`
   - `snagOrder` — 调用 `orderApi.snag({ userId })`
   - `listOrders` — 调用 `orderApi.list(params)`
   - `getUserInfo` — 返回当前登录用户信息
4. ✅ `Home.tsx` 页面挂载时注册/卸载 tools

### 7.4 参考资料

- [WebMCP Spec (W3C)](https://webmachinelearning.github.io/webmcp)
- [Awesome WebMCP](https://github.com/webfuse-com/awesome-webmcp)
- [MCP-B Polyfill & React Hooks](https://docs.mcp-b.ai/)
- [webmcp-react](https://github.com/MCPCat/webmcp-react)
- [Chrome WebMCP Early Preview](https://developer.chrome.com/blog/webmcp-epp)
