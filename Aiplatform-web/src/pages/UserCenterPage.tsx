import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAppStore } from '@/stores/appStore'
import { userApi, request, subscriptionApi, pointsApi } from '@/services/api'
import UsageAnalyticsPanel from '@/components/usage/UsageAnalyticsPanel'
import { useThemeStore } from '@/stores/themeStore'
import { useI18nStore } from '@/stores/i18nStore'
import { motion, AnimatePresence } from 'framer-motion'
import { useToastStore } from '@/stores/toastStore'
import { MEMBERSHIP_QUOTAS } from '@/types'
import ConfirmDialog from '@/components/ConfirmDialog'
import MembershipIcon from '@/components/MembershipIcon'
import { getUserDisplayName, isGeneratedOAuthUsername } from '@/utils/userDisplay'

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

type RecordTab = 'recharge' | 'points' | 'subscription'
type PageTab = 'overview' | 'records' | 'settings'

const MEMBERSHIP_LABELS: Record<string, { zh: string; en: string }> = {
  free: { zh: '普通用户', en: 'Free User' },
  silver: { zh: '白银会员', en: 'Silver Member' },
  gold: { zh: '黄金会员', en: 'Gold Member' },
  diamond: { zh: '钻石会员', en: 'Diamond Member' },
}

export default function UserCenterPage() {
  const { user, fetchUserInfo, logout } = useAppStore()
  const { theme, toggleTheme } = useThemeStore()
  const { lang, toggleLang, t } = useI18nStore()
  const navigate = useNavigate()
  const isDark = theme === 'dark'
  const userDisplayName = getUserDisplayName(user, t('第三方账号', 'External account'))

  const [activeTab, setActiveTab] = useState<PageTab>('overview')
  const [showPw, setShowPw] = useState(false)
  const [oldPw, setOldPw] = useState('')
  const [newPw, setNewPw] = useState('')
  const [confirmPw, setConfirmPw] = useState('')
  const [pwLoading, setPwLoading] = useState(false)
  const [pwMsg, setPwMsg] = useState<{ text: string; ok: boolean } | null>(null)

  const [packages, setPackages] = useState<Package[]>([])
  const [activeRecordTab, setActiveRecordTab] = useState<RecordTab>('recharge')
  const [rechargeRecords, setRechargeRecords] = useState<any[]>([])
  const [pointsBills, setPointsBills] = useState<any[]>([])
  const [subscriptionHistory, setSubscriptionHistory] = useState<any[]>([])
  const [recordsLoading, setRecordsLoading] = useState(false)
  const [recordsError, setRecordsError] = useState('')
  const [recordPage, setRecordPage] = useState(1)
  const recordPageSize = 10
  const [recordTotal, setRecordTotal] = useState(0)
  const [pointsChangeType, setPointsChangeType] = useState('')

  const [pointsInfo, setPointsInfo] = useState<any>(null)
  const [consumptionSummary, setConsumptionSummary] = useState<{
    todayConsumed: number; monthConsumed: number; totalConsumed: number; remainingPoints: number
  } | null>(null)
  const [usageAnalytics, setUsageAnalytics] = useState<import('@/services/api').UsageAnalytics | null>(null)
  const [usageAnalyticsLoading, setUsageAnalyticsLoading] = useState(false)

  const currentMembership = user?.membership || 'free'
  const membershipConfig = MEMBERSHIP_QUOTAS[currentMembership as keyof typeof MEMBERSHIP_QUOTAS] || MEMBERSHIP_QUOTAS.free
  const membershipLabel = MEMBERSHIP_LABELS[currentMembership] || MEMBERSHIP_LABELS.free

  const getMembershipColor = (label: string) => {
    const colors: Record<string, string> = { free: '#94A3B8', silver: '#C0C0C0', gold: '#F59E0B', diamond: '#8B5CF6' }
    return colors[label?.toLowerCase()] || '#94A3B8'
  }

  useEffect(() => {
    loadPackages()
    loadPointsInfo()
    loadUsageStats()
  }, [])

  const loadPackages = async () => {
    try {
      const data = await subscriptionApi.getPackages()
      setPackages(data || [])
    } catch (e) {
      console.error('Failed to load packages:', e)
    }
  }

  const loadPointsInfo = async () => {
    try {
      const info = await pointsApi.getInfo()
      setPointsInfo(info)
    } catch (e) {
      console.error('Failed to load points info:', e)
    }
  }

  const loadUsageStats = async () => {
    setUsageAnalyticsLoading(true)
    try {
      const [summary, analytics] = await Promise.all([
        pointsApi.getConsumptionSummary(),
        userApi.getUsageAnalytics(),
      ])
      setConsumptionSummary(summary)
      setUsageAnalytics(analytics)
    } catch (e) {
      console.error('Failed to load usage stats:', e)
    } finally {
      setUsageAnalyticsLoading(false)
    }
  }

  const loadRecords = async () => {
    setRecordsLoading(true)
    setRecordsError('')
    try {
      if (activeRecordTab === 'recharge') {
        const data = await userApi.getRechargeRecords(recordPage, recordPageSize)
        setRechargeRecords(data?.records || [])
        setRecordTotal(Number(data?.total || 0))
      } else if (activeRecordTab === 'points') {
        const data = await userApi.getPointsBills(recordPage, recordPageSize, pointsChangeType || undefined)
        setPointsBills(data?.records || [])
        setRecordTotal(Number(data?.total || 0))
      } else if (activeRecordTab === 'subscription') {
        const data = await subscriptionApi.getHistoryPage(recordPage, recordPageSize)
        setSubscriptionHistory(data?.records || [])
        setRecordTotal(Number(data?.total || 0))
      }
    } catch (e) {
      console.error('Failed to load records:', e)
      setRecordsError((e as Error).message || (lang === 'zh' ? '记录加载失败' : 'Failed to load records'))
      setRecordTotal(0)
    } finally {
      setRecordsLoading(false)
    }
  }

  useEffect(() => {
    if (activeTab === 'records') loadRecords()
  }, [activeRecordTab, activeTab, recordPage, pointsChangeType])

  const handleChangePw = async () => {
    if (!oldPw || !newPw) { setPwMsg({ text: lang === 'zh' ? '请填写所有密码字段' : 'Please fill all password fields', ok: false }); return }
    if (newPw.length < 8) { setPwMsg({ text: lang === 'zh' ? '新密码至少8个字符' : 'New password must be at least 8 characters', ok: false }); return }
    if (newPw !== confirmPw) { setPwMsg({ text: lang === 'zh' ? '两次输入的新密码不一致' : 'Passwords do not match', ok: false }); return }
    setPwLoading(true); setPwMsg(null)
    try {
      await request('/user/change-password', { method: 'POST', body: JSON.stringify({ oldPassword: oldPw, newPassword: newPw }) })
      setPwMsg({ text: lang === 'zh' ? '密码修改成功！' : 'Password changed!', ok: true })
      setShowPw(false); setOldPw(''); setNewPw(''); setConfirmPw('')
    } catch (err) { setPwMsg({ text: (err as Error).message || (lang === 'zh' ? '修改失败' : 'Change failed'), ok: false }) }
    finally { setPwLoading(false) }
  }

  const [logoutConfirmOpen, setLogoutConfirmOpen] = useState(false)

  const handleLogout = () => {
    setLogoutConfirmOpen(true)
  }

  const confirmLogout = useCallback(() => {
    setLogoutConfirmOpen(false)
    logout()
    navigate('/login')
  }, [logout, navigate])

  const cancelLogout = useCallback(() => {
    setLogoutConfirmOpen(false)
  }, [])

  const formatExpireTime = (time?: string) => {
    if (!time) return lang === 'zh' ? '未开通' : 'Not activated'
    const date = new Date(time)
    const now = new Date()
    if (date < now) return lang === 'zh' ? '已过期' : 'Expired'
    return date.toLocaleDateString()
  }

  const pointTypeLabel = (changeType?: string) => {
    const labels: Record<string, { zh: string; en: string }> = {
      SETTLE: { zh: '对话消耗', en: 'Chat usage' },
      DEDUCT: { zh: '点数扣除', en: 'Deduction' },
      GRANT: { zh: '每日赠送', en: 'Daily grant' },
      SUBSCRIPTION_GRANT: { zh: '会员赠送', en: 'Membership grant' },
      RECHARGE: { zh: '点数充值', en: 'Points recharge' },
      EXPIRE: { zh: '点数过期', en: 'Expired' },
      REFUND: { zh: '点数退款', en: 'Refund' },
    }
    const label = labels[changeType || '']
    return label ? (lang === 'zh' ? label.zh : label.en) : (changeType || (lang === 'zh' ? '点数变动' : 'Points change'))
  }

  return (
    <div className="page-scroll h-full max-w-6xl mx-auto overflow-y-auto p-3 sm:p-6 lg:p-8">
      {/* Header */}
      <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }} className="mb-6">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>{t('user.center')}</h1>
            <p className="text-sm mt-1" style={{ color: 'var(--text-dim)' }}>
              {lang === 'zh' ? '管理您的账户信息、会员状态和消费记录' : 'Manage your account, membership, and billing records'}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => navigate('/recharge')}
              className="px-4 py-2 text-sm font-medium rounded-xl text-white transition-all"
              style={{ background: 'linear-gradient(135deg, #8B5CF6, #3B82F6)' }}
            >
              {lang === 'zh' ? '充值/升级' : 'Recharge/Upgrade'}
            </button>
            <button
              onClick={handleLogout}
              className="px-4 py-2 text-sm rounded-xl transition-colors"
              style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)', border: '1px solid var(--border-color)' }}
            >
              {t('user.logout')}
            </button>
          </div>
        </div>

        {/* Tab Navigation */}
        <div className="flex gap-2 mt-4">
          {([
            { key: 'overview' as PageTab, labelZh: '账户概览', labelEn: 'Overview' },
            { key: 'records' as PageTab, labelZh: '账单记录', labelEn: 'Records' },
            { key: 'settings' as PageTab, labelZh: '设置', labelEn: 'Settings' },
          ]).map(tab => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className="flex-1 sm:flex-none px-4 py-2 rounded-xl text-sm font-medium transition-all"
              style={{
                background: activeTab === tab.key ? 'var(--accent-purple)' : 'var(--bg-surface)',
                color: activeTab === tab.key ? '#fff' : 'var(--text-dim)',
                border: activeTab === tab.key ? 'none' : '1px solid var(--border-color)',
              }}
            >
              {lang === 'zh' ? tab.labelZh : tab.labelEn}
            </button>
          ))}
        </div>
      </motion.div>

      <AnimatePresence mode="wait">
        {/* Overview Tab */}
        {activeTab === 'overview' && (
          <motion.div
            key="overview"
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: 20 }}
            className="space-y-4"
          >
            {/* Membership Status Card */}
            <div
              className="rounded-2xl p-5 sm:p-6 border"
              style={{
                background: `linear-gradient(135deg, ${getMembershipColor(currentMembership)}10, ${getMembershipColor(currentMembership)}05)`,
                borderColor: `${getMembershipColor(currentMembership)}30`,
              }}
            >
              <div className="flex flex-col sm:flex-row sm:items-center gap-4">
                <div
                  className="w-14 h-14 rounded-2xl flex items-center justify-center text-2xl"
                  style={{ background: `${getMembershipColor(currentMembership)}20`, color: getMembershipColor(currentMembership) }}
                >
                  <MembershipIcon kind={currentMembership} size={24} color={getMembershipColor(currentMembership)} />
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-lg font-bold break-all" style={{ color: 'var(--text-primary)' }}>{userDisplayName || '--'}</span>
                    <span
                      className="text-xs px-3 py-1 rounded-full font-medium"
                      style={{ background: `${getMembershipColor(currentMembership)}20`, color: getMembershipColor(currentMembership) }}
                    >
                      <MembershipIcon kind={currentMembership} size={13} color={getMembershipColor(currentMembership)} />
                      {lang === 'zh' ? membershipLabel.zh : membershipLabel.en}
                    </span>
                  </div>
                  <div className="text-sm mt-1" style={{ color: 'var(--text-dim)' }}>
                    {lang === 'zh' ? '到期时间' : 'Expires'}: {formatExpireTime(user?.membershipExpireTime)}
                  </div>
                </div>
                <div className="text-right">
                  <div className="text-xs" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '每日点数' : 'Daily Points'}</div>
                  <div className="text-xl font-bold" style={{ color: getMembershipColor(currentMembership) }}>
                    {membershipConfig.dailyPermanentPoints + membershipConfig.dailyTemporaryPoints}
                    <span className="text-xs font-normal" style={{ color: 'var(--text-dim)' }}> {lang === 'zh' ? '点/天' : 'pts/day'}</span>
                  </div>
                </div>
              </div>
            </div>

            <UsageAnalyticsPanel
              data={usageAnalytics}
              loading={usageAnalyticsLoading}
              isDark={isDark}
              lang={lang}
              accountBalance={Number(user?.balance || 0)}
              pointsInfo={pointsInfo}
              consumptionSummary={consumptionSummary}
              onRecharge={() => navigate('/recharge')}
            />

          </motion.div>
        )}

        {/* Records Tab */}
        {activeTab === 'records' && (
          <motion.div
            key="records"
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: 20 }}
            className="flex min-h-[560px] flex-col rounded-2xl border overflow-hidden sm:min-h-[600px]"
            style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}
          >
            {/* Record Tabs */}
            <div className="flex border-b" style={{ borderColor: 'var(--border-color)' }}>
              {([
                { key: 'recharge' as RecordTab, labelZh: '充值记录', labelEn: 'Recharge' },
                { key: 'points' as RecordTab, labelZh: '点数消耗', labelEn: 'Points' },
                { key: 'subscription' as RecordTab, labelZh: '会员记录', labelEn: 'Membership' },
              ]).map(tab => (
                <button
                  key={tab.key}
                  onClick={() => { setActiveRecordTab(tab.key); setRecordPage(1); setRecordsError('') }}
                  className="flex-1 py-3 px-4 text-sm font-medium transition-all border-b-2"
                  style={{
                    color: activeRecordTab === tab.key ? 'var(--accent-purple)' : 'var(--text-dim)',
                    borderColor: activeRecordTab === tab.key ? 'var(--accent-purple)' : 'transparent',
                    background: activeRecordTab === tab.key ? (isDark ? 'rgba(139,92,246,0.05)' : 'rgba(139,92,246,0.02)') : 'transparent',
                  }}
                >
                  {lang === 'zh' ? tab.labelZh : tab.labelEn}
                </button>
              ))}
            </div>

            {activeRecordTab === 'points' && (
              <div className="flex items-center gap-2 px-4 pt-4">
                <span className="text-xs" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '分类' : 'Filter'}:</span>
                <select
                  value={pointsChangeType}
                  onChange={e => { setPointsChangeType(e.target.value); setRecordPage(1) }}
                  className="px-3 py-1.5 rounded-lg text-xs outline-none"
                  style={{ background: 'var(--bg-surface)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }}
                >
                  <option value="">{lang === 'zh' ? '全部变动' : 'All changes'}</option>
                  <option value="CONSUMPTION">{lang === 'zh' ? '对话消耗' : 'Chat usage'}</option>
                  <option value="INCOME">{lang === 'zh' ? '充值/赠送' : 'Recharge / grants'}</option>
                  <option value="EXPIRE">{lang === 'zh' ? '过期' : 'Expired'}</option>
                  <option value="REFUND">{lang === 'zh' ? '退款' : 'Refund'}</option>
                </select>
              </div>
            )}

            {/* Records Content */}
            <div className="page-scroll min-h-0 flex-1 overflow-y-auto p-4">
              {recordsError && !recordsLoading && (
                <div className="mb-3 flex items-center justify-between gap-3 px-3 py-2 rounded-lg text-xs" style={{ background: 'rgba(244,63,94,0.08)', color: '#F43F5E' }}>
                  <span>{recordsError}</span>
                  <button onClick={loadRecords} className="underline" style={{ color: '#F43F5E' }}>{lang === 'zh' ? '重试' : 'Retry'}</button>
                </div>
              )}
              {recordsLoading ? (
                <div className="text-center py-12 text-sm" style={{ color: 'var(--text-dim)' }}>
                  {lang === 'zh' ? '加载中...' : 'Loading...'}
                </div>
              ) : activeRecordTab === 'recharge' && rechargeRecords.length === 0 ? (
                <div className="text-center py-12 text-sm" style={{ color: 'var(--text-dim)' }}>
                  {lang === 'zh' ? '暂无充值记录' : 'No recharge records'}
                </div>
              ) : activeRecordTab === 'recharge' ? (
                <div className="space-y-2">
                  {rechargeRecords.map((r, i) => (
                    <div key={i} className="flex items-center justify-between p-3 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
                      <div>
                        <div className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
                          {r.changeType === 'BALANCE_RECHARGE'
                            ? `+¥${Number(r.amount || 0).toFixed(2)}`
                            : `+${Math.round(Number(r.points || 0))} ${lang === 'zh' ? '点' : 'pts'}`}
                        </div>
                        <div className="text-xs" style={{ color: 'var(--text-dim)' }}>{r.createTime ? new Date(r.createTime).toLocaleString() : '--'}</div>
                      </div>
                      <span className="px-2 py-1 rounded-md text-xs" style={{ background: 'rgba(16,185,129,0.1)', color: '#10B981' }}>
                        {r.description || (lang === 'zh' ? '成功' : 'Success')}
                      </span>
                    </div>
                  ))}
                </div>
              ) : activeRecordTab === 'points' && pointsBills.length === 0 ? (
                <div className="text-center py-12 text-sm" style={{ color: 'var(--text-dim)' }}>
                  {lang === 'zh' ? '暂无点数消耗记录' : 'No points records'}
                </div>
              ) : activeRecordTab === 'points' ? (
                <div className="space-y-2">
                  {pointsBills.map((r, i) => (
                    <div key={i} className="flex items-center justify-between p-3 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
                      <div className="flex-1">
                        <div className="flex items-center gap-2 text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
                          <span>{r.description || pointTypeLabel(r.changeType)}</span>
                          <span className="px-1.5 py-0.5 rounded text-[10px] font-normal" style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)' }}>{pointTypeLabel(r.changeType)}</span>
                        </div>
                        <div className="text-xs" style={{ color: 'var(--text-dim)' }}>{r.createTime ? new Date(r.createTime).toLocaleString() : '--'}</div>
                      </div>
                      <span className="px-2 py-1 rounded-md text-xs font-mono font-bold ml-2" style={{
                        background: Number(r.pointsChange || 0) > 0 ? 'rgba(16,185,129,0.1)' : 'rgba(244,63,94,0.1)',
                        color: Number(r.pointsChange || 0) > 0 ? '#10B981' : '#F43F5E',
                      }}>
                        {Number(r.pointsChange || 0) > 0 ? '+' : ''}{Math.round(Number(r.pointsChange || 0))} {lang === 'zh' ? '点' : 'pts'}
                      </span>
                    </div>
                  ))}
                </div>
              ) : activeRecordTab === 'subscription' && subscriptionHistory.length === 0 ? (
                <div className="text-center py-12 text-sm" style={{ color: 'var(--text-dim)' }}>
                  {lang === 'zh' ? '暂无会员记录' : 'No membership records'}
                </div>
              ) : activeRecordTab === 'subscription' ? (
                <div className="space-y-2">
                  {subscriptionHistory.map((r, i) => (
                    <div key={i} className="flex items-center justify-between p-3 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
                      <div>
                        <div className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>{r.packageName || r.identityLabel || '--'}</div>
                        <div className="text-xs" style={{ color: 'var(--text-dim)' }}>
                          {r.startTime ? new Date(r.startTime).toLocaleDateString() : '--'} → {r.expireTime ? new Date(r.expireTime).toLocaleDateString() : '--'}
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-medium" style={{ color: 'var(--text-dim)' }}>¥{Number(r.pricePaid || 0).toFixed(2)}</span>
                        <span className="px-2 py-1 rounded-md text-xs" style={{ background: `${getMembershipColor(r.identityLabel || r.packageCode || 'free')}15`, color: getMembershipColor(r.identityLabel || r.packageCode || 'free') }}>
                          {r.status === 'ACTIVE' ? (lang === 'zh' ? '生效中' : 'Active') : r.status === 'CANCELLED' ? (lang === 'zh' ? '已取消' : 'Cancelled') : (lang === 'zh' ? '已过期' : 'Expired')}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              ) : null}

            </div>
            <div className="flex min-h-[72px] shrink-0 flex-col items-stretch justify-center gap-2 border-t px-4 py-3 text-xs sm:flex-row sm:items-center sm:justify-between" style={{ borderColor: 'var(--border-color)', color: 'var(--text-dim)', background: 'var(--bg-card)' }}>
              <span className="text-center sm:text-left">
                {recordTotal > 0
                  ? (lang === 'zh' ? `共 ${recordTotal} 条，第 ${recordPage}/${Math.ceil(recordTotal / recordPageSize)} 页` : `${recordTotal} records · page ${recordPage}/${Math.ceil(recordTotal / recordPageSize)}`)
                  : (recordsLoading ? (lang === 'zh' ? '正在加载记录' : 'Loading records') : (lang === 'zh' ? '暂无记录' : 'No records'))}
              </span>
              <div className="flex shrink-0 justify-end gap-2">
                <button
                  onClick={() => setRecordPage(page => Math.max(1, page - 1))}
                  disabled={recordsLoading || recordPage <= 1 || recordTotal === 0}
                  className="px-3 py-1.5 rounded-lg border disabled:opacity-30"
                  style={{ borderColor: 'var(--border-color)', color: 'var(--text-dim)' }}
                >{lang === 'zh' ? '上一页' : 'Previous'}</button>
                <button
                  onClick={() => setRecordPage(page => page + 1)}
                  disabled={recordsLoading || recordPage * recordPageSize >= recordTotal || recordTotal === 0}
                  className="px-3 py-1.5 rounded-lg border disabled:opacity-30"
                  style={{ borderColor: 'var(--border-color)', color: 'var(--text-dim)' }}
                >{lang === 'zh' ? '下一页' : 'Next'}</button>
              </div>
            </div>
          </motion.div>
        )}

        {/* Settings Tab */}
        {activeTab === 'settings' && (
          <motion.div
            key="settings"
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: 20 }}
            className="space-y-4"
          >
            <button
              type="button"
              onClick={() => navigate('/security')}
              className="flex w-full items-center justify-between gap-4 rounded-2xl p-5 text-left transition-all hover:-translate-y-0.5"
              style={{ background: 'var(--bg-card)', border: '1px solid var(--border-color)' }}
            >
              <span>
                <span className="block text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? '安全与登录' : 'Security & sign-in'}</span>
                <span className="mt-1 block text-xs" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '管理密码、Google 绑定、两步验证和已验证联系方式' : 'Manage password, Google, 2FA, and verified contacts'}</span>
              </span>
              <span aria-hidden="true" className="shrink-0 text-lg" style={{ color: 'var(--accent-purple)' }}>›</span>
            </button>

            {/* Appearance Settings */}
            <div className="rounded-2xl p-5 sm:p-6" style={{ background: 'var(--bg-card)', border: '1px solid var(--border-color)' }}>
              <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? '外观设置' : 'Appearance'}</h3>
              <div className="space-y-3">
                <div className="flex items-center justify-between py-2">
                  <div>
                    <div className="text-sm" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? '深色模式' : 'Dark Mode'}</div>
                    <div className="text-xs" style={{ color: 'var(--text-dim)' }}>{theme === 'dark' ? (lang === 'zh' ? '已开启' : 'Enabled') : (lang === 'zh' ? '已关闭' : 'Disabled')}</div>
                  </div>
                  <button
                    onClick={toggleTheme}
                    className="relative w-12 h-6 rounded-full transition-colors"
                    style={{ background: theme === 'dark' ? '#8B5CF6' : '#CBD5E1' }}
                  >
                    <span
                      className="absolute top-1 w-4 h-4 rounded-full bg-white shadow transition-transform"
                      style={{ transform: theme === 'dark' ? 'translateX(24px)' : 'translateX(2px)' }}
                    />
                  </button>
                </div>
                <div className="flex items-center justify-between py-2">
                  <div>
                    <div className="text-sm" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? '语言' : 'Language'}</div>
                    <div className="text-xs" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '中文' : 'English'}</div>
                  </div>
                  <button
                    onClick={toggleLang}
                    className="px-4 py-2 rounded-lg text-xs font-medium transition-all"
                    style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)', border: '1px solid var(--border-color)' }}
                  >
                    {lang === 'zh' ? '切换 English' : 'Switch 中文'}
                  </button>
                </div>
              </div>
            </div>

            {/* Change Password */}
            <div className="rounded-2xl p-5 sm:p-6" style={{ background: 'var(--bg-card)', border: '1px solid var(--border-color)' }}>
              <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>{t('user.changePassword')}</h3>
              {!showPw ? (
                <button
                  onClick={() => setShowPw(true)}
                  className="px-4 py-2 text-sm rounded-xl transition-colors"
                  style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)', border: '1px solid var(--border-color)' }}
                >
                  {t('user.changePassword')}
                </button>
              ) : (
                <div className="space-y-3">
                  <input
                    value={oldPw}
                    onChange={e => setOldPw(e.target.value)}
                    type="password"
                    placeholder={t('user.oldPassword')}
                    className="w-full px-4 py-3 text-sm rounded-xl"
                    style={{ background: 'var(--bg-surface)', color: 'var(--text-primary)', border: '1px solid var(--border-color)', outline: 'none' }}
                  />
                  <input
                    value={newPw}
                    onChange={e => setNewPw(e.target.value)}
                    type="password"
                    placeholder={t('user.newPassword')}
                    className="w-full px-4 py-3 text-sm rounded-xl"
                    style={{ background: 'var(--bg-surface)', color: 'var(--text-primary)', border: '1px solid var(--border-color)', outline: 'none' }}
                  />
                  <input
                    value={confirmPw}
                    onChange={e => setConfirmPw(e.target.value)}
                    type="password"
                    placeholder={t('user.confirmPassword')}
                    className="w-full px-4 py-3 text-sm rounded-xl"
                    style={{ background: 'var(--bg-surface)', color: 'var(--text-primary)', border: '1px solid var(--border-color)', outline: 'none' }}
                  />
                  {pwMsg && (
                    <div className="text-xs p-2 rounded-lg" style={{ background: pwMsg.ok ? 'rgba(16,185,129,0.1)' : 'rgba(244,63,94,0.1)', color: pwMsg.ok ? '#10B981' : '#F43F5E' }}>
                      {pwMsg.text}
                    </div>
                  )}
                  <div className="flex gap-3">
                    <button
                      onClick={handleChangePw}
                      disabled={pwLoading}
                      className="px-4 py-2 text-sm font-medium rounded-xl text-white transition-all disabled:opacity-40"
                      style={{ background: 'linear-gradient(135deg, #8B5CF6, #3B82F6)' }}
                    >
                      {pwLoading ? (lang === 'zh' ? '修改中...' : 'Changing...') : (lang === 'zh' ? '确认修改' : 'Confirm')}
                    </button>
                    <button
                      onClick={() => { setShowPw(false); setPwMsg(null) }}
                      className="px-4 py-2 text-sm rounded-xl transition-colors"
                      style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)', border: '1px solid var(--border-color)' }}
                    >
                      {lang === 'zh' ? '取消' : 'Cancel'}
                    </button>
                  </div>
                </div>
              )}
            </div>

            {/* Account Info */}
            <div className="rounded-2xl p-5 sm:p-6" style={{ background: 'var(--bg-card)', border: '1px solid var(--border-color)' }}>
              <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? '账户信息' : 'Account Info'}</h3>
              <div className="space-y-3 text-sm">
                {!isGeneratedOAuthUsername(user?.username) && (
                  <div className="flex justify-between py-2 border-b" style={{ borderColor: 'var(--border-color)' }}>
                    <span style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '用户名' : 'Username'}</span>
                    <span style={{ color: 'var(--text-primary)' }}>{user?.username || '--'}</span>
                  </div>
                )}
                <div className="flex justify-between py-2 border-b" style={{ borderColor: 'var(--border-color)' }}>
                  <span style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '邮箱' : 'Email'}</span>
                  <span className="text-right" style={{ color: 'var(--text-primary)' }}>
                    {user?.email || (lang === 'zh' ? '未设置' : 'Not set')}
                    <span className="ml-2 text-xs" style={{ color: user?.emailVerified ? 'var(--accent-emerald)' : 'var(--accent-amber)' }}>
                      {user?.email ? (user.emailVerified ? (lang === 'zh' ? '已验证' : 'Verified') : (lang === 'zh' ? '未验证' : 'Unverified')) : ''}
                    </span>
                  </span>
                </div>
                <div className="flex justify-between py-2 border-b" style={{ borderColor: 'var(--border-color)' }}>
                  <span style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '手机号' : 'Phone'}</span>
                  <span className="text-right" style={{ color: 'var(--text-primary)' }}>
                    {user?.phone || (lang === 'zh' ? '未设置' : 'Not set')}
                    <span className="ml-2 text-xs" style={{ color: user?.phoneVerified ? 'var(--accent-emerald)' : 'var(--accent-amber)' }}>
                      {user?.phone ? (user.phoneVerified ? (lang === 'zh' ? '已验证' : 'Verified') : (lang === 'zh' ? '未验证' : 'Unverified')) : ''}
                    </span>
                  </span>
                </div>
                <div className="flex justify-between py-2 border-b" style={{ borderColor: 'var(--border-color)' }}>
                  <span style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '角色' : 'Role'}</span>
                  <span style={{ color: 'var(--text-primary)' }}>{user?.role || 'USER'}</span>
                </div>
                <div className="flex justify-between py-2 border-b" style={{ borderColor: 'var(--border-color)' }}>
                  <span style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '注册时间' : 'Created At'}</span>
                  <span style={{ color: 'var(--text-primary)' }}>{user?.createTime ? new Date(user.createTime).toLocaleDateString() : '--'}</span>
                </div>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      <ConfirmDialog
        open={logoutConfirmOpen}
        title={lang === 'zh' ? '确认退出' : 'Confirm Logout'}
        message={lang === 'zh' ? '确定要退出登录吗？退出后需要重新登录才能继续使用。' : 'Are you sure you want to logout? You will need to login again to continue.'}
        confirmText={lang === 'zh' ? '退出登录' : 'Logout'}
        cancelText={lang === 'zh' ? '取消' : 'Cancel'}
        variant="warning"
        onConfirm={confirmLogout}
        onCancel={cancelLogout}
      />
    </div>
  )
}
