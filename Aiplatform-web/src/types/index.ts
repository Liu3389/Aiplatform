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
  points: number
  membershipExpireTime?: string
  createTime: string
  updateTime?: string
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

export interface ChatMessageContentPart {
  type: 'text' | 'image_url'
  text?: string
  image_url?: { url: string }
}

export interface ChatMessage {
  role: 'system' | 'user' | 'assistant'
  content: string | ChatMessageContentPart[]
}

export interface ChatRequest {
  model: string
  messages: ChatMessage[]
  stream?: boolean
  maxTokens?: number
  temperature?: number
  mode?: 'paid' | 'free'
  conversationId?: number
  fileUrls?: string[]
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
  free: { dailyTokens: 50000, monthlyPoints: 0, label: '普通用户', labelEn: 'Free', color: '#94A3B8' },
  plus: { dailyTokens: 500000, monthlyPoints: 300, label: '畅享用户', labelEn: 'Plus', color: '#3B82F6' },
  pro: { dailyTokens: 2000000, monthlyPoints: 1000, label: '尊享用户', labelEn: 'Pro', color: '#8B5CF6' },
} as const

export const MEMBERSHIP_PRICES = {
  plus: { monthly: 29.9, yearly: 299 },
  pro: { monthly: 99.9, yearly: 999 },
} as const

export interface DashboardStatsVO {
  totalUsers: number
  todayNewUsers: number
  totalConversations: number
  todayConversations: number
  todayActiveUsers: number
  systemStatus: string
  userTrend: TrendDataVO[]
  conversationTrend: TrendDataVO[]
  activeUserTrend: TrendDataVO[]
  quickStats: {
    totalMessages: number
    totalPointsConsumed: number
    apiSuccessRate: number
    avgConversationLength: number
  }
}

export interface TrendDataVO {
  date: string
  value: number
  growthRate: number
}

export interface UserStatsVO {
  totalUsers: number
  todayNewUsers: number
  activeUsers7d: number
  inactiveUsers30d: number
  statusDistribution: Record<string, number>
  userSegments: {
    highActive: number
    mediumActive: number
    lowActive: number
    inactive: number
  }
}

export interface ConversationStatsVO {
  totalConversations: number
  totalMessages: number
  avgMessagesPerConv: number
  avgDuration: number
  todayConversations: number
  todayMessages: number
  modelUsage: Record<string, { calls: number; tokens: number }>
}

export interface PackageTemplate {
  id: number
  packageCode: string
  packageName: string
  description: string
  identityLabel: string
  points: number
  price: number
  durationDays: number
  dailyCallLimit: number
  monthlyCallLimit: number
  maxTokensPerCall: number
  priorityLevel: number
  features: string[]
  status: number
  sortOrder: number
}

export interface UserSubscription {
  id: number
  userId: number
  packageId: number
  packageCode: string
  packageName: string
  identityLabel: string
  pointsGranted: number
  pricePaid: number
  startTime: string
  expireTime: string
  status: 'ACTIVE' | 'EXPIRED' | 'CANCELLED'
  autoRenew: number
  orderId: string
  couponId?: number
  discountAmount?: number
}

export interface PointsBill {
  id: number
  userId: number
  changeType: 'RECHARGE' | 'DEDUCT' | 'GRANT' | 'EXPIRE' | 'REFUND'
  pointsChange: number
  balanceBefore: number
  balanceAfter: number
  businessId?: string
  description: string
  modelName?: string
  expireTime?: string
  createTime: string
}

export interface AdminOperationLog {
  id: number
  adminId: number
  adminUsername: string
  operationType: string
  operationAction: string
  targetUserIds: number[]
  operationDetail: Record<string, unknown>
  ipAddress: string
  userAgent: string
  result: 'SUCCESS' | 'FAILED'
  errorMessage?: string
  createTime: string
}
