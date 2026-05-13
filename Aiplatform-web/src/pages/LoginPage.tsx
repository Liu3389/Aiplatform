import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { authApi } from '@/services/api'
import { useAppStore } from '@/stores/appStore'
import { motion } from 'framer-motion'

export default function LoginPage() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const { setToken, setUserId, fetchUserInfo } = useAppStore()
  const navigate = useNavigate()

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

  const handleOAuthLogin = async (provider: string) => {
    try {
      const authUrl = await authApi.getOAuthUrl(provider)
      localStorage.setItem('oauth_provider', provider)
      localStorage.setItem('oauth_state', authUrl.state)
      window.open(authUrl.url, '_self')
    } catch {
      setError('获取第三方登录链接失败')
    }
  }

  return (
    <div className="min-h-screen flex bg-[#0B1020] noise-bg relative overflow-hidden">
      {/* Background effects */}
      <div className="absolute inset-0 bg-aurora" />
      <div className="absolute top-1/4 -left-20 w-96 h-96 bg-brand-purple/10 rounded-full blur-3xl animate-float" />
      <div className="absolute bottom-1/4 -right-20 w-96 h-96 bg-brand-blue/10 rounded-full blur-3xl animate-float" style={{ animationDelay: '-3s' }} />

      {/* Left brand panel */}
      <div className="hidden lg:flex w-[480px] relative z-10 flex-col justify-between p-12">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-brand-purple to-brand-blue flex items-center justify-center shadow-lg shadow-brand-purple/25">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
            </svg>
          </div>
          <div>
            <div className="text-lg font-bold text-white">AI Gateway</div>
            <div className="text-xs text-neutral-500">模型调度网关</div>
          </div>
        </div>

        <div className="space-y-6">
          <div>
            <h2 className="text-3xl font-bold text-white mb-1">欢迎回来</h2>
            <p className="text-neutral-400 text-sm">登录你的 AI Gateway 控制台</p>
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
                  <div className="text-sm font-medium text-white">{f.title}</div>
                  <div className="text-xs text-neutral-500">{f.desc}</div>
                </div>
              </div>
            ))}
          </div>
        </div>

        <p className="text-[11px] text-neutral-600">© 2026 AI Gateway Platform</p>
      </div>

      {/* Right login form */}
      <div className="flex-1 flex items-center justify-center relative z-10 p-8">
        <motion.div
          initial={{ opacity:0, y:20 }} animate={{ opacity:1, y:0 }} transition={{ duration:0.5 }}
          className="w-full max-w-md"
        >
          <div className="glass-panel-lg p-8 space-y-5">
            <div className="text-center lg:hidden mb-2">
              <div className="w-12 h-12 mx-auto rounded-xl bg-gradient-to-br from-brand-purple to-brand-blue flex items-center justify-center shadow-lg shadow-brand-purple/25 mb-3">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
              </div>
              <h2 className="text-2xl font-bold text-white">登录</h2>
            </div>

            <h2 className="hidden lg:block text-xl font-semibold text-white">登录账户</h2>

            {error && (
              <div className="flex items-center gap-2 p-3 rounded-lg bg-accent-rose/10 border border-accent-rose/20 text-accent-rose text-xs">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
                {error}
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-neutral-300 mb-1.5">用户名</label>
                <input type="text" value={username} onChange={e=>setUsername(e.target.value)} placeholder="请输入用户名" className="input-glow w-full px-4 py-3 text-sm" />
              </div>
              <div>
                <label className="block text-xs font-medium text-neutral-300 mb-1.5">密码</label>
                <input type="password" value={password} onChange={e=>setPassword(e.target.value)} placeholder="请输入密码" className="input-glow w-full px-4 py-3 text-sm" />
              </div>
              <button type="submit" disabled={loading} className="btn-neon w-full py-3 disabled:opacity-50 text-sm">
                {loading ? '登录中...' : '登录'}
              </button>
            </form>

            <p className="text-center text-xs text-neutral-500">
              还没有账户？ <Link to="/register" className="text-brand-purple hover:text-brand-blue transition-colors font-medium">立即注册</Link>
            </p>

            <div className="pt-2 border-t border-white/[0.06]">
              <p className="text-center text-[10px] text-neutral-600 mb-3">第三方登录</p>
              <div className="flex justify-center gap-3">
                <button
                  onClick={() => handleOAuthLogin('github')}
                  className="w-10 h-10 rounded-xl bg-white/[0.04] border border-white/[0.06] flex items-center justify-center hover:bg-white/[0.08] transition-colors"
                  title="GitHub登录"
                >
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="white"><path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z"/></svg>
                </button>
                <button
                  onClick={() => handleOAuthLogin('google')}
                  className="w-10 h-10 rounded-xl bg-white/[0.04] border border-white/[0.06] flex items-center justify-center hover:bg-white/[0.08] transition-colors"
                  title="Google登录"
                >
                  <svg width="18" height="18" viewBox="0 0 24 24"><path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 01-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z" fill="#4285F4"/><path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/><path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/><path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/></svg>
                </button>
                <button
                  onClick={() => handleOAuthLogin('wechat')}
                  className="w-10 h-10 rounded-xl bg-white/[0.04] border border-white/[0.06] flex items-center justify-center hover:bg-white/[0.08] transition-colors"
                  title="微信登录"
                >
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="#07C160"><path d="M8.691 2.188C3.891 2.188 0 5.476 0 9.53c0 2.212 1.17 4.203 3.002 5.55a.59.59 0 01.213.665l-.39 1.48c-.019.07-.048.141-.048.213 0 .163.13.295.29.295a.326.326 0 00.167-.054l1.903-1.114a.864.864 0 01.717-.098 10.16 10.16 0 002.837.403c.276 0 .543-.027.811-.05-.857-2.578.157-4.972 1.932-6.446 1.703-1.415 3.882-1.98 5.853-1.838-.576-3.583-4.196-6.348-8.596-6.348zM5.785 5.991c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 01-1.162 1.178A1.17 1.17 0 014.623 7.17c0-.651.52-1.18 1.162-1.18zm5.813 0c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 01-1.162 1.178 1.17 1.17 0 01-1.162-1.178c0-.651.52-1.18 1.162-1.18zm3.53 2.962c-2.755 0-4.99 2.04-4.99 4.556 0 2.516 2.235 4.556 4.99 4.556.466 0 .914-.073 1.336-.204a.529.529 0 01.415.058l.918.557a.17.17 0 00.09.027.153.153 0 00.152-.153c0-.042-.014-.075-.026-.11l-.203-.766a.303.303 0 01.11-.34C19.257 17.02 20 15.924 20 14.618c0-2.514-2.236-4.556-4.99-4.556-.012.102-.012.204-.012.306 0 1.75 1.044 3.407 2.734 4.18.176.08.283.25.283.436a.453.453 0 01-.66.393c-1.644-.774-3.136-1.934-3.136-3.77 0-1.18.677-2.376 1.774-3.154-1.015-.266-2.113-.384-3.245-.384zM12.29 13.016a.712.712 0 01.714.716.712.712 0 01-.714.716.712.712 0 01-.713-.716c0-.396.32-.716.713-.716zm3.483 0a.712.712 0 01.714.716.712.712 0 01-.714.716.712.712 0 01-.713-.716c0-.396.32-.716.713-.716z"/></svg>
                </button>
              </div>
            </div>
          </div>

          <div className="mt-6 text-center text-[10px] text-neutral-600 space-y-1">
            <p>测试账号: superadmin / admin123</p>
          </div>
        </motion.div>
      </div>
    </div>
  )
}
