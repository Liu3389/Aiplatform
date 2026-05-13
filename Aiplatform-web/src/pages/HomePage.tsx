import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAppStore } from '@/stores/appStore'
import { adminApi, userApi, modelApi } from '@/services/api'
import { motion } from 'framer-motion'
import * as echarts from 'echarts'
import { useThemeStore } from '@/stores/themeStore'
import { useI18nStore } from '@/stores/i18nStore'

type ECharts = echarts.ECharts

function getChartColors(isDark: boolean): string[] {
  return isDark
    ? ['#8B5CF6', '#3B82F6', '#06B6D4', '#10B981', '#F59E0B', '#F43F5E']
    : ['#7C3AED', '#2563EB', '#0891B2', '#059669', '#D97706', '#E11D48']
}

export default function HomePage() {
  const { user } = useAppStore()
  const { theme } = useThemeStore()
  const { lang, t } = useI18nStore()
  const navigate = useNavigate()
  const isDark = theme === 'dark'
  const colors = getChartColors(isDark)
  const isAdmin = user?.role === 'SUPER_ADMIN' || user?.role === 'ADMIN'

  const BILLING = { freeModelCallLimit: 30, freeModelTokenLimit: 50000, minTopUp: 1.0 }

  const [stats, setStats] = useState({ balance: 0, todayCalls: 0, todayTokens: 0, todayCost: 0 })
  const [systemStats, setSystemStats] = useState<any>(null)
  const [models, setModels] = useState<any[]>([])
  const chartRef = useRef<ECharts | null>(null)

  useEffect(() => {
    (async () => {
      try {
        const [b, c] = await Promise.all([
          userApi.getBalance().catch(() => 0),
          userApi.getFreeQuota().catch(() => ({ usedCalls: 0, maxCalls: 30, usedTokens: 0, maxTokens: 50000 })),
        ])
        setStats({
          balance: Number(b) || 0,
          todayCalls: c.usedCalls || 0,
          todayTokens: c.usedTokens || 0,
          todayCost: 0,
        })
      } catch {}
      try {
        const m = await modelApi.list()
        setModels(m || [])
      } catch {}
      if (isAdmin) {
        try {
          const s = await adminApi.getStats().catch(() => null)
          setSystemStats(s)
        } catch {}
      }
    })()
  }, [])

  useEffect(() => {
    const el = document.getElementById('home-sparkline')
    if (!el) return
    if (chartRef.current) chartRef.current.dispose()
    const chart = echarts.init(el, isDark ? 'dark' : 'light')
    chart.setOption({
      backgroundColor: 'transparent',
      grid: { top: 5, right: 5, bottom: 5, left: 5 },
      xAxis: { show: false, data: Array.from({length: 24}, (_,i) => i) },
      yAxis: { show: false },
      series: [{
        type: 'line',
        data: [3,5,8,12,18,25,30,28,22,15,10,6,4,3,5,9,15,22,28,32,27,20,12,7],
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 2, color: colors[0] },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: colors[0] + '40' },
          { offset: 1, color: colors[0] + '00' }
        ]) },
      }],
    })
    chartRef.current = chart
    return () => { chart?.dispose() }
  }, [isDark])

  const quickActions = [
    { label: lang === 'zh' ? 'AI 对话' : 'AI Chat', desc: lang === 'zh' ? '免费使用 DeepSeek 模型' : 'Free DeepSeek models', path: '/chat', color: colors[0], icon: 'M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z' },
    { label: lang === 'zh' ? '令牌管理' : 'API Keys', desc: lang === 'zh' ? '管理 API 访问密钥' : 'Manage API keys', path: '/api-keys', color: colors[3], icon: 'M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z' },
    { label: lang === 'zh' ? '控制台' : 'Dashboard', desc: lang === 'zh' ? '查看用量数据分析' : 'Usage analytics', path: '/dashboard', color: colors[1], icon: 'M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z' },
  ]

  const statCards = [
    { label: lang === 'zh' ? '账户余额' : 'Balance', value: `$${stats.balance.toFixed(2)}`, color: colors[3] },
    { label: lang === 'zh' ? '今日已用' : 'Today Used', value: `${stats.todayCalls}/${30} ${lang==='zh'?'次':'calls'}`, color: colors[0] },
    { label: lang === 'zh' ? 'Token消耗' : 'Tokens', value: `${(stats.todayTokens/1000).toFixed(1)}K`, color: colors[1] },
  ]

  return (
    <div className="p-4 sm:p-6 lg:p-8 space-y-6 lg:space-y-8 overflow-y-auto h-full max-w-6xl mx-auto">
      {/* Hero Section */}
      <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }} className="relative overflow-hidden rounded-3xl p-6 sm:p-8 lg:p-10" style={{
        background: isDark
          ? 'linear-gradient(135deg, rgba(139,92,246,0.15) 0%, rgba(59,130,246,0.1) 50%, rgba(6,182,212,0.05) 100%)'
          : 'linear-gradient(135deg, rgba(124,58,237,0.08) 0%, rgba(37,99,235,0.05) 50%, rgba(8,145,178,0.03) 100%)',
        border: `1px solid ${isDark ? 'rgba(139,92,246,0.12)' : 'rgba(124,58,237,0.08)'}`,
      }}>
        <div className="absolute top-0 right-0 w-64 h-64 rounded-full blur-3xl opacity-20" style={{ background: colors[0] }} />
        <div className="absolute bottom-0 left-0 w-48 h-48 rounded-full blur-3xl opacity-10" style={{ background: colors[2] }} />
        <div className="relative z-10">
          <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
            <div>
              <div className="flex items-center gap-2 mb-2">
                <span className="px-2 py-0.5 text-[10px] font-medium rounded-full" style={{
                  background: isDark ? 'rgba(16,185,129,0.15)' : 'rgba(5,150,105,0.1)',
                  color: isDark ? '#10B981' : '#059669',
                  border: `1px solid ${isDark ? 'rgba(16,185,129,0.2)' : 'rgba(5,150,105,0.15)'}`,
                }}>{lang === 'zh' ? '凌点 AI' : 'Lingdian AI'}</span>
                <span className="text-[10px]" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? 'v1.0 · 运行中' : 'v1.0 · Running'}</span>
              </div>
              <h1 className="text-xl sm:text-2xl lg:text-3xl font-bold mb-2" style={{ color: 'var(--text-primary)' }}>
                {lang === 'zh' ? `你好，${user?.username || '用户'}` : `Hi, ${user?.username || 'User'}`}
              </h1>
              <p className="text-sm lg:text-base max-w-lg" style={{ color: 'var(--text-dim)' }}>
                {lang === 'zh'
                  ? '新一代 AI 模型调度平台，支持多模型路由、流式对话、实时计费与安全网关.'
                  : 'Next-gen AI model gateway with multi-model routing, streaming chat, and real-time billing.'}
              </p>
            </div>
            <div className="hidden sm:block flex-shrink-0" style={{ width: 140, height: 60 }}>
              <div id="home-sparkline" className="w-full h-full" />
            </div>
          </div>

          {isAdmin && systemStats && (
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-6">
              {[
                { label: lang === 'zh' ? '总用户' : 'Total Users', value: (systemStats.totalUsers||0).toLocaleString(), color: colors[0] },
                { label: lang === 'zh' ? '今日调用' : 'Today Calls', value: (systemStats.todayCalls||0).toLocaleString(), color: colors[1] },
                { label: lang === 'zh' ? '活跃用户' : 'Active Users', value: (systemStats.activeUsers||0).toLocaleString(), color: colors[3] },
                { label: lang === 'zh' ? '总收益' : 'Total Revenue', value: `$${(systemStats.totalCost||0).toFixed(2)}`, color: colors[4] },
              ].map((s, i) => (
                <div key={i} className="rounded-xl p-3" style={{ background: isDark ? 'rgba(255,255,255,0.03)' : 'rgba(0,0,0,0.02)', border: `1px solid ${isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)'}` }}>
                  <div className="text-[10px] mb-0.5" style={{ color: 'var(--text-dim)' }}>{s.label}</div>
                  <div className="text-sm font-bold" style={{ color: s.color }}>{s.value}</div>
                </div>
              ))}
            </div>
          )}
        </div>
      </motion.div>

      {/* Stat Cards */}
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.08 }}
        className="grid grid-cols-1 sm:grid-cols-3 gap-4"
      >
        {statCards.map((card, i) => (
          <div key={i} className="rounded-2xl p-5 border card-lift" style={{
            background: 'var(--bg-card)',
            borderColor: 'var(--border-color)',
          }}>
            <p className="text-[11px] font-medium mb-1.5" style={{ color: 'var(--text-dim)' }}>{card.label}</p>
            <p className="text-xl font-bold" style={{ color: card.color }}>{card.value}</p>
            <div className="mt-3 h-1 rounded-full overflow-hidden" style={{ background: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)' }}>
              <motion.div className="h-full rounded-full" initial={{ width: 0 }}
                animate={{ width: i === 1 ? `${Math.min((stats.todayCalls/30)*100, 100)}%` : i === 2 ? `${Math.min((stats.todayTokens/50000)*100, 100)}%` : '40%' }}
                transition={{ duration: 1, delay: 0.3 + i * 0.1 }}
                style={{ background: `linear-gradient(90deg, ${card.color}, ${card.color}80)` }}
              />
            </div>
          </div>
        ))}
      </motion.div>

      {/* Billing Summary Card */}
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.12 }}
        className="rounded-2xl p-5 border flex flex-col sm:flex-row sm:items-center justify-between gap-3" style={{
          background: isDark ? 'rgba(139,92,246,0.04)' : 'rgba(124,58,237,0.03)',
          borderColor: isDark ? 'rgba(139,92,246,0.1)' : 'rgba(124,58,237,0.06)',
        }}>
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0" style={{ background: 'var(--accent-purple)' + '15' }}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--accent-purple)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
          </div>
          <div>
            <div className="text-xs font-semibold" style={{ color: 'var(--text-primary)' }}>
              {lang === 'zh' ? '双重计费模式' : 'Dual Billing Mode'}
            </div>
            <div className="text-[10px]" style={{ color: 'var(--text-dim)' }}>
              {lang === 'zh' ? `免费：每日${BILLING.freeModelCallLimit}次/${(BILLING.freeModelTokenLimit/1000).toFixed(0)}K Token · 付费：按Token精确计费 · Lua原子结算` : `Free: ${BILLING.freeModelCallLimit} calls/${(BILLING.freeModelTokenLimit/1000)}K tokens daily · Paid: per-token · Lua atomic`}
            </div>
          </div>
        </div>
        <button onClick={() => navigate('/billing')} className="px-3 py-1.5 rounded-lg text-[10px] font-medium whitespace-nowrap transition-colors"
          style={{ background: 'var(--accent-purple)' + '15', color: 'var(--accent-purple)', border: '1px solid var(--accent-purple)' + '20' }}>
          {lang === 'zh' ? '查看策略 →' : 'Policy →'}
        </button>
      </motion.div>

      {/* Quick Actions */}
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.14 }}>
        <h2 className="text-sm font-semibold mb-3" style={{ color: 'var(--text-primary)' }}>
          {lang === 'zh' ? '快速操作' : 'Quick Actions'}
        </h2>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          {quickActions.map((action, i) => (
            <button
              key={i}
              onClick={() => navigate(action.path)}
              className="relative group rounded-2xl p-5 text-left transition-all duration-300 border"
              style={{
                background: 'var(--bg-card)',
                borderColor: 'var(--border-color)',
              }}
            >
              <div className="flex items-start gap-4">
                <div className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 transition-transform duration-300 group-hover:scale-110"
                  style={{ background: `${action.color}15` }}>
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke={action.color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d={action.icon}/></svg>
                </div>
                <div>
                  <div className="text-sm font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>{action.label}</div>
                  <div className="text-xs" style={{ color: 'var(--text-dim)' }}>{action.desc}</div>
                </div>
              </div>
              <div className="absolute bottom-0 left-0 right-0 h-0.5 rounded-b-2xl transition-all duration-300 opacity-0 group-hover:opacity-100"
                style={{ background: `linear-gradient(90deg, ${action.color}, transparent)` }} />
            </button>
          ))}
        </div>
      </motion.div>

      {/* Available Models */}
      {models.length > 0 && (
        <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.18 }}>
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
              {lang === 'zh' ? '可用模型' : 'Available Models'}
            </h2>
            <button onClick={() => navigate('/store')} className="text-[11px] font-medium" style={{ color: 'var(--accent-purple)' }}>
              {lang === 'zh' ? '查看全部 →' : 'View all →'}
            </button>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3">
            {models.slice(0, 4).map((model, i) => (
              <div key={i} className="rounded-xl p-4 border transition-all duration-300 card-lift" style={{
                background: 'var(--bg-card)',
                borderColor: 'var(--border-color)',
              }}>
                <div className="w-8 h-8 rounded-lg flex items-center justify-center mb-2.5"
                  style={{ background: `${colors[i % colors.length]}15` }}>
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke={colors[i % colors.length]} strokeWidth="2"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
                </div>
                <div className="text-xs font-medium" style={{ color: 'var(--text-primary)' }}>{model.modelName}</div>
                <div className="text-[10px] mt-0.5" style={{ color: 'var(--text-dim)' }}>{model.provider}</div>
                <div className="flex items-center gap-1.5 mt-2">
                  <span className={`w-1.5 h-1.5 rounded-full ${model.status === 1 ? 'bg-accent-emerald' : 'bg-accent-rose'}`} />
                  <span className="text-[10px]" style={{ color: model.status === 1 ? 'var(--accent-emerald)' : 'var(--accent-rose)' }}>
                    {model.status === 1 ? (lang === 'zh' ? '可用' : 'Active') : (lang === 'zh' ? '停用' : 'Inactive')}
                  </span>
                  {model.inputPrice === 0 && <span className="text-[10px] ml-auto px-1.5 py-0.5 rounded-md" style={{ background: 'var(--accent-emerald)' + '15', color: 'var(--accent-emerald)' }}>{lang==='zh'?'免费':'Free'}</span>}
                </div>
              </div>
            ))}
          </div>
        </motion.div>
      )}

      {/* Feature highlights */}
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.22 }}
        className="rounded-2xl p-6 sm:p-8 border overflow-hidden relative" style={{
          background: 'var(--bg-card)',
          borderColor: 'var(--border-color)',
        }}
      >
        <h2 className="text-sm font-semibold mb-6" style={{ color: 'var(--text-primary)' }}>
          {lang === 'zh' ? '平台特性' : 'Platform Features'}
        </h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {[
            { title: lang === 'zh' ? '多模型调度' : 'Multi-Model', desc: lang === 'zh' ? '统一接入 OpenAI、DeepSeek 等主流模型' : 'Unified access to major AI models', icon: 'M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4' },
            { title: lang === 'zh' ? '流式渲染' : 'Streaming', desc: lang === 'zh' ? 'Pretext 极速流式引擎，120FPS 零卡顿' : 'Pretext engine, 120FPS zero jank', icon: 'M13 10V3L4 14h7v7l9-11h-7z' },
            { title: lang === 'zh' ? '安全计费' : 'Billing', desc: lang === 'zh' ? 'Redis Lua 原子性计费，毫秒级结算' : 'Atomic billing with Redis Lua', icon: 'M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z' },
            { title: lang === 'zh' ? '智能限流' : 'Rate Limit', desc: lang === 'zh' ? '令牌桶算法 + IP 级防护' : 'Token bucket + IP protection', icon: 'M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z' },
          ].map((f, i) => (
            <div key={i} className="flex gap-3">
              <div className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0" style={{ background: `${colors[i % colors.length]}15` }}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke={colors[i % colors.length]} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d={f.icon}/></svg>
              </div>
              <div>
                <div className="text-xs font-semibold mb-0.5" style={{ color: 'var(--text-primary)' }}>{f.title}</div>
                <div className="text-[10px] leading-relaxed" style={{ color: 'var(--text-dim)' }}>{f.desc}</div>
              </div>
            </div>
          ))}
        </div>
      </motion.div>

      <div className="text-center pb-4">
        <p className="text-[10px]" style={{ color: 'var(--text-dim)' }}>© 2026 AI Gateway Platform · Lingdian AI</p>
      </div>
    </div>
  )
}
