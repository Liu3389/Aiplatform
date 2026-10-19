import { Link, useNavigate } from 'react-router-dom'
import {
  motion,
  useMotionValue,
  useTransform,
  AnimatePresence,
} from 'framer-motion'
import { useRef, useState, useEffect, useCallback, useMemo } from 'react'
import { useThemeStore } from '@/stores/themeStore'
import { useI18nStore } from '@/stores/i18nStore'
import { useAppStore } from '@/stores/appStore'
import PageTransition from '@/components/PageTransition'
import { getUserDisplayName } from '@/utils/userDisplay'

// ===================== Hooks =====================

function useMousePosition() {
  const [pos, setPos] = useState({ x: 0.5, y: 0.5 })
  useEffect(() => {
    const handler = (e: MouseEvent) =>
      setPos({ x: e.clientX / window.innerWidth, y: e.clientY / window.innerHeight })
    window.addEventListener('mousemove', handler)
    return () => window.removeEventListener('mousemove', handler)
  }, [])
  return pos
}

function useCountUp(end: number, duration: number = 2, start: boolean = true) {
  const [count, setCount] = useState(0)
  useEffect(() => {
    if (!start) return
    let startTime: number
    let animationFrame: number
    const animate = (timestamp: number) => {
      if (!startTime) startTime = timestamp
      const progress = Math.min((timestamp - startTime) / (duration * 1000), 1)
      setCount(Math.floor(progress * end))
      if (progress < 1) {
        animationFrame = requestAnimationFrame(animate)
      }
    }
    animationFrame = requestAnimationFrame(animate)
    return () => cancelAnimationFrame(animationFrame)
  }, [end, duration, start])
  return count
}

// ===================== Components =====================

function FloatingOrb({ delay, size, color, x, y }: {
  delay: number; size: number; color: string; x: number; y: number
}) {
  return (
    <motion.div
      className="absolute rounded-full blur-3xl pointer-events-none"
      style={{ width: size, height: size, background: color, left: `${x}%`, top: `${y}%` }}
      animate={{ y: [0, -40, 0], x: [0, 20, 0], opacity: [0.3, 0.6, 0.3] }}
      transition={{ duration: 20 + delay * 3, delay, repeat: Infinity, ease: 'easeInOut' }}
    />
  )
}

function ParticleField({ isDark }: { isDark: boolean }) {
  const particles = useMemo(() => 
    Array.from({ length: 30 }, (_, i) => ({
      id: i,
      x: Math.random() * 100,
      y: Math.random() * 100,
      size: Math.random() * 3 + 1,
      duration: Math.random() * 10 + 10,
      delay: Math.random() * 5,
    })), []
  )

  return (
    <div className="absolute inset-0 overflow-hidden pointer-events-none">
      {particles.map(p => (
        <motion.div
          key={p.id}
          className="absolute rounded-full"
          style={{
            width: p.size,
            height: p.size,
            left: `${p.x}%`,
            top: `${p.y}%`,
            background: isDark ? 'rgba(255,255,255,0.3)' : 'rgba(0,0,0,0.2)',
          }}
          animate={{
            y: [0, -100, 0],
            x: [0, Math.random() * 40 - 20, 0],
            opacity: [0, 0.6, 0],
          }}
          transition={{
            duration: p.duration,
            delay: p.delay,
            repeat: Infinity,
            ease: 'linear',
          }}
        />
      ))}
    </div>
  )
}

function TiltCard({ children, isDark, delay = 0, className = '', glowColor = 'rgba(139,92,246,0.15)' }: {
  children: React.ReactNode; isDark: boolean; delay?: number; className?: string; glowColor?: string
}) {
  const ref = useRef<HTMLDivElement>(null)
  const x = useMotionValue(0)
  const y = useMotionValue(0)
  const rotateX = useTransform(y, [-0.5, 0.5], [8, -8])
  const rotateY = useTransform(x, [-0.5, 0.5], [-8, 8])
  const glareOpacity = useMotionValue(0)
  const glareX = useMotionValue(50)
  const glareY = useMotionValue(50)

  const handleMouseMove = useCallback((e: React.MouseEvent<HTMLDivElement>) => {
    if (!ref.current) return
    const rect = ref.current.getBoundingClientRect()
    const nx = (e.clientX - rect.left) / rect.width - 0.5
    const ny = (e.clientY - rect.top) / rect.height - 0.5
    x.set(nx)
    y.set(ny)
    glareX.set(nx * 100 + 50)
    glareY.set(ny * 100 + 50)
    glareOpacity.set(0.12)
  }, [x, y, glareX, glareY, glareOpacity])

  const handleMouseLeave = useCallback(() => {
    x.set(0)
    y.set(0)
    glareOpacity.set(0)
  }, [x, y, glareOpacity])

  return (
    <motion.div
      ref={ref}
      initial={{ opacity: 0, y: 40 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: '-60px' }}
      transition={{ duration: 0.7, delay, ease: [0.16, 1, 0.3, 1] }}
      style={{ perspective: '800px', transformStyle: 'preserve-3d' }}
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
      className={className}
    >
      <motion.div
        style={{
          rotateX,
          rotateY,
          transformStyle: 'preserve-3d',
          background: isDark ? 'rgba(255,255,255,0.02)' : 'rgba(255,255,255,0.7)',
          border: `1px solid ${isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)'}`,
          boxShadow: isDark ? `0 0 0 1px rgba(255,255,255,0.05), 0 20px 40px rgba(0,0,0,0.3)` : `0 0 0 1px rgba(0,0,0,0.05), 0 20px 40px rgba(0,0,0,0.08)`,
        }}
        className="relative rounded-3xl p-6 transition-all duration-300"
      >
        <motion.div
          className="pointer-events-none absolute inset-0 rounded-3xl"
          style={{
            background: `radial-gradient(circle at ${glareX}% ${glareY}%, rgba(255,255,255,0.9), transparent 50%)`,
            opacity: glareOpacity,
          }}
        />
        <motion.div
          className="pointer-events-none absolute -inset-1 rounded-3xl -z-10 blur-xl"
          style={{
            background: glowColor,
            opacity: useTransform(glareOpacity, [0, 0.12], [0, 0.5]),
          }}
        />
        {children}
      </motion.div>
    </motion.div>
  )
}

function GlowButton({ children, onClick, color = '#8B5CF6', size = 'md' }: {
  children: React.ReactNode; onClick?: () => void; color?: string; size?: 'sm' | 'md' | 'lg'
}) {
  const sizeClasses = {
    sm: 'px-5 py-2 text-xs',
    md: 'px-8 py-3.5 text-sm',
    lg: 'px-10 py-4 text-base',
  }

  return (
    <motion.button
      onClick={onClick}
      className={`relative rounded-2xl font-semibold text-white overflow-hidden ${sizeClasses[size]}`}
      style={{
        background: `linear-gradient(135deg, ${color}, ${color}dd)`,
        boxShadow: `0 0 40px ${color}40, 0 8px 32px ${color}30`,
      }}
      whileHover={{ scale: 1.03, boxShadow: `0 0 60px ${color}50, 0 12px 40px ${color}40` }}
      whileTap={{ scale: 0.98 }}
      transition={{ duration: 0.2 }}
    >
      <span className="relative z-10">{children}</span>
      <motion.div
        className="absolute inset-0"
        style={{ background: `linear-gradient(135deg, ${color}ee, ${color})` }}
        initial={false}
        animate={{ opacity: 0 }}
        whileHover={{ opacity: 1 }}
        transition={{ duration: 0.2 }}
      />
    </motion.button>
  )
}

// ===================== Data =====================

const PLATFORM_MODELS = [
  { name: 'GPT-4o', provider: 'OpenAI', tier: 'Pro', color: '#10A37F', icon: 'M12 2a10 10 0 100 20 10 10 0 000-20z' },
  { name: 'Claude 3.5', provider: 'Anthropic', tier: 'Pro', color: '#D97706', icon: 'M13 10V3L4 14h7v7l9-11h-7z' },
  { name: 'Gemini Pro', provider: 'Google', tier: 'Pro', color: '#4285F4', icon: 'M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5' },
  { name: 'DeepSeek-V4', provider: 'DeepSeek', tier: 'Free', color: '#7C3AED', icon: 'M9 3v2m6-2v2M9 19v2m6-2v2M5 9H3m2 6H3m18-6h-2m2 6h-2M7 19h10a2 2 0 002-2V7a2 2 0 00-2-2H7a2 2 0 00-2 2v10a2 2 0 002 2zM9 9h6v6H9V9z' },
  { name: 'Qwen-Max', provider: 'Alibaba', tier: 'Free', color: '#FF6A00', icon: 'M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4' },
  { name: 'Moonshot', provider: 'Moonshot', tier: 'Free', color: '#8B5CF6', icon: 'M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z' },
  { name: 'MiniMax', provider: 'MiniMax', tier: 'Free', color: '#06B6D4', icon: 'M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z' },
  { name: 'Baichuan', provider: 'Baichuan', tier: 'Free', color: '#EC4899', icon: 'M11.049 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.176 0l-3.976 2.888c-.783.57-1.838-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.38-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z' },
  { name: 'Llama 3', provider: 'Meta', tier: 'Pro', color: '#0866FF', icon: 'M3.055 11H5a2 2 0 012 2v1a2 2 0 002 2 2 2 0 012 2v2.945M8 3.935V5.5A2.5 2.5 0 0010.5 8h.5a2 2 0 012 2 2 2 0 104 0 2 2 0 012-2h1.064M15 20.488V18a2 2 0 012-2h3.064M21 12a9 9 0 11-18 0 9 9 0 0118 0z' },
]

const MEMBERSHIP_TIERS = [
  { name: 'Free', price: '¥0', period: '/永久', color: '#94A3B8', points: '500', features: ['基础模型调用', '每日点数自动刷新', '标准响应速度', '基础用量统计'], popular: false },
  { name: 'Silver', price: '¥29', period: '/月', color: '#C0C0C0', points: '5,000', features: ['全部基础模型', '优先响应', '高级用量分析', '5000 点数赠送', '专属客服通道'], popular: false },
  { name: 'Gold', price: '¥99', period: '/月', color: '#F59E0B', points: '20,000', features: ['所有模型无限制', '极速响应通道', '完整数据面板', '20000 点数赠送', 'API 密钥管理', '团队协作支持'], popular: true },
  { name: 'Diamond', price: '¥299', period: '/月', color: '#8B5CF6', points: '80,000', features: ['全模型 + 自定义', '专属计算资源', '高级 API 网关', '80000 点数赠送', '无限 API 密钥', '数据分析导出', '专属客户经理'], popular: false },
]

const TESTIMONIALS = [
  { name: '张明', role: '全栈开发者', company: '字节跳动', avatar: 'M', content: '凌点AI网关让我们的AI集成效率提升了300%，智能路由功能非常强大。', rating: 5 },
  { name: '李雪', role: 'AI研究员', company: '清华大学', avatar: 'L', content: '多模型切换和负载均衡功能让我们的研究项目进展顺利，强烈推荐！', rating: 5 },
  { name: '王浩', role: '技术总监', company: '小米科技', avatar: 'W', content: '企业级安全性和精确计费是我们选择凌点的关键原因，服务非常稳定。', rating: 5 },
  { name: '陈静', role: '产品经理', company: '腾讯', avatar: 'C', content: '从原型到生产环境，凌点提供了完整的AI接入解决方案，节省了大量时间。', rating: 5 },
]

const FAQ_ITEMS = [
  { q: '凌点AI网关是什么？', a: '凌点AI网关是一个企业级AI服务接入平台，统一接入主流AI模型，提供智能路由、负载均衡、精确计费等功能，让开发者和团队能够高效、安全地使用AI服务。' },
  { q: '如何开始使用？', a: '注册账户即可获得免费点数额度，无需绑定支付方式。登录后可直接在对话页面体验AI模型，或通过API密钥集成到自己的应用中。' },
  { q: '点数如何计算和消耗？', a: '点数根据AI模型的token消耗量计算，不同模型和会员等级有不同的费率。每日点数自动刷新，透明消费，随时可在用量页面查看详细统计。' },
  { q: '支持哪些AI模型？', a: '目前支持GPT-4o、Claude 3.5、Gemini Pro、DeepSeek-V4、Qwen-Max、Moonshot、MiniMax、Baichuan、Llama 3等9+主流模型，并持续增加中。' },
  { q: '如何升级会员？', a: '在计费页面可以选择不同的会员计划，支持按月订阅。升级后立即生效，享受更多点数和高级功能。随时可以降级或取消订阅。' },
]

const FEATURES = [
  { title: '智能路由调度', desc: '自动选择最优模型路径，支持权重分配、负载均衡、智能降级，确保每次调用都高效可靠', icon: 'M13 10V3L4 14h7v7l9-11h-7z', color: '#8B5CF6', tags: ['动态切换', '负载均衡', '降级策略'] },
  { title: '安全网关防护', desc: 'IP 级别限流、API Key 鉴权、参数安全过滤、攻击检测与防御，全方位保护你的 AI 调用', icon: 'M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z', color: '#3B82F6', tags: ['Token Bucket', 'API 鉴权', '攻击防御'] },
  { title: '精确计费引擎', desc: '基于 Redis Lua 的原子性计费，按 Token 精确扣点，消费明细透明可追溯，每一点数都清清楚楚', icon: 'M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z', color: '#10B981', tags: ['原子扣点', '点数余额', '消费追溯'] },
  { title: '实时数据分析', desc: '可视化用量仪表盘，支持多维度筛选、趋势分析、异常告警，帮助你优化 AI 调用策略', icon: 'M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z', color: '#06B6D4', tags: ['可视化', '趋势分析', '异常告警'] },
  { title: '流式对话引擎', desc: '120FPS 零卡顿流式输出，支持 SSE/WebSocket 双协议，Pretext 渲染引擎确保极致流畅体验', icon: 'M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4', color: '#F59E0B', tags: ['120FPS', 'SSE/WS', 'Pretext 渲染'] },
  { title: '开发者友好 API', desc: '完全兼容 OpenAI SDK，零学习成本接入。完善的文档、SDK 和示例代码，5 分钟即可集成', icon: 'M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4', color: '#EC4899', tags: ['OpenAI 兼容', '零学习成本', '5 分钟集成'] },
]

const STATS = [
  { label: '活跃用户', value: 12000, suffix: '+', color: '#8B5CF6' },
  { label: 'API 调用次数', value: 5000000, suffix: '+', color: '#3B82F6' },
  { label: '支持模型', value: 9, suffix: '+', color: '#06B6D4' },
  { label: '系统可用性', value: 99.9, suffix: '%', color: '#10B981' },
]

const INTEGRATIONS = [
  { name: 'React', color: '#61DAFB', icon: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z' },
  { name: 'Python', color: '#3776AB', icon: 'M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5' },
  { name: 'Node.js', color: '#339933', icon: 'M13 10V3L4 14h7v7l9-11h-7z' },
  { name: 'Docker', color: '#2496ED', icon: 'M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4' },
  { name: 'VS Code', color: '#007ACC', icon: 'M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z' },
  { name: 'GitHub', color: '#181717', icon: 'M9 19c-5 1.5-5-2.5-7-3m14 6v-3.87a3.37 3.37 0 00-.94-2.61c3.14-.35 6.44-1.54 6.44-7A5.44 5.44 0 0020 4.77 5.07 5.07 0 0019.91 1S18.73.65 16 2.48a13.38 13.38 0 00-7 0C6.27.65 5.09 1 5.09 1A5.07 5.07 0 005 4.77a5.44 5.44 0 00-1.5 3.78c0 5.42 3.3 6.61 6.44 7A3.37 3.37 0 009 18.13V22' },
]

const WORKFLOW_STEPS = [
  { step: '01', title: '注册账户', desc: '免费注册，立即获得 500 点数额度，无需绑定支付方式', icon: 'M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z', color: '#8B5CF6' },
  { step: '02', title: '选择模型', desc: '从 9+ 主流 AI 模型中选择最适合的，智能路由自动优化', icon: 'M21 12a9 9 0 01-9 9m9-9a9 9 0 00-9-9m9 9H3m9 9a9 9 0 01-9-9m9 9c1.657 0 3-4.03 3-9s-1.343-9-3-9m0 18c-1.657 0-3-4.03-3-9s1.343-9 3-9m-9 9a9 9 0 019-9', color: '#3B82F6' },
  { step: '03', title: '开始使用', desc: '通过 API 密钥集成到应用，或直接在对话页面体验 AI 能力', icon: 'M13 10V3L4 14h7v7l9-11h-7z', color: '#10B981' },
]

const TEAM_MEMBERS = [
  { name: '张明', role: '创始人 & CEO', desc: '前 Google AI 研究员，10年 AI/ML 经验', avatar: 'ZM', color: '#8B5CF6' },
  { name: '李婷', role: 'CTO', desc: '前 AWS 高级工程师，分布式系统专家', avatar: 'LT', color: '#3B82F6' },
  { name: '王浩', role: '首席架构师', desc: '前 Meta 工程师，高并发系统设计专家', avatar: 'WH', color: '#06B6D4' },
  { name: '陈雪', role: '产品总监', desc: '前字节跳动产品经理，AI 产品专家', avatar: 'CX', color: '#10B981' },
]

const BLOG_POSTS = [
  { title: '如何优化 AI 模型调用延迟', desc: '通过智能路由和缓存策略，将 API 响应时间降低 40%', date: '2024-03-15', category: '技术', color: '#8B5CF6' },
  { title: '凌点 AI 网关 v2.0 发布', desc: '全新架构，支持 9+ 主流 AI 模型，性能提升 3 倍', date: '2024-03-10', category: '产品', color: '#3B82F6' },
  { title: 'AI 模型选择指南', desc: 'GPT-4o vs Claude 3.5 vs Gemini Pro，哪个更适合你的场景？', date: '2024-03-05', category: '教程', color: '#06B6D4' },
  { title: '企业级 AI 安全最佳实践', desc: '数据加密、访问控制、审计日志，全方位保护你的 AI 应用', date: '2024-02-28', category: '安全', color: '#10B981' },
]

// ===================== Utility Components =====================

function AnimatedCounter({ end, suffix = '' }: { end: number; suffix?: string }) {
  const [count, setCount] = useState(0)
  const ref = useRef<HTMLSpanElement>(null)
  const [isVisible, setIsVisible] = useState(false)

  useEffect(() => {
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && !isVisible) {
          setIsVisible(true)
        }
      },
      { threshold: 0.3 }
    )
    if (ref.current) observer.observe(ref.current)
    return () => observer.disconnect()
  }, [isVisible])

  useEffect(() => {
    if (!isVisible) return

    const duration = 2000
    const steps = 60
    const increment = end / steps
    let current = 0
    const timer = setInterval(() => {
      current += increment
      if (current >= end) {
        setCount(end)
        clearInterval(timer)
      } else {
        setCount(Math.floor(current))
      }
    }, duration / steps)

    return () => clearInterval(timer)
  }, [end, isVisible])

  const formatNumber = (num: number) => {
    if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
    if (num >= 1000) return (num / 1000).toFixed(num >= 10000 ? 0 : 1) + 'K'
    return num.toString()
  }

  return <span ref={ref}>{formatNumber(count)}{suffix}</span>
}

// ===================== Section Components =====================

function HeroSection({ isDark, isMobile, mouse, navigate, user, onNavigateToChat }: { isDark: boolean; isMobile: boolean; mouse: { x: number; y: number }; navigate: any; user: any; onNavigateToChat: () => void }) {
  return (
    <section className="h-screen flex items-center justify-center relative overflow-hidden px-3 sm:px-6">
      {!isMobile && <ParticleField isDark={isDark} />}
      
      <motion.div 
        className="absolute inset-0 overflow-hidden pointer-events-none"
      >
        <FloatingOrb delay={0} size={isMobile ? 250 : 500} color={isDark ? 'rgba(139,92,246,0.12)' : 'rgba(124,58,237,0.06)'} x={5} y={15} />
        <FloatingOrb delay={2} size={isMobile ? 200 : 400} color={isDark ? 'rgba(59,130,246,0.1)' : 'rgba(37,99,235,0.05)'} x={75} y={65} />
        <FloatingOrb delay={4} size={isMobile ? 180 : 350} color={isDark ? 'rgba(6,182,212,0.08)' : 'rgba(8,145,178,0.04)'} x={45} y={5} />
      </motion.div>

      <motion.div
        className="absolute w-[800px] h-[800px] rounded-full blur-[150px] pointer-events-none"
        style={{
          background: isDark
            ? 'radial-gradient(circle, rgba(139,92,246,0.15) 0%, transparent 70%)'
            : 'radial-gradient(circle, rgba(124,58,237,0.08) 0%, transparent 70%)',
          left: `${(mouse.x - 0.5) * 120 + 50}%`,
          top: `${(mouse.y - 0.5) * 120 + 50}%`,
          transform: 'translate(-50%, -50%)',
        }}
        transition={{ type: 'spring', damping: 40, stiffness: 200 }}
      />

      <div className="relative z-10 max-w-5xl mx-auto text-center">
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 0.2 }}
        >
          <span className="inline-block px-3 sm:px-4 py-1 sm:py-1.5 rounded-full text-[10px] sm:text-xs font-medium mb-3 sm:mb-6" style={{
            background: isDark ? 'rgba(139,92,246,0.1)' : 'rgba(124,58,237,0.08)',
            color: isDark ? '#A78BFA' : '#7C3AED',
            border: `1px solid ${isDark ? 'rgba(139,92,246,0.2)' : 'rgba(124,58,237,0.15)'}`,
          }}>
            新一代 AI 模型调度网关
          </span>
        </motion.div>

        <motion.h1
          initial={{ opacity: 0, y: 30 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 0.4 }}
          className="text-xl sm:text-4xl md:text-5xl lg:text-7xl font-bold tracking-tight mb-2 sm:mb-4 md:mb-6 leading-tight"
          style={{
            fontFamily: 'var(--font-display)',
            background: isDark
              ? 'linear-gradient(135deg, #FFFFFF 0%, #A78BFA 50%, #60A5FA 100%)'
              : 'linear-gradient(135deg, #0F172A 0%, #7C3AED 50%, #2563EB 100%)',
            WebkitBackgroundClip: 'text',
            WebkitTextFillColor: 'transparent',
          }}
        >
          统一接入 · 智能调度
          <br />
          高效 AI 网关
        </motion.h1>

        <motion.p
          initial={{ opacity: 0, y: 30 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 0.6 }}
          className="text-xs sm:text-base md:text-lg max-w-3xl mx-auto mb-4 sm:mb-8 md:mb-10 leading-relaxed px-2 sm:px-4"
          style={{ color: isDark ? 'rgba(255,255,255,0.7)' : 'rgba(15,23,42,0.6)' }}
        >
          凌点 AI 网关统一接入主流 AI 模型，提供智能路由、负载均衡、精确计费等功能，
          让开发者和团队能够高效、安全地使用 AI 服务
        </motion.p>

        <motion.div
          initial={{ opacity: 0, y: 30 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 0.8 }}
          className="flex flex-col sm:flex-row items-center justify-center gap-3 sm:gap-4"
        >
          {user ? (
            <GlowButton onClick={onNavigateToChat} color="#8B5CF6" size={isMobile ? "sm" : "lg"}>
              进入 AI 对话
            </GlowButton>
          ) : (
            <>
              <GlowButton onClick={() => navigate('/register')} color="#8B5CF6" size={isMobile ? "sm" : "lg"}>
                免费开始
              </GlowButton>
              <GlowButton onClick={() => navigate('/login')} color="#3B82F6" size={isMobile ? "sm" : "lg"}>
                登录
              </GlowButton>
            </>
          )}
        </motion.div>
      </div>

      <motion.div
        className="absolute bottom-6 sm:bottom-8 left-1/2 -translate-x-1/2"
        animate={{ y: [0, 10, 0] }}
        transition={{ duration: 2, repeat: Infinity, ease: 'easeInOut' }}
      >
        <svg width="20" height="20" className="sm:w-6 sm:h-6" viewBox="0 0 24 24" fill="none" stroke={isDark ? 'rgba(255,255,255,0.4)' : 'rgba(0,0,0,0.3)'} strokeWidth="2">
          <path d="M7 13l5 5 5-5M7 6l5 5 5-5" />
        </svg>
      </motion.div>
    </section>
  )
}

function FeaturesSection({ isDark, isMobile }: { isDark: boolean; isMobile: boolean }) {
  return (
    <section className="h-screen flex items-center justify-center relative overflow-hidden px-3 sm:px-6">
      <motion.div 
        className="absolute top-1/4 right-0 w-[200px] sm:w-[500px] h-[200px] sm:h-[500px] rounded-full blur-[80px] sm:blur-[150px] opacity-10 pointer-events-none"
        style={{ background: 'var(--accent-purple)' }}
        animate={{ x: [0, -30, 0] }}
        transition={{ duration: 18, repeat: Infinity, ease: 'easeInOut' }}
      />

      <div className="max-w-7xl mx-auto w-full">
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.8 }}
          className="text-center mb-6 sm:mb-12"
        >
          <h2 className="text-xl sm:text-4xl md:text-5xl font-bold mb-1.5 sm:mb-4" style={{
            fontFamily: 'var(--font-display)',
            color: isDark ? '#FFFFFF' : '#0F172A',
          }}>
            核心能力
          </h2>
          <p className="text-xs sm:text-lg max-w-2xl mx-auto" style={{ color: isDark ? 'rgba(255,255,255,0.6)' : 'rgba(15,23,42,0.6)' }}>
            为 AI 调用而生的完整基础设施
          </p>
          <p className="text-[11px] sm:text-sm mt-1 sm:mt-2 max-w-xl mx-auto" style={{ color: isDark ? 'rgba(255,255,255,0.4)' : 'rgba(15,23,42,0.4)' }}>
            从路由调度到安全防护，从精确计费到数据分析，一站式解决所有 AI 集成需求
          </p>
        </motion.div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2 sm:gap-6">
          {FEATURES.map((feature, i) => (
            <TiltCard key={i} isDark={isDark} delay={i * 0.1} glowColor={feature.color + '20'}>
              <div className="flex flex-col items-start">
                <div className="w-7 h-7 sm:w-10 sm:h-10 md:w-12 md:h-12 rounded-lg sm:rounded-xl flex items-center justify-center mb-2 sm:mb-3 md:mb-4" style={{
                  background: feature.color + '15',
                }}>
                  <svg width="14" height="14" className="sm:w-5 sm:h-5 md:w-6 md:h-6" viewBox="0 0 24 24" fill="none" stroke={feature.color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d={feature.icon} />
                  </svg>
                </div>
                <h3 className="text-sm sm:text-lg md:text-xl font-semibold mb-1 sm:mb-2" style={{ color: isDark ? '#FFFFFF' : '#0F172A' }}>
                  {feature.title}
                </h3>
                <p className="text-[11px] sm:text-sm leading-relaxed mb-3 sm:mb-4" style={{ color: isDark ? 'rgba(255,255,255,0.6)' : 'rgba(15,23,42,0.6)' }}>
                  {feature.desc}
                </p>
                {feature.tags && (
                  <div className="flex flex-wrap gap-1.5">
                    {feature.tags.map((tag, j) => (
                      <span key={j} className="px-2 py-0.5 rounded-md text-[10px] font-medium" style={{ background: `${feature.color}08`, color: feature.color, border: `1px solid ${feature.color}15` }}>
                        {tag}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </TiltCard>
          ))}
        </div>
      </div>
    </section>
  )
}

function ModelsSection({ isDark, isMobile }: { isDark: boolean; isMobile: boolean }) {
  return (
    <section className="h-screen flex items-center justify-center relative overflow-hidden px-3 sm:px-6">
      <motion.div 
        className="absolute top-1/4 left-0 w-[200px] sm:w-[500px] h-[200px] sm:h-[500px] rounded-full blur-[80px] sm:blur-[150px] opacity-10 pointer-events-none"
        style={{ background: 'var(--accent-blue)' }}
        animate={{ x: [0, 30, 0] }}
        transition={{ duration: 18, repeat: Infinity, ease: 'easeInOut' }}
      />

      <div className="max-w-7xl mx-auto w-full">
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.8 }}
          className="text-center mb-6 sm:mb-12"
        >
          <h2 className="text-xl sm:text-4xl md:text-5xl font-bold mb-1.5 sm:mb-4" style={{
            fontFamily: 'var(--font-display)',
            color: isDark ? '#FFFFFF' : '#0F172A',
          }}>
            支持的模型
          </h2>
          <p className="text-xs sm:text-lg max-w-2xl mx-auto" style={{ color: isDark ? 'rgba(255,255,255,0.6)' : 'rgba(15,23,42,0.6)' }}>
            9+ 主流 AI 模型，持续扩展中
          </p>
        </motion.div>

        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-2 sm:gap-4">
          {PLATFORM_MODELS.map((model, i) => (
            <motion.div
              key={i}
              initial={{ opacity: 0, scale: 0.9 }}
              whileInView={{ opacity: 1, scale: 1 }}
              viewport={{ once: true }}
              transition={{ duration: 0.5, delay: i * 0.05 }}
              whileHover={{ scale: 1.05 }}
              className="rounded-xl sm:rounded-2xl p-2 sm:p-4 text-center cursor-pointer"
              style={{
                background: isDark ? 'rgba(255,255,255,0.03)' : 'rgba(255,255,255,0.7)',
                border: `1px solid ${isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)'}`,
              }}
            >
              <div className="w-7 h-7 sm:w-10 sm:h-10 rounded-lg sm:rounded-xl mx-auto mb-1.5 sm:mb-3 flex items-center justify-center" style={{
                background: model.color + '20',
              }}>
                <svg width="14" height="14" className="sm:w-5 sm:h-5" viewBox="0 0 24 24" fill="none" stroke={model.color} strokeWidth="2">
                  <path d={model.icon} />
                </svg>
              </div>
              <h4 className="text-[11px] sm:text-sm font-semibold mb-0.5 sm:mb-1" style={{ color: isDark ? '#FFFFFF' : '#0F172A' }}>
                {model.name}
              </h4>
              <p className="text-[9px] sm:text-xs" style={{ color: isDark ? 'rgba(255,255,255,0.5)' : 'rgba(15,23,42,0.5)' }}>
                {model.provider}
              </p>
              <span className="inline-block mt-1 sm:mt-2 px-1.5 py-0.5 rounded-full text-[8px] sm:text-[10px] font-medium" style={{
                background: model.tier === 'Pro' ? 'rgba(139,92,246,0.15)' : 'rgba(16,185,129,0.15)',
                color: model.tier === 'Pro' ? '#A78BFA' : '#10B981',
              }}>
                {model.tier}
              </span>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  )
}

function PricingSection({ isDark, isMobile, navigate }: { isDark: boolean; isMobile: boolean; navigate: any }) {
  return (
    <section className={`${isMobile ? 'min-h-screen py-8 overflow-y-auto' : 'h-screen'} flex items-center justify-center relative overflow-hidden px-3 sm:px-6`}>
      <motion.div 
        className="absolute bottom-0 right-0 w-[200px] sm:w-[600px] h-[200px] sm:h-[600px] rounded-full blur-[80px] sm:blur-[180px] opacity-10 pointer-events-none"
        style={{ background: 'var(--accent-purple)' }}
        animate={{ y: [0, -40, 0] }}
        transition={{ duration: 20, repeat: Infinity, ease: 'easeInOut' }}
      />

      <div className="max-w-7xl mx-auto w-full">
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.8 }}
          className="text-center mb-3 sm:mb-10"
        >
          <h2 className="text-lg sm:text-4xl md:text-5xl font-bold mb-1 sm:mb-4" style={{
            fontFamily: 'var(--font-display)',
            color: isDark ? '#FFFFFF' : '#0F172A',
          }}>
            会员方案
          </h2>
          <p className="text-[11px] sm:text-lg max-w-2xl mx-auto" style={{ color: isDark ? 'rgba(255,255,255,0.6)' : 'rgba(15,23,42,0.6)' }}>
            灵活的会员计划，满足不同需求
          </p>
        </motion.div>

        <div className="grid grid-cols-2 sm:grid-cols-2 lg:grid-cols-4 gap-2 sm:gap-5">
          {MEMBERSHIP_TIERS.map((tier, i) => (
            <motion.div
              key={i}
              initial={{ opacity: 0, y: 30 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.6, delay: i * 0.1 }}
              whileHover={{ scale: 1.03 }}
              className={`rounded-lg sm:rounded-2xl p-2 sm:p-6 relative ${tier.popular ? 'ring-2' : ''}`}
              style={{
                background: isDark ? 'rgba(255,255,255,0.03)' : 'rgba(255,255,255,0.7)',
                border: `1px solid ${isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)'}`,
                outlineColor: tier.popular ? tier.color : 'transparent',
              }}
            >
              {tier.popular && (
                <span className="absolute -top-2 left-1/2 -translate-x-1/2 px-1.5 py-0.5 sm:px-3 sm:py-1 rounded-full text-[8px] sm:text-xs font-medium text-white whitespace-nowrap" style={{
                  background: tier.color,
                }}>
                  最受欢迎
                </span>
              )}

              <h3 className="text-xs sm:text-xl font-semibold mb-0.5 sm:mb-2" style={{ color: isDark ? '#FFFFFF' : '#0F172A' }}>
                {tier.name}
              </h3>

              <div className="mb-1 sm:mb-4">
                <span className="text-sm sm:text-3xl font-bold" style={{ color: tier.color }}>{tier.price}</span>
                <span className="text-[8px] sm:text-sm" style={{ color: isDark ? 'rgba(255,255,255,0.5)' : 'rgba(15,23,42,0.5)' }}>{tier.period}</span>
              </div>

              <p className="text-[9px] sm:text-sm mb-1.5 sm:mb-4" style={{ color: isDark ? 'rgba(255,255,255,0.6)' : 'rgba(15,23,42,0.6)' }}>
                {tier.points} 点数
              </p>

              <ul className="space-y-0.5 sm:space-y-2 mb-2 sm:mb-6">
                {tier.features.slice(0, isMobile ? 3 : tier.features.length).map((feature, j) => (
                  <li key={j} className="flex items-start gap-1 text-[8px] sm:text-sm" style={{ color: isDark ? 'rgba(255,255,255,0.7)' : 'rgba(15,23,42,0.7)' }}>
                    <svg width="10" height="10" className="sm:w-4 sm:h-4 flex-shrink-0 mt-0.5" viewBox="0 0 24 24" fill="none" stroke={tier.color} strokeWidth="2.5">
                      <polyline points="20 6 9 17 4 12" />
                    </svg>
                    <span className="leading-tight">{feature}</span>
                  </li>
                ))}
              </ul>

              <GlowButton onClick={() => navigate('/register')} color={tier.color} size={isMobile ? "sm" : "sm"}>
                立即开始
              </GlowButton>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  )
}

function TestimonialsSection({ isDark, isMobile }: { isDark: boolean; isMobile: boolean }) {
  return (
    <section className="h-screen flex items-center justify-center relative overflow-hidden px-3 sm:px-6">
      <motion.div 
        className="absolute top-0 left-1/2 -translate-x-1/2 w-[200px] sm:w-[700px] h-[200px] sm:h-[700px] rounded-full blur-[80px] sm:blur-[200px] opacity-10 pointer-events-none"
        style={{ background: 'var(--accent-emerald)' }}
        animate={{ scale: [1, 1.15, 1] }}
        transition={{ duration: 25, repeat: Infinity, ease: 'easeInOut' }}
      />

      <div className="max-w-7xl mx-auto w-full">
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.8 }}
          className="text-center mb-4 sm:mb-10"
        >
          <h2 className="text-xl sm:text-4xl md:text-5xl font-bold mb-1.5 sm:mb-4" style={{
            fontFamily: 'var(--font-display)',
            color: isDark ? '#FFFFFF' : '#0F172A',
          }}>
            用户评价
          </h2>
          <p className="text-xs sm:text-lg max-w-2xl mx-auto" style={{ color: isDark ? 'rgba(255,255,255,0.6)' : 'rgba(15,23,42,0.6)' }}>
            来自行业领先团队和开发者的真实反馈
          </p>
        </motion.div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-2 sm:gap-5">
          {TESTIMONIALS.map((testimonial, i) => (
            <motion.div
              key={i}
              initial={{ opacity: 0, y: 30 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.6, delay: i * 0.1 }}
              whileHover={{ scale: 1.02 }}
              className="rounded-xl sm:rounded-2xl p-3 sm:p-6"
              style={{
                background: isDark ? 'rgba(255,255,255,0.03)' : 'rgba(255,255,255,0.7)',
                border: `1px solid ${isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)'}`,
              }}
            >
              <div className="flex items-center gap-2 sm:gap-4 mb-2 sm:mb-4">
                <div className="w-8 h-8 sm:w-12 sm:h-12 rounded-full flex items-center justify-center text-sm sm:text-lg font-bold flex-shrink-0" style={{
                  background: 'linear-gradient(135deg, #8B5CF6, #6366F1)',
                  color: '#FFFFFF',
                }}>
                  {testimonial.avatar}
                </div>
                <div>
                  <h4 className="text-xs sm:text-base font-semibold" style={{ color: isDark ? '#FFFFFF' : '#0F172A' }}>
                    {testimonial.name}
                  </h4>
                  <p className="text-[10px] sm:text-sm" style={{ color: isDark ? 'rgba(255,255,255,0.5)' : 'rgba(15,23,42,0.5)' }}>
                    {testimonial.role} · {testimonial.company}
                  </p>
                </div>
              </div>

              <div className="flex gap-0.5 sm:gap-1 mb-1.5 sm:mb-3">
                {Array.from({ length: testimonial.rating }).map((_, j) => (
                  <svg key={j} width="10" height="10" className="sm:w-4 sm:h-4" viewBox="0 0 24 24" fill="#F59E0B" stroke="none">
                    <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" />
                  </svg>
                ))}
              </div>

              <p className="text-[11px] sm:text-sm leading-relaxed" style={{ color: isDark ? 'rgba(255,255,255,0.7)' : 'rgba(15,23,42,0.7)' }}>
                {testimonial.content}
              </p>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  )
}

function FAQSection({ isDark, isMobile }: { isDark: boolean; isMobile: boolean }) {
  const [activeFaq, setActiveFaq] = useState<number | null>(null)

  return (
    <section className="h-screen flex items-center justify-center relative overflow-hidden px-3 sm:px-6">
      <motion.div 
        className="absolute top-1/2 right-0 w-[150px] sm:w-[400px] h-[150px] sm:h-[400px] rounded-full blur-[60px] sm:blur-[120px] opacity-10 pointer-events-none"
        style={{ background: 'var(--accent-purple)' }}
        animate={{ y: [0, 25, 0] }}
        transition={{ duration: 16, repeat: Infinity, ease: 'easeInOut' }}
      />

      <div className="max-w-3xl mx-auto w-full">
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.8 }}
          className="text-center mb-4 sm:mb-10"
        >
          <h2 className="text-xl sm:text-4xl md:text-5xl font-bold mb-1.5 sm:mb-4" style={{
            fontFamily: 'var(--font-display)',
            color: isDark ? '#FFFFFF' : '#0F172A',
          }}>
            常见问题
          </h2>
          <p className="text-xs sm:text-lg max-w-2xl mx-auto" style={{ color: isDark ? 'rgba(255,255,255,0.6)' : 'rgba(15,23,42,0.6)' }}>
            关于凌点 AI 网关的常见疑问解答
          </p>
        </motion.div>

        <div className="space-y-1.5 sm:space-y-3">
          {FAQ_ITEMS.map((item, i) => (
            <motion.div
              key={i}
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.5, delay: i * 0.05 }}
              className="rounded-xl overflow-hidden"
              style={{
                background: isDark ? 'rgba(255,255,255,0.03)' : 'rgba(255,255,255,0.7)',
                border: `1px solid ${activeFaq === i ? 'rgba(139,92,246,0.3)' : (isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)')}`,
              }}
            >
              <button
                onClick={() => setActiveFaq(activeFaq === i ? null : i)}
                className="w-full px-3 sm:px-6 py-2.5 sm:py-4 flex items-center justify-between text-left"
              >
                <span className="text-xs sm:text-base font-medium" style={{ color: isDark ? '#FFFFFF' : '#0F172A' }}>
                  {item.q}
                </span>
                <motion.svg
                  width="14" height="14"
                  className="sm:w-5 sm:h-5"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke={isDark ? 'rgba(255,255,255,0.5)' : 'rgba(15,23,42,0.5)'}
                  strokeWidth="2"
                  animate={{ rotate: activeFaq === i ? 180 : 0 }}
                  transition={{ duration: 0.3 }}
                >
                  <polyline points="6 9 12 15 18 9" />
                </motion.svg>
              </button>

              <AnimatePresence>
                {activeFaq === i && (
                  <motion.div
                    initial={{ height: 0, opacity: 0 }}
                    animate={{ height: 'auto', opacity: 1 }}
                    exit={{ height: 0, opacity: 0 }}
                    transition={{ duration: 0.3 }}
                    className="overflow-hidden"
                  >
                    <p className="px-3 sm:px-6 pb-2.5 sm:pb-4 text-[11px] sm:text-sm leading-relaxed" style={{ color: isDark ? 'rgba(255,255,255,0.6)' : 'rgba(15,23,42,0.6)' }}>
                      {item.a}
                    </p>
                  </motion.div>
                )}
              </AnimatePresence>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  )
}

function StatsSection({ isDark, isMobile }: { isDark: boolean; isMobile: boolean }) {
  return (
    <section className="h-screen flex items-center justify-center relative overflow-hidden px-3 sm:px-6">
      <motion.div 
        className="absolute top-0 left-0 w-[200px] sm:w-[600px] h-[200px] sm:h-[600px] rounded-full blur-[80px] sm:blur-[180px] opacity-10 pointer-events-none"
        style={{ background: 'var(--accent-purple)' }}
        animate={{ scale: [1, 1.2, 1] }}
        transition={{ duration: 20, repeat: Infinity, ease: 'easeInOut' }}
      />

      <div className="max-w-7xl mx-auto w-full">
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.8 }}
          className="text-center mb-6 sm:mb-16"
        >
          <h2 className="text-xl sm:text-4xl md:text-5xl font-bold mb-1.5 sm:mb-4" style={{
            fontFamily: 'var(--font-display)',
            color: isDark ? '#FFFFFF' : '#0F172A',
          }}>
            平台数据
          </h2>
          <p className="text-xs sm:text-lg max-w-2xl mx-auto" style={{ color: isDark ? 'rgba(255,255,255,0.6)' : 'rgba(15,23,42,0.6)' }}>
            凌点 AI 网关，值得信赖的 AI 服务基础设施
          </p>
        </motion.div>

        <div className="grid grid-cols-2 lg:grid-cols-4 gap-2 sm:gap-6">
          {STATS.map((stat, i) => (
            <motion.div
              key={i}
              initial={{ opacity: 0, scale: 0.9 }}
              whileInView={{ opacity: 1, scale: 1 }}
              viewport={{ once: true }}
              transition={{ duration: 0.6, delay: i * 0.1 }}
              whileHover={{ scale: 1.05 }}
              className="rounded-xl sm:rounded-2xl p-3 sm:p-8 text-center"
              style={{
                background: isDark ? 'rgba(255,255,255,0.03)' : 'rgba(255,255,255,0.7)',
                border: `1px solid ${isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)'}`,
              }}
            >
              <div className="text-xl sm:text-4xl md:text-5xl font-bold mb-0.5 sm:mb-2" style={{ color: stat.color }}>
                <AnimatedCounter end={stat.value} suffix={stat.suffix} />
              </div>
              <p className="text-[10px] sm:text-sm" style={{ color: isDark ? 'rgba(255,255,255,0.6)' : 'rgba(15,23,42,0.6)' }}>
                {stat.label}
              </p>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  )
}

function IntegrationsSection({ isDark, isMobile }: { isDark: boolean; isMobile: boolean }) {
  return (
    <section className="h-screen flex items-center justify-center relative overflow-hidden px-3 sm:px-6">
      <motion.div 
        className="absolute bottom-0 right-0 w-[200px] sm:w-[500px] h-[200px] sm:h-[500px] rounded-full blur-[80px] sm:blur-[150px] opacity-10 pointer-events-none"
        style={{ background: 'var(--accent-blue)' }}
        animate={{ x: [0, -30, 0] }}
        transition={{ duration: 18, repeat: Infinity, ease: 'easeInOut' }}
      />

      <div className="max-w-7xl mx-auto w-full">
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.8 }}
          className="text-center mb-6 sm:mb-16"
        >
          <h2 className="text-xl sm:text-4xl md:text-5xl font-bold mb-1.5 sm:mb-4" style={{
            fontFamily: 'var(--font-display)',
            color: isDark ? '#FFFFFF' : '#0F172A',
          }}>
            集成工具
          </h2>
          <p className="text-xs sm:text-lg max-w-2xl mx-auto" style={{ color: isDark ? 'rgba(255,255,255,0.6)' : 'rgba(15,23,42,0.6)' }}>
            与你常用的开发工具无缝集成
          </p>
        </motion.div>

        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2 sm:gap-6">
          {INTEGRATIONS.map((tool, i) => (
            <motion.div
              key={i}
              initial={{ opacity: 0, scale: 0.9 }}
              whileInView={{ opacity: 1, scale: 1 }}
              viewport={{ once: true }}
              transition={{ duration: 0.5, delay: i * 0.08 }}
              whileHover={{ scale: 1.1 }}
              className="rounded-xl sm:rounded-2xl p-3 sm:p-6 flex flex-col items-center justify-center cursor-pointer"
              style={{
                background: isDark ? 'rgba(255,255,255,0.03)' : 'rgba(255,255,255,0.7)',
                border: `1px solid ${isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)'}`,
              }}
            >
              <div className="w-8 h-8 sm:w-14 sm:h-14 rounded-lg sm:rounded-xl mb-1.5 sm:mb-3 flex items-center justify-center" style={{
                background: tool.color + '20',
              }}>
                <svg width="16" height="16" className="sm:w-7 sm:h-7" viewBox="0 0 24 24" fill="none" stroke={tool.color} strokeWidth="1.5">
                  <path d={tool.icon} />
                </svg>
              </div>
              <span className="text-[10px] sm:text-sm font-medium" style={{ color: isDark ? 'rgba(255,255,255,0.8)' : 'rgba(15,23,42,0.7)' }}>
                {tool.name}
              </span>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  )
}

function WorkflowSection({ isDark, isMobile }: { isDark: boolean; isMobile: boolean }) {
  return (
    <section className="h-screen flex items-center justify-center relative overflow-hidden px-3 sm:px-6">
      <motion.div 
        className="absolute top-1/4 left-1/2 -translate-x-1/2 w-[200px] sm:w-[700px] h-[200px] sm:h-[700px] rounded-full blur-[80px] sm:blur-[200px] opacity-10 pointer-events-none"
        style={{ background: 'var(--accent-cyan)' }}
        animate={{ scale: [1, 1.1, 1] }}
        transition={{ duration: 22, repeat: Infinity, ease: 'easeInOut' }}
      />

      <div className="max-w-7xl mx-auto w-full">
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.8 }}
          className="text-center mb-6 sm:mb-16"
        >
          <h2 className="text-xl sm:text-4xl md:text-5xl font-bold mb-1.5 sm:mb-4" style={{
            fontFamily: 'var(--font-display)',
            color: isDark ? '#FFFFFF' : '#0F172A',
          }}>
            三步开始使用
          </h2>
          <p className="text-xs sm:text-lg max-w-2xl mx-auto" style={{ color: isDark ? 'rgba(255,255,255,0.6)' : 'rgba(15,23,42,0.6)' }}>
            简单三步，快速接入 AI 能力
          </p>
        </motion.div>

        <div className="relative">
          <div className="hidden md:block absolute top-16 sm:top-20 left-[16%] right-[16%] h-px">
            <motion.div
              className="w-full h-full"
              style={{
                background: `linear-gradient(90deg, transparent, ${isDark ? 'rgba(139,92,246,0.3)' : 'rgba(124,58,237,0.2)'}, transparent)`
              }}
              initial={{ scaleX: 0 }}
              whileInView={{ scaleX: 1 }}
              viewport={{ once: true }}
              transition={{ duration: 1.5, ease: 'easeOut' }}
            />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 sm:gap-8">
            {WORKFLOW_STEPS.map((step, i) => (
              <motion.div
                key={i}
                initial={{ opacity: 0, y: 40 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.7, delay: i * 0.2 }}
                className="flex flex-col items-center text-center"
              >
                <div className="w-14 h-14 sm:w-20 sm:h-20 rounded-xl sm:rounded-2xl flex items-center justify-center mb-3 sm:mb-6 relative" style={{
                  background: `linear-gradient(135deg, ${step.color}20, ${step.color}10)`,
                  border: `1px solid ${step.color}30`,
                }}>
                  <svg width="20" height="20" className="sm:w-8 sm:h-8" viewBox="0 0 24 24" fill="none" stroke={step.color} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                    <path d={step.icon} />
                  </svg>
                  <span className="absolute -top-1 -right-1 sm:-top-2 sm:-right-2 w-5 h-5 sm:w-7 sm:h-7 rounded-full flex items-center justify-center text-[8px] sm:text-xs font-bold text-white" style={{
                    background: step.color,
                  }}>
                    {step.step}
                  </span>
                </div>

                <h3 className="text-sm sm:text-xl font-semibold mb-1.5 sm:mb-3" style={{ color: isDark ? '#FFFFFF' : '#0F172A' }}>
                  {step.title}
                </h3>

                <p className="text-[11px] sm:text-sm leading-relaxed max-w-xs px-4" style={{ color: isDark ? 'rgba(255,255,255,0.6)' : 'rgba(15,23,42,0.6)' }}>
                  {step.desc}
                </p>
              </motion.div>
            ))}
          </div>
        </div>
      </div>
    </section>
  )
}

function CTASection({ isDark, isMobile, navigate, user, onNavigateToChat }: { isDark: boolean; isMobile: boolean; navigate: any; user: any; onNavigateToChat: () => void }) {
  return (
    <section className="h-screen flex items-center justify-center relative overflow-hidden px-3 sm:px-6">
      <motion.div 
        className="absolute top-0 left-0 w-[300px] sm:w-[800px] h-[300px] sm:h-[800px] rounded-full blur-[100px] sm:blur-[200px] opacity-10 pointer-events-none"
        style={{ background: 'var(--accent-purple)' }}
        animate={{ scale: [1, 1.15, 1] }}
        transition={{ duration: 25, repeat: Infinity, ease: 'easeInOut' }}
      />

      <div className="max-w-4xl mx-auto w-full">
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          whileInView={{ opacity: 1, scale: 1 }}
          viewport={{ once: true }}
          transition={{ duration: 0.8 }}
          className="rounded-xl sm:rounded-[2.5rem] p-4 sm:p-12 md:p-16 text-center relative overflow-hidden"
          style={{
            background: isDark
              ? 'linear-gradient(135deg, rgba(139,92,246,0.12) 0%, rgba(59,130,246,0.08) 50%, rgba(6,182,212,0.06) 100%)'
              : 'linear-gradient(135deg, rgba(124,58,237,0.06) 0%, rgba(37,99,235,0.04) 50%, rgba(8,145,178,0.03) 100%)',
            border: `1px solid ${isDark ? 'rgba(139,92,246,0.2)' : 'rgba(124,58,237,0.12)'}`,
            boxShadow: isDark ? '0 0 80px rgba(139,92,246,0.15)' : '0 0 80px rgba(124,58,237,0.08)',
          }}
        >
          <motion.div 
            className="absolute top-0 right-0 w-48 sm:w-96 h-48 sm:h-96 rounded-full blur-[60px] sm:blur-[120px] opacity-20" 
            style={{ background: 'var(--accent-purple)' }}
            animate={{ y: [0, 30, 0], opacity: [0.2, 0.3, 0.2] }}
            transition={{ duration: 10, repeat: Infinity, ease: 'easeInOut' }}
          />
          <motion.div 
            className="absolute bottom-0 left-0 w-36 sm:w-72 h-36 sm:h-72 rounded-full blur-[50px] sm:blur-[100px] opacity-15" 
            style={{ background: 'var(--accent-blue)' }}
            animate={{ y: [0, -20, 0], opacity: [0.15, 0.25, 0.15] }}
            transition={{ duration: 8, repeat: Infinity, ease: 'easeInOut', delay: 2 }}
          />

          <div className="relative z-10">
            <h2 className="text-xl sm:text-4xl sm:text-5xl font-bold mb-3 sm:mb-6" style={{
              fontFamily: 'var(--font-display)',
              color: isDark ? '#FFFFFF' : '#0F172A',
            }}>
              准备好开始了吗？
            </h2>

            <p className="text-xs sm:text-lg max-w-2xl mx-auto mb-4 sm:mb-10 leading-relaxed" style={{ color: isDark ? 'rgba(255,255,255,0.7)' : 'rgba(15,23,42,0.6)' }}>
              立即体验凌点 AI 网关的强大能力，开启智能对话之旅。
              注册即享免费点数，无需绑定支付方式。
            </p>

            <div className="flex flex-col sm:flex-row items-center justify-center gap-3 sm:gap-5">
              {user ? (
                <GlowButton onClick={onNavigateToChat} color="#8B5CF6" size="lg">
                  进入 AI 对话
                </GlowButton>
              ) : (
                <>
                  <GlowButton onClick={() => navigate('/register')} color="#8B5CF6" size="lg">
                    免费注册
                  </GlowButton>
                  <GlowButton onClick={() => navigate('/login')} color="#3B82F6" size="lg">
                    登录
                  </GlowButton>
                </>
              )}
            </div>
          </div>
        </motion.div>
      </div>
    </section>
  )
}

function TeamSection({ isDark, isMobile }: { isDark: boolean; isMobile: boolean }) {
  return (
    <section className="h-screen flex items-center justify-center relative overflow-hidden px-3 sm:px-6">
      <motion.div 
        className="absolute top-1/4 right-0 w-[200px] sm:w-[500px] h-[200px] sm:h-[500px] rounded-full blur-[80px] sm:blur-[150px] opacity-10 pointer-events-none"
        style={{ background: 'var(--accent-purple)' }}
        animate={{ y: [0, 30, 0] }}
        transition={{ duration: 18, repeat: Infinity, ease: 'easeInOut' }}
      />

      <div className="max-w-7xl mx-auto w-full">
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.8 }}
          className="text-center mb-8 sm:mb-16"
        >
          <h2 className="text-xl sm:text-4xl md:text-5xl font-bold mb-1.5 sm:mb-4" style={{
            fontFamily: 'var(--font-display)',
            color: isDark ? '#FFFFFF' : '#0F172A',
          }}>
            核心团队
          </h2>
          <p className="text-xs sm:text-lg max-w-2xl mx-auto" style={{ color: isDark ? 'rgba(255,255,255,0.6)' : 'rgba(15,23,42,0.6)' }}>
            来自全球顶尖科技公司的 AI 专家
          </p>
        </motion.div>

        <div className="grid grid-cols-2 sm:grid-cols-2 lg:grid-cols-4 gap-2 sm:gap-8">
          {TEAM_MEMBERS.map((member, i) => (
            <motion.div
              key={i}
              initial={{ opacity: 0, y: 40 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.6, delay: i * 0.1 }}
              whileHover={{ y: -8 }}
              className="rounded-xl sm:rounded-2xl p-3 sm:p-6 text-center"
              style={{
                background: isDark ? 'rgba(255,255,255,0.03)' : 'rgba(255,255,255,0.7)',
                border: `1px solid ${isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)'}`,
              }}
            >
              <div className="w-12 h-12 sm:w-20 sm:h-20 rounded-full mx-auto mb-2 sm:mb-4 flex items-center justify-center text-sm sm:text-lg font-bold" style={{
                background: `linear-gradient(135deg, ${member.color}, ${member.color}80)`,
                color: '#FFFFFF',
              }}>
                {member.avatar}
              </div>

              <h3 className="text-xs sm:text-lg font-semibold mb-0.5 sm:mb-1" style={{ color: isDark ? '#FFFFFF' : '#0F172A' }}>
                {member.name}
              </h3>

              <p className="text-[9px] sm:text-sm mb-1.5 sm:mb-3" style={{ color: member.color }}>
                {member.role}
              </p>

              <p className="text-[9px] sm:text-xs leading-relaxed" style={{ color: isDark ? 'rgba(255,255,255,0.5)' : 'rgba(15,23,42,0.5)' }}>
                {member.desc}
              </p>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  )
}

function BlogSection({ isDark, isMobile }: { isDark: boolean; isMobile: boolean }) {
  return (
    <section className="h-screen flex items-center justify-center relative overflow-hidden px-3 sm:px-6">
      <motion.div 
        className="absolute bottom-0 left-0 w-[200px] sm:w-[600px] h-[200px] sm:h-[600px] rounded-full blur-[80px] sm:blur-[180px] opacity-10 pointer-events-none"
        style={{ background: 'var(--accent-blue)' }}
        animate={{ x: [0, 40, 0] }}
        transition={{ duration: 20, repeat: Infinity, ease: 'easeInOut' }}
      />

      <div className="max-w-7xl mx-auto w-full">
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.8 }}
          className="text-center mb-4 sm:mb-16"
        >
          <h2 className="text-xl sm:text-4xl md:text-5xl font-bold mb-1.5 sm:mb-4" style={{
            fontFamily: 'var(--font-display)',
            color: isDark ? '#FFFFFF' : '#0F172A',
          }}>
            最新文章
          </h2>
          <p className="text-xs sm:text-lg max-w-2xl mx-auto" style={{ color: isDark ? 'rgba(255,255,255,0.6)' : 'rgba(15,23,42,0.6)' }}>
            探索 AI 技术的最新趋势和最佳实践
          </p>
        </motion.div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2 sm:gap-6">
          {BLOG_POSTS.map((post, i) => (
            <motion.div
              key={i}
              initial={{ opacity: 0, y: 40 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.6, delay: i * 0.1 }}
              whileHover={{ y: -6 }}
              className="rounded-xl sm:rounded-2xl overflow-hidden cursor-pointer"
              style={{
                background: isDark ? 'rgba(255,255,255,0.03)' : 'rgba(255,255,255,0.7)',
                border: `1px solid ${isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)'}`,
              }}
            >
              <div className="h-20 sm:h-32 relative" style={{
                background: `linear-gradient(135deg, ${post.color}30, ${post.color}10)`,
              }}>
                <div className="absolute top-2 left-2 sm:top-3 sm:left-3 px-1.5 sm:px-2 py-0.5 sm:py-1 rounded-md text-[8px] sm:text-xs font-medium" style={{
                  background: post.color,
                  color: '#FFFFFF',
                }}>
                  {post.category}
                </div>
              </div>

              <div className="p-2.5 sm:p-5">
                <h3 className="text-[11px] sm:text-sm font-semibold mb-1 sm:mb-2 line-clamp-2" style={{ color: isDark ? '#FFFFFF' : '#0F172A' }}>
                  {post.title}
                </h3>

                <p className="text-[9px] sm:text-xs leading-relaxed mb-1.5 sm:mb-3 line-clamp-2" style={{ color: isDark ? 'rgba(255,255,255,0.5)' : 'rgba(15,23,42,0.5)' }}>
                  {post.desc}
                </p>

                <p className="text-[9px] sm:text-xs" style={{ color: isDark ? 'rgba(255,255,255,0.4)' : 'rgba(15,23,42,0.4)' }}>
                  {post.date}
                </p>
              </div>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  )
}

function FooterSection({ isDark, isMobile, navigate }: { isDark: boolean; isMobile: boolean; navigate: any }) {
  return (
    <section className="min-h-screen flex items-center justify-center relative overflow-hidden px-3 sm:px-6 py-8 sm:py-0">
      <div className="max-w-7xl mx-auto w-full">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6 sm:gap-12 mb-8 sm:mb-16">
          <div className="md:col-span-2">
            <div className="flex items-center gap-2 sm:gap-3 mb-3 sm:mb-4">
              <div className="w-8 h-8 sm:w-10 sm:h-10 rounded-xl flex items-center justify-center shadow-lg" style={{ 
                background: 'linear-gradient(135deg, #8B5CF6, #6366F1, #3B82F6)',
              }}>
                <svg width="16" height="16" className="sm:w-5 sm:h-5" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5">
                  <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
                </svg>
              </div>
              <span className="text-base sm:text-xl font-bold" style={{ color: isDark ? '#FFFFFF' : '#0F172A' }}>
                凌点 AI
              </span>
            </div>
            <p className="text-[11px] sm:text-sm leading-relaxed max-w-sm mb-4 sm:mb-6" style={{ color: isDark ? 'rgba(255,255,255,0.6)' : 'rgba(15,23,42,0.6)' }}>
              新一代 AI 模型调度网关平台，统一接入主流 AI 模型，提供智能路由、负载均衡、精确计费等功能。
            </p>
          </div>

          <div>
            <h4 className="text-xs sm:text-sm font-semibold mb-3 sm:mb-4" style={{ color: isDark ? '#FFFFFF' : '#0F172A' }}>产品</h4>
            <ul className="space-y-2 sm:space-y-3">
              {['AI 对话', '模型商店', 'API 密钥', '计费中心'].map((item, i) => (
                <li key={i}>
                  <button onClick={() => navigate(`/${item === 'AI 对话' ? 'chat' : item === '模型商店' ? 'store' : item === 'API 密钥' ? 'api-keys' : 'billing'}`)} className="text-[11px] sm:text-sm transition-colors hover:text-[var(--accent-purple)]" style={{ color: isDark ? 'rgba(255,255,255,0.6)' : 'rgba(15,23,42,0.6)' }}>
                    {item}
                  </button>
                </li>
              ))}
            </ul>
          </div>

          <div>
            <h4 className="text-xs sm:text-sm font-semibold mb-3 sm:mb-4" style={{ color: isDark ? '#FFFFFF' : '#0F172A' }}>支持</h4>
            <ul className="space-y-2 sm:space-y-3">
              {['文档中心', 'API 参考', '常见问题', '联系我们'].map((item, i) => (
                <li key={i}>
                  <span className="text-[11px] sm:text-sm cursor-pointer transition-colors hover:text-[var(--accent-purple)]" style={{ color: isDark ? 'rgba(255,255,255,0.6)' : 'rgba(15,23,42,0.6)' }}>
                    {item}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        </div>

        <div className="pt-6 sm:pt-8 border-t" style={{ borderColor: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)' }}>
          <div className="flex flex-col md:flex-row items-center justify-between gap-4">
            <div className="flex flex-col items-center md:items-start gap-2">
              <p className="text-xs sm:text-sm" style={{ color: isDark ? 'rgba(255,255,255,0.4)' : 'rgba(15,23,42,0.4)' }}>
                © 2024 凌点 AI. All rights reserved.
              </p>
              <a 
                href="https://beian.miit.gov.cn/" 
                target="_blank" 
                rel="noopener noreferrer"
                className="text-[10px] sm:text-xs transition-colors hover:text-[var(--accent-purple)]"
                style={{ color: isDark ? 'rgba(255,255,255,0.3)' : 'rgba(15,23,42,0.3)' }}
              >
                鄂ICP备2025144762号
              </a>
            </div>
            <div className="flex flex-wrap items-center justify-center gap-4 sm:gap-6">
              {[
                { label: '隐私政策', to: '/privacy' },
                { label: '服务条款', to: '/terms' },
                { label: 'Cookie 与本地存储', to: '/privacy#browser-storage' },
              ].map((item) => (
                <Link key={item.to} to={item.to} className="text-[11px] sm:text-sm transition-colors hover:text-[var(--accent-purple)]" style={{ color: isDark ? 'rgba(255,255,255,0.4)' : 'rgba(15,23,42,0.4)' }}>
                  {item.label}
                </Link>
              ))}
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}

// ===================== Main Page =====================

export default function WelcomePage() {
  const { theme } = useThemeStore()
  const { lang } = useI18nStore()
  const { user } = useAppStore()
  const navigate = useNavigate()
  const isDark = theme === 'dark'
  const mouse = useMousePosition()
  const [currentIndex, setCurrentIndex] = useState(0)
  const [isScrolling, setIsScrolling] = useState(false)
  const [touchStart, setTouchStart] = useState<number | null>(null)
  const [touchEnd, setTouchEnd] = useState<number | null>(null)
  const [showTransition, setShowTransition] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  const minSwipeDistance = 50
  const currentIndexRef = useRef(currentIndex)
  const isScrollingRef = useRef(false)
  const wheelTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    currentIndexRef.current = currentIndex
  }, [currentIndex])

  const sections = [
    { id: 'hero' },
    { id: 'features' },
    { id: 'models' },
    { id: 'pricing' },
    { id: 'testimonials' },
    { id: 'faq' },
    { id: 'stats' },
    { id: 'integrations' },
    { id: 'workflow' },
    { id: 'cta' },
    { id: 'footer' },
  ]

  const scrollToSection = useCallback((index: number) => {
    if (index < 0 || index >= sections.length) return
    if (isScrollingRef.current) return

    isScrollingRef.current = true
    setIsScrolling(true)
    setCurrentIndex(index)

    if (wheelTimeoutRef.current) {
      clearTimeout(wheelTimeoutRef.current)
    }

    wheelTimeoutRef.current = setTimeout(() => {
      isScrollingRef.current = false
      setIsScrolling(false)
    }, 800)
  }, [sections.length])

  const handleWheel = useCallback((e: WheelEvent) => {
    e.preventDefault()
    
    if (isScrollingRef.current) return

    if (wheelTimeoutRef.current) {
      clearTimeout(wheelTimeoutRef.current)
    }

    if (e.deltaY > 0) {
      scrollToSection(currentIndexRef.current + 1)
    } else if (e.deltaY < 0) {
      scrollToSection(currentIndexRef.current - 1)
    }
  }, [scrollToSection])

  const handleKeyDown = useCallback((e: KeyboardEvent) => {
    if (e.key === 'ArrowDown' || e.key === 'PageDown') {
      e.preventDefault()
      scrollToSection(currentIndexRef.current + 1)
    } else if (e.key === 'ArrowUp' || e.key === 'PageUp') {
      e.preventDefault()
      scrollToSection(currentIndexRef.current - 1)
    } else if (e.key === 'Home') {
      e.preventDefault()
      scrollToSection(0)
    } else if (e.key === 'End') {
      e.preventDefault()
      scrollToSection(sections.length - 1)
    }
  }, [scrollToSection, sections.length])

  const onTouchStart = useCallback((e: React.TouchEvent) => {
    setTouchEnd(null)
    setTouchStart(e.targetTouches[0].clientY)
  }, [])

  const onTouchMove = useCallback((e: React.TouchEvent) => {
    setTouchEnd(e.targetTouches[0].clientY)
  }, [])

  const onTouchEnd = useCallback(() => {
    if (!touchStart || !touchEnd) return
    
    const distance = touchStart - touchEnd
    const isUpSwipe = distance > minSwipeDistance
    const isDownSwipe = distance < -minSwipeDistance

    if (isUpSwipe) {
      scrollToSection(currentIndexRef.current + 1)
    } else if (isDownSwipe) {
      scrollToSection(currentIndexRef.current - 1)
    }
    
    setTouchStart(null)
    setTouchEnd(null)
  }, [touchStart, touchEnd, scrollToSection])

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    container.addEventListener('wheel', handleWheel, { passive: false })
    window.addEventListener('keydown', handleKeyDown)

    return () => {
      container.removeEventListener('wheel', handleWheel)
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [handleWheel, handleKeyDown])

  useEffect(() => {
    return () => {
      if (wheelTimeoutRef.current) {
        clearTimeout(wheelTimeoutRef.current)
      }
    }
  }, [])

  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const [isMobile, setIsMobile] = useState(false)

  useEffect(() => {
    const checkMobile = () => setIsMobile(window.innerWidth < 768)
    checkMobile()
    window.addEventListener('resize', checkMobile)
    return () => window.removeEventListener('resize', checkMobile)
  }, [])

  useEffect(() => {
    if (mobileMenuOpen) {
      document.body.style.overflow = 'hidden'
    } else {
      document.body.style.overflow = ''
    }
    return () => { document.body.style.overflow = '' }
  }, [mobileMenuOpen])

  const t = (zh: string, en: string) => lang === 'zh' ? zh : en
  const userDisplayName = getUserDisplayName(user, t('第三方账号', 'External account'))

  const handleNavigateToChat = useCallback(() => {
    setShowTransition(true)
  }, [])

  if (showTransition) {
    return <PageTransition targetPath="/chat" />
  }

  return (
    <div ref={containerRef} className="h-screen overflow-hidden relative" style={{ 
      background: isDark ? '#080C14' : '#FAFBFF', 
    }}>
      {/* 高级背景纹理 */}
      <div className="absolute inset-0 pointer-events-none" style={{
        backgroundImage: isDark
          ? `radial-gradient(circle at 1px 1px, rgba(255,255,255,0.05) 1px, transparent 0)`
          : `radial-gradient(circle at 1px 1px, rgba(0,0,0,0.05) 1px, transparent 0)`,
        backgroundSize: '32px 32px',
      }} />

      {/* 动态渐变光晕层 */}
      {!isMobile && (
        <div className="absolute inset-0 overflow-hidden pointer-events-none">
          {/* 左上角紫色光晕 */}
          <motion.div
            className="absolute -top-48 -left-48 w-[800px] h-[800px] rounded-full blur-[100px]"
            style={{
              background: isDark
                ? 'radial-gradient(circle, rgba(139,92,246,0.2) 0%, rgba(99,102,241,0.1) 40%, transparent 70%)'
                : 'radial-gradient(circle, rgba(124,58,237,0.1) 0%, rgba(99,102,241,0.05) 40%, transparent 70%)',
            }}
            animate={{
              x: [0, 80, 0],
              y: [0, 60, 0],
              scale: [1, 1.2, 1],
            }}
            transition={{
              duration: 18,
              repeat: Infinity,
              ease: 'easeInOut',
            }}
          />

          {/* 右下角蓝色光晕 */}
          <motion.div
            className="absolute -bottom-48 -right-48 w-[900px] h-[900px] rounded-full blur-[120px]"
            style={{
              background: isDark
                ? 'radial-gradient(circle, rgba(59,130,246,0.15) 0%, rgba(99,102,241,0.1) 40%, transparent 70%)'
                : 'radial-gradient(circle, rgba(37,99,235,0.08) 0%, rgba(99,102,241,0.04) 40%, transparent 70%)',
            }}
            animate={{
              x: [0, -60, 0],
              y: [0, -80, 0],
              scale: [1, 1.25, 1],
            }}
            transition={{
              duration: 22,
              repeat: Infinity,
              ease: 'easeInOut',
              delay: 3,
            }}
          />

          {/* 中心青色光晕 */}
          <motion.div
            className="absolute top-1/3 left-1/3 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] rounded-full blur-[80px]"
            style={{
              background: isDark
                ? 'radial-gradient(circle, rgba(6,182,212,0.12) 0%, rgba(14,116,144,0.06) 50%, transparent 70%)'
                : 'radial-gradient(circle, rgba(8,145,178,0.06) 0%, rgba(14,116,144,0.03) 50%, transparent 70%)',
            }}
            animate={{
              x: [0, 40, -40, 0],
              y: [0, -30, 30, 0],
              scale: [1, 1.15, 0.95, 1],
            }}
            transition={{
              duration: 25,
              repeat: Infinity,
              ease: 'easeInOut',
              delay: 8,
            }}
          />

          {/* 右上角粉色光晕 */}
          <motion.div
            className="absolute -top-24 -right-24 w-[500px] h-[500px] rounded-full blur-[90px]"
            style={{
              background: isDark
                ? 'radial-gradient(circle, rgba(236,72,153,0.1) 0%, rgba(219,39,119,0.05) 50%, transparent 70%)'
                : 'radial-gradient(circle, rgba(219,39,119,0.05) 0%, rgba(190,24,93,0.02) 50%, transparent 70%)',
            }}
            animate={{
              x: [0, -50, 0],
              y: [0, 40, 0],
              scale: [1, 1.1, 1],
            }}
            transition={{
              duration: 20,
              repeat: Infinity,
              ease: 'easeInOut',
              delay: 12,
            }}
          />

          {/* 左下角靛蓝光晕 */}
          <motion.div
            className="absolute -bottom-24 -left-24 w-[550px] h-[550px] rounded-full blur-[100px]"
            style={{
              background: isDark
                ? 'radial-gradient(circle, rgba(99,102,241,0.12) 0%, rgba(79,70,229,0.06) 50%, transparent 70%)'
                : 'radial-gradient(circle, rgba(79,70,229,0.06) 0%, rgba(67,56,202,0.03) 50%, transparent 70%)',
            }}
            animate={{
              x: [0, 60, 0],
              y: [0, -50, 0],
              scale: [1, 1.2, 1],
            }}
            transition={{
              duration: 24,
              repeat: Infinity,
              ease: 'easeInOut',
              delay: 15,
            }}
          />
        </div>
      )}

      {/* 顶部渐变遮罩 */}
      <div className="absolute top-0 left-0 right-0 h-32 pointer-events-none" style={{
        background: isDark
          ? 'linear-gradient(180deg, rgba(8,12,20,0.8) 0%, transparent 100%)'
          : 'linear-gradient(180deg, rgba(250,251,255,0.8) 0%, transparent 100%)',
      }} />

      {/* 底部渐变遮罩 */}
      <div className="absolute bottom-0 left-0 right-0 h-32 pointer-events-none" style={{
        background: isDark
          ? 'linear-gradient(0deg, rgba(8,12,20,0.8) 0%, transparent 100%)'
          : 'linear-gradient(0deg, rgba(250,251,255,0.8) 0%, transparent 100%)',
      }} />

      {/* ==================== NAVBAR ==================== */}
      <header className="fixed top-0 left-0 right-0 z-50" style={{
        background: isDark ? 'rgba(8,12,20,0.85)' : 'rgba(250,251,255,0.85)',
        backdropFilter: 'blur(20px) saturate(180%)',
        WebkitBackdropFilter: 'blur(20px) saturate(180%)',
        borderBottom: `1px solid ${isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.05)'}`,
      }}>
        <div className="max-w-7xl mx-auto px-4 sm:px-6 h-14 sm:h-16 flex items-center justify-between">
          <motion.div 
            className="flex items-center gap-2 sm:gap-3 cursor-pointer"
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            onClick={() => scrollToSection(0)}
          >
            <div className="w-8 h-8 sm:w-9 sm:h-9 rounded-xl flex items-center justify-center shadow-lg" style={{ 
              background: 'linear-gradient(135deg, #8B5CF6, #6366F1, #3B82F6)',
              boxShadow: '0 4px 20px rgba(139,92,246,0.3)',
            }}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
              </svg>
            </div>
            <span className="text-sm sm:text-base font-bold tracking-tight" style={{ color: isDark ? '#FFFFFF' : '#0F172A' }}>
              {t('凌点 AI', 'Lingdian AI')}
            </span>
          </motion.div>

          <nav className="hidden md:flex items-center gap-6 lg:gap-8">
            {[
              { key: '功能', en: 'Features', index: 1 },
              { key: '模型', en: 'Models', index: 2 },
              { key: '定价', en: 'Pricing', index: 3 },
              { key: '评价', en: 'Reviews', index: 4 },
              { key: 'FAQ', en: 'FAQ', index: 5 },
              { key: '数据', en: 'Stats', index: 6 },
              { key: '集成', en: 'Integrations', index: 7 },
              { key: '流程', en: 'Workflow', index: 8 },
            ].map((item, i) => (
              <button
                key={i}
                onClick={() => scrollToSection(item.index)}
                className="text-xs font-medium transition-all duration-300 relative group whitespace-nowrap"
                style={{
                  color: currentIndex === item.index
                    ? (isDark ? '#A78BFA' : '#7C3AED')
                    : (isDark ? 'rgba(255,255,255,0.7)' : 'rgba(15,23,42,0.6)'),
                  fontSize: currentIndex === item.index ? '13px' : '12px',
                  fontWeight: currentIndex === item.index ? 600 : 500,
                }}
              >
                {t(item.key, item.en)}
                <motion.span
                  className="absolute -bottom-1 left-0 h-0.5 bg-[var(--accent-purple)]"
                  initial={false}
                  animate={{
                    width: currentIndex === item.index ? '100%' : '0%',
                  }}
                  transition={{ duration: 0.3 }}
                />
              </button>
            ))}
          </nav>

          <div className="flex items-center gap-2 sm:gap-3">
            {user ? (
              <>
                <GlowButton onClick={handleNavigateToChat} color="#8B5CF6" size="sm">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="sm:mr-1">
                    <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z" />
                  </svg>
                  <span className="hidden sm:inline">AI 对话</span>
                </GlowButton>
                <div className="flex items-center gap-2 sm:gap-3">
                  <span className="text-xs sm:text-sm font-medium hidden lg:inline" style={{ color: isDark ? 'rgba(255,255,255,0.8)' : 'rgba(15,23,42,0.7)' }}>
                    {userDisplayName}
                  </span>
                  <div className="w-7 h-7 sm:w-8 sm:h-8 rounded-full flex items-center justify-center text-xs font-bold" style={{
                    background: 'linear-gradient(135deg, #8B5CF6, #6366F1)',
                    color: '#FFFFFF',
                  }}>
                    {userDisplayName.charAt(0).toUpperCase()}
                  </div>
                </div>
              </>
            ) : (
              <>
                <motion.button 
                  onClick={() => navigate('/login')}
                  className="px-3 sm:px-5 py-1.5 sm:py-2 rounded-lg sm:rounded-xl text-xs font-medium transition-all"
                  style={{ color: isDark ? 'rgba(255,255,255,0.8)' : 'rgba(15,23,42,0.7)' }}
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                >
                  {t('登录', 'Login')}
                </motion.button>
                <GlowButton onClick={() => navigate('/register')} color="#8B5CF6" size="sm">
                  <span className="hidden sm:inline">{t('免费开始', 'Start Free')}</span>
                  <span className="sm:hidden">注册</span>
                </GlowButton>
              </>
            )}

            {/* 移动端汉堡菜单按钮 */}
            <button
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              className="md:hidden p-2 rounded-lg transition-colors"
              style={{ color: isDark ? 'rgba(255,255,255,0.8)' : 'rgba(15,23,42,0.7)' }}
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                {mobileMenuOpen ? (
                  <>
                    <line x1="18" y1="6" x2="6" y2="18" />
                    <line x1="6" y1="6" x2="18" y2="18" />
                  </>
                ) : (
                  <>
                    <line x1="3" y1="6" x2="21" y2="6" />
                    <line x1="3" y1="12" x2="21" y2="12" />
                    <line x1="3" y1="18" x2="21" y2="18" />
                  </>
                )}
              </svg>
            </button>
          </div>
        </div>
      </header>

      {/* 移动端菜单 */}
      <AnimatePresence>
        {mobileMenuOpen && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
            className="fixed inset-0 z-40 md:hidden"
            style={{
              top: '56px',
              background: isDark ? 'rgba(8,12,20,0.98)' : 'rgba(250,251,255,0.98)',
              backdropFilter: 'blur(20px)',
            }}
          >
            <div className="flex flex-col items-center justify-center h-full gap-4 p-6">
              {[
                { key: '首页', en: 'Home', index: 0 },
                { key: '功能', en: 'Features', index: 1 },
                { key: '模型', en: 'Models', index: 2 },
                { key: '定价', en: 'Pricing', index: 3 },
                { key: '评价', en: 'Reviews', index: 4 },
                { key: 'FAQ', en: 'FAQ', index: 5 },
                { key: '数据', en: 'Stats', index: 6 },
                { key: '集成', en: 'Integrations', index: 7 },
                { key: '流程', en: 'Workflow', index: 8 },
              ].map((item, i) => (
                <motion.button
                  key={i}
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: i * 0.05 }}
                  onClick={() => {
                    scrollToSection(item.index)
                    setMobileMenuOpen(false)
                  }}
                  className="text-lg font-medium py-3 px-6 rounded-xl transition-all w-full text-center"
                  style={{
                    color: currentIndex === item.index
                      ? (isDark ? '#A78BFA' : '#7C3AED')
                      : (isDark ? 'rgba(255,255,255,0.8)' : 'rgba(15,23,42,0.7)'),
                    background: currentIndex === item.index
                      ? (isDark ? 'rgba(139,92,246,0.1)' : 'rgba(124,58,237,0.08)')
                      : 'transparent',
                  }}
                >
                  {t(item.key, item.en)}
                </motion.button>
              ))}
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* ==================== SECTIONS ==================== */}
      <AnimatePresence initial={false} mode="wait">
        <motion.div
          key={currentIndex}
          initial={{ opacity: 0, y: 50 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -50 }}
          transition={{ 
            duration: 0.5, 
            ease: [0.25, 0.46, 0.45, 0.94]
          }}
          className="h-full"
          onTouchStart={onTouchStart}
          onTouchMove={onTouchMove}
          onTouchEnd={onTouchEnd}
        >
          {currentIndex === 0 && <HeroSection isDark={isDark} isMobile={isMobile} mouse={mouse} navigate={navigate} user={user} onNavigateToChat={handleNavigateToChat} />}
          {currentIndex === 1 && <FeaturesSection isDark={isDark} isMobile={isMobile} />}
          {currentIndex === 2 && <ModelsSection isDark={isDark} isMobile={isMobile} />}
          {currentIndex === 3 && <PricingSection isDark={isDark} isMobile={isMobile} navigate={navigate} />}
          {currentIndex === 4 && <TestimonialsSection isDark={isDark} isMobile={isMobile} />}
          {currentIndex === 5 && <FAQSection isDark={isDark} isMobile={isMobile} />}
          {currentIndex === 6 && <StatsSection isDark={isDark} isMobile={isMobile} />}
          {currentIndex === 7 && <IntegrationsSection isDark={isDark} isMobile={isMobile} />}
          {currentIndex === 8 && <WorkflowSection isDark={isDark} isMobile={isMobile} />}
          {currentIndex === 9 && <CTASection isDark={isDark} isMobile={isMobile} navigate={navigate} user={user} onNavigateToChat={handleNavigateToChat} />}
          {currentIndex === 10 && <FooterSection isDark={isDark} isMobile={isMobile} navigate={navigate} />}
        </motion.div>
      </AnimatePresence>

      {/* 高级滚动指示器 - 流体轨道 (桌面端显示) */}
      <div className="hidden md:flex fixed right-8 top-1/2 -translate-y-1/2 z-50 flex-col items-center gap-6">
        {/* 轨道容器 */}
        <div className="relative flex flex-col items-center gap-3">
          {sections.map((_, index) => {
            const isActive = index === currentIndex
            return (
              <button
                key={index}
                onClick={() => scrollToSection(index)}
                className="relative w-8 h-8 flex items-center justify-center group"
              >
                {/* 激活状态背景光晕 */}
                {isActive && (
                  <motion.div
                    className="absolute inset-0 rounded-lg"
                    style={{
                      background: 'linear-gradient(135deg, rgba(139,92,246,0.15), rgba(99,102,241,0.15))',
                      boxShadow: '0 0 20px rgba(139,92,246,0.3)',
                    }}
                    layoutId="activeIndicator"
                    transition={{ type: 'spring', stiffness: 300, damping: 30 }}
                  />
                )}
                
                {/* 横线指示器 */}
                <motion.div
                  className="relative h-px"
                  style={{
                    width: isActive ? '24px' : '12px',
                    background: isActive
                      ? 'linear-gradient(90deg, transparent, #8B5CF6, transparent)'
                      : isDark ? 'rgba(255,255,255,0.15)' : 'rgba(15,23,42,0.15)',
                  }}
                  transition={{ duration: 0.4, ease: 'easeOut' }}
                />
                
                {/* 悬停效果 */}
                <motion.div
                  className="absolute inset-0 rounded-lg"
                  style={{
                    background: isDark ? 'rgba(255,255,255,0.05)' : 'rgba(15,23,42,0.05)',
                  }}
                  initial={{ opacity: 0, scale: 0.8 }}
                  whileHover={{ opacity: 1, scale: 1 }}
                  transition={{ duration: 0.2 }}
                />
              </button>
            )
          })}
        </div>

        {/* 当前进度数字 */}
        <div className="flex items-baseline gap-0.5">
          <motion.span
            className="text-sm font-semibold tabular-nums"
            style={{
              background: 'linear-gradient(135deg, #8B5CF6, #6366F1)',
              WebkitBackgroundClip: 'text',
              WebkitTextFillColor: 'transparent',
            }}
            key={currentIndex}
            initial={{ y: 8, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            transition={{ duration: 0.3, ease: 'easeOut' }}
          >
            {currentIndex + 1}
          </motion.span>
          <span className="text-xs" style={{ color: isDark ? 'rgba(255,255,255,0.25)' : 'rgba(15,23,42,0.25)' }}>
            / {sections.length}
          </span>
        </div>
      </div>
    </div>
  )
}
