import { useState, useEffect, useRef } from 'react'
import { useAppStore } from '@/stores/appStore'
import { userApi } from '@/services/api'
import { motion } from 'framer-motion'
import * as echarts from 'echarts'
import { useThemeStore } from '@/stores/themeStore'
import { useI18nStore } from '@/stores/i18nStore'
import PointsIcon from '@/components/PointsIcon'
import { MEMBERSHIP_QUOTAS } from '@/types'

type ECharts = echarts.ECharts
type EChartsOption = echarts.EChartsOption

function getChartTheme(isDark: boolean) {
  return {
    textColor: isDark ? '#94A3B8' : '#64748B',
    axisLineColor: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)',
    splitLineColor: isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)',
  }
}

const colors = ['#8B5CF6', '#3B82F6', '#06B6D4', '#10B981', '#F59E0B', '#EC4899']

export default function DashboardPage() {
  const { user } = useAppStore()
  const { theme } = useThemeStore()
  const { lang } = useI18nStore()
  const isDark = theme === 'dark'
  const membership = MEMBERSHIP_QUOTAS[(user?.membership?.toLowerCase() || 'free') as keyof typeof MEMBERSHIP_QUOTAS] || MEMBERSHIP_QUOTAS.free
  const ct = getChartTheme(isDark)

  const [range, setRange] = useState<'7d' | '30d'>('7d')
  const [tokensUsed, setTokensUsed] = useState(0)
  const [monthlyPoints, setMonthlyPoints] = useState(0)
  const [dailyData, setDailyData] = useState<{ label: string; tokens: number }[]>([])
  const [loading, setLoading] = useState(true)

  const tokenChartRef = useRef<HTMLDivElement>(null)
  const chartInstRef = useRef<ECharts | null>(null)

  useEffect(() => {
    setLoading(true)
    userApi.getFreeQuota().then(q => {
      setTokensUsed(q?.usedTokens || 0)
      setMonthlyPoints(q?.monthlyPoints || 0)
    }).catch(() => {})

    const days = range === '7d' ? 7 : 30
    const now = new Date()
    const mock: { label: string; tokens: number }[] = []
    for (let i = days - 1; i >= 0; i--) {
      const d = new Date(now)
      d.setDate(d.getDate() - i)
      mock.push({
        label: `${d.getMonth() + 1}/${d.getDate()}`,
        tokens: Math.floor(Math.random() * 8000 + 1000),
      })
    }
    setDailyData(mock)
    setLoading(false)
  }, [range])

  useEffect(() => {
    if (loading || dailyData.length === 0) return
    const labels = dailyData.map(d => d.label)
    const tokenVals = dailyData.map(d => d.tokens)

    const areaOption = (data: number[], name: string, colorIdx: number): EChartsOption => ({
      backgroundColor: 'transparent',
      grid: { top: 10, right: 10, bottom: 25, left: 40 },
      tooltip: {
        trigger: 'axis',
        backgroundColor: isDark ? 'rgba(17,24,39,0.95)' : 'rgba(255,255,255,0.95)',
        borderColor: ct.splitLineColor,
        textStyle: { color: ct.textColor, fontSize: 11 },
        axisPointer: { type: 'line', lineStyle: { color: ct.splitLineColor } },
      },
      xAxis: {
        type: 'category', data: labels,
        axisLine: { lineStyle: { color: ct.axisLineColor } },
        axisTick: { show: false },
        axisLabel: { color: ct.textColor, fontSize: 9 },
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: ct.splitLineColor } },
        axisLabel: { color: ct.textColor, fontSize: 9, formatter: (v: number) => v > 1000 ? (v / 1000).toFixed(0) + 'K' : String(v) },
      },
      series: [{
        name, type: 'line', data,
        smooth: true, symbol: 'none',
        lineStyle: { color: colors[colorIdx], width: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: colors[colorIdx] + '40' },
            { offset: 1, color: colors[colorIdx] + '05' },
          ]),
        },
      }],
    })

    const elToken = tokenChartRef.current
    if (elToken) {
      if (chartInstRef.current) chartInstRef.current.dispose()
      chartInstRef.current = echarts.init(elToken, isDark ? 'dark' : undefined)
      chartInstRef.current.setOption(areaOption(tokenVals, 'Token', 1))
    }

    const handleResize = () => {
      chartInstRef.current?.resize()
    }
    window.addEventListener('resize', handleResize)
    return () => {
      window.removeEventListener('resize', handleResize)
      chartInstRef.current?.dispose()
    }
  }, [dailyData, loading, isDark, range, lang])

  if (loading) {
    return <div className="flex items-center justify-center h-full"><div className="w-6 h-6 border-2 border-brand-purple/30 border-t-brand-purple rounded-full animate-spin" /></div>
  }

  const maxTokens = ((membership.dailyTemporaryPoints + membership.dailyPermanentPoints) / 1000).toFixed(0) + 'K'
  const pointPercent = monthlyPoints > 0 ? Math.min(100, (tokensUsed / 100000) * 100) : 0

  return (
    <div className="p-3 sm:p-4 lg:p-8 max-w-5xl mx-auto overflow-y-auto h-full">
      <motion.div initial={{ opacity: 0, y: -20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.4 }}>
        <div className="flex items-center justify-between mb-4 sm:mb-6">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? '用量信息' : 'Usage'}</h1>
            <p className="text-xs sm:text-sm mt-1" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '你的个人用量概览' : 'Your personal usage overview'}</p>
          </div>
          <div className="flex items-center gap-1 p-0.5 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
            {[{ k: '7d' as const, l: '7d' }, { k: '30d' as const, l: '30d' }].map(r => (
              <button key={r.k} onClick={() => setRange(r.k)} className="px-3 py-1.5 rounded-lg text-xs font-medium transition-all"
                style={{ background: range === r.k ? 'var(--accent-purple)' : 'transparent', color: range === r.k ? '#fff' : 'var(--text-dim)' }}>
                {r.l}
              </button>
            ))}
          </div>
        </div>
      </motion.div>

      {/* KPI Cards - Token + Points only */}
      <div className="grid grid-cols-3 gap-3 mb-6">
        {[
          { l: lang === 'zh' ? '今日Token' : 'Today Tokens', v: (tokensUsed / 1000).toFixed(1) + 'K', s: '', c: colors[1], icon: null },
          { l: lang === 'zh' ? 'Token上限' : 'Token Limit', v: maxTokens, s: lang === 'zh' ? '/天' : '/day', c: colors[0], icon: null },
          { l: lang === 'zh' ? '点数包月' : 'Monthly Pts', v: String(monthlyPoints || 0), s: lang === 'zh' ? '点' : 'pts', c: membership.color, icon: <PointsIcon size={14} color={membership.color} /> },
        ].map((card, i) => (
          <motion.div key={i} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.06 }}
            className="rounded-2xl p-4 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
            <div className="flex items-center justify-between mb-2">
              <span className="text-[10px]" style={{ color: 'var(--text-dim)' }}>{card.l}</span>
              {card.icon}
            </div>
            <div className="text-lg sm:text-xl font-bold" style={{ color: card.c }}>{card.v}<span className="text-xs ml-1 font-normal" style={{ color: 'var(--text-dim)' }}>{card.s}</span></div>
          </motion.div>
        ))}
      </div>

      {/* Points Progress */}
      {monthlyPoints > 0 && (
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.2 }}
          className="rounded-2xl p-5 border mb-6" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
          <div className="flex items-center justify-between mb-3">
            <span className="text-xs font-medium flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
              <PointsIcon size={14} color={membership.color} /> {lang === 'zh' ? '月度点数' : 'Monthly Points'}
            </span>
            <span className="text-xs font-bold" style={{ color: membership.color }}>{tokensUsed / 100 > 0 ? (tokensUsed / 100).toFixed(0) : 0} / {monthlyPoints} {lang === 'zh' ? '点' : 'pts'}</span>
          </div>
          <div className="h-2.5 rounded-full overflow-hidden" style={{ background: 'var(--bg-surface)' }}>
            <motion.div className="h-full rounded-full" initial={{ width: 0 }} animate={{ width: `${pointPercent}%` }} transition={{ duration: 0.8, delay: 0.3 }}
              style={{ background: `linear-gradient(90deg, ${membership.color}, ${membership.color}60)` }} />
          </div>
        </motion.div>
      )}

      {/* Token Chart */}
      <div className="mb-6">
        <div className="rounded-2xl p-4 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
          <h3 className="text-xs font-semibold mb-2" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? 'Token 消耗趋势' : 'Token Usage Trend'}</h3>
          <div ref={tokenChartRef} className="w-full" style={{ height: 220 }} />
        </div>
      </div>

      {/* Membership info */}
      <div className="rounded-2xl p-5 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
        <h3 className="text-xs font-semibold mb-3" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? '当前套餐' : 'Current Plan'}</h3>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl flex items-center justify-center" style={{ background: membership.color + '18' }}>
              <PointsIcon size={18} color={membership.color} />
            </div>
            <div>
              <div className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? membership.label : membership.labelEn}</div>
              <div className="text-[10px]" style={{ color: 'var(--text-dim)' }}>
                {monthlyPoints > 0
                  ? (lang === 'zh' ? `每月 ${monthlyPoints} 点数 · ${maxTokens} Token/天` : `${monthlyPoints} pts/mo · ${maxTokens} tokens/day`)
                  : (lang === 'zh' ? `${maxTokens} Token/天` : `${maxTokens} tokens/day`)
                }
              </div>
            </div>
          </div>
          {monthlyPoints === 0 && (
            <button onClick={() => window.location.href = '/recharge'} className="px-4 py-2 rounded-xl text-xs font-medium text-white transition-all" style={{ background: 'var(--accent-purple)' }}>
              {lang === 'zh' ? '升级套餐' : 'Upgrade'}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}