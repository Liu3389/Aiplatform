import { useState, useEffect } from 'react'
import { useAppStore } from '@/stores/appStore'
import { motion, AnimatePresence } from 'framer-motion'
import { useI18nStore } from '@/stores/i18nStore'
import { useThemeStore } from '@/stores/themeStore'
import { subscriptionApi, userApi } from '@/services/api'
import { useToastStore } from '@/stores/toastStore'

interface Package {
  id: number
  packageCode: string
  packageName: string
  description: string
  identityLabel: string
  pointsGranted: number
  dailyPoints: number
  priceMonthly: number
  priceQuarterly: number
  priceYearly: number
  maxInputTokens: number
  maxOutputTokens: number
  modelAccessLevel: number
  priorityLevel: number
  features: string
  status: number
  sortOrder: number
}

const TIERS = [
  {
    key: 'free',
    nameZh: '免费普通用户',
    nameEn: 'Free User',
    color: '#94A3B8',
    icon: '○',
    dailyPoints: 30,
    dailyPointsNoteZh: '每日清空',
    dailyPointsNoteEn: 'Reset daily',
    bonusPoints: 0,
    maxTokens: 6000,
    models: '仅基础文字模型（DeepSeek 基础版）',
    features: ['SSE 极速流式输出', '支持图片对话'],
    missing: ['不支持长文件解析', '对话响应速度常规'],
    priceMonthly: 0,
    priceQuarterly: 0,
    priceYearly: 0,
  },
  {
    key: 'silver',
    nameZh: '白银月卡会员',
    nameEn: 'Silver Member',
    color: '#C0C0C0',
    icon: '●',
    dailyPoints: 80,
    dailyPointsNoteZh: '永久有效、可叠加',
    dailyPointsNoteEn: 'Permanent, stackable',
    bonusPoints: 600,
    maxTokens: 12000,
    models: '全部基础模型 + 全量中端模型',
    features: ['SSE 极速流式输出', '支持图片对话', '支持单文件上传解析', '支持多文件上传解析', '对话响应速度优先'],
    priceMonthly: 15,
    priceQuarterly: 40,
    priceYearly: 100,
  },
  {
    key: 'gold',
    nameZh: '黄金进阶会员',
    nameEn: 'Gold Member',
    color: '#F59E0B',
    icon: '★',
    dailyPoints: 150,
    dailyPointsNoteZh: '永久有效、可叠加',
    dailyPointsNoteEn: 'Permanent, stackable',
    bonusPoints: 1800,
    maxTokens: 18000,
    models: '全量中端模型，部分平台顶级旗舰模型',
    features: ['SSE 极速流式输出', '对话云端存储', '支持多文件解析', '长文本连续对话', '后台请求优先队列'],
    priceMonthly: 30,
    priceQuarterly: 80,
    priceYearly: 200,
  },
  {
    key: 'diamond',
    nameZh: '钻石旗舰会员',
    nameEn: 'Diamond Member',
    color: '#8B5CF6',
    icon: '◆',
    dailyPoints: 300,
    dailyPointsNoteZh: '永久有效、可叠加',
    dailyPointsNoteEn: 'Permanent, stackable',
    bonusPoints: 5000,
    maxTokens: 32000,
    models: '平台内所有大模型无权限限制（包含高清识图模型、旗舰推理大模型）',
    features: ['SSE 极速流式输出', '对话云端存储', '超大体积文件解析', '长文本连续对话', '后台请求优先队列'],
    priceMonthly: 60,
    priceQuarterly: 160,
    priceYearly: 420,
  },
]

export default function RechargePage() {
  const { user, fetchUserInfo } = useAppStore()
  const { lang } = useI18nStore()
  const { theme } = useThemeStore()
  const isDark = theme === 'dark'
  const currentMembership = user?.membership || 'free'

  const [packages, setPackages] = useState<Package[]>([])
  const [packagesLoading, setPackagesLoading] = useState(true)
  const [purchasing, setPurchasing] = useState(false)

  const [confirmTier, setConfirmTier] = useState<typeof TIERS[0] | null>(null)
  const [selectedCycle, setSelectedCycle] = useState<'MONTHLY' | 'QUARTERLY' | 'YEARLY'>('MONTHLY')

  const [rechargeAmount, setRechargeAmount] = useState('')
  const [rechargeLoading, setRechargeLoading] = useState(false)

  const quickAmounts = [10, 30, 50, 100, 200, 500]

  useEffect(() => {
    loadPackages()
  }, [])

  const loadPackages = async () => {
    setPackagesLoading(true)
    try {
      const data = await subscriptionApi.getPackages()
      console.log('[RechargePage] 加载到的套餐列表:', data)
      const filtered = (data || []).filter((p: any) => p.status === 1 && p.packageCode !== 'free')
      console.log('[RechargePage] 过滤后的套餐:', filtered.map(p => ({ code: p.packageCode, name: p.packageName })))
      setPackages(filtered)
    } catch (e) {
      console.error('[RechargePage] 加载套餐失败:', e)
      useToastStore.getState().showToast({
        title: lang === 'zh' ? '加载失败' : 'Load Failed',
        message: lang === 'zh' ? '无法加载套餐信息，请刷新页面重试' : 'Failed to load packages, please refresh',
        type: 'error',
      })
    } finally {
      setPackagesLoading(false)
    }
  }

  const getPkgByTierKey = (tierKey: string) => {
    return packages.find(p => p.packageCode === tierKey)
  }

  const handleOpenConfirm = (tier: typeof TIERS[0]) => {
    const pkg = getPkgByTierKey(tier.key)
    if (!pkg) return
    setConfirmTier(tier)
    setSelectedCycle('MONTHLY')
  }

  const handlePurchaseConfirm = async () => {
    if (!confirmTier) return
    const pkg = getPkgByTierKey(confirmTier.key)
    if (!pkg) return
    setPurchasing(true)
    try {
      console.log('[RechargePage] 开始购买套餐:', { packageCode: pkg.packageCode, cycle: selectedCycle })
      const result = await subscriptionApi.purchase(pkg.packageCode, selectedCycle)
      console.log('[RechargePage] 购买成功:', result)
      setConfirmTier(null)
      
      // 根据返回结果判断是首次开通、续费还是升级
      let title: string
      let message: string
      if (result.isUpgrade) {
        title = lang === 'zh' ? '升级成功' : 'Upgrade Success'
        message = lang === 'zh' ? `已升级为 ${pkg.identityLabel}！` : `Upgraded to ${pkg.identityLabel}!`
      } else if (result.isRenewal) {
        title = lang === 'zh' ? '续费成功' : 'Renewal Success'
        message = lang === 'zh' ? `${pkg.identityLabel} 已续费！` : `${pkg.identityLabel} renewed!`
      } else {
        title = lang === 'zh' ? '开通成功' : 'Activation Success'
        message = lang === 'zh' ? `您已成为 ${pkg.identityLabel}！` : `You are now ${pkg.identityLabel}!`
      }
      
      useToastStore.getState().showToast({ title, message, type: 'success' })
      await fetchUserInfo()
    } catch (e: any) {
      console.error('[RechargePage] 购买失败:', e)
      useToastStore.getState().showToast({
        title: lang === 'zh' ? '开通失败' : 'Activation Failed',
        message: e.message || (lang === 'zh' ? '余额不足或购买失败' : 'Insufficient balance or purchase failed'),
        type: 'error',
      })
    }
    setPurchasing(false)
  }

  const getCyclePrice = (tier: typeof TIERS[0], cycle: 'MONTHLY' | 'QUARTERLY' | 'YEARLY') => {
    switch (cycle) {
      case 'MONTHLY': return tier.priceMonthly
      case 'QUARTERLY': return tier.priceQuarterly
      case 'YEARLY': return tier.priceYearly
    }
  }

  const handleRecharge = async () => {
    const val = parseFloat(rechargeAmount)
    if (!val || val <= 0) {
      useToastStore.getState().showToast({
        title: lang === 'zh' ? '错误' : 'Error',
        message: lang === 'zh' ? '请输入有效金额' : 'Enter a valid amount',
        type: 'error',
      })
      return
    }
    setRechargeLoading(true)
    try {
      await userApi.recharge(val)
      useToastStore.getState().showToast({
        title: lang === 'zh' ? '充值成功' : 'Recharge Success',
        message: lang === 'zh' ? `已成功充值 ¥${val.toFixed(2)}` : `Successfully recharged ¥${val.toFixed(2)}`,
        type: 'success',
      })
      setRechargeAmount('')
      await fetchUserInfo()
    } catch (err) {
      useToastStore.getState().showToast({
        title: lang === 'zh' ? '充值失败' : 'Recharge Failed',
        message: (err as Error).message || (lang === 'zh' ? '充值失败，请重试' : 'Recharge failed, please retry'),
        type: 'error',
      })
    }
    setRechargeLoading(false)
  }

  const getTierByKey = (key: string) => TIERS.find(t => t.key === key) || TIERS[0]

  return (
    <div className="p-4 sm:p-6 lg:p-8 max-w-6xl mx-auto overflow-y-auto h-full space-y-6">
      {/* Header */}
      <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }}>
        <h1 className="text-xl sm:text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
          {lang === 'zh' ? '会员充值' : 'Membership & Recharge'}
        </h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-dim)' }}>
          {lang === 'zh' ? '充值余额，升级会员解锁更多权益' : 'Recharge balance, upgrade membership for more benefits'}
        </p>
      </motion.div>

      {/* Current membership status - 放在最上面 */}
      <motion.div
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ delay: 0.05 }}
        className="rounded-2xl p-4 border"
        style={{
          background: `${getTierByKey(currentMembership).color}10`,
          borderColor: `${getTierByKey(currentMembership).color}30`,
        }}
      >
        <div className="flex items-center gap-3">
          <div
            className="w-10 h-10 rounded-xl flex items-center justify-center text-lg"
            style={{ background: `${getTierByKey(currentMembership).color}20`, color: getTierByKey(currentMembership).color }}
          >
            {getTierByKey(currentMembership).icon}
          </div>
          <div className="flex-1">
            <div className="text-sm font-semibold" style={{ color: getTierByKey(currentMembership).color }}>
              {lang === 'zh' ? '当前会员' : 'Current Membership'}: {getTierByKey(currentMembership).nameZh}
            </div>
            {user?.membershipExpireTime && (
              <div className="text-xs" style={{ color: 'var(--text-dim)' }}>
                {lang === 'zh' ? '到期时间' : 'Expires'}: {new Date(user.membershipExpireTime).toLocaleDateString()}
              </div>
            )}
          </div>
          <div className="text-right">
            <div className="text-xs" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '余额' : 'Balance'}</div>
            <div className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>¥{Number(user?.balance || 0).toFixed(2)}</div>
          </div>
        </div>
      </motion.div>

      {/* Tier comparison table */}
      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}>
        <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
          {lang === 'zh' ? '📊 套餐权益对比' : '📊 Tier Comparison'}
        </h3>
        <div className="rounded-2xl border overflow-hidden" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr style={{ borderBottom: `1px solid var(--border-color)`, background: isDark ? 'rgba(255,255,255,0.02)' : 'rgba(0,0,0,0.02)' }}>
                  <th className="text-left py-3 px-4" style={{ color: 'var(--text-dim)', minWidth: 120 }}>{lang === 'zh' ? '权益' : 'Benefits'}</th>
                  {TIERS.map(tier => (
                    <th key={tier.key} className="text-center py-3 px-3 relative" style={{ color: tier.color, minWidth: 100 }}>
                      <span className="text-base">{tier.icon}</span>
                      <div className="font-medium mt-1">{lang === 'zh' ? tier.nameZh.replace(/会员|用户/g, '') : tier.nameEn.replace(/ Member| User/g, '')}</div>
                      {tier.key === currentMembership && (
                        <div className="mt-1 px-2 py-0.5 text-xs rounded-full font-medium" style={{ background: tier.color, color: '#fff' }}>
                          {lang === 'zh' ? '当前' : 'Current'}
                        </div>
                      )}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {[
                  {
                    labelZh: '每日点数', labelEn: 'Daily Points',
                    values: TIERS.map(t => t.dailyPoints > 0 ? `${t.dailyPoints} 点${lang === 'zh' ? `（${t.dailyPointsNoteZh}）` : ''}` : `${t.dailyPoints} 点（${lang === 'zh' ? '每日清空' : 'reset daily'}）`),
                  },
                  {
                    labelZh: '开通赠送', labelEn: 'Bonus Points',
                    values: TIERS.map(t => t.bonusPoints > 0 ? `${t.bonusPoints} 永久点数` : '0 点'),
                  },
                  {
                    labelZh: '可用模型', labelEn: 'Models',
                    values: TIERS.map(t => t.models),
                    isWide: true,
                  },
                  {
                    labelZh: '开放功能', labelEn: 'Features',
                    values: TIERS.map(t => t.features.join('、')),
                    isWide: true,
                  },
                  {
                    labelZh: '月付价格', labelEn: 'Monthly',
                    values: TIERS.map(t => t.priceMonthly > 0 ? `¥${t.priceMonthly}` : (lang === 'zh' ? '免费' : 'Free')),
                  },
                  {
                    labelZh: '季付价格', labelEn: 'Quarterly',
                    values: TIERS.map(t => t.priceQuarterly > 0 ? `¥${t.priceQuarterly}` : '-'),
                  },
                  {
                    labelZh: '年付价格', labelEn: 'Yearly',
                    values: TIERS.map(t => t.priceYearly > 0 ? `¥${t.priceYearly}` : '-'),
                  },
                ].map((row, i) => (
                  <tr key={i} style={{ borderBottom: `1px solid var(--border-color)` }}>
                    <td className="py-3 px-4 font-medium" style={{ color: 'var(--text-primary)', background: isDark ? 'rgba(255,255,255,0.01)' : 'rgba(0,0,0,0.01)' }}>
                      {lang === 'zh' ? row.labelZh : row.labelEn}
                    </td>
                    {row.values.map((val, j) => (
                      <td key={j} className={`py-3 px-3 ${(row as any).isWide ? 'text-left' : 'text-center'}`} style={{ color: 'var(--text-dim)' }}>
                        {(row as any).isWide ? val : <span className="font-medium" style={{ color: val.includes('免费') || val === '-' ? 'var(--text-dim)' : 'var(--text-primary)' }}>{val}</span>}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </motion.div>

      {/* Recharge section */}
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        className="rounded-2xl p-6 border"
        style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}
      >
        <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
          {lang === 'zh' ? '💰 充值余额' : '💰 Recharge Balance'}
        </h3>
        <p className="text-xs mb-4" style={{ color: 'var(--text-dim)' }}>
          {lang === 'zh' ? '充值金额将直接加入您的账户余额，可用于购买会员套餐或 AI 对话消费' : 'Recharged amount goes directly to your balance, usable for memberships and AI chat'}
        </p>
        <div className="flex items-center gap-3 mb-3">
          <input
            value={rechargeAmount}
            onChange={e => setRechargeAmount(e.target.value)}
            placeholder={lang === 'zh' ? '输入充值金额' : 'Enter amount'}
            type="number"
            min={1}
            step={0.01}
            className="flex-1 px-4 py-3 text-sm rounded-xl outline-none"
            style={{ background: 'var(--bg-input)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }}
          />
          <button
            onClick={handleRecharge}
            disabled={rechargeLoading}
            className="px-6 py-3 text-sm font-medium rounded-xl text-white transition-all disabled:opacity-40"
            style={{ background: 'linear-gradient(135deg, #10B981, #059669)' }}
          >
            {rechargeLoading ? (lang === 'zh' ? '充值中...' : 'Processing...') : (lang === 'zh' ? '充值' : 'Recharge')}
          </button>
        </div>
        <div className="flex flex-wrap gap-2">
          {quickAmounts.map(amt => (
            <button
              key={amt}
              onClick={() => setRechargeAmount(String(amt))}
              className="px-3 py-1.5 rounded-lg text-xs font-medium transition-colors"
              style={{
                background: rechargeAmount === String(amt) ? 'rgba(16,185,129,0.15)' : 'var(--bg-surface)',
                color: rechargeAmount === String(amt) ? '#10B981' : 'var(--text-dim)',
                border: rechargeAmount === String(amt) ? '1px solid rgba(16,185,129,0.3)' : '1px solid var(--border-color)',
              }}
            >
              ¥{amt}
            </button>
          ))}
        </div>
      </motion.div>

      {/* Point deduction rules */}
      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.08 }}
        className="rounded-2xl border overflow-hidden"
        style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}
      >
        <div className="p-4 border-b" style={{ borderColor: 'var(--border-color)', background: isDark ? 'rgba(245,158,11,0.05)' : 'rgba(245,158,11,0.03)' }}>
          <h3 className="text-sm font-semibold flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#F59E0B" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 2L14.5 9.5L22 12L14.5 14.5L12 22L9.5 14.5L2 12L9.5 9.5L12 2Z"/>
            </svg>
            {lang === 'zh' ? '点数消耗规则' : 'Point Deduction Rules'}
          </h3>
        </div>
        
        <div className="p-4">
          <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-3 text-xs">
            {[
              { model: '基础模型 (DeepSeek Flash)', points: '1 点/次', desc: lang === 'zh' ? '简单问答、文本生成' : 'Simple Q&A, text generation' },
              { model: '标准模型 (DeepSeek V3)', points: '3 点/次', desc: lang === 'zh' ? '复杂推理、代码生成' : 'Complex reasoning, code gen' },
              { model: '高级模型 (Claude/GPT-4)', points: '5 点/次', desc: lang === 'zh' ? '深度分析、长文本处理' : 'Deep analysis, long text' },
              { model: '旗舰模型 (o1/Claude Pro)', points: '10 点/次', desc: lang === 'zh' ? '极限推理、专业任务' : 'Extreme reasoning, pro tasks' },
            ].map((rule, i) => (
              <div key={i} className="p-3 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
                <div className="font-medium mb-1" style={{ color: 'var(--text-primary)' }}>{rule.model}</div>
                <div className="font-bold mb-0.5" style={{ color: '#F59E0B' }}>{rule.points}</div>
                <div style={{ color: 'var(--text-dim)' }}>{rule.desc}</div>
              </div>
            ))}
          </div>
          <div className="mt-3 p-3 rounded-lg text-xs" style={{ background: isDark ? 'rgba(139,92,246,0.05)' : 'rgba(139,92,246,0.03)', color: 'var(--text-dim)' }}>
            {lang === 'zh' ? '💡 提示：点数仅用于 AI 对话消耗，与账户余额严格分离。余额仅用于 API Key 调用消费。' : '💡 Note: Points are only for AI chat consumption, strictly separated from balance. Balance is only for API Key usage.'}
          </div>
        </div>
      </motion.div>

      {/* Membership upgrade cards */}
      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}>
        <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
          {lang === 'zh' ? '🎁 升级会员' : '🎁 Upgrade Membership'}
        </h3>
      </motion.div>

      <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {TIERS.filter(t => t.key !== 'free').map((tier, i) => {
          const isCurrent = currentMembership === tier.key
          const color = tier.color

          return (
            <motion.div
              key={tier.key}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: i * 0.1 }}
              className="rounded-2xl p-5 border-2 relative overflow-hidden transition-all hover:shadow-lg flex flex-col"
              style={{
                background: 'var(--bg-card)',
                borderColor: isCurrent ? color : 'var(--border-color)',
                ...(isCurrent ? { boxShadow: `0 0 20px ${color}20` } : {}),
              }}
            >
              {isCurrent && (
                <div className="absolute top-3 right-3 px-2 py-0.5 rounded-md text-[10px] font-medium text-white" style={{ background: color }}>
                  {lang === 'zh' ? '当前' : 'Current'}
                </div>
              )}
              <div className="flex items-center gap-2 mb-3">
                <div className="w-8 h-8 rounded-lg flex items-center justify-center text-base" style={{ background: `${color}20`, color }}>
                  {tier.icon}
                </div>
                <div className="text-sm font-semibold" style={{ color }}>{tier.nameZh}</div>
              </div>

              {/* Prices */}
              <div className="space-y-1 mb-3">
                <div className="flex items-baseline gap-1">
                  <span className="text-xl font-bold" style={{ color: 'var(--text-primary)' }}>¥{tier.priceMonthly}</span>
                  <span className="text-xs" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '/月' : '/mo'}</span>
                </div>
                <div className="flex items-center gap-2 text-[10px]" style={{ color: 'var(--text-dim)' }}>
                  <span>{lang === 'zh' ? `季付 ¥${tier.priceQuarterly}（9折）` : `Quarterly ¥${tier.priceQuarterly} (10% off)`}</span>
                  <span>·</span>
                  <span>{lang === 'zh' ? `年付 ¥${tier.priceYearly}（7折）` : `Yearly ¥${tier.priceYearly} (30% off)`}</span>
                </div>
              </div>

              {/* Points badge */}
              <div className="flex items-center gap-1.5 mb-3 px-2 py-1.5 rounded-lg inline-block self-start" style={{ background: `${color}10` }}>
                <svg width="12" height="12" viewBox="0 0 24 24" fill={color} stroke="none">
                  <path d="M12 2L14.86 9.14L22 12L14.86 14.86L12 22L9.14 14.86L2 12L9.14 9.14L12 2Z"/>
                </svg>
                <span className="text-xs font-medium" style={{ color }}>{lang === 'zh' ? `${tier.dailyPoints} 点/天` : `${tier.dailyPoints} pts/day`}</span>
              </div>

              {/* Features */}
              <ul className="space-y-1.5 mb-4 flex-1">
                <li className="flex items-start gap-2 text-xs" style={{ color: 'var(--text-dim)' }}>
                  <span style={{ color }}>✓</span>
                  {lang === 'zh' ? `每日 ${tier.dailyPoints} 永久点数（${tier.dailyPointsNoteZh}）` : `${tier.dailyPoints} permanent pts/day (${tier.dailyPointsNoteEn})`}
                </li>
                {tier.features.slice(0, 3).map((f, j) => (
                  <li key={j} className="flex items-start gap-2 text-xs" style={{ color: 'var(--text-dim)' }}>
                    <span style={{ color }}>✓</span>
                    {f}
                  </li>
                ))}
              </ul>

              {/* Bonus */}
              {tier.bonusPoints > 0 && (
                <div className="text-xs mb-4 px-2 py-1 rounded-md inline-block self-start" style={{ background: `${color}10`, color }}>
                  {lang === 'zh' ? `🎁 开通赠送 ${tier.bonusPoints} 永久点数` : `🎁 Bonus ${tier.bonusPoints} permanent pts`}
                </div>
              )}

              {/* Button - aligned at bottom */}
              <div className="mt-auto">
                {isCurrent ? (
                  <button disabled className="w-full py-2.5 rounded-xl text-xs font-medium opacity-50" style={{ background: color, color: '#fff' }}>
                    {lang === 'zh' ? '当前方案' : 'Current Plan'}
                  </button>
                ) : (
                  <button
                    onClick={() => {
                      const pkg = getPkgByTierKey(tier.key)
                      if (!pkg) {
                        console.warn('[RechargePage] 套餐不存在:', tier.key)
                        useToastStore.getState().showToast({
                          title: lang === 'zh' ? '套餐不可用' : 'Package Unavailable',
                          message: lang === 'zh' ? '该套餐暂未配置，请联系管理员' : 'This package is not configured',
                          type: 'warning',
                        })
                        return
                      }
                      handleOpenConfirm(tier)
                    }}
                    disabled={purchasing || packagesLoading}
                    className="w-full py-2.5 rounded-xl text-xs font-medium text-white transition-all hover:opacity-90 disabled:opacity-50"
                    style={{ background: color }}
                  >
                    {purchasing ? (lang === 'zh' ? '处理中...' : 'Processing...') :
                      packagesLoading ? (lang === 'zh' ? '加载中...' : 'Loading...') :
                      (lang === 'zh' ? '立即开通' : 'Activate Now')}
                  </button>
                )}
              </div>
            </motion.div>
          )
        })}
      </div>

      {/* Purchase confirmation modal */}
      <AnimatePresence>
        {confirmTier && getPkgByTierKey(confirmTier.key) && (() => {
          const pkg = getPkgByTierKey(confirmTier.key)!
          const price = getCyclePrice(confirmTier, selectedCycle)
          const insufficientBalance = Number(user?.balance || 0) < price
          return (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 flex items-center justify-center p-4"
            style={{ background: 'rgba(0,0,0,0.6)' }}
            onClick={() => !purchasing && setConfirmTier(null)}
          >
            <motion.div
              initial={{ scale: 0.9, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.9, opacity: 0 }}
              className="rounded-2xl p-6 max-w-md w-full border"
              style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
                  {lang === 'zh' ? '确认开通会员' : 'Confirm Membership'}
                </h3>
                <button
                  onClick={() => !purchasing && setConfirmTier(null)}
                  className="w-8 h-8 rounded-lg flex items-center justify-center transition-colors"
                  style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)' }}
                >
                  ✕
                </button>
              </div>

              <div className="space-y-4">
                <div className="flex items-center gap-3">
                  <div
                    className="w-12 h-12 rounded-xl flex items-center justify-center text-xl"
                    style={{
                      background: `${confirmTier.color}20`,
                      color: confirmTier.color,
                    }}
                  >
                    {confirmTier.icon}
                  </div>
                  <div>
                    <div className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>{pkg.identityLabel}</div>
                    <div className="text-xs" style={{ color: 'var(--text-dim)' }}>{pkg.packageName}</div>
                  </div>
                </div>

                {/* Cycle selector */}
                <div className="flex gap-2">
                  {(['MONTHLY', 'QUARTERLY', 'YEARLY'] as const).map(cycle => {
                    const cycleLabels: Record<string, string> = { MONTHLY: lang === 'zh' ? '月付' : 'Monthly', QUARTERLY: lang === 'zh' ? '季付' : 'Quarterly', YEARLY: lang === 'zh' ? '年付' : 'Yearly' }
                    const cyclePrices: Record<string, number> = { MONTHLY: confirmTier.priceMonthly, QUARTERLY: confirmTier.priceQuarterly, YEARLY: confirmTier.priceYearly }
                    return (
                      <button
                        key={cycle}
                        onClick={() => setSelectedCycle(cycle)}
                        className="flex-1 py-2 rounded-xl text-xs font-medium transition-all"
                        style={{
                          background: selectedCycle === cycle ? `${confirmTier.color}20` : 'var(--bg-surface)',
                          color: selectedCycle === cycle ? confirmTier.color : 'var(--text-dim)',
                          border: `1px solid ${selectedCycle === cycle ? `${confirmTier.color}50` : 'var(--border-color)'}`,
                        }}
                      >
                        <div>{cycleLabels[cycle]}</div>
                        <div className="font-bold">¥{cyclePrices[cycle]}</div>
                      </button>
                    )
                  })}
                </div>

                <div className="p-4 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
                  <div className="flex items-baseline gap-1 mb-2">
                    <span className="text-3xl font-bold" style={{ color: confirmTier.color }}>
                      ¥{price}
                    </span>
                    <span className="text-sm" style={{ color: 'var(--text-dim)' }}>
                      {selectedCycle === 'MONTHLY' ? (lang === 'zh' ? '/月' : '/month') :
                       selectedCycle === 'QUARTERLY' ? (lang === 'zh' ? '/季' : '/quarter') :
                       (lang === 'zh' ? '/年' : '/year')}
                    </span>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3 text-xs">
                  <div className="p-3 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
                    <div style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '开通赠送' : 'Bonus Points'}</div>
                    <div className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>{pkg.pointsGranted}</div>
                  </div>
                  <div className="p-3 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
                    <div style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '每日永久点数' : 'Daily Pts'}</div>
                    <div className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>{pkg.dailyPoints}</div>
                  </div>
                </div>

                <div className="p-3 rounded-xl text-xs" style={{ background: 'rgba(139,92,246,0.08)', color: 'var(--text-dim)', border: '1px solid rgba(139,92,246,0.15)' }}>
                  {lang === 'zh' ? `将从您账户余额中扣除 ¥${price}，${selectedCycle === 'MONTHLY' ? '月付' : selectedCycle === 'QUARTERLY' ? '季付' : '年付'}，到期后自动降级为免费用户` : `¥${price} will be deducted from your balance, ${selectedCycle === 'MONTHLY' ? 'monthly' : selectedCycle === 'QUARTERLY' ? 'quarterly' : 'yearly'}, auto downgrade to free user on expiry`}
                </div>

                {insufficientBalance && (
                  <div className="p-3 rounded-xl text-xs" style={{ background: 'rgba(245,158,11,0.1)', color: '#F59E0B', border: '1px solid rgba(245,158,11,0.2)' }}>
                    {lang === 'zh' ? `余额不足！当前余额 ¥${Number(user?.balance || 0).toFixed(2)}，请先充值` : `Insufficient balance! Current: ¥${Number(user?.balance || 0).toFixed(2)}, please recharge first`}
                  </div>
                )}

                <div className="pt-2 flex gap-3">
                  <button
                    onClick={() => setConfirmTier(null)}
                    disabled={purchasing}
                    className="flex-1 py-3 rounded-xl text-sm font-medium transition-colors disabled:opacity-50"
                    style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)', border: '1px solid var(--border-color)' }}
                  >
                    {lang === 'zh' ? '取消' : 'Cancel'}
                  </button>
                  <button
                    onClick={handlePurchaseConfirm}
                    disabled={purchasing || insufficientBalance}
                    className="flex-1 py-3 rounded-xl text-sm font-medium text-white transition-all disabled:opacity-50"
                    style={{ background: confirmTier.color }}
                  >
                    {purchasing ? (lang === 'zh' ? '处理中...' : 'Processing...') : (lang === 'zh' ? `确认支付 ¥${price}` : `Confirm ¥${price}`)}
                  </button>
                </div>
              </div>
            </motion.div>
          </motion.div>
          )
        })()}
      </AnimatePresence>
    </div>
  )
}
