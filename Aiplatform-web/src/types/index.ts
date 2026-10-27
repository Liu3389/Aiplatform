export interface UserInfo {
  id: number
  username: string
  displayName?: string | null
  avatarUrl?: string | null
  customAvatar?: boolean
  email: string | null
  emailVerified: boolean
  phone: string | null
  phoneVerified: boolean
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

export type SearchEngine = 'searxng' | 'jina' | 'tavily' | 'bing' | 'baidu' | 'auto'

export interface SearchResult {
  title: string
  url: string
  snippet: string
  source?: string
}

export interface SearchInfo {
  query: string
  results: SearchResult[]
  searching: boolean
  stage?: 'searching' | 'results' | 'empty'
  engine?: string
}

export interface ChatRequest {
  model: string
  messages: ChatMessage[]
  idempotencyKey?: string
  stream?: boolean
  maxTokens?: number
  temperature?: number
  mode?: string
  conversationId?: number
  fileUrls?: string[]
  searchEngine?: SearchEngine
  /** 文件元数据列表（传给后端持久化到消息 metadata，用于文件卡片跨会话显示） */
  files?: Array<{ fileId: string; fileName: string; fileSize: number; fileType: string; ossUrl?: string; uploadTime: number }>
}

export interface SystemStats {
  totalUsers: number
  activeUsers: number
  totalApiKeys: number
  totalCalls: number
  totalPointsConsumed: number
  todayCalls: number
  todayPointsConsumed: number
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
  thinkingTime?: number
  /** 关联文件URL列表（JSON数组字符串，后端透传） */
  fileUrls?: string
  /** 元数据（JSON字符串，含 files 数组：fileName/fileSize/fileType/ossUrl/uploadTime/expireTime） */
  metadata?: string
}

export interface ChatMessagePageVO {
  messages: BackendMessageVO[]
  hasMore: boolean
  nextCursorId: number | null
  nextCursorTime?: string | null
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
  pointsConsumed?: number
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
  /** 仅用于区分尚未拿到服务端ID的本地消息，提交云端时不会作为业务字段发送 */
  localOnly?: boolean
  /** 服务端消息创建时间；用于冷热表合并时保持真实时间顺序 */
  createTime?: string
  tokens?: string
  /** 本次平台对话实际扣除的点数 */
  pointsConsumed?: number
  feedback?: 'like' | 'dislike' | null  // 点赞/点踩反馈
  searchInfo?: SearchInfo | null  // 联网搜索来源标识（持久化到消息中，让用户在历史记录中可见）
  thinkingTime?: number  // 思考耗时（秒），完成后显示在消息下方
  /** 前端流式生命周期；异常断流或用户停止时保留在本地，便于明确提示和重试 */
  streamStatus?: 'streaming' | 'complete' | 'error' | 'cancelled'
  files?: Array<{  // 新增：文件信息
    fileId: string
    fileName: string
    fileSize: number
    fileType: string
    ossUrl?: string  // OSS下载地址
    uploadTime?: number  // 上传时间戳
    expireTime?: number  // 过期时间戳（后端写入，15天后过期，前端据此显示过期提示）
  }>
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
  chatEnabled: boolean
  accessible: boolean
  requiredLevel: number
  requiredLevelName: string | null
  upgradeHint: string | null
}

/** Public platform model summary. This deliberately contains no provider credential. */
export interface PlatformModelSummary {
  id: number
  displayName: string
  modelCode: string
  fixedPoints: number
  provider: string
  description: string | null
  supportsVision: number
  sortOrder: number
  accessLevel: number
  chatEnabled: boolean
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
