export interface UserInfo {
  id: number
  username: string
  email: string | null
  balance: number
  role: 'USER' | 'ADMIN' | 'SUPER_ADMIN'
  status: 'ACTIVE' | 'DISABLED'
  membership: 'free' | 'silver' | 'gold' | 'diamond'
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
  mode?: string
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

export interface BackendMessageVO {
  id: number
  role: string
  content: string
  model: string
  tokens: number
  createTime: string
}

export interface ChatMessagePageVO {
  messages: BackendMessageVO[]
  hasMore: boolean
  nextCursorId: number | null
  totalCount?: number
}

export interface ConversationData {
  id: number
  title: string
  model: string
  messageCount: number
  lastMessageTime: string
  createTime: string
  updateTime: string
}

export interface TokenStats {
  inputTokens: number
  outputTokens: number
  cost: number
}

export interface FrontendConversation {
  id: string
  title: string
  messages: FrontendMessage[]
  createdAt: number
  updatedAt: number
  favorited?: boolean
}

export interface FrontendMessage {
  id?: number
  role: 'user' | 'assistant' | 'system'
  content: string
  tokens?: string
}

/** 模型可用列表响应（/api/platform-model/available） */
export interface AvailableModel {
  modelId: number
  displayName: string
  modelCode: string
  provider: string
  fixedPoints: number
  supportsVision: number
  sortOrder: number
  accessible: boolean
  requiredLevel: number
  requiredLevelName: string | null
  upgradeHint: string | null
}

/** 用户点数详情响应（/api/user/points/info） */
export interface UserPointsInfo {
  totalPoints: number
  temporaryPoints: number
  permanentPoints: number
  temporaryExpireTime: string | null
  membershipLabel: string
  dailyTemporaryPoints: number
  dailyPermanentPoints: number
  hasActiveSubscription: boolean
}

export const MEMBERSHIP_QUOTAS = {
  free:    { dailyTemporaryPoints: 30, dailyPermanentPoints: 0,   label: '普通用户', labelEn: 'Free',    color: '#94A3B8' },
  silver:  { dailyTemporaryPoints: 30, dailyPermanentPoints: 80,  label: '白银会员', labelEn: 'Silver',  color: '#C0C0C0' },
  gold:    { dailyTemporaryPoints: 30, dailyPermanentPoints: 150, label: '黄金会员', labelEn: 'Gold',    color: '#F59E0B' },
  diamond: { dailyTemporaryPoints: 30, dailyPermanentPoints: 300, label: '钻石会员', labelEn: 'Diamond', color: '#8B5CF6' },
} as const

export const MEMBERSHIP_PRICES = {
  silver:  { monthly: 15.00, quarterly: 40.00, yearly: 100.00 },
  gold:    { monthly: 30.00, quarterly: 80.00, yearly: 200.00 },
  diamond: { monthly: 60.00, quarterly: 160.00, yearly: 420.00 },
} as const