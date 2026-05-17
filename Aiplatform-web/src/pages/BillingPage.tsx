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
    pricePerCall: 0.50,
    pricePerMonth: 29.90,
    features: ['GPT-4o', 'GPT-4 Turbo', 'GPT-3.5 Turbo', 'DALL·E 3'],
    status: 1,
  },
  {
    id: 'anthropic',
    nameZh: 'Anthropic',
    nameEn: 'Anthropic',
    logo: 'M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z',
    pricePerCall: 0.80,
    pricePerMonth: 39.90,
    features: ['Claude 3.5 Sonnet', 'Claude 3 Opus', 'Claude 3 Haiku'],
    status: 1,
  },
  {
    id: 'google',
    nameZh: 'Google',
    nameEn: 'Google',
    logo: 'M21 12a9 9 0 01-9 9m9-9a9 9 0 00-9-9m9 9H3m9 9a9 9 0 01-9-9m9 9c1.657 0 3-4.03 3-9s-1.343-9-3-9m0 18c-1.657 0-3-4.03-3-9s1.343-9 3-9m-9 9a9 9 0 019-9',
    pricePerCall: 0.40,
    pricePerMonth: 19.90,
    features: ['Gemini 2.0 Flash', 'Gemini 2.0 Pro', 'Gemini 1.5 Pro'],
    status: 1,
  },
  {
    id: 'deepseek',
    nameZh: 'DeepSeek',
    nameEn: 'DeepSeek',
    logo: 'M13 10V3L4 14h7v7l9-11h-7z',
    pricePerCall: 0.20,
    pricePerMonth: 9.90,
    features: ['DeepSeek V3', 'DeepSeek R1', 'DeepSeek Coder'],
    status: 1,
  },
]

const FAQ_ITEMS = [
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
    qZh: '按次计费和包月有什么区别？',
    qEn: 'What\'s the difference between pay-per-call and monthly subscription?',
    aZh: '按次计费适合偶尔使用的用户，每次调用单独收费。包月适合高频用户，支付固定月费后可无限次调用。',
    aEn: 'Pay-per-call is suitable for occasional users, with each call charged separately. Monthly subscription is for high-frequency users who pay a fixed fee for unlimited calls.',
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
          {lang === 'zh' ? '模型商店计费策略' : 'Model Store Pricing'}
        </h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-dim)' }}>
          {lang === 'zh' ? '购买全球顶尖 AI 厂商的 API Key，按需付费或包月订阅' : 'Purchase API Keys from top global AI vendors, pay-per-use or monthly subscription'}
        </p>
      </motion.div>

      {/* 计费模式说明 */}
      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}
        className="grid md:grid-cols-2 gap-4">
        <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
          <div className="flex items-center gap-3 mb-4">
            <div className="w-10 h-10 rounded-xl flex items-center justify-center" style={{ background: colors[0] + '20' }}>
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke={colors[0]} strokeWidth="2"><path d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
            </div>
            <div>
              <div className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                {lang === 'zh' ? '按次计费' : 'Pay-Per-Call'}
              </div>
              <div className="text-xs" style={{ color: 'var(--text-dim)' }}>
                {lang === 'zh' ? '适合偶尔使用' : 'For occasional use'}
              </div>
            </div>
          </div>
          <ul className="space-y-2 text-xs" style={{ color: 'var(--text-dim)' }}>
            <li className="flex items-start gap-2">
              <span style={{ color: 'var(--accent-emerald)' }}>◆</span>
              <span>{lang === 'zh' ? '每次调用单独收费，用多少付多少' : 'Each call charged separately, pay as you go'}</span>
            </li>
            <li className="flex items-start gap-2">
              <span style={{ color: 'var(--accent-emerald)' }}>◆</span>
              <span>{lang === 'zh' ? '无需订阅，灵活自由' : 'No subscription required, flexible'}</span>
            </li>
            <li className="flex items-start gap-2">
              <span style={{ color: 'var(--accent-emerald)' }}>◆</span>
              <span>{lang === 'zh' ? '从平台余额中自动扣除' : 'Auto-deducted from platform balance'}</span>
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

      {/* 厂商标价表 */}
      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}
        className="rounded-2xl border overflow-hidden" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
        <div className="p-6 border-b" style={{ borderColor: 'var(--border-color)' }}>
          <h3 className="text-sm font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>
            {lang === 'zh' ? '厂商定价明细' : 'Vendor Pricing Details'}
          </h3>
          <p className="text-xs" style={{ color: 'var(--text-dim)' }}>
            {lang === 'zh' ? '价格单位：人民币 (CNY)' : 'Unit: Chinese Yuan (CNY)'}
          </p>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-xs">
            <thead>
              <tr style={{ borderBottom: `1px solid var(--border-color)` }}>
                <th className="text-left py-3 px-4" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '厂商' : 'Vendor'}</th>
                <th className="text-left py-3 px-4" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '包含模型' : 'Models'}</th>
                <th className="text-right py-3 px-4" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '按次价格' : 'Per-Call Price'}</th>
                <th className="text-right py-3 px-4" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '包月价格' : 'Monthly Price'}</th>
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
                  <td className="py-3 px-4 text-right font-mono" style={{ color: 'var(--accent-emerald)' }}>¥{(p.pricePerCall || 0).toFixed(2)}</td>
                  <td className="py-3 px-4 text-right font-mono" style={{ color: 'var(--accent-blue)' }}>¥{(p.pricePerMonth || 0).toFixed(2)}<span className="text-[10px]" style={{ color: 'var(--text-dim)' }}>/月</span></td>
                  <td className="py-3 px-4 text-center">
                    <span className="w-2 h-2 rounded-full inline-block" style={{ background: p.status === 1 ? 'var(--accent-emerald)' : 'var(--accent-rose)' }} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </motion.div>

      {/* 计费安全保障 */}
      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}
        className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
        <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
          {lang === 'zh' ? '计费安全保障' : 'Billing Security'}
        </h3>
        <div className="grid sm:grid-cols-2 gap-4">
          {[
            { titleZh: '安全存储', titleEn: 'Secure Storage', descZh: 'API Key 采用 AES-256-GCM 加密存储，平台无法查看明文', descEn: 'API Keys encrypted with AES-256-GCM, platform cannot view plaintext' },
            { titleZh: '按量计费', titleEn: 'Pay-Per-Use', descZh: '每次调用精确计费，从平台余额自动扣除，透明可追溯', descEn: 'Each call precisely billed, auto-deducted from balance, transparent' },
            { titleZh: '随时取消', titleEn: 'Cancel Anytime', descZh: '包月订阅可随时取消，取消后到期自动停止计费', descEn: 'Monthly subscriptions can be cancelled anytime, stops at expiration' },
            { titleZh: '调用日志', titleEn: 'Call Logs', descZh: '所有调用记录完整保存，包含时间、模型、消耗等详细信息', descEn: 'All calls logged with time, model, consumption details' },
          ].map((item, i) => (
            <div key={i} className="flex gap-3 p-3 rounded-xl" style={{ background: isDark ? 'rgba(255,255,255,0.02)' : 'rgba(0,0,0,0.02)', border: `1px solid ${isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)'}` }}>
              <div className="w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0" style={{ background: colors[i % colors.length] + '15' }}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke={colors[i % colors.length]} strokeWidth="2"><path d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"/></svg>
              </div>
              <div>
                <div className="text-xs font-semibold mb-0.5" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? item.titleZh : item.titleEn}</div>
                <div className="text-[10px] leading-relaxed" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? item.descZh : item.descEn}</div>
              </div>
            </div>
          ))}
        </div>
      </motion.div>

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
        </div>
      )}
    </div>
  )
}
