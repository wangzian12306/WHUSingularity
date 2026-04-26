import { request } from './client'
import type { 
  MerchantLoginRequest, 
  MerchantLoginResponse, 
  MerchantRegisterRequest, 
  MerchantView,
  UpdateMerchantRequest
} from './types'

export const merchantApi = {
  login: (data: MerchantLoginRequest) =>
    request<MerchantLoginResponse>({ method: 'POST', url: '/api/merchant/login', data }),

  register: (data: MerchantRegisterRequest) =>
    request<MerchantView>({ method: 'POST', url: '/api/merchant/register', data }),

  logout: () =>
    request({ method: 'POST', url: '/api/merchant/logout' }),

  profile: () =>
    request<MerchantView>({ method: 'GET', url: '/api/merchant/profile' }),

  updateProfile: (data: UpdateMerchantRequest) =>
    request<MerchantView>({ method: 'PUT', url: '/api/merchant/profile', data }),

  getMerchant: (id: number) =>
    request<MerchantView>({ method: 'GET', url: `/api/merchant/${id}` }),
}
