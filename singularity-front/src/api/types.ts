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
  userId: string
}

export interface SnagOrderResponse {
  orderId: string
}

export interface Order {
  orderId: string
  userId: string
  productId: string
  slotId: string
  status: string
  createTime: string
  updateTime: string
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
  status?: string
  page?: number
  size?: number
}

// Merchant
export interface Merchant {
  id: number
  username: string
  shopName: string
  contactName: string | null
  contactPhone: string | null
  address: string | null
  description: string | null
  status: number
  avatar: string | null
  createTime: string
}

export interface MerchantView {
  id: number
  username: string
  shopName: string
  contactName: string | null
  contactPhone: string | null
  address: string | null
  description: string | null
  status: number
  avatar: string | null
  createTime: string
}

export interface MerchantLoginRequest {
  username: string
  password: string
}

export interface MerchantLoginResponse {
  tokenType: string
  accessToken: string
  expiresIn: number
  merchant: MerchantView
}

export interface MerchantRegisterRequest {
  username: string
  password: string
  shopName: string
  contactName?: string
  contactPhone?: string
  address?: string
  description?: string
}

export interface UpdateMerchantRequest {
  shopName?: string
  contactName?: string
  contactPhone?: string
  address?: string
  description?: string
  avatar?: string
  password?: string
}
