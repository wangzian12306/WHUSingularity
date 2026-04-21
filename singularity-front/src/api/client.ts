import axios from 'axios'
import type { ApiResponse } from './types'

const api = axios.create({
  baseURL: '',
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (res) => res,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('expiresIn')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

export async function request<T>(config: Parameters<typeof api.request>[0]): Promise<ApiResponse<T>> {
  const res = await api.request<ApiResponse<T>>(config)
  return res.data
}
