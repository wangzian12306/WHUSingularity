export interface ApiResponse<T = unknown> {
  success: boolean
  data?: T
  error?: ApiError
  message?: string
}

export interface ApiError {
  code: string
  message: string
}

export interface User {
  id: number
  username: string
  nickname: string | null
  role: 'normal' | 'admin'
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  tokenType: string
  accessToken: string
  expiresIn: number
  user: User
}

export interface RegisterRequest {
  username: string
  password: string
  nickname?: string
}

export type RegisterResponse = User

export interface UserDetail extends User {
  balance: number
  createTime: string
  updateTime: string
}

export interface UpdateUserRequest {
  nickname?: string
  role?: 'normal' | 'admin'
  balance?: number
  password?: string
}

export interface RechargeRequest {
  amount: number
}

// Stock

export interface Stock {
  productId: string
  availableQuantity: number
  reservedQuantity: number
  totalQuantity: number
}

export interface StockChangeLog {
  messageId: string
  productId: string
  changeQuantity: number
  changeType: number
  orderId: string | null
  status: number
  remark: string
  createTime: string
}

export interface InitStockRequest {
  productId: string
  totalQuantity: number
}

// Order

export interface SnagOrderRequest {
  slotId: string
}

export interface SnagOrderResponse {
  orderId: string
  slotId: string
  status: number
}

export interface Order {
  orderId: string
  actorId: string
  slotId: string
  status: number
  createTime: string
}

export interface OrderListResponse {
  content: Order[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

export interface OrderListParams {
  actorId?: string
  status?: number
  page?: number
  size?: number
}
