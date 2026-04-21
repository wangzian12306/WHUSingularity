import { request } from './client'
import type { SnagOrderRequest, SnagOrderResponse, OrderListResponse, OrderListParams } from './types'

export const orderApi = {
  snag: (data: SnagOrderRequest) =>
    request<SnapOrderResponse>({ method: 'POST', url: '/api/order/snag', data }),

  list: (params?: OrderListParams) =>
    request<OrderListResponse>({ method: 'GET', url: '/api/order/list', params }),
}
