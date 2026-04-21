import { request } from './client'
import type { LoginRequest, LoginResponse, RegisterRequest, RegisterResponse, User, UserDetail, UpdateUserRequest, RechargeRequest } from './types'

export const userApi = {
  login: (data: LoginRequest) =>
    request<LoginResponse>({ method: 'POST', url: '/api/user/login', data }),

  register: (data: RegisterRequest) =>
    request<RegisterResponse>({ method: 'POST', url: '/api/user/register', data }),

  logout: () =>
    request({ method: 'POST', url: '/api/user/logout' }),

  me: () =>
    request<User>({ method: 'GET', url: '/api/user/me' }),

  list: () =>
    request<UserDetail[]>({ method: 'GET', url: '/api/user/list' }),

  update: (id: number, data: UpdateUserRequest) =>
    request<User>({ method: 'PUT', url: `/api/user/${id}`, data }),

  remove: (id: number) =>
    request({ method: 'DELETE', url: `/api/user/${id}` }),

  recharge: (id: number, data: RechargeRequest) =>
    request({ method: 'POST', url: `/api/user/${id}/recharge`, data }),
}
