export type ApiEnvelope<T> = {
  code: number
  message?: string
  data?: T
}

export const AUTH_SESSION_NOTICE_KEY = 'lingdian_auth_notice'
export const AUTH_SESSION_EXPIRED_NOTICE = '登录状态已失效，请使用账号、密码和图形验证码重新登录。'

type NoticeStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

function isUnauthorizedError(error: unknown): boolean {
  if (typeof error !== 'object' || error === null || !('code' in error)) return false
  return Number((error as { code?: unknown }).code) === 401
}

export function recordAuthSessionNotice(storage: NoticeStorage) {
  try {
    storage.setItem(AUTH_SESSION_NOTICE_KEY, AUTH_SESSION_EXPIRED_NOTICE)
  } catch {
    // Storage may be disabled by the browser; authentication cleanup must still proceed.
  }
}

export function consumeAuthSessionNotice(storage: NoticeStorage): string {
  try {
    const notice = storage.getItem(AUTH_SESSION_NOTICE_KEY)
    if (!notice) return ''
    storage.removeItem(AUTH_SESSION_NOTICE_KEY)
    return notice
  } catch {
    return ''
  }
}

/** Retry a request once after token refresh; clear the session only if auth still fails. */
export async function recoverUnauthorized<T, E extends ApiEnvelope<T>>(
  initial: E,
  retryWithRefreshedToken: () => Promise<E>,
  refreshToken: () => Promise<boolean>,
  expireSession: () => void | Promise<void>,
): Promise<E> {
  if (initial.code !== 401 || initial.message?.includes('锁定')) return initial

  let refreshed = false
  try {
    refreshed = await refreshToken()
  } catch {
    refreshed = false
  }

  if (refreshed) {
    try {
      const retried = await retryWithRefreshedToken()
      if (retried.code !== 401 || retried.message?.includes('锁定')) return retried
      initial = retried
    } catch (error) {
      if (!isUnauthorizedError(error)) throw error
    }
  }

  await expireSession()
  return initial
}
