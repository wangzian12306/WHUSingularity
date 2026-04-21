import { request } from './client'
import type { Stock, StockChangeLog, InitStockRequest } from './types'

export const stockApi = {
  getStock: (productId: string) =>
    request<Stock>({ method: 'GET', url: `/api/stock/${productId}` }),

  list: () =>
    request<Stock[]>({ method: 'GET', url: '/api/stock/list' }),

  init: (data: InitStockRequest) =>
    request({ method: 'POST', url: '/api/stock/init', data }),

  getChangeLog: (params?: { productId?: string; status?: number }) =>
    request<StockChangeLog[]>({ method: 'GET', url: '/api/stock/change-log', params }),
}
