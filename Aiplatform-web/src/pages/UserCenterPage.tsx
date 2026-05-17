import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAppStore } from '@/stores/appStore'
import { userApi, request, subscriptionApi } from '@/services/api'
import { useThemeStore } from '@/stores/themeStore'
import { useI18nStore } from '@/stores/i18nStore'
import { motion, AnimatePresence } from 'framer-motion'
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

type RecordTab = 'recharge' | 'points' | 'subscription'

export default function UserCenterPage() {
  const { user, fetchUserInfo, logout } = useAppStore()
  const { theme, toggleTheme } = useThemeStore()
  const { lang, toggleLang, t } = useI18nStore()
  const navigate = useNavigate()

  const [showPw, setShowPw] = useState(false)
  const [oldPw, setOldPw] = useState('')
  const [newPw, setNewPw] = useState('')
  const [confirmPw, setConfirmPw] = useState('')
  const [pwLoading, setPwLoading] = useState(false)
  const [pwMsg, setPwMsg] = useState<{ text: string; ok: boolean } | null>(null)

  const [selectedPackage, setSelectedPackage] = useState<Package | null>(null)
  const [showDetails, setShowDetails] = useState(false)
  const [purchasing, setPurchasing] = useState(false)
  const [packages, setPackages] = useState<Package[]>([])

  const [activeRecordTab, setActiveRecordTab] = useState<RecordTab>('recharge')
  const [rechargeRecords, setRechargeRecords] = useState<any[]>([])
  const [pointsBills, setPointsBills] = useState<any[]>([])
  const [subscriptionHistory, setSubscriptionHistory] = useState<any[]>([])
  const [recordsLoading, setRecordsLoading] = useState(false)
  const [showRecords, setShowRecords] = useState(false)

  const currentMembership = user?.membership || 'free'

  const getMembershipColor = (label: string) => {
    const colors: Record<string, string> = {
      free: '#94A3B8',
      silver: '#C0C0C0',
      gold: '#F59E0B',
      diamond: '#8B5CF6',
    }
    return colors[label?.toLowerCase()] || '#94A3B8'
  }

  const getMembershipIcon = (label: string) => {
    const icons: Record<string, string> = {
      free: '○',
      silver: '●',
      gold: '★',
      diamond: '◆',
    }
    return icons[label?.toLowerCase()] || '○'
  }

  const loadPackages = async () => {
    try {
      const data = await subscriptionApi.getPackages()
      setPackages(data || [])
    } catch (e) {
      console.error('Failed to load packages:', e)
    }
  }

  const handleSelectPackage = (pkg: Package) => {
    if (currentMembership === pkg.identityLabel.toLowerCase()) {
      return
    }
    setSelectedPackage(pkg)
    setShowDetails(true)
  }

  const handlePurchase = async () => {
    if (!selectedPackage) return
    setPurchasing(true)
    try {
      await subscriptionApi.purchase(selectedPackage.packageCode)
      useToastStore.getState().showToast({
        title: lang === 'zh' ? '升级成功' : 'Upgrade Success',
        message: lang === 'zh' ? `您已成为 ${selectedPackage.identityLabel} 用户！` : `You are now a ${selectedPackage.identityLabel} user!`,
        type: 'success',
      })
      setShowDetails(false)
      setSelectedPackage(null)
      await fetchUserInfo()
    } catch (e: any) {
      useToastStore.getState().showToast({
        title: lang === 'zh' ? '升级失败' : 'Upgrade Failed',
        message: (e as Error).message || (lang === 'zh' ? '购买失败，请重试' : 'Purchase failed, please retry'),
        type: 'error',
      })
    }
    setPurchasing(false)
  }

  const loadRecords = async () => {
    setRecordsLoading(true)
    try {
      if (activeRecordTab === 'recharge') {
        const data = await userApi.getRechargeRecords(1, 50)
        setRechargeRecords(data || [])
      } else if (activeRecordTab === 'points') {
        const data = await userApi.getPointsBills(1, 50)
        setPointsBills(data?.records || [])
      } else if (activeRecordTab === 'subscription') {
        const data = await subscriptionApi.getHistory()
        setSubscriptionHistory(data || [])
      }
    } catch (e) {
      console.error('Failed to load records:', e)
    }
    setRecordsLoading(false)
  }

  useEffect(() => {
    if (showRecords) loadRecords()
  }, [activeRecordTab, showRecords])

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

  const handleLogout = () => {
    logout()
  }

  return (
    <div className="p-4 lg:p-6 max-w-2xl mx-auto overflow-y-auto h-full">
      <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }}>
        <div className="flex items-center justify-between mb-6">
          <div>
            <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>{t('user.center')}</h1>
            <p className="text-sm mt-1" style={{ color: 'var(--text-dim)' }}>
              {lang === 'zh' ? '账户信息与余额管理' : 'Account & Settings'}
            </p>
          </div>
          <button
            onClick={handleLogout}
            className="px-4 py-2 text-xs rounded-xl transition-colors"
            style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)', border: '1px solid var(--border-color)' }}
          >
            {t('user.logout')}
          </button>
        </div>
      </motion.div>

      <div className="space-y-4">
        {/* User Profile Card */}
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.05 }}
          className="rounded-2xl p-6"
          style={{ background: 'var(--bg-card)', border: '1px solid var(--border-color)' }}
        >
          <div className="flex items-center gap-4 mb-5">
            <div
              className="w-12 h-12 rounded-2xl flex items-center justify-center text-lg font-bold text-white"
              style={{ background: 'linear-gradient(135deg, #8B5CF6, #3B82F6)' }}
            >
              {(user?.username || 'U')[0].toUpperCase()}
            </div>
            <div>
              <div className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>{user?.username || '--'}</div>
              <div className="flex items-center gap-2 mt-0.5">
                <span
                  className="text-[10px] px-2 py-0.5 rounded-full font-medium flex items-center gap-1"
                  style={{ background: `${getMembershipColor(currentMembership)}15`, color: getMembershipColor(currentMembership), border: `1px solid ${getMembershipColor(currentMembership)}30` }}
                >
                  {getMembershipIcon(currentMembership)} {user?.membership || 'free'}
                </span>
                <span
                  className="text-[10px] px-2 py-0.5 rounded-full font-medium"
                  style={{ background: 'rgba(16, 185, 129, 0.1)', color: '#10B981', border: '1px solid rgba(16, 185, 129, 0.2)' }}
                >
                  {user?.status === 'ACTIVE' ? (lang === 'zh' ? '活跃' : 'Active') : (lang === 'zh' ? '禁用' : 'Disabled')}
                </span>
              </div>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="p-4 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
              <div className="text-[10px] mb-1" style={{ color: 'var(--text-dim)' }}>{t('user.balance')}</div>
              <div className="text-xl font-bold" style={{ color: 'var(--text-primary)' }}>¥{Number(user?.balance || 0).toFixed(2)}</div>
            </div>
            <div className="p-4 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
              <div className="text-[10px] mb-1" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '邮箱' : 'Email'}</div>
              <div className="text-sm" style={{ color: 'var(--text-primary)' }}>{user?.email || (lang === 'zh' ? '未设置' : 'Not set')}</div>
            </div>
          </div>
          <div className="mt-4 grid grid-cols-2 gap-3">
            <button
              onClick={() => navigate('/recharge')}
              className="py-2.5 rounded-xl text-sm font-medium text-white transition-all"
              style={{ background: 'linear-gradient(135deg, #8B5CF6, #3B82F6)' }}
            >
              {lang === 'zh' ? '充值/升级' : 'Recharge/Upgrade'}
            </button>
            <button
              onClick={() => setShowRecords(!showRecords)}
              className="py-2.5 rounded-xl text-sm font-medium transition-all"
              style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)', border: '1px solid var(--border-color)' }}
            >
              {showRecords ? (lang === 'zh' ? '收起记录' : 'Hide Records') : (lang === 'zh' ? '账户记录' : 'Account Records')}
            </button>
          </div>
        </motion.div>

        {/* Account Records */}
        <AnimatePresence>
          {showRecords && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              className="overflow-hidden"
            >
              <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
                  {lang === 'zh' ? '📋 账户记录' : '📋 Account Records'}
                </h3>

                {/* Record tabs */}
                <div className="flex gap-2 mb-4">
                  {([
                    { key: 'recharge' as RecordTab, labelZh: '充值记录', labelEn: 'Recharge' },
                    { key: 'points' as RecordTab, labelZh: '点数消耗', labelEn: 'Points' },
                    { key: 'subscription' as RecordTab, labelZh: '会员记录', labelEn: 'Membership' },
                  ]).map(tab => (
                    <button
                      key={tab.key}
                      onClick={() => setActiveRecordTab(tab.key)}
                      className="flex-1 py-2 px-3 rounded-lg text-xs font-medium transition-all"
                      style={{
                        background: activeRecordTab === tab.key ? 'var(--accent-purple)' : 'var(--bg-surface)',
                        color: activeRecordTab === tab.key ? '#fff' : 'var(--text-dim)',
                      }}
                    >
                      {lang === 'zh' ? tab.labelZh : tab.labelEn}
                    </button>
                  ))}
                </div>

                {/* Records content */}
                <div className="max-h-80 overflow-y-auto space-y-2">
                  {recordsLoading ? (
                    <div className="text-center py-8 text-xs" style={{ color: 'var(--text-dim)' }}>
                      {lang === 'zh' ? '加载中...' : 'Loading...'}
                    </div>
                  ) : activeRecordTab === 'recharge' && rechargeRecords.length === 0 ? (
                    <div className="text-center py-8 text-xs" style={{ color: 'var(--text-dim)' }}>
                      {lang === 'zh' ? '暂无充值记录' : 'No recharge records'}
                    </div>
                  ) : activeRecordTab === 'recharge' ? (
                    rechargeRecords.map((r, i) => (
                      <div key={i} className="flex items-center justify-between p-3 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
                        <div>
                          <div className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>¥{Number(r.amount || 0).toFixed(2)}</div>
                          <div className="text-[10px]" style={{ color: 'var(--text-dim)' }}>{r.createTime ? new Date(r.createTime).toLocaleString() : '--'}</div>
                        </div>
                        <span className="px-2 py-1 rounded-md text-[10px]" style={{ background: 'rgba(16,185,129,0.1)', color: '#10B981' }}>
                          {lang === 'zh' ? '成功' : 'Success'}
                        </span>
                      </div>
                    ))
                  ) : activeRecordTab === 'points' && pointsBills.length === 0 ? (
                    <div className="text-center py-8 text-xs" style={{ color: 'var(--text-dim)' }}>
                      {lang === 'zh' ? '暂无点数消耗记录' : 'No points records'}
                    </div>
                  ) : activeRecordTab === 'points' ? (
                    pointsBills.map((r, i) => (
                      <div key={i} className="flex items-center justify-between p-3 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
                        <div>
                          <div className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>{r.description || r.changeType || '--'}</div>
                          <div className="text-[10px]" style={{ color: 'var(--text-dim)' }}>{r.createTime ? new Date(r.createTime).toLocaleString() : '--'}</div>
                        </div>
                        <span className={`px-2 py-1 rounded-md text-[10px] font-mono font-bold ${Number(r.changeAmount || 0) > 0 ? '' : ''}`} style={{
                          background: Number(r.changeAmount || 0) > 0 ? 'rgba(16,185,129,0.1)' : 'rgba(244,63,94,0.1)',
                          color: Number(r.changeAmount || 0) > 0 ? '#10B981' : '#F43F5E',
                        }}>
                          {Number(r.changeAmount || 0) > 0 ? '+' : ''}{Math.round(r.changeAmount || 0)} {lang === 'zh' ? '点' : 'pts'}
                        </span>
                      </div>
                    ))
                  ) : activeRecordTab === 'subscription' && subscriptionHistory.length === 0 ? (
                    <div className="text-center py-8 text-xs" style={{ color: 'var(--text-dim)' }}>
                      {lang === 'zh' ? '暂无会员记录' : 'No membership records'}
                    </div>
                  ) : activeRecordTab === 'subscription' ? (
                    subscriptionHistory.map((r, i) => (
                      <div key={i} className="flex items-center justify-between p-3 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
                        <div>
                          <div className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>{r.packageName || r.identityLabel || '--'}</div>
                          <div className="text-[10px]" style={{ color: 'var(--text-dim)' }}>{r.purchaseTime ? new Date(r.purchaseTime).toLocaleString() : '--'}</div>
                        </div>
                        <span className="px-2 py-1 rounded-md text-[10px]" style={{ background: `${getMembershipColor(r.identityLabel || r.membership || 'free')}15`, color: getMembershipColor(r.identityLabel || r.membership || 'free') }}>
                          {lang === 'zh' ? '已购买' : 'Purchased'}
                        </span>
                      </div>
                    ))
                  ) : null}
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Settings */}
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
          className="rounded-2xl p-6"
          style={{ background: 'var(--bg-card)', border: '1px solid var(--border-color)' }}
        >
          <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>{t('user.settings')}</h3>
          <div className="space-y-3">
            <div className="flex items-center justify-between py-2">
              <span className="text-xs" style={{ color: 'var(--text-dim)' }}>{t('theme.dark')} / {t('theme.light')}</span>
              <button
                onClick={toggleTheme}
                className="relative w-10 h-5 rounded-full transition-colors"
                style={{ background: theme === 'dark' ? '#8B5CF6' : '#CBD5E1' }}
              >
                <span
                  className="absolute top-0.5 w-4 h-4 rounded-full bg-white shadow transition-transform"
                  style={{ transform: theme === 'dark' ? 'translateX(20px)' : 'translateX(2px)' }}
                />
              </button>
            </div>
            <div className="flex items-center justify-between py-2">
              <span className="text-xs" style={{ color: 'var(--text-dim)' }}>语言 / Language</span>
              <button
                onClick={toggleLang}
                className="px-3 py-1 rounded-lg text-xs font-medium transition-all"
                style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)' }}
              >
                {lang === 'zh' ? '切换 English' : 'Switch 中文'}
              </button>
            </div>
          </div>
        </motion.div>

        {/* Change Password */}
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
          className="rounded-2xl p-6"
          style={{ background: 'var(--bg-card)', border: '1px solid var(--border-color)' }}
        >
          <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>{t('user.changePassword')}</h3>
          {!showPw ? (
            <button
              onClick={() => setShowPw(true)}
              className="px-4 py-2 text-xs rounded-xl transition-colors"
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
              <div className="flex gap-3">
                <button
                  onClick={handleChangePw}
                  disabled={pwLoading}
                  className="px-5 py-2.5 text-sm font-medium rounded-xl transition-all"
                  style={{ background: 'linear-gradient(135deg, #8B5CF6, #3B82F6)', color: 'white' }}
                >
                  {pwLoading ? (lang === 'zh' ? '修改中...' : 'Changing...') : t('user.changePassword')}
                </button>
                <button
                  onClick={() => { setShowPw(false); setPwMsg(null) }}
                  className="px-5 py-2.5 text-sm rounded-xl transition-all"
                  style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)', border: '1px solid var(--border-color)' }}
                >
                  {lang === 'zh' ? '取消' : 'Cancel'}
                </button>
              </div>
              {pwMsg && <p className={`text-xs ${pwMsg.ok ? 'text-green-500' : 'text-red-500'}`}>{pwMsg.text}</p>}
            </div>
          )}
        </motion.div>
      </div>

      {/* Package detail modal */}
      <AnimatePresence>
        {showDetails && selectedPackage && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 flex items-center justify-center p-4"
            style={{ background: 'rgba(0,0,0,0.6)' }}
            onClick={() => !purchasing && setShowDetails(false)}
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
                  {lang === 'zh' ? '套餐详情' : 'Package Details'}
                </h3>
                <button
                  onClick={() => !purchasing && setShowDetails(false)}
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
                      background: `${getMembershipColor(selectedPackage.identityLabel.toLowerCase())}20`,
                      color: getMembershipColor(selectedPackage.identityLabel.toLowerCase()),
                    }}
                  >
                    {getMembershipIcon(selectedPackage.identityLabel.toLowerCase())}
                  </div>
                  <div>
                    <div className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>
                      {selectedPackage.identityLabel}
                    </div>
                    <div className="text-xs" style={{ color: 'var(--text-dim)' }}>
                      {selectedPackage.packageName}
                    </div>
                  </div>
                </div>

                <div className="p-4 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
                  <div className="flex items-baseline gap-1 mb-2">
                    <span className="text-3xl font-bold" style={{ color: getMembershipColor(selectedPackage.identityLabel.toLowerCase()) }}>
                      ¥{selectedPackage.priceMonthly}
                    </span>
                    <span className="text-sm" style={{ color: 'var(--text-dim)' }}>
                      {lang === 'zh' ? '/月' : '/month'}
                    </span>
                  </div>
                  {selectedPackage.description && (
                    <p className="text-xs" style={{ color: 'var(--text-dim)' }}>{selectedPackage.description}</p>
                  )}
                </div>

                <div className="grid grid-cols-2 gap-3 text-xs">
                  <div className="p-3 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
                    <div style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '开通赠送' : 'Bonus Points'}</div>
                    <div className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>{selectedPackage.pointsGranted}</div>
                  </div>
                  <div className="p-3 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
                    <div style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '每日永久点数' : 'Daily Pts'}</div>
                    <div className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>{selectedPackage.dailyPoints}</div>
                  </div>
                  {selectedPackage.priceQuarterly > 0 && (
                    <div className="p-3 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
                      <div style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '季付（9折）' : 'Quarterly'}</div>
                      <div className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>¥{selectedPackage.priceQuarterly}</div>
                    </div>
                  )}
                  {selectedPackage.priceYearly > 0 && (
                    <div className="p-3 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
                      <div style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '年付（7折）' : 'Yearly'}</div>
                      <div className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>¥{selectedPackage.priceYearly}</div>
                    </div>
                  )}
                  {selectedPackage.modelAccessLevel > 0 && (
                    <div className="p-3 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
                      <div style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '模型权限' : 'Model Access'}</div>
                      <div className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>Lv.{selectedPackage.modelAccessLevel}</div>
                    </div>
                  )}
                </div>

                <div className="text-xs" style={{ color: 'var(--text-dim)' }}>
                  <div className="font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>
                    {lang === 'zh' ? '权益说明' : 'Benefits'}
                  </div>
                  <ul className="space-y-1">
                    {selectedPackage.features && (() => {
                      try {
                        const features = JSON.parse(selectedPackage.features)
                        return features.map((f: string, j: number) => (
                          <li key={j} className="flex items-start gap-2">
                            <span style={{ color: getMembershipColor(selectedPackage.identityLabel.toLowerCase()) }}>✓</span>
                            {f}
                          </li>
                        ))
                      } catch {
                        return null
                      }
                    })()}
                  </ul>
                </div>

                <div className="pt-2 flex gap-3">
                  <button
                    onClick={() => setShowDetails(false)}
                    disabled={purchasing}
                    className="flex-1 py-3 rounded-xl text-sm font-medium transition-colors disabled:opacity-50"
                    style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)', border: '1px solid var(--border-color)' }}
                  >
                    {lang === 'zh' ? '取消' : 'Cancel'}
                  </button>
                  <button
                    onClick={handlePurchase}
                    disabled={purchasing}
                    className="flex-1 py-3 rounded-xl text-sm font-medium text-white transition-all disabled:opacity-50"
                    style={{ background: getMembershipColor(selectedPackage.identityLabel.toLowerCase()) }}
                  >
                    {purchasing
                      ? (lang === 'zh' ? '处理中...' : 'Processing...')
                      : (lang === 'zh' ? `确认升级 (¥${selectedPackage.priceMonthly})` : `Confirm Upgrade (¥${selectedPackage.priceMonthly})`)
                    }
                  </button>
                </div>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
