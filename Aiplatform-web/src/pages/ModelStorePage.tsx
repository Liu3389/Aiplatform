import { useState, useEffect, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { modelApi } from '@/services/api'
import { motion } from 'framer-motion'
import { useI18nStore } from '@/stores/i18nStore'
import { useThemeStore } from '@/stores/themeStore'
import PointsIcon from '@/components/PointsIcon'

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

  const getPointsTier = (inputPrice: number): { label: string; labelEn: string; points: number; color: string; bg: string } => {
    if (inputPrice === 0) return { label: '免费', labelEn: 'FREE', points: 0, color: 'var(--accent-emerald)', bg: 'var(--accent-emerald)' + '15' }
    if (inputPrice <= 0.001) return { label: '1 点/次', labelEn: '1 pt/call', points: 1, color: 'var(--accent-blue)', bg: 'var(--accent-blue)' + '15' }
    return { label: '3 点/次', labelEn: '3 pts/call', points: 3, color: 'var(--accent-purple)', bg: 'var(--accent-purple)' + '15' }
  }

  const tiers = useMemo(() => {
    const free: any[] = []
    const medium: any[] = []
    const premium: any[] = []
    models.forEach(m => {
      const ip = Number(m.inputPrice || 0)
      const op = Number(m.outputPrice || 0)
      if (ip === 0 && op === 0) free.push(m)
      else if (ip <= 0.001) medium.push(m)
      else premium.push(m)
    })
    return { free, medium, premium }
  }, [models])

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

      {/* Free Models - 免费对话 */}
      {tiers.free.length > 0 && (
        <div>
          <div className="flex items-center gap-2 mb-4">
            <span className="w-2 h-2 rounded-full" style={{ background: 'var(--accent-emerald)' }} />
            <h2 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
              {lang === 'zh' ? `免费模型 (${tiers.free.length})` : `Free Models (${tiers.free.length})`}
            </h2>
            <span className="text-[10px] px-1.5 py-0.5 rounded-md flex items-center gap-1" style={{ background: 'var(--accent-emerald)' + '15', color: 'var(--accent-emerald)' }}>
              <PointsIcon size={10} color="var(--accent-emerald)" /> {lang === 'zh' ? '免费对话' : 'Free'}
            </span>
          </div>
          <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {tiers.free.map((m, i) => (
              <motion.div key={m.id} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.05 }}
                className="rounded-2xl p-5 border transition-all duration-300 card-lift" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                <div className="flex items-start justify-between mb-3">
                  <div className="w-10 h-10 rounded-xl flex items-center justify-center" style={{ background: colors[i % colors.length] + '15' }}>
                    <PointsIcon size={18} color={colors[i % colors.length]} />
                  </div>
                  <span className="text-xs font-bold px-2 py-1 rounded-lg flex items-center gap-1" style={{ background: 'var(--accent-emerald)' + '15', color: 'var(--accent-emerald)' }}>
                    <PointsIcon size={10} color="var(--accent-emerald)" /> {lang === 'zh' ? '免费' : 'FREE'}
                  </span>
                </div>
                <h4 className="text-sm font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>{m.modelName}</h4>
                <p className="text-[10px] mb-3" style={{ color: 'var(--text-dim)' }}>{m.provider}</p>
                <div className="flex items-center gap-4 text-[10px]" style={{ color: 'var(--text-dim)' }}>
                  <span className="flex items-center gap-1"><PointsIcon size={10} /> {lang === 'zh' ? '0 点数/次' : '0 pts/call'}</span>
                </div>
                <div className="mt-3 pt-3 border-t flex items-center justify-between" style={{ borderColor: 'var(--border-color)' }}>
                  <span className="text-[10px] flex items-center gap-1" style={{ color: 'var(--text-dim)' }}>
                    <span className="w-1.5 h-1.5 rounded-full" style={{ background: m.status === 1 ? 'var(--accent-emerald)' : 'var(--accent-rose)' }} />
                    {m.status === 1 ? (lang === 'zh' ? '可用' : 'Active') : (lang === 'zh' ? '停用' : 'Inactive')}
                  </span>
                  <span className="text-[10px]" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '每日30次/50K Token' : '30 calls/50K tokens daily'}</span>
                </div>
              </motion.div>
            ))}
          </div>
        </div>
      )}

      {/* Medium Models - 1点/次 */}
      {tiers.medium.length > 0 && (
        <div>
          <div className="flex items-center gap-2 mb-4">
            <span className="w-2 h-2 rounded-full" style={{ background: 'var(--accent-blue)' }} />
            <h2 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
              {lang === 'zh' ? `中端模型 (${tiers.medium.length})` : `Medium Models (${tiers.medium.length})`}
            </h2>
            <span className="text-[10px] px-1.5 py-0.5 rounded-md flex items-center gap-1" style={{ background: 'var(--accent-blue)' + '15', color: 'var(--accent-blue)' }}>
              <PointsIcon size={10} color="var(--accent-blue)" /> 1 {lang === 'zh' ? '点/次' : 'pt/call'}
            </span>
          </div>
          <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {tiers.medium.map((m, i) => (
              <motion.div key={m.id} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.05 }}
                className="rounded-2xl p-5 border transition-all duration-300 card-lift" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                <div className="flex items-start justify-between mb-3">
                  <div className="w-10 h-10 rounded-xl flex items-center justify-center" style={{ background: 'var(--accent-blue)' + '15' }}>
                    <PointsIcon size={18} color="var(--accent-blue)" />
                  </div>
                  <span className="text-xs font-bold px-2 py-1 rounded-lg flex items-center gap-1" style={{ background: 'var(--accent-blue)' + '15', color: 'var(--accent-blue)' }}>
                    <PointsIcon size={10} color="var(--accent-blue)" /> 1 {lang === 'zh' ? '点' : 'pt'}
                  </span>
                </div>
                <h4 className="text-sm font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>{m.modelName}</h4>
                <p className="text-[10px] mb-3" style={{ color: 'var(--text-dim)' }}>{m.provider}</p>
                <div className="flex flex-col gap-1 text-[10px] mb-3" style={{ color: 'var(--text-dim)' }}>
                  <div className="flex items-center justify-between">
                    <span>{lang === 'zh' ? '输入/1K Token' : 'Input/1K tokens'}</span>
                    <span className="font-mono" style={{ color: 'var(--text-secondary)' }}>¥{Number(m.inputPrice).toFixed(2)}</span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span>{lang === 'zh' ? '输出/1K Token' : 'Output/1K tokens'}</span>
                    <span className="font-mono" style={{ color: 'var(--text-secondary)' }}>¥{Number(m.outputPrice).toFixed(2)}</span>
                  </div>
                  <div className="flex items-center justify-between pt-1 border-t" style={{ borderColor: 'var(--border-color)' }}>
                    <span className="flex items-center gap-1" style={{ color: 'var(--text-dim)' }}>
                      <PointsIcon size={10} color="var(--accent-blue)" /> {lang === 'zh' ? '点数' : 'Points'}
                    </span>
                    <span className="font-mono font-semibold flex items-center gap-1" style={{ color: 'var(--accent-blue)' }}>
                      <PointsIcon size={10} color="var(--accent-blue)" /> 1
                    </span>
                  </div>
                </div>
                <div className="mt-2 pt-3 border-t flex items-center justify-between" style={{ borderColor: 'var(--border-color)' }}>
                  <span className="text-[10px] flex items-center gap-1" style={{ color: 'var(--text-dim)' }}>
                    <span className="w-1.5 h-1.5 rounded-full" style={{ background: m.status === 1 ? 'var(--accent-emerald)' : 'var(--accent-rose)' }} />
                    {m.status === 1 ? (lang === 'zh' ? '可用' : 'Active') : (lang === 'zh' ? '停用' : 'Inactive')}
                  </span>
                </div>
              </motion.div>
            ))}
          </div>
        </div>
      )}

      {/* Premium Models - 3点/次 */}
      {tiers.premium.length > 0 && (
        <div>
          <div className="flex items-center gap-2 mb-4">
            <span className="w-2 h-2 rounded-full" style={{ background: 'var(--accent-purple)' }} />
            <h2 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
              {lang === 'zh' ? `高端模型 (${tiers.premium.length})` : `Premium Models (${tiers.premium.length})`}
            </h2>
            <span className="text-[10px] px-1.5 py-0.5 rounded-md flex items-center gap-1" style={{ background: 'var(--accent-purple)' + '15', color: 'var(--accent-purple)' }}>
              <PointsIcon size={10} color="var(--accent-purple)" /> 3 {lang === 'zh' ? '点/次' : 'pts/call'}
            </span>
          </div>
          <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {tiers.premium.map((m, i) => {
              const tier = getPointsTier(Number(m.inputPrice))
              return (
              <motion.div key={m.id} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.05 }}
                className="rounded-2xl p-5 border transition-all duration-300 card-lift" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                <div className="flex items-start justify-between mb-3">
                  <div className="w-10 h-10 rounded-xl flex items-center justify-center" style={{ background: tier.bg }}>
                    <PointsIcon size={18} color={tier.color} />
                  </div>
                  <span className="text-xs font-bold px-2 py-1 rounded-lg flex items-center gap-1" style={{ background: tier.bg, color: tier.color }}>
                    <PointsIcon size={10} color={tier.color} /> {tier.points} {lang === 'zh' ? '点' : 'pts'}
                  </span>
                </div>
                <h4 className="text-sm font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>{m.modelName}</h4>
                <p className="text-[10px] mb-3" style={{ color: 'var(--text-dim)' }}>{m.provider}</p>
                <div className="flex flex-col gap-1 text-[10px] mb-3" style={{ color: 'var(--text-dim)' }}>
                  <div className="flex items-center justify-between">
                    <span>{lang === 'zh' ? '输入/1K Token' : 'Input/1K tokens'}</span>
                    <span className="font-mono" style={{ color: 'var(--text-secondary)' }}>¥{Number(m.inputPrice).toFixed(2)}</span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span>{lang === 'zh' ? '输出/1K Token' : 'Output/1K tokens'}</span>
                    <span className="font-mono" style={{ color: 'var(--text-secondary)' }}>¥{Number(m.outputPrice).toFixed(2)}</span>
                  </div>
                  <div className="flex items-center justify-between pt-1 border-t" style={{ borderColor: 'var(--border-color)' }}>
                    <span className="flex items-center gap-1" style={{ color: 'var(--text-dim)' }}>
                      <PointsIcon size={10} color="var(--accent-purple)" /> {lang === 'zh' ? '点数' : 'Points'}
                    </span>
                    <span className="font-mono font-semibold flex items-center gap-1" style={{ color: 'var(--accent-purple)' }}>
                      <PointsIcon size={10} color="var(--accent-purple)" /> {tier.points}
                    </span>
                  </div>
                </div>
                <div className="mt-2 pt-3 border-t flex items-center justify-between" style={{ borderColor: 'var(--border-color)' }}>
                  <span className="text-[10px] flex items-center gap-1" style={{ color: 'var(--text-dim)' }}>
                    <span className="w-1.5 h-1.5 rounded-full" style={{ background: m.status === 1 ? 'var(--accent-emerald)' : 'var(--accent-rose)' }} />
                    {m.status === 1 ? (lang === 'zh' ? '可用' : 'Active') : (lang === 'zh' ? '停用' : 'Inactive')}
                  </span>
                </div>
              </motion.div>
            )})}
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
