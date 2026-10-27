import { useEffect, useState, type FormEvent } from 'react'
import PlatformMark from '@/components/PlatformMark'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { authApi } from '@/services/api'
import { useAppStore } from '@/stores/appStore'
import { useThemeStore } from '@/stores/themeStore'
import { consumeAuthSessionNotice } from '@/services/authResponseRecovery'
import { motion } from 'framer-motion'

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
type LoginMode = 'password' | 'email-code'

export default function LoginPage() {
  const [mode, setMode] = useState<LoginMode>('email-code')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [email, setEmail] = useState('')
  const [emailCode, setEmailCode] = useState('')
  const [emailCodeSent, setEmailCodeSent] = useState(false)
  const [emailCodeCountdown, setEmailCodeCountdown] = useState(0)
  const [emailCodeLoading, setEmailCodeLoading] = useState(false)
  const [captchaId, setCaptchaId] = useState('')
  const [captchaImage, setCaptchaImage] = useState('')
  const [captchaCode, setCaptchaCode] = useState('')
  const [captchaLoading, setCaptchaLoading] = useState(false)
  const [twoFactorRequired, setTwoFactorRequired] = useState(false)
  const [twoFactorCode, setTwoFactorCode] = useState('')
  const [oauthLoading, setOauthLoading] = useState(false)
  const [qqOAuthStatus, setQqOAuthStatus] = useState<'checking' | 'configured' | 'pending_review' | 'unconfigured' | 'unavailable'>('checking')
  const [error, setError] = useState('')
  const [sessionNotice, setSessionNotice] = useState('')
  const [loading, setLoading] = useState(false)
  const { setToken, setUserId, fetchUserInfo } = useAppStore()
  const { theme } = useThemeStore()
  const location = useLocation()
  const navigate = useNavigate()
  const isDark = theme === 'dark'
  const notice = (location.state as { notice?: string } | null)?.notice || sessionNotice
  const identifier = username.trim()
  const identifierIsEmail = identifier.includes('@')

  const textPrimary = isDark ? '#F1F5F9' : '#0F172A'
  const textDim = isDark ? '#94A3B8' : '#64748B'
  const borderColor = isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.08)'
  const inputBg = isDark ? 'rgba(20,28,48,0.6)' : 'rgba(0,0,0,0.03)'
  const panelBg = isDark ? 'rgba(16,24,42,0.6)' : 'rgba(255,255,255,0.82)'
  const helperText = twoFactorRequired
    ? (mode === 'email-code'
      ? '邮箱验证码已验证，请输入动态验证码或一次性备用码。'
      : '密码已通过验证，请输入动态验证码或一次性备用码。')
    : mode === 'email-code'
      ? '输入已注册邮箱，获取验证码后直接登录。'
      : identifierIsEmail
        ? '将使用邮箱和密码登录。'
        : '支持用户名或邮箱 + 密码登录。'

  const loadCaptcha = async () => {
    setCaptchaLoading(true)
    try {
      const result = await authApi.getLoginCaptcha()
      setCaptchaId(result.captchaId)
      setCaptchaImage(result.image)
      setCaptchaCode('')
    } catch (err) {
      setCaptchaId('')
      setCaptchaImage('')
      // 保留已经展示的登录错误，避免验证码刷新失败把真正原因盖掉。
      setError(previous => previous || (err as Error).message || '验证码加载失败，请刷新页面重试')
    } finally {
      setCaptchaLoading(false)
    }
  }

  useEffect(() => { void loadCaptcha() }, [])
  useEffect(() => {
    let active = true
    authApi.getOAuthProviders()
      .then(providers => {
        if (!active) return
        setQqOAuthStatus(!providers.qq ? 'unconfigured' : providers.qqReviewStatus === 'approved' ? 'configured' : 'pending_review')
      })
      .catch(() => { if (active) setQqOAuthStatus('unavailable') })
    return () => { active = false }
  }, [])
  useEffect(() => {
    setSessionNotice(consumeAuthSessionNotice(window.sessionStorage))
  }, [])
  useEffect(() => {
    if (emailCodeCountdown <= 0) return
    const timer = window.setTimeout(() => setEmailCodeCountdown(value => Math.max(0, value - 1)), 1000)
    return () => window.clearTimeout(timer)
  }, [emailCodeCountdown])

  const completeLogin = async (data: { token: string; userInfo: { id: number } }) => {
    setToken(data.token)
    setUserId(data.userInfo.id)
    await fetchUserInfo()
    navigate('/chat', { replace: true })
  }

  const switchMode = (next: LoginMode) => {
    if (next === mode) return
    setMode(next)
    setError('')
    setTwoFactorRequired(false)
    setTwoFactorCode('')
    if (next === 'password' && !captchaId && !captchaLoading) void loadCaptcha()
  }

  const handleEmailChange = (value: string) => {
    setEmail(value)
    if (emailCodeSent) {
      setEmailCodeSent(false)
      setEmailCodeCountdown(0)
      setEmailCode('')
    }
    if (error) setError('')
  }

  const handleSendEmailCode = async () => {
    const target = email.trim()
    if (!target) { setError('请输入邮箱'); return }
    if (!EMAIL_PATTERN.test(target)) { setError('邮箱格式不正确，请检查后重试'); return }
    if (!captchaId || !captchaCode.trim()) { setError('请先输入图形验证码'); return }
    setError('')
    setEmailCodeLoading(true)
    try {
      await authApi.sendLoginEmailCode({ email: target, captchaId, captchaCode: captchaCode.trim() })
      setEmailCodeSent(true)
      setEmailCodeCountdown(60)
      setEmailCode('')
      // 发送验证码会消费当前图形验证码；立即换一张，便于 60 秒后重发或切回密码登录。
      void loadCaptcha()
    } catch (err) {
      setError((err as Error).message || '验证码发送失败，请稍后重试')
      void loadCaptcha()
    } finally {
      setEmailCodeLoading(false)
    }
  }

  const handleEmailCodeSubmit = async () => {
    const target = email.trim()
    if (!target) { setError('请输入邮箱'); return }
    if (!EMAIL_PATTERN.test(target)) { setError('邮箱格式不正确，请检查后重试'); return }
    if (!emailCodeSent) { setError('请先点击“发送验证码”获取邮箱验证码'); return }
    if (!/^\d{6}$/.test(emailCode)) { setError('请输入6位邮箱验证码'); return }
    if (twoFactorRequired && !twoFactorCode.trim()) { setError('请输入动态验证码或备用码'); return }
    setError('')
    setLoading(true)
    try {
      const data = await authApi.loginByEmailCode({ email: target, code: emailCode, twoFactorCode })
      await completeLogin(data)
    } catch (err) {
      const apiError = err as Error & { code?: number }
      if (apiError.code === 428) {
        setTwoFactorRequired(true)
        setTwoFactorCode('')
        setError('邮箱验证码已验证。请输入动态验证码或一次性备用码。')
      } else {
        setError(apiError.message || '登录失败')
      }
    } finally {
      setLoading(false)
    }
  }

  const handlePasswordSubmit = async () => {
    const loginIdentifier = username.trim()
    if (!loginIdentifier) { setError('请输入用户名或邮箱'); return }
    if (loginIdentifier.includes('@') && !EMAIL_PATTERN.test(loginIdentifier)) {
      setError('邮箱格式不正确，请检查后重试')
      return
    }
    if (!password) { setError('请输入密码'); return }
    if (!captchaId || !captchaCode.trim()) { setError('请输入图形验证码'); return }
    if (twoFactorRequired && !twoFactorCode.trim()) { setError('请输入动态验证码或备用码'); return }
    setError('')
    setLoading(true)
    try {
      const data = await authApi.login({ username: loginIdentifier, password, captchaId, captchaCode, twoFactorCode })
      await completeLogin(data)
    } catch (err) {
      const apiError = err as Error & { code?: number }
      if (apiError.code === 428) {
        setTwoFactorRequired(true)
        setTwoFactorCode('')
        setError('密码验证通过。请输入动态验证码或一次性备用码，并完成新的图形验证码。')
      } else if (apiError.code === 401 && loginIdentifier.includes('@')) {
        setError('邮箱或密码错误。请检查邮箱和密码，或改用用户名登录。')
      } else {
        setError(apiError.message || '登录失败')
      }
      void loadCaptcha()
    } finally {
      setLoading(false)
    }
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (mode === 'email-code') await handleEmailCodeSubmit()
    else await handlePasswordSubmit()
  }

  const handleProviderLogin = async (provider: 'qq' | 'google') => {
    if (provider === 'google') {
      setError('Google 登录暂不支持')
      return
    }
    setError('')
    setOauthLoading(true)
    try {
      const result = await authApi.getOAuthUrl(provider, 'login')
      localStorage.setItem('oauth_provider', result.provider)
      window.location.assign(result.url)
    } catch (err) {
      localStorage.removeItem('oauth_provider')
      setError((err as Error).message || `${provider === 'qq' ? 'QQ' : 'Google'} 登录暂不可用`)
      setOauthLoading(false)
    }
  }

  const submitLabel = loading
    ? (mode === 'email-code' ? '验证中...' : '登录中...')
    : (twoFactorRequired ? '验证并登录' : '登录')

  return (
    <div className="relative flex h-screen w-full overflow-x-hidden overflow-y-auto" style={{ background: isDark ? '#080B16' : '#F8FAFC' }}>
      {/* Ambient background glow */}
      <div className="pointer-events-none fixed inset-0 bg-aurora opacity-60" />
      <div className="pointer-events-none fixed top-20 -left-40 h-[500px] w-[500px] rounded-full opacity-[0.07]"
        style={{ background: 'radial-gradient(circle, #8B5CF6, transparent 70%)' }} />
      <div className="pointer-events-none fixed bottom-20 -right-40 h-[500px] w-[500px] rounded-full opacity-[0.05]"
        style={{ background: 'radial-gradient(circle, #3B82F6, transparent 70%)' }} />

      {/* Brand panel — left side */}
      <aside className="relative z-10 hidden w-[440px] shrink-0 flex-col justify-between p-10 lg:sticky lg:top-0 lg:flex lg:h-screen xl:w-[480px] xl:p-14">
        <div className="flex items-center gap-3">
          <PlatformMark className="h-11 w-11 rounded-xl shadow-sm" />
          <div>
            <div className="text-lg font-bold tracking-tight" style={{ color: textPrimary }}>凌点 AI Gateway</div>
            <div className="text-[11px] tracking-wide" style={{ color: textDim }}>新一代模型调度网关平台</div>
          </div>
        </div>

        <div className="space-y-8">
          <div>
            <h1 className="mb-2 text-4xl font-bold tracking-tight" style={{ color: textPrimary, fontFamily: 'var(--font-display)' }}>
              欢迎回来
            </h1>
            <p className="text-sm leading-relaxed" style={{ color: textDim }}>
              登录你的 AI Gateway 控制台，开始模型调度与对话
            </p>
          </div>
          <div className="space-y-5">
            {[
              { title: '多模型统一调度', desc: '统一管理 OpenAI、Claude、DeepSeek 等主流模型' },
              { title: '高性能流式渲染', desc: '基于 Pretext 引擎，120FPS 零 DOM 重排流式输出' },
              { title: '企业级安全保障', desc: 'BCrypt 加密 · Redis Token 鉴权 · 点数计费' },
            ].map((f, i) => (
              <div key={i} className="flex items-start gap-3.5" style={{ animation: `fade-in-up 0.4s ${0.1 * i}s ease-out both` }}>
                <div className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg"
                  style={{ background: 'rgba(139,92,246,0.08)', border: '1px solid rgba(139,92,246,0.12)' }}>
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#8B5CF6" strokeWidth="2.5"><polyline points="20 6 9 17 4 12" /></svg>
                </div>
                <div>
                  <div className="mb-0.5 text-sm font-semibold" style={{ color: textPrimary }}>{f.title}</div>
                  <div className="text-[12px] leading-relaxed" style={{ color: textDim }}>{f.desc}</div>
                </div>
              </div>
            ))}
          </div>
        </div>

        <p className="text-[11px]" style={{ color: 'var(--text-dim)', opacity: 0.5 }}>© 2026 Lingdian AI Gateway</p>
      </aside>

      {/* Login form — right side */}
      <main className="relative z-10 flex min-h-full min-w-0 flex-1 items-start justify-center px-4 py-8 sm:px-6 lg:px-10">
        <motion.div
          initial={{ opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, ease: [0.34, 1.56, 0.64, 1] }}
          className="my-auto w-full min-w-0 max-w-[26rem]"
        >
          <div className="flex w-full min-w-0 flex-col gap-3 rounded-3xl p-6 backdrop-blur-xl sm:gap-4 sm:p-8" style={{
            background: panelBg,
            border: `1px solid ${borderColor}`,
            boxShadow: '0 24px 64px rgba(0,0,0,0.12)',
          }}>
            <div className="text-center lg:text-left">
              <PlatformMark className="mx-auto mb-4 h-12 w-12 rounded-2xl shadow-sm lg:hidden" />
              <h2 className="text-xl font-bold tracking-tight" style={{ color: textPrimary }}>
                {twoFactorRequired ? '完成两步验证' : '登录你的账户'}
              </h2>
            </div>

            {!twoFactorRequired && (
              <div className="grid grid-cols-2 gap-1 rounded-xl p-1" role="tablist" aria-label="登录方式"
                style={{ background: inputBg, border: `1px solid ${borderColor}` }}>
                <button type="button" role="tab" aria-selected={mode === 'password'}
                  onClick={() => switchMode('password')} disabled={loading || emailCodeLoading}
                  className="h-9 rounded-lg text-xs font-semibold transition-colors disabled:opacity-50"
                  style={{
                    background: mode === 'password' ? (isDark ? 'rgba(139,92,246,0.22)' : '#FFFFFF') : 'transparent',
                    color: mode === 'password' ? '#8B5CF6' : textDim,
                    boxShadow: mode === 'password' ? '0 1px 3px rgba(0,0,0,0.08)' : 'none',
                  }}>
                  密码登录
                </button>
                <button type="button" role="tab" aria-selected={mode === 'email-code'}
                  onClick={() => switchMode('email-code')} disabled={loading || emailCodeLoading}
                  className="h-9 rounded-lg text-xs font-semibold transition-colors disabled:opacity-50"
                  style={{
                    background: mode === 'email-code' ? (isDark ? 'rgba(139,92,246,0.22)' : '#FFFFFF') : 'transparent',
                    color: mode === 'email-code' ? '#8B5CF6' : textDim,
                    boxShadow: mode === 'email-code' ? '0 1px 3px rgba(0,0,0,0.08)' : 'none',
                  }}>
                  邮箱验证码
                </button>
              </div>
            )}

            {notice && !error ? (
              <div role="status" className="flex min-w-0 items-start gap-2.5 rounded-xl p-2.5 text-[12px] leading-5"
                style={{ background: 'rgba(16,185,129,0.06)', border: '1px solid rgba(16,185,129,0.15)', color: '#059669' }}>
                <svg className="mt-0.5 shrink-0" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M20 6 9 17l-5-5" /></svg>
                <span className="min-w-0 break-words [overflow-wrap:anywhere]">{notice}</span>
              </div>
            ) : (
              <p className="min-w-0 text-[11px] leading-5" style={{ color: textDim }}>
                {helperText}
              </p>
            )}

            <form noValidate onSubmit={handleSubmit} className="flex min-w-0 flex-col gap-4">
              {mode === 'password' ? (
                <>
                  <div className="min-w-0">
                    <label htmlFor="login-username" className="mb-1.5 block text-[12px] font-medium" style={{ color: textDim }}>用户名或邮箱</label>
                    <input
                      id="login-username"
                      type="text"
                      autoComplete="username"
                      inputMode={identifierIsEmail ? 'email' : 'text'}
                      autoCapitalize="none"
                      spellCheck={false}
                      maxLength={128}
                      value={username}
                      onChange={e => { setUsername(e.target.value); if (error) setError('') }}
                      placeholder="输入用户名或邮箱"
                      className="h-11 w-full min-w-0 rounded-xl px-3.5 text-sm outline-none transition-all duration-200 focus:border-violet-400/60 focus:shadow-[0_0_0_3px_rgba(139,92,246,0.08)]"
                      style={{ background: inputBg, color: textPrimary, border: `1px solid ${borderColor}` }}
                    />
                  </div>

                  <div className="min-w-0">
                    <label htmlFor="login-password" className="mb-1.5 block text-[12px] font-medium" style={{ color: textDim }}>密码</label>
                    <div className="relative min-w-0">
                      <input
                        id="login-password"
                        type={showPassword ? 'text' : 'password'}
                        autoComplete="current-password"
                        value={password}
                        onChange={e => { setPassword(e.target.value); if (error) setError('') }}
                        placeholder="请输入密码"
                        className="h-11 w-full min-w-0 rounded-xl px-3.5 pr-11 text-sm outline-none transition-all duration-200 focus:border-violet-400/60 focus:shadow-[0_0_0_3px_rgba(139,92,246,0.08)]"
                        style={{ background: inputBg, color: textPrimary, border: `1px solid ${borderColor}` }}
                      />
                      <button
                        type="button"
                        onClick={() => setShowPassword(value => !value)}
                        aria-label={showPassword ? '隐藏密码' : '显示密码'}
                        aria-pressed={showPassword}
                        className="absolute inset-y-0 right-0 flex w-11 items-center justify-center rounded-r-xl transition-opacity hover:opacity-70"
                        style={{ color: textDim }}
                      >
                        {showPassword ? (
                          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" /><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" /><line x1="1" y1="1" x2="23" y2="23" /></svg>
                        ) : (
                          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8Z" /><circle cx="12" cy="12" r="3" /></svg>
                        )}
                      </button>
                    </div>
                  </div>
                </>
              ) : (
                <div className="min-w-0">
                  <label htmlFor="login-email" className="mb-1.5 block text-[12px] font-medium" style={{ color: textDim }}>邮箱</label>
                  <input
                    id="login-email"
                    type="email"
                    autoComplete="email"
                    inputMode="email"
                    autoCapitalize="none"
                    spellCheck={false}
                    maxLength={128}
                    value={email}
                    onChange={e => handleEmailChange(e.target.value)}
                    placeholder="输入已注册邮箱"
                    className="h-11 w-full min-w-0 rounded-xl px-3.5 text-sm outline-none transition-all duration-200 focus:border-violet-400/60 focus:shadow-[0_0_0_3px_rgba(139,92,246,0.08)]"
                    style={{ background: inputBg, color: textPrimary, border: `1px solid ${borderColor}` }}
                  />
                </div>
              )}

              <div className="min-w-0">
                <label htmlFor="login-captcha" className="mb-1.5 block text-[12px] font-medium" style={{ color: textDim }}>图形验证码</label>
                <div className="flex min-w-0 items-center gap-2">
                  <input
                    id="login-captcha"
                    type="text"
                    value={captchaCode}
                    onChange={e => { setCaptchaCode(e.target.value.toUpperCase()); if (error) setError('') }}
                    placeholder="输入验证码"
                    maxLength={5}
                    autoComplete="off"
                    aria-label="图形验证码"
                    className="h-11 min-w-0 flex-1 rounded-xl px-3.5 text-sm uppercase outline-none transition-all duration-200 focus:border-violet-400/60 focus:shadow-[0_0_0_3px_rgba(139,92,246,0.08)]"
                    style={{ background: inputBg, color: textPrimary, border: `1px solid ${borderColor}` }}
                  />
                  <button
                    type="button"
                    onClick={() => { setError(''); void loadCaptcha() }}
                    disabled={captchaLoading}
                    aria-label="刷新登录验证码"
                    className="h-11 w-32 shrink-0 overflow-hidden rounded-xl border transition-opacity disabled:opacity-50 sm:w-36"
                    style={{ background: inputBg, borderColor }}
                  >
                    {captchaImage ? <img src={captchaImage} alt="登录图形验证码，点击刷新" className="h-full w-full object-cover" /> : <span className="text-xs" style={{ color: textDim }}>{captchaLoading ? '加载中' : '点击重试'}</span>}
                  </button>
                </div>
                <p className="mt-1.5 text-[11px]" style={{ color: textDim }}>
                  {mode === 'email-code' ? '发送邮箱验证码前需要填写，点击图片可刷新' : '验证码5分钟内有效，点击图片可刷新'}
                </p>
              </div>

              {mode === 'email-code' && (
                <div className="min-w-0">
                  <label htmlFor="login-email-code" className="mb-1.5 block text-[12px] font-medium" style={{ color: textDim }}>邮箱验证码</label>
                  <div className="flex min-w-0 items-center gap-2">
                    <input
                      id="login-email-code"
                      type="text"
                      value={emailCode}
                      onChange={e => { setEmailCode(e.target.value.replace(/\D/g, '').slice(0, 6)); if (error) setError('') }}
                      placeholder="6位验证码"
                      inputMode="numeric"
                      autoComplete="one-time-code"
                      maxLength={6}
                      className="h-11 min-w-0 flex-1 rounded-xl px-3.5 text-sm outline-none transition-all duration-200 focus:border-violet-400/60 focus:shadow-[0_0_0_3px_rgba(139,92,246,0.08)]"
                      style={{ background: inputBg, color: textPrimary, border: `1px solid ${borderColor}` }}
                    />
                    <button
                      type="button"
                      onClick={() => void handleSendEmailCode()}
                      disabled={emailCodeLoading || emailCodeCountdown > 0}
                      className="h-11 min-w-[116px] shrink-0 rounded-xl border px-3 text-xs font-semibold transition-opacity disabled:opacity-50"
                      style={{ background: inputBg, color: '#8B5CF6', borderColor }}>
                      {emailCodeLoading ? '发送中…' : emailCodeCountdown > 0 ? `${emailCodeCountdown} 秒后重发` : emailCodeSent ? '重新发送' : '发送验证码'}
                    </button>
                  </div>
                  <p className="mt-1.5 text-[11px]" style={{ color: textDim }}>
                    {emailCodeSent ? '验证码10分钟内有效；未收到请检查垃圾邮件文件夹' : '先填写邮箱和图形验证码，再获取邮箱验证码'}
                  </p>
                </div>
              )}

              {twoFactorRequired && (
                <div className="min-w-0">
                  <label htmlFor="login-two-factor" className="mb-1.5 block text-[12px] font-medium" style={{ color: textDim }}>两步验证码</label>
                  <input
                    id="login-two-factor"
                    type="text"
                    value={twoFactorCode}
                    onChange={e => { setTwoFactorCode(e.target.value.toUpperCase()); if (error) setError('') }}
                    placeholder="动态验证码或一次性备用码"
                    autoComplete="one-time-code"
                    aria-label="动态验证码或备用码"
                    className="h-11 w-full min-w-0 rounded-xl px-3.5 text-sm outline-none transition-all duration-200 focus:border-violet-400/60 focus:shadow-[0_0_0_3px_rgba(139,92,246,0.08)]"
                    style={{ background: inputBg, color: textPrimary, border: `1px solid ${borderColor}` }}
                  />
                  <p className="mt-1.5 text-[11px]" style={{ color: textDim }}>每个备用码只能使用一次</p>
                </div>
              )}

              {error && (
                <div role="alert" className="flex min-w-0 items-start gap-2.5 rounded-xl p-2.5 text-[12px] leading-5"
                  style={{ background: 'rgba(244,63,94,0.06)', border: '1px solid rgba(244,63,94,0.15)', color: '#F43F5E' }}>
                  <svg className="mt-0.5 shrink-0" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" /></svg>
                  <span className="min-w-0 break-words [overflow-wrap:anywhere]">{error}</span>
                </div>
              )}

              <button type="submit" disabled={loading || emailCodeLoading}
                className="mt-1 h-11 w-full rounded-xl text-sm font-semibold text-white transition-all duration-200 disabled:opacity-50 hover:opacity-90 active:scale-[0.98]"
                style={{ background: 'linear-gradient(135deg, #8B5CF6, #6366F1)', boxShadow: '0 4px 20px rgba(139,92,246,0.3)' }}>
                {loading ? (
                  <span className="flex items-center justify-center gap-2">
                    <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white" />
                    {submitLabel}
                  </span>
                ) : submitLabel}
              </button>

              {twoFactorRequired && (
                <button
                  type="button"
                  onClick={() => {
                    setTwoFactorRequired(false)
                    setTwoFactorCode('')
                    setError('')
                    if (mode === 'password') void loadCaptcha()
                  }}
                  className="w-full py-1 text-xs font-medium"
                  style={{ color: textDim }}
                >
                  返回上一步
                </button>
              )}
            </form>

            {!twoFactorRequired && (
              <div className="flex min-w-0 flex-col gap-3">
                <div className="flex items-center gap-3" style={{ color: textDim }}>
                  <span className="h-px flex-1" style={{ background: borderColor }} />
                  <span className="shrink-0 text-[11px]">其他登录方式</span>
                  <span className="h-px flex-1" style={{ background: borderColor }} />
                </div>
                <div className="grid min-w-0 grid-cols-2 gap-3">
                  <button type="button" onClick={() => void handleProviderLogin('qq')} disabled={oauthLoading}
                    aria-label="使用 QQ 登录"
                    title={qqOAuthStatus === 'unconfigured' ? 'QQ 登录服务尚未配置' : qqOAuthStatus === 'pending_review' ? 'QQ 应用正在等待腾讯审核' : '使用 QQ 登录'}
                    className="flex h-11 min-w-0 items-center justify-center gap-2 rounded-xl border text-sm font-semibold transition-colors hover:bg-sky-500/10 disabled:opacity-50"
                    style={{ color: textPrimary, borderColor, background: inputBg }}>
                    {oauthLoading ? <span className="h-4 w-4 animate-spin rounded-full border-2 border-sky-500/30 border-t-sky-500" /> : <img src="/qq-connect-login.png" alt="" width={16} height={16} />}
                    <span className="truncate">QQ 登录</span>
                  </button>
                  <button type="button" onClick={() => void handleProviderLogin('google')}
                    aria-label="Google 登录暂不支持" title="Google 登录暂不支持"
                    className="flex h-11 min-w-0 items-center justify-center gap-2 rounded-xl border text-sm font-semibold transition-colors hover:bg-slate-500/10"
                    style={{ color: textPrimary, borderColor, background: inputBg }}>
                    <svg aria-hidden="true" width="18" height="18" viewBox="0 0 48 48">
                      <path fill="#FFC107" d="M43.6 24.5c0-1.4-.1-2.8-.4-4.1H24v7.8h11a9.4 9.4 0 0 1-4.1 6.2v5.1h6.6c3.9-3.6 6.1-8.8 6.1-15Z" />
                      <path fill="#FF3D00" d="M24 44c5.5 0 10.1-1.8 13.5-4.8l-6.6-5.1c-1.8 1.2-4.1 2-6.9 2-5.3 0-9.8-3.6-11.4-8.4H5.8v5.3A20 20 0 0 0 24 44Z" />
                      <path fill="#4CAF50" d="M12.6 27.7a12 12 0 0 1 0-7.4V15H5.8a20 20 0 0 0 0 18l6.8-5.3Z" />
                      <path fill="#1976D2" d="M24 12.1c3 0 5.7 1 7.8 3.1l5.8-5.8C34.1 6.1 29.5 4 24 4A20 20 0 0 0 5.8 15l6.8 5.3c1.6-4.8 6.1-8.2 11.4-8.2Z" />
                    </svg>
                    <span className="truncate">Google</span>
                  </button>
                </div>
                <p className="min-w-0 text-center text-[11px] leading-4" style={{ color: textDim }}>
                  {qqOAuthStatus === 'configured'
                    ? '支持 QQ 账号登录 · Google 登录暂不支持'
                    : qqOAuthStatus === 'pending_review'
                      ? 'QQ 登录入口已开放，腾讯应用审核中，暂可能无法完成授权 · Google 登录暂不支持'
                      : qqOAuthStatus === 'unconfigured'
                        ? 'QQ 登录服务尚未配置，请联系管理员 · Google 登录暂不支持'
                        : qqOAuthStatus === 'unavailable'
                          ? 'QQ 登录状态暂不可用，点击 QQ 按钮可重试 · Google 登录暂不支持'
                          : '正在检查 QQ 登录服务 · Google 登录暂不支持'}
                </p>
              </div>
            )}

            <p className="text-center text-[12px]" style={{ color: textDim }}>
              还没有账户？ <Link to="/register" className="font-semibold transition-colors hover:opacity-80" style={{ color: '#8B5CF6' }}>立即注册</Link>
            </p>
          </div>
        </motion.div>
      </main>
    </div>
  )
}
