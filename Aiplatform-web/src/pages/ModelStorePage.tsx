import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { modelApi } from '@/services/api'
import { motion } from 'framer-motion'
import { useI18nStore } from '@/stores/i18nStore'
import { useThemeStore } from '@/stores/themeStore'
import { ProviderIcon, providerDisplayName } from '@/components/ProviderIcon'
import PointsIcon from '@/components/PointsIcon'

interface StoreModel {
  id: number
  displayName: string
  modelCode: string
  fixedPoints: number
  provider: string
  description: string | null
  supportsVision: number
  sortOrder: number
  accessLevel: number
  chatEnabled: boolean
}

interface AvailableModel {
  modelId: number
  displayName: string
  modelCode: string
  provider: string
  fixedPoints: number
  chatEnabled: boolean
  accessible: boolean
  requiredLevel: number
  requiredLevelName: string | null
  upgradeHint: string | null
}

const LEVEL_NAMES_ZH = ['全部用户', '白银会员', '黄金会员', '钻石会员']

export default function ModelStorePage() {
  const { lang } = useI18nStore()
  const { theme } = useThemeStore()
  const navigate = useNavigate()
  const isDark = theme === 'dark'
  const [models, setModels] = useState<StoreModel[]>([])
  const [available, setAvailable] = useState<Map<string, AvailableModel>>(new Map())
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  useEffect(() => {
    (async () => {
      try {
        const m = await modelApi.list()
        setModels(m || [])
        // 可用性查询（登录态），失败不阻塞展示
        try {
          const av = await modelApi.getAvailable()
          const map = new Map<string, AvailableModel>()
          for (const a of av || []) map.set(a.displayName, a)
          setAvailable(map)
        } catch {}
      } catch {
        setError(true)
      }
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
    <div className="page-scroll h-full max-w-6xl mx-auto space-y-5 overflow-y-auto p-3 sm:space-y-6 sm:p-6 lg:p-8">
      <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }}>
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
              {lang === 'zh' ? '模型商店' : 'Model Store'}
            </h1>
            <p className="text-sm mt-1" style={{ color: 'var(--text-dim)' }}>
              {lang === 'zh' ? '汇聚全球前沿 AI 模型，按次点数计费，即开即用' : 'Frontier AI models worldwide, points-based billing'}
            </p>
          </div>
        </div>
      </motion.div>

      {/* 加载失败 */}
      {error && (
        <div className="rounded-2xl p-10 border text-center" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
          <svg className="mx-auto mb-3" width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="var(--accent-rose)" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><path d="M12 3 2.8 19a1.5 1.5 0 0 0 1.3 2.2h15.8a1.5 1.5 0 0 0 1.3-2.2L12 3Z" /><path d="M12 9v4M12 17h.01" /></svg>
          <h3 className="text-base font-semibold mb-2" style={{ color: 'var(--text-primary)' }}>
            {lang === 'zh' ? '模型列表加载失败' : 'Failed to load models'}
          </h3>
          <button onClick={() => window.location.reload()} className="text-xs px-4 py-2 rounded-lg" style={{ background: 'var(--accent-purple)', color: '#fff' }}>
            {lang === 'zh' ? '重试' : 'Retry'}
          </button>
        </div>
      )}

      {/* 模型列表 */}
      {!error && models.length > 0 && (
        <div className="rounded-2xl border overflow-hidden" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
          {/* 表头 */}
          <div className="grid grid-cols-12 gap-2 px-4 py-3 text-xs font-medium border-b" style={{ borderColor: 'var(--border-color)', color: 'var(--text-dim)' }}>
            <div className="col-span-4">{lang === 'zh' ? '模型' : 'Model'}</div>
            <div className="col-span-2 text-right">{lang === 'zh' ? '消耗点数' : 'Points / Call'}</div>
            <div className="col-span-2 text-center">{lang === 'zh' ? '厂商' : 'Provider'}</div>
            <div className="col-span-2 text-center">{lang === 'zh' ? '所需等级' : 'Access'}</div>
            <div className="col-span-2 text-center">{lang === 'zh' ? '操作' : 'Action'}</div>
          </div>

          {/* 模型行 */}
          {models.map((m, i) => {
            const avail = available.get(m.displayName)
            const accessible = avail ? avail.accessible : m.accessLevel === 0
            const canChat = m.chatEnabled && (avail?.chatEnabled ?? true)
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
                  <ProviderIcon provider={m.provider} size={36} />
                  <div>
                    <div className="text-sm font-medium flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
                      {m.displayName}
                      {m.supportsVision === 1 && (
                        <span className="text-[9px] px-1.5 py-0.5 rounded" style={{ background: colors[3] + '18', color: colors[3] }}>
                          {lang === 'zh' ? '视觉' : 'Vision'}
                        </span>
                      )}
                    </div>
                    <div className="text-[10px] line-clamp-1" style={{ color: 'var(--text-dim)' }}>{m.description || ''}</div>
                  </div>
                </div>

                {/* 消耗点数 */}
                <div className="col-span-2 text-right">
                  <span className="text-xs font-mono inline-flex items-center gap-1" style={{ color: 'var(--text-secondary)' }}>
                    <PointsIcon size={12} color={colors[0]} />
                    {Number(m.fixedPoints || 0)}
                  </span>
                  <span className="text-[10px] ml-1" style={{ color: 'var(--text-dim)' }}>
                    /{lang === 'zh' ? '次' : 'call'}
                  </span>
                </div>

                {/* 厂商 */}
                <div className="col-span-2 text-center">
                  <span className="text-xs px-2 py-1 rounded-md" style={{ background: 'var(--bg-surface)', color: 'var(--text-secondary)' }}>
                    {providerDisplayName(m.provider)}
                  </span>
                </div>

                {/* 所需等级 */}
                <div className="col-span-2 text-center">
                  <span className="text-xs px-2 py-1 rounded-md" style={{
                    background: m.accessLevel === 0 ? colors[3] + '15' : colors[4] + '15',
                    color: m.accessLevel === 0 ? colors[3] : colors[4],
                  }}>
                    {lang === 'zh' ? LEVEL_NAMES_ZH[m.accessLevel] || '全部用户' : ['Free', 'Silver', 'Gold', 'Diamond'][m.accessLevel] || 'Free'}
                  </span>
                </div>

                {/* 操作 */}
                <div className="col-span-2 text-center">
                  {canChat && accessible ? (
                    <button
                      onClick={() => navigate('/chat')}
                      className="text-xs px-3 py-1.5 rounded-lg font-medium transition-all hover:opacity-90"
                      style={{ background: 'var(--accent-purple)', color: '#fff' }}
                    >
                      {lang === 'zh' ? '去对话 →' : 'Try Chat →'}
                    </button>
                  ) : !canChat ? (
                    <span className="text-[10px] px-2 py-1 rounded-md" style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)' }}>
                      {lang === 'zh' ? '暂不可用' : 'Unavailable'}
                    </span>
                  ) : (
                    <button
                      onClick={() => navigate('/recharge')}
                      className="text-[10px] px-2 py-1 rounded-md font-medium transition-all hover:opacity-90"
                      style={{ background: colors[4] + '15', color: colors[4] }}
                    >
                      {(avail?.upgradeHint) || (lang === 'zh' ? '升级解锁' : 'Upgrade')}
                    </button>
                  )}
                </div>
              </motion.div>
            )
          })}
        </div>
      )}

      {/* 空状态 */}
      {!error && models.length === 0 && (
        <div className="rounded-2xl p-10 border text-center" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
          <svg className="mx-auto mb-3" width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="var(--text-dim)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><path d="m4 7 8-4 8 4-8 4-8-4Z" /><path d="M4 12l8 4 8-4M4 17l8 4 8-4M12 11v9" /></svg>
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
            <PointsIcon size={16} color="var(--accent-purple)" />
          </div>
          <div>
            <div className="text-xs font-semibold" style={{ color: 'var(--text-primary)' }}>
              {lang === 'zh' ? '按次点数计费' : 'Points-Based Billing'}
            </div>
            <div className="text-[10px]" style={{ color: 'var(--text-dim)' }}>
              {lang === 'zh' ? '每次对话按模型固定点数实时扣减，点数消耗可在个人中心查询' : 'Real-time points deduction per call, bills available in user center'}
            </div>
          </div>
        </div>
      </motion.div>
    </div>
  )
}
