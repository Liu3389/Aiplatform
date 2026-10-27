import { useEffect, useState, type FormEvent } from 'react'
import PlatformMark from '@/components/PlatformMark'
import { Link, useNavigate } from 'react-router-dom'
import { authApi } from '@/services/api'
import { useThemeStore } from '@/stores/themeStore'
import { motion } from 'framer-motion'

export default function RegisterPage() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [email, setEmail] = useState('')
  const [emailCode, setEmailCode] = useState('')
  const [emailCodeSent, setEmailCodeSent] = useState(false)
  const [emailCodeCountdown, setEmailCodeCountdown] = useState(0)
  const [emailCodeLoading, setEmailCodeLoading] = useState(false)
  const [captchaId, setCaptchaId] = useState('')
  const [captchaImage, setCaptchaImage] = useState('')
  const [captchaCode, setCaptchaCode] = useState('')
  const [captchaLoading, setCaptchaLoading] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [oauthLoading, setOauthLoading] = useState(false)
  const [emailVerificationRequired, setEmailVerificationRequired] = useState(false)
  const [registrationConfigLoading, setRegistrationConfigLoading] = useState(true)
  const [registrationConfigError, setRegistrationConfigError] = useState(false)
  const [qqOAuthStatus, setQqOAuthStatus] = useState<'checking' | 'configured' | 'pending_review' | 'unconfigured' | 'unavailable'>('checking')
  const { theme } = useThemeStore()
  const navigate = useNavigate()
  const isDark = theme === 'dark'

  const loadCaptcha = async () => {
    setCaptchaLoading(true)
    try {
      const result = await authApi.getRegisterCaptcha()
      setCaptchaId(result.captchaId)
      setCaptchaImage(result.image)
      setCaptchaCode('')
    } catch (err) {
      setCaptchaId('')
      setCaptchaImage('')
      setError(previous => previous || (err as Error).message || '验证码加载失败，请刷新页面重试')
    } finally {
      setCaptchaLoading(false)
    }
  }

  useEffect(() => { void loadCaptcha() }, [])
  useEffect(() => {
    if (emailCodeCountdown <= 0) {
      if (emailCodeSent) void loadCaptcha()
      return
    }
    const timer = window.setTimeout(() => setEmailCodeCountdown(value => Math.max(0, value - 1)), 1000)
    return () => window.clearTimeout(timer)
  }, [emailCodeCountdown, emailCodeSent])
  useEffect(() => {
    let active = true
    authApi.getRegistrationConfig()
      .then(config => {
        if (!active) return
        setEmailVerificationRequired(config.emailVerificationRequired)
        setRegistrationConfigError(false)
      })
      .catch(() => {
        if (!active) return
        setRegistrationConfigError(true)
        setError('无法读取注册验证配置，请刷新页面后重试')
      })
      .finally(() => { if (active) setRegistrationConfigLoading(false) })
    authApi.getOAuthProviders()
      .then(providers => {
        if (!active) return
        setQqOAuthStatus(!providers.qq ? 'unconfigured' : providers.qqReviewStatus === 'approved' ? 'configured' : 'pending_review')
      })
      .catch(() => { if (active) setQqOAuthStatus('unavailable') })
    return () => { active = false }
  }, [])

  const textPrimary = isDark ? '#F1F5F9' : '#0F172A'
  const textDim = isDark ? '#94A3B8' : '#64748B'
  const borderColor = isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.08)'
  const inputBg = isDark ? 'rgba(20,28,48,0.6)' : 'rgba(0,0,0,0.03)'
  const panelBg = isDark ? 'rgba(16,24,42,0.6)' : 'rgba(255,255,255,0.82)'

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (registrationConfigLoading || registrationConfigError) { setError('注册方式尚未确认，请刷新页面后重试'); return }
    if (!username.trim()) { setError('请输入用户名'); return }
    if (!password) { setError('请输入密码'); return }
    if (password.length < 8) { setError('密码至少8个字符，并包含大小写字母和数字'); return }
    if (emailVerificationRequired && (!email.trim() || !emailCode.trim())) { setError('请填写邮箱和邮箱验证码'); return }
    if (!emailVerificationRequired && (!captchaId || !captchaCode.trim())) { setError('请输入图形验证码'); return }
    setError('')
    setLoading(true)
    try {
      const registration = emailVerificationRequired
        ? { username: username.trim(), password, email: email.trim(), emailCode: emailCode.trim() }
        : { username: username.trim(), password, captchaId, captchaCode }
      await authApi.register(registration)
      navigate('/login', { replace: true, state: { notice: '注册成功，请使用用户名或该邮箱和密码登录' } })
    } catch (err) {
      setError((err as Error).message || '注册失败')
      void loadCaptcha()
    } finally {
      setLoading(false)
    }
  }

  const handleSendEmailCode = async () => {
    if (registrationConfigLoading || registrationConfigError || !emailVerificationRequired) {
      setError('邮箱注册配置尚未就绪，请刷新页面后重试')
      return
    }
    if (!email.trim()) { setError('请先填写邮箱'); return }
    if (!captchaId || !captchaCode.trim()) { setError('请先完成图形验证码'); return }
    setError('')
    setEmailCodeLoading(true)
    try {
      await authApi.sendRegistrationEmailCode({ email: email.trim(), captchaId, captchaCode: captchaCode.trim() })
      setEmailCodeSent(true)
      setEmailCodeCountdown(60)
    } catch (err) {
      setError((err as Error).message || '邮箱验证码发送失败')
      void loadCaptcha()
    } finally {
      setEmailCodeLoading(false)
    }
  }

  const handleQQLogin = async () => {
    setError('')
    setOauthLoading(true)
    try {
      const result = await authApi.getOAuthUrl('qq', 'login')
      localStorage.setItem('oauth_provider', result.provider)
      window.location.assign(result.url)
    } catch (err) {
      localStorage.removeItem('oauth_provider')
      setError((err as Error).message || 'QQ 登录暂不可用')
      setOauthLoading(false)
    }
  }

  return (
    <div className="relative flex h-screen w-full overflow-x-hidden overflow-y-auto" style={{ background: isDark ? '#080B16' : '#F8FAFC' }}>
      {/* Ambient background */}
      <div className="pointer-events-none fixed inset-0 bg-aurora opacity-60" />
      <div className="pointer-events-none fixed top-40 -right-20 h-[400px] w-[400px] rounded-full opacity-[0.06]"
        style={{ background: 'radial-gradient(circle, #3B82F6, transparent 70%)' }} />
      <div className="pointer-events-none fixed bottom-40 -left-20 h-[400px] w-[400px] rounded-full opacity-[0.04]"
        style={{ background: 'radial-gradient(circle, #06B6D4, transparent 70%)' }} />

      {/* Brand panel */}
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
              创建账户
            </h1>
            <p className="text-sm leading-relaxed" style={{ color: textDim }}>
              注册即享免费额度，开始 AI 模型调度之旅
            </p>
          </div>
          <div className="space-y-5">
            {[
              { title: '快速接入', desc: '注册即获 API Key，立即调用主流 AI 模型' },
              { title: '免费额度', desc: '新用户赠送免费调用额度，按 Token 精确计费' },
              { title: '流式体验', desc: 'SSE 流式输出 + Pretext 高性能渲染引擎' },
            ].map((f, i) => (
              <div key={i} className="flex items-start gap-3.5" style={{ animation: `fade-in-up 0.4s ${0.1 * i}s ease-out both` }}>
                <div className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg"
                  style={{ background: 'rgba(59,130,246,0.08)', border: '1px solid rgba(59,130,246,0.12)' }}>
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#3B82F6" strokeWidth="2.5"><polyline points="20 6 9 17 4 12" /></svg>
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

      {/* Register form */}
      <main className="relative z-10 flex min-h-full min-w-0 flex-1 items-start justify-center px-4 py-8 sm:px-6 lg:px-10">
        <motion.div
          initial={{ opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, ease: [0.34, 1.56, 0.64, 1] }}
          className="my-auto w-full min-w-0 max-w-[26rem]"
        >
          <div className="flex w-full min-w-0 flex-col gap-4 rounded-3xl p-6 backdrop-blur-xl sm:gap-5 sm:p-8" style={{
            background: panelBg,
            border: `1px solid ${borderColor}`,
            boxShadow: '0 24px 64px rgba(0,0,0,0.12)',
          }}>
            <div className="text-center lg:text-left">
              <PlatformMark className="mx-auto mb-4 h-12 w-12 rounded-2xl shadow-sm lg:hidden" />
              <h2 className="text-xl font-bold tracking-tight" style={{ color: textPrimary }}>注册账户</h2>
              <p className="mt-1 text-[11px] leading-5" style={{ color: textDim }}>
                {registrationConfigLoading ? '正在读取邮箱注册设置…' : registrationConfigError ? '暂时无法确认注册设置' : emailVerificationRequired ? '验证邮箱后可用该邮箱和密码登录' : '邮箱和手机号可在安全中心完成验证绑定'}
              </p>
            </div>

            {registrationConfigError && (
              <button type="button" onClick={() => window.location.reload()}
                className="h-9 w-full rounded-xl border px-3 text-xs font-medium"
                style={{ color: '#6366F1', borderColor, background: inputBg }}>
                重新加载注册设置
              </button>
            )}

            {error && (
              <div role="alert" className="flex min-w-0 items-start gap-2.5 rounded-xl p-2.5 text-[12px] leading-5"
                style={{ background: 'rgba(244,63,94,0.06)', border: '1px solid rgba(244,63,94,0.15)', color: '#F43F5E' }}>
                <svg className="mt-0.5 shrink-0" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" /></svg>
                <span className="min-w-0 break-words [overflow-wrap:anywhere]">{error}</span>
              </div>
            )}

            <form noValidate onSubmit={handleSubmit} className="flex min-w-0 flex-col gap-4">
              <div className="min-w-0">
                <label htmlFor="register-username" className="mb-1.5 block text-[12px] font-medium" style={{ color: textDim }}>用户名 *</label>
                <input id="register-username" type="text" value={username} onChange={e => { setUsername(e.target.value); if (error) setError('') }} placeholder="请输入用户名"
                  autoComplete="username" autoCapitalize="none" maxLength={64}
                  className="h-11 w-full min-w-0 rounded-xl px-3.5 text-sm outline-none transition-all duration-200 focus:border-violet-400/60 focus:shadow-[0_0_0_3px_rgba(139,92,246,0.08)]"
                  style={{ background: inputBg, color: textPrimary, border: `1px solid ${borderColor}` }} />
              </div>
              <div className="min-w-0">
                <label htmlFor="register-password" className="mb-1.5 block text-[12px] font-medium" style={{ color: textDim }}>密码 *（至少8位，含大小写字母和数字）</label>
                <input id="register-password" type="password" value={password} onChange={e => { setPassword(e.target.value); if (error) setError('') }} placeholder="请输入密码"
                  autoComplete="new-password"
                  className="h-11 w-full min-w-0 rounded-xl px-3.5 text-sm outline-none transition-all duration-200 focus:border-violet-400/60 focus:shadow-[0_0_0_3px_rgba(139,92,246,0.08)]"
                  style={{ background: inputBg, color: textPrimary, border: `1px solid ${borderColor}` }} />
              </div>
              {emailVerificationRequired && <div className="min-w-0">
                <label htmlFor="register-email" className="mb-1.5 block text-[12px] font-medium" style={{ color: textDim }}>邮箱 *</label>
                <input id="register-email" type="email" value={email} onChange={e => { setEmail(e.target.value); if (error) setError('') }} placeholder="用于接收注册验证码"
                  autoComplete="email" autoCapitalize="none" spellCheck={false} maxLength={128}
                  className="h-11 w-full min-w-0 rounded-xl px-3.5 text-sm outline-none transition-all duration-200 focus:border-violet-400/60 focus:shadow-[0_0_0_3px_rgba(139,92,246,0.08)]"
                  style={{ background: inputBg, color: textPrimary, border: `1px solid ${borderColor}` }} />
              </div>}
              {emailVerificationRequired && <div className="min-w-0">
                <label htmlFor="register-email-code" className="mb-1.5 block text-[12px] font-medium" style={{ color: textDim }}>邮箱验证码 *</label>
                <div className="flex min-w-0 items-center gap-2">
                  <input id="register-email-code" type="text" value={emailCode} onChange={e => { setEmailCode(e.target.value.replace(/\D/g, '').slice(0, 6)); if (error) setError('') }}
                    placeholder="6位验证码" inputMode="numeric" autoComplete="one-time-code" maxLength={6}
                    className="h-11 min-w-0 flex-1 rounded-xl px-3.5 text-sm outline-none transition-all duration-200 focus:border-violet-400/60 focus:shadow-[0_0_0_3px_rgba(139,92,246,0.08)]"
                    style={{ background: inputBg, color: textPrimary, border: `1px solid ${borderColor}` }} />
                  <button type="button" onClick={() => void handleSendEmailCode()}
                    disabled={emailCodeLoading || emailCodeCountdown > 0}
                    className="h-11 min-w-[116px] shrink-0 rounded-xl border px-3 text-xs font-semibold transition-opacity disabled:opacity-50"
                    style={{ background: inputBg, color: '#6366F1', borderColor }}>
                    {emailCodeLoading ? '发送中…' : emailCodeCountdown > 0 ? `${emailCodeCountdown} 秒后重发` : emailCodeSent ? '重新发送' : '发送验证码'}
                  </button>
                </div>
              </div>}
              {(!emailVerificationRequired || !emailCodeSent || emailCodeCountdown === 0) && <div className="min-w-0">
                <label htmlFor="register-captcha" className="mb-1.5 block text-[12px] font-medium" style={{ color: textDim }}>图形验证码 *</label>
                <div className="flex min-w-0 items-center gap-2">
                  <input
                    id="register-captcha"
                    type="text"
                    value={captchaCode}
                    onChange={e => { setCaptchaCode(e.target.value.toUpperCase()); if (error) setError('') }}
                    placeholder="输入验证码"
                    maxLength={5}
                    autoComplete="off"
                    className="h-11 min-w-0 flex-1 rounded-xl px-3.5 text-sm uppercase outline-none transition-all duration-200 focus:border-violet-400/60 focus:shadow-[0_0_0_3px_rgba(139,92,246,0.08)]"
                    style={{ background: inputBg, color: textPrimary, border: `1px solid ${borderColor}` }}
                  />
                  <button
                    type="button"
                    onClick={() => { setError(''); void loadCaptcha() }}
                    disabled={captchaLoading}
                    aria-label="刷新图形验证码"
                    className="h-11 w-32 shrink-0 overflow-hidden rounded-xl border transition-opacity disabled:opacity-50 sm:w-36"
                    style={{ background: inputBg, borderColor }}
                  >
                    {captchaImage ? <img src={captchaImage} alt="图形验证码，点击刷新" className="h-full w-full object-cover" /> : <span className="text-xs" style={{ color: textDim }}>{captchaLoading ? '加载中' : '点击重试'}</span>}
                  </button>
                </div>
                <p className="mt-1.5 text-[11px]" style={{ color: textDim }}>看不清？点击图片刷新</p>
              </div>}
              {emailVerificationRequired && emailCodeSent && emailCodeCountdown > 0 && <p className="text-[11px] leading-5" style={{ color: textDim }}>验证码已发送，请检查收件箱和垃圾邮件文件夹；10分钟内有效。</p>}
              <button type="submit" disabled={loading || registrationConfigLoading || registrationConfigError}
                className="mt-1 h-11 w-full rounded-xl text-sm font-semibold text-white transition-all duration-200 disabled:opacity-50 hover:opacity-90 active:scale-[0.98]"
                style={{ background: 'linear-gradient(135deg, #3B82F6, #6366F1)', boxShadow: '0 4px 20px rgba(59,130,246,0.3)' }}>
                {loading ? (
                  <span className="flex items-center justify-center gap-2">
                    <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white" />
                    注册中...
                  </span>
                ) : '注册'}
              </button>
            </form>

            <div className="flex min-w-0 flex-col gap-3">
              <div className="flex items-center gap-3" style={{ color: textDim }}>
                <span className="h-px flex-1" style={{ background: borderColor }} />
                <span className="shrink-0 text-[11px]">其他注册方式</span>
                <span className="h-px flex-1" style={{ background: borderColor }} />
              </div>
              <button
                type="button"
                onClick={() => void handleQQLogin()}
                disabled={oauthLoading}
                aria-label="使用 QQ 登录或注册"
                title={qqOAuthStatus === 'pending_review' ? 'QQ 应用正在等待腾讯审核' : undefined}
                className="flex h-11 w-full items-center justify-center gap-2 rounded-xl border text-sm font-semibold transition-colors hover:bg-sky-500/10 disabled:opacity-50"
                style={{ color: textPrimary, borderColor, background: inputBg }}
              >
                {oauthLoading
                  ? <span className="h-4 w-4 animate-spin rounded-full border-2 border-sky-500/30 border-t-sky-500" />
                  : <img src="/qq-connect-login.png" alt="" width={16} height={16} />}
                <span>QQ 登录或注册</span>
              </button>
              <p className="text-center text-[11px] leading-4" style={{ color: textDim }}>
                {qqOAuthStatus === 'configured'
                  ? '可使用 QQ 账号快速登录或注册'
                  : qqOAuthStatus === 'pending_review'
                    ? 'QQ 登录入口已开放，腾讯应用审核中，暂可能无法完成授权'
                    : qqOAuthStatus === 'unconfigured'
                      ? 'QQ 登录服务尚未配置'
                      : qqOAuthStatus === 'unavailable'
                        ? '暂时无法读取 QQ 登录状态，可点击按钮重试'
                        : '正在检查 QQ 登录服务'}
              </p>
            </div>

            <p className="text-center text-[12px]" style={{ color: textDim }}>
              已有账户？ <Link to="/login" className="font-semibold transition-colors hover:opacity-80" style={{ color: '#8B5CF6' }}>立即登录</Link>
            </p>
          </div>
        </motion.div>
      </main>
    </div>
  )
}
