import { request } from './client'
import type {
  ProductCatalogView,
  ProductDetailResponse,
  ProductListParams,
  ProductPageResponse,
} from './types'

export const productCatalogApi = {
  list: (params?: ProductListParams) =>
    request<ProductPageResponse>({ method: 'GET', url: '/api/product/list', params }),

  get: (productId: string) =>
    request<ProductCatalogView>({ method: 'GET', url: `/api/product/${productId}` }),

  getWithStock: (productId: string) =>
    request<ProductDetailResponse>({ method: 'GET', url: `/api/product/${productId}/with-stock` }),

  metrics: () =>
    request<Record<string, unknown>>({ method: 'GET', url: '/api/product/metrics' }),
}
