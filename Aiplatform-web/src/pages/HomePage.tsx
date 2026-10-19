import { useState, useEffect, useRef, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAppStore } from '@/stores/appStore'
import { modelApi } from '@/services/api'
import { motion, useInView, useScroll, useTransform } from 'framer-motion'
import * as echarts from 'echarts'
import { useThemeStore } from '@/stores/themeStore'
import { useI18nStore } from '@/stores/i18nStore'
import { providerDisplayName } from '@/components/ProviderIcon'
import type { PlatformModelSummary } from '@/types'

type ECharts = echarts.ECharts

function getChartColors(isDark: boolean): string[] {
  return isDark
    ? ['#8B5CF6', '#3B82F6', '#06B6D4', '#10B981', '#F59E0B', '#F43F5E']
    : ['#7C3AED', '#2563EB', '#0891B2', '#059669', '#D97706', '#E11D48']
}

function FadeIn({ children, delay = 0, className = '' }: { children: React.ReactNode; delay?: number; className?: string }) {
  const ref = useRef(null)
  const isInView = useInView(ref, { once: true, margin: '-80px' })
  return (
    <div ref={ref} className={className} style={{
      opacity: isInView ? 1 : 0,
      transform: isInView ? 'translateY(0)' : 'translateY(32px)',
      transition: `opacity 0.7s cubic-bezier(0.16,1,0.3,1) ${delay}s, transform 0.7s cubic-bezier(0.16,1,0.3,1) ${delay}s`,
    }}>
      {children}
    </div>
  )
}

function StaggerGroup({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  const ref = useRef(null)
  const isInView = useInView(ref, { once: true, margin: '-60px' })
  return (
    <div ref={ref} className={className}>
      {Array.isArray(children) ? children.map((child, i) => (
        <div key={i} style={{
          opacity: isInView ? 1 : 0,
          transform: isInView ? 'translateY(0)' : 'translateY(24px)',
          transition: `opacity 0.6s cubic-bezier(0.16,1,0.3,1) ${i * 0.08}s, transform 0.6s cubic-bezier(0.16,1,0.3,1) ${i * 0.08}s`,
        }}>
          {child}
        </div>
      )) : children}
    </div>
  )
}

const navLinks = [
  { labelZh: '功能', labelEn: 'Features', href: '#features' },
  { labelZh: '工作流', labelEn: 'Workflow', href: '#workflow' },
  { labelZh: '模型', labelEn: 'Models', href: '#models' },
  { labelZh: '定价', labelEn: 'Pricing', href: '#pricing' },
]

export default function HomePage() {
  const { user } = useAppStore()
  const { theme } = useThemeStore()
  const { lang, t } = useI18nStore()
  const navigate = useNavigate()
  const isDark = theme === 'dark'
  const colors = getChartColors(isDark)
  const [scrolled, setScrolled] = useState(false)
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)

  const [models, setModels] = useState<PlatformModelSummary[]>([])
  const chartRef = useRef<ECharts | null>(null)
  const chart2Ref = useRef<ECharts | null>(null)

  useEffect(() => {
    (async () => {
      try { const m = await modelApi.list(); setModels(m || []) } catch {}
    })()
  }, [])

  useEffect(() => {
    const h = () => setScrolled(window.scrollY > 20)
    window.addEventListener('scroll', h, { passive: true })
    return () => window.removeEventListener('scroll', h)
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
        type: 'line', data: [3,5,8,12,18,25,30,28,22,15,10,6,4,3,5,9,15,22,28,32,27,20,12,7],
        smooth: true, symbol: 'none', lineStyle: { width: 2, color: colors[0] },
        areaStyle: { color: new echarts.graphic.LinearGradient(0,0,0,1, [{offset:0,color:colors[0]+'40'},{offset:1,color:colors[0]+'00'}]) },
      }],
    })
    chartRef.current = chart
    return () => { chart?.dispose() }
  }, [isDark])

  useEffect(() => {
    const el = document.getElementById('home-bar-chart')
    if (!el) return
    if (chart2Ref.current) chart2Ref.current.dispose()
    const chart = echarts.init(el, isDark ? 'dark' : 'light')
    chart.setOption({
      backgroundColor: 'transparent',
      tooltip: { trigger: 'axis' },
      grid: { top: 10, right: 10, bottom: 20, left: 35 },
      xAxis: { type: 'category', data: ['Mon','Tue','Wed','Thu','Fri','Sat','Sun'], axisLine: { lineStyle: { color: isDark ? '#334155' : '#CBD5E1' } }, axisLabel: { color: isDark ? '#94A3B8' : '#64748B', fontSize: 10 } },
      yAxis: { type: 'value', splitLine: { lineStyle: { color: isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)' } }, axisLabel: { color: isDark ? '#94A3B8' : '#64748B', fontSize: 10 } },
      series: [{
        type: 'bar', data: [120,200,150,80,70,110,130],
        itemStyle: { color: new echarts.graphic.LinearGradient(0,0,0,1, [{offset:0,color:colors[0]},{offset:1,color:colors[0]+'30'}]), borderRadius: [6,6,0,0] },
        barWidth: 16,
      }],
    })
    chart2Ref.current = chart
    return () => { chart?.dispose() }
  }, [isDark])

  const scrollTo = useCallback((id: string) => {
    setMobileMenuOpen(false)
    const el = document.querySelector(id)
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, [])

  return (
    <div className="page-scroll overflow-y-auto" style={{ background: 'var(--bg-primary)' }}>
      {/* ============ STICKY HEADER ============ */}
      <header className="fixed top-0 left-0 right-0 z-50 transition-all duration-300" style={{
        background: scrolled ? (isDark ? 'rgba(15,15,20,0.85)' : 'rgba(255,255,255,0.85)') : 'transparent',
        backdropFilter: scrolled ? 'blur(16px)' : 'none',
        borderBottom: scrolled ? `1px solid ${isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)'}` : '1px solid transparent',
      }}>
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-10 h-14 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 rounded-lg flex items-center justify-center" style={{ background: 'linear-gradient(135deg, #6366F1, #3B82F6)' }}>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/>
              </svg>
            </div>
            <span className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>
              {lang === 'zh' ? '凌点 AI' : 'Lingdian AI'}
            </span>
          </div>

          <nav className="hidden md:flex items-center gap-6">
            {navLinks.map((l, i) => (
              <button key={i} onClick={() => scrollTo(l.href)}
                className="text-[13px] font-medium transition-colors hover:text-[var(--accent-purple)]"
                style={{ color: 'var(--text-secondary)' }}>
                {lang === 'zh' ? l.labelZh : l.labelEn}
              </button>
            ))}
          </nav>

          <div className="flex items-center gap-3">
            {user ? (
              <motion.button onClick={() => navigate('/chat')}
                className="hidden sm:inline-flex px-4 py-1.5 rounded-lg text-xs font-semibold text-white transition-all"
                style={{ background: 'linear-gradient(135deg, #8B5CF6, #6366F1)', boxShadow: '0 2px 12px rgba(139,92,246,0.3)' }}
                whileHover={{ boxShadow: '0 4px 20px rgba(139,92,246,0.45)' }}
                whileTap={{ scale: 0.97 }}>
                {lang === 'zh' ? '进入控制台' : 'Dashboard'}
              </motion.button>
            ) : (
              <>
                <button onClick={() => navigate('/login')}
                  className="hidden sm:inline-flex px-4 py-1.5 rounded-lg text-xs font-medium transition-colors"
                  style={{ color: 'var(--text-secondary)' }}>
                  {lang === 'zh' ? '登录' : 'Sign In'}
                </button>
                <motion.button onClick={() => navigate('/register')}
                  className="hidden sm:inline-flex px-4 py-1.5 rounded-lg text-xs font-semibold text-white transition-all"
                  style={{ background: 'linear-gradient(135deg, #8B5CF6, #6366F1)' }}
                  whileHover={{ boxShadow: '0 4px 20px rgba(139,92,246,0.4)' }}
                  whileTap={{ scale: 0.97 }}>
                  {lang === 'zh' ? '免费开始' : 'Get Started'}
                </motion.button>
              </>
            )}
            <button onClick={() => setMobileMenuOpen(!mobileMenuOpen)} className="md:hidden p-1.5 rounded-lg" style={{ color: 'var(--text-secondary)' }}>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                {mobileMenuOpen ? <><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></> : <><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></>}
              </svg>
            </button>
          </div>
        </div>

        {mobileMenuOpen && (
          <div className="md:hidden px-4 pb-4 border-t" style={{ borderColor: 'var(--border-color)' }}>
            <div className="flex flex-col gap-1 pt-3">
              {navLinks.map((l, i) => (
                <button key={i} onClick={() => scrollTo(l.href)}
                  className="text-left px-3 py-2 rounded-lg text-sm font-medium transition-colors hover:bg-[var(--hover-bg)]"
                  style={{ color: 'var(--text-secondary)' }}>
                  {lang === 'zh' ? l.labelZh : l.labelEn}
                </button>
              ))}
              {!user && (
                <div className="flex gap-2 mt-2 pt-3 border-t" style={{ borderColor: 'var(--border-color)' }}>
                  <button onClick={() => { navigate('/login'); setMobileMenuOpen(false) }}
                    className="flex-1 py-2 rounded-lg text-xs font-medium text-center" style={{ color: 'var(--text-secondary)', border: '1px solid var(--border-color)' }}>
                    {lang === 'zh' ? '登录' : 'Sign In'}
                  </button>
                  <button onClick={() => { navigate('/register'); setMobileMenuOpen(false) }}
                    className="flex-1 py-2 rounded-lg text-xs font-semibold text-white text-center" style={{ background: 'linear-gradient(135deg, #8B5CF6, #6366F1)' }}>
                    {lang === 'zh' ? '免费开始' : 'Get Started'}
                  </button>
                </div>
              )}
            </div>
          </div>
        )}
      </header>

      {/* ============ HERO SECTION ============ */}
      <section className="relative min-h-screen flex items-center justify-center overflow-hidden pt-14">
        {/* Background grid */}
        <div className="absolute inset-0 pointer-events-none" style={{
          backgroundImage: isDark
            ? 'linear-gradient(rgba(255,255,255,0.02) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.02) 1px, transparent 1px)'
            : 'linear-gradient(rgba(0,0,0,0.03) 1px, transparent 1px), linear-gradient(90deg, rgba(0,0,0,0.03) 1px, transparent 1px)',
          backgroundSize: '60px 60px',
        }} />

        {/* Ambient glow */}
        <div className="absolute top-1/4 left-1/2 -translate-x-1/2 w-[800px] h-[500px] rounded-full opacity-[0.08] blur-[120px] pointer-events-none" style={{ background: 'linear-gradient(135deg, #8B5CF6, #3B82F6)' }} />
        <div className="absolute bottom-0 left-0 w-[400px] h-[400px] rounded-full opacity-[0.04] blur-[100px] pointer-events-none" style={{ background: '#06B6D4' }} />
        <div className="absolute top-20 right-20 w-[300px] h-[300px] rounded-full opacity-[0.03] blur-[80px] pointer-events-none" style={{ background: '#10B981' }} />

        <div className="relative z-10 max-w-4xl mx-auto px-4 sm:px-6 text-center">
          <FadeIn>
            <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full mb-6 text-[11px] font-medium" style={{
              background: isDark ? 'rgba(139,92,246,0.1)' : 'rgba(139,92,246,0.06)',
              border: `1px solid ${isDark ? 'rgba(139,92,246,0.2)' : 'rgba(139,92,246,0.12)'}`,
              color: colors[0],
            }}>
              <span className="w-1.5 h-1.5 rounded-full animate-pulse" style={{ background: colors[0] }} />
              {lang === 'zh' ? '凌点 AI · 新一代模型调度网关' : 'Lingdian AI · Next-Gen Model Gateway'}
            </div>
          </FadeIn>

          <FadeIn delay={0.1}>
            <h1 className="text-3xl sm:text-4xl md:text-5xl lg:text-6xl font-extrabold tracking-tight mb-6 leading-[1.1]" style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-display)' }}>
              {lang === 'zh' ? '一个 API，调用所有 AI 模型' : 'One API for Every AI Model'}
              <br />
              <span className="text-gradient">{lang === 'zh' ? '高性能 · 低延迟 · 零门槛' : 'High Performance · Low Latency · Zero Friction'}</span>
            </h1>
          </FadeIn>

          <FadeIn delay={0.2}>
            <p className="text-sm sm:text-base md:text-lg max-w-2xl mx-auto mb-10 leading-relaxed" style={{ color: 'var(--text-dim)' }}>
              {lang === 'zh'
                ? '统一接入 OpenAI、DeepSeek、Claude 等主流模型。智能路由、自动降级、实时计费，让 AI 调用像呼吸一样自然。'
                : 'Unified access to OpenAI, DeepSeek, Claude and more. Smart routing, auto-failover, real-time billing — AI calls as natural as breathing.'}
            </p>
          </FadeIn>

          <FadeIn delay={0.3}>
            <div className="flex flex-col sm:flex-row items-center justify-center gap-3">
              <motion.button onClick={() => navigate(user ? '/chat' : '/register')}
                className="px-8 py-3.5 rounded-xl text-sm font-semibold text-white transition-all w-full sm:w-auto"
                style={{ background: 'linear-gradient(135deg, #8B5CF6, #6366F1)', boxShadow: '0 4px 24px rgba(139,92,246,0.35)' }}
                whileHover={{ boxShadow: '0 8px 40px rgba(139,92,246,0.5)', y: -2 }}
                whileTap={{ scale: 0.97 }}>
                {lang === 'zh' ? '免费开始使用 →' : 'Start Free →'}
              </motion.button>
              <motion.button onClick={() => scrollTo('#features')}
                className="px-8 py-3.5 rounded-xl text-sm font-semibold transition-all w-full sm:w-auto"
                style={{ background: 'var(--bg-card)', border: '1px solid var(--border-color)', color: 'var(--text-secondary)' }}
                whileHover={{ borderColor: 'var(--border-accent)', y: -1 }}
                whileTap={{ scale: 0.97 }}>
                {lang === 'zh' ? '了解更多' : 'Learn More'}
              </motion.button>
            </div>
          </FadeIn>

          <FadeIn delay={0.5}>
            <div className="mt-16 flex flex-col items-center gap-2">
              <span className="text-[11px]" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '向下滚动探索更多' : 'Scroll to explore'}</span>
              <div className="w-5 h-8 rounded-full border-2 flex items-start justify-center pt-1.5" style={{ borderColor: 'var(--text-dim)' }}>
                <div className="w-1 h-2 rounded-full animate-bounce" style={{ background: 'var(--text-dim)' }} />
              </div>
            </div>
          </FadeIn>
        </div>
      </section>

      {/* ============ SOCIAL PROOF ============ */}
      <section className="py-16 border-t" style={{ borderColor: 'var(--border-color)' }}>
        <div className="max-w-5xl mx-auto px-4 sm:px-6 text-center">
          <FadeIn>
            <p className="text-[11px] font-medium uppercase tracking-widest mb-8" style={{ color: 'var(--text-dim)' }}>
              {lang === 'zh' ? '接入主流 AI 模型与平台' : 'Powered by Leading AI Platforms'}
            </p>
          </FadeIn>
          <StaggerGroup className="flex flex-wrap items-center justify-center gap-8 md:gap-12">
            {['OpenAI', 'DeepSeek', 'Claude', 'Gemini', 'Mistral', 'Qwen'].map((name, i) => (
              <div key={i} className="text-lg font-bold tracking-wide transition-opacity hover:opacity-100" style={{
                color: 'var(--text-dim)',
                opacity: 0.4,
              }}>
                {name}
              </div>
            ))}
          </StaggerGroup>
        </div>
      </section>

      {/* ============ FEATURES ============ */}
      <section id="features" className="py-20 sm:py-28">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-10">
          <FadeIn className="text-center mb-14">
            <span className="text-[11px] font-semibold uppercase tracking-widest mb-3 block" style={{ color: colors[0] }}>
              {lang === 'zh' ? '核心能力' : 'Core Capabilities'}
            </span>
            <h2 className="text-2xl sm:text-3xl font-bold mb-3" style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-display)' }}>
              {lang === 'zh' ? '为 AI 调用而生的完整基础设施' : 'Complete Infrastructure for AI Calls'}
            </h2>
            <p className="text-sm max-w-lg mx-auto" style={{ color: 'var(--text-dim)' }}>
              {lang === 'zh' ? '从路由调度到安全防护，从精确计费到数据分析，一站式解决所有 AI 集成需求' : 'From routing to security, billing to analytics — everything you need in one platform'}
            </p>
          </FadeIn>

          <StaggerGroup className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {[
              {
                icon: 'M13 10V3L4 14h7v7l9-11h-7z',
                titleZh: '智能路由调度', titleEn: 'Smart Routing',
                descZh: '自动选择最优模型路径，支持权重分配、负载均衡、智能降级，确保每次调用都高效可靠',
                descEn: 'Auto-select optimal model paths with weighted distribution, load balancing, and smart failover',
                tagsZh: ['动态切换', '负载均衡', '降级策略'], tagsEn: ['Dynamic', 'Load Balance', 'Failover'],
                color: colors[0],
              },
              {
                icon: 'M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z',
                titleZh: '安全网关防护', titleEn: 'Security Gateway',
                descZh: 'IP 级别限流、API Key 鉴权、参数安全过滤、攻击检测与防御，全方位保护你的 AI 调用',
                descEn: 'IP rate limiting, API key auth, parameter filtering, attack detection — full-spectrum protection',
                tagsZh: ['Token Bucket', 'API 鉴权', '攻击防御'], tagsEn: ['Token Bucket', 'API Auth', 'Defense'],
                color: colors[1],
              },
              {
                icon: 'M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z',
                titleZh: '精确计费引擎', titleEn: 'Precision Billing',
                descZh: '基于 Redis Lua 的原子性计费，按模型规则精确扣点，消费明细透明可追溯，每一点数都清清楚楚',
                descEn: 'Atomic billing via Redis Lua, per-token precision, transparent and traceable spending',
                tagsZh: ['原子扣点', '点数余额', '消费追溯'], tagsEn: ['Atomic', 'Points', 'Traceable'],
                color: colors[3],
              },
              {
                icon: 'M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z',
                titleZh: '实时数据分析', titleEn: 'Real-time Analytics',
                descZh: '可视化用量仪表盘，支持多维度筛选、趋势分析、异常告警，帮助你优化 AI 调用策略',
                descEn: 'Visual dashboard with multi-dimensional filtering, trend analysis, and anomaly alerts',
                tagsZh: ['可视化', '趋势分析', '异常告警'], tagsEn: ['Visual', 'Trends', 'Alerts'],
                color: colors[2],
              },
              {
                icon: 'M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4',
                titleZh: '流式对话引擎', titleEn: 'Streaming Engine',
                descZh: '120FPS 零卡顿流式输出，支持 SSE/WebSocket 双协议，Pretext 渲染引擎确保极致流畅体验',
                descEn: '120FPS zero-lag streaming with SSE/WebSocket, Pretext rendering for ultra-smooth experience',
                tagsZh: ['120FPS', 'SSE/WS', 'Pretext 渲染'], tagsEn: ['120FPS', 'SSE/WS', 'Pretext'],
                color: colors[4],
              },
              {
                icon: 'M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4',
                titleZh: '开发者友好 API', titleEn: 'Developer-First API',
                descZh: '完全兼容 OpenAI SDK，零学习成本接入。完善的文档、SDK 和示例代码，5 分钟即可集成',
                descEn: 'Fully OpenAI SDK compatible. Zero learning curve. Docs, SDKs, and examples — integrate in 5 minutes',
                tagsZh: ['OpenAI 兼容', '零学习成本', '5 分钟集成'], tagsEn: ['OpenAI Compatible', 'Zero Curve', '5-min Setup'],
                color: colors[5],
              },
            ].map((f, i) => (
              <div key={i} className="group rounded-2xl p-6 border transition-all duration-300 hover:border-[var(--border-accent)]" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                <div className="w-10 h-10 rounded-xl flex items-center justify-center mb-4 transition-transform duration-300 group-hover:scale-110" style={{ background: `${f.color}12` }}>
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={f.color} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d={f.icon}/></svg>
                </div>
                <h3 className="text-sm font-bold mb-1.5" style={{ color: 'var(--text-primary)' }}>
                  {lang === 'zh' ? f.titleZh : f.titleEn}
                </h3>
                <p className="text-[12px] leading-relaxed mb-4" style={{ color: 'var(--text-dim)' }}>
                  {lang === 'zh' ? f.descZh : f.descEn}
                </p>
                <div className="flex flex-wrap gap-1.5">
                  {(lang === 'zh' ? f.tagsZh : f.tagsEn).map((tag, j) => (
                    <span key={j} className="px-2 py-0.5 rounded-md text-[10px] font-medium" style={{ background: `${f.color}08`, color: f.color, border: `1px solid ${f.color}15` }}>
                      {tag}
                    </span>
                  ))}
                </div>
              </div>
            ))}
          </StaggerGroup>
        </div>
      </section>

      {/* ============ WORKFLOW ============ */}
      <section id="workflow" className="py-20 sm:py-28" style={{ background: isDark ? 'rgba(255,255,255,0.01)' : 'rgba(0,0,0,0.01)' }}>
        <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-10">
          <FadeIn className="text-center mb-14">
            <span className="text-[11px] font-semibold uppercase tracking-widest mb-3 block" style={{ color: colors[1] }}>
              {lang === 'zh' ? '工作流程' : 'How It Works'}
            </span>
            <h2 className="text-2xl sm:text-3xl font-bold mb-3" style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-display)' }}>
              {lang === 'zh' ? '三步开始使用' : 'Get Started in 3 Steps'}
            </h2>
          </FadeIn>

          <StaggerGroup className="space-y-12">
            {[
              { step: '01', titleZh: '创建 API Key', titleEn: 'Create API Key', descZh: '注册账户后，在控制台一键生成你的专属 API Key，支持多 Key 管理和权限控制', descEn: 'Register and generate your专属 API Key in one click, with multi-key management and permission control' },
              { step: '02', titleZh: '配置模型路由', titleEn: 'Configure Routing', descZh: '选择目标模型，设置权重和优先级，开启智能降级策略，让系统自动选择最优路径', descEn: 'Select models, set weights and priorities, enable smart failover for automatic optimal path selection' },
              { step: '03', titleZh: '开始调用', titleEn: 'Start Calling', descZh: '使用兼容 OpenAI SDK 的方式发起调用，享受智能路由、实时计费和流式输出的完整体验', descEn: 'Call using OpenAI-compatible SDK, enjoy smart routing, real-time billing, and streaming output' },
            ].map((s, i) => (
              <div key={i} className="flex gap-6 md:gap-10 items-start">
                <div className="flex-shrink-0 w-12 h-12 rounded-2xl flex items-center justify-center text-sm font-bold" style={{
                  background: `linear-gradient(135deg, ${colors[i % colors.length]}, ${colors[(i + 1) % colors.length]})`,
                  color: '#fff',
                }}>
                  {s.step}
                </div>
                <div className="flex-1 pt-1">
                  <h3 className="text-base font-bold mb-1.5" style={{ color: 'var(--text-primary)' }}>
                    {lang === 'zh' ? s.titleZh : s.titleEn}
                  </h3>
                  <p className="text-sm leading-relaxed" style={{ color: 'var(--text-dim)' }}>
                    {lang === 'zh' ? s.descZh : s.descEn}
                  </p>
                </div>
              </div>
            ))}
          </StaggerGroup>
        </div>
      </section>

      {/* ============ MODELS ============ */}
      <section id="models" className="py-20 sm:py-28">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-10">
          <FadeIn className="text-center mb-14">
            <span className="text-[11px] font-semibold uppercase tracking-widest mb-3 block" style={{ color: colors[2] }}>
              {lang === 'zh' ? '模型生态' : 'Model Ecosystem'}
            </span>
            <h2 className="text-2xl sm:text-3xl font-bold mb-3" style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-display)' }}>
              {lang === 'zh'
                ? `已接入 ${models.filter(m => m.chatEnabled).length} 款 AI 模型`
                : `${models.filter(m => m.chatEnabled).length} AI models connected`}
            </h2>
            <p className="text-sm max-w-lg mx-auto" style={{ color: 'var(--text-dim)' }}>
              {lang === 'zh' ? '模型服务接入后即可在对话工作区中使用' : 'Connected models are ready to use in the chat workspace'}
            </p>
          </FadeIn>

          {models.length > 0 && (
            models.some(m => m.chatEnabled) ? (
            <StaggerGroup className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3">
              {models.filter(m => m.chatEnabled).slice(0, 12).map((m) => (
                <div key={m.id} className="group rounded-xl p-4 border transition-all duration-200 hover:border-[var(--border-accent)]" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                  <div className="text-xs font-semibold mb-1 truncate" style={{ color: 'var(--text-primary)' }}>{m.displayName}</div>
                  <div className="text-[10px]" style={{ color: 'var(--text-dim)' }}>
                    {m.provider ? providerDisplayName(m.provider) : lang === 'zh' ? '通用模型' : 'General Model'}
                  </div>
                </div>
              ))}
            </StaggerGroup>
            ) : (
              <div className="rounded-xl border p-5 text-center text-sm" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)', color: 'var(--text-dim)' }}>
                {lang === 'zh' ? '模型服务正在配置中，暂时没有可用模型。' : 'Model services are being configured; no models are available yet.'}
              </div>
            )
          )}
        </div>
      </section>

      {/* ============ STATS ============ */}
      <section className="py-20 sm:py-28" style={{ background: isDark ? 'rgba(255,255,255,0.01)' : 'rgba(0,0,0,0.01)' }}>
        <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-10">
          <FadeIn className="text-center mb-14">
            <span className="text-[11px] font-semibold uppercase tracking-widest mb-3 block" style={{ color: colors[3] }}>
              {lang === 'zh' ? '平台数据' : 'Platform Stats'}
            </span>
            <h2 className="text-2xl sm:text-3xl font-bold mb-3" style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-display)' }}>
              {lang === 'zh' ? '用数据说话' : 'Numbers That Speak'}
            </h2>
          </FadeIn>

          <StaggerGroup className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            {[
              { labelZh: '日均调用', labelEn: 'Daily Calls', value: '120K+', icon: 'M13 10V3L4 14h7v7l9-11h-7z', color: colors[0] },
              { labelZh: '平均延迟', labelEn: 'Avg Latency', value: '<80ms', icon: 'M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z', color: colors[1] },
              { labelZh: '服务可用率', labelEn: 'Uptime', value: '99.9%', icon: 'M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z', color: colors[3] },
              { labelZh: '接入模型', labelEn: 'Models', value: '20+', icon: 'M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4', color: colors[2] },
            ].map((s, i) => (
              <div key={i} className="rounded-2xl p-6 border text-center" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                <div className="w-10 h-10 rounded-xl flex items-center justify-center mx-auto mb-3" style={{ background: `${s.color}12` }}>
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={s.color} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d={s.icon}/></svg>
                </div>
                <div className="text-2xl font-extrabold mb-1" style={{ color: s.color }}>{s.value}</div>
                <div className="text-[11px]" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? s.labelZh : s.labelEn}</div>
              </div>
            ))}
          </StaggerGroup>
        </div>
      </section>

      {/* ============ USAGE CHART ============ */}
      <section className="py-20 sm:py-28">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-10">
          <FadeIn className="text-center mb-10">
            <span className="text-[11px] font-semibold uppercase tracking-widest mb-3 block" style={{ color: colors[0] }}>
              {lang === 'zh' ? '用量可视化' : 'Usage Visualization'}
            </span>
            <h2 className="text-xl sm:text-2xl font-bold mb-2" style={{ color: 'var(--text-primary)' }}>
              {lang === 'zh' ? '实时追踪你的 AI 调用' : 'Track Your AI Calls in Real-Time'}
            </h2>
          </FadeIn>

          <FadeIn delay={0.15}>
            <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
              <div className="flex items-center justify-between mb-4">
                <div>
                  <div className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>
                    {lang === 'zh' ? '本周用量趋势' : 'Weekly Trend'}
                  </div>
                  <div className="text-[11px]" style={{ color: 'var(--text-dim)' }}>
                    {lang === 'zh' ? 'API 调用次数' : 'API Calls'}
                  </div>
                </div>
                <span className="text-[10px] px-2 py-1 rounded-full font-medium" style={{ background: colors[0]+'12', color: colors[0] }}>
                  {lang === 'zh' ? '实时' : 'Live'}
                </span>
              </div>
              <div id="home-bar-chart" style={{ width: '100%', height: 220 }} />
            </div>
          </FadeIn>
        </div>
      </section>

      {/* ============ FINAL CTA ============ */}
      <section className="py-20 sm:py-28 relative overflow-hidden">
        <div className="absolute inset-0 pointer-events-none">
          <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[400px] rounded-full opacity-[0.06] blur-[100px]" style={{ background: 'linear-gradient(135deg, #8B5CF6, #3B82F6)' }} />
        </div>

        <div className="relative z-10 max-w-3xl mx-auto px-4 sm:px-6 text-center">
          <FadeIn>
            <h2 className="text-2xl sm:text-3xl lg:text-4xl font-bold mb-4" style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-display)' }}>
              {lang === 'zh' ? '准备好开始了吗？' : 'Ready to Get Started?'}
            </h2>
          </FadeIn>
          <FadeIn delay={0.1}>
            <p className="text-sm sm:text-base mb-8 max-w-lg mx-auto" style={{ color: 'var(--text-dim)' }}>
              {lang === 'zh'
                ? '注册即可免费获得月度点数，无需信用卡。5 分钟完成集成，立即体验智能 AI 调度。'
                : 'Sign up for free monthly points, no credit card required. Integrate in 5 minutes and start smart AI routing today.'}
            </p>
          </FadeIn>
          <FadeIn delay={0.2}>
            <div className="flex flex-col sm:flex-row items-center justify-center gap-3">
              <motion.button onClick={() => navigate(user ? '/chat' : '/register')}
                className="px-8 py-3.5 rounded-xl text-sm font-semibold text-white transition-all w-full sm:w-auto"
                style={{ background: 'linear-gradient(135deg, #8B5CF6, #6366F1)', boxShadow: '0 4px 24px rgba(139,92,246,0.35)' }}
                whileHover={{ boxShadow: '0 8px 40px rgba(139,92,246,0.5)', y: -2 }}
                whileTap={{ scale: 0.97 }}>
                {user ? (lang === 'zh' ? '进入控制台 →' : 'Go to Dashboard →') : (lang === 'zh' ? '免费注册 →' : 'Sign Up Free →')}
              </motion.button>
              <motion.button onClick={() => navigate('/store')}
                className="px-8 py-3.5 rounded-xl text-sm font-semibold transition-all w-full sm:w-auto"
                style={{ background: 'var(--bg-card)', border: '1px solid var(--border-color)', color: 'var(--text-secondary)' }}
                whileHover={{ borderColor: 'var(--border-accent)', y: -1 }}
                whileTap={{ scale: 0.97 }}>
                {lang === 'zh' ? '浏览模型' : 'Browse Models'}
              </motion.button>
            </div>
          </FadeIn>
        </div>
      </section>

      {/* ============ FOOTER ============ */}
      <footer className="py-10 border-t" style={{ borderColor: 'var(--border-color)' }}>
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-10 flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <div className="w-6 h-6 rounded-md flex items-center justify-center" style={{ background: 'linear-gradient(135deg, #6366F1, #3B82F6)' }}>
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.2"><path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/></svg>
            </div>
            <span className="text-xs font-medium" style={{ color: 'var(--text-dim)' }}>
              © 2025 {lang === 'zh' ? '凌点 AI. 保留所有权利。' : 'Lingdian AI. All rights reserved.'}
            </span>
          </div>
          <div className="flex items-center gap-4">
            {['文档', 'API', '状态', '隐私'].map((l, i) => (
              <span key={i} className="text-[11px]" style={{ color: 'var(--text-dim)' }}>
                {lang === 'zh' ? l : ['Docs', 'API', 'Status', 'Privacy'][i]}
              </span>
            ))}
          </div>
        </div>
      </footer>
    </div>
  )
}
