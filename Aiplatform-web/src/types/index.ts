export interface UserInfo {
  id: number
  username: string
  email: string | null
  balance: number
  role: 'USER' | 'ADMIN' | 'SUPER_ADMIN'
  status: 'ACTIVE' | 'DISABLED'
  freeApiStrategy: 'NONE' | 'UNLIMITED' | 'QUOTA_BASED' | 'COUNT_LIMITED' | 'TIME_LIMITED' | 'MODEL_SPECIFIC'
  freeQuota: number
  dailyCallLimit: number
  monthlyCallLimit: number
  createTime: string
}

export interface LoginResponse {
  token: string
  userInfo: UserInfo
}

export interface ApiKeyInfo {
  id: number
  apiKey: string
  name: string
  status: 'ACTIVE' | 'DISABLED'
  rateLimit: number
  createTime: string
  expireTime: string | null
}

export interface ChatMessage {
  role: 'system' | 'user' | 'assistant'
  content: string
}

export interface ChatRequest {
  model: string
  messages: ChatMessage[]
  maxTokens?: number
  temperature?: number
}

export interface SystemStats {
  totalUsers: number
  activeUsers: number
  totalApiKeys: number
  totalCalls: number
  totalCost: number
  todayCalls: number
  todayCost: number
}

export interface AdminUserInfo extends UserInfo {
  apiKeyCount: number
  totalCalls: number
  totalCost: number
}

export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
  timestamp: number
}

export interface StreamMessage {
  content: string
}

export interface StreamDone {
  finished: boolean
  inputTokens: number
  outputTokens: number
  cost: number
}
