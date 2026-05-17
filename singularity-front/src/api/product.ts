import { request } from './client'
import type {
  PageResponse,
  ProductDetailResponse,
  ProductListParams,
  ProductView,
} from './types'

export const productCatalogApi = {
  list: (params?: ProductListParams) =>
    request<PageResponse<ProductView>>({ method: 'GET', url: '/api/product/public/list', params }),

  get: (productId: string) =>
    request<ProductView>({ method: 'GET', url: `/api/product/public/${productId}` }),

  getWithStock: (productId: string) =>
    request<ProductDetailResponse>({ method: 'GET', url: `/api/product/public/${productId}/with-stock` }),

  metrics: () =>
    request<Record<string, unknown>>({ method: 'GET', url: '/api/product/public/metrics' }),
}
