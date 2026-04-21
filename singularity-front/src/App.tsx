import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import { AuthProvider } from './contexts/AuthContext'
import { ProtectedRoute } from './components/AuthGuard'
import { AdminRoute } from './components/AdminGuard'
import AppLayout from './components/AppLayout'
import LoginPage from './pages/Login'
import RegisterPage from './pages/Register'
import AdminUserList from './pages/admin/AdminUserList'

function Placeholder() {
  return <div style={{ padding: 40 }}>秒杀主页 — 待实现</div>
}

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
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route element={<ProtectedRoute><AppLayout /></ProtectedRoute>}>
              <Route path="/" element={<Placeholder />} />
              <Route path="/admin/users" element={<AdminRoute><AdminUserList /></AdminRoute>} />
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    </ConfigProvider>
  )
}
