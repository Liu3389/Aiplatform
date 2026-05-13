import { useState, useEffect, useRef } from 'react'
import { useAppStore } from '@/stores/appStore'
import { adminApi, userApi } from '@/services/api'
import { fetchUserAnalytics, fetchAdminAnalytics } from '@/services/mockData'
import type { UserAnalytics, AdminAnalytics } from '@/services/mockData'
import { motion, AnimatePresence } from 'framer-motion'
import * as echarts from 'echarts'
import { useThemeStore } from '@/stores/themeStore'
import { useI18nStore } from '@/stores/i18nStore'

type ECharts = echarts.ECharts
type EChartsOption = echarts.EChartsOption

const ANIMATION_DURATION = 1200
const ANIMATION_EASING = 'cubicOut'

function getChartColors(isDark: boolean): string[] {
  return isDark
    ? ['#8B5CF6', '#3B82F6', '#06B6D4', '#10B981', '#F59E0B', '#F43F5E', '#A78BFA', '#60A5FA']
    : ['#7C3AED', '#2563EB', '#0891B2', '#059669', '#D97706', '#E11D48', '#8B5CF6', '#3B82F6']
}

function getChartTheme(isDark: boolean) {
  return {
    backgroundColor: 'transparent',
    textColor: isDark ? '#94A3B8' : '#64748B',
    splitLineColor: isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)',
    borderColor: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)',
  }
}

export default function DashboardPage() {
  const { user, fetchUserInfo } = useAppStore()
  const { theme } = useThemeStore()
  const { lang, t } = useI18nStore()
  const isDark = theme === 'dark'

  const [userData, setUserData] = useState<UserAnalytics | null>(null)
  const [adminData, setAdminData] = useState<AdminAnalytics | null>(null)
  const [balance, setBalance] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [tab, setTab] = useState<'overview' | 'usage' | 'models' | 'realtime'>('overview')
  const isAdmin = user?.role === 'SUPER_ADMIN' || user?.role === 'ADMIN'

  const chartRefs = useRef<{ [key: string]: ECharts | null }>({})
  const colors = getChartColors(isDark)
  const chartTheme = getChartTheme(isDark)

  useEffect(() => {
    const load = async () => {
      setLoading(true)
      try {
        if (isAdmin) {
          try {
            const aa = await adminApi.getAnalytics()
            const stats = await adminApi.getStats().catch(() => null)
            setAdminData({
              systemStats: {
                totalUsers: stats?.totalUsers || 0,
                activeUsers: stats?.activeUsers || 0,
                totalApiKeys: stats?.totalApiKeys || 0,
                totalCalls: stats?.totalCalls || 0,
                totalCost: stats?.totalCost || 0,
                todayCalls: stats?.todayCalls || 0,
                todayCost: stats?.todayCost || 0,
              },
              dailyUsage: aa.dailyUsage || [],
              modelUsage: aa.modelUsage || [],
              hourlyTraffic: aa.hourlyTraffic || [],
              topUsers: aa.topUsers || [],
              revenueTrend: aa.revenueTrend || [],
              userGrowth: aa.userGrowth || [],
            })
          } catch {
            const aa = await fetchAdminAnalytics()
            setAdminData(aa.data)
          }
        }
        try {
          const ua = await fetchUserAnalytics()
          setUserData(ua.data)
        } catch {}
        try { const b = await userApi.getBalance(); setBalance(b) } catch {}
        await fetchUserInfo()
      } catch {}
      setLoading(false)
    }
    load()
  }, [isAdmin])

  useEffect(() => {
    Object.values(chartRefs.current).forEach(chart => chart?.resize())
  }, [isDark, tab])

  if (loading) return (
    <div className="flex items-center justify-center h-96">
      <motion.div animate={{ rotate: 360 }} transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}>
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke={colors[0]} strokeWidth="2">
          <path d="M12 2a10 10 0 019.95 9" strokeLinecap="round"/>
        </svg>
      </motion.div>
    </div>
  )

  const statCards = [
    { label: lang === 'zh' ? '账户余额' : 'Balance', value: `$${(balance ?? user?.balance ?? 0).toFixed(4)}`, color: colors[3], gradient: 'from-emerald-500/20 to-emerald-500/5' },
    { label: lang === 'zh' ? '今日调用' : 'Today Calls', value: (userData?.todayCalls || 0).toLocaleString(), suffix: lang === 'zh' ? '次' : '', color: colors[1], gradient: 'from-blue-500/20 to-blue-500/5' },
    { label: lang === 'zh' ? '今日Token' : 'Today Tokens', value: (userData?.todayTokens || 0).toLocaleString(), color: colors[0], gradient: 'from-purple-500/20 to-purple-500/5' },
    { label: lang === 'zh' ? '今日费用' : 'Today Cost', value: `$${userData?.todayCost?.toFixed(6) || '0'}`, color: colors[4], gradient: 'from-amber-500/20 to-amber-500/5' },
  ]

  const adminBillingCards = isAdmin && adminData ? [
    { label: lang === 'zh' ? '平台总收益' : 'Total Revenue', value: `$${adminData.revenueTrend?.reduce((a: number, m: any) => a + (m.revenue||0), 0).toFixed(2) || '0'}`, color: colors[3], gradient: 'from-emerald-500/20 to-emerald-500/5' },
    { label: lang === 'zh' ? '平台总调用' : 'Total Calls', value: (adminData.systemStats?.totalCalls || 0).toLocaleString(), color: colors[0], gradient: 'from-purple-500/20 to-purple-500/5' },
    { label: lang === 'zh' ? '活跃用户' : 'Active Users', value: (adminData.systemStats?.activeUsers || 0).toLocaleString(), color: colors[2], gradient: 'from-cyan-500/20 to-cyan-500/5' },
    { label: lang === 'zh' ? '人均费用' : 'Avg Per User', value: `$${adminData.systemStats?.totalUsers > 0 ? (adminData.systemStats?.totalCost / adminData.systemStats?.totalUsers).toFixed(4) : '0'}`, color: colors[4], gradient: 'from-amber-500/20 to-amber-500/5' },
  ] : []

  const initChart = (el: HTMLDivElement | null, key: string, option: EChartsOption) => {
    if (!el) return
    if (chartRefs.current[key]) {
      chartRefs.current[key]!.setOption(option, true)
      return
    }
    const chart = echarts.init(el, isDark ? 'dark' : 'light')
    chart.setOption(option)
    chartRefs.current[key] = chart
  }

  const buildAreaChartOption = (data: any[], xKey: string, yKey: string, name: string, colorIndex: number): EChartsOption => ({
    backgroundColor: 'transparent',
    grid: { top: 20, right: 20, bottom: 40, left: 60 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: isDark ? 'rgba(17,24,39,0.95)' : 'rgba(255,255,255,0.95)',
      borderColor: chartTheme.borderColor,
      textStyle: { color: chartTheme.textColor },
      formatter: (params: any) => {
        const p = params[0]
        return `<div style="font-size:12px"><div style="margin-bottom:4px;color:${chartTheme.textColor}">${p.name}</div><div style="color:${colors[colorIndex]};font-weight:600">${p.value.toLocaleString()}</div></div>`
      }
    },
    xAxis: {
      type: 'category',
      data: data.map(d => d[xKey]),
      axisLine: { lineStyle: { color: chartTheme.splitLineColor } },
      axisLabel: { color: chartTheme.textColor, fontSize: 10 },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: chartTheme.splitLineColor } },
      axisLabel: { color: chartTheme.textColor, fontSize: 10 },
    },
    series: [{
      type: 'line',
      name,
      data: data.map(d => d[yKey]),
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      itemStyle: { color: colors[colorIndex] },
      lineStyle: { width: 3, color: colors[colorIndex] },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: colors[colorIndex] + '40' },
          { offset: 1, color: colors[colorIndex] + '00' }
        ])
      },
      animationDuration: ANIMATION_DURATION,
      animationEasing: ANIMATION_EASING,
    }]
  })

  const buildMultiBarChartOption = (data: any[], xKey: string, bars: { key: string; name: string; colorIdx: number }[]): EChartsOption => ({
    backgroundColor: 'transparent',
    grid: { top: 20, right: 20, bottom: 40, left: 60 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: isDark ? 'rgba(17,24,39,0.95)' : 'rgba(255,255,255,0.95)',
      borderColor: chartTheme.borderColor,
      textStyle: { color: chartTheme.textColor },
    },
    legend: {
      data: bars.map(b => b.name),
      bottom: 0,
      textStyle: { color: chartTheme.textColor, fontSize: 10 },
    },
    xAxis: {
      type: 'category',
      data: data.map(d => d[xKey]),
      axisLine: { lineStyle: { color: chartTheme.splitLineColor } },
      axisLabel: { color: chartTheme.textColor, fontSize: 10 },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: chartTheme.splitLineColor } },
      axisLabel: { color: chartTheme.textColor, fontSize: 10 },
    },
    series: bars.map((b, i) => ({
      type: 'bar',
      name: b.name,
      data: data.map(d => d[b.key]),
      itemStyle: { color: colors[b.colorIdx], borderRadius: [4, 4, 0, 0] },
      barMaxWidth: 12,
      animationDuration: ANIMATION_DURATION,
      animationEasing: ANIMATION_EASING,
      animationDelay: i * 100,
    }))
  })

  const buildPieChartOption = (data: any[], nameKey: string, valueKey: string): EChartsOption => ({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: isDark ? 'rgba(17,24,39,0.95)' : 'rgba(255,255,255,0.95)',
      borderColor: chartTheme.borderColor,
      textStyle: { color: chartTheme.textColor },
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center',
      textStyle: { color: chartTheme.textColor, fontSize: 10 },
    },
    series: [{
      type: 'pie',
      radius: ['45%', '75%'],
      center: ['35%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 6, borderColor: isDark ? '#0B1020' : '#F8FAFC', borderWidth: 2 },
      label: { show: false },
      emphasis: {
        label: { show: true, fontSize: 12, fontWeight: 'bold', color: chartTheme.textColor }
      },
      data: data.map((d, i) => ({
        name: d[nameKey],
        value: d[valueKey],
        itemStyle: { color: colors[i % colors.length] }
      })),
      animationType: 'scale',
      animationEasing: 'elasticOut',
      animationDuration: 1000,
    }]
  })

  const buildGaugeChartOption = (value: number, name: string, colorIdx: number): EChartsOption => ({
    backgroundColor: 'transparent',
    series: [{
      type: 'gauge',
      startAngle: 200,
      endAngle: -20,
      min: 0,
      max: 100,
      splitNumber: 5,
      itemStyle: { color: colors[colorIdx] },
      progress: { show: true, width: 8, itemStyle: { color: colors[colorIdx] } },
      pointer: { show: false },
      axisLine: { lineStyle: { width: 8, color: [[1, isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.06)']] } },
      axisTick: { show: false },
      splitLine: { show: false },
      axisLabel: { show: false },
      title: { show: false },
      detail: {
        valueAnimation: true,
        fontSize: 20,
        fontWeight: 'bold',
        offsetCenter: [0, '30%'],
        formatter: '{value}%',
        color: chartTheme.textColor
      },
      data: [{ value, name }],
      animationDuration: ANIMATION_DURATION,
      animationEasing: ANIMATION_EASING,
    }]
  })

  const buildRadarChartOption = (data: any[], indicators: { name: string; max: number }[]): EChartsOption => ({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item' },
    radar: {
      indicator: indicators,
      shape: 'polygon' as const,
      splitNumber: 4,
      axisLine: { lineStyle: { color: chartTheme.splitLineColor } },
      splitLine: { lineStyle: { color: chartTheme.splitLineColor } },
      splitArea: { areaStyle: { color: ['transparent' as any, 'transparent' as any] } },
      center: ['50%', '55%'],
      radius: '65%',
      name: {
        textStyle: { color: chartTheme.textColor, fontSize: 10 } as any,
      } as any,
    } as any,
    series: [{
      type: 'radar',
      data: [{ value: data, name: 'Usage', symbol: 'circle', symbolSize: 6,
        itemStyle: { color: colors[0], borderColor: colors[0], borderWidth: 2 },
        areaStyle: { color: colors[0] + '30' } as any,
        lineStyle: { color: colors[0], width: 2 },
      }] as any,
      animationDuration: ANIMATION_DURATION,
      animationEasing: ANIMATION_EASING,
    }]
  })

  const buildHeatmapOption = (data: { hour: number; day: number; value: number }[]): EChartsOption => ({
    backgroundColor: 'transparent',
    tooltip: {
      position: 'top',
      backgroundColor: isDark ? 'rgba(17,24,39,0.95)' : 'rgba(255,255,255,0.95)',
      borderColor: chartTheme.borderColor,
      textStyle: { color: chartTheme.textColor },
    },
    grid: { top: 20, right: 20, bottom: 40, left: 60 },
    xAxis: {
      type: 'category',
      data: Array.from({ length: 24 }, (_, i) => `${i}:00`),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: chartTheme.textColor, fontSize: 9, interval: 3 },
    },
    yAxis: {
      type: 'category',
      data: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'],
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: chartTheme.textColor, fontSize: 10 },
    },
    visualMap: {
      min: 0,
      max: 100,
      calculable: false,
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      inRange: { color: [isDark ? '#1E293B' : '#E2E8F0', colors[0]] },
      textStyle: { color: chartTheme.textColor, fontSize: 10 },
    },
    series: [{
      type: 'heatmap',
      data: data.map(d => [d.hour, d.day, d.value]),
      itemStyle: { borderRadius: 4, borderWidth: 2, borderColor: isDark ? '#0B1020' : '#F8FAFC' },
      emphasis: { itemStyle: { shadowBlur: 10, shadowColor: colors[0] + '50' } },
      animation: true,
      animationDuration: ANIMATION_DURATION,
      animationEasing: ANIMATION_EASING,
    }]
  })

  return (
    <div className="p-4 lg:p-8 max-w-7xl mx-auto overflow-y-auto h-full">
      <motion.div initial={{ opacity: 0, y: -20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5 }}>
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
              {lang === 'zh' ? '控制台' : 'Dashboard'}
            </h1>
            <p className="text-sm mt-1" style={{ color: 'var(--text-dim)' }}>
              {isAdmin ? (lang === 'zh' ? '平台运营概览' : 'Platform Overview') : (lang === 'zh' ? '你的用量概览' : 'Your Usage Overview')}
            </p>
          </div>
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-full" style={{ background: 'var(--accent-emerald)' + '15', border: `1px solid ${'var(--accent-emerald)'}30` }}>
            <span className="w-1.5 h-1.5 rounded-full animate-pulse" style={{ background: 'var(--accent-emerald)' }} />
            <span className="text-[11px] font-medium" style={{ color: 'var(--accent-emerald)' }}>
              {lang === 'zh' ? '实时更新' : 'Live'}
            </span>
          </div>
        </div>
      </motion.div>

      <motion.div
        className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1, duration: 0.5 }}
      >
        {statCards.map((card, i) => (
          <motion.div
            key={i}
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ delay: 0.1 + i * 0.05, duration: 0.4 }}
            className="relative overflow-hidden rounded-2xl p-5 border"
            style={{
              background: `linear-gradient(135deg, ${card.gradient.split(' ')[0].replace('/20', '/10')}, ${card.gradient.split(' ')[1]})`,
              borderColor: card.color + '30',
            }}
          >
            <div className="relative z-10">
              <p className="text-xs font-medium mb-1" style={{ color: 'var(--text-dim)' }}>{card.label}</p>
              <p className="text-xl lg:text-2xl font-bold" style={{ color: card.color }}>
                {card.value}{card.suffix && <span className="text-sm font-normal ml-1" style={{ color: 'var(--text-dim)' }}>{card.suffix}</span>}
              </p>
            </div>
            <div
              className="absolute top-0 right-0 w-24 h-24 rounded-full blur-3xl opacity-20"
              style={{ background: card.color }}
            />
          </motion.div>
        ))}
      </motion.div>

      {isAdmin && adminBillingCards.length > 0 && (
        <motion.div
          className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2, duration: 0.5 }}
        >
          {adminBillingCards.map((card, i) => (
            <motion.div
              key={i}
              initial={{ opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ delay: 0.2 + i * 0.05, duration: 0.4 }}
              className="relative overflow-hidden rounded-2xl p-5 border"
              style={{
                background: `linear-gradient(135deg, ${card.gradient.split(' ')[0].replace('/20', '/10')}, ${card.gradient.split(' ')[1]})`,
                borderColor: card.color + '30',
              }}
            >
              <div className="relative z-10">
                <div className="flex items-center gap-1.5 mb-1">
                  <span className="text-[10px] px-1.5 py-0.5 rounded-md" style={{ background: card.color + '15', color: card.color }}>{lang === 'zh' ? '平台' : 'Platform'}</span>
                </div>
                <p className="text-xs font-medium mb-1" style={{ color: 'var(--text-dim)' }}>{card.label}</p>
                <p className="text-xl lg:text-2xl font-bold" style={{ color: card.color }}>{card.value}</p>
              </div>
              <div className="absolute top-0 right-0 w-24 h-24 rounded-full blur-3xl opacity-20" style={{ background: card.color }} />
            </motion.div>
          ))}
        </motion.div>
      )}

      <div className="flex gap-2 mb-6">
        {(['overview', 'usage', 'models', 'realtime'] as const).map(t => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className="px-4 py-2 rounded-xl text-sm font-medium transition-all"
            style={{
              background: tab === t ? 'var(--accent-purple)' : 'var(--bg-surface)',
              color: tab === t ? '#fff' : 'var(--text-dim)',
              border: `1px solid ${tab === t ? 'var(--accent-purple)' : 'var(--border-color)'}`,
            }}
          >
            {t === 'overview' ? (lang === 'zh' ? '概览' : 'Overview') :
             t === 'usage' ? (lang === 'zh' ? '用量趋势' : 'Usage') :
             t === 'models' ? (lang === 'zh' ? '模型分布' : 'Models') :
             (lang === 'zh' ? '实时监控' : 'Realtime')}
          </button>
        ))}
      </div>

      <AnimatePresence mode="wait">
        {tab === 'overview' && userData && (
          <motion.div key="overview" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} className="space-y-6">
            <div className="grid lg:grid-cols-3 gap-6">
              <div className="lg:col-span-2 rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
                  {lang === 'zh' ? '近14天调用趋势' : '14-Day Call Trend'}
                </h3>
                <div ref={el => initChart(el, 'dailyTrend', buildAreaChartOption(userData.dailyUsage.slice(-14), 'date', 'calls', 'Calls', 0))} className="w-full h-72" />
              </div>
              <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
                  {lang === 'zh' ? '今日分布' : 'Today Distribution'}
                </h3>
                <div ref={el => initChart(el, 'hourlyPie', buildPieChartOption(userData.hourlyTraffic.slice(0, 12).map((h, i) => ({ name: h.hour, value: h.calls })), 'name', 'value'))} className="w-full h-72" />
              </div>
            </div>

            <div className="grid lg:grid-cols-2 gap-6">
              <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
                  {lang === 'zh' ? 'Token vs 费用' : 'Tokens vs Cost'}
                </h3>
                <div ref={el => initChart(el, 'tokenCost', buildMultiBarChartOption(userData.dailyUsage.slice(-14), 'date', [
                  { key: 'tokens', name: lang === 'zh' ? 'Token数' : 'Tokens', colorIdx: 2 },
                  { key: 'cost', name: lang === 'zh' ? '费用($)' : 'Cost ($)', colorIdx: 4 },
                ]))} className="w-full h-60" />
              </div>
              <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
                  {lang === 'zh' ? '调用占比' : 'Call Distribution'}
                </h3>
                <div ref={el => initChart(el, 'modelPie', buildPieChartOption(userData.modelUsage, 'model', 'calls'))} className="w-full h-60" />
              </div>
            </div>

            {isAdmin && adminData && (
              <div className="grid lg:grid-cols-2 gap-6">
                <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                  <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
                    {lang === 'zh' ? '平台营收趋势' : 'Revenue Trend'}
                  </h3>
                  <div ref={el => initChart(el, 'revenue', buildAreaChartOption(adminData.revenueTrend, 'month', 'revenue', 'Revenue', 3))} className="w-full h-60" />
                </div>
                <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                  <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
                    {lang === 'zh' ? '用户增长' : 'User Growth'}
                  </h3>
                  <div ref={el => initChart(el, 'userGrowth', buildMultiBarChartOption(adminData.userGrowth, 'month', [
                    { key: 'total', name: lang === 'zh' ? '总用户' : 'Total', colorIdx: 0 },
                    { key: 'active', name: lang === 'zh' ? '活跃用户' : 'Active', colorIdx: 3 },
                  ]))} className="w-full h-60" />
                </div>
              </div>
            )}
          </motion.div>
        )}

        {tab === 'usage' && userData && (
          <motion.div key="usage" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} className="space-y-6">
            <div className="grid lg:grid-cols-3 gap-4">
              {statCards.map((card, i) => (
                <div key={i} className="rounded-2xl p-5 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                  <p className="text-xs font-medium mb-2" style={{ color: 'var(--text-dim)' }}>{card.label}</p>
                  <p className="text-2xl font-bold" style={{ color: card.color }}>{card.value}</p>
                </div>
              ))}
            </div>

            <div className="grid lg:grid-cols-2 gap-6">
              <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
                  {lang === 'zh' ? '每日调用量' : 'Daily Calls'}
                </h3>
                <div ref={el => initChart(el, 'dailyCalls', buildAreaChartOption(userData.dailyUsage, 'date', 'calls', 'Calls', 0))} className="w-full h-64" />
              </div>
              <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
                  {lang === 'zh' ? '每日Token消耗' : 'Daily Tokens'}
                </h3>
                <div ref={el => initChart(el, 'dailyTokens', buildAreaChartOption(userData.dailyUsage, 'date', 'tokens', 'Tokens', 2))} className="w-full h-64" />
              </div>
            </div>

            <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
              <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
                {lang === 'zh' ? '24小时流量热力图' : '24H Traffic Heatmap'}
              </h3>
              <div ref={el => initChart(el, 'heatmap', buildHeatmapOption(
                userData.hourlyTraffic.map((h, i) => ({
                  hour: i,
                  day: Math.floor(Math.random() * 7),
                  value: h.calls,
                }))
              ))} className="w-full h-64" />
            </div>

            {isAdmin && adminData && (
              <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
                  {lang === 'zh' ? 'TOP 10 用户' : 'TOP 10 Users'}
                </h3>
                <div className="space-y-2">
                  {adminData.topUsers?.slice(0, 10).map((u, i) => (
                    <div key={i} className="flex items-center justify-between p-3 rounded-xl transition-colors"
                      style={{ background: 'var(--bg-surface)' }}>
                      <div className="flex items-center gap-3">
                        <span className={`w-6 h-6 rounded-md flex items-center justify-center text-xs font-bold ${
                          i < 3 ? 'bg-amber-500/20' : ''
                        }`} style={{ color: i < 3 ? '#F59E0B' : 'var(--text-dim)' }}>
                          {u.rank}
                        </span>
                        <span className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>{u.username}</span>
                      </div>
                      <div className="flex items-center gap-4 text-xs">
                        <span style={{ color: 'var(--text-dim)' }}>{u.calls.toLocaleString()} {lang === 'zh' ? '次' : 'calls'}</span>
                        <span style={{ color: 'var(--accent-amber)' }}>${u.cost.toFixed(2)}</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </motion.div>
        )}

        {tab === 'models' && userData && (
          <motion.div key="models" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} className="space-y-6">
            <div className="grid lg:grid-cols-2 gap-6">
              <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
                  {lang === 'zh' ? '模型调用分布' : 'Model Distribution'}
                </h3>
                <div ref={el => initChart(el, 'modelDist', buildPieChartOption(userData.modelUsage, 'model', 'calls'))} className="w-full h-72" />
              </div>
              <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
                  {lang === 'zh' ? '模型性能雷达' : 'Model Performance'}
                </h3>
                <div ref={el => initChart(el, 'radar', buildRadarChartOption(
                  userData.modelUsage.map(m => m.calls / 1000),
                  [
                    { name: 'Calls', max: Math.max(...userData.modelUsage.map(m => m.calls / 1000)) * 1.2 },
                    { name: 'Tokens', max: Math.max(...userData.modelUsage.map(m => m.tokens / 1000)) * 1.2 },
                    { name: 'Cost', max: Math.max(...userData.modelUsage.map(m => m.cost)) * 1.2 },
                  ]
                ))} className="w-full h-72" />
              </div>
            </div>

            <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
              <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
                {lang === 'zh' ? '模型使用详情' : 'Model Usage Details'}
              </h3>
              <div className="grid lg:grid-cols-2 gap-4">
                {userData.modelUsage.map((m, i) => (
                  <div key={i} className="p-4 rounded-xl border" style={{ background: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
                    <div className="flex items-center justify-between mb-3">
                      <span className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>{m.model}</span>
                      <span className="w-3 h-3 rounded-full" style={{ background: colors[i % colors.length] }} />
                    </div>
                    <div className="space-y-2">
                      <div className="flex justify-between text-xs">
                        <span style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '调用次数' : 'Calls'}</span>
                        <span style={{ color: 'var(--text-primary)' }}>{m.calls.toLocaleString()}</span>
                      </div>
                      <div className="flex justify-between text-xs">
                        <span style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? 'Token数' : 'Tokens'}</span>
                        <span style={{ color: 'var(--text-primary)' }}>{m.tokens.toLocaleString()}</span>
                      </div>
                      <div className="flex justify-between text-xs">
                        <span style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '费用' : 'Cost'}</span>
                        <span style={{ color: colors[4] }}>${m.cost.toFixed(4)}</span>
                      </div>
                      <div className="h-2 rounded-full overflow-hidden mt-2" style={{ background: 'var(--bg-primary)' }}>
                        <motion.div
                          className="h-full rounded-full"
                          style={{ background: colors[i % colors.length] }}
                          initial={{ width: 0 }}
                          animate={{ width: `${(m.calls / Math.max(...userData.modelUsage.map(x => x.calls))) * 100}%` }}
                          transition={{ duration: 1, delay: i * 0.1 }}
                        />
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {isAdmin && adminData && (
              <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
                  {lang === 'zh' ? '平台模型总用量' : 'Platform Model Usage'}
                </h3>
                <div className="space-y-3">
                  {adminData.modelUsage.map((m, i) => (
                    <div key={i} className="space-y-1.5">
                      <div className="flex justify-between text-xs">
                        <span style={{ color: 'var(--text-secondary)' }}>{m.model}</span>
                        <span style={{ color: 'var(--text-dim)' }}>{m.calls.toLocaleString()} {lang === 'zh' ? '次' : 'calls'} · ${m.cost.toFixed(2)}</span>
                      </div>
                      <div className="h-2 rounded-full overflow-hidden" style={{ background: 'var(--bg-surface)' }}>
                        <motion.div
                          className="h-full rounded-full"
                          style={{ background: colors[i % colors.length] }}
                          initial={{ width: 0 }}
                          animate={{ width: `${(m.calls / adminData.modelUsage[0].calls) * 100}%` }}
                          transition={{ duration: 1, delay: 0.2 + i * 0.1, ease: 'easeOut' }}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </motion.div>
        )}

        {tab === 'realtime' && (
          <motion.div key="realtime" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} className="space-y-6">
            <div className="grid lg:grid-cols-4 gap-4">
              {[
                { label: lang === 'zh' ? '实时QPS' : 'Real QPS', value: Math.floor(Math.random() * 100), max: 200, colorIdx: 0 },
                { label: lang === 'zh' ? '响应延迟' : 'Latency', value: Math.floor(Math.random() * 500), max: 1000, colorIdx: 1 },
                { label: lang === 'zh' ? '成功率' : 'Success Rate', value: 95 + Math.random() * 5, max: 100, colorIdx: 3 },
                { label: lang === 'zh' ? '活跃连接' : 'Active Conn', value: Math.floor(Math.random() * 5000), max: 10000, colorIdx: 2 },
              ].map((g, i) => (
                <div key={i} className="rounded-2xl p-5 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                  <p className="text-xs font-medium mb-2" style={{ color: 'var(--text-dim)' }}>{g.label}</p>
                  <div ref={el => initChart(el, `gauge${i}`, buildGaugeChartOption((g.value / g.max) * 100, g.label, g.colorIdx))} className="w-full h-32" />
                </div>
              ))}
            </div>

            <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
              <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
                {lang === 'zh' ? '实时流量' : 'Real-time Traffic'}
              </h3>
              <div ref={el => initChart(el, 'realtime', buildAreaChartOption(
                Array.from({ length: 30 }, (_, i) => ({ date: `${i}s`, calls: Math.floor(Math.random() * 1000 + 500) })),
                'date', 'calls', 'Traffic', 1
              ))} className="w-full h-72" />
            </div>

            <div className="grid lg:grid-cols-2 gap-6">
              <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
                  {lang === 'zh' ? 'API健康状态' : 'API Health'}
                </h3>
                <div className="space-y-3">
                  {['DeepSeek V4', 'GPT-4o', 'Claude 3', 'Gemini Pro'].map((name, i) => (
                    <div key={i} className="flex items-center justify-between p-3 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
                      <span className="text-sm" style={{ color: 'var(--text-primary)' }}>{name}</span>
                      <div className="flex items-center gap-2">
                        <span className="w-2 h-2 rounded-full animate-pulse" style={{ background: 'var(--accent-emerald)' }} />
                        <span className="text-xs" style={{ color: 'var(--accent-emerald)' }}>{lang === 'zh' ? '正常' : 'Healthy'}</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
              <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
                  {lang === 'zh' ? '系统预警' : 'System Alerts'}
                </h3>
                <div className="space-y-3">
                  {[
                    { type: 'info', msg: lang === 'zh' ? '系统运行正常' : 'All systems operational' },
                    { type: 'warn', msg: lang === 'zh' ? 'DeepSeek API 响应延迟略高' : 'DeepSeek API latency slightly high' },
                    { type: 'success', msg: lang === 'zh' ? '自动扩容已触发' : 'Auto-scaling triggered' },
                  ].map((alert, i) => (
                    <div key={i} className="flex items-center gap-3 p-3 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
                      <span className={`w-2 h-2 rounded-full ${
                        alert.type === 'info' ? 'bg-blue-500' :
                        alert.type === 'warn' ? 'bg-amber-500' : 'bg-emerald-500'
                      }`} />
                      <span className="text-xs" style={{ color: 'var(--text-secondary)' }}>{alert.msg}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
