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
