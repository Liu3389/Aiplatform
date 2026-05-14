export interface UserInfo {
  id: number
  username: string
  email: string | null
  balance: number
  role: 'USER' | 'ADMIN' | 'SUPER_ADMIN'
  status: 'ACTIVE' | 'DISABLED'
  membership: 'free' | 'plus' | 'pro'
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

export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
  timestamp: number
}

export const MEMBERSHIP_QUOTAS = {
  free: { dailyCalls: 30, dailyTokens: 50000, monthlyPoints: 0, label: '普通用户', labelEn: 'Free', color: '#94A3B8' },
  plus: { dailyCalls: 500, dailyTokens: 500000, monthlyPoints: 300, label: '畅享用户', labelEn: 'Plus', color: '#3B82F6' },
  pro: { dailyCalls: 999999, dailyTokens: 2000000, monthlyPoints: 1000, label: '尊享用户', labelEn: 'Pro', color: '#8B5CF6' },
} as const

export const MEMBERSHIP_PRICES = {
  plus: { monthly: 29.9, yearly: 299 },
  pro: { monthly: 99.9, yearly: 999 },
} as const
