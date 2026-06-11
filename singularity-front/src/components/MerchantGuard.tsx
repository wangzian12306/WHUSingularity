import { Navigate } from 'react-router-dom'
import { useMerchantAuth } from '../contexts/MerchantAuthContext'
import { Spin } from 'antd'

export function MerchantRoute({ children }: { children: React.ReactNode }) {
  const { merchant, loading } = useMerchantAuth()
  if (loading) return <Spin style={{ display: 'block', margin: '100px auto' }} size="large" />
  if (!merchant) return <Navigate to="/login" replace />
  return <>{children}</>
}
