import { Navigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { Spin } from 'antd'

export function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth()

  if (loading) return <Spin style={{ display: 'block', margin: '100px auto' }} size="large" />
  if (!user) return <Navigate to="/login" replace />
  return <>{children}</>
}
