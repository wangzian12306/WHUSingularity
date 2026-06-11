import axios from 'axios'
import type { ApiResponse } from './types'

export function getApiErrorMessage(err: unknown, fallback = '请求失败，请稍后重试'): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as ApiResponse | string | undefined
    if (data && typeof data === 'object') {
      if (data.error?.message) return data.error.message
      if (data.message) return data.message
    }
    if (err.response?.status === 401) return '登录已过期，请重新登录'
    if (err.response?.status === 403) return '没有操作权限'
  }
  if (err instanceof Error && err.message) return err.message
  return fallback
}
