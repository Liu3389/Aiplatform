import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { modelApi } from '@/services/api'
import { motion } from 'framer-motion'
import { useI18nStore } from '@/stores/i18nStore'
import { useThemeStore } from '@/stores/themeStore'

function getColors(isDark: boolean) {
  return isDark
    ? ['#8B5CF6', '#3B82F6', '#06B6D4', '#10B981', '#F59E0B', '#F43F5E']
    : ['#7C3AED', '#2563EB', '#0891B2', '#059669', '#D97706', '#E11D48']
}

export default function ModelStorePage() {
  const { lang } = useI18nStore()
  const { theme } = useThemeStore()
  const navigate = useNavigate()
  const isDark = theme === 'dark'
  const colors = getColors(isDark)
  const [models, setModels] = useState<any[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    (async () => {
      try {
        const m = await modelApi.list()
        setModels(m || [])
      } catch {}
      setLoading(false)
    })()
  }, [])

  const freeModels = models.filter(m => Number(m.inputPrice) === 0 && Number(m.outputPrice) === 0)
  const paidModels = models.filter(m => Number(m.inputPrice) > 0 || Number(m.outputPrice) > 0)

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
              {lang === 'zh' ? '模型大厅' : 'Model Store'}
            </h1>
            <p className="text-sm mt-1" style={{ color: 'var(--text-dim)' }}>
              {lang === 'zh' ? '浏览所有可用 AI 模型及其定价信息' : 'Browse all available AI models and pricing'}
            </p>
          </div>
          <button onClick={() => navigate('/billing')} className="px-4 py-2 rounded-xl text-xs font-medium transition-all"
            style={{ background: 'var(--accent-purple)', color: '#fff' }}>
            {lang === 'zh' ? '查看计费策略 →' : 'Billing Policy →'}
          </button>
        </div>
      </motion.div>

      {/* Free Models Section */}
      {freeModels.length > 0 && (
        <div>
          <div className="flex items-center gap-2 mb-4">
            <span className="w-2 h-2 rounded-full" style={{ background: 'var(--accent-emerald)' }} />
            <h2 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
              {lang === 'zh' ? `免费模型 (${freeModels.length})` : `Free Models (${freeModels.length})`}
            </h2>
            <span className="text-[10px] px-1.5 py-0.5 rounded-md" style={{ background: 'var(--accent-emerald)' + '15', color: 'var(--accent-emerald)' }}>
              {lang === 'zh' ? '按次数计费' : 'Per-Call'}
            </span>
          </div>
          <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {freeModels.map((m, i) => (
              <motion.div key={m.id} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.05 }}
                className="rounded-2xl p-5 border transition-all duration-300 card-lift" style={{
                  background: 'var(--bg-card)',
                  borderColor: 'var(--border-color)',
                }}>
                <div className="flex items-start justify-between mb-3">
                  <div className="w-10 h-10 rounded-xl flex items-center justify-center" style={{ background: colors[i % colors.length] + '15' }}>
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={colors[i % colors.length]} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
                  </div>
                  <span className="text-xs font-bold px-2 py-1 rounded-lg" style={{ background: 'var(--accent-emerald)' + '15', color: 'var(--accent-emerald)' }}>
                    {lang === 'zh' ? '免费' : 'FREE'}
                  </span>
                </div>
                <h4 className="text-sm font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>{m.modelName}</h4>
                <p className="text-[10px] mb-3" style={{ color: 'var(--text-dim)' }}>{m.provider}</p>
                <div className="flex items-center gap-4 text-[10px]" style={{ color: 'var(--text-dim)' }}>
                  <span>{lang === 'zh' ? '输入：免费' : 'Input: Free'}</span>
                  <span>{lang === 'zh' ? '输出：免费' : 'Output: Free'}</span>
                </div>
                <div className="mt-3 pt-3 border-t flex items-center justify-between" style={{ borderColor: 'var(--border-color)' }}>
                  <span className="text-[10px] flex items-center gap-1" style={{ color: 'var(--text-dim)' }}>
                    <span className="w-1.5 h-1.5 rounded-full" style={{ background: m.status === 1 ? 'var(--accent-emerald)' : 'var(--accent-rose)' }} />
                    {m.status === 1 ? (lang === 'zh' ? '可用' : 'Active') : (lang === 'zh' ? '停用' : 'Inactive')}
                  </span>
                  <span className="text-[10px]" style={{ color: 'var(--text-dim)' }}>
                    {lang === 'zh' ? '每日30次/50K Token' : '30 calls/50K tokens daily'}
                  </span>
                </div>
              </motion.div>
            ))}
          </div>
        </div>
      )}

      {/* Paid Models Section */}
      {paidModels.length > 0 && (
        <div>
          <div className="flex items-center gap-2 mb-4">
            <span className="w-2 h-2 rounded-full" style={{ background: 'var(--accent-amber)' }} />
            <h2 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
              {lang === 'zh' ? `付费模型 (${paidModels.length})` : `Paid Models (${paidModels.length})`}
            </h2>
            <span className="text-[10px] px-1.5 py-0.5 rounded-md" style={{ background: 'var(--accent-amber)' + '15', color: 'var(--accent-amber)' }}>
              {lang === 'zh' ? '按Token计费' : 'Per-Token'}
            </span>
          </div>
          <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {paidModels.map((m, i) => (
              <motion.div key={m.id} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.05 }}
                className="rounded-2xl p-5 border transition-all duration-300 card-lift" style={{
                  background: 'var(--bg-card)',
                  borderColor: 'var(--border-color)',
                }}>
                <div className="flex items-start justify-between mb-3">
                  <div className="w-10 h-10 rounded-xl flex items-center justify-center" style={{ background: colors[(freeModels.length + i) % colors.length] + '15' }}>
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={colors[(freeModels.length + i) % colors.length]} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
                  </div>
                  <span className="text-xs font-bold px-2 py-1 rounded-lg" style={{ background: 'var(--accent-amber)' + '15', color: 'var(--accent-amber)' }}>
                    {lang === 'zh' ? '按量付费' : 'PAYG'}
                  </span>
                </div>
                <h4 className="text-sm font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>{m.modelName}</h4>
                <p className="text-[10px] mb-3" style={{ color: 'var(--text-dim)' }}>{m.provider}</p>
                <div className="flex flex-col gap-1 text-[10px] mb-3" style={{ color: 'var(--text-dim)' }}>
                  <div className="flex items-center justify-between">
                    <span>{lang === 'zh' ? '输入/1K Token' : 'Input/1K tokens'}</span>
                    <span className="font-mono" style={{ color: 'var(--text-secondary)' }}>${Number(m.inputPrice).toFixed(6)}</span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span>{lang === 'zh' ? '输出/1K Token' : 'Output/1K tokens'}</span>
                    <span className="font-mono" style={{ color: 'var(--text-secondary)' }}>${Number(m.outputPrice).toFixed(6)}</span>
                  </div>
                  <div className="flex items-center justify-between pt-1 border-t" style={{ borderColor: 'var(--border-color)' }}>
                    <span style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '平均/1K调用' : 'Avg/1K call'}</span>
                    <span className="font-mono font-semibold" style={{ color: 'var(--accent-amber)' }}>
                      ${((Number(m.inputPrice) + Number(m.outputPrice)) / 2).toFixed(6)}
                    </span>
                  </div>
                </div>
                <div className="mt-2 pt-3 border-t flex items-center justify-between" style={{ borderColor: 'var(--border-color)' }}>
                  <span className="text-[10px] flex items-center gap-1" style={{ color: 'var(--text-dim)' }}>
                    <span className="w-1.5 h-1.5 rounded-full" style={{ background: m.status === 1 ? 'var(--accent-emerald)' : 'var(--accent-rose)' }} />
                    {m.status === 1 ? (lang === 'zh' ? '可用' : 'Active') : (lang === 'zh' ? '停用' : 'Inactive')}
                  </span>
                  <span className="text-[10px]" style={{ color: 'var(--text-dim)' }}>
                    {lang === 'zh' ? '预扣结算' : 'Pre-deduct'}
                  </span>
                </div>
              </motion.div>
            ))}
          </div>
        </div>
      )}

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

      {/* Billing Info Banner */}
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
              {lang === 'zh' ? '查看完整计费策略' : 'View Full Billing Policy'}
            </div>
            <div className="text-[10px]" style={{ color: 'var(--text-dim)' }}>
              {lang === 'zh' ? '了解按次数和按Token两种计费模式的详细规则' : 'Learn about per-call and per-token billing modes'}
            </div>
          </div>
        </div>
        <button onClick={() => navigate('/billing')} className="px-4 py-2 rounded-xl text-xs font-medium whitespace-nowrap transition-colors"
          style={{ background: 'var(--accent-purple)' + '15', color: 'var(--accent-purple)', border: '1px solid var(--accent-purple)' + '30' }}>
          {lang === 'zh' ? '计费策略 →' : 'Billing Policy →'}
        </button>
      </motion.div>
    </div>
  )
}
