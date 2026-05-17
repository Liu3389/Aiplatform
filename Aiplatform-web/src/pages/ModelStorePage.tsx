import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { modelApi } from '@/services/api'
import { motion } from 'framer-motion'
import { useI18nStore } from '@/stores/i18nStore'
import { useThemeStore } from '@/stores/themeStore'

// 聊天页面可用的模型列表（与 platform_model_config 表中的模型对应）
const CHAT_MODELS = new Set([
  'deepseek-v4-flash',
  'deepseek-v3',
  'gpt-4o',
  'gpt-4-turbo',
  'claude-3-5-sonnet',
  'gemini-2.0-flash',
  'grok-3',
  'qwen-max',
  'glm-4',
  'doubao-seed-1.6',
  'mini-max-m1',
])

export default function ModelStorePage() {
  const { lang } = useI18nStore()
  const { theme } = useThemeStore()
  const navigate = useNavigate()
  const isDark = theme === 'dark'
  const [models, setModels] = useState<any[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    (async () => {
      try {
        const m = await modelApi.list()
        setModels((m || []).filter((item: any) => item.status === 1))
      } catch {}
      setLoading(false)
    })()
  }, [])

  const colors = isDark
    ? ['#8B5CF6', '#3B82F6', '#06B6D4', '#10B981', '#F59E0B', '#F43F5E']
    : ['#7C3AED', '#2563EB', '#0891B2', '#059669', '#D97706', '#E11D48']

  if (loading) return (
    <div className="flex items-center justify-center h-full">
      <motion.div animate={{ rotate: 360 }} transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}>
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke={colors[0]} strokeWidth="2"><path d="M12 2a10 10 0 019.95 9" strokeLinecap="round"/></svg>
      </motion.div>
    </div>
  )

  return (
    <div className="p-4 sm:p-6 lg:p-8 max-w-6xl mx-auto overflow-y-auto h-full space-y-6">
      <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }}>
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
              {lang === 'zh' ? '模型商店' : 'Model Store'}
            </h1>
            <p className="text-sm mt-1" style={{ color: 'var(--text-dim)' }}>
              {lang === 'zh' ? '浏览全球顶尖 AI 模型，了解定价并立即体验' : 'Browse top-tier AI models worldwide with pricing'}
            </p>
          </div>
        </div>
      </motion.div>

      {/* 模型列表 */}
      {models.length > 0 && (
        <div className="rounded-2xl border overflow-hidden" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
          {/* 表头 */}
          <div className="grid grid-cols-12 gap-2 px-4 py-3 text-xs font-medium border-b" style={{ borderColor: 'var(--border-color)', color: 'var(--text-dim)' }}>
            <div className="col-span-4">{lang === 'zh' ? '模型' : 'Model'}</div>
            <div className="col-span-2 text-right">{lang === 'zh' ? '输入价格' : 'Input Price'}</div>
            <div className="col-span-2 text-right">{lang === 'zh' ? '输出价格' : 'Output Price'}</div>
            <div className="col-span-2 text-center">{lang === 'zh' ? '厂商' : 'Provider'}</div>
            <div className="col-span-2 text-center">{lang === 'zh' ? '操作' : 'Action'}</div>
          </div>

          {/* 模型行 */}
          {models.map((m, i) => {
            const inChat = CHAT_MODELS.has(m.modelName)
            return (
              <motion.div
                key={m.id}
                initial={{ opacity: 0, y: 5 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: i * 0.03 }}
                className="grid grid-cols-12 gap-2 px-4 py-3 items-center border-b last:border-b-0 hover:bg-white/[0.02] transition-colors"
                style={{ borderColor: 'var(--border-color)' }}
              >
                {/* 模型名称 */}
                <div className="col-span-4 flex items-center gap-3">
                  <div className="w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0" style={{ background: colors[i % colors.length] + '15' }}>
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke={colors[i % colors.length]} strokeWidth="2">
                      <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
                    </svg>
                  </div>
                  <div>
                    <div className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>{m.modelName}</div>
                    <div className="text-[10px]" style={{ color: 'var(--text-dim)' }}>{m.description || ''}</div>
                  </div>
                </div>

                {/* 输入价格 */}
                <div className="col-span-2 text-right">
                  <span className="text-xs font-mono" style={{ color: 'var(--text-secondary)' }}>
                    ¥{Number(m.inputPrice || 0).toFixed(2)}
                  </span>
                  <span className="text-[10px] ml-1" style={{ color: 'var(--text-dim)' }}>/1K tokens</span>
                </div>

                {/* 输出价格 */}
                <div className="col-span-2 text-right">
                  <span className="text-xs font-mono" style={{ color: 'var(--text-secondary)' }}>
                    ¥{Number(m.outputPrice || 0).toFixed(2)}
                  </span>
                  <span className="text-[10px] ml-1" style={{ color: 'var(--text-dim)' }}>/1K tokens</span>
                </div>

                {/* 厂商 */}
                <div className="col-span-2 text-center">
                  <span className="text-xs px-2 py-1 rounded-md" style={{ background: 'var(--bg-surface)', color: 'var(--text-secondary)' }}>
                    {m.provider}
                  </span>
                </div>

                {/* 操作 */}
                <div className="col-span-2 text-center">
                  {inChat ? (
                    <button
                      onClick={() => navigate('/chat')}
                      className="text-xs px-3 py-1.5 rounded-lg font-medium transition-all hover:opacity-90"
                      style={{ background: 'var(--accent-purple)', color: '#fff' }}
                    >
                      {lang === 'zh' ? '去对话 →' : 'Try Chat →'}
                    </button>
                  ) : (
                    <span className="text-[10px]" style={{ color: 'var(--text-dim)' }}>
                      {lang === 'zh' ? '即将上线' : 'Coming Soon'}
                    </span>
                  )}
                </div>
              </motion.div>
            )
          })}
        </div>
      )}

      {/* 空状态 */}
      {models.length === 0 && (
        <div className="rounded-2xl p-10 border text-center" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
          <div className="text-4xl mb-3">📦</div>
          <h3 className="text-base font-semibold mb-2" style={{ color: 'var(--text-primary)' }}>
            {lang === 'zh' ? '暂无可用的模型' : 'No models available'}
          </h3>
          <p className="text-sm" style={{ color: 'var(--text-dim)' }}>
            {lang === 'zh' ? '请管理员在后台配置模型' : 'Please configure models in admin panel'}
          </p>
        </div>
      )}

      {/* 计费说明 */}
      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}
        className="rounded-2xl p-5 border flex flex-col sm:flex-row sm:items-center justify-between gap-3" style={{
          background: isDark ? 'rgba(139,92,246,0.06)' : 'rgba(124,58,237,0.04)',
          borderColor: isDark ? 'rgba(139,92,246,0.12)' : 'rgba(124,58,237,0.08)',
        }}>
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0" style={{ background: 'var(--accent-purple)' + '15' }}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--accent-purple)" strokeWidth="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
          </div>
          <div>
            <div className="text-xs font-semibold" style={{ color: 'var(--text-primary)' }}>
              {lang === 'zh' ? '按 Token 计费' : 'Token-Based Billing'}
            </div>
            <div className="text-[10px]" style={{ color: 'var(--text-dim)' }}>
              {lang === 'zh' ? '根据输入和输出 token 数量分别计费' : 'Separate pricing for input and output tokens'}
            </div>
          </div>
        </div>
      </motion.div>
    </div>
  )
}
