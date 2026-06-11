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
