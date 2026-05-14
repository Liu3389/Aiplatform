import { useState } from 'react'
import { useAppStore } from '@/stores/appStore'
import { userApi, request } from '@/services/api'
import { useThemeStore } from '@/stores/themeStore'
import { useI18nStore } from '@/stores/i18nStore'
import { motion } from 'framer-motion'

export default function UserCenterPage() {
  const { user, fetchUserInfo, logout } = useAppStore()
  const { theme, toggleTheme } = useThemeStore()
  const { lang, toggleLang, t } = useI18nStore()
  const [amount, setAmount] = useState('')
  const [loading, setLoading] = useState(false)
  const [msg, setMsg] = useState<{ text: string; ok: boolean } | null>(null)

  const [showPw, setShowPw] = useState(false)
  const [oldPw, setOldPw] = useState('')
  const [newPw, setNewPw] = useState('')
  const [confirmPw, setConfirmPw] = useState('')
  const [pwLoading, setPwLoading] = useState(false)
  const [pwMsg, setPwMsg] = useState<{ text: string; ok: boolean } | null>(null)

  const handleRecharge = async () => {
    const val = parseFloat(amount)
    if (!val || val <= 0) { setMsg({ text: lang === 'zh' ? '请输入有效金额' : 'Enter a valid amount', ok: false }); return }
    setLoading(true); setMsg(null)
    try { await userApi.recharge(val); await fetchUserInfo(); setMsg({ text: lang === 'zh' ? '充值成功！' : 'Recharge success!', ok: true }); setAmount('') }
    catch (err) { setMsg({ text: (err as Error).message || (lang === 'zh' ? '充值失败' : 'Recharge failed'), ok: false }) }
    finally { setLoading(false) }
  }

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
                  className="text-[10px] px-2 py-0.5 rounded-full font-medium"
                  style={{ background: 'rgba(139, 92, 246, 0.15)', color: '#8B5CF6', border: '1px solid rgba(139, 92, 246, 0.2)' }}
                >
                  {user?.role || 'USER'}
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
              <div className="text-xl font-bold" style={{ color: 'var(--text-primary)' }}>¥{Number(user?.balance || 0).toFixed(4)}</div>
            </div>
            <div className="p-4 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
              <div className="text-[10px] mb-1" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '邮箱' : 'Email'}</div>
              <div className="text-sm" style={{ color: 'var(--text-primary)' }}>{user?.email || (lang === 'zh' ? '未设置' : 'Not set')}</div>
            </div>
          </div>
        </motion.div>

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

        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.15 }}
          className="rounded-2xl p-6"
          style={{ background: 'var(--bg-card)', border: '1px solid var(--border-color)' }}
        >
          <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>{t('user.recharge')}</h3>
          <div className="flex items-center gap-3">
            <input
              value={amount}
              onChange={e => setAmount(e.target.value)}
              placeholder={lang === 'zh' ? '输入金额（美元）' : 'Amount (USD)'}
              type="number"
              className="flex-1 px-4 py-3 text-sm rounded-xl"
              style={{ background: 'var(--bg-surface)', color: 'var(--text-primary)', border: '1px solid var(--border-color)', outline: 'none' }}
            />
            <button
              onClick={handleRecharge}
              disabled={loading}
              className="px-6 py-3 text-sm font-medium rounded-xl transition-all"
              style={{ background: 'linear-gradient(135deg, #8B5CF6, #3B82F6)', color: 'white' }}
            >
              {loading ? (lang === 'zh' ? '处理中...' : 'Processing...') : (lang === 'zh' ? '充值' : 'Recharge')}
            </button>
          </div>
          {msg && <p className={`text-xs mt-3 ${msg.ok ? 'text-green-500' : 'text-red-500'}`}>{msg.text}</p>}
        </motion.div>

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
    </div>
  )
}
