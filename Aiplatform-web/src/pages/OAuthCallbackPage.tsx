import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { authApi } from '@/services/api'
import { useAppStore } from '@/stores/appStore'
import { motion } from 'framer-motion'
import { runOnceWhilePending } from '@/utils/runOnceWhilePending'

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

      if (providerError || !provider || !code || !state) {
        if (active) {
          setError(providerError ? '第三方授权未完成' : '授权信息不完整或已过期，请重新发起登录')
          localStorage.removeItem('oauth_provider')
          setTimeout(() => navigate('/login', { replace: true }), 3000)
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
        setError((err as Error).message || '第三方登录失败，请重试')
        setTimeout(() => navigate('/login', { replace: true }), 3000)
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
        {error && <p className="text-accent-rose text-sm mb-4">{error}</p>}
        {requiresTwoFactor ? (
          <form onSubmit={completeTwoFactor} className="space-y-4">
            <h1 className="text-white font-semibold">完成两步验证</h1>
            <p className="text-neutral-400 text-sm">输入动态验证码或一次性备用码</p>
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
        ) : (
          <div>
            <motion.div animate={{ rotate: 360 }} transition={{ duration: 1, repeat: Infinity, ease: 'linear' }} className="w-12 h-12 mx-auto mb-4">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#8B5CF6" strokeWidth="2">
                <path d="M12 2a10 10 0 019.95 9" strokeLinecap="round" />
              </svg>
            </motion.div>
            <p className="text-white text-sm">{error ? '即将返回登录页…' : status}</p>
          </div>
        )}
      </motion.div>
    </div>
  )
}
