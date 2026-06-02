import { request } from './client'
import type { 
  ProductView,
  CreateProductRequest,
  UpdateProductRequest
} from './types'

export const productApi = {
  create: (data: CreateProductRequest) =>
    request<ProductView>({ method: 'POST', url: '/api/merchant-product', data }),

  list: () =>
    request<ProductView[]>({ method: 'GET', url: '/api/merchant-product/list' }),

  get: (productId: string) =>
    request<ProductView>({ method: 'GET', url: `/api/merchant-product/${productId}` }),

  update: (productId: string, data: UpdateProductRequest) =>
    request<ProductView>({ method: 'PUT', url: `/api/merchant-product/${productId}`, data }),

  updateStatus: (productId: string, status: number) =>
    request({ method: 'PUT', url: `/api/merchant-product/${productId}/status`, data: { status } }),

  delete: (productId: string) =>
    request({ method: 'DELETE', url: `/api/merchant-product/${productId}` }),
}
