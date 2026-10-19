import { useState, useEffect } from 'react'
import { useAppStore } from '@/stores/appStore'
import { motion, AnimatePresence } from 'framer-motion'
import { useI18nStore } from '@/stores/i18nStore'
import { useThemeStore } from '@/stores/themeStore'
import { subscriptionApi, modelApi } from '@/services/api'
import { useToastStore } from '@/stores/toastStore'
import MembershipIcon from '@/components/MembershipIcon'

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

type MembershipKey = 'free' | 'silver' | 'gold' | 'diamond'
const MEMBERSHIP_LEVEL: Record<MembershipKey, number> = { free: 0, silver: 1, gold: 2, diamond: 3 }

type ComparisonRow = {
  key: string
  labelZh: string
  labelEn: string
  values: string[]
  isWide?: boolean
}

export default function RechargePage() {
  const { user, fetchUserInfo } = useAppStore()
  const { lang } = useI18nStore()
  const { theme } = useThemeStore()
  const isDark = theme === 'dark'
  const currentMembership = user?.membership || 'free'
  const currentLevel = MEMBERSHIP_LEVEL[currentMembership] ?? 0

  const [packages, setPackages] = useState<Package[]>([])
  const [packagesLoading, setPackagesLoading] = useState(true)
  const [purchasing, setPurchasing] = useState(false)

  const [confirmTier, setConfirmTier] = useState<typeof TIERS[0] | null>(null)
  const [selectedCycle, setSelectedCycle] = useState<'MONTHLY' | 'QUARTERLY' | 'YEARLY'>('MONTHLY')

  const [comparisonTierKey, setComparisonTierKey] = useState<MembershipKey>(currentMembership)

  // 模型点数消耗（真实数据，来自 platform_model_config）
  const [modelPointRules, setModelPointRules] = useState<{ model: string; points: number }[]>([])

  useEffect(() => {
    loadPackages()
    modelApi.list().then(models => {
      const rules = (models || [])
        .slice()
        .sort((a, b) => (a.fixedPoints || 0) - (b.fixedPoints || 0))
        .map(m => ({ model: m.displayName, points: Number(m.fixedPoints || 0) }))
      setModelPointRules(rules)
    }).catch(() => {})
  }, [])

  useEffect(() => {
    setComparisonTierKey(currentMembership)
  }, [currentMembership])

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

  /** 价格以后端套餐配置为准（真数据），未配置返回 0 */
  const priceOf = (tierKey: string, cycle: 'MONTHLY' | 'QUARTERLY' | 'YEARLY') => {
    const pkg = getPkgByTierKey(tierKey)
    if (!pkg) return 0
    return cycle === 'MONTHLY' ? Number(pkg.priceMonthly || 0)
      : cycle === 'QUARTERLY' ? Number(pkg.priceQuarterly || 0)
      : Number(pkg.priceYearly || 0)
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
    return priceOf(tier.key, cycle)
  }

  const getTierByKey = (key: string) => TIERS.find(t => t.key === key) || TIERS[0]

  const comparisonRows: ComparisonRow[] = [
    {
      key: 'dailyPoints', labelZh: '每日点数', labelEn: 'Daily Points',
      values: TIERS.map(t => {
        const pkg = getPkgByTierKey(t.key)
        const dp = pkg ? pkg.dailyPoints : t.dailyPoints
        return dp > 0 ? `${dp} 点${lang === 'zh' ? `（${t.dailyPointsNoteZh}）` : ''}` : `${dp} 点（${lang === 'zh' ? '每日清空' : 'reset daily'}）`
      }),
    },
    {
      key: 'bonusPoints', labelZh: '开通赠送', labelEn: 'Bonus Points',
      values: TIERS.map(t => {
        const pkg = getPkgByTierKey(t.key)
        const bp = pkg ? pkg.pointsGranted : t.bonusPoints
        return bp > 0 ? `${bp} 永久点数` : '0 点'
      }),
    },
    { key: 'models', labelZh: '可用模型', labelEn: 'Models', values: TIERS.map(t => t.models), isWide: true },
    { key: 'features', labelZh: '开放功能', labelEn: 'Features', values: TIERS.map(t => t.features.join('、')), isWide: true },
    {
      key: 'monthly', labelZh: '月付价格', labelEn: 'Monthly',
      values: TIERS.map(t => {
        const p = priceOf(t.key, 'MONTHLY')
        return p > 0 ? `¥${p}` : (t.key === 'free' ? (lang === 'zh' ? '免费' : 'Free') : '-')
      }),
    },
    {
      key: 'quarterly', labelZh: '季付价格', labelEn: 'Quarterly',
      values: TIERS.map(t => {
        const p = priceOf(t.key, 'QUARTERLY')
        return p > 0 ? `¥${p}` : '-'
      }),
    },
    {
      key: 'yearly', labelZh: '年付价格', labelEn: 'Yearly',
      values: TIERS.map(t => {
        const p = priceOf(t.key, 'YEARLY')
        return p > 0 ? `¥${p}` : '-'
      }),
    },
  ]
  const comparisonTier = getTierByKey(comparisonTierKey)
  const comparisonTierIndex = TIERS.findIndex(tier => tier.key === comparisonTier.key)

  return (
    <div className="page-scroll h-full max-w-6xl mx-auto space-y-5 overflow-y-auto p-3 sm:space-y-6 sm:p-6 lg:p-8">
      {/* Header */}
      <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }}>
        <h1 className="text-xl sm:text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
          {lang === 'zh' ? '会员套餐' : 'Membership Plans'}
        </h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-dim)' }}>
          {lang === 'zh'
            ? '可使用已有账户余额开通或续费；自助充值暂未开放。'
            : 'Use your existing account balance to subscribe or renew. Self-service recharge is currently unavailable.'}
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
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
          <div
            className="w-10 h-10 rounded-xl flex items-center justify-center text-lg"
            style={{ background: `${getTierByKey(currentMembership).color}20`, color: getTierByKey(currentMembership).color }}
          >
            <MembershipIcon kind={currentMembership} size={20} color={getTierByKey(currentMembership).color} />
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
          <div className="text-left sm:text-right">
            <div className="text-xs" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '余额' : 'Balance'}</div>
            <div className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>¥{Number(user?.balance || 0).toFixed(2)}</div>
          </div>
        </div>
      </motion.div>

      {/* Tier comparison */}
      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}>
        <div className="mb-4 flex items-end justify-between gap-3">
          <div>
            <h3 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
              {lang === 'zh' ? '套餐权益对比' : 'Tier Comparison'}
            </h3>
            <p className="mt-1 text-xs" style={{ color: 'var(--text-dim)' }}>
              {lang === 'zh' ? '桌面端快速对比，手机端点击套餐查看详情' : 'Compare on desktop; select a plan on mobile for details'}
            </p>
          </div>
        </div>

        <div className="hidden rounded-2xl border md:block" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
          <div className="overflow-hidden rounded-2xl">
            <table className="w-full text-xs">
              <thead>
                <tr style={{ borderBottom: `1px solid var(--border-color)`, background: isDark ? 'rgba(255,255,255,0.02)' : 'rgba(0,0,0,0.02)' }}>
                  <th className="text-left py-3 px-4" style={{ color: 'var(--text-dim)', minWidth: 120 }}>{lang === 'zh' ? '权益' : 'Benefits'}</th>
                  {TIERS.map(tier => (
                    <th key={tier.key} className="text-center py-3 px-3 relative" style={{ color: tier.color, minWidth: 100 }}>
                      <MembershipIcon kind={tier.key} size={18} color={tier.color} />
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
                {comparisonRows.map(row => (
                  <tr key={row.key} style={{ borderBottom: `1px solid var(--border-color)` }}>
                    <td className="py-3 px-4 font-medium" style={{ color: 'var(--text-primary)', background: isDark ? 'rgba(255,255,255,0.01)' : 'rgba(0,0,0,0.01)' }}>
                      {lang === 'zh' ? row.labelZh : row.labelEn}
                    </td>
                    {row.values.map((val, j) => (
                      <td key={j} className={`px-3 py-3 ${row.isWide ? 'text-left' : 'text-center'}`} style={{ color: 'var(--text-dim)' }}>
                        {row.isWide ? val : <span className="font-medium" style={{ color: val.includes('免费') || val === '-' ? 'var(--text-dim)' : 'var(--text-primary)' }}>{val}</span>}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="space-y-3 md:hidden">
          <div className="grid grid-cols-2 gap-2" role="tablist" aria-label={lang === 'zh' ? '选择套餐查看权益' : 'Select a plan to view benefits'}>
            {TIERS.map(tier => {
              const active = tier.key === comparisonTier.key
              const tierIndex = TIERS.findIndex(item => item.key === tier.key)
              return (
                <button
                  key={tier.key}
                  type="button"
                  onClick={() => setComparisonTierKey(tier.key as MembershipKey)}
                  role="tab"
                  aria-selected={active}
                  aria-controls="mobile-plan-details"
                  className="flex min-h-16 items-center gap-2 rounded-xl border px-3 py-2.5 text-left transition-colors"
                  style={{
                    background: active ? `${tier.color}12` : 'var(--bg-card)',
                    borderColor: active ? `${tier.color}65` : 'var(--border-color)',
                  }}
                >
                  <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg" style={{ background: `${tier.color}18` }}>
                    <MembershipIcon kind={tier.key} size={17} color={tier.color} />
                  </span>
                  <span className="min-w-0">
                    <span className="block truncate text-xs font-semibold" style={{ color: active ? tier.color : 'var(--text-primary)' }}>{tier.nameZh.replace(/会员|用户/g, '')}</span>
                    <span className="mt-0.5 block text-[10px]" style={{ color: 'var(--text-dim)' }}>{active ? (lang === 'zh' ? '正在查看' : 'Selected') : comparisonRows[0].values[tierIndex]}</span>
                  </span>
                  {tier.key === currentMembership && <span className="ml-auto shrink-0 rounded-full px-1.5 py-0.5 text-[9px] font-medium" style={{ background: `${tier.color}20`, color: tier.color }}>{lang === 'zh' ? '当前' : 'Current'}</span>}
                </button>
              )
            })}
          </div>

          <section id="mobile-plan-details" role="tabpanel" className="rounded-2xl border p-4" style={{ background: 'var(--bg-card)', borderColor: `${comparisonTier.color}45` }}>
            <div className="flex items-start justify-between gap-3">
              <div className="flex min-w-0 items-center gap-3">
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl" style={{ background: `${comparisonTier.color}18` }}>
                  <MembershipIcon kind={comparisonTier.key} size={20} color={comparisonTier.color} />
                </span>
                <div className="min-w-0">
                  <h4 className="truncate text-sm font-bold" style={{ color: comparisonTier.color }}>{comparisonTier.nameZh}</h4>
                  <p className="mt-0.5 text-[11px]" style={{ color: 'var(--text-dim)' }}>{comparisonTier.models}</p>
                </div>
              </div>
              {comparisonTier.key === currentMembership && <span className="shrink-0 rounded-full px-2 py-1 text-[10px] font-medium" style={{ background: `${comparisonTier.color}18`, color: comparisonTier.color }}>{lang === 'zh' ? '当前方案' : 'Current plan'}</span>}
            </div>

            <div className="mt-4 grid grid-cols-2 gap-2">
              {[
                { label: lang === 'zh' ? '每日点数' : 'Daily points', value: comparisonRows[0].values[comparisonTierIndex] },
                { label: lang === 'zh' ? '开通赠送' : 'Bonus points', value: comparisonRows[1].values[comparisonTierIndex] },
                { label: lang === 'zh' ? '月付价格' : 'Monthly', value: comparisonRows[4].values[comparisonTierIndex] },
                { label: lang === 'zh' ? '年付价格' : 'Yearly', value: comparisonRows[6].values[comparisonTierIndex] },
              ].map(item => (
                <div key={item.label} className="rounded-xl p-3" style={{ background: 'var(--bg-surface)' }}>
                  <div className="text-[10px]" style={{ color: 'var(--text-dim)' }}>{item.label}</div>
                  <div className="mt-1 text-xs font-semibold" style={{ color: 'var(--text-primary)' }}>{item.value}</div>
                </div>
              ))}
            </div>

            <div className="mt-4">
              <div className="text-[11px] font-semibold" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? '开放功能' : 'Features'}</div>
              <div className="mt-2 space-y-2">
                {comparisonTier.features.map(feature => (
                  <div key={feature} className="flex items-start gap-2 text-xs" style={{ color: 'var(--text-dim)' }}>
                    <svg className="mt-0.5 shrink-0" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke={comparisonTier.color} strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"><path d="m5 12 4 4L19 6" /></svg>
                    <span>{feature}</span>
                  </div>
                ))}
              </div>
            </div>
          </section>
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
          {lang === 'zh' ? '账户余额充值' : 'Add Account Balance'}
        </h3>
        <div className="rounded-xl border border-amber-500/20 bg-amber-500/5 p-4 text-sm" style={{ color: 'var(--text-dim)' }}>
          {lang === 'zh'
            ? '自助充值暂时关闭：支付渠道与验签回调尚未接入。重新开放前不会扣款或增加账户余额，现有余额与 AI 点数不受影响。'
            : 'Self-service recharge is paused until a payment provider and verified callback are configured. No payment will be taken and no balance will be added; existing balance and AI points are unaffected.'}
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
            {modelPointRules.length > 0 ? modelPointRules.slice(0, 8).map((rule, i) => (
              <div key={i} className="p-3 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
                <div className="font-medium mb-1" style={{ color: 'var(--text-primary)' }}>{rule.model}</div>
                <div className="font-bold mb-0.5" style={{ color: '#F59E0B' }}>{rule.points} {lang === 'zh' ? '点/次' : 'pts/call'}</div>
              </div>
            )) : (
              <div className="p-3 rounded-xl" style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)' }}>
                {lang === 'zh' ? '模型点数规则加载中...' : 'Loading point rules...'}
              </div>
            )}
          </div>
          <div className="mt-3 p-3 rounded-lg text-xs" style={{ background: isDark ? 'rgba(139,92,246,0.05)' : 'rgba(139,92,246,0.03)', color: 'var(--text-dim)' }}>
            {lang === 'zh' ? '提示：点数仅用于 AI 对话消耗，与账户余额严格分离。账户余额仅用于购买会员套餐。' : 'Note: Points are only for AI chat, strictly separated from balance. Account balance is only for membership purchases.'}
          </div>
        </div>
      </motion.div>

      {/* Membership upgrade cards */}
      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}>
        <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
          {lang === 'zh' ? '会员方案' : 'Membership Plans'}
        </h3>
      </motion.div>

      <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {TIERS.filter(t => t.key !== 'free').map((tier, i) => {
          const isCurrent = currentMembership === tier.key
          const isLower = (MEMBERSHIP_LEVEL[tier.key as MembershipKey] ?? 0) < currentLevel
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
                  <MembershipIcon kind={tier.key} size={17} color={color} />
                </div>
                <div className="text-sm font-semibold" style={{ color }}>{tier.nameZh}</div>
              </div>

              {/* Prices — 以后端套餐配置为准 */}
              <div className="space-y-1 mb-3">
                <div className="flex items-baseline gap-1">
                  <span className="text-xl font-bold" style={{ color: 'var(--text-primary)' }}>¥{priceOf(tier.key, 'MONTHLY')}</span>
                  <span className="text-xs" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '/月' : '/mo'}</span>
                </div>
                <div className="flex items-center gap-2 text-[10px]" style={{ color: 'var(--text-dim)' }}>
                  <span>{lang === 'zh' ? `季付 ¥${priceOf(tier.key, 'QUARTERLY') || '-'}` : `Quarterly ¥${priceOf(tier.key, 'QUARTERLY') || '-'}`}</span>
                  <span>·</span>
                  <span>{lang === 'zh' ? `年付 ¥${priceOf(tier.key, 'YEARLY') || '-'}` : `Yearly ¥${priceOf(tier.key, 'YEARLY') || '-'}`}</span>
                </div>
              </div>

              {/* Points badge */}
              <div className="flex items-center gap-1.5 mb-3 px-2 py-1.5 rounded-lg inline-block self-start" style={{ background: `${color}10` }}>
                <svg width="12" height="12" viewBox="0 0 24 24" fill={color} stroke="none">
                  <path d="M12 2L14.86 9.14L22 12L14.86 14.86L12 22L9.14 14.86L2 12L9.14 9.14L12 2Z"/>
                </svg>
                <span className="text-xs font-medium" style={{ color }}>{lang === 'zh' ? `${getPkgByTierKey(tier.key)?.dailyPoints ?? tier.dailyPoints} 点/天` : `${getPkgByTierKey(tier.key)?.dailyPoints ?? tier.dailyPoints} pts/day`}</span>
              </div>

              {/* Features */}
              <ul className="space-y-1.5 mb-4 flex-1">
                <li className="flex items-start gap-2 text-xs" style={{ color: 'var(--text-dim)' }}>
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" className="mt-0.5 flex-shrink-0"><path d="m5 12 4 4L19 6" /></svg>
                  {lang === 'zh' ? `每日 ${tier.dailyPoints} 永久点数（${tier.dailyPointsNoteZh}）` : `${tier.dailyPoints} permanent pts/day (${tier.dailyPointsNoteEn})`}
                </li>
                {tier.features.slice(0, 3).map((f, j) => (
                  <li key={j} className="flex items-start gap-2 text-xs" style={{ color: 'var(--text-dim)' }}>
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" className="mt-0.5 flex-shrink-0"><path d="m5 12 4 4L19 6" /></svg>
                    {f}
                  </li>
                ))}
              </ul>

              {/* Bonus */}
              {(() => {
                const bonus = getPkgByTierKey(tier.key)?.pointsGranted ?? tier.bonusPoints
                return bonus > 0 ? (
                  <div className="text-xs mb-4 px-2 py-1 rounded-md inline-block self-start" style={{ background: `${color}10`, color }}>
                    {lang === 'zh' ? `开通赠送 ${bonus} 永久点数` : `Bonus ${bonus} permanent pts`}
                  </div>
                ) : null
              })()}

              {/* Button - aligned at bottom */}
              <div className="mt-auto">
                {isCurrent ? (
                  <button
                    onClick={() => handleOpenConfirm(tier)}
                    disabled={purchasing || packagesLoading}
                    className="w-full py-2.5 rounded-xl text-xs font-medium text-white transition-all hover:opacity-90 disabled:opacity-50"
                    style={{ background: color }}
                  >
                    {purchasing ? (lang === 'zh' ? '处理中...' : 'Processing...') : (lang === 'zh' ? '续费当前会员' : 'Renew plan')}
                  </button>
                ) : isLower ? (
                  <button disabled className="w-full py-2.5 rounded-xl text-xs font-medium opacity-50" style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)', border: '1px solid var(--border-color)' }}>
                    {lang === 'zh' ? '低于当前等级' : 'Lower than current'}
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
                      (lang === 'zh' ? (currentLevel > 0 ? '立即升级' : '立即开通') : (currentLevel > 0 ? 'Upgrade now' : 'Activate now'))}
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
              className="page-scroll max-h-[calc(100dvh-2rem)] w-full max-w-md overflow-y-auto rounded-2xl border p-4 sm:p-6"
              style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
                  {lang === 'zh'
                    ? (confirmTier.key === currentMembership ? '确认续费会员' : '确认升级会员')
                    : (confirmTier.key === currentMembership ? 'Confirm renewal' : 'Confirm upgrade')}
                </h3>
                <button
                  onClick={() => !purchasing && setConfirmTier(null)}
                  className="w-8 h-8 rounded-lg flex items-center justify-center transition-colors"
                  style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)' }}
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><path d="m6 6 12 12M18 6 6 18" /></svg>
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
                    <MembershipIcon kind={confirmTier.key} size={22} color={confirmTier.color} />
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
                    const cyclePrices: Record<string, number> = { MONTHLY: priceOf(confirmTier.key, 'MONTHLY'), QUARTERLY: priceOf(confirmTier.key, 'QUARTERLY'), YEARLY: priceOf(confirmTier.key, 'YEARLY') }
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
                  {lang === 'zh'
                    ? (confirmTier.key === currentMembership
                      ? `将从账户余额中扣除 ¥${price}，续费后在当前到期时间上顺延一个${selectedCycle === 'MONTHLY' ? '月' : selectedCycle === 'QUARTERLY' ? '季度' : '年'}。`
                      : `将从您账户余额中扣除 ¥${price}，开通${selectedCycle === 'MONTHLY' ? '月付' : selectedCycle === 'QUARTERLY' ? '季付' : '年付'}会员。`)
                    : (confirmTier.key === currentMembership
                      ? `¥${price} will be deducted and the current expiry will be extended by one cycle.`
                      : `¥${price} will be deducted from your balance for this membership cycle.`)}
                </div>

                {insufficientBalance && (
                  <div className="p-3 rounded-xl text-xs" style={{ background: 'rgba(245,158,11,0.1)', color: '#F59E0B', border: '1px solid rgba(245,158,11,0.2)' }}>
                    {lang === 'zh'
                      ? `余额不足！当前余额 ¥${Number(user?.balance || 0).toFixed(2)}。自助充值暂未开放，请联系管理员处理。`
                      : `Insufficient balance. Current balance: ¥${Number(user?.balance || 0).toFixed(2)}. Self-service recharge is unavailable; please contact an administrator.`}
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
                    {purchasing ? (lang === 'zh' ? '处理中...' : 'Processing...') : (lang === 'zh' ? `确认扣除余额 ¥${price}` : `Confirm balance deduction ¥${price}`)}
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
