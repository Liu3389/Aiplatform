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

  const textPrimary = isDark ? '#fff' : '#333'
  const textDim = isDark ? '#9CA3AF' : '#666'
  const textMuted = isDark ? '#6B7280' : '#999'
  const borderColor = isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.1)'
  const inputBg = isDark ? 'rgba(255,255,255,0.04)' : '#f5f5f5'
  const panelBg = isDark ? 'rgba(255,255,255,0.03)' : 'rgba(255,255,255,0.9)'

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!username || !password) { setError('请填写用户名和密码'); return }
    setError(''); setLoading(true)
    try {
      const data = await authApi.login({ username, password })
      setToken(data.token)
      setUserId(data.userInfo.id)
      await fetchUserInfo()
      navigate('/', { replace: true })
    } catch (err) { setError((err as Error).message || '登录失败') }
    finally { setLoading(false) }
  }

  return (
    <div className="min-h-screen flex relative overflow-hidden" style={{ background: isDark ? '#0B1020' : '#f8fafc' }}>
      {isDark && (
        <>
          <div className="absolute inset-0 bg-aurora" />
          <div className="absolute top-1/4 -left-20 w-96 h-96 bg-brand-purple/10 rounded-full blur-3xl animate-float" />
          <div className="absolute bottom-1/4 -right-20 w-96 h-96 bg-brand-blue/10 rounded-full blur-3xl animate-float" style={{ animationDelay: '-3s' }} />
        </>
      )}

      {/* Left brand panel - desktop only */}
      <div className="hidden lg:flex w-[480px] relative z-10 flex-col justify-between p-12" style={{ background: isDark ? 'transparent' : 'linear-gradient(180deg, #667eea10, #764ba210)' }}>
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-brand-purple to-brand-blue flex items-center justify-center shadow-lg shadow-brand-purple/25">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
            </svg>
          </div>
          <div>
            <div className="text-lg font-bold" style={{ color: textPrimary }}>AI Gateway</div>
            <div className="text-xs" style={{ color: textDim }}>模型调度网关</div>
          </div>
        </div>

        <div className="space-y-6">
          <div>
            <h2 className="text-3xl font-bold mb-1" style={{ color: textPrimary }}>欢迎回来</h2>
            <p className="text-sm" style={{ color: textDim }}>登录你的 AI Gateway 控制台</p>
          </div>
          <div className="space-y-4">
            {[
              { title:'多模型调度', desc:'统一管理 OpenAI、Claude、DeepSeek 等主流模型' },
              { title:'高性能流式', desc:'基于 Pretext 引擎，120FPS 零 DOM 重排流式渲染' },
              { title:'企业级安全', desc:'BCrypt 加密 · Redis Token · API Key 鉴权' },
            ].map((f,i)=>(
              <div key={i} className="flex items-start gap-3">
                <div className="w-6 h-6 rounded-lg bg-brand-purple/10 border border-brand-purple/20 flex items-center justify-center flex-shrink-0 mt-0.5">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#8B5CF6" strokeWidth="2.5"><polyline points="20 6 9 17 4 12"/></svg>
                </div>
                <div>
                  <div className="text-sm font-medium" style={{ color: textPrimary }}>{f.title}</div>
                  <div className="text-xs" style={{ color: textDim }}>{f.desc}</div>
                </div>
              </div>
            ))}
          </div>
        </div>

        <p className="text-[11px]" style={{ color: textMuted }}>© 2026 AI Gateway Platform</p>
      </div>

      {/* Right login form */}
      <div className="flex-1 flex items-center justify-center relative z-10 p-4 sm:p-8">
        <motion.div
          initial={{ opacity:0, y:20 }} animate={{ opacity:1, y:0 }} transition={{ duration:0.5 }}
          className="w-full max-w-sm sm:max-w-md"
        >
          <div className="rounded-2xl p-6 sm:p-8 space-y-5 border" style={{ background: panelBg, borderColor }}>
            <div className="text-center lg:hidden mb-2">
              <div className="w-12 h-12 mx-auto rounded-xl bg-gradient-to-br from-brand-purple to-brand-blue flex items-center justify-center shadow-lg shadow-brand-purple/25 mb-3">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
              </div>
              <h2 className="text-2xl font-bold" style={{ color: textPrimary }}>登录</h2>
            </div>

            <h2 className="hidden lg:block text-xl font-semibold" style={{ color: textPrimary }}>登录账户</h2>

            {error && (
              <div className="flex items-center gap-2 p-3 rounded-lg bg-accent-rose/10 border border-accent-rose/20 text-accent-rose text-xs">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
                {error}
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-medium mb-1.5" style={{ color: textDim }}>用户名</label>
                <input type="text" value={username} onChange={e=>setUsername(e.target.value)} placeholder="请输入用户名"
                  className="w-full px-4 py-3 rounded-xl text-sm outline-none border transition-colors focus:border-brand-purple"
                  style={{ background: inputBg, color: textPrimary, borderColor }} />
              </div>
              <div>
                <label className="block text-xs font-medium mb-1.5" style={{ color: textDim }}>密码</label>
                <input type="password" value={password} onChange={e=>setPassword(e.target.value)} placeholder="请输入密码"
                  className="w-full px-4 py-3 rounded-xl text-sm outline-none border transition-colors focus:border-brand-purple"
                  style={{ background: inputBg, color: textPrimary, borderColor }} />
              </div>
              <button type="submit" disabled={loading}
                className="w-full py-3 rounded-xl text-sm font-medium text-white transition-all disabled:opacity-50"
                style={{ background: 'linear-gradient(135deg, #8B5CF6, #3B82F6)' }}>
                {loading ? '登录中...' : '登录'}
              </button>
            </form>

            <p className="text-center text-xs" style={{ color: textDim }}>
              还没有账户？ <Link to="/register" className="text-brand-purple hover:text-brand-blue transition-colors font-medium">立即注册</Link>
            </p>

            <div className="pt-2 border-t" style={{ borderColor }}>
              <p className="text-center text-[10px] mb-3" style={{ color: textMuted }}>第三方登录</p>
              <div className="flex justify-center gap-3">
                <button className="w-10 h-10 rounded-xl border flex items-center justify-center transition-colors hover:bg-black/5"
                  style={{ background: isDark ? 'rgba(255,255,255,0.04)' : '#f5f5f5', borderColor }}>
                  <svg width="18" height="18" viewBox="0 0 24 24" fill={isDark ? 'white' : '#333'}><path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z"/></svg>
                </button>
                <button className="w-10 h-10 rounded-xl border flex items-center justify-center transition-colors hover:bg-black/5"
                  style={{ background: isDark ? 'rgba(255,255,255,0.04)' : '#f5f5f5', borderColor }}>
                  <svg width="18" height="18" viewBox="0 0 24 24"><path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 01-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z" fill="#4285F4"/><path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/><path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/><path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/></svg>
                </button>
              </div>
            </div>
          </div>
        </motion.div>
      </div>
    </div>
  )
}
