# 前端技术栈

> 版本：v1.0
> 日期：2026-04-20

## 1. 基础技术栈

| 类别 | 选型 | 说明 |
|---|---|---|
| 框架 | React 18+ TypeScript | 项目既定规划 |
| 构建 | Vite | 快速 HMR，开箱即用的 TS 支持 |
| 路由 | React Router v6 | SPA 路由，支持路由守卫 |
| 请求 | axios | 拦截器处理 token 注入和 401 跳转 |
| UI 组件库 | Ant Design | 中后台场景成熟，表单/表格开箱即用 |
| 状态管理 | React Context + hooks | 页面少、逻辑简单，无需引入 Redux/Zustand |
| 包管理 | pnpm | 快速、节省磁盘空间 |

## 2. 多后端服务代理

前端需同时请求 user(8090)、order(8081)、stock(8082) 三个微服务，需要解决 CORS 和路由分发。

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

### 2.2 生产环境（Nginx 反向代理）

无网关阶段，使用 Nginx 统一入口：

```nginx
location /api/user  { proxy_pass http://localhost:8090; }
location /api/order { proxy_pass http://localhost:8081; }
location /api/stock { proxy_pass http://localhost:8082; }
location /          { root /usr/share/nginx/html; try_files $uri /index.html; }
```

> 后续若引入 API Gateway（如 Spring Cloud Gateway），前端只需指向单一网关地址。

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

| 类别 | 选型 | 说明 |
|---|---|---|
| 单元测试 | Vitest | Vite 原生集成，零配置启动 |
| 组件测试 | React Testing Library | 测试用户行为而非实现细节 |
| E2E 测试 | Playwright | 抢单并发场景需端到端验证 |

### 5.1 单元测试 + 组件测试

覆盖重点：
- 登录/注册表单校验逻辑
- Token 存储/清除/过期判断
- 请求拦截器（token 注入、401 跳转）
- 抢单按钮的防重复提交、倒计时禁用

### 5.2 E2E 测试

覆盖场景：
- 注册 → 登录 → 抢单 → 退出 完整链路
- 未登录访问受保护页面 → 跳转登录
- token 过期后操作 → 弹回登录页

> 测试在基础页面稳定后再补充，不阻塞首次开发。

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

### 7.3 落地计划

1. 基础页面跑通后再接入 WebMCP
2. 引入 `@mcp-b/webmcp-polyfill` 兼容非 Chrome 146 浏览器
3. 在关键业务操作上注册 tool，例如：

```js
// 示例：注册抢单 tool
navigator.modelContext.registerTool({
  name: "snagOrder",
  description: "抢购指定商品的秒杀资格",
  parameters: {
    type: "object",
    properties: { slotId: { type: "string", description: "商品/槽位ID" } },
    required: ["slotId"],
  },
  handler: async ({ slotId }) => { /* 调用后端抢单 API */ },
});
```

### 7.4 参考资料

- [WebMCP Spec (W3C)](https://webmachinelearning.github.io/webmcp)
- [Awesome WebMCP](https://github.com/webfuse-com/awesome-webmcp)
- [MCP-B Polyfill & React Hooks](https://docs.mcp-b.ai/)
- [webmcp-react](https://github.com/MCPCat/webmcp-react)
- [Chrome WebMCP Early Preview](https://developer.chrome.com/blog/webmcp-epp)
