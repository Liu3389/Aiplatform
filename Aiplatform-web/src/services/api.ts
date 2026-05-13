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
      // 如果是 423 锁定，不跳转登录页
      if (result.message?.includes('锁定')) return result.data as unknown as T
      localStorage.removeItem('lingdian_token');
      localStorage.removeItem('lingdian_uid');
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
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
};

export const userApi = {
  request: <T>(opts: { method?: string; url: string; headers?: Record<string, string>; data?: any; params?: Record<string, any> }): Promise<T> =>
    request<T>(opts.url, { method: opts.method || 'GET', headers: opts.headers, body: opts.data ? JSON.stringify(opts.data) : undefined }),
  getInfo: () => request<{ id: number; username: string; email: string | null; balance: number; role: string; status: string; freeApiStrategy: string; freeQuota: number; dailyCallLimit: number; monthlyCallLimit: number; createTime: string }>('/user/info'),
  getBalance: () => request<number>('/user/balance'),
  getFreeQuota: () => request<{ usedCalls: number; maxCalls: number; usedTokens: number; maxTokens: number }>('/user/free-quota'),
  recharge: (amount: number, paymentMethod?: string) => request<null>('/user/recharge?amount=' + amount + (paymentMethod ? '&paymentMethod=' + paymentMethod : ''), { method: 'POST' }),
  changePassword: (oldPassword: string, newPassword: string) => request<null>('/user/change-password', { method: 'POST', body: JSON.stringify({ oldPassword, newPassword }) }),
  syncConversations: (conversations: any[]) => request<null>('/user/conversations/sync', { method: 'POST', body: JSON.stringify(conversations) }),
  fetchConversations: () => request<any[]>('/user/conversations/fetch'),
  clearCloudConversations: () => request<null>('/user/conversations/clear', { method: 'DELETE' }),
};

export interface UserApiKeyItem {
  id: number; userId: number; provider: string; displayName: string; apiKey: string; baseUrl: string; createTime: string;
}

export const userApiKeyApi = {
  list: () => request<UserApiKeyItem[]>('/user/api-keys'),
  add: (data: { provider: string; displayName?: string; apiKey: string; baseUrl?: string }) => request<UserApiKeyItem>('/user/api-keys', { method: 'POST', body: JSON.stringify(data) }),
  delete: (id: number) => request<null>('/user/api-keys/' + id, { method: 'DELETE' }),
  test: (data: { provider: string; apiKey: string; baseUrl?: string }) => request<null>('/user/api-keys/test', { method: 'POST', body: JSON.stringify(data) }),
};

export const modelStoreApi = {
  list: () => request<any[]>('/model-store/list'),
  detail: (id: number) => request<any>('/model-store/' + id),
};

export const apiKeyApi = {
  generate: (name: string, rateLimit: number) =>
    request<{ id: number; apiKey: string; name: string; status: string; rateLimit: number; createTime: string }>('/api-key/generate', { method: 'POST', body: JSON.stringify({ name, rateLimit }) }),
  list: () =>
    request<Array<{ id: number; apiKey: string; name: string; status: string; rateLimit: number; createTime: string; expireTime: string | null }>>('/api-key/list'),
  delete: (id: number) => request<null>('/api-key/' + id, { method: 'DELETE' }),
  enable: (id: number) => request<null>('/api-key/' + id + '/enable', { method: 'PUT' }),
  disable: (id: number) => request<null>('/api-key/' + id + '/disable', { method: 'PUT' }),
};

export const modelApi = {
  list: () => request<Array<{ id: number; modelName: string; provider: string; baseUrl: string; inputPrice: number; outputPrice: number; status: number }>>('/model-config/list'),
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
    const h: Record<string, string> = { 'Content-Type': 'application/json', 'X-API-Key': apiKeyValue };
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
          'X-API-Key': apiKeyValue,
        };
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

        while (true) {
          const { done, value } = await reader.read();
          if (done) {
            onDone({ inputTokens: 0, outputTokens: 0, cost: 0 });
            break;
          }
          buffer += decoder.decode(value, { stream: true });

          const events = buffer.split('\n\n');
          buffer = events.pop() || '';

          for (const event of events) {
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
  role: string; status: string; apiKeyCount: number; totalCalls: number;
  totalCost: number; createTime: string;
};

export const adminApi = {
  getStats: () => request<{ totalUsers: number; activeUsers: number; totalApiKeys: number; totalCalls: number; totalCost: number; todayCalls: number; todayCost: number }>('/admin/stats'),
  getUsers: () =>
    request<{ users: AdminUserInfo[] }>('/admin/users').then(r => (Array.isArray(r) ? r : (r as unknown as { users: AdminUserInfo[] }).users || [])),
  getAdmins: () =>
    request<{ users: AdminUserInfo[] }>('/admin/users').then(r =>
      ((Array.isArray(r) ? r : (r as unknown as { users: AdminUserInfo[] }).users) || [])
        .filter(u => u.role === 'ADMIN' || u.role === 'SUPER_ADMIN')
    ),
  getUserDetail: (userId: number) => request<AdminUserInfo>('/admin/user/' + userId),
  toggleUserStatus: (userId: number, status: 'ACTIVE' | 'DISABLED') => request<null>('/admin/user/status', { method: 'POST', body: JSON.stringify({ userId, status }) }),
  revokeAdminRole: (userId: number) => request<null>('/admin/revoke-role', { method: 'POST', body: JSON.stringify({ userId }) }),
  assignAdminRole: (userId: number, role: 'ADMIN' | 'SUPER_ADMIN') => request<null>('/admin/assign-role', { method: 'POST', body: JSON.stringify({ userId, role }) }),
  getAnalytics: () => request<{
    dailyUsage: Array<{ date: string; calls: number; tokens: number; cost: number }>;
    modelUsage: Array<{ model: string; calls: number; tokens: number; cost: number; color: string }>;
    hourlyTraffic: Array<{ hour: string; calls: number }>;
    topUsers: Array<{ rank: number; username: string; userId: number; calls: number; cost: number }>;
    revenueTrend: Array<{ month: string; revenue: number; costs: number }>;
    userGrowth: Array<{ month: string; total: number; active: number; new: number }>;
  }>('/admin/analytics/full'),
};

export { request, ApiError };
