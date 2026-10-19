import type { ApiResult, ChatMessagePageVO, ChatRequest, SearchInfo } from '@/types';
import { recordAuthSessionNotice, recoverUnauthorized } from './authResponseRecovery'

const BASE_URL = '/api';

class ApiError extends Error {
  constructor(
    public code: number,
    message: string,
    public data?: unknown
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

function httpUnauthorizedResult<T>(): ApiResult<T> {
  return { code: 401, message: 'HTTP 401', data: undefined as T, timestamp: Date.now() }
}

async function readApiResponse<T>(response: Response): Promise<ApiResult<T>> {
  if (response.ok) return await response.json()
  if (response.status === 401) {
    try {
      const body = await response.clone().json() as Partial<ApiResult<T>>
      if (body?.code === 401) return { ...httpUnauthorizedResult<T>(), ...body }
    } catch {}
    return httpUnauthorizedResult<T>()
  }
  throw new ApiError(response.status, 'HTTP ' + response.status + ': ' + response.statusText)
}

async function refreshTokenIfNeeded(): Promise<boolean> {
  const token = localStorage.getItem('lingdian_token');
  if (!token) return false;
  try {
    const r = await fetch(BASE_URL + '/auth/refresh-token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-User-Token': token },
    });
    if (r.ok) {
      const j: ApiResult<{ token: string }> = await r.json();
      if (j.code === 200 && j.data?.token) {
        localStorage.setItem('lingdian_token', j.data.token);
        try {
          const { useAppStore } = await import('@/stores/appStore');
          useAppStore.getState().setToken(j.data.token);
        } catch {}
        return true;
      }
    }
  } catch {}
  return false;
}

let lastRefreshTime = 0

async function request<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const url = BASE_URL + endpoint;

  // Token 续期：每 30 分钟检查一次
  const now = Date.now()
  if (now - lastRefreshTime > 30 * 60 * 1000) {
    lastRefreshTime = now
    refreshTokenIfNeeded()
  }

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  };

  const token = localStorage.getItem('lingdian_token');
  if (token) {
    headers['X-User-Token'] = token;
    lastRefreshTime = now;  // 确保首次就刷新
  }

  const response = await fetch(url, {
    ...options,
    headers,
  });

  let result: ApiResult<T> = await readApiResponse<T>(response)

  if (result.code === 401) {
    result = await recoverUnauthorized(
      result,
      async () => {
        const refreshedToken = localStorage.getItem('lingdian_token')
        if (!refreshedToken) return result
        const retryResponse = await fetch(url, {
          ...options,
          headers: { ...headers, 'X-User-Token': refreshedToken },
        })
        return await readApiResponse<T>(retryResponse)
      },
      refreshTokenIfNeeded,
      async () => {
        recordAuthSessionNotice(sessionStorage)
        localStorage.removeItem('lingdian_token')
        localStorage.removeItem('lingdian_uid')
        sessionStorage.removeItem('ld_active_apikey')
        try {
          const { useAppStore } = await import('@/stores/appStore')
          useAppStore.getState().logout()
        } catch {}
      },
    )
  }

  if (result.code === 401 && result.message?.includes('锁定')) {
    return result.data as unknown as T
  }

  if (result.code !== 200) {
    throw new ApiError(result.code, result.message, result.data);
  }

  return result.data;
}

export const authApi = {
  getRegisterCaptcha: () => request<{ captchaId: string; image: string; expiresIn: number }>('/auth/register/captcha'),
  getLoginCaptcha: () => request<{ captchaId: string; image: string; expiresIn: number }>('/auth/login/captcha'),
  register: (data: { username: string; password: string; captchaId: string; captchaCode: string }) =>
    request<{
      id: number;
      username: string;
      email: string | null;
      balance: number;
      role: string;
      status: string;
    }>('/auth/register', { method: 'POST', body: JSON.stringify(data) }),

  login: (data: { username: string; password: string; captchaId: string; captchaCode: string; twoFactorCode?: string }) =>
    request<{
      token: string;
      userInfo: { id: number; username: string; email: string | null; balance: number; role: string; status: string; freeApiStrategy: string; createTime: string };
    }>('/auth/login', { method: 'POST', body: JSON.stringify(data) }),

  logout: () => request<null>('/auth/logout', { method: 'POST' }),
  getOAuthUrl: (provider: string, purpose: 'login' | 'bind' = 'login') => request<{ url: string; state: string; provider: string; purpose: string }>(`/auth/oauth/url?provider=${encodeURIComponent(provider)}&purpose=${purpose}`),
  oauthCallback: (provider: string, code: string, state: string) => request<{ mode: 'login' | 'bind'; token?: string; deviceId?: string; userInfo?: { id: number; username: string; email: string | null; balance: number; role: string; status: string; freeApiStrategy: string; createTime: string }; requiresTwoFactor: boolean; challengeToken?: string }>('/auth/oauth/callback', { method: 'POST', body: JSON.stringify({ provider, code, state }) }),
  oauthComplete: (challengeToken: string, twoFactorCode: string) => request<{ token: string; deviceId: string; userInfo: { id: number; username: string; email: string | null; balance: number; role: string; status: string; freeApiStrategy: string; createTime: string } }>('/auth/oauth/complete', { method: 'POST', body: JSON.stringify({ challengeToken, twoFactorCode }) }),
  oauthBindings: () => request<{ provider: string; displayName: string; email: string | null }[]>('/auth/oauth/bindings'),
  oauthUnbind: (provider: string) => request<null>('/auth/oauth/unbind', { method: 'POST', body: JSON.stringify({ provider }) }),
  getUserInfo: () => request<{ id: number; username: string; email: string | null; emailVerified: boolean; phone: string | null; phoneVerified: boolean; balance: number; role: string; status: string; membership: string; createTime: string }>('/auth/userinfo'),
  getBalance: () => request<number>('/auth/balance'),
};

export interface UsageAnalytics {
  startDate: string
  endDate: string
  timezone: string
  calls: Array<{
    date: string
    model: string
    calls: number
    inputTokens: number
    outputTokens: number
  }>
  points: Array<{
    date: string
    model: string
    points: number
  }>
}

export const userApi = {
  request: <T>(opts: { method?: string; url: string; headers?: Record<string, string>; data?: any; params?: Record<string, any> }): Promise<T> =>
    request<T>(opts.url, { method: opts.method || 'GET', headers: opts.headers, body: opts.data ? JSON.stringify(opts.data) : undefined }),
  getInfo: () => request<{ id: number; username: string; email: string | null; emailVerified: boolean; phone: string | null; phoneVerified: boolean; balance: number; role: string; status: string; membership: string; freeApiStrategy: string; freeQuota: number; dailyCallLimit: number; monthlyCallLimit: number; points: number; membershipExpireTime?: string; createTime: string }>('/user/info'),
  getBalance: () => request<number>('/user/balance'),
  getUsageDaily: (days = 7) => request<{
    days: number
    daily: { date: string; label: string; tokens: number; calls: number; points: number }[]
    totalTokens: number
    totalCalls: number
    totalPoints: number
  }>('/user/usage/daily?days=' + days),
  getUsageAnalytics: () => request<UsageAnalytics>('/user/usage/analytics'),
  changePassword: (oldPassword: string, newPassword: string) => request<null>('/user/change-password', { method: 'POST', body: JSON.stringify({ oldPassword, newPassword }) }),
  syncConversations: (conversations: any[]) => request<{ version: number; count: number }>('/user/conversations/sync', { method: 'POST', body: JSON.stringify(conversations) }),
  fetchConversations: () => request<{ conversations: any[]; redisConversations: any[]; version: number }>('/user/conversations/fetch'),
  getConversationVersion: () => request<{ version: number }>('/user/conversations/version'),
  clearCloudConversations: () => request<null>('/user/conversations/clear', { method: 'DELETE' }),
  deleteConversation: (conversationId: string) => request<null>(`/user/conversations/${conversationId}`, { method: 'DELETE' }),
  getConversationMessages: (conversationId: string, limit = 15) => request<ChatMessagePageVO>('/user/conversations/' + conversationId + '/messages/latest?limit=' + limit),
  deleteMessage: (conversationId: string, role: string, content: string) =>
    request<null>('/user/conversations/' + conversationId + '/messages', { method: 'DELETE', body: JSON.stringify({ role, content }) }),
  getRechargeRecords: (page = 1, size = 20) => request<{ records: any[]; total: number; page: number; size: number }>('/user/recharge-records?page=' + page + '&size=' + size),
  getPointsBills: (page = 1, size = 20, changeType?: string) => {
    let url = '/user/points-bills?page=' + page + '&size=' + size
    if (changeType) url += '&changeType=' + changeType
    return request<{ records: any[]; total: number; page: number; size: number }>(url)
  },
};

export const apiKeyApi = {
  generate: (name: string, rateLimit: number) =>
    request<{ id: number; apiKey: string; name: string; status: string; rateLimit: number; createTime: string }>('/api-key/generate', { method: 'POST', body: JSON.stringify({ name, rateLimit }) }),
  list: () =>
    request<Array<{ id: number; apiKey: string; name: string; status: string; rateLimit: number; createTime: string; expireTime: string | null }>>('/api-key/list'),
  delete: (id: number) => request<null>('/api-key/' + id, { method: 'DELETE' }),
  enable: (id: number) => request<null>('/api-key/' + id + '/enable', { method: 'PUT' }),
  disable: (id: number) => request<null>('/api-key/' + id + '/disable', { method: 'PUT' }),
  updateRateLimit: (id: number, rateLimit: number) => request<null>('/api-key/' + id + '/rate-limit?rateLimit=' + rateLimit, { method: 'PUT' }),
};

export interface UserApiKeyItem {
  id: number
  provider: string
  displayName: string
  apiKey: string
  baseUrl?: string
  status: number
  createTime?: string
  lastUsedTime?: string
}

export const userApiKeyApi = {
  list: () => request<UserApiKeyItem[]>('/user/api-keys'),
  add: (data: { provider: string; displayName?: string; apiKey: string; baseUrl?: string }) =>
    request<UserApiKeyItem>('/user/api-keys', { method: 'POST', body: JSON.stringify(data) }),
  delete: (id: number) => request<null>('/user/api-keys/' + id, { method: 'DELETE' }),
};

export const modelApi = {
  list: () => request<import('@/types').PlatformModelSummary[]>('/platform-model/list'),
  getAvailable: () => request<import('@/types').AvailableModel[]>('/platform-model/available'),
};

export const notificationApi = {
  list: (unreadOnly = false) => request<any[]>('/user/notifications?unreadOnly=' + unreadOnly),
  unreadCount: () => request<{ unreadCount: number }>('/user/notifications/unread-count'),
  markRead: (id: number) => request<null>('/user/notifications/' + id + '/read', { method: 'PUT' }),
  markAllRead: () => request<null>('/user/notifications/read-all', { method: 'PUT' }),
};

export const couponApi = {
  list: () => request<any[]>('/user/my-coupons'),
};



export async function ensureApiKey(): Promise<string | undefined> {
  const token = localStorage.getItem('lingdian_token');
  if (!token) return undefined;

  const cached = sessionStorage.getItem('ld_active_apikey');
  if (cached) return cached;

  const h: Record<string, string> = { 'Content-Type': 'application/json', 'X-User-Token': token };

  try {
    const r = await fetch(BASE_URL + '/api-key/list', { headers: h });
    if (r.ok) {
      const j: ApiResult<Array<{ id: number; apiKey: string; status: string }>> = await r.json();
      if (j.code === 200 && j.data) {
        const active = j.data.find(k => k.status === 'ACTIVE' || String(k.status) === '1');
        if (active && active.apiKey) {
          sessionStorage.setItem('ld_active_apikey', active.apiKey);
          return active.apiKey;
        }
      }
    }
  } catch {}

  try {
    const r = await fetch(BASE_URL + '/api-key/generate', {
      method: 'POST', headers: h,
      body: JSON.stringify({ name: 'auto-default', rateLimit: 100 }),
    });
    if (r.ok) {
      const j: ApiResult<{ id: number; apiKey: string }> = await r.json();
      if (j.code === 200 && j.data?.apiKey) {
        sessionStorage.setItem('ld_active_apikey', j.data.apiKey);
        return j.data.apiKey;
      }
    }
  } catch {}

  return undefined;
}

type StreamEventCallbacks = {
  onMessage?: (content: string) => void
  onDone?: (data: {
    finished?: boolean
    message?: string
    inputTokens?: number
    outputTokens?: number
    cost?: number
    conversationId?: number
    pointsConsumed?: number
  }) => void
  onError?: (error: string) => void
  onSearch?: (stage: string | undefined, data: SearchInfo) => void
  onStatus?: (stage: string, message?: string) => void
  onCancelled?: () => void
}

/**
 * Consume the platform SSE contract in one place.
 *
 * The server deliberately has an explicit `done` event. Reaching EOF without
 * that event is an interrupted response, not a successful empty response.
 * Keeping this rule shared by text and file chats prevents the two paths from
 * drifting and leaving the UI stuck or showing a false settlement.
 */
async function consumeSseStream(response: Response, callbacks: StreamEventCallbacks, signal?: AbortSignal): Promise<void> {
  if (!response.body) {
    callbacks.onError?.('无法读取响应流')
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let abortedByCaller = false
  let timedOut = false
  let terminal: 'done' | 'error' | 'cancelled' | null = null
  let buffer = ''
  let eventName = ''
  let dataLines: string[] = []
  let chunkCount = 0
  let idleTimer: ReturnType<typeof setTimeout> | null = null

  const clearIdleTimer = () => {
    if (idleTimer) clearTimeout(idleTimer)
    idleTimer = null
  }

  const armIdleTimer = () => {
    clearIdleTimer()
    // 后端上游超时为 60 秒；前端再留出少量网络缓冲，但不能让断开的
    // 连接把界面永久留在“思考中”。
    idleTimer = setTimeout(() => {
      timedOut = true
      // response 已经建立后，新的 AbortController 无法取消原始 fetch；
      // 必须取消 reader，才能让 read() 结束并进入统一的超时终态。
      void reader.cancel('idle timeout').catch(() => {})
    }, 75_000)
  }

  const abortFromCaller = () => {
    abortedByCaller = true
    void reader.cancel('caller abort').catch(() => {})
  }

  if (signal) {
    if (signal.aborted) abortFromCaller()
    else signal.addEventListener('abort', abortFromCaller, { once: true })
  }

  const finishDone = (data: StreamEventCallbacks['onDone'] extends ((data: infer T) => void) | undefined ? T : never) => {
    if (terminal) return
    terminal = 'done'
    clearIdleTimer()
    callbacks.onDone?.(data)
  }

  const finishError = (message: string) => {
    if (terminal) return
    terminal = 'error'
    clearIdleTimer()
    callbacks.onError?.(message || '对话服务异常')
  }

  const dispatchEvent = () => {
    const payload = dataLines.join('\n').trim()
    const currentEvent = eventName || 'message'
    eventName = ''
    dataLines = []
    if (!payload || terminal) return

    if (payload === '[DONE]') {
      finishDone({ finished: true })
      return
    }

    try {
      const data = JSON.parse(payload) as Record<string, any>
      if (currentEvent === 'message') {
        if (typeof data.content === 'string' && data.content.length > 0) {
          callbacks.onMessage?.(data.content)
          chunkCount += 1
        }
      } else if (currentEvent === 'search') {
        callbacks.onSearch?.(data.stage, data as SearchInfo)
      } else if (currentEvent === 'status') {
        callbacks.onStatus?.(String(data.stage || ''), data.message)
      } else if (currentEvent === 'done') {
        finishDone(data)
      } else if (currentEvent === 'error') {
        finishError(String(data.message || '对话服务异常'))
      }
    } catch (error) {
      console.warn('[SSE] Cannot parse event:', payload.slice(0, 200), error)
    }
  }

  const processLine = (line: string) => {
    if (line === '') {
      dispatchEvent()
      return
    }
    if (line.startsWith(':')) return
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      // SSE permits multiple data lines in one event. Do not discard newlines
      // before JSON parsing; this also handles providers that pretty-print.
      dataLines.push(line.slice(5).replace(/^\s/, ''))
    }
  }

  try {
    armIdleTimer()
    while (true) {
      const { done, value } = await reader.read()
      if (value && value.byteLength > 0) {
        armIdleTimer()
        buffer += decoder.decode(value, { stream: !done })
        const lines = buffer.split(/\r?\n/)
        buffer = lines.pop() || ''
        for (const line of lines) processLine(line)
      }

      if (done) {
        // Flush the decoder and the final unterminated line/event. Spring's
        // emitter normally ends with a blank line, but proxies are allowed to
        // close immediately after the last data line.
        buffer += decoder.decode()
        if (buffer) processLine(buffer)
        dispatchEvent()
        if (abortedByCaller && !terminal) {
          terminal = 'cancelled'
          clearIdleTimer()
          callbacks.onCancelled?.()
        } else if (!terminal) {
          finishError(timedOut
            ? '响应超时，当前内容已保留，请稍后重试'
            : '响应连接已中断，当前内容已保留，请点击重试')
        }
        console.log(`[SSE] Stream ended. chunks=${chunkCount}, terminal=${terminal}`)
        break
      }
    }
  } catch (error) {
    if ((error as Error)?.name === 'AbortError') {
      if (timedOut) finishError('响应超时，当前内容已保留，请稍后重试')
      else if (abortedByCaller && !terminal) {
        terminal = 'cancelled'
        clearIdleTimer()
        callbacks.onCancelled?.()
      }
      return
    }
    finishError((error as Error)?.message || '连接错误')
  } finally {
    clearIdleTimer()
    signal?.removeEventListener('abort', abortFromCaller)
    try { reader.releaseLock() } catch {}
  }
}

export const chatApi = {
  nonStream: (
    data: ChatRequest & { customApiKey?: string; customBaseUrl?: string; customFormat?: string }
  ) => {
    const h: Record<string, string> = { 'Content-Type': 'application/json' };
    const token = localStorage.getItem('lingdian_token');
    if (token) h['X-User-Token'] = token;
    return fetch(BASE_URL + '/chat/completions', { method: 'POST', headers: h, body: JSON.stringify(data) })
      .then(async r => {
        if (!r.ok) throw new ApiError(r.status, 'HTTP ' + r.status);
        const j: ApiResult<string> = await r.json();
        if (j.code !== 200) throw new ApiError(j.code, j.message);
        return j.data;
      });
  },

  stream: (
    data: ChatRequest & { customApiKey?: string; customBaseUrl?: string; customFormat?: string },
    callbacks: {
      onMessage: (content: string) => void
      onDone: (finished: boolean, inputTokens?: number, outputTokens?: number, cost?: number, conversationId?: number, pointsConsumed?: number) => void
      onError: (error: string) => void
      onSearch?: (stage: string | undefined, data: SearchInfo) => void
      onCancelled?: () => void
    },
    signal?: AbortSignal
  ): Promise<void> => {
    const run = async () => {
      try {
        const headers: Record<string, string> = {
          'Content-Type': 'application/json',
        }
        const token = localStorage.getItem('lingdian_token')
        if (token) headers['X-User-Token'] = token

        console.log('[SSE] Starting stream request to:', BASE_URL + '/chat/stream')
        const response = await fetch(BASE_URL + '/chat/stream', {
          method: 'POST',
          headers,
          body: JSON.stringify({ ...data, stream: true }),
          signal
        })

        if (!response.ok) {
          const text = await response.text().catch(() => '')
          let msg = 'HTTP ' + response.status
          try { const j = JSON.parse(text); msg = j.message || msg } catch {}
          console.error('[SSE] HTTP error:', msg)
          callbacks.onError(msg)
          return
        }

        console.log('[SSE] Response received, starting to read stream...')
        await consumeSseStream(response, {
          onMessage: callbacks.onMessage,
          onSearch: callbacks.onSearch,
          onError: callbacks.onError,
          onCancelled: callbacks.onCancelled,
          onDone: (done) => callbacks.onDone(
            done.finished !== false,
            done.inputTokens,
            done.outputTokens,
            done.cost,
            done.conversationId,
            done.pointsConsumed,
          ),
        }, signal)
      } catch (err) {
        if ((err as Error).name === 'AbortError') {
          callbacks.onCancelled?.()
          return
        }
        console.error('[SSE] Stream error:', err)
        callbacks.onError((err as Error).message || '连接错误')
      }
    }
    return run()
  }
};

export type AdminUserInfo = {
  id: number; username: string; email: string | null; balance: number;
  role: string; status: string; membership: string; apiKeyCount: number; totalCalls: number;
    totalPointsConsumed: number; createTime: string;
};

export const adminApi = {
  getStats: () => request<{ totalUsers: number; activeUsers: number; totalApiKeys: number; totalCalls: number; totalPointsConsumed: number; todayCalls: number; todayPointsConsumed: number }>('/admin/stats'),
  getUsers: (params?: { page?: number; size?: number; search?: string; membership?: string; status?: string }) => {
    const qs = new URLSearchParams()
    if (params?.page) qs.set('page', String(params.page))
    if (params?.size) qs.set('size', String(params.size))
    if (params?.search) qs.set('search', params.search)
    if (params?.membership) qs.set('membership', params.membership)
    if (params?.status) qs.set('status', params.status)
    const q = qs.toString()
    return request<{ records: AdminUserInfo[]; total: number; page: number; size: number }>('/admin/users' + (q ? '?' + q : ''))
  },
  getAdmins: () =>
    adminApi.getUsers({ size: 100 }).then(r =>
      (r.records || []).filter(u => u.role === 'ADMIN' || u.role === 'SUPER_ADMIN')
    ),
  getUserDetail: (userId: number) => request<AdminUserInfo>('/admin/user/' + userId),
  toggleUserStatus: (userId: number, status: 'ACTIVE' | 'DISABLED') => request<null>('/admin/user/status', { method: 'POST', body: JSON.stringify({ userId, status }) }),
  revokeAdminRole: (userId: number) => request<null>('/admin/revoke-role', { method: 'POST', body: JSON.stringify({ userId }) }),
  assignAdminRole: (userId: number, role: 'ADMIN' | 'SUPER_ADMIN') => request<null>('/admin/assign-role', { method: 'POST', body: JSON.stringify({ userId, role }) }),
  getAnalytics: (days = 7, months = 6) => request<{
    totalUsers: number; activeUsers: number; totalApiKeys: number;
    totalCalls: number; totalPointsConsumed: number; todayCalls: number; todayPointsConsumed: number;
    users: Array<{
      id: number; username: string; email: string | null; balance: number;
      role: string; status: string; apiKeyCount: number; totalCalls: number;
      totalPointsConsumed: number; createTime: string;
    }>;
    dailyUsage: Array<Record<string, unknown>>;
    modelUsage: Array<Record<string, unknown>>;
    hourlyTraffic: Array<Record<string, unknown>>;
    topUsers: Array<Record<string, unknown>>;
    revenueTrend: Array<Record<string, unknown>>;
    userGrowth: Array<Record<string, unknown>>;
  }>(`/admin/analytics/full?days=${days}&months=${months}`),
  getPlatformRevenue: () => request<{
    totalRevenue: number; todayRevenue: number; monthRevenue: number;
    subscriptionRevenue: number; rechargeRevenue: number;
  }>('/admin/platform/revenue'),
  getSystemStatus: () => request<{
    backend: { status: string; uptimeSeconds: number; usedMemoryMB: number; maxMemoryMB: number };
    mysql: { status: string; latencyMs?: number; error?: string };
    redis: { status: string; latencyMs?: number; error?: string };
    docker: { status: string; latencyMs?: number; error?: string };
    timestamp: number;
  }>('/admin/components/status'),
};

// ==================== 系统控制 API（需二级密码） ====================

export interface ContainerStatus {
  name: string
  containerName: string
  status: string
  running: boolean
  state: string
  image: string
  startedAt: string
}

/** 从 sessionStorage 获取二级密码验证 Token */
function getSecondaryToken(): string {
  return sessionStorage.getItem('lingdian_secondary_token') || ''
}

export const systemControlApi = {
  verify: (password: string) =>
    request<{ token: string; expiresIn: number; dockerAvailable: boolean }>('/admin/system/verify', {
      method: 'POST', body: JSON.stringify({ password }),
    }),
  getStatus: () =>
    request<ContainerStatus[]>('/admin/system/status', {
      headers: { 'X-Secondary-Token': getSecondaryToken() },
    }),
  startContainer: (name: string) =>
    request<string>(`/admin/system/container/${name}/start`, {
      method: 'POST', headers: { 'X-Secondary-Token': getSecondaryToken() },
    }),
  stopContainer: (name: string) =>
    request<string>(`/admin/system/container/${name}/stop`, {
      method: 'POST', headers: { 'X-Secondary-Token': getSecondaryToken() },
    }),
  restartContainer: (name: string) =>
    request<string>(`/admin/system/container/${name}/restart`, {
      method: 'POST', headers: { 'X-Secondary-Token': getSecondaryToken() },
    }),
  startEnvironment: () =>
    request<string>('/admin/system/environment/start', {
      method: 'POST', headers: { 'X-Secondary-Token': getSecondaryToken() },
    }),
  stopEnvironment: () =>
    request<string>('/admin/system/environment/stop', {
      method: 'POST', headers: { 'X-Secondary-Token': getSecondaryToken() },
    }),
  getLogs: (name: string, lines = 100) =>
    request<string[]>(`/admin/system/logs/${name}?lines=${lines}`, {
      headers: { 'X-Secondary-Token': getSecondaryToken() },
    }),
}

export const pointsApi = {
  getBalance: () => request<number>('/user/points'),
  getInfo: () => request<import('@/types').UserPointsInfo>('/user/points/info'),
  getConsumptionSummary: () => request<{ todayConsumed: number; monthConsumed: number; totalConsumed: number; remainingPoints: number }>('/user/points/consumption-summary'),
  getBills: (page = 1, size = 20, changeType?: string) => {
    const qs = new URLSearchParams()
    qs.set('page', String(page))
    qs.set('size', String(size))
    if (changeType) qs.set('changeType', changeType)
    return request<any>('/user/points-bills?' + qs.toString())
  },
};

export const rechargeApi = {
  getRecords: (page = 1, size = 20) =>
    request<{ records: any[]; total: number; page: number; size: number }>('/user/recharge-records?page=' + page + '&size=' + size),
};

export interface UserCouponSummary {
  id: number;
  code: string;
  name: string;
  type: 'CASH';
  value: number;
  minAmount: number;
  applicablePlan?: string | null;
  expireTime?: string | null;
  status: 'AVAILABLE' | 'USED' | 'EXPIRED';
}

export const userCouponApi = {
  list: () => request<UserCouponSummary[]>('/user/coupons'),
};

export const bindApi = {
  bindEmail: (email: string, code: string) => request<null>('/user/bind-email', { method: 'POST', body: JSON.stringify({ email, code }) }),
  bindPhone: (phone: string, code: string) => request<null>('/user/bind-phone', { method: 'POST', body: JSON.stringify({ phone, code }) }),
  sendBindCode: (type: string, target: string) => request<null>('/user/send-bind-code', { method: 'POST', body: JSON.stringify({ type, target }) }),
};

export const twoFAApi = {
  getStatus: () => request<{ enabled: boolean; backupCodesRemaining: number }>('/user/2fa/status'),
  enable: () => request<{ secret: string }>('/user/2fa/enable', { method: 'POST' }),
  verify: (code: string) => request<{ enabled: boolean; backupCodes: string[] }>('/user/2fa/verify', { method: 'POST', body: JSON.stringify({ code }) }),
  disable: (code: string) => request<null>('/user/2fa/disable', { method: 'POST', body: JSON.stringify({ code }) }),
};

export const loginDeviceApi = {
  list: () => request<{ deviceId: string; info: string; lastActive: string; isCurrent: boolean }[]>('/user/login-devices'),
  remove: (deviceId: string) => request<null>('/user/login-devices/remove', { method: 'POST', body: JSON.stringify({ deviceId }) }),
  removeAll: (exceptCurrent?: boolean) => request<null>('/user/login-devices/remove-all', { method: 'POST', body: JSON.stringify({ exceptCurrent: exceptCurrent ? 'true' : 'false' }) }),
};

export const accountApi = {
  deactivateAccount: (password: string) => request<null>('/user/deactivate-account', { method: 'POST', body: JSON.stringify({ password, confirm: 'DEACTIVATE' }) }),
};

export const invoiceApi = {
  apply: (data: { title: string; taxNumber?: string; amount?: number }) =>
    request<null>('/user/invoice/apply', { method: 'POST', body: JSON.stringify(data) }),
};

export const subscriptionApi = {
  getPackages: () => request<any[]>('/user/subscriptions/packages'),
  getCurrent: () => request<any>('/user/subscriptions/current'),
  getHistory: () => request<any[]>('/user/subscriptions/history'),
  getHistoryPage: (page = 1, size = 20) => request<{ records: any[]; total: number; current: number; size: number }>('/user/subscriptions/history/page?page=' + page + '&size=' + size),
  purchase: (packageCode: string, cycle = 'MONTHLY', couponId?: number) =>
    request<any>('/user/subscriptions/purchase', { method: 'POST', body: JSON.stringify({ packageCode, cycle, couponId }) }),
  cancel: (subscriptionId: number) =>
    request<null>('/user/subscriptions/cancel/' + subscriptionId, { method: 'POST' }),
};

export const adminPointsApi = {
  recharge: (userId: number, points: number) =>
    request<null>('/admin/points/recharge', { method: 'POST', body: JSON.stringify({ userId, points }) }),
  adjust: (userId: number, points: number, reason?: string) =>
    request<null>('/admin/points/adjust', { method: 'POST', body: JSON.stringify({ userId, points, reason }) }),
  setFreeCount: (userId: number, count: number) =>
    request<null>('/admin/points/set-free-count', { method: 'POST', body: JSON.stringify({ userId, count }) }),
  distributeDaily: (userId?: number) =>
    request<any>('/admin/points/distribute-daily', { method: 'POST', body: JSON.stringify(userId ? { userId } : {}) }),
};

export const adminMembershipApi = {
  update: (userId: number, membership: string) =>
    request<null>('/admin/membership/update', { method: 'POST', body: JSON.stringify({ userId, membership }) }),
  setExpireTime: (userId: number, expireTime: string) =>
    request<null>('/admin/membership/set-expire-time', { method: 'POST', body: JSON.stringify({ userId, expireTime }) }),
};

// 余额调整（仅超级管理员可用，后端 403 校验）
export const adminBalanceApi = {
  adjust: (userId: number, balance: number, reason?: string) =>
    request<null>('/admin/balance/adjust', { method: 'POST', body: JSON.stringify({ userId, balance, reason }) }),
};

export const adminBatchApi = {
  updateStatus: (userIds: number[], status: number) =>
    request<{ successCount: number; totalCount: number }>('/admin/batch/update-status', { method: 'POST', body: JSON.stringify({ userIds, status }) }),
  grantPoints: (userIds: number[], points: number) =>
    request<{ successCount: number; totalCount: number }>('/admin/batch/grant-points', { method: 'POST', body: JSON.stringify({ userIds, points }) }),
  activateMembership: (userIds: number[], membership: string, days: number) =>
    request<{ successCount: number; totalCount: number }>('/admin/batch/activate-membership', { method: 'POST', body: JSON.stringify({ userIds, membership, days }) }),
};

export const adminLogApi = {
  list: (params?: { page?: number; size?: number; adminId?: number; operationType?: string; startDate?: string; endDate?: string }) => {
    const qs = new URLSearchParams()
    if (params?.page) qs.set('page', String(params.page))
    if (params?.size) qs.set('size', String(params.size))
    if (params?.adminId) qs.set('adminId', String(params.adminId))
    if (params?.operationType) qs.set('operationType', params.operationType)
    if (params?.startDate) qs.set('startDate', params.startDate)
    if (params?.endDate) qs.set('endDate', params.endDate)
    return request<any>('/admin/logs?' + qs.toString())
  },
};

export const adminStatisticsApi = {
  dashboard: (period = '7d') => request<any>('/admin/statistics/dashboard?period=' + period),
  userStats: () => request<any>('/admin/statistics/users'),
  conversationStats: () => request<any>('/admin/statistics/conversations'),
  daily: (startDate: string, endDate: string) =>
    request<any[]>('/admin/statistics/daily?startDate=' + startDate + '&endDate=' + endDate),
  weekly: (weeks = 4) => request<any[]>('/admin/statistics/weekly?weeks=' + weeks),
  monthly: (months = 6) => request<any[]>('/admin/statistics/monthly?months=' + months),
  userGrowth: (months = 6) => request<any[]>('/admin/statistics/user-growth?months=' + months),
  revenue: (months = 6) => request<any[]>('/admin/statistics/revenue?months=' + months),
  exportCsv: (type: string, startDate: string, endDate: string) => {
    const token = localStorage.getItem('lingdian_token')
    return fetch('/api/admin/statistics/export?type=' + type + '&startDate=' + startDate + '&endDate=' + endDate, {
      headers: { 'X-User-Token': token || '' },
    }).then(r => {
      if (!r.ok) throw new Error('导出失败')
      return r.blob()
    })
  },
};

export const adminUserApi = {
  getUsersAdvanced: (params?: { page?: number; size?: number; search?: string; membership?: string; status?: string; minBalance?: number; maxBalance?: number; startDate?: string; endDate?: string }) => {
    const qs = new URLSearchParams()
    if (params?.page) qs.set('page', String(params.page))
    if (params?.size) qs.set('size', String(params.size))
    if (params?.search) qs.set('search', params.search)
    if (params?.membership) qs.set('membership', params.membership)
    if (params?.status) qs.set('status', params.status)
    if (params?.minBalance !== undefined) qs.set('minBalance', String(params.minBalance))
    if (params?.maxBalance !== undefined) qs.set('maxBalance', String(params.maxBalance))
    if (params?.startDate) qs.set('startDate', params.startDate)
    if (params?.endDate) qs.set('endDate', params.endDate)
    return request<any>('/admin/users/advanced?' + qs.toString())
  },
  resetPassword: (userId: number, password: string) =>
    request<null>('/admin/reset-password', { method: 'POST', body: JSON.stringify({ userId, password }) }),
  banUser: (userId: number, reason?: string) =>
    request<null>('/admin/user/' + userId + '/ban', { method: 'PUT', body: JSON.stringify({ reason: reason || '管理员操作' }) }),
};

export const adminNotificationApi = {
  list: (params?: { page?: number; size?: number; isRead?: number }) => {
    const qs = new URLSearchParams()
    if (params?.page) qs.set('page', String(params.page))
    if (params?.size) qs.set('size', String(params.size))
    if (params?.isRead !== undefined) qs.set('isRead', String(params.isRead))
    return request<any>('/admin/notifications?' + qs.toString())
  },
  unreadCount: () => request<{ unreadCount: number }>('/admin/notifications/unread-count'),
  markRead: (id: number) => request<null>('/admin/notifications/' + id + '/read', { method: 'POST' }),
  markAllRead: () => request<null>('/admin/notifications/read-all', { method: 'POST' }),
  delete: (id: number) => request<null>('/admin/notifications/' + id, { method: 'DELETE' }),
  latest: (limit = 5) => request<any[]>('/admin/notifications/latest?limit=' + limit),
  send: (data: { target?: string; membership?: string; title: string; content: string; type?: string; days?: number }) =>
    request<{ sent: number; title: string }>('/admin/notifications/send', { method: 'POST', body: JSON.stringify(data) }),
};

export const adminCouponApi = {
  send: (data: { target?: string; membership?: string; amount: number; thresholdAmount?: number; applicablePlan?: string; days?: number; expireTime?: string }) =>
    request<{ sent: number; amount: number }>('/admin/coupons/send', { method: 'POST', body: JSON.stringify(data) }),
};

export const adminFinanceApi = {
  rechargeList: (params?: { page?: number; size?: number; startDate?: string; endDate?: string }) => {
    const qs = new URLSearchParams()
    if (params?.page) qs.set('page', String(params.page))
    if (params?.size) qs.set('size', String(params.size))
    if (params?.startDate) qs.set('startDate', params.startDate)
    if (params?.endDate) qs.set('endDate', params.endDate)
    return request<{ records: any[]; total: number; page: number; size: number }>('/admin/finance/recharge/list?' + qs.toString())
  },
  consumptionList: (params?: { page?: number; size?: number; startDate?: string; endDate?: string }) => {
    const qs = new URLSearchParams()
    if (params?.page) qs.set('page', String(params.page))
    if (params?.size) qs.set('size', String(params.size))
    if (params?.startDate) qs.set('startDate', params.startDate)
    if (params?.endDate) qs.set('endDate', params.endDate)
    return request<{ records: any[]; total: number; page: number; size: number }>('/admin/finance/consumption/list?' + qs.toString())
  },
  ordersList: (params?: { page?: number; size?: number; status?: string; startDate?: string; endDate?: string }) => {
    const qs = new URLSearchParams()
    if (params?.page) qs.set('page', String(params.page))
    if (params?.size) qs.set('size', String(params.size))
    if (params?.status) qs.set('status', params.status)
    if (params?.startDate) qs.set('startDate', params.startDate)
    if (params?.endDate) qs.set('endDate', params.endDate)
    return request<{ records: any[]; total: number; page: number; size: number; totalPaid: number }>('/admin/finance/orders/list?' + qs.toString())
  },
};

export type AiChargeReviewQueueItem = {
  requestId: string;
  userId: number;
  reservedPoints: number;
  modelName: string;
  upstreamStartedAt?: string | null;
  createTime: string;
  updateTime: string;
  reason: string;
};

export type AiChargeReviewAudit = {
  requestId: string;
  userId: number;
  adminId: number;
  action: 'REFUND' | 'SETTLE';
  actualPoints?: number | null;
  evidence: string;
  status: 'PENDING' | 'RESOLVED' | 'FAILED';
  lastError?: string | null;
  createTime: string;
  updateTime: string;
  completedAt?: string | null;
};

export type AiChargeReviewResolution = {
  requestId: string;
  userId: number;
  action: 'REFUND' | 'SETTLE';
  status: 'PENDING' | 'RESOLVED' | 'FAILED';
  intentStatus: string;
  actualPoints?: number | null;
  message: string;
};

type AdminPage<T> = { records: T[]; total: number; page: number; size: number };

export const adminAiChargeReviewApi = {
  queue: (params?: { page?: number; size?: number }) => {
    const qs = new URLSearchParams();
    if (params?.page) qs.set('page', String(params.page));
    if (params?.size) qs.set('size', String(params.size));
    return request<AdminPage<AiChargeReviewQueueItem>>('/admin/ai-charge-intents/manual-review?' + qs.toString());
  },
  history: (params?: { page?: number; size?: number }) => {
    const qs = new URLSearchParams();
    if (params?.page) qs.set('page', String(params.page));
    if (params?.size) qs.set('size', String(params.size));
    return request<AdminPage<AiChargeReviewAudit>>('/admin/ai-charge-intents/reconciliations?' + qs.toString());
  },
  resolve: (requestId: string, data: { action: 'REFUND' | 'SETTLE'; actualPoints?: number; evidence: string }) =>
    request<AiChargeReviewResolution>(`/admin/ai-charge-intents/${encodeURIComponent(requestId)}/resolve`, {
      method: 'POST', body: JSON.stringify(data),
    }),
};

export const adminStatsApi = {
  /** 统计数据对账验证 */
  consistencyCheck: () => request<{
    items: Array<{
      metric: string; source: string; sourceValue: string;
      rawTable: string; rawValue: string; diff: string;
      diffPercent: string; status: string; message: string;
    }>;
    summary: { total: number; ok: number; warn: number; error: number; overallStatus: string; elapsedMs: number };
  }>('/admin/stats/consistency-check'),
  /** 手动重算统计缓存 */
  recalculate: () => request<{ clearedKeys: number }>('/admin/stats/recalculate', { method: 'POST' }),
};

export const conversationApi = {
  getLatestMessages: (conversationId: number, limit = 10) =>
    request<ChatMessagePageVO>('/user/conversations/' + conversationId + '/messages/latest?limit=' + limit),
  getMessagesByCursor: (conversationId: number, lastId?: number, limit = 10, lastCreateTime?: string) =>
    request<ChatMessagePageVO>('/user/conversations/messages/page', {
      method: 'POST',
      body: JSON.stringify({
        conversationId,
        lastId: lastId || null,
        lastCreateTime: lastCreateTime || null,
        limit,
      }),
    }),
  clearCache: () => request<null>('/user/conversations/cache/clear', { method: 'POST' }),
};

export const couponUsageApi = {
  list: () => request<any[]>('/user/coupon-usage-records'),
};

export const marketplaceApi = {
  listKeys: () => request<any[]>('/marketplace/keys'),
  buyKey: (data: Record<string, any>) =>
    request<any>('/marketplace/buy-key', { method: 'POST', body: JSON.stringify(data) }),
};

export const adminPackageApi = {
  list: (includeInactive?: boolean) => {
    const qs = includeInactive ? '?includeInactive=true' : ''
    return request<any[]>('/admin/packages/list' + qs)
  },
  detail: (id: number) => request<any>('/admin/packages/' + id),
  create: (data: any) => request<any>('/admin/packages/create', { method: 'POST', body: JSON.stringify(data) }),
  update: (data: any) => request<null>('/admin/packages/update', { method: 'PUT', body: JSON.stringify(data) }),
  toggleStatus: (id: number, status: number) =>
    request<null>('/admin/packages/toggle-status', { method: 'POST', body: JSON.stringify({ id, status }) }),
  delete: (id: number) => request<null>('/admin/packages/' + id, { method: 'DELETE' }),
};

export const adminTestDataApi = {
  initConversationCache: (userId?: number) =>
    request<string>('/admin/test-data/init-conversation-cache' + (userId ? '?userId=' + userId : ''), { method: 'POST' }),
  initAllConversationCache: () =>
    request<string>('/admin/test-data/init-all-conversation-cache', { method: 'POST' }),
  checkConversationCache: (userId?: number) =>
    request<any>('/admin/test-data/check-conversation-cache' + (userId ? '?userId=' + userId : '')),
  clearConversationCache: (userId?: number) =>
    request<null>('/admin/test-data/clear-conversation-cache' + (userId ? '?userId=' + userId : ''), { method: 'DELETE' }),
};

export const chatFileApi = {
  chatWithFile: (file: File, question: string) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('question', question)
    const token = localStorage.getItem('lingdian_token')
    return fetch('/api/chat/with-file', {
      method: 'POST',
      headers: { 'X-User-Token': token || '' },
      body: formData,
    }).then(async r => {
      const j = await r.json()
      if (j.code !== 200) throw new Error(j.message || '请求失败')
      return j.data
    })
  },
  generateFile: (requirement: string, format: string) => {
    const body = JSON.stringify({ requirement, format })
    const token = localStorage.getItem('lingdian_token')
    return request<any>('/chat/generate-file', { method: 'POST', body })
  },
  sync: (conversationData: any) =>
    request<any>('/chat/sync', { method: 'POST', body: JSON.stringify(conversationData) }),
  exportContent: (content: string, fileName: string) => {
    const token = localStorage.getItem('lingdian_token')
    return fetch('/api/chat/export-content', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-User-Token': token || '' },
      body: JSON.stringify({ content, fileName }),
    })
  },
  loadFiles: (conversationId: number) =>
    request<string[]>('/chat/load?conversationId=' + conversationId),
};

export const aiDocApi = {
  /** 独立上传文件并解析 */
  upload: (file: File) => {
    const formData = new FormData()
    formData.append('files', file)
    const token = localStorage.getItem('lingdian_token')
    return fetch('/v1/file/upload', {
      method: 'POST',
      headers: { 'X-User-Token': token || '' },
      body: formData,
    }).then(async r => {
      const j = await r.json()
      if (j.code !== 200) throw new Error(j.message || '上传解析失败')
      const files = j.data?.files || []
      if (!Array.isArray(files) || files.length === 0) {
        throw new Error('上传解析失败，未返回文件信息')
      }
      return files[0] as { fileId: string; fileName: string; fileSize: number; status: string; ossUrl: string }
    })
  },
  /** 查询文件解析状态 */
  getParseStatus: (fileId: string) => {
    const token = localStorage.getItem('lingdian_token')
    return fetch('/v1/file/parse-status/' + fileId, {
      method: 'GET',
      headers: { 'X-User-Token': token || '' },
    }).then(async r => {
      const j = await r.json()
      if (j.code !== 200) throw new Error(j.message || '查询失败')
      return j.data as { fileId: string; fileName: string; status: string; createdAt: number }
    })
  },
  /** 轮询监听文件解析进度 */
  watchParseProgress: (fileId: string, callbacks: {
    onStatus?: (status: { fileId: string; status: string }) => void
    onDone?: () => void
    onError?: (error: Error) => void
  }, signal?: AbortSignal) => {
    const poll = async () => {
      const startTime = Date.now()
      const TIMEOUT_MS = 90000 // 90秒超时，大文件解析需要更长时间
      while (!signal?.aborted) {
        // 超时检查：防止后端异步解析卡住导致前端永远轮询
        if (Date.now() - startTime > TIMEOUT_MS) {
          callbacks.onError?.(new Error('文件解析超时，请重试'))
          return
        }
        try {
          const status = await aiDocApi.getParseStatus(fileId)
          callbacks.onStatus?.(status)
          if (status.status === 'SUCCESS') {
            callbacks.onDone?.()
            return
          } else if (status.status === 'FAILED') {
            callbacks.onError?.(new Error('解析失败'))
            return
          }
          await new Promise(resolve => setTimeout(resolve, 1000))
        } catch (e: any) {
          callbacks.onError?.(e)
          return
        }
      }
    }
    return poll()
  },
  /** 删除已上传的文件 */
  deleteUpload: (fileId: string) => {
    const token = localStorage.getItem('lingdian_token')
    return fetch('/v1/file/' + fileId, {
      method: 'DELETE',
      headers: { 'X-User-Token': token || '' },
    }).then(async r => {
      const j = await r.json()
      if (j.code !== 200) throw new Error(j.message || '删除失败')
      return true
    })
  },
  /** 基于已解析文件进行流式对话 (fileIds + question) */
  chatStreamWithFileId: (
    fileIds: string[],
    question: string,
    callbacks: {
      onContent?: (text: string) => void
      onDone?: (message: string, conversationId?: number, inputTokens?: number, outputTokens?: number, cost?: number, pointsConsumed?: number) => void
      onError?: (error: string) => void
      onStatus?: (stage: string, message?: string) => void
      onSearch?: (stage: string | undefined, data: SearchInfo) => void
      onCancelled?: () => void
    },
    signal?: AbortSignal,
    model?: string,
    conversationId?: string,
    filesMeta?: Array<{ fileId: string; fileName: string; fileSize: number; fileType: string; ossUrl?: string; uploadTime: number }>,
    fileUrls?: string[],
    idempotencyKey?: string
  ) => {
    const token = localStorage.getItem('lingdian_token')
    console.log('[SSE with files] Starting stream request to:', '/chat/stream-with-files')
    // 透传文件元数据和 fileUrls，让后端 saveChatMessages 持久化到消息的 metadata 字段
    const body: Record<string, any> = { fileIds, question, model, conversationId, mode: 'paid', idempotencyKey }
    if (filesMeta && filesMeta.length > 0) body.files = filesMeta
    if (fileUrls && fileUrls.length > 0) body.fileUrls = fileUrls
    return fetch('/chat/stream-with-files', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-User-Token': token || '' },
      body: JSON.stringify(body),
      signal,
    }).then(async response => {
      if (!response.ok) {
        const errText = await response.text()
        console.error('[SSE with files] HTTP error:', response.status, errText)
        throw new Error(errText || `请求失败 (${response.status})`)
      }
      console.log('[SSE with files] Response received, reading stream...')
      await consumeSseStream(response, {
        onMessage: callbacks.onContent,
        onSearch: callbacks.onSearch,
        onStatus: callbacks.onStatus,
        onError: callbacks.onError,
        onCancelled: callbacks.onCancelled,
        onDone: (done) => callbacks.onDone?.(
          done.message || '对话完成',
          done.conversationId,
          done.inputTokens,
          done.outputTokens,
          done.cost,
          done.pointsConsumed || 0,
        ),
      }, signal)
    })
  },
  exportResult: (content: string, format = 'md') => {
    return fetch('/api/v1/ai-doc/export', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content, format }),
    }).then(r => {
      if (!r.ok) throw new Error('导出失败')
      return r.blob()
    })
  },
};

export { request, ApiError };
