import { useState, useEffect, useRef } from 'react'
import { useAppStore } from '@/stores/appStore'
import { adminApi, userApi } from '@/services/api'
import { fetchUserAnalytics } from '@/services/mockData'
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
  const { lang } = useI18nStore()
  const isDark = theme === 'dark'
  const colors = getChartColors(isDark)

  const [stats, setStats] = useState({ balance: 0, todayCalls: 0, todayTokens: 0, todayCost: 0 })
  const [userData, setUserData] = useState<any>(null)
  const chartRefs = useRef<{ [key: string]: ECharts | null }>({})

  useEffect(() => {
    const load = async () => {
      try {
        const [ua, balance] = await Promise.all([
          fetchUserAnalytics(),
          userApi.getBalance().catch(() => 0)
        ])
        setUserData(ua.data)
        setStats({
          balance: Number(balance) || 0,
          todayCalls: ua.data?.todayCalls || 0,
          todayTokens: ua.data?.todayTokens || 0,
          todayCost: ua.data?.todayCost || 0,
        })
      } catch {}
    }
    load()
  }, [])

  useEffect(() => {
    return () => {
      Object.values(chartRefs.current).forEach(c => c?.dispose())
    }
  }, [])

  const kpis = [
    { label: lang === 'zh' ? '账户余额' : 'Balance', value: `$${stats.balance.toFixed(4)}`, color: colors[3] },
    { label: lang === 'zh' ? '今日调用' : 'Today Calls', value: stats.todayCalls.toLocaleString(), color: colors[0] },
    { label: lang === 'zh' ? '今日Token' : 'Today Tokens', value: stats.todayTokens.toLocaleString(), color: colors[1] },
    { label: lang === 'zh' ? '今日费用' : 'Today Cost', value: `$${stats.todayCost.toFixed(6)}`, color: colors[4] },
  ]

  return (
    <div className="p-4 lg:p-6 space-y-6 overflow-y-auto h-full">
      <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }}>
        <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
          {lang === 'zh' ? '概览' : 'Overview'}
        </h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-dim)' }}>
          AI Gateway · {lang === 'zh' ? '模型调度控制台' : 'Model Gateway Console'}
        </p>
      </motion.div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {kpis.map((kpi, i) => (
          <motion.div
            key={i}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.08 }}
            className="rounded-2xl p-5 border"
            style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}
          >
            <p className="text-xs font-medium" style={{ color: 'var(--text-dim)' }}>{kpi.label}</p>
            <p className="text-xl lg:text-2xl font-bold mt-1" style={{ color: kpi.color }}>{kpi.value}</p>
          </motion.div>
        ))}
      </div>

      <div className="grid lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
          <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
            {lang === 'zh' ? '请求趋势' : 'Request Trend'}
          </h3>
          <div ref={el => {
            if (!el) return
            const key = 'trend'
            if (chartRefs.current[key]) chartRefs.current[key]!.dispose()
            const chart = echarts.init(el, isDark ? 'dark' : 'light')
            chart.setOption({
              backgroundColor: 'transparent',
              grid: { top: 20, right: 20, bottom: 40, left: 60 },
              tooltip: { trigger: 'axis', backgroundColor: isDark ? 'rgba(17,24,39,0.95)' : 'rgba(255,255,255,0.95)', borderColor: 'var(--border-color)', textStyle: { color: 'var(--text-dim)' } },
              xAxis: { type: 'category', data: userData?.dailyUsage?.slice(-14)?.map((d: any) => d.date) || [], axisLine: { lineStyle: { color: 'var(--border-color)' } }, axisLabel: { color: 'var(--text-dim)', fontSize: 10 } },
              yAxis: { type: 'value', splitLine: { lineStyle: { color: 'var(--border-color)' } }, axisLabel: { color: 'var(--text-dim)', fontSize: 10 } },
              series: [{
                type: 'line', data: userData?.dailyUsage?.slice(-14)?.map((d: any) => d.calls) || [],
                smooth: true, symbol: 'circle', symbolSize: 6,
                itemStyle: { color: colors[0] },
                lineStyle: { width: 3, color: colors[0] },
                areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: colors[0] + '40' }, { offset: 1, color: colors[0] + '00' }]) },
              }],
            })
            chartRefs.current[key] = chart
          }} className="w-full h-64" />
        </div>

        <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
          <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
            {lang === 'zh' ? '模型调用分布' : 'Model Usage'}
          </h3>
          <div ref={el => {
            if (!el) return
            const key = 'pie'
            if (chartRefs.current[key]) chartRefs.current[key]!.dispose()
            const chart = echarts.init(el, isDark ? 'dark' : 'light')
            const pieData = userData?.modelUsage?.map((m: any, i: number) => ({ name: m.model, value: m.calls, itemStyle: { color: colors[i % colors.length] } })) || []
            chart.setOption({
              backgroundColor: 'transparent',
              tooltip: { trigger: 'item', backgroundColor: isDark ? 'rgba(17,24,39,0.95)' : 'rgba(255,255,255,0.95)', borderColor: 'var(--border-color)', textStyle: { color: 'var(--text-dim)' } },
              series: [{
                type: 'pie', radius: ['50%', '78%'], center: ['50%', '50%'],
                itemStyle: { borderRadius: 6, borderColor: isDark ? '#0B1020' : '#F8FAFC', borderWidth: 2 },
                label: { show: false },
                emphasis: { label: { show: true, fontSize: 12, fontWeight: 'bold', color: isDark ? '#fff' : '#333' } },
                data: pieData,
              }],
            })
            chartRefs.current[key] = chart
          }} className="w-full h-64" />
        </div>
      </div>

      <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
        <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
          {lang === 'zh' ? '每日 Token 消耗' : 'Daily Token Usage'}
        </h3>
        <div ref={el => {
          if (!el) return
          const key = 'bar'
          if (chartRefs.current[key]) chartRefs.current[key]!.dispose()
          const chart = echarts.init(el, isDark ? 'dark' : 'light')
          chart.setOption({
            backgroundColor: 'transparent',
            grid: { top: 20, right: 20, bottom: 40, left: 60 },
            tooltip: { trigger: 'axis', backgroundColor: isDark ? 'rgba(17,24,39,0.95)' : 'rgba(255,255,255,0.95)', borderColor: 'var(--border-color)', textStyle: { color: 'var(--text-dim)' } },
            xAxis: { type: 'category', data: userData?.dailyUsage?.slice(-14)?.map((d: any) => d.date) || [], axisLine: { lineStyle: { color: 'var(--border-color)' } }, axisLabel: { color: 'var(--text-dim)', fontSize: 10 } },
            yAxis: { type: 'value', splitLine: { lineStyle: { color: 'var(--border-color)' } }, axisLabel: { color: 'var(--text-dim)', fontSize: 10 } },
            series: [{
              type: 'bar', data: userData?.dailyUsage?.slice(-14)?.map((d: any) => d.tokens) || [],
              itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: colors[2] }, { offset: 1, color: colors[3] }]), borderRadius: [6, 6, 0, 0] },
              barMaxWidth: 16, animationDelay: (idx: number) => idx * 50,
            }],
          })
          chartRefs.current[key] = chart
        }} className="w-full h-64" />
      </div>
    </div>
  )
}
