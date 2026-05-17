# AI Gateway 前端开发指导文件

> 本文档基于对后端代码（30+ Controller, 100+ API端点）和前端代码的全面对比分析编写，旨在指导前端团队进行规范化的功能开发和问题修复。

---

## 目录

1. [技术栈与规范](#1-技术栈与规范)
2. [项目架构约定](#2-项目架构约定)
3. [后端 API 对接清单](#3-后端-api-对接清单)
4. [待实现功能清单](#4-待实现功能清单)
5. [类型定义规范](#5-类型定义规范)
6. [状态管理规范](#6-状态管理规范)
7. [安全规范](#7-安全规范)
8. [性能规范](#8-性能规范)
9. [用户体验规范](#9-用户体验规范)
10. [代码质量要求](#10-代码质量要求)
11. [开发优先级](#11-开发优先级)

---

## 1. 技术栈与规范

### 1.1 当前技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 框架 | React | 18.x |
| 语言 | TypeScript | 5.x |
| 构建 | Vite | 5.x |
| 路由 | react-router-dom | v6 |
| 状态管理 | Zustand | 最新 |
| UI 样式 | Tailwind CSS | 3.x |
| 图表 | ECharts | 5.x |
| Markdown | react-markdown + remark-gfm | 最新 |
| 动画 | framer-motion | 最新 |

### 1.2 推荐新增依赖

```json
{
  "react-query": "用于服务端状态管理与请求缓存（替代手写 fetch 逻辑）",
  "react-hook-form": "表单验证管理（替代手写表单逻辑）",
  "zod": "运行时类型校验（确保前后端类型一致）",
  "react-virtuoso": "虚拟列表（用于长对话消息列表）",
  "use-debounce": "搜索/输入防抖"
}
```

### 1.3 代码风格约定

- **禁止使用 `any`**：所有类型必须明确定义，API 返回值必须使用 `ApiResult<T>` 泛型
- **单个文件不超过 300 行**：超过必须拆分为多个文件
- **组件 Props 必须定义 interface**：禁止内联类型
- **使用 named export**：除页面组件外，不使用 `default export`
- **注释使用中文**：业务逻辑注释使用中文，代码注释使用 JSDoc

---

## 2. 项目架构约定

### 2.1 目录结构（建议重构后）

```
src/
├── App.tsx                          # 路由根组件
├── main.tsx                         # 入口
├── components/                      # 通用 UI 组件
│   ├── ui/                          # 基础 UI 组件（Button, Input, Modal, etc.）
│   ├── layout/                      # 布局组件
│   └── chat/                        # 对话相关组件
├── features/                        # 功能模块（按业务拆分）
│   ├── auth/                        # 认证模块
│   ├── chat/                        # 对话模块
│   ├── admin/                       # 管理后台模块
│   ├── billing/                     # 计费模块
│   ├── subscription/                # 订阅模块
│   └── security/                    # 安全模块
├── hooks/                           # 自定义 Hooks
│   ├── useAuth.ts                   # 认证相关
│   ├── useChat.ts                   # 对话核心逻辑
│   ├── useConversations.ts          # 会话列表管理
│   ├── useStreamChat.ts             # 流式对话
│   └── usePagination.ts             # 分页逻辑
├── services/                        # API 服务层
│   ├── api.ts                       # 基础请求 + 全部 API 模块
│   ├── queryClient.ts               # React Query 客户端配置
│   └── tokenManager.ts              # Token 管理（加密存储）
├── stores/                          # Zustand 全局状态
│   ├── appStore.ts                  # 用户/认证状态
│   ├── i18nStore.ts                 # 国际化
│   ├── themeStore.ts                # 主题
│   └── toastStore.ts                # 通知
├── types/                           # 类型定义
│   ├── index.ts                     # 公共类型
│   ├── api.ts                       # API 请求/响应类型
│   └── entities.ts                  # 实体类型
└── utils/                           # 工具函数
    ├── crypto.ts                    # 加密/解密
    ├── formatters.ts                # 格式化
    └── validators.ts                # 表单校验
```

### 2.2 组件拆分原则

- **页面组件（pages/ → features/）**：每个功能模块独立目录，包含该模块的页面、子组件、hooks、types
- **容器组件 vs 展示组件**：页面级为容器组件负责数据获取，子组件为展示组件只接收 props
- **自定义 Hook 优先级**：超过 30 行的副作用逻辑必须抽为 hook
- **禁止在组件内定义其他组件**：会导致每次渲染重新创建

### 2.3 数据流规范

```
┌─────────────────────────────────────────────────────┐
│                   React Query                        │
│  (服务端状态：API 数据缓存、自动刷新、乐观更新)        │
├─────────────────────────────────────────────────────┤
│                   Zustand                            │
│  (客户端状态：token、user、theme、lang、sidebar 等)    │
├─────────────────────────────────────────────────────┤
│                   URL 参数                            │
│  (页面状态：分页、筛选、tab 切换等可分享的状态)        │
└─────────────────────────────────────────────────────┘
```

---

## 3. 后端 API 对接清单

### 3.1 认证模块（已对接 ✅）

| 前端函数 | 后端端点 | 状态 |
|----------|----------|------|
| `authApi.register` | POST /auth/register | ✅ |
| `authApi.login` | POST /auth/login | ✅ |
| `authApi.logout` | POST /auth/logout | ✅ |
| `authApi.getOAuthUrl` | GET /auth/oauth/url | ✅ |
| `authApi.oauthCallback` | POST /auth/oauth/callback | ✅ |
| `authApi.oauthBind` | POST /auth/oauth/bind | ✅ |
| `authApi.oauthUnbind` | POST /auth/oauth/unbind | ✅ |
| `authApi.getUserInfo` | GET /auth/userinfo | ✅ |
| `authApi.getBalance` | GET /auth/balance | ✅ |

### 3.2 用户模块（已对接 ✅）

| 前端函数 | 后端端点 | 状态 |
|----------|----------|------|
| `userApi.getInfo` | GET /user/info | ✅ |
| `userApi.getBalance` | GET /user/balance | ✅ |
| `userApi.getFreeQuota` | GET /user/free-quota | ✅ |
| `userApi.recharge` | POST /user/recharge | ✅ |
| `userApi.changePassword` | POST /user/change-password | ✅ |
| `pointsApi.getBalance` | GET /user/points | ✅ |
| `pointsApi.getBills` | GET /user/points-bills | ✅ |
| `rechargeApi.rechargeWithCoupon` | POST /user/recharge-with-coupon | ✅ |
| `rechargeApi.getRecords` | GET /user/recharge-records | ✅ |
| `userCouponApi.list` | GET /user/coupons | ✅ |
| `userCouponApi.redeem` | POST /user/coupons/redeem | ✅ |
| `userCouponApi.use` | POST /user/coupons/use | ✅ |
| `bindApi.*` | POST /user/bind-email, bind-phone, send-bind-code | ✅ |
| `twoFAApi.*` | POST /user/2fa/* | ✅ |
| `loginDeviceApi.*` | GET/POST /user/login-devices/* | ✅ |
| `accountApi.deleteAccount` | POST /user/delete-account | ✅ |
| `invoiceApi.apply` | POST /user/invoice/apply | ✅ |

### 3.3 对话模块（部分对接 ⚠️）

| 前端函数 | 后端端点 | 状态 |
|----------|----------|------|
| `chatApi.nonStream` | POST /chat/completions | ✅ |
| `chatApi.stream` | POST /chat/stream | ✅ |
| `chatFileApi.chatWithFile` | POST /chat/with-file | ⚠️ API 已有，ChatPage 未集成 |
| `chatFileApi.generateFile` | POST /chat/generate-file | ⚠️ API 已有，UI 未实现 |
| `chatFileApi.sync` | POST /chat/sync | ⚠️ API 已有，未使用 |
| `chatFileApi.exportContent` | POST /chat/export-content | ⚠️ API 已有，未使用 |
| `chatFileApi.loadFiles` | GET /chat/load | ⚠️ API 已有，UI 未实现 |

### 3.4 会话管理（已对接 ✅）

| 前端函数 | 后端端点 | 状态 |
|----------|----------|------|
| `userApi.syncConversations` | POST /user/conversations/sync | ✅ |
| `userApi.fetchConversations` | GET /user/conversations/fetch | ✅ |
| `userApi.getConversationVersion` | GET /user/conversations/version | ✅ |
| `userApi.clearCloudConversations` | DELETE /user/conversations/clear | ✅ |
| `conversationApi.getLatestMessages` | GET /user/conversations/{id}/messages/latest | ✅ |
| `conversationApi.getMessagesByCursor` | POST /user/conversations/messages/page | ✅ |
| `conversationApi.clearCache` | POST /user/conversations/cache/clear | ✅ |

### 3.5 订阅模块（部分对接 ⚠️）

| 前端函数 | 后端端点 | 状态 |
|----------|----------|------|
| `subscriptionApi.getPackages` | GET /user/subscriptions/packages | ✅ |
| `subscriptionApi.getCurrent` | GET /user/subscriptions/current | ✅ |
| `subscriptionApi.getHistory` | GET /user/subscriptions/history | ✅ |
| `subscriptionApi.purchase` | POST /user/subscriptions/purchase | ⚠️ RechargePage 使用旧接口 |
| `subscriptionApi.cancel` | POST /user/subscriptions/cancel/{id} | ✅ |

### 3.6 管理员模块（严重不完整 ❌）

| 后端端点 | 前端函数 | 状态 |
|----------|----------|------|
| GET /admin/stats | `adminApi.getStats` | ✅ 对接 |
| GET /admin/users | `adminApi.getUsers` | ✅ 对接 |
| GET /admin/user/{userId} | `adminApi.getUserDetail` | ✅ 对接 |
| POST /admin/user/status | `adminApi.toggleUserStatus` | ✅ 对接 |
| POST /admin/assign-role | `adminApi.assignAdminRole` | ✅ 对接 |
| POST /admin/revoke-role | `adminApi.revokeAdminRole` | ✅ 对接 |
| GET /admin/analytics/full | `adminApi.getAnalytics` | ✅ 对接 |
| GET /admin/statistics/dashboard | `adminStatisticsApi.dashboard` | ⚠️ API 已定义，页面未使用 |
| GET /admin/statistics/users | `adminStatisticsApi.userStats` | ⚠️ API 已定义，页面未使用 |
| GET /admin/statistics/conversations | `adminStatisticsApi.conversationStats` | ⚠️ API 已定义，页面未使用 |
| GET /admin/statistics/daily | `adminStatisticsApi.daily` | ❌ 未使用 |
| GET /admin/statistics/weekly | `adminStatisticsApi.weekly` | ❌ 未使用 |
| GET /admin/statistics/monthly | `adminStatisticsApi.monthly` | ❌ 未使用 |
| GET /admin/statistics/export | `adminStatisticsApi.exportCsv` | ❌ 未使用 |
| GET /admin/statistics/user-growth | `adminStatisticsApi.userGrowth` | ❌ 未使用 |
| GET /admin/statistics/revenue | `adminStatisticsApi.revenue` | ❌ 未使用 |
| POST /admin/points/recharge | `adminPointsApi.recharge` | ❌ 未在 AdminPage 使用 |
| POST /admin/points/adjust | `adminPointsApi.adjust` | ❌ 未在 AdminPage 使用 |
| POST /admin/points/set-free-count | `adminPointsApi.setFreeCount` | ❌ 未在 AdminPage 使用 |
| POST /admin/points/distribute-daily | `adminPointsApi.distributeDaily` | ❌ 未在 AdminPage 使用 |
| POST /admin/membership/update | `adminMembershipApi.update` | ❌ 未在 AdminPage 使用 |
| POST /admin/membership/set-expire-time | `adminMembershipApi.setExpireTime` | ❌ 未在 AdminPage 使用 |
| POST /admin/batch/update-status | `adminBatchApi.updateStatus` | ❌ 未在 AdminPage 使用 |
| POST /admin/batch/grant-points | `adminBatchApi.grantPoints` | ❌ 未在 AdminPage 使用 |
| POST /admin/batch/activate-membership | `adminBatchApi.activateMembership` | ❌ 未在 AdminPage 使用 |
| GET /admin/logs | `adminLogApi.list` | ❌ 未使用 |
| GET/POST /admin/packages/* | `adminPackageApi.*` | ❌ 未在 AdminPage 使用 |
| PUT /admin/user/{userId}/ban | `adminUserApi.banUser` | ❌ 未在 AdminPage 使用 |
| POST /admin/reset-password | `adminUserApi.resetPassword` | ❌ 未在 AdminPage 使用 |
| GET /admin/users/advanced | `adminUserApi.getUsersAdvanced` | ❌ 未在 AdminPage 使用 |

---

## 4. 待实现功能清单

### 4.1 高优先级（P0 - 后端已有接口，前端缺失）

#### 管理后台完整重构（AdminPage）

当前 AdminPage 只有 4 个 Tab，需要扩展为完整的后台管理系统：

| Tab | 功能 | 使用的 API |
|-----|------|-----------|
| **数据大屏** | 完整仪表盘 KPI + 趋势图 | `adminStatisticsApi.dashboard` + `userStats` + `conversationStats` |
| **用户管理** | 搜索/筛选/封禁/解封/批量操作/重置密码 | `adminApi.getUsers` + `adminUserApi.*` + `adminBatchApi.*` |
| **点数管理** | 充值/调整/设置免费次数/批量发放/每日发放 | `adminPointsApi.*` |
| **会员管理** | 更新会员等级/设置到期时间/批量开通 | `adminMembershipApi.*` + `adminBatchApi.activateMembership` |
| **套餐管理** | CRUD + 上下架 | `adminPackageApi.*` |
| **统计报表** | 日/周/月统计 + 导出CSV | `adminStatisticsApi.daily/weekly/monthly/export` |
| **操作日志** | 管理员操作审计日志 | `adminLogApi.list` |
| **消息广播** | 发送通知 + 发放优惠券 | `adminNotificationApi.send` + `adminCouponApi.send` |

#### 对话功能增强（ChatPage）

| 功能 | 说明 | 后端 API |
|------|------|----------|
| 文件对话 | 上传文件后与 AI 对话 | POST /chat/with-file |
| AI 生成文件 | 按需求生成文档 | POST /chat/generate-file |
| 对话导出 | 导出对话为文件 | POST /chat/export-content |
| 关联文件加载 | 查看对话关联的文件 | GET /chat/load |
| Token 预估 | 发送前显示预估消耗 | 前端计算 + 后端返回 |

#### 充值/订阅页面重构（RechargePage）

| 功能 | 说明 |
|------|------|
| 套餐对比与购买 | 对接 `/user/subscriptions/purchase` 替代旧 `/user/recharge` |
| 优惠券使用 | 充值/购买时支持使用优惠券 |
| 点数账单 | 展示点数消费明细 |
| 充值记录 | 对接 `/user/recharge-records` |

### 4.2 中优先级（P1 - 现代 AI 平台必备功能）

| 功能 | 说明 |
|------|------|
| **Prompt 模板库** | 预设系统提示词模板，按场景分类 |
| **上下文窗口可视化** | 显示当前对话的 Token 用量和模型上限 |
| **消息编辑重发** | 编辑已发送消息后重新生成回复 |
| **对话分享** | 生成对话分享链接/图片 |
| **多模型对比** | 同一问题发送给多个模型并排对比 |
| **快捷键面板** | `/` 命令面板，显示所有快捷键 |
| **移动端适配优化** | 对话页移动端全屏模式 |

### 4.3 低优先级（P2 - 体验优化）

| 功能 | 说明 |
|------|------|
| 对话搜索 | 在所有对话中搜索关键词 |
| 对话标签/分类 | 给对话打标签，按标签筛选 |
| 暗色模式自动切换 | 跟随系统时间自动切换 |
| PWA 支持 | 添加到桌面，离线缓存 |
| 国际化完善 | 补全所有页面的英文翻译 |

---

## 5. 类型定义规范

### 5.1 与服务端类型对齐

后端 Java Entity/DTO/VO 必须与前端 TypeScript 类型对应。以下是必须修正的类型：

```typescript
// ========== 修正 ChatRequest（补全缺失字段）==========
interface ChatRequest {
  model: string
  messages: ChatMessage[]
  stream?: boolean           // ← 缺失
  maxTokens?: number
  temperature?: number
  mode?: string              // ← 缺失 (chat/completion)
  conversationId?: number    // ← 缺失
  fileUrls?: string[]        // ← 缺失（多模态文件链接）
}

// ========== 修正 ChatMessage（支持多模态）==========
interface ChatMessageContentPart {
  type: 'text' | 'image_url'
  text?: string
  image_url?: { url: string }
}

interface ChatMessage {
  role: 'system' | 'user' | 'assistant'
  content: string | ChatMessageContentPart[]  // ← 支持多模态
}

// ========== 修正 UserInfo（补全缺失字段）==========
interface UserInfo {
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
  points: number             // ← 缺失（点数余额）
  membershipExpireTime?: string  // ← 缺失（会员到期时间）
  createTime: string
  updateTime?: string        // ← 缺失
}

// ========== 新增：后端有但前端未定义的类型 ==========

interface DashboardStatsVO {
  totalUsers: number
  todayNewUsers: number
  totalConversations: number
  todayConversations: number
  todayActiveUsers: number
  systemStatus: string
  userTrend: TrendDataVO[]
  conversationTrend: TrendDataVO[]
  activeUserTrend: TrendDataVO[]
  quickStats: { totalMessages: number; totalPointsConsumed: number; apiSuccessRate: number; avgConversationLength: number }
}

interface TrendDataVO {
  date: string
  value: number
  growthRate: number
}

interface UserStatsVO {
  totalUsers: number
  todayNewUsers: number
  activeUsers7d: number
  inactiveUsers30d: number
  statusDistribution: Record<string, number>
  userSegments: { highActive: number; mediumActive: number; lowActive: number; inactive: number }
}

interface ConversationStatsVO {
  totalConversations: number
  totalMessages: number
  avgMessagesPerConv: number
  avgDuration: number
  todayConversations: number
  todayMessages: number
  modelUsage: ModelUsageStatsVO
}

interface PackageTemplate {
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
  status: number       // 0=下架, 1=上架
  sortOrder: number
}

interface UserSubscription {
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

interface PointsBill {
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

interface AdminOperationLog {
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
```

### 5.2 类型文件组织

```
src/types/
├── index.ts            # 导出所有类型
├── entities.ts         # 实体类型（UserInfo, ApiKeyInfo, PackageTemplate 等）
├── api.ts              # API 请求/响应类型（ChatRequest, LoginResponse, ApiResult 等）
├── admin.ts            # 管理后台专用类型
└── common.ts           # 通用工具类型
```

---

## 6. 状态管理规范

### 6.1 Zustand 使用规范

```typescript
// ✅ 正确：保持 Store 精简，只存客户端状态
interface AppState {
  token: string | null
  user: UserInfo | null
  sidebarCollapsed: boolean
  mobileSidebarOpen: boolean
  // 不要存：API 返回的数据列表（如 users, conversations）
}

// ✅ 正确：服务端状态使用 React Query
function useUsers(params: UserQueryParams) {
  return useQuery({
    queryKey: ['users', params],
    queryFn: () => adminApi.getUsers(params),
    staleTime: 30 * 1000,  // 30秒内不重新请求
  })
}
```

### 6.2 迁移到 React Query

当前 `api.ts` 中的 `request()` 函数应替换为 React Query：

```typescript
// queryClient.ts
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 2,
      staleTime: 30 * 1000,
      refetchOnWindowFocus: false,
    },
    mutations: {
      onError: (error) => {
        toastStore.getState().showToast({
          title: '操作失败',
          message: (error as ApiError).message || '未知错误',
          type: 'error',
        })
      },
    },
  },
})
```

---

## 7. 安全规范

### 7.1 Token 安全存储

```typescript
// ❌ 当前做法：明文存储
localStorage.setItem('lingdian_token', token)

// ✅ 应改为：加密存储
import { encrypt, decrypt } from '@/utils/crypto'

function secureSetItem(key: string, value: string) {
  const encrypted = encrypt(value)  // AES-GCM 加密
  sessionStorage.setItem(key, encrypted)  // 使用 sessionStorage 而非 localStorage
}

function secureGetItem(key: string): string | null {
  const encrypted = sessionStorage.getItem(key)
  if (!encrypted) return null
  try {
    return decrypt(encrypted)
  } catch {
    sessionStorage.removeItem(key)
    return null
  }
}
```

### 7.2 请求安全

- 所有 API 请求必须携带 `X-User-Token` header
- 敏感操作（删除、注销、密码修改）需要二次确认 + 密码验证
- 不在前端代码中硬编码任何密钥/密码
- 不在 console.log 中输出敏感信息
- API 错误信息不要直接展示给用户，使用通用错误提示

### 7.3 XSS 防护

- MarkdownRenderer 中 `dangerouslySetInnerHTML` 必须被移除或严格 sanitize
- 用户输入内容展示前必须转义

---

## 8. 性能规范

### 8.1 组件懒加载

```typescript
// ✅ 所有页面组件已使用 lazy() + Suspense，继续保持
const ChatPage = lazy(() => import('@/pages/ChatPage'))

// ✅ 大型组件内部也应懒加载
const HeavyChart = lazy(() => import('./HeavyChart'))
```

### 8.2 虚拟列表（长列表场景）

ChatPage 的消息列表、AdminPage 的用户列表必须使用虚拟列表：

```typescript
import { Virtuoso } from 'react-virtuoso'

<Virtuoso
  data={messages}
  itemContent={(index, message) => <MessageBubble message={message} />}
  followOutput="smooth"
/>
```

### 8.3 请求优化

- **防抖搜索**：所有搜索输入必须 300ms 防抖
- **请求去重**：相同参数的同时请求使用 React Query 自动去重
- **乐观更新**：增删改操作先更新 UI，再异步确认

```typescript
// ✅ 防抖搜索
import { useDebounce } from 'use-debounce'

function UserSearch() {
  const [search, setSearch] = useState('')
  const [debouncedSearch] = useDebounce(search, 300)

  const { data } = useQuery({
    queryKey: ['users', debouncedSearch],
    queryFn: () => adminApi.getUsers({ search: debouncedSearch }),
    enabled: debouncedSearch.length > 0,
  })
}
```

### 8.4 ECharts 实例管理

```typescript
// ✅ 正确的 ECharts 使用模式
function ChartComponent({ data }) {
  const chartRef = useRef<HTMLDivElement>(null)
  const instanceRef = useRef<echarts.ECharts>()

  useEffect(() => {
    if (!chartRef.current) return

    // 先销毁旧实例
    instanceRef.current?.dispose()

    // 创建新实例
    const instance = echarts.init(chartRef.current, undefined, { renderer: 'canvas' })
    instance.setOption(options)
    instanceRef.current = instance

    // Resize 监听
    const handleResize = () => instance.resize()
    window.addEventListener('resize', handleResize)

    return () => {
      window.removeEventListener('resize', handleResize)
      instance.dispose()  // ← 必须销毁！
    }
  }, [data])

  return <div ref={chartRef} style={{ width: '100%', height: 400 }} />
}
```

---

## 9. 用户体验规范

### 9.1 加载状态

所有异步操作必须有三种状态：**loading、success、error**

```typescript
// ✅ 统一的三态模式
function DataComponent() {
  const { data, isLoading, isError, error, refetch } = useQuery(...)

  if (isLoading) return <Skeleton />       // 骨架屏
  if (isError) return <ErrorState onRetry={refetch} message={error.message} />
  return <DataView data={data} />           // 成功状态
}
```

### 9.2 空状态

列表为空时必须显示有意义的空状态提示，而不是空白页面：

```
┌──────────────────────────┐
│                          │
│        [空状态图标]       │
│                          │
│     暂无对话记录          │
│   开始你的第一次 AI 对话   │
│                          │
│    [立即开始对话]         │
│                          │
└──────────────────────────┘
```

### 9.3 Toast 通知规范

| 操作 | Type | Title |
|------|------|-------|
| 创建成功 | success | 操作成功 |
| 删除成功 | success | 已删除 |
| 保存成功 | success | 已保存 |
| 请求失败 | error | 操作失败 |
| 参数错误 | warning | 请检查输入 |
| 权限不足 | error | 权限不足 |
| 网络超时 | error | 网络连接失败，请重试 |

### 9.4 确认弹窗

危险操作必须使用确认弹窗（`ConfirmDialog`），包含：
- 清晰的标题和描述
- 红色按钮表示危险操作
- "取消" 和 "确认" 两个按钮

---

## 10. 代码质量要求

### 10.1 组件大小限制

| 文件类型 | 最大行数 | 超过时的处理 |
|----------|----------|-------------|
| 页面组件 | 300 行 | 拆分为子组件 + hooks |
| 通用组件 | 200 行 | 拆分或提取逻辑到 hooks |
| Hook 文件 | 100 行 | 拆分为多个 hook |
| 工具函数 | 200 行 | 按功能拆分文件 |

### 10.2 必须抽离的自定义 Hooks

```typescript
// hooks/useConversations.ts - 对话列表管理（从 ChatPage 抽离）
// hooks/useStreamChat.ts - 流式对话核心逻辑（从 ChatPage 抽离）
// hooks/useAdminUsers.ts - 管理员用户管理（从 AdminPage 抽离）
// hooks/useAdminAnalytics.ts - 数据大屏核心逻辑（从 AdminPage 抽离）
// hooks/usePagination.ts - 通用分页逻辑
// hooks/useConfirm.ts - 确认弹窗状态管理
// hooks/useECharts.ts - ECharts 实例生命周期管理
```

### 10.3 禁止事项

- ❌ 禁止使用 `any` 类型
- ❌ 禁止在 useEffect 中使用 async 函数而不清理
- ❌ 禁止在组件内定义其他组件
- ❌ 禁止直接操作 DOM（使用 ref + React 状态驱动）
- ❌ 禁止超过 3 层的条件嵌套
- ❌ 禁止提交 `console.log`（使用统一的日志工具）
- ❌ 禁止硬编码颜色值（使用 Tailwind 类或 CSS 变量）
- ❌ 禁止 formData 直接使用（需用 react-hook-form）

### 10.4 命名规范

| 类型 | 命名格式 | 示例 |
|------|---------|------|
| React 组件 | PascalCase | `ChatPanel`, `AdminPage` |
| Hook | camelCase, use 前缀 | `useStreamChat`, `usePagination` |
| 类型/接口 | PascalCase | `ChatMessage`, `ApiResult<T>` |
| 工具函数 | camelCase | `formatBalance`, `encryptToken` |
| 常量 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT`, `STORAGE_KEYS` |
| 文件名 | 与主要导出一致 | `ChatPanel.tsx`, `useAuth.ts` |
| API 函数 | camelCase, 动词开头 | `getUsers`, `createPackage` |

---

## 11. 开发优先级

### Phase 1：补齐后端已有接口的前端实现（2-3 周）

| 序号 | 任务 | 涉及文件 | 预估工作量 |
|------|------|----------|-----------|
| 1 | AdminPage 重构：添加统计报表 Tab | AdminPage.tsx（拆分） | 3天 |
| 2 | AdminPage：添加点数管理 Tab | 同上 | 2天 |
| 3 | AdminPage：添加会员管理 Tab | 同上 | 2天 |
| 4 | AdminPage：添加套餐管理 Tab | 同上 | 2天 |
| 5 | AdminPage：添加操作日志 Tab | 同上 | 1天 |
| 6 | AdminPage：批量操作功能 | 同上 | 1天 |
| 7 | ChatPage：集成文件对话 | ChatPage.tsx | 2天 |
| 8 | ChatPage：Token 预估展示 | ChatPage.tsx | 1天 |
| 9 | RechargePage：对接新订阅接口 | RechargePage.tsx | 2天 |
| 10 | SecurityPage：完善点数账单 | SecurityPage.tsx | 1天 |

### Phase 2：类型安全与架构优化（1-2 周）

| 序号 | 任务 | 说明 |
|------|------|------|
| 1 | 补全 types/ 目录类型定义 | 按后端 Entity 补充全部类型 |
| 2 | 引入 React Query | 替换手写 fetch，实现缓存 |
| 3 | 引入 react-hook-form | 替换手写表单逻辑 |
| 4 | 引入 zod | 请求/响应运行时校验 |
| 5 | 组件拆分 | ChatPage、AdminPage、SecurityPage 拆分 |

### Phase 3：安全加固（1 周）

| 序号 | 任务 |
|------|------|
| 1 | Token + API Key 加密存储 |
| 2 | CSP 策略配置 |
| 3 | 输入输出 sanitize |
| 4 | 敏感操作二次确认 |

### Phase 4：现代 AI 平台特性（2-3 周）

| 序号 | 任务 |
|------|------|
| 1 | Prompt 模板库 |
| 2 | 多模态对话支持 |
| 3 | 上下文窗口可视化 |
| 4 | 对话分享功能 |
| 5 | 移动端全屏对话优化 |
| 6 | 虚拟列表性能优化 |

---

> **重要提醒**：
> 1. 每次开发前先阅读本指导文件
> 2. 开发完成后运行 `npm run typecheck` 确保无类型错误
> 3. 所有新增功能必须添加对应的 TypeScript 类型定义
> 4. 后端已有的接口优先实现，不要自定义接口路径
> 5. 遵循命名规范和代码质量要求