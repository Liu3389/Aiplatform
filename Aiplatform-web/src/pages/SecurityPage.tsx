import { useState, useEffect } from 'react'
import { useI18nStore } from '@/stores/i18nStore'
import { useAppStore } from '@/stores/appStore'
import { request } from '@/services/api'
import { motion, AnimatePresence } from 'framer-motion'

type Tab = 'password' | 'email' | 'phone' | '2fa' | 'devices' | 'delete' | 'recharge' | 'invoice' | 'coupons' | 'subscription' | 'oauth'

interface ApiResponse<T = any> { code: number; message?: string; data?: T }

export default function SecurityPage() {
  const { lang } = useI18nStore()
  const { token, logout } = useAppStore()

  const [tab, setTab] = useState<Tab>('password')
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  const [passwordForm, setPasswordForm] = useState({ oldPassword: '', newPassword: '', confirmPassword: '' })
  const [emailForm, setEmailForm] = useState({ email: '', code: '' })
  const [phoneForm, setPhoneForm] = useState({ phone: '', code: '' })
  const [twoFAEnabled, setTwoFAEnabled] = useState(false)
  const [twoFASecret, setTwoFASecret] = useState('')
  const [twoFACode, setTwoFACode] = useState('')
  const [devices, setDevices] = useState<any[]>([])
  const [rechargeAmount, setRechargeAmount] = useState('')
  const [rechargeRecords, setRechargeRecords] = useState<any[]>([])
  const [invoiceForm, setInvoiceForm] = useState({ title: '', taxNumber: '', amount: '' })
  const [coupons, setCoupons] = useState<any[]>([])
  const [redeemCode, setRedeemCode] = useState('')
  const [subscription, setSubscription] = useState<any>(null)
  const [deleteConfirm, setDeleteConfirm] = useState('')
  const [codeSending, setCodeSending] = useState<string | null>(null)
  const [paymentMethod, setPaymentMethod] = useState('Alipay')
  const [oauthBindings, setOauthBindings] = useState<{ provider: string; bound: boolean }[]>([])

  const api = async <T,>(opts: { method?: string; url: string; data?: any }): Promise<ApiResponse<T>> => {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (token) headers['X-User-Token'] = token
    return request<ApiResponse<T>>(opts.url, {
      method: opts.method || 'GET',
      headers,
      body: opts.data ? JSON.stringify(opts.data) : undefined,
    })
  }

  useEffect(() => {
    if (token) {
      load2FAStatus()
      loadDevices()
      loadRechargeRecords()
      loadCoupons()
      loadSubscription()
      loadOAuthBindings()
    }
  }, [token])

  const load2FAStatus = async () => {
    try {
      const res = await api<{ enabled: boolean }>({ url: '/user/2fa/status' })
      if (res.code === 200) setTwoFAEnabled(res.data?.enabled || false)
    } catch { /* ignore */ }
  }

  const loadDevices = async () => {
    try {
      const res = await api<any[]>({ url: '/user/login-devices' })
      if (res.code === 200) setDevices(res.data || [])
    } catch { /* ignore */ }
  }

  const loadRechargeRecords = async () => {
    try {
      const res = await api<any[]>({ url: '/user/recharge-records' })
      if (res.code === 200) setRechargeRecords(res.data || [])
    } catch { /* ignore */ }
  }

  const loadCoupons = async () => {
    try {
      const res = await api<any[]>({ url: '/user/coupons' })
      if (res.code === 200) setCoupons(res.data || [])
    } catch { /* ignore */ }
  }

  const loadSubscription = async () => {
    try {
      const res = await api<any>({ url: '/user/subscription' })
      if (res.code === 200) setSubscription(res.data)
    } catch { /* ignore */ }
  }

  const showMessage = (type: 'success' | 'error', text: string) => {
    setMessage({ type, text })
    setTimeout(() => setMessage(null), 3000)
  }

  const handleChangePassword = async () => {
    if (!passwordForm.oldPassword || !passwordForm.newPassword || !passwordForm.confirmPassword) {
      return showMessage('error', lang === 'zh' ? '请填写所有字段' : 'Please fill all fields')
    }
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      return showMessage('error', lang === 'zh' ? '两次密码不一致' : 'Passwords do not match')
    }
    if (passwordForm.newPassword.length < 6) {
      return showMessage('error', lang === 'zh' ? '密码长度至少6位' : 'Password must be at least 6 characters')
    }
    setLoading(true)
    try {
      const res = await api({ url: '/user/change-password', method: 'POST', data: { oldPassword: passwordForm.oldPassword, newPassword: passwordForm.newPassword } })
      if (res.code === 200) {
        showMessage('success', lang === 'zh' ? '密码修改成功' : 'Password changed successfully')
        setPasswordForm({ oldPassword: '', newPassword: '', confirmPassword: '' })
      } else {
        showMessage('error', res.message || (lang === 'zh' ? '修改失败' : 'Failed'))
      }
    } catch { showMessage('error', lang === 'zh' ? '请求失败' : 'Request failed') }
    setLoading(false)
  }

  const sendCode = async (type: 'email' | 'phone', target: string) => {
    if (!target) return
    setCodeSending(type)
    try {
      await api({ url: '/user/send-bind-code', method: 'POST', data: { type, target } })
      showMessage('success', lang === 'zh' ? '验证码已发送' : 'Code sent')
    } catch { showMessage('error', lang === 'zh' ? '发送失败' : 'Failed') }
    setCodeSending(null)
  }

  const handleBindEmail = async () => {
    if (!emailForm.email || !emailForm.code) return showMessage('error', lang === 'zh' ? '请填写邮箱和验证码' : 'Please fill email and code')
    setLoading(true)
    try {
      const res = await api({ url: '/user/bind-email', method: 'POST', data: { email: emailForm.email, code: emailForm.code } })
      if (res.code === 200) {
        showMessage('success', lang === 'zh' ? '邮箱绑定成功' : 'Email bound successfully')
        setEmailForm({ email: '', code: '' })
      } else {
        showMessage('error', res.message || (lang === 'zh' ? '绑定失败' : 'Failed'))
      }
    } catch { showMessage('error', lang === 'zh' ? '请求失败' : 'Request failed') }
    setLoading(false)
  }

  const handleBindPhone = async () => {
    if (!phoneForm.phone || !phoneForm.code) return showMessage('error', lang === 'zh' ? '请填写手机号和验证码' : 'Please fill phone and code')
    setLoading(true)
    try {
      const res = await api({ url: '/user/bind-phone', method: 'POST', data: { phone: phoneForm.phone, code: phoneForm.code } })
      if (res.code === 200) {
        showMessage('success', lang === 'zh' ? '手机号绑定成功' : 'Phone bound successfully')
        setPhoneForm({ phone: '', code: '' })
      } else {
        showMessage('error', res.message || (lang === 'zh' ? '绑定失败' : 'Failed'))
      }
    } catch { showMessage('error', lang === 'zh' ? '请求失败' : 'Request failed') }
    setLoading(false)
  }

  const handleEnable2FA = async () => {
    setLoading(true)
    try {
      const res = await api<{ secret: string }>({ url: '/user/2fa/enable', method: 'POST' })
      if (res.code === 200) {
        setTwoFASecret(res.data?.secret || '')
        showMessage('success', lang === 'zh' ? '请扫描二维码并输入验证码' : 'Please scan QR and enter code')
      } else {
        showMessage('error', res.message || (lang === 'zh' ? '启用失败' : 'Failed'))
      }
    } catch { showMessage('error', lang === 'zh' ? '请求失败' : 'Request failed') }
    setLoading(false)
  }

  const handleVerify2FA = async () => {
    if (!twoFACode) return showMessage('error', lang === 'zh' ? '请输入验证码' : 'Please enter code')
    setLoading(true)
    try {
      const res = await api<{ success: boolean }>({ url: '/user/2fa/verify', method: 'POST', data: { code: twoFACode } })
      if (res.code === 200 && res.data?.success) {
        setTwoFAEnabled(true)
        setTwoFASecret('')
        setTwoFACode('')
        showMessage('success', lang === 'zh' ? '2FA已启用' : '2FA enabled')
      } else {
        showMessage('error', lang === 'zh' ? '验证码错误' : 'Invalid code')
      }
    } catch { showMessage('error', lang === 'zh' ? '请求失败' : 'Request failed') }
    setLoading(false)
  }

  const handleDisable2FA = async () => {
    if (!twoFACode) return showMessage('error', lang === 'zh' ? '请输入验证码' : 'Please enter code')
    setLoading(true)
    try {
      const res = await api({ url: '/user/2fa/disable', method: 'POST', data: { code: twoFACode } })
      if (res.code === 200) {
        setTwoFAEnabled(false)
        setTwoFACode('')
        showMessage('success', lang === 'zh' ? '2FA已禁用' : '2FA disabled')
      } else {
        showMessage('error', res.message || (lang === 'zh' ? '禁用失败' : 'Failed'))
      }
    } catch { showMessage('error', lang === 'zh' ? '请求失败' : 'Request failed') }
    setLoading(false)
  }

  const handleRemoveDevice = async (deviceId: string) => {
    try {
      await api({ url: '/user/login-devices/remove', method: 'POST', data: { deviceId } })
      showMessage('success', lang === 'zh' ? '设备已移除' : 'Device removed')
      loadDevices()
    } catch { showMessage('error', lang === 'zh' ? '移除失败' : 'Failed') }
  }

  const handleRecharge = async () => {
    const amount = parseFloat(rechargeAmount)
    if (!amount || amount <= 0) return showMessage('error', lang === 'zh' ? '请输入有效金额' : 'Please enter valid amount')
    setLoading(true)
    try {
      const res = await api({ url: '/user/recharge?amount=' + amount + '&paymentMethod=' + paymentMethod, method: 'POST' })
      if (res.code === 200) {
        showMessage('success', lang === 'zh' ? '充值成功' : 'Recharge successful')
        setRechargeAmount('')
        loadRechargeRecords()
      } else {
        showMessage('error', res.message || (lang === 'zh' ? '充值失败' : 'Failed'))
      }
    } catch { showMessage('error', lang === 'zh' ? '请求失败' : 'Request failed') }
    setLoading(false)
  }

  const handleApplyInvoice = async () => {
    if (!invoiceForm.title) return showMessage('error', lang === 'zh' ? '请填写发票抬头' : 'Please enter invoice title')
    setLoading(true)
    try {
      const res = await api({ url: '/user/invoice/apply', method: 'POST', data: { ...invoiceForm, amount: parseFloat(invoiceForm.amount) || 0 } })
      if (res.code === 200) {
        showMessage('success', lang === 'zh' ? '发票申请已提交' : 'Invoice applied')
        setInvoiceForm({ title: '', taxNumber: '', amount: '' })
      } else {
        showMessage('error', res.message || (lang === 'zh' ? '申请失败' : 'Failed'))
      }
    } catch { showMessage('error', lang === 'zh' ? '请求失败' : 'Request failed') }
    setLoading(false)
  }

  const handleRedeemCoupon = async () => {
    if (!redeemCode) return showMessage('error', lang === 'zh' ? '请输入兑换码' : 'Please enter code')
    setLoading(true)
    try {
      const res = await api({ url: '/user/coupons/redeem', method: 'POST', data: { redeemCode } })
      if (res.code === 200) {
        showMessage('success', lang === 'zh' ? '兑换成功' : 'Redeemed successfully')
        setRedeemCode('')
        loadCoupons()
      } else {
        showMessage('error', res.message || (lang === 'zh' ? '兑换失败' : 'Failed'))
      }
    } catch { showMessage('error', lang === 'zh' ? '请求失败' : 'Request failed') }
    setLoading(false)
  }

  const handleSubscribe = async (plan: string) => {
    setLoading(true)
    try {
      const res = await api({ url: '/user/subscription/subscribe', method: 'POST', data: { plan, paymentMethod: 'Alipay' } })
      if (res.code === 200) {
        showMessage('success', lang === 'zh' ? '订阅成功' : 'Subscribed successfully')
        loadSubscription()
      } else {
        showMessage('error', res.message || (lang === 'zh' ? '订阅失败' : 'Failed'))
      }
    } catch { showMessage('error', lang === 'zh' ? '请求失败' : 'Request failed') }
    setLoading(false)
  }

  const handleCancelSubscription = async () => {
    setLoading(true)
    try {
      const res = await api({ url: '/user/subscription/cancel', method: 'POST' })
      if (res.code === 200) {
        showMessage('success', lang === 'zh' ? '订阅已取消' : 'Subscription cancelled')
        loadSubscription()
      } else {
        showMessage('error', res.message || (lang === 'zh' ? '取消失败' : 'Failed'))
      }
    } catch { showMessage('error', lang === 'zh' ? '请求失败' : 'Request failed') }
    setLoading(false)
  }

  const handleDeleteAccount = async () => {
    if (deleteConfirm !== 'DELETE') return showMessage('error', lang === 'zh' ? '请输入 DELETE 确认' : 'Please enter DELETE to confirm')
    setLoading(true)
    try {
      const res = await api({ url: '/user/delete-account', method: 'POST', data: { confirm: 'DELETE' } })
      if (res.code === 200) {
        showMessage('success', lang === 'zh' ? '账户已注销' : 'Account deleted')
        setTimeout(() => logout(), 1500)
      } else {
        showMessage('error', res.message || (lang === 'zh' ? '注销失败' : 'Failed'))
      }
    } catch { showMessage('error', lang === 'zh' ? '请求失败' : 'Request failed') }
    setLoading(false)
  }

  const loadOAuthBindings = () => {
    setOauthBindings([
      { provider: 'github', bound: !!localStorage.getItem('oauth_github_bound') },
      { provider: 'google', bound: !!localStorage.getItem('oauth_google_bound') },
      { provider: 'wechat', bound: !!localStorage.getItem('oauth_wechat_bound') },
    ])
  }

  const handleBindOAuth = async (provider: string) => {
    setLoading(true)
    try {
      localStorage.setItem('oauth_' + provider + '_bound', 'true')
      loadOAuthBindings()
      showMessage('success', lang === 'zh' ? '绑定成功' : 'Bound successfully')
    } catch { showMessage('error', lang === 'zh' ? '绑定失败' : 'Bind failed') }
    setLoading(false)
  }

  const handleUnbindOAuth = async (provider: string) => {
    setLoading(true)
    try {
      localStorage.removeItem('oauth_' + provider + '_bound')
      loadOAuthBindings()
      showMessage('success', lang === 'zh' ? '解绑成功' : 'Unbound successfully')
    } catch { showMessage('error', lang === 'zh' ? '解绑失败' : 'Unbind failed') }
    setLoading(false)
  }

  const tabs: { key: Tab; label: string }[] = [
    { key: 'password', label: lang === 'zh' ? '修改密码' : 'Password' },
    { key: 'email', label: lang === 'zh' ? '绑定邮箱' : 'Email' },
    { key: 'phone', label: lang === 'zh' ? '绑定手机' : 'Phone' },
    { key: '2fa', label: lang === 'zh' ? '两步验证' : '2FA' },
    { key: 'devices', label: lang === 'zh' ? '登录设备' : 'Devices' },
    { key: 'recharge', label: lang === 'zh' ? '充值' : 'Recharge' },
    { key: 'invoice', label: lang === 'zh' ? '发票' : 'Invoice' },
    { key: 'coupons', label: lang === 'zh' ? '优惠券' : 'Coupons' },
    { key: 'subscription', label: lang === 'zh' ? '订阅' : 'Subscription' },
    { key: 'oauth', label: lang === 'zh' ? '第三方登录' : 'OAuth' },
    { key: 'delete', label: lang === 'zh' ? '账户注销' : 'Delete Account' },
  ]

  return (
    <div className="p-4 lg:p-8 max-w-5xl mx-auto overflow-y-auto h-full">
      <motion.div initial={{ opacity: 0, y: -20 }} animate={{ opacity: 1, y: 0 }}>
        <h1 className="text-2xl font-bold mb-6" style={{ color: 'var(--text-primary)' }}>
          {lang === 'zh' ? '账户与安全' : 'Account & Security'}
        </h1>
      </motion.div>

      <AnimatePresence>
        {message && (
          <motion.div
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            className="mb-6 px-4 py-3 rounded-xl text-sm font-medium"
            style={{
              background: message.type === 'success' ? 'rgba(16,185,129,0.15)' : 'rgba(244,63,94,0.15)',
              color: message.type === 'success' ? '#10B981' : '#F43F5E',
              border: '1px solid ' + (message.type === 'success' ? 'rgba(16,185,129,0.3)' : 'rgba(244,63,94,0.3)')
            }}
          >
            {message.text}
          </motion.div>
        )}
      </AnimatePresence>

      <div className="flex flex-wrap gap-2 mb-8">
        {tabs.map(t => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className="px-4 py-2 rounded-xl text-sm font-medium transition-all"
            style={{
              background: tab === t.key ? 'var(--accent-purple)' : 'var(--bg-surface)',
              color: tab === t.key ? '#fff' : 'var(--text-dim)',
              border: '1px solid ' + (tab === t.key ? 'var(--accent-purple)' : 'var(--border-color)')
            }}
          >
            {t.label}
          </button>
        ))}
      </div>

      <motion.div
        key={tab}
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        className="rounded-2xl p-6 border"
        style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}
      >
        {tab === 'password' && (
          <div className="space-y-4">
            <h3 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? '修改密码' : 'Change Password'}</h3>
            <div className="space-y-3">
              <div>
                <label className="block text-sm mb-1.5" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '旧密码' : 'Old Password'}</label>
                <input type="password" value={passwordForm.oldPassword} onChange={e => setPasswordForm({ ...passwordForm, oldPassword: e.target.value })} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none" style={{ background: 'var(--bg-input)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }} />
              </div>
              <div>
                <label className="block text-sm mb-1.5" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '新密码' : 'New Password'}</label>
                <input type="password" value={passwordForm.newPassword} onChange={e => setPasswordForm({ ...passwordForm, newPassword: e.target.value })} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none" style={{ background: 'var(--bg-input)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }} />
              </div>
              <div>
                <label className="block text-sm mb-1.5" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '确认密码' : 'Confirm Password'}</label>
                <input type="password" value={passwordForm.confirmPassword} onChange={e => setPasswordForm({ ...passwordForm, confirmPassword: e.target.value })} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none" style={{ background: 'var(--bg-input)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }} />
              </div>
              <button onClick={handleChangePassword} disabled={loading} className="w-full py-2.5 rounded-xl text-sm font-medium text-white transition-all disabled:opacity-50" style={{ background: 'var(--accent-purple)' }}>
                {loading ? (lang === 'zh' ? '处理中...' : 'Processing...') : (lang === 'zh' ? '修改密码' : 'Change Password')}
              </button>
            </div>
          </div>
        )}

        {tab === 'email' && (
          <div className="space-y-4">
            <h3 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? '绑定邮箱' : 'Bind Email'}</h3>
            <div className="space-y-3">
              <div>
                <label className="block text-sm mb-1.5" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '邮箱地址' : 'Email'}</label>
                <div className="flex gap-2">
                  <input type="email" value={emailForm.email} onChange={e => setEmailForm({ ...emailForm, email: e.target.value })} className="flex-1 px-4 py-2.5 rounded-xl text-sm outline-none" style={{ background: 'var(--bg-input)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }} />
                  <button onClick={() => sendCode('email', emailForm.email)} disabled={codeSending !== null} className="px-4 py-2 rounded-xl text-sm" style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)', border: '1px solid var(--border-color)' }}>
                    {codeSending === 'email' ? '...' : (lang === 'zh' ? '发送验证码' : 'Send Code')}
                  </button>
                </div>
              </div>
              <div>
                <label className="block text-sm mb-1.5" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '验证码' : 'Verification Code'}</label>
                <input type="text" value={emailForm.code} onChange={e => setEmailForm({ ...emailForm, code: e.target.value })} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none" style={{ background: 'var(--bg-input)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }} />
              </div>
              <button onClick={handleBindEmail} disabled={loading} className="w-full py-2.5 rounded-xl text-sm font-medium text-white transition-all disabled:opacity-50" style={{ background: 'var(--accent-purple)' }}>
                {loading ? (lang === 'zh' ? '处理中...' : 'Processing...') : (lang === 'zh' ? '绑定邮箱' : 'Bind Email')}
              </button>
            </div>
          </div>
        )}

        {tab === 'phone' && (
          <div className="space-y-4">
            <h3 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? '绑定手机' : 'Bind Phone'}</h3>
            <div className="space-y-3">
              <div>
                <label className="block text-sm mb-1.5" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '手机号' : 'Phone Number'}</label>
                <div className="flex gap-2">
                  <input type="tel" value={phoneForm.phone} onChange={e => setPhoneForm({ ...phoneForm, phone: e.target.value })} className="flex-1 px-4 py-2.5 rounded-xl text-sm outline-none" style={{ background: 'var(--bg-input)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }} />
                  <button onClick={() => sendCode('phone', phoneForm.phone)} disabled={codeSending !== null} className="px-4 py-2 rounded-xl text-sm" style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)', border: '1px solid var(--border-color)' }}>
                    {codeSending === 'phone' ? '...' : (lang === 'zh' ? '发送验证码' : 'Send Code')}
                  </button>
                </div>
              </div>
              <div>
                <label className="block text-sm mb-1.5" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '验证码' : 'Verification Code'}</label>
                <input type="text" value={phoneForm.code} onChange={e => setPhoneForm({ ...phoneForm, code: e.target.value })} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none" style={{ background: 'var(--bg-input)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }} />
              </div>
              <button onClick={handleBindPhone} disabled={loading} className="w-full py-2.5 rounded-xl text-sm font-medium text-white transition-all disabled:opacity-50" style={{ background: 'var(--accent-purple)' }}>
                {loading ? (lang === 'zh' ? '处理中...' : 'Processing...') : (lang === 'zh' ? '绑定手机' : 'Bind Phone')}
              </button>
            </div>
          </div>
        )}

        {tab === '2fa' && (
          <div className="space-y-4">
            <h3 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? '两步验证 (2FA)' : 'Two-Factor Authentication'}</h3>
            <div className="p-4 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
              <p className="text-sm" style={{ color: 'var(--text-dim)' }}>
                {twoFAEnabled ? (lang === 'zh' ? '2FA 已启用' : '2FA is enabled') : (lang === 'zh' ? '2FA 未启用' : '2FA is not enabled')}
              </p>
            </div>
            {!twoFAEnabled && !twoFASecret && (
              <button onClick={handleEnable2FA} disabled={loading} className="w-full py-2.5 rounded-xl text-sm font-medium text-white transition-all disabled:opacity-50" style={{ background: 'var(--accent-purple)' }}>
                {loading ? (lang === 'zh' ? '处理中...' : 'Processing...') : (lang === 'zh' ? '启用 2FA' : 'Enable 2FA')}
              </button>
            )}
            {twoFASecret && (
              <div className="space-y-3">
                <div className="p-4 rounded-xl text-center" style={{ background: 'var(--bg-surface)' }}>
                  <p className="text-sm mb-2" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '密钥' : 'Secret Key'}</p>
                  <p className="text-lg font-mono" style={{ color: 'var(--text-primary)' }}>{twoFASecret}</p>
                </div>
                <input type="text" value={twoFACode} onChange={e => setTwoFACode(e.target.value)} placeholder={lang === 'zh' ? '输入验证码' : 'Enter code'} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none" style={{ background: 'var(--bg-input)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }} />
                <div className="flex gap-2">
                  <button onClick={handleVerify2FA} disabled={loading} className="flex-1 py-2.5 rounded-xl text-sm font-medium text-white transition-all disabled:opacity-50" style={{ background: 'var(--accent-purple)' }}>{lang === 'zh' ? '验证' : 'Verify'}</button>
                  <button onClick={() => { setTwoFASecret(''); setTwoFACode('') }} className="px-4 py-2.5 rounded-xl text-sm" style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)', border: '1px solid var(--border-color)' }}>{lang === 'zh' ? '取消' : 'Cancel'}</button>
                </div>
              </div>
            )}
            {twoFAEnabled && (
              <button onClick={handleDisable2FA} disabled={loading} className="w-full py-2.5 rounded-xl text-sm font-medium text-white transition-all disabled:opacity-50" style={{ background: 'rgba(244,63,94,0.8)' }}>
                {loading ? (lang === 'zh' ? '处理中...' : 'Processing...') : (lang === 'zh' ? '禁用 2FA' : 'Disable 2FA')}
              </button>
            )}
          </div>
        )}

        {tab === 'devices' && (
          <div className="space-y-4">
            <h3 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? '登录设备' : 'Login Devices'}</h3>
            {devices.length === 0 ? (
              <p className="text-sm" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '暂无登录设备' : 'No login devices'}</p>
            ) : (
              <div className="space-y-2">
                {devices.map((device, i) => (
                  <div key={i} className="flex items-center justify-between p-3 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
                    <div>
                      <p className="text-sm" style={{ color: 'var(--text-primary)' }}>{device.info || 'Unknown Device'}</p>
                      <p className="text-xs" style={{ color: 'var(--text-dim)' }}>{device.lastActive || ''}</p>
                    </div>
                    <button onClick={() => handleRemoveDevice(device.deviceId)} className="px-3 py-1 rounded-lg text-xs" style={{ background: 'rgba(244,63,94,0.1)', color: '#F43F5E' }}>{lang === 'zh' ? '移除' : 'Remove'}</button>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {tab === 'recharge' && (
          <div className="space-y-4">
            <h3 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? '在线充值' : 'Online Recharge'}</h3>
            <p className="text-sm" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '选择支付方式' : 'Select payment method'}</p>
            <div className="grid grid-cols-3 gap-2">
              {[
                { id: 'Alipay', label: '支付宝', color: '#1677FF' },
                { id: 'WeChat', label: '微信支付', color: '#07C160' },
                { id: 'CreditCard', label: lang === 'zh' ? '信用卡' : 'Credit Card', color: '#F59E0B' },
              ].map(pm => (
                <button key={pm.id} onClick={() => setPaymentMethod(pm.id)} className="py-2 rounded-xl text-xs font-medium transition-all" style={{
                  background: paymentMethod === pm.id ? pm.color : 'var(--bg-surface)',
                  color: paymentMethod === pm.id ? '#fff' : 'var(--text-dim)',
                  border: '1px solid ' + (paymentMethod === pm.id ? pm.color : 'var(--border-color)')
                }}>
                  {pm.label}
                </button>
              ))}
            </div>
            <div className="grid grid-cols-3 gap-3">
              {[10, 50, 100].map(amount => (
                <button key={amount} onClick={() => setRechargeAmount(amount.toString())} className="py-3 rounded-xl text-sm font-medium transition-all" style={{ background: rechargeAmount === amount.toString() ? 'var(--accent-purple)' : 'var(--bg-surface)', color: rechargeAmount === amount.toString() ? '#fff' : 'var(--text-dim)', border: '1px solid var(--border-color)' }}>
                  ${amount}
                </button>
              ))}
            </div>
            <input type="number" value={rechargeAmount} onChange={e => setRechargeAmount(e.target.value)} placeholder={lang === 'zh' ? '输入自定义金额' : 'Custom amount'} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none" style={{ background: 'var(--bg-input)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }} />
            <button onClick={handleRecharge} disabled={loading} className="w-full py-2.5 rounded-xl text-sm font-medium text-white transition-all disabled:opacity-50" style={{ background: 'var(--accent-purple)' }}>
              {loading ? (lang === 'zh' ? '处理中...' : 'Processing...') : (lang === 'zh' ? '立即充值' : 'Recharge Now')}
            </button>
            {rechargeRecords.length > 0 && (
              <div className="mt-6">
                <h4 className="text-sm font-medium mb-2" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? '充值记录' : 'Recharge Records'}</h4>
                <div className="space-y-2">
                  {rechargeRecords.map((record, i) => (
                    <div key={i} className="flex justify-between p-3 rounded-xl text-sm" style={{ background: 'var(--bg-surface)' }}>
                      <span style={{ color: 'var(--text-dim)' }}>{record.time || ''}</span>
                      <span style={{ color: 'var(--accent-emerald)' }}>¥{record.amount || 0}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        {tab === 'invoice' && (
          <div className="space-y-4">
            <h3 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? '发票申请' : 'Invoice Application'}</h3>
            <div className="space-y-3">
              <div>
                <label className="block text-sm mb-1.5" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '发票抬头' : 'Invoice Title'}</label>
                <input type="text" value={invoiceForm.title} onChange={e => setInvoiceForm({ ...invoiceForm, title: e.target.value })} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none" style={{ background: 'var(--bg-input)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }} />
              </div>
              <div>
                <label className="block text-sm mb-1.5" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '税号' : 'Tax Number'}</label>
                <input type="text" value={invoiceForm.taxNumber} onChange={e => setInvoiceForm({ ...invoiceForm, taxNumber: e.target.value })} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none" style={{ background: 'var(--bg-input)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }} />
              </div>
              <div>
                <label className="block text-sm mb-1.5" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '金额' : 'Amount'}</label>
                <input type="number" value={invoiceForm.amount} onChange={e => setInvoiceForm({ ...invoiceForm, amount: e.target.value })} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none" style={{ background: 'var(--bg-input)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }} />
              </div>
              <button onClick={handleApplyInvoice} disabled={loading} className="w-full py-2.5 rounded-xl text-sm font-medium text-white transition-all disabled:opacity-50" style={{ background: 'var(--accent-purple)' }}>
                {loading ? (lang === 'zh' ? '处理中...' : 'Processing...') : (lang === 'zh' ? '提交申请' : 'Submit Application')}
              </button>
            </div>
          </div>
        )}

        {tab === 'coupons' && (
          <div className="space-y-4">
            <h3 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? '优惠券' : 'Coupons'}</h3>
            <div className="flex gap-2">
              <input type="text" value={redeemCode} onChange={e => setRedeemCode(e.target.value)} placeholder={lang === 'zh' ? '输入兑换码' : 'Enter code'} className="flex-1 px-4 py-2.5 rounded-xl text-sm outline-none" style={{ background: 'var(--bg-input)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }} />
              <button onClick={handleRedeemCoupon} disabled={loading} className="px-4 py-2.5 rounded-xl text-sm font-medium text-white transition-all disabled:opacity-50" style={{ background: 'var(--accent-purple)' }}>
                {loading ? '...' : (lang === 'zh' ? '兑换' : 'Redeem')}
              </button>
            </div>
            {coupons.length > 0 && (
              <div className="space-y-2">
                {coupons.map((coupon, i) => (
                  <div key={i} className="flex items-center justify-between p-3 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
                    <div>
                      <p className="text-sm" style={{ color: 'var(--text-primary)' }}>{coupon.code || ''}</p>
                      <p className="text-xs" style={{ color: 'var(--text-dim)' }}>{coupon.type === 'discount' ? (lang === 'zh' ? '折扣' : 'Discount') : (lang === 'zh' ? '金额' : 'Amount')}</p>
                    </div>
                    <span className="text-sm font-medium" style={{ color: 'var(--accent-emerald)' }}>{coupon.value}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {tab === 'subscription' && (
          <div className="space-y-4">
            <h3 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? '自动续费订阅' : 'Subscription'}</h3>
            {subscription && (
              <div className="p-4 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
                <p className="text-sm" style={{ color: 'var(--text-dim)' }}>{lang === 'zh' ? '当前计划' : 'Current Plan'}</p>
                <p className="text-lg font-medium" style={{ color: 'var(--text-primary)' }}>{subscription.plan || 'None'}</p>
              </div>
            )}
            <div className="grid grid-cols-2 gap-3">
              {['monthly', 'yearly'].map(plan => (
                <button key={plan} onClick={() => handleSubscribe(plan)} disabled={loading} className="py-4 rounded-xl text-sm font-medium transition-all" style={{ background: 'var(--bg-surface)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }}>
                  {plan === 'monthly' ? (lang === 'zh' ? '月卡' : 'Monthly') : (lang === 'zh' ? '年卡' : 'Yearly')}
                </button>
              ))}
            </div>
            {subscription && (
              <button onClick={handleCancelSubscription} disabled={loading} className="w-full py-2.5 rounded-xl text-sm" style={{ background: 'rgba(244,63,94,0.1)', color: '#F43F5E' }}>
                {loading ? (lang === 'zh' ? '处理中...' : 'Processing...') : (lang === 'zh' ? '取消订阅' : 'Cancel Subscription')}
              </button>
            )}
          </div>
        )}

        {tab === 'oauth' && (
          <div className="space-y-4">
            <h3 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? '第三方登录绑定' : 'OAuth Bindings'}</h3>
            <div className="space-y-3">
              {[
                { provider: 'github', name: 'GitHub', icon: 'github' },
                { provider: 'google', name: 'Google', icon: 'google' },
                { provider: 'wechat', name: lang === 'zh' ? '微信' : 'WeChat', icon: 'wechat' },
              ].map(oauth => {
                const binding = oauthBindings.find(b => b.provider === oauth.provider)
                const isBound = binding?.bound
                return (
                  <div key={oauth.provider} className="flex items-center justify-between p-4 rounded-xl" style={{ background: 'var(--bg-surface)' }}>
                    <div className="flex items-center gap-3">
                      {oauth.icon === 'github' ? (
                        <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor" style={{ color: 'var(--text-primary)' }}><path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z"/></svg>
                      ) : oauth.icon === 'google' ? (
                        <svg width="24" height="24" viewBox="0 0 24 24"><path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 01-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z" fill="#4285F4"/><path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/><path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/><path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/></svg>
                      ) : (
                        <svg width="24" height="24" viewBox="0 0 24 24" fill="#07C160"><path d="M8.691 2.188C3.891 2.188 0 5.476 0 9.53c0 2.212 1.17 4.203 3.002 5.55a.59.59 0 01.213.665l-.39 1.48c-.019.07-.048.141-.048.213 0 .163.13.295.29.295a.326.326 0 00.167-.054l1.903-1.114a.864.864 0 01.717-.098 10.16 10.16 0 002.837.403c.276 0 .543-.027.811-.05-.857-2.578.157-4.972 1.932-6.446 1.703-1.415 3.882-1.98 5.853-1.838-.576-3.583-4.196-6.348-8.596-6.348zM5.785 5.991c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 01-1.162 1.178A1.17 1.17 0 014.623 7.17c0-.651.52-1.18 1.162-1.18zm5.813 0c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 01-1.162 1.178 1.17 1.17 0 01-1.162-1.178c0-.651.52-1.18 1.162-1.18z"/></svg>
                      )}
                      <div>
                        <p className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>{oauth.name}</p>
                        <p className="text-xs" style={{ color: 'var(--text-dim)' }}>{isBound ? (lang === 'zh' ? '已绑定' : 'Bound') : (lang === 'zh' ? '未绑定' : 'Not bound')}</p>
                      </div>
                    </div>
                    {isBound ? (
                      <button onClick={() => handleUnbindOAuth(oauth.provider)} className="px-3 py-1 rounded-lg text-xs" style={{ background: 'rgba(244,63,94,0.1)', color: '#F43F5E' }}>
                        {lang === 'zh' ? '解绑' : 'Unbind'}
                      </button>
                    ) : (
                      <button onClick={() => handleBindOAuth(oauth.provider)} className="px-3 py-1 rounded-lg text-xs" style={{ background: 'var(--accent-purple)', color: '#fff' }}>
                        {lang === 'zh' ? '绑定' : 'Bind'}
                      </button>
                    )}
                  </div>
                )
              })}
            </div>
          </div>
        )}

        {tab === 'delete' && (
          <div className="space-y-4">
            <h3 className="text-lg font-semibold" style={{ color: '#F43F5E' }}>{lang === 'zh' ? '账户注销' : 'Delete Account'}</h3>
            <div className="p-4 rounded-xl" style={{ background: 'rgba(244,63,94,0.1)', border: '1px solid rgba(244,63,94,0.2)' }}>
              <p className="text-sm" style={{ color: 'var(--text-dim)' }}>
                {lang === 'zh' ? '注销账户后，所有数据将被永久删除且无法恢复。请输入 DELETE 确认。' : 'After deleting your account, all data will be permanently deleted and cannot be recovered. Please enter DELETE to confirm.'}
              </p>
            </div>
            <input type="text" value={deleteConfirm} onChange={e => setDeleteConfirm(e.target.value)} placeholder="DELETE" className="w-full px-4 py-2.5 rounded-xl text-sm outline-none" style={{ background: 'var(--bg-input)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }} />
            <button onClick={handleDeleteAccount} disabled={loading || deleteConfirm !== 'DELETE'} className="w-full py-2.5 rounded-xl text-sm font-medium text-white transition-all disabled:opacity-50" style={{ background: '#F43F5E' }}>
              {loading ? (lang === 'zh' ? '处理中...' : 'Processing...') : (lang === 'zh' ? '永久删除账户' : 'Permanently Delete Account')}
            </button>
          </div>
        )}
      </motion.div>
    </div>
  )
}
