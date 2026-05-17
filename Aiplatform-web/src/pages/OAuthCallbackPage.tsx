import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { authApi } from '@/services/api'
import { useAppStore } from '@/stores/appStore'
import { motion } from 'framer-motion'

export default function OAuthCallbackPage() {
  const [searchParams] = useSearchParams()
  const [error, setError] = useState('')
  const [status, setStatus] = useState('处理中...')
  const { setToken, setUserId, fetchUserInfo } = useAppStore()
  const navigate = useNavigate()

  useEffect(() => {
    const handleCallback = async () => {
      const code = searchParams.get('code')
      const state = searchParams.get('state')
      const provider = localStorage.getItem('oauth_provider') || 'github'
      const savedState = localStorage.getItem('oauth_state')

      if (!code) {
        setError('授权失败，未获取到授权码')
        setTimeout(() => navigate('/login'), 3000)
        return
      }

      setStatus('正在验证登录...')
      try {
        const data = await authApi.oauthCallback(provider, code, state || savedState || '')
        setToken(data.token)
        setUserId(data.userInfo.id)
        await fetchUserInfo()
        localStorage.removeItem('oauth_provider')
        localStorage.removeItem('oauth_state')
        setStatus('登录成功，正在跳转...')
        setTimeout(() => navigate('/chat', { replace: true }), 500)
      } catch (err) {
        setError('第三方登录失败: ' + ((err as Error).message || '请重试'))
        setTimeout(() => navigate('/login'), 3000)
      }
    }
    handleCallback()
  }, [])

  return (
    <div className="min-h-screen flex items-center justify-center bg-[#0B1020]">
      <div className="bg-aurora absolute inset-0" />
      <motion.div
        initial={{ opacity: 0, scale: 0.9 }}
        animate={{ opacity: 1, scale: 1 }}
        className="relative z-10 p-8 rounded-2xl border border-white/[0.06] bg-white/[0.02] backdrop-blur-xl text-center"
      >
        {error ? (
          <div>
            <div className="w-12 h-12 mx-auto rounded-full bg-accent-rose/10 border border-accent-rose/20 flex items-center justify-center mb-4">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#F43F5E" strokeWidth="2">
                <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
              </svg>
            </div>
            <p className="text-accent-rose text-sm mb-2">{error}</p>
            <p className="text-neutral-500 text-xs">3秒后返回登录页...</p>
          </div>
        ) : (
          <div>
            <motion.div animate={{ rotate: 360 }} transition={{ duration: 1, repeat: Infinity, ease: 'linear' }} className="w-12 h-12 mx-auto mb-4">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#8B5CF6" strokeWidth="2">
                <path d="M12 2a10 10 0 019.95 9" strokeLinecap="round"/>
              </svg>
            </motion.div>
            <p className="text-white text-sm">{status}</p>
          </div>
        )}
      </motion.div>
    </div>
  )
}
