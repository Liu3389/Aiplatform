import { useState, useEffect, useRef } from 'react'
import { useAppStore } from '@/stores/appStore'
import { userApi } from '@/services/api'
import { motion, AnimatePresence, type Variants } from 'framer-motion'
import * as echarts from 'echarts'
import { useThemeStore } from '@/stores/themeStore'
import { useI18nStore } from '@/stores/i18nStore'
import PointsIcon from '@/components/PointsIcon'
import { MEMBERSHIP_QUOTAS } from '@/types'
import { Link } from 'react-router-dom'

type ECharts = echarts.ECharts
type EChartsOption = echarts.EChartsOption

interface StatCard {
  label: string
  value: string | number
  suffix?: string
  icon: React.ReactNode
  color: string
  trend?: number
}

function getChartTheme(isDark: boolean) {
  return {
    textColor: isDark ? '#94A3B8' : '#64748B',
    axisLineColor: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)',
    splitLineColor: isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)',
  }
}

const colors = ['#8B5CF6', '#3B82F6', '#06B6D4', '#10B981']

// Animated counter component
function AnimatedCounter({ value, duration = 1 }: { value: number; duration?: number }) {
  const [displayValue, setDisplayValue] = useState(0)
  
  useEffect(() => {
    let startTime: number
    let animationFrame: number
    
    const animate = (timestamp: number) => {
      if (!startTime) startTime = timestamp
      const progress = Math.min((timestamp - startTime) / (duration * 1000), 1)
      const eased = 1 - Math.pow(1 - progress, 3) // ease-out cubic
      setDisplayValue(Math.floor(eased * value))
      
      if (progress < 1) {
        animationFrame = requestAnimationFrame(animate)
      }
    }
    
    animationFrame = requestAnimationFrame(animate)
    return () => cancelAnimationFrame(animationFrame)
  }, [value, duration])
  
  return <span>{displayValue.toLocaleString()}</span>
}

export default function DashboardPage() {
  const { user } = useAppStore()
  const { theme } = useThemeStore()
  const { lang, t } = useI18nStore()
  const isDark = theme === 'dark'
  const membership = MEMBERSHIP_QUOTAS[(user?.membership?.toLowerCase() || 'free') as keyof typeof MEMBERSHIP_QUOTAS] || MEMBERSHIP_QUOTAS.free
  const ct = getChartTheme(isDark)

  const [range, setRange] = useState<'7d' | '30d'>('7d')
  const [userPoints, setUserPoints] = useState(0)
  const [tokensUsed, setTokensUsed] = useState(0)
  const [apiCallsCount, setApiCallsCount] = useState(0)
  const [dailyData, setDailyData] = useState<{ label: string; tokens: number; points: number; calls: number }[]>([])
  const [loading, setLoading] = useState(true)

  const tokenChartRef = useRef<HTMLDivElement>(null)
  const chartInstRef = useRef<ECharts | null>(null)

  useEffect(() => {
    const fetchPoints = () => {
      userApi.getFreeQuota().then(q => {
        setUserPoints(q?.monthlyPoints || user?.points || 0)
        setTokensUsed(q?.usedTokens || 0)
        setApiCallsCount(q?.usedCalls || 0)
      }).catch(() => {
        setUserPoints(user?.points || 0)
      })
    }

    setLoading(true)
    fetchPoints()

    const days = range === '7d' ? 7 : 30
    const now = new Date()
    const mock: { label: string; tokens: number; points: number; calls: number }[] = []
    for (let i = days - 1; i >= 0; i--) {
      const d = new Date(now)
      d.setDate(d.getDate() - i)
      mock.push({
        label: `${d.getMonth() + 1}/${d.getDate()}`,
        tokens: Math.floor(Math.random() * 8000 + 1000),
        points: Math.floor(Math.random() * 50 + 10),
        calls: Math.floor(Math.random() * 100 + 20),
      })
    }
    setDailyData(mock)
    setLoading(false)

    const interval = setInterval(fetchPoints, 5000)
    return () => clearInterval(interval)
  }, [range])

  useEffect(() => {
    if (loading || dailyData.length === 0) return

    const labels = dailyData.map(d => d.label)
    const tokenVals = dailyData.map(d => d.tokens)

    const areaOption: EChartsOption = {
      backgroundColor: 'transparent',
      grid: { top: 20, right: 20, bottom: 30, left: 50 },
      tooltip: {
        trigger: 'axis',
        backgroundColor: isDark ? 'rgba(17,24,39,0.95)' : 'rgba(255,255,255,0.95)',
        borderColor: ct.splitLineColor,
        textStyle: { color: ct.textColor, fontSize: 12 },
        axisPointer: { type: 'line', lineStyle: { color: ct.splitLineColor } },
      },
      xAxis: {
        type: 'category', 
        data: labels,
        axisLine: { lineStyle: { color: ct.axisLineColor } },
        axisTick: { show: false },
        axisLabel: { color: ct.textColor, fontSize: 11 },
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: ct.splitLineColor } },
        axisLabel: { 
          color: ct.textColor, 
          fontSize: 11, 
          formatter: (v: number) => v > 1000 ? (v / 1000).toFixed(0) + 'K' : String(v) 
        },
      },
      series: [{
        name: 'Token',
        type: 'line',
        data: tokenVals,
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { color: colors[1], width: 2.5 },
        itemStyle: { color: colors[1], borderWidth: 2, borderColor: isDark ? '#0F172A' : '#FFFFFF' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: colors[1] + '30' },
            { offset: 1, color: colors[1] + '05' },
          ]),
        },
      }],
    }

    const elToken = tokenChartRef.current
    if (elToken) {
      if (chartInstRef.current) chartInstRef.current.dispose()
      chartInstRef.current = echarts.init(elToken, isDark ? 'dark' : undefined)
      chartInstRef.current.setOption(areaOption)
    }

    const handleResize = () => {
      chartInstRef.current?.resize()
    }
    window.addEventListener('resize', handleResize)
    return () => {
      window.removeEventListener('resize', handleResize)
      chartInstRef.current?.dispose()
    }
  }, [dailyData, loading, isDark, range])

  if (loading) {
    return (
      <div className="flex items-center justify-center h-full">
        <motion.div 
          className="w-8 h-8 border-2 border-purple-500/30 border-t-purple-500 rounded-full"
          animate={{ rotate: 360 }}
          transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
        />
      </div>
    )
  }

  const dailyPointsLimit = membership.dailyPermanentPoints > 0 ? membership.dailyPermanentPoints : membership.dailyTemporaryPoints
  const maxTokens = ((membership.dailyTemporaryPoints + membership.dailyPermanentPoints) / 1000).toFixed(0) + 'K'

  const statCards: StatCard[] = [
    { 
      label: lang === 'zh' ? '当前点数' : 'Current Points',
      value: userPoints,
      suffix: lang === 'zh' ? '点' : 'pts',
      icon: <PointsIcon size={20} color={membership.color} />,
      color: membership.color,
    },
    { 
      label: lang === 'zh' ? 'Token消耗' : 'Token Used',
      value: (tokensUsed / 1000).toFixed(1) + 'K',
      suffix: lang === 'zh' ? `/ ${maxTokens}/天` : `/ ${maxTokens}/day`,
      icon: (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke={colors[1]} strokeWidth="2">
          <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/>
        </svg>
      ),
      color: colors[1],
    },
    { 
      label: lang === 'zh' ? 'API调用' : 'API Calls',
      value: apiCallsCount,
      suffix: lang === 'zh' ? '次' : 'times',
      icon: (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke={colors[2]} strokeWidth="2">
          <polyline points="16 18 22 12 16 6"/>
          <polyline points="8 6 2 12 8 18"/>
        </svg>
      ),
      color: colors[2],
    },
  ]

  const containerVariants: Variants = {
    hidden: { opacity: 0 },
    visible: {
      opacity: 1,
      transition: {
        staggerChildren: 0.08,
      },
    },
  }

  const itemVariants: Variants = {
    hidden: { opacity: 0, y: 20 },
    visible: {
      opacity: 1,
      y: 0,
      transition: {
        duration: 0.5,
        ease: [0.22, 1, 0.36, 1] as const,
      },
    },
  }

  return (
    <motion.div 
      className="p-4 sm:p-6 lg:p-8 max-w-6xl mx-auto overflow-y-auto h-full"
      variants={containerVariants}
      initial="hidden"
      animate="visible"
    >
      {/* Header */}
      <motion.div variants={itemVariants} className="mb-8">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
              {lang === 'zh' ? '欢迎回来' : 'Welcome back'}
            </h1>
            <p className="text-sm mt-1" style={{ color: 'var(--text-dim)' }}>
              {lang === 'zh' ? '这是你的AI使用概览' : 'Here\'s your AI usage overview'}
            </p>
          </div>
          
          <div className="flex items-center gap-2 p-1 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
            {[{ k: '7d' as const, l: '7D' }, { k: '30d' as const, l: '30D' }].map(r => (
              <button 
                key={r.k} 
                onClick={() => setRange(r.k)} 
                className="px-4 py-2 rounded-lg text-xs font-semibold transition-all"
                style={{ 
                  background: range === r.k ? 'var(--accent-purple)' : 'transparent', 
                  color: range === r.k ? '#fff' : 'var(--text-dim)' 
                }}
              >
                {r.l}
              </button>
            ))}
          </div>
        </div>
      </motion.div>

      {/* Stats Grid */}
      <motion.div variants={itemVariants} className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
        {statCards.map((card, i) => (
          <motion.div
            key={i}
            className="relative rounded-2xl p-6 border overflow-hidden group"
            style={{ 
              background: 'var(--bg-card)', 
              borderColor: 'var(--border-color)',
            }}
            whileHover={{ y: -4, boxShadow: '0 20px 40px rgba(0,0,0,0.1)' }}
            transition={{ duration: 0.3, ease: [0.22, 1, 0.36, 1] }}
          >
            <div className="absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-300"
              style={{
                background: `radial-gradient(circle at top right, ${card.color}10, transparent 70%)`,
              }}
            />
            
            <div className="relative">
              <div className="flex items-center justify-between mb-4">
                <span className="text-xs font-medium" style={{ color: 'var(--text-dim)' }}>{card.label}</span>
                <div className="p-2 rounded-xl" style={{ background: card.color + '15' }}>
                  {card.icon}
                </div>
              </div>
              
              <div className="flex items-baseline gap-1">
                <span className="text-3xl font-bold" style={{ color: card.color }}>
                  {typeof card.value === 'number' ? <AnimatedCounter value={card.value} /> : card.value}
                </span>
                {card.suffix && (
                  <span className="text-xs font-medium" style={{ color: 'var(--text-dim)' }}>
                    {card.suffix}
                  </span>
                )}
              </div>
            </div>
          </motion.div>
        ))}
      </motion.div>

      {/* Points Progress */}
      {dailyPointsLimit > 0 && (
        <motion.div variants={itemVariants}
          className="rounded-2xl p-6 border mb-6" 
          style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}
        >
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-xl" style={{ background: membership.color + '15' }}>
                <PointsIcon size={18} color={membership.color} />
              </div>
              <div>
                <span className="text-sm font-semibold block" style={{ color: 'var(--text-primary)' }}>
                  {lang === 'zh' ? '剩余点数' : 'Remaining Points'}
                </span>
              </div>
            </div>
            <span className="text-2xl font-bold" style={{ color: membership.color }}>
              {Math.round(userPoints)}
            </span>
          </div>
        </motion.div>
      )}

      {/* Chart Section */}
      <motion.div variants={itemVariants} className="mb-6">
        <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
              {lang === 'zh' ? 'Token 消耗趋势' : 'Token Usage Trend'}
            </h3>
            <div className="flex items-center gap-2 text-xs" style={{ color: 'var(--text-dim)' }}>
              <span className="inline-block w-2 h-2 rounded-full" style={{ background: colors[1] }} />
              {lang === 'zh' ? 'Token' : 'Token'}
            </div>
          </div>
          <div ref={tokenChartRef} className="w-full" style={{ height: 280 }} />
        </div>
      </motion.div>

      {/* Quick Actions */}
      <motion.div variants={itemVariants} className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {[
          {
            title: lang === 'zh' ? '新建对话' : 'New Chat',
            desc: lang === 'zh' ? '开始与AI对话' : 'Start a conversation with AI',
            icon: (
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/>
              </svg>
            ),
            to: '/chat',
            color: '#8B5CF6',
          },
          {
            title: lang === 'zh' ? '上传文件' : 'Upload Files',
            desc: lang === 'zh' ? '分析文档内容' : 'Analyze document content',
            icon: (
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/>
                <polyline points="17 8 12 3 7 8"/>
                <line x1="12" y1="3" x2="12" y2="15"/>
              </svg>
            ),
            to: '/chat',
            color: '#3B82F6',
          },
          {
            title: lang === 'zh' ? '探索模型' : 'Explore Models',
            desc: lang === 'zh' ? '浏览可用AI模型' : 'Browse available AI models',
            icon: (
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M12 6v6l4 2"/>
              </svg>
            ),
            to: '/store',
            color: '#06B6D4',
          },
        ].map((action, i) => (
          <Link key={i} to={action.to}>
            <motion.div
              className="rounded-2xl p-6 border cursor-pointer group"
              style={{ 
                background: 'var(--bg-card)', 
                borderColor: 'var(--border-color)',
              }}
              whileHover={{ y: -4, scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              transition={{ duration: 0.3, ease: [0.22, 1, 0.36, 1] }}
            >
              <div className="flex items-start gap-4">
                <div 
                  className="p-3 rounded-xl transition-transform group-hover:scale-110"
                  style={{ 
                    background: action.color + '15',
                    color: action.color,
                  }}
                >
                  {action.icon}
                </div>
                <div className="flex-1">
                  <h4 className="text-sm font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>
                    {action.title}
                  </h4>
                  <p className="text-xs" style={{ color: 'var(--text-dim)' }}>
                    {action.desc}
                  </p>
                </div>
                <svg 
                  width="16" 
                  height="16" 
                  viewBox="0 0 24 24" 
                  fill="none" 
                  stroke="currentColor" 
                  strokeWidth="2"
                  className="opacity-0 group-hover:opacity-100 transition-opacity"
                  style={{ color: action.color }}
                >
                  <polyline points="9 18 15 12 9 6"/>
                </svg>
              </div>
            </motion.div>
          </Link>
        ))}
      </motion.div>
    </motion.div>
  )
}
