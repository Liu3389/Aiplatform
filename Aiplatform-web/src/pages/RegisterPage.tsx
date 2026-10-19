import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { authApi } from '@/services/api'
import { useThemeStore } from '@/stores/themeStore'
import { motion } from 'framer-motion'

export default function RegisterPage() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [captchaId, setCaptchaId] = useState('')
  const [captchaImage, setCaptchaImage] = useState('')
  const [captchaCode, setCaptchaCode] = useState('')
  const [captchaLoading, setCaptchaLoading] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
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
      setError((err as Error).message || '验证码加载失败，请刷新页面重试')
    } finally {
      setCaptchaLoading(false)
    }
  }

  useEffect(() => { void loadCaptcha() }, [])

  const textPrimary = isDark ? '#F1F5F9' : '#0F172A'
  const textDim = isDark ? '#94A3B8' : '#64748B'
  const borderColor = isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)'
  const inputBg = isDark ? 'rgba(20,28,48,0.6)' : 'rgba(0,0,0,0.03)'
  const panelBg = isDark ? 'rgba(16,24,42,0.6)' : 'rgba(255,255,255,0.8)'

  const handleSubmit = async (e: React.SyntheticEvent) => {
    e.preventDefault()
    if (!username || !password) { setError('请填写用户名和密码'); return }
    if (password.length < 8) { setError('密码至少8个字符，并包含大小写字母和数字'); return }
    if (!captchaId || !captchaCode.trim()) { setError('请输入图形验证码'); return }
    setError(''); setLoading(true)
    try {
      await authApi.register({ username, password, captchaId, captchaCode })
      navigate('/login', { replace: true, state: { notice: '注册成功，请使用账号和密码登录' } })
    } catch (err) {
      setError((err as Error).message || '注册失败')
      void loadCaptcha()
    }
    finally { setLoading(false) }
  }

  return (
    <div className="min-h-screen flex relative overflow-hidden" style={{ background: isDark ? '#080B16' : '#F8FAFC' }}>
      {/* Ambient background */}
      <div className="absolute inset-0 bg-aurora opacity-60" />
      <div className="absolute top-40 -right-20 w-[400px] h-[400px] rounded-full opacity-[0.06]"
        style={{ background: 'radial-gradient(circle, #3B82F6, transparent 70%)' }} />
      <div className="absolute bottom-40 -left-20 w-[400px] h-[400px] rounded-full opacity-[0.04]"
        style={{ background: 'radial-gradient(circle, #06B6D4, transparent 70%)' }} />

      {/* Brand panel */}
      <div className="hidden lg:flex w-[480px] relative z-10 flex-col justify-between p-14">
        <div className="flex items-center gap-3">
          <div className="w-11 h-11 rounded-xl flex items-center justify-center shadow-xl"
            style={{ background: 'linear-gradient(135deg, #8B5CF6, #6366F1)', boxShadow: '0 8px 32px rgba(139,92,246,0.3)' }}>
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
          </div>
          <div>
            <div className="text-lg font-bold tracking-tight" style={{ color: textPrimary }}>凌点 AI Gateway</div>
            <div className="text-[11px] tracking-wide" style={{ color: textDim }}>新一代模型调度网关平台</div>
          </div>
        </div>

        <div className="space-y-8">
          <div>
            <h1 className="text-4xl font-bold mb-2 tracking-tight" style={{ color: textPrimary, fontFamily: 'var(--font-display)' }}>
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
                <div className="w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0 mt-0.5"
                  style={{ background: 'rgba(59,130,246,0.08)', border: '1px solid rgba(59,130,246,0.12)' }}>
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#3B82F6" strokeWidth="2.5"><polyline points="20 6 9 17 4 12"/></svg>
                </div>
                <div>
                  <div className="text-sm font-semibold mb-0.5" style={{ color: textPrimary }}>{f.title}</div>
                  <div className="text-[12px] leading-relaxed" style={{ color: textDim }}>{f.desc}</div>
                </div>
              </div>
            ))}
          </div>
        </div>

        <p className="text-[11px]" style={{ color: 'var(--text-dim)', opacity: 0.5 }}>© 2026 Lingdian AI Gateway</p>
      </div>

      {/* Register form */}
      <div className="flex-1 flex items-center justify-center relative z-10 p-6">
        <motion.div
          initial={{ opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, ease: [0.34, 1.56, 0.64, 1] }}
          className="w-full max-w-sm sm:max-w-md"
        >
          <div className="rounded-3xl p-8 space-y-6 backdrop-blur-xl" style={{
            background: panelBg,
            border: `1px solid ${borderColor}`,
            boxShadow: '0 24px 64px rgba(0,0,0,0.12)',
          }}>
            <h2 className="text-2xl font-bold tracking-tight" style={{ color: textPrimary }}>注册账户</h2>
            <p className="-mt-4 text-xs" style={{ color: textDim }}>邮箱和手机号可在安全中心完成验证绑定</p>

            {error && (
              <div className="flex items-center gap-2.5 p-3.5 rounded-xl text-[12px]"
                style={{ background: 'rgba(244,63,94,0.06)', border: '1px solid rgba(244,63,94,0.15)', color: '#F43F5E' }}>
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
                {error}
              </div>
            )}

            <form noValidate onSubmit={handleSubmit} className="space-y-4.5">
              <div>
                <label className="block text-[12px] font-medium mb-2" style={{ color: textDim }}>用户名 *</label>
                <input type="text" value={username} onChange={e => setUsername(e.target.value)} placeholder="请输入用户名"
                  className="w-full px-4 py-3.5 rounded-xl text-sm outline-none transition-all duration-200 focus:shadow-[0_0_0_3px_rgba(139,92,246,0.08)]"
                  style={{ background: inputBg, color: textPrimary, border: `1px solid ${borderColor}` }} />
              </div>
              <div>
                <label className="block text-[12px] font-medium mb-2" style={{ color: textDim }}>密码 *（至少8位，含大小写字母和数字）</label>
                <input type="password" value={password} onChange={e => setPassword(e.target.value)} placeholder="请输入密码"
                  className="w-full px-4 py-3.5 rounded-xl text-sm outline-none transition-all duration-200 focus:shadow-[0_0_0_3px_rgba(139,92,246,0.08)]"
                  style={{ background: inputBg, color: textPrimary, border: `1px solid ${borderColor}` }} />
              </div>
              <div>
                <label htmlFor="register-captcha" className="block text-[12px] font-medium mb-2" style={{ color: textDim }}>图形验证码 *</label>
                <div className="flex items-center gap-2">
                  <input
                    id="register-captcha"
                    type="text"
                    value={captchaCode}
                    onChange={e => setCaptchaCode(e.target.value.toUpperCase())}
                    placeholder="输入验证码"
                    maxLength={5}
                    autoComplete="off"
                    className="min-w-0 flex-1 px-4 py-3.5 rounded-xl text-sm uppercase outline-none transition-all duration-200 focus:shadow-[0_0_0_3px_rgba(139,92,246,0.08)]"
                    style={{ background: inputBg, color: textPrimary, border: `1px solid ${borderColor}` }}
                  />
                  <button
                    type="button"
                    onClick={() => void loadCaptcha()}
                    disabled={captchaLoading}
                    aria-label="刷新图形验证码"
                    className="h-[50px] w-[144px] shrink-0 overflow-hidden rounded-xl border transition-opacity disabled:opacity-50"
                    style={{ background: inputBg, borderColor }}
                  >
                    {captchaImage ? <img src={captchaImage} alt="图形验证码，点击刷新" className="h-full w-full object-cover" /> : <span className="text-xs" style={{ color: textDim }}>加载中</span>}
                  </button>
                </div>
                <p className="mt-1.5 text-[11px]" style={{ color: textDim }}>看不清？点击图片刷新</p>
              </div>
              <button type="submit" disabled={loading}
                className="w-full py-3.5 rounded-xl text-sm font-semibold text-white transition-all duration-200 disabled:opacity-50 hover:opacity-90 active:scale-[0.98]"
                style={{ background: 'linear-gradient(135deg, #3B82F6, #6366F1)', boxShadow: '0 4px 20px rgba(59,130,246,0.3)' }}>
                {loading ? (
                  <span className="flex items-center justify-center gap-2">
                    <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                    注册中...
                  </span>
                ) : '注册'}
              </button>
            </form>

            <p className="text-center text-[12px]" style={{ color: textDim }}>
              已有账户？ <Link to="/login" className="font-semibold transition-colors hover:opacity-80" style={{ color: '#8B5CF6' }}>立即登录</Link>
            </p>
          </div>
        </motion.div>
      </div>
    </div>
  )
}
