# 商户/用户注册登录 Tab 切换 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在注册和登录页面增加 Tab 切换，用户可选择注册/登录为"普通用户"或"商户"。

**Architecture:** Vite 代理增加 `/api/merchant` 路由；Register/Login 页面用 Ant Design Tabs 组件切换"用户"和"商户"表单；新建 MerchantAuthContext 管理商户独立 JWT（与 User JWT 互不干扰）；API 拦截器根据 URL 前缀自动选择正确的 token。

**Tech Stack:** React 19, TypeScript, Vite, Ant Design, Axios, React Router

---

## 文件结构映射

| 文件 | 操作 | 职责 |
|---|---|---|
| `singularity-front/vite.config.ts` | 修改 | 新增 `/api/merchant` 代理到 9091 |
| `singularity-front/src/api/client.ts` | 修改 | 请求拦截器根据 URL 前缀选择 merchantAccessToken 或 accessToken |
| `singularity-front/src/contexts/MerchantAuthContext.tsx` | 创建 | 商户登录态管理（login/logout/profile/restore），独立 localStorage key |
| `singularity-front/src/pages/Register.tsx` | 修改 | 增加 Tabs 切换用户/商户注册，商户注册调用 merchantApi |
| `singularity-front/src/pages/Login.tsx` | 修改 | 增加 Tabs 切换用户/商户登录，商户登录使用 MerchantAuthContext |
| `singularity-front/src/App.tsx` | 修改 | 包裹 MerchantAuthProvider，添加 merchant logout 到 401 重定向逻辑 |

---

## Task 1: Vite 代理配置

**Files:**
- Modify: `singularity-front/vite.config.ts`

- [ ] **Step 1: 添加 merchant 代理**

在 `vite.config.ts` 的 `proxy` 对象中新增：

```ts
'/api/merchant': 'http://localhost:9091',
```

最终文件内容：

```ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api/user': 'http://localhost:8090',
      '/api/order': 'http://localhost:8081',
      '/api/stock': 'http://localhost:8082',
      '/api/merchant': 'http://localhost:9091',
    },
  },
})
```

- [ ] **Step 2: Commit**

```bash
git add singularity-front/vite.config.ts
git commit -m "feat(frontend): add merchant API proxy to vite config"
```

---

## Task 2: API 拦截器支持双 token

**Files:**
- Modify: `singularity-front/src/api/client.ts`

当前 `client.ts` 的请求拦截器只从 `localStorage.getItem('accessToken')` 取 token。商户和用户的 JWT 由不同服务签发，需要独立存储。

- [ ] **Step 1: 修改请求拦截器**

替换 `api.interceptors.request.use` 中的 token 读取逻辑，根据请求 URL 前缀选择 token：

```ts
import axios from 'axios'
import type { ApiResponse } from './types'

const api = axios.create({
  baseURL: '',
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const url = config.url ?? ''
  const token = url.startsWith('/api/merchant')
    ? localStorage.getItem('merchantAccessToken')
    : localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (res) => res,
  (error) => {
    if (error.response?.status === 401) {
      const url = error.config?.url ?? ''
      if (url.startsWith('/api/merchant')) {
        localStorage.removeItem('merchantAccessToken')
        localStorage.removeItem('merchantExpiresIn')
      } else {
        localStorage.removeItem('accessToken')
        localStorage.removeItem('expiresIn')
      }
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

export async function request<T>(config: Parameters<typeof api.request>[0]): Promise<ApiResponse<T>> {
  const res = await api.request<ApiResponse<T>>(config)
  return res.data
}
```

- [ ] **Step 2: Commit**

```bash
git add singularity-front/src/api/client.ts
git commit -m "feat(frontend): support dual JWT token in API interceptor"
```

---

## Task 3: MerchantAuthContext

**Files:**
- Create: `singularity-front/src/contexts/MerchantAuthContext.tsx`

- [ ] **Step 1: 创建 MerchantAuthContext**

```tsx
import { createContext, useContext, useState, useEffect, useCallback, type ReactNode } from 'react'
import type { MerchantView } from '../api/types'
import { merchantApi } from '../api/merchant'

interface MerchantAuthContextType {
  merchant: MerchantView | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

const MerchantAuthContext = createContext<MerchantAuthContextType | null>(null)

export function MerchantAuthProvider({ children }: { children: ReactNode }) {
  const [merchant, setMerchant] = useState<MerchantView | null>(null)
  const [loading, setLoading] = useState(true)

  const restore = useCallback(async () => {
    const token = localStorage.getItem('merchantAccessToken')
    if (!token) {
      setLoading(false)
      return
    }
    try {
      const res = await merchantApi.profile()
      if (res.success && res.data) setMerchant(res.data)
    } catch {
      localStorage.removeItem('merchantAccessToken')
      localStorage.removeItem('merchantExpiresIn')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { restore() }, [restore])

  const login = useCallback(async (username: string, password: string) => {
    const res = await merchantApi.login({ username, password })
    if (!res.success || !res.data) throw new Error(res.error?.message ?? 'Login failed')
    localStorage.setItem('merchantAccessToken', res.data.accessToken)
    localStorage.setItem('merchantExpiresIn', String(res.data.expiresIn))
    setMerchant(res.data.merchant)
  }, [])

  const logout = useCallback(async () => {
    try { await merchantApi.logout() } catch { /* ignore */ }
    localStorage.removeItem('merchantAccessToken')
    localStorage.removeItem('merchantExpiresIn')
    setMerchant(null)
  }, [])

  return (
    <MerchantAuthContext.Provider value={{ merchant, loading, login, logout }}>
      {children}
    </MerchantAuthContext.Provider>
  )
}

export function useMerchantAuth() {
  const ctx = useContext(MerchantAuthContext)
  if (!ctx) throw new Error('useMerchantAuth must be used within MerchantAuthProvider')
  return ctx
}
```

- [ ] **Step 2: Commit**

```bash
git add singularity-front/src/contexts/MerchantAuthContext.tsx
git commit -m "feat(frontend): add MerchantAuthContext for merchant login state"
```

---

## Task 4: 注册页面 Tab 切换

**Files:**
- Modify: `singularity-front/src/pages/Register.tsx`

- [ ] **Step 1: 重写 Register.tsx**

完整替换文件内容：

```tsx
import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { Form, Input, Button, Card, Typography, Tabs, message } from 'antd'
import { UserOutlined, ShopOutlined } from '@ant-design/icons'
import { userApi } from '../api/user'
import { merchantApi } from '../api/merchant'
import type { ApiResponse, ApiError } from '../api/types'

const { Title } = Typography

export default function RegisterPage() {
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()
  const [activeTab, setActiveTab] = useState('user')

  const onUserFinish = async (values: { username: string; password: string; nickname?: string }) => {
    setLoading(true)
    try {
      const res = await userApi.register(values)
      if (res.success) {
        message.success('注册成功，请登录')
        navigate('/login', { replace: true })
        return
      }
      const err = res.error as ApiError | undefined
      message.error(err?.message ?? '注册失败')
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: ApiResponse } }
        const apiError = axiosErr.response?.data?.error
        if (apiError) {
          message.error(apiError.message)
          return
        }
      }
      message.error('注册失败，请稍后重试')
    } finally {
      setLoading(false)
    }
  }

  const onMerchantFinish = async (values: { username: string; password: string; shopName: string; contactName?: string; contactPhone?: string }) => {
    setLoading(true)
    try {
      const res = await merchantApi.register(values)
      if (res.success) {
        message.success('商户注册成功，请登录')
        navigate('/login', { replace: true })
        return
      }
      const err = res.error as ApiError | undefined
      message.error(err?.message ?? '注册失败')
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: ApiResponse } }
        const apiError = axiosErr.response?.data?.error
        if (apiError) {
          message.error(apiError.message)
          return
        }
      }
      message.error('注册失败，请稍后重试')
    } finally {
      setLoading(false)
    }
  }

  const userForm = (
    <Form size="large" onFinish={onUserFinish} autoComplete="off">
      <Form.Item name="username" rules={[
        { required: true, message: '请输入用户名' },
        { min: 4, max: 32, message: '用户名需 4-32 个字符' },
        { pattern: /^[a-zA-Z0-9_]+$/, message: '仅支持字母、数字、下划线' },
      ]}>
        <Input placeholder="用户名" />
      </Form.Item>
      <Form.Item name="password" rules={[
        { required: true, message: '请输入密码' },
        { min: 8, max: 64, message: '密码需 8-64 个字符' },
      ]}>
        <Input.Password placeholder="密码" />
      </Form.Item>
      <Form.Item name="nickname">
        <Input placeholder="昵称（可选）" />
      </Form.Item>
      <Form.Item>
        <Button type="primary" htmlType="submit" loading={loading} block>
          注册
        </Button>
      </Form.Item>
    </Form>
  )

  const merchantForm = (
    <Form size="large" onFinish={onMerchantFinish} autoComplete="off">
      <Form.Item name="username" rules={[
        { required: true, message: '请输入用户名' },
        { min: 4, max: 32, message: '用户名需 4-32 个字符' },
      ]}>
        <Input placeholder="用户名" />
      </Form.Item>
      <Form.Item name="password" rules={[
        { required: true, message: '请输入密码' },
        { min: 8, max: 64, message: '密码需 8-64 个字符' },
      ]}>
        <Input.Password placeholder="密码" />
      </Form.Item>
      <Form.Item name="shopName" rules={[{ required: true, message: '请输入店铺名称' }]}>
        <Input placeholder="店铺名称" />
      </Form.Item>
      <Form.Item name="contactName">
        <Input placeholder="联系人（可选）" />
      </Form.Item>
      <Form.Item name="contactPhone">
        <Input placeholder="联系电话（可选）" />
      </Form.Item>
      <Form.Item>
        <Button type="primary" htmlType="submit" loading={loading} block>
          注册
        </Button>
      </Form.Item>
    </Form>
  )

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh' }}>
      <Card style={{ width: 400 }}>
        <Title level={3} style={{ textAlign: 'center', marginBottom: 24 }}>注册</Title>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          centered
          items={[
            { key: 'user', label: <span><UserOutlined /> 普通用户</span>, children: userForm },
            { key: 'merchant', label: <span><ShopOutlined /> 商户</span>, children: merchantForm },
          ]}
        />
        <div style={{ textAlign: 'center' }}>
          <Link to="/login">已有账号？去登录</Link>
        </div>
      </Card>
    </div>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add singularity-front/src/pages/Register.tsx
git commit -m "feat(frontend): add user/merchant tab switching on register page"
```

---

## Task 5: 登录页面 Tab 切换

**Files:**
- Modify: `singularity-front/src/pages/Login.tsx`

- [ ] **Step 1: 重写 Login.tsx**

完整替换文件内容：

```tsx
import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { Form, Input, Button, Card, Typography, Tabs, message } from 'antd'
import { UserOutlined, ShopOutlined } from '@ant-design/icons'
import { useAuth } from '../contexts/AuthContext'
import { useMerchantAuth } from '../contexts/MerchantAuthContext'

const { Title } = Typography

export default function LoginPage() {
  const [loading, setLoading] = useState(false)
  const { login: userLogin } = useAuth()
  const { login: merchantLogin } = useMerchantAuth()
  const navigate = useNavigate()
  const [activeTab, setActiveTab] = useState('user')

  const onUserLogin = async (values: { username: string; password: string }) => {
    setLoading(true)
    try {
      await userLogin(values.username, values.password)
      message.success('登录成功')
      navigate('/', { replace: true })
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '登录失败'
      message.error(msg)
    } finally {
      setLoading(false)
    }
  }

  const onMerchantLogin = async (values: { username: string; password: string }) => {
    setLoading(true)
    try {
      await merchantLogin(values.username, values.password)
      message.success('商户登录成功')
      navigate('/', { replace: true })
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '登录失败'
      message.error(msg)
    } finally {
      setLoading(false)
    }
  }

  const userForm = (
    <Form size="large" onFinish={onUserLogin} autoComplete="off">
      <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
        <Input placeholder="用户名" />
      </Form.Item>
      <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
        <Input.Password placeholder="密码" />
      </Form.Item>
      <Form.Item>
        <Button type="primary" htmlType="submit" loading={loading} block>
          登录
        </Button>
      </Form.Item>
    </Form>
  )

  const merchantForm = (
    <Form size="large" onFinish={onMerchantLogin} autoComplete="off">
      <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
        <Input placeholder="用户名" />
      </Form.Item>
      <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
        <Input.Password placeholder="密码" />
      </Form.Item>
      <Form.Item>
        <Button type="primary" htmlType="submit" loading={loading} block>
          登录
        </Button>
      </Form.Item>
    </Form>
  )

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh' }}>
      <Card style={{ width: 400 }}>
        <Title level={3} style={{ textAlign: 'center', marginBottom: 24 }}>登录</Title>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          centered
          items={[
            { key: 'user', label: <span><UserOutlined /> 普通用户</span>, children: userForm },
            { key: 'merchant', label: <span><ShopOutlined /> 商户</span>, children: merchantForm },
          ]}
        />
        <div style={{ textAlign: 'center' }}>
          <Link to="/register">没有账号？去注册</Link>
        </div>
      </Card>
    </div>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add singularity-front/src/pages/Login.tsx
git commit -m "feat(frontend): add user/merchant tab switching on login page"
```

---

## Task 6: App.tsx 集成 MerchantAuthProvider

**Files:**
- Modify: `singularity-front/src/App.tsx`

- [ ] **Step 1: 包裹 MerchantAuthProvider**

完整替换文件内容：

```tsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import { AuthProvider } from './contexts/AuthContext'
import { MerchantAuthProvider } from './contexts/MerchantAuthContext'
import { ProtectedRoute } from './components/AuthGuard'
import { AdminRoute } from './components/AdminGuard'
import AppLayout from './components/AppLayout'
import LoginPage from './pages/Login'
import RegisterPage from './pages/Register'
import Home from './pages/Home'
import UserCenter from './pages/UserCenter'
import AdminUserList from './pages/admin/AdminUserList'
import AdminStockList from './pages/admin/AdminStockList'
import AdminOrderList from './pages/admin/AdminOrderList'

export default function App() {
  return (
    <ConfigProvider theme={{
      token: {
        colorPrimary: '#002554',
        colorSuccess: '#115740',
        colorError: '#e10800',
        colorWarning: '#ffa300',
        colorInfo: '#41b6e6',
        colorBorder: '#c1c6c8',
        colorTextDisabled: '#c1c6c8',
      },
    }}>
      <BrowserRouter>
        <AuthProvider>
          <MerchantAuthProvider>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
              <Route element={<ProtectedRoute><AppLayout /></ProtectedRoute>}>
                <Route path="/" element={<Home />} />
                <Route path="/user" element={<UserCenter />} />
                <Route path="/admin/users" element={<AdminRoute><AdminUserList /></AdminRoute>} />
                <Route path="/admin/stock" element={<AdminRoute><AdminStockList /></AdminRoute>} />
                <Route path="/admin/orders" element={<AdminRoute><AdminOrderList /></AdminRoute>} />
              </Route>
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </MerchantAuthProvider>
        </AuthProvider>
      </BrowserRouter>
    </ConfigProvider>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add singularity-front/src/App.tsx
git commit -m "feat(frontend): integrate MerchantAuthProvider in App"
```

---

## Task 7: 构建验证

- [ ] **Step 1: 启动前端开发服务器确认无编译错误**

```bash
cd singularity-front && npm run dev
```

访问 http://localhost:5173/register 和 http://localhost:5173/login，确认：
- 两个页面都有 Tab 切换（普通用户 / 商户）
- Tab 切换正常，表单内容正确
- 商户注册表单包含：用户名、密码、店铺名称（必填）、联系人、联系电话

- [ ] **Step 2: 构建生产包确认无 TypeScript 错误**

```bash
cd singularity-front && npm run build
```

Expected: 构建成功，无错误。

- [ ] **Step 3: Commit（如做过修复）**

```bash
git add -A
git commit -m "fix(frontend): resolve build errors after merchant register/login tabs"
```

---

## Self-Review Checklist

**1. Spec coverage:**
- [x] 注册页 Tab 切换 — Task 4
- [x] 登录页 Tab 切换 — Task 5
- [x] 商户注册表单字段 — Task 4 (username, password, shopName, contactName, contactPhone)
- [x] Vite 代理配置 — Task 1
- [x] 商户独立 JWT 管理 — Task 2 (client interceptor) + Task 3 (MerchantAuthContext)
- [x] App 集成 — Task 6

**2. Placeholder scan:**
- [x] 无 TBD / TODO
- [x] 每个步骤含完整代码
- [x] 每个步骤含精确文件路径

**3. Type consistency:**
- [x] `MerchantRegisterRequest` 字段名与 api/types.ts 一致
- [x] `MerchantLoginResponse` 结构（accessToken, expiresIn, merchant）与 MerchantAuthContext 使用一致
- [x] localStorage key (`merchantAccessToken`, `merchantExpiresIn`) 在 client.ts 和 MerchantAuthContext 中一致
