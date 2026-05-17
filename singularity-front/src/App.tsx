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
import WebMCPDemo from './pages/WebMCPDemo'

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
                <Route path="/webmcp-demo" element={<WebMCPDemo />} />
              </Route>
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </MerchantAuthProvider>
        </AuthProvider>
      </BrowserRouter>
    </ConfigProvider>
  )
}
