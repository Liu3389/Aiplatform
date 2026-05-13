import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { adminApi } from '@/services/api'
import { fetchAdminAnalytics } from '@/services/mockData'
import type { AdminAnalytics } from '@/services/mockData'
import {
  AreaChart, Area, BarChart, Bar, PieChart, Pie, Cell, RadarChart, Radar,
  PolarGrid, PolarAngleAxis, PolarRadiusAxis,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend,
  ComposedChart, Line,
} from 'recharts'

const COLORS = ['#818cf8', '#34d399', '#f472b6', '#fbbf24', '#94a3b8', '#fb923c', '#a78bfa']

const CustomTooltip = ({ active, payload, label }: any) => {
  if (!active || !payload?.length) return null
  return (
    <div className="bg-surface-800 border border-white/10 rounded-xl px-4 py-3 shadow-2xl">
      <p className="text-xs text-surface-400 mb-1">{label}</p>
      {payload.map((p: any, i: number) => (
        <p key={i} className="text-sm font-semibold" style={{ color: p.color }}>{p.name}: {typeof p.value === 'number' ? p.value.toLocaleString() : p.value}</p>
      ))}
    </div>
  )
}

export default function AdminAnalyticsPage() {
  const [data, setData] = useState<AdminAnalytics | null>(null)
  const [loading, setLoading] = useState(true)
  const [timeRange, setTimeRange] = useState<'7d' | '14d' | '30d'>('14d')

  useEffect(() => {
    setLoading(true)
    Promise.all([
      adminApi.getAnalytics().catch(() => null),
      adminApi.getStats().catch(() => null),
    ]).then(([analyticsData, statsData]) => {
      if (analyticsData || statsData) {
        const stats = statsData || { totalUsers: 0, activeUsers: 0, totalApiKeys: 0, totalCalls: 0, totalCost: 0, todayCalls: 0, todayCost: 0 }
        const d: AdminAnalytics = {
          systemStats: {
            totalUsers: stats.totalUsers || 0,
            activeUsers: stats.activeUsers || 0,
            totalApiKeys: stats.totalApiKeys || 0,
            totalCalls: stats.totalCalls || 0,
            totalCost: stats.totalCost || 0,
            todayCalls: stats.todayCalls || 0,
            todayCost: stats.todayCost || 0,
          },
          dailyUsage: analyticsData?.dailyUsage || [],
          modelUsage: analyticsData?.modelUsage || [],
          hourlyTraffic: analyticsData?.hourlyTraffic || [],
          topUsers: analyticsData?.topUsers || [],
          revenueTrend: analyticsData?.revenueTrend || [],
          userGrowth: analyticsData?.userGrowth || [],
        }
        setData(d)
        setLoading(false)
      } else {
        fetchAdminAnalytics().then(res => {
          setData(res.data)
          setLoading(false)
        })
      }
    })
  }, [])

  if (loading) return (
    <div className="flex items-center justify-center h-96">
      <motion.div animate={{ rotate: 360 }} transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}>
        <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="#818cf8" strokeWidth="2">
          <path d="M12 2a10 10 0 019.95 9" strokeLinecap="round"/>
        </svg>
      </motion.div>
    </div>
  )

  if (!data) return null

  const filteredDaily = timeRange === '7d' ? data.dailyUsage.slice(-7) : timeRange === '14d' ? data.dailyUsage.slice(-14) : data.dailyUsage

  const radarData = data.modelUsage.map(m => ({ model: m.model.replace('deepseek-', ''), calls: m.calls, tokens: m.tokens }))

  return (
    <div className="p-4 lg:p-8 max-w-7xl mx-auto">
      <motion.div initial={{ opacity: 0, y: -20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5 }}>
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-2xl font-bold text-white">数据分析</h1>
            <p className="text-sm text-surface-400 mt-1">全平台运营数据可视化 · 实时监控</p>
          </div>
          <div className="flex items-center gap-1 bg-surface-800/50 border border-white/[0.06] rounded-xl p-1">
            {(['7d', '14d', '30d'] as const).map(r => (
              <button key={r} onClick={() => setTimeRange(r)}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${timeRange === r ? 'bg-white text-black' : 'text-surface-400 hover:text-white'}`}
              >{r === '7d' ? '7天' : r === '14d' ? '14天' : '30天'}</button>
            ))}
          </div>
        </div>
      </motion.div>

      {/* KPI Cards */}
      <motion.div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8"
        initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}>
        {[
          { label: '总用户', value: data.systemStats.totalUsers, suffix: '', color: 'text-primary-400 bg-primary-500/10' },
          { label: '活跃用户', value: data.systemStats.activeUsers, suffix: '', color: 'text-emerald-400 bg-emerald-500/10' },
          { label: '总API Keys', value: data.systemStats.totalApiKeys, suffix: '', color: 'text-purple-400 bg-purple-500/10' },
          { label: '总调用量', value: data.systemStats.totalCalls.toLocaleString(), suffix: '', color: 'text-amber-400 bg-amber-500/10' },
          { label: '今日调用', value: data.systemStats.todayCalls, suffix: '', color: 'text-cyan-400 bg-cyan-500/10' },
          { label: '今日营收', value: `$${data.systemStats.todayCost.toFixed(2)}`, suffix: '', color: 'text-rose-400 bg-rose-500/10' },
          { label: '总营收', value: `$${data.systemStats.totalCost.toFixed(2)}`, suffix: '', color: 'text-emerald-400 bg-emerald-500/10' },
          { label: 'API Keys', value: data.systemStats.totalApiKeys, suffix: '', color: 'text-indigo-400 bg-indigo-500/10' },
        ].slice(0, 8).map((card, i) => (
          <motion.div key={i} initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }}
            transition={{ delay: 0.1 + i * 0.03 }} className={`rounded-2xl border border-white/[0.06] p-5 ${card.color.split(' ')[1]}`}>
            <p className="text-xs text-surface-500 font-medium mb-1">{card.label}</p>
            <p className={`text-xl lg:text-2xl font-bold ${card.color.split(' ')[0]}`}>{card.value}</p>
          </motion.div>
        ))}
      </motion.div>

      <div className="grid lg:grid-cols-2 gap-6 mb-6">
        {/* Daily Calls + Revenue */}
        <motion.div className="glass-light rounded-2xl p-6 lg:col-span-2"
          initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}>
          <h3 className="text-sm font-semibold text-white mb-4">调用量与营收趋势</h3>
          <ResponsiveContainer width="100%" height={300}>
            <ComposedChart data={filteredDaily}>
              <defs>
                <linearGradient id="barGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#818cf8" stopOpacity={0.5} />
                  <stop offset="95%" stopColor="#818cf8" stopOpacity={0.05} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.04)" />
              <XAxis dataKey="date" tick={{ fontSize: 10, fill: '#64748b' }} interval={timeRange === '7d' ? 0 : timeRange === '14d' ? 1 : 3} />
              <YAxis yAxisId="left" tick={{ fontSize: 10, fill: '#64748b' }} />
              <YAxis yAxisId="right" orientation="right" tick={{ fontSize: 10, fill: '#64748b' }} />
              <Tooltip content={<CustomTooltip />} />
              <Legend />
              <Bar yAxisId="left" dataKey="calls" name="调用量" fill="url(#barGrad)" radius={[4, 4, 0, 0]} />
              <Line yAxisId="right" type="monotone" dataKey="cost" name="费用($)" stroke="#34d399" strokeWidth={2} dot={false} />
            </ComposedChart>
          </ResponsiveContainer>
        </motion.div>

        {/* Pie Chart */}
        <motion.div className="glass-light rounded-2xl p-6"
          initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.25 }}>
          <h3 className="text-sm font-semibold text-white mb-4">模型调用占比</h3>
          <ResponsiveContainer width="100%" height={280}>
            <PieChart>
              <Pie data={data.modelUsage} cx="50%" cy="50%" innerRadius={65} outerRadius={110} paddingAngle={3}
                dataKey="calls" nameKey="model" label={({ name, percent }: any) => `${String(name || '').replace('deepseek-', '')} ${(percent * 100).toFixed(0)}%`}
                labelLine={{ stroke: '#475569', strokeWidth: 1 }}>
                {data.modelUsage.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} stroke="transparent" />)}
              </Pie>
              <Tooltip content={<CustomTooltip />} />
            </PieChart>
          </ResponsiveContainer>
        </motion.div>

        {/* Radar */}
        <motion.div className="glass-light rounded-2xl p-6"
          initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }}>
          <h3 className="text-sm font-semibold text-white mb-4">模型综合对比</h3>
          <ResponsiveContainer width="100%" height={280}>
            <RadarChart data={radarData}>
              <PolarGrid stroke="rgba(255,255,255,0.06)" />
              <PolarAngleAxis dataKey="model" tick={{ fontSize: 10, fill: '#64748b' }} />
              <PolarRadiusAxis tick={{ fontSize: 9, fill: '#475569' }} />
              <Radar name="调用量" dataKey="calls" stroke="#818cf8" fill="#818cf8" fillOpacity={0.2} strokeWidth={2} />
              <Radar name="Token数" dataKey="tokens" stroke="#34d399" fill="#34d399" fillOpacity={0.2} strokeWidth={2} />
              <Legend />
              <Tooltip content={<CustomTooltip />} />
            </RadarChart>
          </ResponsiveContainer>
        </motion.div>
      </div>

      {/* Revenue & User Growth side by side */}
      <div className="grid lg:grid-cols-2 gap-6 mb-6">
        <motion.div className="glass-light rounded-2xl p-6"
          initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.35 }}>
          <h3 className="text-sm font-semibold text-white mb-4">月度营收 vs 成本</h3>
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={data.revenueTrend} barGap={4}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.04)" />
              <XAxis dataKey="month" tick={{ fontSize: 10, fill: '#64748b' }} />
              <YAxis tick={{ fontSize: 10, fill: '#64748b' }} />
              <Tooltip content={<CustomTooltip />} />
              <Legend />
              <Bar dataKey="revenue" name="营收" fill="#34d399" radius={[4, 4, 0, 0]} />
              <Bar dataKey="costs" name="成本" fill="#f472b6" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </motion.div>

        <motion.div className="glass-light rounded-2xl p-6"
          initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.4 }}>
          <h3 className="text-sm font-semibold text-white mb-4">用户增长趋势</h3>
          <ResponsiveContainer width="100%" height={260}>
            <AreaChart data={data.userGrowth}>
              <defs>
                <linearGradient id="uTotal" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#818cf8" stopOpacity={0.3} /><stop offset="95%" stopColor="#818cf8" stopOpacity={0} />
                </linearGradient>
                <linearGradient id="uActive" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#34d399" stopOpacity={0.3} /><stop offset="95%" stopColor="#34d399" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.04)" />
              <XAxis dataKey="month" tick={{ fontSize: 10, fill: '#64748b' }} />
              <YAxis tick={{ fontSize: 10, fill: '#64748b' }} />
              <Tooltip content={<CustomTooltip />} />
              <Legend />
              <Area type="monotone" dataKey="total" name="总用户" stroke="#818cf8" fill="url(#uTotal)" strokeWidth={2} />
              <Area type="monotone" dataKey="active" name="活跃用户" stroke="#34d399" fill="url(#uActive)" strokeWidth={2} />
            </AreaChart>
          </ResponsiveContainer>
        </motion.div>
      </div>

      {/* Top Users + Hourly Traffic */}
      <div className="grid lg:grid-cols-2 gap-6">
        <motion.div className="glass-light rounded-2xl p-6"
          initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.45 }}>
          <h3 className="text-sm font-semibold text-white mb-4">TOP 10 用户调用排行</h3>
          <div className="space-y-1">
            {data.topUsers.map((u, i) => (
              <div key={i} className="flex items-center justify-between px-3 py-2.5 rounded-lg hover:bg-white/[0.02] transition-colors">
                <div className="flex items-center gap-3">
                  <span className={`w-6 h-6 rounded-md flex items-center justify-center text-xs font-bold ${
                    i < 3 ? 'bg-gradient-to-br from-amber-500/30 to-amber-600/20 text-amber-400' : 'bg-surface-700 text-surface-500'
                  }`}>{u.rank}</span>
                  <span className="text-sm text-white font-medium">{u.username}</span>
                  <span className="text-xs text-surface-500">ID:{u.userId}</span>
                </div>
                <div className="flex items-center gap-4 text-xs">
                  <span className="text-surface-400">{u.calls.toLocaleString()} 次</span>
                  <span className="text-emerald-400/80 font-mono">${u.cost.toFixed(2)}</span>
                </div>
              </div>
            ))}
          </div>
        </motion.div>

        <motion.div className="glass-light rounded-2xl p-6"
          initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.5 }}>
          <h3 className="text-sm font-semibold text-white mb-4">24小时流量热力图</h3>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={data.hourlyTraffic}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.04)" />
              <XAxis dataKey="hour" tick={{ fontSize: 9, fill: '#64748b' }} interval={2} />
              <YAxis tick={{ fontSize: 10, fill: '#64748b' }} />
              <Tooltip content={<CustomTooltip />} />
              <defs>
                <linearGradient id="hourGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#a78bfa" stopOpacity={0.9} />
                  <stop offset="100%" stopColor="#6366f1" stopOpacity={0.4} />
                </linearGradient>
              </defs>
              <Bar dataKey="calls" name="请求量" fill="url(#hourGrad)" radius={[3, 3, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </motion.div>
      </div>
    </div>
  )
}
