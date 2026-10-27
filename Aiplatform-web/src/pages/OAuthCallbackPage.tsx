import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { authApi } from '@/services/api'
import { useAppStore } from '@/stores/appStore'
import { motion } from 'framer-motion'
import { runOnceWhilePending } from '@/utils/runOnceWhilePending'
import { oauthCallbackErrorMessage } from '@/utils/oauthCallbackError'

type OAuthSession = {
  token: string
  userInfo: {
    id: number
    username: string
    displayName?: string | null
    avatarUrl?: string | null
  }
  firstLogin?: boolean
}

export default function OAuthCallbackPage() {
  const [searchParams] = useSearchParams()
  const [error, setError] = useState('')
  const [status, setStatus] = useState('正在验证第三方身份…')
  const [requiresTwoFactor, setRequiresTwoFactor] = useState(false)
  const [challengeToken, setChallengeToken] = useState('')
  const [twoFactorCode, setTwoFactorCode] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [welcomeProfile, setWelcomeProfile] = useState<{ displayName: string; avatarUrl: string | null } | null>(null)
  const { setToken, setUserId, fetchUserInfo } = useAppStore()
  const navigate = useNavigate()

  const finishLogin = async (data: OAuthSession, provider?: string) => {
    setToken(data.token)
    setUserId(data.userInfo.id)
    await fetchUserInfo()
    localStorage.removeItem('oauth_provider')
    localStorage.removeItem('oauth_return_to')
    if (provider === 'qq' && data.firstLogin) {
      setWelcomeProfile({
        displayName: data.userInfo.displayName?.trim() || data.userInfo.username,
        avatarUrl: data.userInfo.avatarUrl || null,
      })
      setStatus('登录成功')
      return
    }
    setStatus('登录成功，正在跳转…')
    setTimeout(() => navigate('/chat', { replace: true }), 500)
  }

  useEffect(() => {
    let active = true
    const handleCallback = async () => {
      const provider = searchParams.get('provider') || (() => {
        try { return localStorage.getItem('oauth_provider') } catch { return null }
      })()
      const code = searchParams.get('code')
      const state = searchParams.get('state')
      const providerError = searchParams.get('error')
      if (code || state || providerError) {
        // Authorization codes are single-use; remove them from browser history as soon as captured.
        window.history.replaceState({}, document.title, window.location.pathname)
      }

      if (providerError || !code || !state) {
        if (active) {
          const providerName = provider === 'qq' ? 'QQ' : provider === 'google' ? 'Google' : '第三方'
          setError(providerError
            ? `${providerName} 授权未完成或已取消，请返回登录页重新发起。`
            : `授权信息不完整或已过期，请返回登录页重新发起${providerName}登录。`)
          try { localStorage.removeItem('oauth_provider') } catch {}
        }
        return
      }

      try {
        const data = await runOnceWhilePending(`oauth:${provider || 'auto'}:${state}`, () =>
          authApi.oauthCallback(provider, code, state),
        )
        if (!active) return
        const resolvedProvider = data.provider || provider || undefined
        try { localStorage.removeItem('oauth_provider') } catch {}
        if (data.mode === 'bind') {
          setStatus('第三方账号绑定成功，正在返回…')
          let returnTo: string | null = null
          try {
            returnTo = localStorage.getItem('oauth_return_to')
            localStorage.removeItem('oauth_return_to')
          } catch {}
          const safeReturnTo = returnTo?.startsWith('/') && !returnTo.startsWith('//') ? returnTo : '/chat'
          setTimeout(() => navigate(safeReturnTo, { replace: true }), 500)
          return
        }
        if (data.requiresTwoFactor && data.challengeToken) {
          setChallengeToken(data.challengeToken)
          setRequiresTwoFactor(true)
          setStatus(`${resolvedProvider === 'qq' ? 'QQ' : resolvedProvider === 'google' ? 'Google' : '第三方'}身份已验证，请完成二次验证。`)
          return
        }
        if (!data.token || !data.userInfo) throw new Error('登录响应缺少会话信息')
        await finishLogin(data as OAuthSession, resolvedProvider)
      } catch (err) {
        if (!active) return
        try {
          localStorage.removeItem('oauth_provider')
          localStorage.removeItem('oauth_return_to')
        } catch {}
        setError(oauthCallbackErrorMessage(err))
      }
    }
    void handleCallback()
    return () => { active = false }
  }, [])

  const completeTwoFactor = async (event: FormEvent) => {
    event.preventDefault()
    if (!twoFactorCode.trim()) {
      setError('请输入动态验证码或一次性备用码')
      return
    }
    setError('')
    setSubmitting(true)
    try {
      const data = await authApi.oauthComplete(challengeToken, twoFactorCode.trim())
      await finishLogin(data)
    } catch (err) {
      setError((err as Error).message || '二次验证失败')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-[#0B1020]">
      <div className="bg-aurora absolute inset-0" />
      <motion.div
        initial={{ opacity: 0, scale: 0.9 }}
        animate={{ opacity: 1, scale: 1 }}
        className="relative z-10 w-full max-w-sm p-8 rounded-2xl border border-white/[0.06] bg-white/[0.02] backdrop-blur-xl text-center"
      >
        {requiresTwoFactor ? (
          <form onSubmit={completeTwoFactor} className="space-y-4">
            <h1 className="text-white font-semibold">完成两步验证</h1>
            <p className="text-neutral-400 text-sm">输入动态验证码或一次性备用码</p>
            {error && <p role="alert" className="text-accent-rose text-sm">{error}</p>}
            <input
              autoComplete="one-time-code"
              value={twoFactorCode}
              onChange={event => setTwoFactorCode(event.target.value)}
              className="w-full rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-center text-white outline-none focus:border-violet-400"
              placeholder="验证码或备用码"
            />
            <button disabled={submitting} className="w-full rounded-xl bg-violet-600 py-3 text-sm font-semibold text-white disabled:opacity-50">
              {submitting ? '验证中…' : '验证并登录'}
            </button>
          </form>
        ) : welcomeProfile ? (
          <div className="space-y-5">
            {welcomeProfile.avatarUrl ? (
              <img
                src={welcomeProfile.avatarUrl}
                alt={`${welcomeProfile.displayName} 的 QQ 头像`}
                className="mx-auto h-20 w-20 rounded-full border border-white/10 object-cover"
                referrerPolicy="no-referrer"
              />
            ) : (
              <div className="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-violet-600 text-2xl font-semibold text-white" aria-hidden="true">
                {welcomeProfile.displayName.slice(0, 1).toUpperCase()}
              </div>
            )}
            <div className="space-y-2">
              <p className="text-xs font-medium tracking-[0.18em] text-violet-300">欢迎来到凌点 AI</p>
              <h1 className="text-xl font-semibold text-white">{welcomeProfile.displayName}</h1>
              <p className="text-sm leading-6 text-neutral-400">你的账号已创建，可以开始使用 AI 工作区。</p>
            </div>
            <div className="rounded-xl border border-white/10 bg-white/[0.04] px-4 py-3 text-sm text-neutral-300">
              登录方式：<span className="font-semibold text-white">QQ 登录</span>
            </div>
            <button
              type="button"
              onClick={() => navigate('/chat', { replace: true })}
              className="w-full rounded-xl bg-violet-600 py-3 text-sm font-semibold text-white transition hover:bg-violet-500 focus:outline-none focus:ring-2 focus:ring-violet-300 focus:ring-offset-2 focus:ring-offset-[#0B1020]"
            >
              进入 AI 工作区
            </button>
          </div>
        ) : error ? (
          <div className="space-y-4">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full border border-rose-400/20 bg-rose-400/10 text-rose-300" aria-hidden="true">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
                <circle cx="12" cy="12" r="9" />
                <path d="M12 8v4m0 4h.01" />
              </svg>
            </div>
            <h1 className="text-white font-semibold">无法完成第三方登录</h1>
            <p role="alert" className="text-accent-rose text-sm leading-6">{error}</p>
            <p className="text-neutral-400 text-xs leading-5">
              授权状态只能使用一次。请重新发起登录；如果仍然失败，请联系管理员检查对应的第三方登录服务连接。
            </p>
            <button
              type="button"
              onClick={() => navigate('/login', { replace: true })}
              className="w-full rounded-xl bg-violet-600 py-3 text-sm font-semibold text-white transition hover:bg-violet-500 focus:outline-none focus:ring-2 focus:ring-violet-300 focus:ring-offset-2 focus:ring-offset-[#0B1020]"
            >
              返回登录页并重新发起
            </button>
          </div>
        ) : (
          <div>
            <motion.div animate={{ rotate: 360 }} transition={{ duration: 1, repeat: Infinity, ease: 'linear' }} className="w-12 h-12 mx-auto mb-4">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#8B5CF6" strokeWidth="2">
                <path d="M12 2a10 10 0 019.95 9" strokeLinecap="round" />
              </svg>
            </motion.div>
            <p className="text-white text-sm">{status}</p>
          </div>
        )}
      </motion.div>
    </div>
  )
}
