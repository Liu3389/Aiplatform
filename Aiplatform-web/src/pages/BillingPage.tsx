import { useState, useEffect } from 'react'
import { marketplaceApi } from '@/services/api'
import { motion } from 'framer-motion'
import { useThemeStore } from '@/stores/themeStore'
import { useI18nStore } from '@/stores/i18nStore'

type ProviderKey = {
  id: number;
  providerName: string;
  providerNameEn: string;
  description: string;
  apiKey: string;
  baseUrl: string;
  pricePerCall: number;
  pricePerMonth: number;
  status: number;
}

function getColors(isDark: boolean) {
  return isDark
    ? ['#8B5CF6', '#3B82F6', '#06B6D4', '#10B981', '#F59E0B', '#F43F5E']
    : ['#7C3AED', '#2563EB', '#0891B2', '#059669', '#D97706', '#E11D48']
}

const PROVIDERS = [
  {
    id: 'openai',
    nameZh: 'OpenAI',
    nameEn: 'OpenAI',
    logo: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z',
    inputPrice: 10.50,
    outputPrice: 31.50,
    features: ['GPT-4o', 'GPT-4 Turbo', 'GPT-3.5 Turbo', 'DALL·E 3'],
    status: 1,
  },
  {
    id: 'anthropic',
    nameZh: 'Anthropic',
    nameEn: 'Anthropic',
    logo: 'M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z',
    inputPrice: 105.00,
    outputPrice: 52.50,
    features: ['Claude 3.5 Sonnet', 'Claude 3 Opus', 'Claude 3 Haiku'],
    status: 1,
  },
  {
    id: 'google',
    nameZh: 'Google',
    nameEn: 'Google',
    logo: 'M21 12a9 9 0 01-9 9m9-9a9 9 0 00-9-9m9 9H3m9 9a9 9 0 01-9-9m9 9c1.657 0 3-4.03 3-9s-1.343-9-3-9m0 18c-1.657 0-3-4.03-3-9s1.343-9 3-9m-9 9a9 9 0 019-9',
    inputPrice: 3.50,
    outputPrice: 10.50,
    features: ['Gemini 2.0 Flash', 'Gemini 2.0 Pro', 'Gemini 1.5 Pro'],
    status: 1,
  },
  {
    id: 'deepseek',
    nameZh: 'DeepSeek',
    nameEn: 'DeepSeek',
    logo: 'M13 10V3L4 14h7v7l9-11h-7z',
    inputPrice: 1.00,
    outputPrice: 2.00,
    features: ['DeepSeek V3', 'DeepSeek R1', 'DeepSeek Coder'],
    status: 1,
  },
]

const POINTS_MODELS = [
  { id: 'deepseek-chat', nameZh: 'DeepSeek Chat', nameEn: 'DeepSeek Chat', pointsPerCall: 1, description: '基础对话模型' },
  { id: 'deepseek-reasoner', nameZh: 'DeepSeek Reasoner', nameEn: 'DeepSeek Reasoner', pointsPerCall: 2, description: '推理增强模型' },
  { id: 'deepseek-v3', nameZh: 'DeepSeek V3', nameEn: 'DeepSeek V3', pointsPerCall: 3, description: '高级通用模型' },
  { id: 'deepseek-pro', nameZh: 'DeepSeek Pro', nameEn: 'DeepSeek Pro', pointsPerCall: 5, description: '旗舰级模型' },
]

const FAQ_ITEMS = [
  {
    qZh: 'AI 对话如何使用点数？',
    qEn: 'How does AI Chat use points?',
    aZh: '每次对话消耗不同数量的点数（1～5 点不等，取决于模型能力），从您的平台点数余额中扣除。',
    aEn: 'Each conversation consumes different points (1-5 points depending on model capability), deducted from your platform points balance.',
  },
  {
    qZh: '如何获取点数？',
    qEn: 'How to get points?',
    aZh: '您可以通过充值获得点数。最低充值金额为 ¥1，充值后点数立即到账。',
    aEn: 'You can get points by recharging. Minimum recharge amount is ¥1, points are credited immediately.',
  },
  {
    qZh: '购买 API Key 后如何使用？',
    qEn: 'How to use after purchasing an API Key?',
    aZh: '购买后，您将获得该厂商的专属 API Key，可在任何支持该厂商 API 的平台或项目中使用。',
    aEn: 'After purchase, you will receive a dedicated API Key that can be used on any platform or project supporting that vendor\'s API.',
  },
  {
    qZh: 'API Key 有有效期吗？',
    qEn: 'Do API Keys have an expiration date?',
    aZh: 'API Key 按月订阅，订阅期间内可无限次调用该厂商的模型。到期后可续费继续使用。',
    aEn: 'API Keys are subscribed monthly. During the subscription period, you can make unlimited calls to the vendor\'s models. You can renew after expiration.',
  },
  {
    qZh: '可以退款吗？',
    qEn: 'Can I get a refund?',
    aZh: '购买后 7 天内未使用可申请退款，使用后不支持退款。',
    aEn: 'You can request a refund within 7 days of purchase if unused. No refunds after use.',
  },
]

export default function BillingPage() {
  const { theme } = useThemeStore()
  const { lang } = useI18nStore()
  const isDark = theme === 'dark'
  const colors = getColors(isDark)
  const [activeTab, setActiveTab] = useState<'points' | 'apikey'>('points')

  const [providers, setProviders] = useState<ProviderKey[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    (async () => {
      try {
        const keys = await marketplaceApi.listKeys()
        setProviders(keys || [])
      } catch {
        setProviders(PROVIDERS as any)
      }
      setLoading(false)
    })()
  }, [])

  if (loading) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="w-8 h-8 border-2 border-brand-purple/30 border-t-brand-purple rounded-full animate-spin" />
      </div>
    )
  }

  return (
    <div className="p-4 sm:p-6 lg:p-8 max-w-6xl mx-auto overflow-y-auto h-full space-y-6">
      <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }}>
        <h1 className="text-xl sm:text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
          {lang === 'zh' ? '参考文档' : 'Documentation'}
        </h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-dim)' }}>
          {lang === 'zh' ? '平台计费规则、API 使用指南与常见问题' : 'Platform billing rules, API usage guide, and FAQs'}
        </p>
      </motion.div>

      {/* 业务线切换 Tab */}
      <div className="flex gap-2">
        <button
          onClick={() => setActiveTab('points')}
          className={`flex-1 py-3 px-4 rounded-xl text-sm font-medium transition-all ${activeTab === 'points' ? 'shadow-lg' : ''}`}
          style={{
            background: activeTab === 'points' ? 'var(--accent-purple)' : 'var(--bg-card)',
            color: activeTab === 'points' ? '#fff' : 'var(--text-dim)',
            border: activeTab === 'points' ? 'none' : '1px solid var(--border-color)',
          }}
        >
          <div className="flex items-center justify-center gap-2">
            <span>🪙</span>
            <span>{lang === 'zh' ? 'AI 对话计费' : 'AI Chat Billing'}</span>
          </div>
        </button>
        <button
          onClick={() => setActiveTab('apikey')}
          className={`flex-1 py-3 px-4 rounded-xl text-sm font-medium transition-all ${activeTab === 'apikey' ? 'shadow-lg' : ''}`}
          style={{
            background: activeTab === 'apikey' ? 'var(--accent-blue)' : 'var(--bg-card)',
            color: activeTab === 'apikey' ? '#fff' : 'var(--text-dim)',
            border: activeTab === 'apikey' ? 'none' : '1px solid var(--border-color)',
          }}
        >
          <div className="flex items-center justify-center gap-2">
            <span>🔑</span>
            <span>{lang === 'zh' ? '模型商店计费' : 'Model Store Billing'}</span>
          </div>
        </button>
      </div>

      {/* API 使用指南 */}
      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}
        className="rounded-2xl p-6 border"
        style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}
      >
        <div className="flex items-center gap-3 mb-4">
          <div className="w-10 h-10 rounded-xl flex items-center justify-center" style={{ background: colors[0] + '20' }}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke={colors[0]} strokeWidth="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
          </div>
          <div>
            <div className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
              {lang === 'zh' ? 'API 快速开始' : 'API Quick Start'}
            </div>
            <div className="text-xs" style={{ color: 'var(--text-dim)' }}>
              {lang === 'zh' ? '三步接入凌点 AI 网关' : 'Get started with Lingdian AI Gateway in 3 steps'}
            </div>
          </div>
        </div>
        <div className="grid sm:grid-cols-3 gap-4">
          {[
            { step: '01', titleZh: '获取 API Key', titleEn: 'Get API Key', descZh: '在 API Keys 页面创建您的专属访问密钥', descEn: 'Create your access key on the API Keys page' },
            { step: '02', titleZh: '配置请求头', titleEn: 'Configure Headers', descZh: '在 HTTP 请求中添加 Authorization 头', descEn: 'Add Authorization header to your HTTP requests' },
            { step: '03', titleZh: '发起调用', titleEn: 'Make Requests', descZh: '使用 OpenAI 兼容格式调用 AI 模型', descEn: 'Call AI models using OpenAI-compatible format' },
          ].map((item, i) => (
            <div key={i} className="flex gap-3 p-4 rounded-xl" style={{ background: isDark ? 'rgba(255,255,255,0.02)' : 'rgba(0,0,0,0.02)', border: `1px solid ${isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)'}` }}>
              <span className="text-lg font-bold text-gradient flex-shrink-0">{item.step}</span>
              <div>
                <div className="text-xs font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? item.titleZh : item.titleEn}</div>
                <div className="text-[10px] leading-relaxed" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? item.descZh : item.descEn}</div>
              </div>
            </div>
          ))}
        </div>
      </motion.div>

      {/* AI 对话计费业务线 */}
      {activeTab === 'points' && (
        <div className="space-y-6">
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}
            className="rounded-2xl p-6 border"
            style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}
          >
            <div className="flex items-center gap-3 mb-6">
              <div className="w-10 h-10 rounded-xl flex items-center justify-center" style={{ background: colors[0] + '20' }}>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke={colors[0]} strokeWidth="2"><path d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"/></svg>
              </div>
              <div>
                <div className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                  {lang === 'zh' ? 'AI 对话 - 按点数计费' : 'AI Chat - Pay with Points'}
                </div>
                <div className="text-xs" style={{ color: 'var(--text-dim)' }}>
                  {lang === 'zh' ? '所有 AI 对话按 Token 精确计费，每次对话消耗固定点数' : 'All AI chats billed by token, each chat consumes fixed points'}
                </div>
              </div>
            </div>

            <div className="grid sm:grid-cols-3 gap-4 mb-6">
              {[
                { icon: '🎯', titleZh: '按点数付费', titleEn: 'Pay with Points', descZh: '每次对话消耗 1～5 点', descEn: 'Each chat consumes 1-5 points' },
                { icon: '🔄', titleZh: '预扣余额机制', titleEn: 'Pre-deduct Balance', descZh: '请求前预扣最大预估费用，完成后退还差额', descEn: 'Pre-deduct max estimated fee, refund difference after completion' },
                { icon: '⚡', titleZh: 'Redis Lua 原子结算', titleEn: 'Redis Lua Atomic', descZh: '高并发下零超卖，精确到每个 Token', descEn: 'Zero oversell under high concurrency, accurate to each token' },
              ].map((item, i) => (
                <div key={i} className="flex gap-3 p-4 rounded-xl" style={{ background: isDark ? 'rgba(255,255,255,0.02)' : 'rgba(0,0,0,0.02)', border: `1px solid ${isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)'}` }}>
                  <span className="text-2xl">{item.icon}</span>
                  <div>
                    <div className="text-xs font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? item.titleZh : item.titleEn}</div>
                    <div className="text-[10px] leading-relaxed" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? item.descZh : item.descEn}</div>
                  </div>
                </div>
              ))}
            </div>
          </motion.div>

          {/* 模型点数明细表 */}
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}
            className="rounded-2xl border overflow-hidden"
            style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}
          >
            <div className="p-6 border-b" style={{ borderColor: 'var(--border-color)' }}>
              <h3 className="text-sm font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>
                {lang === 'zh' ? '模型点数消耗明细' : 'Model Point Consumption Details'}
              </h3>
              <p className="text-xs" style={{ color: 'var(--text-dim)' }}>
                {lang === 'zh' ? '点数单位：整数点（无小数位）' : 'Points unit: Integer (no decimals)'}
              </p>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-xs">
                <thead>
                  <tr style={{ borderBottom: `1px solid var(--border-color)` }}>
                    <th className="text-left py-3 px-4" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '模型' : 'Model'}</th>
                    <th className="text-left py-3 px-4" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '描述' : 'Description'}</th>
                    <th className="text-right py-3 px-4" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '每次对话消耗' : 'Points per Chat'}</th>
                  </tr>
                </thead>
                <tbody>
                  {POINTS_MODELS.map((m, i) => (
                    <tr key={m.id} style={{ borderBottom: `1px solid var(--border-color)` }}>
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-3">
                          <div className="w-8 h-8 rounded-lg flex items-center justify-center font-bold text-xs" style={{ background: colors[i % colors.length] + '15', color: colors[i % colors.length] }}>
                            {m.id.slice(0, 2).toUpperCase()}
                          </div>
                          <span className="font-medium" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? m.nameZh : m.nameEn}</span>
                        </div>
                      </td>
                      <td className="py-3 px-4" style={{ color: 'var(--text-dim)' }}>{m.description}</td>
                      <td className="py-3 px-4 text-right">
                        <span className="px-2 py-1 rounded-md text-xs font-bold" style={{ background: colors[i % colors.length] + '20', color: colors[i % colors.length] }}>
                          {m.pointsPerCall} {lang === 'zh' ? '点' : 'pts'}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </motion.div>
        </div>
      )}

      {/* 模型商店计费业务线 */}
      {activeTab === 'apikey' && (
        <div className="space-y-6">
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}
            className="grid md:grid-cols-2 gap-4">
            <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
              <div className="flex items-center gap-3 mb-4">
                <div className="w-10 h-10 rounded-xl flex items-center justify-center" style={{ background: colors[0] + '20' }}>
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke={colors[0]} strokeWidth="2"><path d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
                </div>
                <div>
                  <div className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                    {lang === 'zh' ? '按 Token 计费' : 'Pay-Per-Token'}
                  </div>
                  <div className="text-xs" style={{ color: 'var(--text-dim)' }}>
                    {lang === 'zh' ? '输入/输出分别计价' : 'Input/output priced separately'}
                  </div>
                </div>
              </div>
              <ul className="space-y-2 text-xs" style={{ color: 'var(--text-dim)' }}>
                <li className="flex items-start gap-2">
                  <span style={{ color: 'var(--accent-emerald)' }}>◆</span>
                  <span>{lang === 'zh' ? '按输入/输出 Token 分别计价' : 'Input/output tokens priced separately'}</span>
                </li>
                <li className="flex items-start gap-2">
                  <span style={{ color: 'var(--accent-emerald)' }}>◆</span>
                  <span>{lang === 'zh' ? '不同模型价格不同' : 'Different models have different prices'}</span>
                </li>
                <li className="flex items-start gap-2">
                  <span style={{ color: 'var(--accent-emerald)' }}>◆</span>
                  <span>{lang === 'zh' ? '从平台余额中自动扣除（人民币）' : 'Auto-deducted from platform balance (CNY)'}</span>
                </li>
              </ul>
            </div>

            <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
              <div className="flex items-center gap-3 mb-4">
                <div className="w-10 h-10 rounded-xl flex items-center justify-center" style={{ background: colors[1] + '20' }}>
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke={colors[1]} strokeWidth="2"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
                </div>
                <div>
                  <div className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                    {lang === 'zh' ? '包月订阅' : 'Monthly Subscription'}
                  </div>
                  <div className="text-xs" style={{ color: 'var(--text-dim)' }}>
                    {lang === 'zh' ? '适合高频用户' : 'For high-frequency users'}
                  </div>
                </div>
              </div>
              <ul className="space-y-2 text-xs" style={{ color: 'var(--text-dim)' }}>
                <li className="flex items-start gap-2">
                  <span style={{ color: 'var(--accent-blue)' }}>◆</span>
                  <span>{lang === 'zh' ? '固定月费，无限次调用' : 'Fixed monthly fee, unlimited calls'}</span>
                </li>
                <li className="flex items-start gap-2">
                  <span style={{ color: 'var(--accent-blue)' }}>◆</span>
                  <span>{lang === 'zh' ? '专属 API Key，可在任意平台使用' : 'Dedicated API Key, usable on any platform'}</span>
                </li>
                <li className="flex items-start gap-2">
                  <span style={{ color: 'var(--accent-blue)' }}>◆</span>
                  <span>{lang === 'zh' ? '到期自动续费，可随时取消' : 'Auto-renewal, cancel anytime'}</span>
                </li>
              </ul>
            </div>
          </motion.div>

          {/* 厂商定价表 */}
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}
            className="rounded-2xl border overflow-hidden" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
            <div className="p-6 border-b" style={{ borderColor: 'var(--border-color)' }}>
              <h3 className="text-sm font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>
                {lang === 'zh' ? '厂商 Token 定价明细' : 'Vendor Token Pricing Details'}
              </h3>
              <p className="text-xs" style={{ color: 'var(--text-dim)' }}>
                {lang === 'zh' ? '价格单位：人民币 (CNY) / 1K tokens' : 'Unit: Chinese Yuan (CNY) / 1K tokens'}
              </p>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-xs">
                <thead>
                  <tr style={{ borderBottom: `1px solid var(--border-color)` }}>
                    <th className="text-left py-3 px-4" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '厂商' : 'Vendor'}</th>
                    <th className="text-left py-3 px-4" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '包含模型' : 'Models'}</th>
                    <th className="text-right py-3 px-4" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '输入价格' : 'Input Price'}</th>
                    <th className="text-right py-3 px-4" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '输出价格' : 'Output Price'}</th>
                    <th className="text-center py-3 px-4" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '状态' : 'Status'}</th>
                  </tr>
                </thead>
                <tbody>
                  {providers.filter((p: any) => p.status === 1).map((p: any, i: number) => (
                    <tr key={p.id || i} style={{ borderBottom: `1px solid var(--border-color)` }}>
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-3">
                          <div className="w-8 h-8 rounded-lg flex items-center justify-center" style={{ background: colors[i % colors.length] + '15' }}>
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke={colors[i % colors.length]} strokeWidth="2"><path d={p.logo || 'M13 10V3L4 14h7v7l9-11h-7z'}/></svg>
                          </div>
                          <span className="font-medium" style={{ color: 'var(--text-primary)' }}>{p.providerName || p.providerNameEn || p.nameZh || p.nameEn}</span>
                        </div>
                      </td>
                      <td className="py-3 px-4" style={{ color: 'var(--text-dim)' }}>
                        {(p.features || []).slice(0, 2).join(', ')}
                        {(p.features || []).length > 2 ? ` +${(p.features || []).length - 2}` : ''}
                      </td>
                      <td className="py-3 px-4 text-right font-mono" style={{ color: 'var(--accent-emerald)' }}>¥{(p.inputPrice || 0).toFixed(2)}</td>
                      <td className="py-3 px-4 text-right font-mono" style={{ color: 'var(--accent-rose)' }}>¥{(p.outputPrice || 0).toFixed(2)}</td>
                      <td className="py-3 px-4 text-center">
                        <span className="w-2 h-2 rounded-full inline-block" style={{ background: p.status === 1 ? 'var(--accent-emerald)' : 'var(--accent-rose)' }} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </motion.div>
        </div>
      )}

      {/* FAQ */}
      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.25 }}
        className="space-y-3">
        <h3 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
          {lang === 'zh' ? '常见问题' : 'FAQ'}
        </h3>
        {FAQ_ITEMS.map((item, i) => (
          <div key={i} className="rounded-2xl p-5 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
            <div className="flex items-start gap-3">
              <span className="text-sm font-bold flex-shrink-0" style={{ color: 'var(--accent-purple)' }}>Q</span>
              <div>
                <div className="text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? item.qZh : item.qEn}</div>
                <div className="text-xs leading-relaxed" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? item.aZh : item.aEn}</div>
              </div>
            </div>
          </div>
        ))}
      </motion.div>

      {/* 状态提示 */}
      {providers.length === 0 && (
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}
          className="rounded-2xl p-8 border text-center" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
          <div className="text-4xl mb-3">🔑</div>
          <h3 className="text-base font-semibold mb-2" style={{ color: 'var(--text-primary)' }}>
            {lang === 'zh' ? '模型商店即将上线' : 'Model Store Coming Soon'}
          </h3>
          <p className="text-sm" style={{ color: 'var(--text-dim)' }}>
            {lang === 'zh' ? '我们正在与各大 AI 厂商洽谈，敬请期待' : 'We are negotiating with major AI vendors, stay tuned'}
          </p>
        </motion.div>
      )}
    </div>
  )
}
