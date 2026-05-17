import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { authApi } from '@/services/api'
import { useAppStore } from '@/stores/appStore'
import { useThemeStore } from '@/stores/themeStore'
import { motion } from 'framer-motion'

export default function LoginPage() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const { setToken, setUserId, fetchUserInfo } = useAppStore()
  const { theme } = useThemeStore()
  const navigate = useNavigate()
  const isDark = theme === 'dark'

  const textPrimary = isDark ? '#F1F5F9' : '#0F172A'
  const textDim = isDark ? '#94A3B8' : '#64748B'
  const borderColor = isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)'
  const inputBg = isDark ? 'rgba(20,28,48,0.6)' : 'rgba(0,0,0,0.03)'
  const panelBg = isDark ? 'rgba(16,24,42,0.6)' : 'rgba(255,255,255,0.8)'

  const handleSubmit = async (e: React.SyntheticEvent) => {
    e.preventDefault()
    if (!username || !password) { setError('请填写用户名和密码'); return }
    setError(''); setLoading(true)
    try {
      const data = await authApi.login({ username, password })
      setToken(data.token)
      setUserId(data.userInfo.id)
      await fetchUserInfo()
      navigate('/chat', { replace: true })
    } catch (err) { setError((err as Error).message || '登录失败') }
    finally { setLoading(false) }
  }

  return (
    <div className="min-h-screen flex relative overflow-hidden" style={{ background: isDark ? '#080B16' : '#F8FAFC' }}>
      {/* Ambient background glow */}
      <div className="absolute inset-0 bg-aurora opacity-60" />
      <div className="absolute top-20 -left-40 w-[500px] h-[500px] rounded-full opacity-[0.07]"
        style={{ background: 'radial-gradient(circle, #8B5CF6, transparent 70%)' }} />
      <div className="absolute bottom-20 -right-40 w-[500px] h-[500px] rounded-full opacity-[0.05]"
        style={{ background: 'radial-gradient(circle, #3B82F6, transparent 70%)' }} />

      {/* Brand panel — left side */}
      <div className="hidden lg:flex w-[480px] relative z-10 flex-col justify-between p-14">
        <div className="flex items-center gap-3">
          <div className="w-11 h-11 rounded-xl flex items-center justify-center shadow-xl"
            style={{ background: 'linear-gradient(135deg, #8B5CF6, #6366F1)', boxShadow: '0 8px 32px rgba(139,92,246,0.3)' }}>
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
            </svg>
          </div>
          <div>
            <div className="text-lg font-bold tracking-tight" style={{ color: textPrimary }}>凌点 AI Gateway</div>
            <div className="text-[11px] tracking-wide" style={{ color: textDim }}>新一代模型调度网关平台</div>
          </div>
        </div>

        <div className="space-y-8">
          <div>
            <h1 className="text-4xl font-bold mb-2 tracking-tight" style={{ color: textPrimary, fontFamily: 'var(--font-display)' }}>
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
                <div className="w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0 mt-0.5"
                  style={{ background: 'rgba(139,92,246,0.08)', border: '1px solid rgba(139,92,246,0.12)' }}>
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#8B5CF6" strokeWidth="2.5"><polyline points="20 6 9 17 4 12"/></svg>
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

      {/* Login form — right side */}
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
            {/* Mobile logo */}
            <div className="text-center lg:hidden">
              <div className="w-14 h-14 mx-auto rounded-2xl flex items-center justify-center shadow-xl mb-4"
                style={{ background: 'linear-gradient(135deg, #8B5CF6, #6366F1)', boxShadow: '0 8px 32px rgba(139,92,246,0.3)' }}>
                <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
              </div>
              <h2 className="text-2xl font-bold tracking-tight" style={{ color: textPrimary }}>登录账户</h2>
            </div>

            <h2 className="hidden lg:block text-xl font-bold tracking-tight" style={{ color: textPrimary }}>登录你的账户</h2>

            {error && (
              <div className="flex items-center gap-2.5 p-3.5 rounded-xl text-[12px]"
                style={{ background: 'rgba(244,63,94,0.06)', border: '1px solid rgba(244,63,94,0.15)', color: '#F43F5E' }}>
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
                {error}
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-[12px] font-medium mb-1.5" style={{ color: textDim }}>用户名</label>
                <input type="text" value={username} onChange={e => setUsername(e.target.value)} placeholder="请输入用户名"
                  className="w-full px-3.5 py-3 rounded-xl text-sm outline-none transition-all duration-200 focus:shadow-[0_0_0_3px_rgba(139,92,246,0.08)]"
                  style={{ background: inputBg, color: textPrimary, border: `1px solid ${borderColor}` }} />
              </div>
              <div>
                <label className="block text-[12px] font-medium mb-1.5" style={{ color: textDim }}>密码</label>
                <input type="password" value={password} onChange={e => setPassword(e.target.value)} placeholder="请输入密码"
                  className="w-full px-3.5 py-3 rounded-xl text-sm outline-none transition-all duration-200 focus:shadow-[0_0_0_3px_rgba(139,92,246,0.08)]"
                  style={{ background: inputBg, color: textPrimary, border: `1px solid ${borderColor}` }} />
              </div>
              <button type="submit" disabled={loading}
                className="w-full py-3 rounded-xl text-sm font-semibold text-white transition-all duration-200 disabled:opacity-50 hover:opacity-90 active:scale-[0.98]"
                style={{ background: 'linear-gradient(135deg, #8B5CF6, #6366F1)', boxShadow: '0 4px 20px rgba(139,92,246,0.3)' }}>
                {loading ? (
                  <span className="flex items-center justify-center gap-2">
                    <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                    登录中...
                  </span>
                ) : '登录'}
              </button>
            </form>

            <p className="text-center text-[12px]" style={{ color: textDim }}>
              还没有账户？ <Link to="/register" className="font-semibold transition-colors hover:opacity-80" style={{ color: '#8B5CF6' }}>立即注册</Link>
            </p>
          </div>
        </motion.div>
      </div>
    </div>
  )
}