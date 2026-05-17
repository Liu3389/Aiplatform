import type { ApiResult, ChatRequest } from '@/types';

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

  if (!response.ok) {
    throw new ApiError(response.status, 'HTTP ' + response.status + ': ' + response.statusText);
  }

  const result: ApiResult<T> = await response.json();

  if (result.code !== 200) {
    if (result.code === 401) {
      await refreshTokenIfNeeded()
      if (result.message?.includes('锁定')) return result.data as unknown as T
      localStorage.removeItem('lingdian_token');
      localStorage.removeItem('lingdian_uid');
      sessionStorage.removeItem('ld_active_apikey');
      try {
        const { useAppStore } = await import('@/stores/appStore');
        useAppStore.getState().logout();
      } catch {}
    }
    throw new ApiError(result.code, result.message, result.data);
  }

  return result.data;
}

export const authApi = {
  register: (data: { username: string; password: string; email?: string }) =>
    request<{
      id: number;
      username: string;
      email: string | null;
      balance: number;
      role: string;
      status: string;
    }>('/auth/register', { method: 'POST', body: JSON.stringify(data) }),

  login: (data: { username: string; password: string }) =>
    request<{
      token: string;
      userInfo: { id: number; username: string; email: string | null; balance: number; role: string; status: string; freeApiStrategy: string; createTime: string };
    }>('/auth/login', { method: 'POST', body: JSON.stringify(data) }),

  logout: () => request<null>('/auth/logout', { method: 'POST' }),
  getOAuthUrl: (provider: string) => request<{ url: string; state: string; provider: string }>('/auth/oauth/url?provider=' + provider),
  oauthCallback: (provider: string, code: string, state: string) => request<{ token: string; userInfo: { id: number; username: string; email: string | null; balance: number; role: string; status: string; freeApiStrategy: string; createTime: string } }>('/auth/oauth/callback', { method: 'POST', body: JSON.stringify({ provider, code, state }) }),
  oauthBind: (provider: string, code: string) => request<null>('/auth/oauth/bind', { method: 'POST', body: JSON.stringify({ provider, code }) }),
  oauthUnbind: (provider: string) => request<null>('/auth/oauth/unbind', { method: 'POST', body: JSON.stringify({ provider }) }),
  getUserInfo: () => request<{ id: number; username: string; email: string | null; balance: number; role: string; status: string; membership: string; createTime: string }>('/auth/userinfo'),
  getBalance: () => request<number>('/auth/balance'),
};

export const userApi = {
  request: <T>(opts: { method?: string; url: string; headers?: Record<string, string>; data?: any; params?: Record<string, any> }): Promise<T> =>
    request<T>(opts.url, { method: opts.method || 'GET', headers: opts.headers, body: opts.data ? JSON.stringify(opts.data) : undefined }),
  getInfo: () => request<{ id: number; username: string; email: string | null; balance: number; role: string; status: string; membership: string; freeApiStrategy: string; freeQuota: number; dailyCallLimit: number; monthlyCallLimit: number; createTime: string }>('/user/info'),
  getBalance: () => request<number>('/user/balance'),
  getFreeQuota: () => request<{ usedCalls: number; maxCalls: number; usedTokens: number; maxTokens: number; monthlyPoints: number }>('/user/free-quota'),
  recharge: (amount: number) => request<null>('/user/recharge?amount=' + amount, { method: 'POST' }),
  changePassword: (oldPassword: string, newPassword: string) => request<null>('/user/change-password', { method: 'POST', body: JSON.stringify({ oldPassword, newPassword }) }),
  syncConversations: (conversations: any[]) => request<{ version: number; count: number }>('/user/conversations/sync', { method: 'POST', body: JSON.stringify(conversations) }),
  fetchConversations: () => request<{ conversations: any[]; version: number }>('/user/conversations/fetch'),
  getConversationVersion: () => request<{ version: number }>('/user/conversations/version'),
  clearCloudConversations: () => request<null>('/user/conversations/clear', { method: 'DELETE' }),
};

export const modelStoreApi = {
  list: () => request<any[]>('/platform-model/list'),
  detail: (id: number) => {
    return request<any[]>('/platform-model/list').then(models => models.find((m: any) => m.id === id) || null)
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

export const modelApi = {
  list: () => request<Array<{ id: number; modelName: string; provider: string; baseUrl: string; inputPrice: number; outputPrice: number; status: number }>>('/platform-model/list'),
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

export const chatApi = {
  nonStream: (
    apiKeyValue: string,
    data: ChatRequest & { customApiKey?: string; customBaseUrl?: string; customFormat?: string }
  ) => {
    const h: Record<string, string> = { 'Content-Type': 'application/json' };
    // API Key 是可选的，优先使用 X-User-Token 进行用户认证
    if (apiKeyValue) h['X-API-Key'] = apiKeyValue;
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
    apiKeyValue: string,
    data: ChatRequest & { customApiKey?: string; customBaseUrl?: string; customFormat?: string },
    onMessage: (content: string) => void,
    onDone: (result: { inputTokens: number; outputTokens: number; cost: number }) => void,
    onError: (error: string) => void
  ): AbortController => {
    const controller = new AbortController();
    const run = async () => {
      try {
        const headers: Record<string, string> = {
          'Content-Type': 'application/json',
        };
        // API Key 是可选的，优先使用 X-User-Token 进行用户认证
        if (apiKeyValue) {
          headers['X-API-Key'] = apiKeyValue;
        }
        const token = localStorage.getItem('lingdian_token');
        if (token) headers['X-User-Token'] = token;

        const response = await fetch(BASE_URL + '/chat/stream', {
          method: 'POST',
          headers,
          body: JSON.stringify({ ...data, stream: true }),
          signal: controller.signal
        });

        if (!response.ok) {
          const text = await response.text().catch(() => '');
          let msg = 'HTTP ' + response.status;
          try { const j = JSON.parse(text); msg = j.message || msg; } catch {}
          onError(msg);
          return;
        }

        const reader = response.body?.getReader();
        if (!reader) { onError('无法读取响应流'); return; }

        const decoder = new TextDecoder();
        let buffer = '';

        let receivedDone = false

        while (true) {
          const { done, value } = await reader.read();

          // Always decode value if present (Streams API may return data + done=true together)
          if (value) {
            buffer += decoder.decode(value, { stream: !done });
          }

          // Process all complete SSE events from buffer
          const events = buffer.split('\n\n');
          buffer = events.pop() || '';

          for (const event of events) {
            if (!event.trim()) continue;
            const lines = event.split('\n');
            let dataLine = '';
            let eventType = '';

            for (const line of lines) {
              if (line.startsWith('event:')) eventType = line.slice(6).trim();
              else if (line.startsWith('data:')) dataLine = line.slice(5).trim();
            }

            if (!dataLine || dataLine === '[DONE]') continue;

            try {
              const json = JSON.parse(dataLine);
              if (eventType === 'message') {
                if (json.content) onMessage(json.content);
              } else if (eventType === 'done') {
                receivedDone = true;
                onDone({
                  inputTokens: json.inputTokens || 0,
                  outputTokens: json.outputTokens || 0,
                  cost: json.cost || 0,
                });
              } else if (eventType === 'error') {
                onError(json.message || '发生错误');
              }
            } catch {}
          }

          if (done) {
            // Flush any remaining incomplete data in buffer (last event without \n\n)
            if (buffer.trim() && !receivedDone) {
              const lines = buffer.split('\n');
              let dataLine = '';
              let eventType = '';
              for (const line of lines) {
                if (line.startsWith('event:')) eventType = line.slice(6).trim();
                else if (line.startsWith('data:')) dataLine = line.slice(5).trim();
              }
              if (dataLine && dataLine !== '[DONE]') {
                try {
                  const json = JSON.parse(dataLine);
                  if (eventType === 'done') {
                    receivedDone = true;
                    onDone({ inputTokens: json.inputTokens || 0, outputTokens: json.outputTokens || 0, cost: json.cost || 0 });
                  }
                } catch {}
              }
            }
            if (!receivedDone) onDone({ inputTokens: 0, outputTokens: 0, cost: 0 });
            break;
          }
        }
      } catch (err) {
        if ((err as Error).name === 'AbortError') return;
        onError((err as Error).message || '连接错误');
      }
    };
    run();
    return controller;
  }
};

export type AdminUserInfo = {
  id: number; username: string; email: string | null; balance: number;
  role: string; status: string; membership: string; apiKeyCount: number; totalCalls: number;
  totalCost: number; createTime: string;
};

export const adminApi = {
  getStats: () => request<{ totalUsers: number; activeUsers: number; totalApiKeys: number; totalCalls: number; totalCost: number; todayCalls: number; todayCost: number }>('/admin/stats'),
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
    totalCalls: number; totalCost: number; todayCalls: number; todayCost: number;
    users: Array<{
      id: number; username: string; email: string | null; balance: number;
      role: string; status: string; apiKeyCount: number; totalCalls: number;
      totalCost: number; createTime: string;
    }>;
    dailyUsage: Array<Record<string, unknown>>;
    modelUsage: Array<Record<string, unknown>>;
    hourlyTraffic: Array<Record<string, unknown>>;
    topUsers: Array<Record<string, unknown>>;
    revenueTrend: Array<Record<string, unknown>>;
    userGrowth: Array<Record<string, unknown>>;
  }>(`/admin/analytics/full?days=${days}&months=${months}`),
};

export const pointsApi = {
  getBalance: () => request<number>('/user/points'),
  getBills: (page = 1, size = 20, changeType?: string) => {
    const qs = new URLSearchParams()
    qs.set('page', String(page))
    qs.set('size', String(size))
    if (changeType) qs.set('changeType', changeType)
    return request<any>('/user/points-bills?' + qs.toString())
  },
};

export const rechargeApi = {
  recharge: (amount: number) =>
    request<null>('/user/recharge?amount=' + amount, { method: 'POST' }),
  rechargeWithCoupon: (amount: number, couponId?: number) =>
    request<Map<string, any>>('/user/recharge-with-coupon', { method: 'POST', body: JSON.stringify({ amount, couponId }) }),
  getRecords: (page = 1, size = 20) =>
    request<any[]>('/user/recharge-records?page=' + page + '&size=' + size),
};

export const userCouponApi = {
  list: () => request<any[]>('/user/coupons'),
  redeem: (redeemCode: string) => request<null>('/user/coupons/redeem', { method: 'POST', body: JSON.stringify({ redeemCode }) }),
  use: (code: string) => request<any>('/user/coupons/use', { method: 'POST', body: JSON.stringify({ code }) }),
};

export const bindApi = {
  bindEmail: (email: string, code: string) => request<null>('/user/bind-email', { method: 'POST', body: JSON.stringify({ email, code }) }),
  bindPhone: (phone: string, code: string) => request<null>('/user/bind-phone', { method: 'POST', body: JSON.stringify({ phone, code }) }),
  sendBindCode: (type: string, target: string) => request<null>('/user/send-bind-code', { method: 'POST', body: JSON.stringify({ type, target }) }),
};

export const twoFAApi = {
  getStatus: () => request<{ enabled: boolean }>('/user/2fa/status'),
  enable: () => request<{ secret: string }>('/user/2fa/enable', { method: 'POST' }),
  verify: (code: string) => request<boolean>('/user/2fa/verify', { method: 'POST', body: JSON.stringify({ code }) }),
  disable: (code: string) => request<null>('/user/2fa/disable', { method: 'POST', body: JSON.stringify({ code }) }),
};

export const loginDeviceApi = {
  list: () => request<any[]>('/user/login-devices'),
  remove: (deviceId: string) => request<null>('/user/login-devices/remove', { method: 'POST', body: JSON.stringify({ deviceId }) }),
  removeAll: (exceptCurrent?: boolean) => request<null>('/user/login-devices/remove-all', { method: 'POST', body: JSON.stringify({ exceptCurrent: exceptCurrent ? 'true' : 'false' }) }),
};

export const accountApi = {
  deleteAccount: (password: string) => request<null>('/user/delete-account', { method: 'POST', body: JSON.stringify({ password, confirm: 'DELETE' }) }),
};

export const invoiceApi = {
  apply: (data: { title: string; taxNumber?: string; amount?: number }) =>
    request<null>('/user/invoice/apply', { method: 'POST', body: JSON.stringify(data) }),
};

export const subscriptionApi = {
  getPackages: () => request<any[]>('/user/subscriptions/packages'),
  getCurrent: () => request<any>('/user/subscriptions/current'),
  getHistory: () => request<any[]>('/user/subscriptions/history'),
  purchase: (packageCode: string, couponId?: number) =>
    request<any>('/user/subscriptions/purchase', { method: 'POST', body: JSON.stringify({ packageCode, couponId }) }),
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

export const conversationApi = {
  getLatestMessages: (conversationId: number, limit = 10) =>
    request<any>('/user/conversations/' + conversationId + '/messages/latest?limit=' + limit),
  getMessagesByCursor: (conversationId: number, lastId?: number, limit = 10) =>
    request<any>('/user/conversations/messages/page', {
      method: 'POST',
      body: JSON.stringify({ conversationId, lastId: lastId || null, limit }),
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
  chat: (file: File, question: string) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('question', question)
    const token = localStorage.getItem('lingdian_token')
    return fetch('/api/v1/ai-doc/chat', {
      method: 'POST',
      headers: { 'X-User-Token': token || '' },
      body: formData,
    }).then(async r => {
      const j = await r.json()
      if (j.code !== 200) throw new Error(j.message || '请求失败')
      return j.data
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

export const deprecatedSubscriptionApi = {
  get: () => request<any>('/user/subscription'),
  subscribe: (plan: string, paymentMethod: string) =>
    request<null>('/user/subscription/subscribe', { method: 'POST', body: JSON.stringify({ plan, paymentMethod }) }),
  cancel: () => request<null>('/user/subscription/cancel', { method: 'POST' }),
};

export { request, ApiError };
