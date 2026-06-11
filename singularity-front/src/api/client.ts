import axios from 'axios'
import type { ApiResponse } from './types'

export const MERCHANT_AUTH_EXPIRED_EVENT = 'merchant-auth-expired'
export const USER_AUTH_EXPIRED_EVENT = 'user-auth-expired'

const api = axios.create({
  baseURL: '',
  headers: { 'Content-Type': 'application/json' },
})

const MERCHANT_PUBLIC_PATHS = ['/api/merchant/login', '/api/merchant/register']
const USER_PUBLIC_PATHS = ['/api/user/login', '/api/user/register']

api.interceptors.request.use((config) => {
  const url = config.url ?? ''
  const publicPaths = ['/api/product/public', ...MERCHANT_PUBLIC_PATHS, ...USER_PUBLIC_PATHS]
  if (publicPaths.some(p => url.startsWith(p))) {
    return config
  }
  const merchantPaths = ['/api/merchant', '/api/merchant-product', '/api/inventory']
  const isMerchantApi = merchantPaths.some(p => url.startsWith(p))
  const token = isMerchantApi
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
      const merchantPaths = ['/api/merchant', '/api/merchant-product', '/api/inventory']
      const isMerchantApi = merchantPaths.some(p => url.startsWith(p))
      if (isMerchantApi) {
        localStorage.removeItem('merchantAccessToken')
        localStorage.removeItem('merchantExpiresIn')
        window.dispatchEvent(new Event(MERCHANT_AUTH_EXPIRED_EVENT))
      } else {
        localStorage.removeItem('accessToken')
        localStorage.removeItem('expiresIn')
        window.dispatchEvent(new Event(USER_AUTH_EXPIRED_EVENT))
      }
    }
    return Promise.reject(error)
  },
)

export async function request<T>(config: Parameters<typeof api.request>[0]): Promise<ApiResponse<T>> {
  const res = await api.request<ApiResponse<T>>(config)
  return res.data
}
