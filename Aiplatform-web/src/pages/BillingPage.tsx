import { useState, useEffect } from 'react'
import { useAppStore } from '@/stores/appStore'
import { modelApi, adminApi, userApi } from '@/services/api'
import { motion } from 'framer-motion'
import * as echarts from 'echarts'
import { useThemeStore } from '@/stores/themeStore'
import { useI18nStore } from '@/stores/i18nStore'

type ECharts = echarts.ECharts

function getColors(isDark: boolean) {
  return isDark
    ? ['#8B5CF6', '#3B82F6', '#06B6D4', '#10B981', '#F59E0B', '#F43F5E']
    : ['#7C3AED', '#2563EB', '#0891B2', '#059669', '#D97706', '#E11D48']
}

const BILLING_RULES = {
  freeModelCallLimit: 30,
  freeModelTokenLimit: 50000,
  freeModelDailyReset: '00:00 UTC+8',
  perCallBaseCost: 0.001,
  perTokenInputCost: 0.0000,
  perTokenOutputCost: 0.0000,
  minBalanceForCall: 0.0,
  minTopUp: 1.0,
  settlementMode: 'pre_deduct',
  concurrencySafety: 'redis_lua_atomic',
}

const FAQ_ITEMS = [
  { q: '免费额度如何计算？', a: '每个用户每天享有 30 次免费调用次数和 50,000 免费 Token，每日零点重置。调用 DeepSeek 系列模型不消耗余额。' },
  { q: '余额如何扣费？', a: '付费模式下，系统采用"预扣余额 + 实际结算"两阶段机制。请求前按最大预估费用预扣，完成后按实际用量多退少补。' },
  { q: '如何保证计费不丢钱？', a: '所有余额操作均使用 Redis Lua 脚本，在单线程中原子执行，杜绝并发超扣。计费记录与调用日志一一关联，可追溯。' },
  { q: '欠费了怎么办？', a: '余额不足时请求会被即时拦截（402），不会产生欠费。充值后即可恢复使用，最小充值金额为 ¥1.00。' },
  { q: '自定义 API Key 怎么收费？', a: '使用自定义第三方 API Key（如你自己的 DeepSeek/OpenAI Key）时，平台不收取任何费用，完全免费。' },
  { q: 'Token 是怎么计算的？', a: '输入 Token 基于消息总字数 / 4 估算，输出 Token 基于 AI 回复字数 / 4 估算，实际以模型厂商返回为准。' },
]

export default function BillingPage() {
  const { user } = useAppStore()
  const { theme } = useThemeStore()
  const { lang } = useI18nStore()
  const isDark = theme === 'dark'
  const colors = getColors(isDark)
  const isAdmin = user?.role === 'SUPER_ADMIN' || user?.role === 'ADMIN'

  const [models, setModels] = useState<any[]>([])
  const [userStats, setUserStats] = useState<any>(null)
  const [platformStats, setPlatformStats] = useState<any>(null)
  const [balance, setBalance] = useState(0)
  const [freeQuota, setFreeQuota] = useState<any>(null)
  const [activeTab, setActiveTab] = useState<'overview' | 'detail' | 'faq'>('overview')

  useEffect(() => {
    (async () => {
      try { const m = await modelApi.list(); setModels(m || []) } catch {}
      try { const b = await userApi.getBalance(); setBalance(Number(b)||0) } catch {}
      try { const q = await userApi.getFreeQuota(); setFreeQuota(q) } catch {}
      if (isAdmin) {
        try {
          const s = await adminApi.getStats().catch(() => null)
          setPlatformStats(s)
        } catch {}
      }
    })()
  }, [])

  useEffect(() => {
    const el = document.getElementById('billing-pie')
    if (!el || models.length === 0) return
    const chart = echarts.init(el, isDark ? 'dark' : 'light')
    chart.setOption({
      backgroundColor: 'transparent',
      tooltip: { trigger: 'item', backgroundColor: isDark ? 'rgba(17,24,39,0.95)' : 'rgba(255,255,255,0.95)' },
      series: [{
        type: 'pie', radius: ['55%', '82%'], center: ['50%', '50%'],
        itemStyle: { borderRadius: 6, borderColor: isDark ? '#0B1020' : '#F8FAFC', borderWidth: 2 },
        label: { show: true, fontSize: 10, color: isDark ? '#94A3B8' : '#64748B' },
        emphasis: { label: { fontSize: 14, fontWeight: 'bold' } },
        data: models.filter(m => m.status === 1).map((m, i) => ({
          name: m.modelName,
          value: Number(m.inputPrice) + Number(m.outputPrice) || 0.001,
          itemStyle: { color: colors[i % colors.length] }
        })),
      }]
    })
    return () => { chart.dispose() }
  }, [models, isDark])

  const freeModels = models.filter(m => Number(m.inputPrice) === 0)
  const paidModels = models.filter(m => Number(m.inputPrice) > 0)

  return (
    <div className="p-4 sm:p-6 lg:p-8 max-w-6xl mx-auto overflow-y-auto h-full space-y-6">
      <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }}>
        <h1 className="text-xl sm:text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
          {lang === 'zh' ? '计费策略' : 'Billing Policy'}
        </h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-dim)' }}>
          {lang === 'zh' ? '透明、安全、可追溯的平台计费体系' : 'Transparent, secure, and traceable billing system'}
        </p>
      </motion.div>

      {/* Tab Selector */}
      <div className="flex gap-2 flex-wrap">
        {(['overview', 'detail', 'faq'] as const).map(t => (
          <button key={t} onClick={() => setActiveTab(t)} className="px-4 py-2 rounded-xl text-sm font-medium transition-all"
            style={{
              background: activeTab === t ? 'var(--accent-purple)' : 'var(--bg-surface)',
              color: activeTab === t ? '#fff' : 'var(--text-dim)',
              border: `1px solid ${activeTab === t ? 'var(--accent-purple)' : 'var(--border-color)'}`,
            }}>
            {t === 'overview' ? (lang === 'zh' ? '策略总览' : 'Overview') :
             t === 'detail' ? (lang === 'zh' ? '价格明细' : 'Pricing Details') :
             (lang === 'zh' ? '常见问题' : 'FAQ')}
          </button>
        ))}
      </div>

      {activeTab === 'overview' && (
        <div className="space-y-6">
          {/* Strategy Hero */}
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}
            className="rounded-3xl p-6 sm:p-8 border overflow-hidden relative"
            style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
            <div className="absolute top-0 right-0 w-48 h-48 rounded-full blur-3xl opacity-10" style={{ background: colors[0] }} />
            <div className="relative z-10">
              <div className="flex items-center gap-2 mb-3">
                <span className="px-2 py-0.5 text-[10px] font-medium rounded-full" style={{ background: isDark ? 'rgba(139,92,246,0.15)' : 'rgba(124,58,237,0.1)', color: 'var(--accent-purple)' }}>
                  {lang === 'zh' ? '双重计费模式' : 'Dual Billing Mode'}
                </span>
                <span className="px-2 py-0.5 text-[10px] font-medium rounded-full" style={{ background: isDark ? 'rgba(16,185,129,0.15)' : 'rgba(5,150,105,0.1)', color: 'var(--accent-emerald)' }}>
                  Lua Atomic
                </span>
              </div>
              <h2 className="text-lg font-bold mb-2" style={{ color: 'var(--text-primary)' }}>
                {lang === 'zh' ? '按次数 + 按 Token 双轨计费' : 'Per-Call + Per-Token Dual Billing'}
              </h2>
              <p className="text-sm leading-relaxed max-w-3xl" style={{ color: 'var(--text-dim)' }}>
                {lang === 'zh'
                  ? '平台提供两种计费维度：免费用户按日配额（次数 + Token 双限）使用模型；付费用户按 Token 精确计费。所有计费操作由 Redis Lua 脚本原子执行，确保高并发下的资金安全。'
                  : 'Free users enjoy daily quotas (call + token limits). Paid users pay per token with atomic Redis Lua billing ensuring fund safety under high concurrency.'}
              </p>
            </div>
          </motion.div>

          {/* Dual Mode Cards */}
          <div className="grid md:grid-cols-2 gap-4">
            <motion.div initial={{ opacity: 0, x: -10 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.1 }}
              className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
              <div className="flex items-center gap-2 mb-3">
                <div className="w-8 h-8 rounded-lg flex items-center justify-center" style={{ background: colors[3] + '20' }}>
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke={colors[3]} strokeWidth="2"><path d="M12 2l9 4.5v7L12 18l-9-4.5v-7L12 2z"/></svg>
                </div>
                <span className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                  {lang === 'zh' ? '模式一：按次数计费' : 'Mode 1: Per-Call'}
                </span>
                <span className="text-[10px] px-1.5 py-0.5 rounded-md" style={{ background: 'var(--accent-emerald)' + '15', color: 'var(--accent-emerald)' }}>免费</span>
              </div>
              <ul className="space-y-2 text-xs" style={{ color: 'var(--text-dim)' }}>
                <li className="flex items-start gap-2">
                  <span style={{ color: 'var(--accent-emerald)' }}>✓</span>
                  <span>{lang === 'zh' ? `每日免费 ${BILLING_RULES.freeModelCallLimit} 次调用` : `${BILLING_RULES.freeModelCallLimit} free calls/day`}</span>
                </li>
                <li className="flex items-start gap-2">
                  <span style={{ color: 'var(--accent-emerald)' }}>✓</span>
                  <span>{lang === 'zh' ? `每日免费 ${(BILLING_RULES.freeModelTokenLimit/1000).toFixed(0)}K Token` : `${(BILLING_RULES.freeModelTokenLimit/1000)}K tokens/day`}</span>
                </li>
                <li className="flex items-start gap-2">
                  <span style={{ color: 'var(--accent-emerald)' }}>✓</span>
                  <span>{lang === 'zh' ? `每日 ${BILLING_RULES.freeModelDailyReset} 重置` : `Resets at ${BILLING_RULES.freeModelDailyReset}`}</span>
                </li>
                <li className="flex items-start gap-2">
                  <span style={{ color: 'var(--accent-emerald)' }}>✓</span>
                  <span>{lang === 'zh' ? '使用平台内置 API Key，无需额外配置' : 'Uses built-in platform API keys'}</span>
                </li>
                <li className="flex items-start gap-2">
                  <span style={{ color: 'var(--accent-emerald)' }}>✓</span>
                  <span>{lang === 'zh' ? '适用于：DeepSeek V3 Chat、DeepSeek R1 等平台模型' : 'For: DeepSeek V3 Chat, R1, etc.'}</span>
                </li>
              </ul>
            </motion.div>

            <motion.div initial={{ opacity: 0, x: 10 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.15 }}
              className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
              <div className="flex items-center gap-2 mb-3">
                <div className="w-8 h-8 rounded-lg flex items-center justify-center" style={{ background: colors[4] + '20' }}>
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke={colors[4]} strokeWidth="2"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>
                </div>
                <span className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                  {lang === 'zh' ? '模式二：按 Token 计费' : 'Mode 2: Per-Token'}
                </span>
                <span className="text-[10px] px-1.5 py-0.5 rounded-md" style={{ background: 'var(--accent-amber)' + '15', color: 'var(--accent-amber)' }}>付费</span>
              </div>
              <ul className="space-y-2 text-xs" style={{ color: 'var(--text-dim)' }}>
                <li className="flex items-start gap-2">
                  <span style={{ color: 'var(--accent-amber)' }}>◆</span>
                  <span>{lang === 'zh' ? '按输入/输出 Token 分别计价，不同模型价格不同' : 'Priced per input/output token per model'}</span>
                </li>
                <li className="flex items-start gap-2">
                  <span style={{ color: 'var(--accent-amber)' }}>◆</span>
                  <span>{lang === 'zh' ? '预扣余额机制：请求前预扣最大预估费用，完成后退还差额' : 'Pre-deduct: max estimated cost reserved before call'}</span>
                </li>
                <li className="flex items-start gap-2">
                  <span style={{ color: 'var(--accent-amber)' }}>◆</span>
                  <span>{lang === 'zh' ? 'Redis Lua 脚本原子结算，高并发下零超卖' : 'Atomic Redis Lua settlement, zero overselling'}</span>
                </li>
                <li className="flex items-start gap-2">
                  <span style={{ color: 'var(--accent-amber)' }}>◆</span>
                  <span>{lang === 'zh' ? `最小充值金额：¥${BILLING_RULES.minTopUp}` : `Min top-up: ¥${BILLING_RULES.minTopUp}`}</span>
                </li>
                <li className="flex items-start gap-2">
                  <span style={{ color: 'var(--accent-amber)' }}>◆</span>
                  <span>{lang === 'zh' ? '适用于：所有付费模型（含自定义 API Key）' : 'For: All paid models (incl. custom API keys)'}</span>
                </li>
              </ul>
            </motion.div>
          </div>

          {/* Usage Stats */}
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}
            className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
            <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
              {lang === 'zh' ? '我的用量概览' : 'My Usage Overview'}
            </h3>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
              {[
                { label: lang === 'zh' ? '当前余额' : 'Balance', value: `¥${balance.toFixed(4)}`, color: colors[3] },
                { label: lang === 'zh' ? '今日调用' : 'Today Calls', value: `${freeQuota?.usedCalls || 0} / ${BILLING_RULES.freeModelCallLimit}`, color: colors[0] },
                { label: lang === 'zh' ? '今日Token' : 'Today Tokens', value: `${((freeQuota?.usedTokens || 0) / 1000).toFixed(1)}K / ${(BILLING_RULES.freeModelTokenLimit / 1000).toFixed(0)}K`, color: colors[1] },
                { label: lang === 'zh' ? '总费用' : 'Total Cost', value: `¥${(userStats?.totalCost || 0).toFixed(4)}`, color: colors[4] },
              ].map((s, i) => (
                <div key={i} className="rounded-xl p-4" style={{ background: isDark ? 'rgba(255,255,255,0.02)' : 'rgba(0,0,0,0.02)', border: `1px solid ${isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)'}` }}>
                  <div className="text-[10px] mb-1" style={{ color: 'var(--text-dim)' }}>{s.label}</div>
                  <div className="text-base font-bold" style={{ color: s.color }}>{s.value}</div>
                </div>
              ))}
            </div>
          </motion.div>

          {isAdmin && platformStats && (
            <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.25 }}
              className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
              <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
                {lang === 'zh' ? '平台计费总览（管理员）' : 'Platform Billing Overview (Admin)'}
              </h3>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
                {[
                  { label: lang === 'zh' ? '总调用次数' : 'Total Calls', value: (platformStats.totalCalls || 0).toLocaleString(), color: colors[0] },
                  { label: lang === 'zh' ? '总收益' : 'Total Revenue', value: `¥${(platformStats.totalCost || 0).toFixed(2)}`, color: colors[3] },
                  { label: lang === 'zh' ? '今日调用' : 'Today Calls', value: (platformStats.todayCalls || 0).toLocaleString(), color: colors[1] },
                  { label: lang === 'zh' ? '今日收益' : 'Today Revenue', value: `¥${(platformStats.todayCost || 0).toFixed(2)}`, color: colors[4] },
                ].map((s, i) => (
                  <div key={i} className="rounded-xl p-4" style={{ background: isDark ? 'rgba(255,255,255,0.02)' : 'rgba(0,0,0,0.02)', border: `1px solid ${isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)'}` }}>
                    <div className="text-[10px] mb-1" style={{ color: 'var(--text-dim)' }}>{s.label}</div>
                    <div className="text-base font-bold" style={{ color: s.color }}>{s.value}</div>
                  </div>
                ))}
              </div>
            </motion.div>
          )}
        </div>
      )}

      {activeTab === 'detail' && (
        <div className="space-y-6">
          {/* Model Pricing Table */}
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}
            className="rounded-2xl border overflow-hidden" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
            <div className="p-6 border-b" style={{ borderColor: 'var(--border-color)' }}>
              <h3 className="text-sm font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>
                {lang === 'zh' ? '模型定价明细' : 'Model Pricing Details'}
              </h3>
              <p className="text-xs" style={{ color: 'var(--text-dim)' }}>
                {lang === 'zh' ? '价格单位：美元/千Token (USD per 1K tokens)' : 'Unit: USD per 1K tokens'}
              </p>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-xs">
                <thead>
                  <tr style={{ borderBottom: `1px solid var(--border-color)` }}>
                    <th className="text-left py-3 px-4" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '模型名称' : 'Model'}</th>
                    <th className="text-left py-3 px-4" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '提供商' : 'Provider'}</th>
                    <th className="text-right py-3 px-4" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '输入价格' : 'Input Price'}</th>
                    <th className="text-right py-3 px-4" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '输出价格' : 'Output Price'}</th>
                    <th className="text-right py-3 px-4" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '计费模式' : 'Billing Mode'}</th>
                    <th className="text-center py-3 px-4" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '状态' : 'Status'}</th>
                  </tr>
                </thead>
                <tbody>
                  {freeModels.map((m, i) => (
                    <tr key={m.id} style={{ borderBottom: `1px solid var(--border-color)` }}>
                      <td className="py-3 px-4 font-medium" style={{ color: 'var(--text-primary)' }}>{m.modelName}</td>
                      <td className="py-3 px-4" style={{ color: 'var(--text-dim)' }}>{m.provider}</td>
                      <td className="py-3 px-4 text-right font-mono" style={{ color: 'var(--accent-emerald)' }}>{lang === 'zh' ? '免费' : 'Free'}</td>
                      <td className="py-3 px-4 text-right font-mono" style={{ color: 'var(--accent-emerald)' }}>{lang === 'zh' ? '免费' : 'Free'}</td>
                      <td className="py-3 px-4 text-right">
                        <span className="px-2 py-0.5 rounded-md text-[10px]" style={{ background: 'var(--accent-emerald)' + '15', color: 'var(--accent-emerald)' }}>
                          {lang === 'zh' ? '按次数' : 'Per-Call'}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-center">
                        <span className="w-2 h-2 rounded-full inline-block" style={{ background: m.status === 1 ? 'var(--accent-emerald)' : 'var(--accent-rose)' }} />
                      </td>
                    </tr>
                  ))}
                  {paidModels.map((m, i) => (
                    <tr key={m.id} style={{ borderBottom: `1px solid var(--border-color)` }}>
                      <td className="py-3 px-4 font-medium" style={{ color: 'var(--text-primary)' }}>{m.modelName}</td>
                      <td className="py-3 px-4" style={{ color: 'var(--text-dim)' }}>{m.provider}</td>
                      <td className="py-3 px-4 text-right font-mono" style={{ color: 'var(--text-secondary)' }}>¥{Number(m.inputPrice).toFixed(6)}</td>
                      <td className="py-3 px-4 text-right font-mono" style={{ color: 'var(--text-secondary)' }}>¥{Number(m.outputPrice).toFixed(6)}</td>
                      <td className="py-3 px-4 text-right">
                        <span className="px-2 py-0.5 rounded-md text-[10px]" style={{ background: 'var(--accent-amber)' + '15', color: 'var(--accent-amber)' }}>
                          {lang === 'zh' ? '按Token' : 'Per-Token'}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-center">
                        <span className="w-2 h-2 rounded-full inline-block" style={{ background: m.status === 1 ? 'var(--accent-emerald)' : 'var(--accent-rose)' }} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </motion.div>

          {/* Pie Chart */}
          {freeModels.length + paidModels.length > 0 && (
            <div className="grid md:grid-cols-2 gap-6">
              <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
                  {lang === 'zh' ? '模型价格分布' : 'Model Price Distribution'}
                </h3>
                <div id="billing-pie" className="w-full h-64" />
              </div>
              <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
                  {lang === 'zh' ? '计费安全保障' : 'Billing Security'}
                </h3>
                <div className="space-y-3">
                  {[
                    { title: 'Redis Lua 原子脚本', desc: '所有余额操作在 Redis 单线程中执行，杜绝并发竞争，确保高并发下资金零损失。', icon: 'M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z' },
                    { title: '预扣 + 结算双阶段', desc: '请求前预扣最大预估费用防止超支，请求完成后按实际用量结算，多退少补。', icon: 'M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z' },
                    { title: '幂等性保障', desc: '每次计费使用 UUID 唯一标识，防止重复扣费，所有交易可完整追溯。', icon: 'M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15' },
                    { title: '调用日志关联', desc: '每笔费用与 call_log 记录一一对应，任意时刻可审计每一分钱的去向。', icon: 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2' },
                  ].map((item, i) => (
                    <div key={i} className="flex gap-3 p-2">
                      <div className="w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0 mt-0.5" style={{ background: colors[i % colors.length] + '15' }}>
                        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke={colors[i % colors.length]} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d={item.icon}/></svg>
                      </div>
                      <div>
                        <div className="text-xs font-semibold mb-0.5" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? item.title : item.title}</div>
                        <div className="text-[10px] leading-relaxed" style={{ color: 'var(--text-dim)' }}>{item.desc}</div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {activeTab === 'faq' && (
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="space-y-3">
          {FAQ_ITEMS.map((item, i) => (
            <div key={i} className="rounded-2xl p-5 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
              <div className="flex items-start gap-3">
                <span className="text-sm font-bold flex-shrink-0" style={{ color: 'var(--accent-purple)' }}>Q</span>
                <div>
                  <div className="text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>{item.q}</div>
                  <div className="text-xs leading-relaxed" style={{ color: 'var(--text-dim)' }}>{item.a}</div>
                </div>
              </div>
            </div>
          ))}
        </motion.div>
      )}
    </div>
  )
}
