import { request } from './client'
import type { ProductView, PageResponse } from './types'

export const publicProductApi = {
  list: (params?: { category?: string; keyword?: string; pageNo?: number; pageSize?: number }) =>
    request<PageResponse<ProductView>>({ method: 'GET', url: '/api/product/public/list', params }),
}
