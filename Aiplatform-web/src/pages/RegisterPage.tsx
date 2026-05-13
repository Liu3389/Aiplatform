import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { authApi } from '@/services/api'
import { useAppStore } from '@/stores/appStore'
import { motion } from 'framer-motion'

export default function RegisterPage() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [email, setEmail] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const { setToken, setUserId, fetchUserInfo } = useAppStore()
  const navigate = useNavigate()

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!username || !password) { setError('请填写用户名和密码'); return }
    if (password.length < 6) { setError('密码至少6个字符'); return }
    setError(''); setLoading(true)
    try {
      await authApi.register({ username, password, email: email || undefined })
      const loginData = await authApi.login({ username, password })
      setToken(loginData.token)
      setUserId(loginData.userInfo.id)
      await fetchUserInfo()
      navigate('/', { replace: true })
    } catch (err) { setError((err as Error).message || '注册失败') }
    finally { setLoading(false) }
  }

  return (
    <div className="min-h-screen flex bg-[#0B1020] noise-bg relative overflow-hidden">
      <div className="absolute inset-0 bg-aurora" />
      <div className="absolute bottom-1/3 left-1/3 w-96 h-96 bg-brand-purple/10 rounded-full blur-3xl animate-float" style={{animationDelay:'-4s'}}/>
      <div className="absolute top-1/3 right-1/3 w-96 h-96 bg-brand-cyan/8 rounded-full blur-3xl animate-float" style={{animationDelay:'-1.5s'}}/>

      <div className="hidden lg:flex w-[480px] relative z-10 flex-col justify-between p-12">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-brand-purple to-brand-blue flex items-center justify-center shadow-lg shadow-brand-purple/25">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
          </div>
          <div>
            <div className="text-lg font-bold text-white">AI Gateway</div>
            <div className="text-xs text-neutral-500">模型调度网关</div>
          </div>
        </div>

        <div className="space-y-6">
          <div>
            <h2 className="text-3xl font-bold text-white mb-1">创建账户</h2>
            <p className="text-neutral-400 text-sm">开始使用 AI Gateway 平台</p>
          </div>
          <div className="space-y-4">
            {[
              { title:'快速接入', desc:'注册即获 API Key，立即调用主流模型' },
              { title:'免费额度', desc:'新用户赠送免费调用额度' },
              { title:'流式体验', desc:'支持 SSE 流式输出，Pretext 高性能渲染' },
            ].map((f,i)=>(
              <div key={i} className="flex items-start gap-3">
                <div className="w-6 h-6 rounded-lg bg-brand-blue/10 border border-brand-blue/20 flex items-center justify-center flex-shrink-0 mt-0.5">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#3B82F6" strokeWidth="2.5"><polyline points="20 6 9 17 4 12"/></svg>
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

      <div className="flex-1 flex items-center justify-center relative z-10 p-8">
        <motion.div initial={{ opacity:0, y:20 }} animate={{ opacity:1, y:0 }} className="w-full max-w-md">
          <div className="glass-panel-lg p-8 space-y-5">
            <h2 className="text-xl font-semibold text-white">注册账户</h2>

            {error && (
              <div className="flex items-center gap-2 p-3 rounded-lg bg-accent-rose/10 border border-accent-rose/20 text-accent-rose text-xs">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
                {error}
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-neutral-300 mb-1.5">用户名 *</label>
                <input type="text" value={username} onChange={e=>setUsername(e.target.value)} placeholder="请输入用户名" className="input-glow w-full px-4 py-3 text-sm" />
              </div>
              <div>
                <label className="block text-xs font-medium text-neutral-300 mb-1.5">邮箱（选填）</label>
                <input type="email" value={email} onChange={e=>setEmail(e.target.value)} placeholder="请输入邮箱" className="input-glow w-full px-4 py-3 text-sm" />
              </div>
              <div>
                <label className="block text-xs font-medium text-neutral-300 mb-1.5">密码 *（至少6个字符）</label>
                <input type="password" value={password} onChange={e=>setPassword(e.target.value)} placeholder="请输入密码" className="input-glow w-full px-4 py-3 text-sm" />
              </div>
              <button type="submit" disabled={loading} className="btn-neon w-full py-3 disabled:opacity-50 text-sm">
                {loading ? '注册中...' : '注册'}
              </button>
            </form>

            <p className="text-center text-xs text-neutral-500">
              已有账户？ <Link to="/login" className="text-brand-purple hover:text-brand-blue transition-colors font-medium">立即登录</Link>
            </p>
          </div>
        </motion.div>
      </div>
    </div>
  )
}
