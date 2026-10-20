type OAuthCallbackFailure = {
  code?: unknown
  message?: unknown
}

export function oauthCallbackErrorMessage(error: unknown): string {
  const failure = (error && typeof error === 'object' ? error : {}) as OAuthCallbackFailure
  const message = typeof failure.message === 'string' ? failure.message.trim() : ''
  const normalizedMessage = message.toLowerCase()

  if ((failure.code === 400 || failure.code === undefined)
    && /(oauth\s*state|授权状态)/i.test(message)
    && /(无效|过期|invalid|expired|已使用)/i.test(message)) {
    return '这次 Google 授权已失效或已使用，请返回登录页重新点击 Google 登录。不要刷新或再次打开旧回调链接。'
  }

  if (failure.code === 502 || failure.code === 503 || normalizedMessage.includes('google 登录服务暂时不可用')) {
    return message || 'Google 登录服务暂时不可用，请稍后重试。'
  }

  return message || '第三方登录失败，请返回登录页重新发起。'
}
