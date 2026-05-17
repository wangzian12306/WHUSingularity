import { request } from './client'
import type { 
  ProductView,
  CreateProductRequest,
  UpdateProductRequest,
  ProductInventory,
  InventoryChangeLog,
  AdjustInventoryRequest
} from './types'

export const productApi = {
  create: (data: CreateProductRequest) =>
    request<ProductView>({ method: 'POST', url: '/api/product', data }),

  list: () =>
    request<ProductView[]>({ method: 'GET', url: '/api/product/list' }),

  get: (id: number) =>
    request<ProductView>({ method: 'GET', url: `/api/product/${id}` }),

  update: (id: number, data: UpdateProductRequest) =>
    request<ProductView>({ method: 'PUT', url: `/api/product/${id}`, data }),

  updateStatus: (id: number, status: number) =>
    request({ method: 'PUT', url: `/api/product/${id}/status`, data: { status } }),

  delete: (id: number) =>
    request({ method: 'DELETE', url: `/api/product/${id}` }),
}

export const inventoryApi = {
  getByProduct: (productId: number) =>
    request<ProductInventory>({ method: 'GET', url: `/api/inventory/product/${productId}` }),

  add: (productId: number, data: AdjustInventoryRequest) =>
    request({ method: 'POST', url: `/api/inventory/product/${productId}/add`, data }),

  adjust: (productId: number, data: AdjustInventoryRequest) =>
    request({ method: 'POST', url: `/api/inventory/product/${productId}/adjust`, data }),

  getLogs: (productId: number) =>
    request<InventoryChangeLog[]>({ method: 'GET', url: `/api/inventory/product/${productId}/logs` }),

  getMerchantLogs: () =>
    request<InventoryChangeLog[]>({ method: 'GET', url: '/api/inventory/merchant/logs' }),
}
