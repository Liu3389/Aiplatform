import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { authApi } from '@/services/api'
import { useAppStore } from '@/stores/appStore'
import { motion } from 'framer-motion'
import { runOnceWhilePending } from '@/utils/runOnceWhilePending'
import { oauthCallbackErrorMessage } from '@/utils/oauthCallbackError'

export default function OAuthCallbackPage() {
  const [searchParams] = useSearchParams()
  const [error, setError] = useState('')
  const [status, setStatus] = useState('正在验证第三方身份…')
  const [requiresTwoFactor, setRequiresTwoFactor] = useState(false)
  const [challengeToken, setChallengeToken] = useState('')
  const [twoFactorCode, setTwoFactorCode] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const { setToken, setUserId, fetchUserInfo } = useAppStore()
  const navigate = useNavigate()

  const finishLogin = async (data: { token: string; userInfo: { id: number } }) => {
    setToken(data.token)
    setUserId(data.userInfo.id)
    await fetchUserInfo()
    localStorage.removeItem('oauth_provider')
    localStorage.removeItem('oauth_return_to')
    setStatus('登录成功，正在跳转…')
    setTimeout(() => navigate('/chat', { replace: true }), 500)
  }

  useEffect(() => {
    let active = true
    const handleCallback = async () => {
      const provider = localStorage.getItem('oauth_provider')
      const code = searchParams.get('code')
      const state = searchParams.get('state')
      const providerError = searchParams.get('error')
      if (code || state || providerError) {
        // Authorization codes are single-use; remove them from browser history as soon as captured.
        window.history.replaceState({}, document.title, window.location.pathname)
      }

      if (providerError || !provider || !code || !state) {
        if (active) {
          setError(providerError
            ? 'Google 授权未完成或已取消，请返回登录页重新发起。'
            : '授权信息不完整或已过期，请返回登录页重新发起 Google 登录。')
          localStorage.removeItem('oauth_provider')
        }
        return
      }

      try {
        const data = await runOnceWhilePending(`oauth:${provider}:${state}`, () =>
          authApi.oauthCallback(provider, code, state),
        )
        if (!active) return
        localStorage.removeItem('oauth_provider')
        if (data.mode === 'bind') {
          setStatus('第三方账号绑定成功，正在返回…')
          const returnTo = localStorage.getItem('oauth_return_to')
          localStorage.removeItem('oauth_return_to')
          const safeReturnTo = returnTo?.startsWith('/') && !returnTo.startsWith('//') ? returnTo : '/chat'
          setTimeout(() => navigate(safeReturnTo, { replace: true }), 500)
          return
        }
        if (data.requiresTwoFactor && data.challengeToken) {
          setChallengeToken(data.challengeToken)
          setRequiresTwoFactor(true)
          setStatus('Google 身份已验证，请完成二次验证。')
          return
        }
        if (!data.token || !data.userInfo) throw new Error('登录响应缺少会话信息')
        await finishLogin(data as { token: string; userInfo: { id: number } })
      } catch (err) {
        if (!active) return
        localStorage.removeItem('oauth_provider')
        localStorage.removeItem('oauth_return_to')
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
        ) : error ? (
          <div className="space-y-4">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full border border-rose-400/20 bg-rose-400/10 text-rose-300" aria-hidden="true">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
                <circle cx="12" cy="12" r="9" />
                <path d="M12 8v4m0 4h.01" />
              </svg>
            </div>
            <h1 className="text-white font-semibold">无法完成 Google 登录</h1>
            <p role="alert" className="text-accent-rose text-sm leading-6">{error}</p>
            <p className="text-neutral-400 text-xs leading-5">
              授权状态只能使用一次。请重新发起登录；如果仍然失败，请联系管理员检查 Google 登录服务连接。
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
