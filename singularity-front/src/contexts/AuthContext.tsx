import { createContext, useContext, useState, useEffect, useCallback, type ReactNode } from 'react'
import type { User } from '../api/types'
import { userApi } from '../api/user'

interface AuthContextType {
  user: User | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  const restore = useCallback(async () => {
    const token = localStorage.getItem('accessToken')
    if (!token) {
      setLoading(false)
      return
    }
    try {
      const res = await userApi.me()
      if (res.success && res.data) setUser(res.data)
    } catch {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('expiresIn')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { restore() }, [restore])

  const login = useCallback(async (username: string, password: string) => {
    const res = await userApi.login({ username, password })
    if (!res.success || !res.data) throw new Error(res.error?.message ?? 'Login failed')
    localStorage.setItem('accessToken', res.data.accessToken)
    localStorage.setItem('expiresIn', String(res.data.expiresIn))
    setUser(res.data.user)
  }, [])

  const logout = useCallback(async () => {
    try { await userApi.logout() } catch { /* ignore */ }
    localStorage.removeItem('accessToken')
    localStorage.removeItem('expiresIn')
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{ user, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
