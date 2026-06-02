import { Navigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { useMerchantAuth } from '../contexts/MerchantAuthContext'
import { Spin } from 'antd'

export function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { user, loading: userLoading } = useAuth()
  const { merchant, loading: merchantLoading } = useMerchantAuth()

  if (userLoading || merchantLoading) return <Spin style={{ display: 'block', margin: '100px auto' }} size="large" />
  if (!user && !merchant) return <Navigate to="/login" replace />
  return <>{children}</>
}
