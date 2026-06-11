import { request } from './client'
import type { SnagOrderRequest, SnagOrderResponse, OrderListResponse, OrderListParams } from './types'

export const orderApi = {
  snag: (data: SnagOrderRequest) =>
    request<SnagOrderResponse>({ method: 'POST', url: '/api/order/snag', data }),

  list: (params?: OrderListParams) =>
    request<OrderListResponse>({ method: 'GET', url: '/api/order/list', params }),

  listByProducts: (productIds: string[], params?: { status?: string; page?: number; size?: number }) =>
    request<OrderListResponse>({
      method: 'GET',
      url: '/api/order/list-by-products',
      params: { productIds, ...params },
    }),

  pay: (orderId: string, userId: string, userType?: string) =>
    request<void>({ method: 'POST', url: `/api/order/${orderId}/pay`, data: { userId, userType } }),
}
