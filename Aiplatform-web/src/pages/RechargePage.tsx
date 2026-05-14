import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAppStore } from '@/stores/appStore'
import { MEMBERSHIP_QUOTAS, MEMBERSHIP_PRICES } from '@/types'
import { motion } from 'framer-motion'
import { useI18nStore } from '@/stores/i18nStore'
import { useThemeStore } from '@/stores/themeStore'
import PointsIcon from '@/components/PointsIcon'

export default function RechargePage() {
  const { user, fetchUserInfo } = useAppStore()
  const { lang } = useI18nStore()
  const { theme } = useThemeStore()
  const navigate = useNavigate()
  const isDark = theme === 'dark'
  const currentMembership = user?.membership || 'free'

  const [amount, setAmount] = useState('')
  const [loading, setLoading] = useState(false)
  const [msg, setMsg] = useState<{ text: string; ok: boolean } | null>(null)

  const tiers = [
    {
      key: 'free' as const,
      name: lang === 'zh' ? '普通用户' : 'Free',
      price: '¥0',
      period: lang === 'zh' ? '永久' : 'Forever',
      monthlyPoints: MEMBERSHIP_QUOTAS.free.monthlyPoints,
      features: [
        lang === 'zh' ? '每日 30 次免费调用' : '30 free calls/day',
        lang === 'zh' ? '每日 50K Token 额度' : '50K tokens/day',
        lang === 'zh' ? '使用免费模型' : 'Free models access',
      ],
      color: '#94A3B8',
      current: currentMembership === 'free',
    },
    {
      key: 'plus' as const,
      name: lang === 'zh' ? '畅享用户' : 'Plus',
      price: `¥${MEMBERSHIP_PRICES.plus.monthly}`,
      yearly: lang === 'zh' ? `¥${MEMBERSHIP_PRICES.plus.yearly}/年` : `¥${MEMBERSHIP_PRICES.plus.yearly}/yr`,
      period: lang === 'zh' ? '/月' : '/mo',
      monthlyPoints: MEMBERSHIP_QUOTAS.plus.monthlyPoints,
      features: [
        lang === 'zh' ? `每月 ${MEMBERSHIP_QUOTAS.plus.monthlyPoints} 点数` : `${MEMBERSHIP_QUOTAS.plus.monthlyPoints} pts/month`,
        lang === 'zh' ? '每日 500 次调用' : '500 calls/day',
        lang === 'zh' ? '每日 500K Token 额度' : '500K tokens/day',
        lang === 'zh' ? '使用全平台模型' : 'All model access',
        lang === 'zh' ? '优先响应队列' : 'Priority queue',
      ],
      color: '#3B82F6',
      current: currentMembership === 'plus',
      highlight: true,
    },
    {
      key: 'pro' as const,
      name: lang === 'zh' ? '尊享用户' : 'Pro',
      price: `¥${MEMBERSHIP_PRICES.pro.monthly}`,
      yearly: lang === 'zh' ? `¥${MEMBERSHIP_PRICES.pro.yearly}/年` : `¥${MEMBERSHIP_PRICES.pro.yearly}/yr`,
      period: lang === 'zh' ? '/月' : '/mo',
      monthlyPoints: MEMBERSHIP_QUOTAS.pro.monthlyPoints,
      features: [
        lang === 'zh' ? `每月 ${MEMBERSHIP_QUOTAS.pro.monthlyPoints} 点数` : `${MEMBERSHIP_QUOTAS.pro.monthlyPoints} pts/month`,
        lang === 'zh' ? '每日无限次调用' : 'Unlimited calls',
        lang === 'zh' ? '每日 2M Token 额度' : '2M tokens/day',
        lang === 'zh' ? '使用全平台模型' : 'All model access',
        lang === 'zh' ? '最高优先响应队列' : 'Top priority queue',
        lang === 'zh' ? '专属技术支持' : 'Dedicated support',
      ],
      color: '#8B5CF6',
      current: currentMembership === 'pro',
      highlight: true,
    },
  ]

  const handleRecharge = async () => {
    if (!amount || isNaN(Number(amount)) || Number(amount) <= 0) {
      setMsg({ text: lang === 'zh' ? '请输入有效金额' : 'Enter a valid amount', ok: false })
      return
    }
    setLoading(true)
    try {
      const token = localStorage.getItem('lingdian_token')
      const res = await fetch(`/api/user/recharge?amount=${Number(amount)}`, {
        method: 'POST',
        headers: { 'X-User-Token': token || '' },
      })
      const j = await res.json()
      if (j.code === 200) {
        setMsg({ text: lang === 'zh' ? `充值成功！余额 ¥${Number(amount).toFixed(2)}` : `Recharged ¥${Number(amount).toFixed(2)}!`, ok: true })
        setAmount('')
        await fetchUserInfo()
      } else {
        setMsg({ text: j.message || (lang === 'zh' ? '充值失败' : 'Failed'), ok: false })
      }
    } catch {
      setMsg({ text: lang === 'zh' ? '网络错误' : 'Network error', ok: false })
    }
    setLoading(false)
  }

  return (
    <div className="p-4 sm:p-6 lg:p-8 max-w-4xl mx-auto overflow-y-auto h-full space-y-6">
      <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }}>
        <h1 className="text-xl sm:text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
          {lang === 'zh' ? '会员充值' : 'Membership & Recharge'}
        </h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-dim)' }}>
          {lang === 'zh' ? '选择适合你的会员等级，解锁更多功能' : 'Choose a plan that fits your needs'}
        </p>
      </motion.div>

      {/* Tier Cards */}
      <div className="grid sm:grid-cols-3 gap-4">
        {tiers.map((tier, i) => (
          <motion.div key={tier.key} initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.08 }}
            className={`rounded-2xl p-5 border-2 relative overflow-hidden ${tier.current ? '' : ''}`}
            style={{
              background: 'var(--bg-card)',
              borderColor: tier.current ? tier.color : 'var(--border-color)',
              ...(tier.current ? { boxShadow: `0 0 20px ${tier.color}20` } : {}),
            }}
          >
            {tier.current && (
              <div className="absolute top-3 right-3 px-2 py-0.5 rounded-md text-[10px] font-medium text-white" style={{ background: tier.color }}>
                {lang === 'zh' ? '当前' : 'Current'}
              </div>
            )}
            <div className="text-xs font-semibold mb-1" style={{ color: tier.color }}>{tier.name}</div>
            <div className="flex items-baseline gap-1 mb-1">
              <span className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>{tier.price}</span>
              <span className="text-xs" style={{ color: 'var(--text-dim)' }}>{tier.period}</span>
            </div>
            {tier.yearly && <p className="text-[10px] mb-3" style={{ color: 'var(--text-dim)' }}>{tier.yearly}</p>}
            <ul className="space-y-1.5 mb-4">
              {tier.features.map((f, j) => (
                <li key={j} className="flex items-start gap-2 text-xs" style={{ color: 'var(--text-dim)' }}>
                  <span style={{ color: tier.color }}>✓</span>
                  {f}
                </li>
              ))}
            </ul>
            {tier.current ? (
              <button disabled className="w-full py-2 rounded-xl text-xs font-medium opacity-50" style={{ background: tier.color, color: '#fff' }}>
                {lang === 'zh' ? '当前方案' : 'Current Plan'}
              </button>
            ) : (
              <button onClick={() => navigate('/user')} className="w-full py-2 rounded-xl text-xs font-medium text-white transition-all hover:opacity-90" style={{ background: tier.color }}>
                {lang === 'zh' ? '升级' : 'Upgrade'}
              </button>
            )}
          </motion.div>
        ))}
      </div>

      {/* Quick Recharge */}
      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.25 }}
        className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
        <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
          {lang === 'zh' ? '快速充值' : 'Quick Recharge'}
        </h3>
        <div className="flex flex-wrap justify-center gap-2 mb-4">
          {[10, 50, 100, 200, 500].map(v => (
            <button key={v} onClick={() => setAmount(String(v))}
              className="w-[64px] py-2.5 rounded-xl text-xs font-bold transition-all border"
              style={{
                background: amount === String(v) ? 'var(--accent-purple)' : 'transparent',
                borderColor: amount === String(v) ? 'var(--accent-purple)' : 'var(--border-color)',
                color: amount === String(v) ? '#fff' : 'var(--text-dim)',
                borderWidth: amount === String(v) ? '2px' : '1px',
              }}
            >
              ¥{v}
            </button>
          ))}
        </div>
        <div className="flex gap-3">
          <div className="flex-1 flex items-center gap-1 px-4 rounded-xl border" style={{ borderColor: 'var(--border-color)', background: 'var(--bg-input)' }}>
            <span className="text-sm" style={{ color: 'var(--text-dim)' }}>¥</span>
            <input
              value={amount}
              onChange={e => setAmount(e.target.value)}
              type="number"
              min="1"
              step="0.01"
              placeholder="0.00"
              className="flex-1 py-2.5 bg-transparent text-sm outline-none"
              style={{ color: 'var(--text-primary)' }}
            />
          </div>
          <button onClick={handleRecharge} disabled={loading}
            className="px-6 py-2.5 rounded-xl text-sm font-medium text-white transition-all disabled:opacity-50"
            style={{ background: 'var(--accent-purple)' }}
          >
            {loading ? (lang === 'zh' ? '处理中...' : 'Processing...') : (lang === 'zh' ? '充值' : 'Recharge')}
          </button>
        </div>
        {msg && (
          <div className={`mt-3 px-4 py-2 rounded-xl text-xs ${msg.ok ? 'bg-emerald-500/10 text-emerald-500 border border-emerald-500/20' : 'bg-rose-500/10 text-rose-500 border border-rose-500/20'}`}>
            {msg.text}
          </div>
        )}
      </motion.div>

      {/* FAQ */}
      <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
        <h3 className="text-sm font-semibold mb-3" style={{ color: 'var(--text-primary)' }}>
          {lang === 'zh' ? '常见问题' : 'FAQ'}
        </h3>
        <div className="space-y-3 text-xs" style={{ color: 'var(--text-dim)' }}>
          <p><strong style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? 'Q: 如何升级会员？' : 'Q: How to upgrade?'}</strong><br/>{lang === 'zh' ? 'A: 联系管理员或前往个人中心提交升级申请。目前仅支持通过管理后台手动升级。' : 'A: Contact admin or submit upgrade request via profile. Currently manual upgrade only.'}</p>
          <p><strong style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? 'Q: 充值是否安全？' : 'Q: Is it safe?'}</strong><br/>{lang === 'zh' ? 'A: 所有余额操作使用 Redis Lua 原子脚本，确保资金安全。' : 'A: All balance operations use Redis Lua atomic scripts.'}</p>
          <p><strong style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? 'Q: 余额可以提现吗？' : 'Q: Can I withdraw?'}</strong><br/>{lang === 'zh' ? 'A: 当前版本不支持提现功能，余额仅用于平台内的付费模型调用。' : 'A: Balance is for platform usage only, withdrawals not supported.'}</p>
        </div>
      </div>
    </div>
  )
}
