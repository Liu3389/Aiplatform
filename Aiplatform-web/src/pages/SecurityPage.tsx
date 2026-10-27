import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  userApi, authApi, couponApi, bindApi, twoFAApi, loginDeviceApi,
  accountApi, rechargeApi, invoiceApi, request
} from '../services/api'
import { useAppStore } from '../stores/appStore'
import { useToastStore } from '../stores/toastStore'

type ProviderKey = 'qq' | 'google'

interface OAuthItem {
  provider: ProviderKey
  label: string
  connected: boolean
  account?: string
}

interface LoginDevice {
  deviceId: string
  info: string
  lastActive: string
  isCurrent: boolean
}

const OAUTH_PROVIDERS: { key: ProviderKey; label: string }[] = [
  { key: 'qq', label: 'QQ' },
  { key: 'google', label: 'Google' },
]

function SecurityPage({ embedded = false }: { embedded?: boolean }) {
  const navigate = useNavigate()
  const { token, user, fetchUserInfo, logout } = useAppStore()
  const { showToast } = useToastStore()

  const showSuccess = (msg: string) => showToast({ title: '成功', message: msg, type: 'success' })
  const showError = (msg: string) => showToast({ title: '错误', message: msg, type: 'error' })

  // ---- Tab ----
  const [tab, setTab] = useState<
    'password' | 'recharge' | 'coupons' | 'bind' | '2fa' | 'devices' |
    'invoice' | 'deactivate' | 'recharge-history'
  >('password')

  // ---- 修改密码 ----
  const [pwd, setPwd] = useState({ oldPassword: '', newPassword: '', confirmPassword: '' })
  const [pwdMsg, setPwdMsg] = useState('')

  // ---- 优惠券 ----
  const [coupons, setCoupons] = useState<any[]>([])

  // ---- 第三方绑定 ----
  const [oauthItems, setOauthItems] = useState<OAuthItem[]>([])
  const [oauthState, setOauthState] = useState<'idle' | 'loading' | 'ready' | 'error'>('idle')
  const [oauthError, setOauthError] = useState('')

  // ---- 两步验证 ----
  const [twoFAEnabled, setTwoFAEnabled] = useState(false)
  const [twoFAState, setTwoFAState] = useState<'idle' | 'loading' | 'ready' | 'error'>('idle')
  const [twoFAError, setTwoFAError] = useState('')
  const [twoFALoading, setTwoFALoading] = useState(false)
  const [show2FASetup, setShow2FASetup] = useState(false)
  const [twoFACode, setTwoFACode] = useState('')
  const [twoFASecret, setTwoFASecret] = useState('')
  const [twoFABackupCodes, setTwoFABackupCodes] = useState<string[]>([])
  const [show2FADisable, setShow2FADisable] = useState(false)
  const [twoFADisableCode, setTwoFADisableCode] = useState('')

  // ---- 登录设备 ----
  const [devices, setDevices] = useState<LoginDevice[]>([])
  const [devicesState, setDevicesState] = useState<'idle' | 'loading' | 'ready' | 'error'>('idle')
  const [devicesError, setDevicesError] = useState('')

  // ---- 发票 ----
  const [invoiceForm, setInvoiceForm] = useState({ title: '', taxNumber: '', amount: '' })

  // ---- 充值记录 ----
  const [rechargeRecords, setRechargeRecords] = useState<any[]>([])

  // ---- 绑定 ----
  const [bindEmailForm, setBindEmailForm] = useState({ email: '', code: '' })
  const [bindPhoneForm, setBindPhoneForm] = useState({ phone: '', code: '' })
  const [bindCodeLoading, setBindCodeLoading] = useState<'email' | 'phone' | null>(null)
  const [bindCountdown, setBindCountdown] = useState({ email: 0, phone: 0 })

  useEffect(() => {
    if (bindCountdown.email === 0 && bindCountdown.phone === 0) return
    const timer = window.setTimeout(() => {
      setBindCountdown(current => ({
        email: Math.max(0, current.email - 1),
        phone: Math.max(0, current.phone - 1),
      }))
    }, 1000)
    return () => window.clearTimeout(timer)
  }, [bindCountdown])

  // ---- 停用账号 ----
  const [deactivatePassword, setDeactivatePassword] = useState('')
  const [deactivateLoading, setDeactivateLoading] = useState(false)

  useEffect(() => {
    if (!token) return
    if (token && (tab === 'coupons' || tab === 'bind' || tab === '2fa' ||
      tab === 'devices' || tab === 'recharge-history')) {
      loadByTab(tab)
    }
  }, [tab, token])

  const loadByTab = async (t: string) => {
    if (t === 'coupons') await loadCoupons()
    if (t === 'bind') await loadOAuthBindings()
    if (t === '2fa') await load2FAStatus()
    if (t === 'devices') await loadDevices()
    if (t === 'recharge-history') await loadRechargeRecords()
  }

  // ==================== 修改密码 ====================
  const handleChangePassword = async () => {
    if (pwd.newPassword !== pwd.confirmPassword) {
      setPwdMsg('两次密码输入不一致')
      return
    }
    if (pwd.newPassword.length < 6) {
      setPwdMsg('新密码长度不能少于 6 位')
      return
    }
    try {
      await userApi.changePassword(pwd.oldPassword, pwd.newPassword)
      showSuccess('密码修改成功')
      setPwd({ oldPassword: '', newPassword: '', confirmPassword: '' })
      setPwdMsg('')
    } catch (e: any) {
      setPwdMsg(e.message || '修改失败')
    }
  }

  // ==================== 优惠券 ====================
  const loadCoupons = async () => {
    try {
      const list = await couponApi.list()
      setCoupons(Array.isArray(list) ? list : [])
    } catch {
      setCoupons([])
    }
  }

  // ==================== 第三方绑定 ====================
  const loadOAuthBindings = async () => {
    setOauthState('loading')
    setOauthError('')
    try {
      const bindings = await authApi.oauthBindings()
      const byProvider = new Map(bindings.map(binding => [binding.provider, binding]))
      setOauthItems(OAUTH_PROVIDERS.map(provider => {
        const binding = byProvider.get(provider.key)
        return {
          provider: provider.key,
          label: provider.label,
          connected: Boolean(binding),
          account: binding?.email || binding?.displayName,
        }
      }))
      setOauthState('ready')
    } catch (e: any) {
      setOauthError(e?.message || '第三方绑定状态暂时无法获取')
      setOauthState('error')
    }
  }

  const handleBindOAuth = async (provider: string) => {
    try {
      const result = await authApi.getOAuthUrl(provider, 'bind')
      localStorage.setItem('oauth_provider', provider)
      localStorage.setItem('oauth_return_to', window.location.pathname + window.location.search)
      window.location.assign(result.url)
    } catch (e: any) {
      showError(e.message || '绑定失败')
    }
  }

  const handleUnbindOAuth = async (provider: string) => {
    try {
      await authApi.oauthUnbind(provider)
      showSuccess(`${OAUTH_PROVIDERS.find(p => p.key === provider)?.label || provider} 解绑成功`)
      await loadOAuthBindings()
    } catch (e: any) {
      showError(e.message || '解绑失败')
    }
  }

  // ==================== 两步验证 ====================
  const load2FAStatus = async () => {
    setTwoFAState('loading')
    setTwoFAError('')
    try {
      const data = await twoFAApi.getStatus()
      setTwoFAEnabled(data.enabled === true)
      setTwoFAState('ready')
    } catch (e: any) {
      setTwoFAError(e?.message || '两步验证状态暂时无法获取')
      setTwoFAState('error')
    }
  }

  const handleEnable2FA = async () => {
    try {
      setTwoFALoading(true)
      setTwoFABackupCodes([])
      const data = await twoFAApi.enable()
      setTwoFASecret(data.secret || '')
      setShow2FASetup(true)
    } catch (e: any) {
      showError(e.message || '启用失败')
    } finally {
      setTwoFALoading(false)
    }
  }

  const handleVerify2FA = async () => {
    try {
      setTwoFALoading(true)
      const result = await twoFAApi.verify(twoFACode)
      setTwoFAEnabled(true)
      setTwoFABackupCodes(result.backupCodes || [])
      setShow2FASetup(false)
      setTwoFACode('')
      showSuccess('两步验证已启用')
    } catch (e: any) {
      showError(e.message || '验证码错误')
    } finally {
      setTwoFALoading(false)
    }
  }

  const handleDisable2FA = async () => {
    try {
      setTwoFALoading(true)
      await twoFAApi.disable(twoFADisableCode)
      setTwoFAEnabled(false)
      setShow2FADisable(false)
      setTwoFADisableCode('')
      setTwoFABackupCodes([])
      showSuccess('两步验证已禁用')
    } catch (e: any) {
      showError(e.message || '验证码错误')
    } finally {
      setTwoFALoading(false)
    }
  }

  // ==================== 登录设备 ====================
  const loadDevices = async () => {
    setDevicesState('loading')
    setDevicesError('')
    try {
      const list = await loginDeviceApi.list()
      setDevices(Array.isArray(list) ? list : [])
      setDevicesState('ready')
    } catch (e: any) {
      setDevicesError(e?.message || '登录设备列表暂时无法获取')
      setDevicesState('error')
    }
  }

  const handleRemoveDevice = async (deviceId: string) => {
    try {
      await loginDeviceApi.remove(deviceId)
      showSuccess('设备已移除')
      await loadDevices()
    } catch (e: any) {
      showError(e.message || '操作失败')
    }
  }

  const handleRemoveAllDevices = async () => {
    try {
      await loginDeviceApi.removeAll(true)
      showSuccess('其他设备均已下线')
      await loadDevices()
    } catch (e: any) {
      showError(e.message || '操作失败')
    }
  }

  // ==================== 发票 ====================
  const handleApplyInvoice = async () => {
    if (!invoiceForm.title) {
      showError('请输入发票抬头')
      return
    }
    try {
      await invoiceApi.apply({
        title: invoiceForm.title,
        taxNumber: invoiceForm.taxNumber || undefined,
        amount: invoiceForm.amount ? Number(invoiceForm.amount) : undefined,
      })
      showSuccess('发票申请已提交')
    } catch (e: any) {
      showError(e.message || '申请失败')
    }
  }

  // ==================== 充值记录 ====================
  const loadRechargeRecords = async () => {
    try {
      const records = await rechargeApi.getRecords()
      setRechargeRecords(records?.records || [])
    } catch {
      setRechargeRecords([])
    }
  }

  // ==================== 绑定 ====================
  const handleSendBindCode = async (type: 'email' | 'phone') => {
    const target = type === 'email' ? bindEmailForm.email : bindPhoneForm.phone
    if (!target) {
      showError(type === 'email' ? '请输入邮箱' : '请输入手机号')
      return
    }
    try {
      setBindCodeLoading(type)
      await bindApi.sendBindCode(type, target)
      showSuccess('验证码已发送')
      setBindCountdown(current => ({ ...current, [type]: 60 }))
    } catch (e: any) {
      showError(e.message || '发送失败')
    } finally {
      setBindCodeLoading(null)
    }
  }

  const handleBindEmail = async () => {
    if (!bindEmailForm.email || !bindEmailForm.code) {
      showError('请填写邮箱和验证码')
      return
    }
    try {
      await bindApi.bindEmail(bindEmailForm.email, bindEmailForm.code)
      showSuccess('邮箱绑定成功')
      setBindEmailForm({ email: '', code: '' })
      await fetchUserInfo()
    } catch (e: any) {
      showError(e.message || '绑定失败')
    }
  }

  const handleBindPhone = async () => {
    if (!bindPhoneForm.phone || !bindPhoneForm.code) {
      showError('请填写手机号和验证码')
      return
    }
    try {
      await bindApi.bindPhone(bindPhoneForm.phone, bindPhoneForm.code)
      showSuccess('手机号绑定成功')
      setBindPhoneForm({ phone: '', code: '' })
      await fetchUserInfo()
    } catch (e: any) {
      showError(e.message || '绑定失败')
    }
  }

  // ==================== 停用账号 ====================
  const handleDeactivateAccount = async () => {
    if (!deactivatePassword) {
      showError('请输入密码确认')
      return
    }
    setDeactivateLoading(true)
    try {
      await accountApi.deactivateAccount(deactivatePassword)
      showSuccess('账号已停用，账号资料和历史数据已保留')
      logout()
      navigate('/login')
    } catch (e: any) {
      showError(e.message || '停用失败')
    } finally {
      setDeactivateLoading(false)
    }
  }

  // ==================== 复制 2FA 密钥 ====================
  const copySecret = () => {
    navigator.clipboard.writeText(twoFASecret).then(() => {
      showSuccess('密钥已复制')
    })
  }

  const copyBackupCodes = () => {
    navigator.clipboard.writeText(twoFABackupCodes.join('\n')).then(() => {
      showSuccess('备用码已复制，请保存到安全位置')
    })
  }

  if (!token) return null

  // ==================== Render ====================
  const tabs = [
    { key: 'password', label: '修改密码' },
    { key: 'recharge', label: '余额充值' },
    { key: 'coupons', label: '优惠券' },
    { key: 'bind', label: '三方绑定' },
    { key: '2fa', label: '两步验证' },
    { key: 'invoice', label: '发票' },
    { key: 'devices', label: '登录设备' },
    { key: 'recharge-history', label: '充值记录' },
    { key: 'deactivate', label: '停用账号' },
  ] as const

  return (
    <div className={embedded ? 'security-page min-w-0' : 'page-scroll security-page h-full max-w-5xl mx-auto overflow-y-auto px-3 py-5 pb-24 sm:px-6 sm:py-8 sm:pb-8'}>
      <div className="mb-6">
        <h2 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>账户安全</h2>
        <p className="mt-1 text-sm" style={{ color: 'var(--text-dim)' }}>管理登录方式、验证联系方式和当前会话。</p>
      </div>

      {/* Tab 导航 */}
      <nav aria-label="账户设置分类" className="flex flex-wrap gap-2 mb-8 pb-3" style={{ borderBottom: '1px solid var(--border-color)' }}>
        {tabs.map(t => (
          <button
            key={t.key}
            type="button"
            onClick={() => setTab(t.key as typeof tab)}
            aria-pressed={tab === t.key}
            className={`px-4 py-2 rounded-full text-sm font-medium transition-colors security-tab ${
              tab === t.key
                ? 'is-active text-white shadow'
                : 'text-gray-600 hover:bg-gray-100'
            }`}
            style={tab === t.key
              ? { background: 'var(--accent-purple)' }
              : { color: 'var(--text-dim)', border: '1px solid var(--border-color)' }}
          >
            {t.label}
          </button>
        ))}
      </nav>

      {/* ---- 修改密码 ---- */}
      {tab === 'password' && (
        <div className="max-w-md space-y-4">
          <h3 className="text-lg font-semibold mb-2">修改密码</h3>
          <input
            className="w-full border rounded-lg px-4 py-2"
            type="password"
            placeholder="旧密码"
            aria-label="旧密码"
            autoComplete="current-password"
            value={pwd.oldPassword}
            onChange={e => setPwd({ ...pwd, oldPassword: e.target.value })}
          />
          <input
            className="w-full border rounded-lg px-4 py-2"
            type="password"
            placeholder="新密码"
            aria-label="新密码"
            autoComplete="new-password"
            value={pwd.newPassword}
            onChange={e => setPwd({ ...pwd, newPassword: e.target.value })}
          />
          <input
            className="w-full border rounded-lg px-4 py-2"
            type="password"
            placeholder="确认新密码"
            aria-label="确认新密码"
            autoComplete="new-password"
            value={pwd.confirmPassword}
            onChange={e => setPwd({ ...pwd, confirmPassword: e.target.value })}
          />
          {pwdMsg && <p role={pwdMsg.includes('成功') ? 'status' : 'alert'} className={`text-sm ${pwdMsg.includes('成功') ? 'text-green-600' : 'text-red-500'}`}>{pwdMsg}</p>}
          <button type="button" onClick={handleChangePassword} className="w-full bg-indigo-600 text-white py-2 rounded-lg hover:bg-indigo-700 transition-colors">
            确认修改
          </button>
        </div>
      )}

      {/* ---- 余额充值 ---- */}
      {tab === 'recharge' && (
        <div className="max-w-md rounded-lg border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900">
          <h3 className="mb-2 text-lg font-semibold">余额充值暂不可用</h3>
          <p>支付渠道与验签回调尚未接入。此期间不会扣款、消费优惠券或增加账户余额。</p>
        </div>
      )}

      {/* ---- 优惠券 ---- */}
      {tab === 'coupons' && (
        <div className="space-y-4">
          <h3 className="text-lg font-semibold mb-2">我的优惠券</h3>
          {coupons.length === 0 ? (
            <p className="text-gray-500">暂无优惠券</p>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {coupons.map((c: any, i: number) => (
                <div key={c.id || i} className="border rounded-lg p-4 shadow-sm">
                  <div className="flex justify-between items-start">
                    <div>
                      <p className="font-semibold">{c.title || c.name || '优惠券'}</p>
                      <p className="text-sm text-gray-500">
                        {c.discount ? `折扣 ${c.discount} 折` : c.description || ''}
                      </p>
                    </div>
                    <span className={`text-xs px-2 py-0.5 rounded-full ${
                      c.status === 1 || c.status === 'UNUSED' ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'
                    }`}>
                      {c.status === 1 || c.status === 'UNUSED' ? '可用' : '已使用'}
                    </span>
                  </div>
                  {c.expireTime && (
                    <p className="text-xs text-gray-400 mt-2">有效期至 {new Date(c.expireTime).toLocaleDateString()}</p>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* ---- 三方绑定 ---- */}
      {tab === 'bind' && (
        <div className="space-y-6">
          <h3 className="text-lg font-semibold mb-2">第三方绑定</h3>
          {oauthState === 'loading' && <p className="text-sm text-gray-500" role="status">正在读取第三方绑定状态…</p>}
          {oauthState === 'error' && (
            <div className="flex flex-wrap items-center gap-3 rounded-xl border p-4 text-sm" role="alert">
              <span>{oauthError}</span>
              <button type="button" onClick={() => void loadOAuthBindings()} className="font-medium text-indigo-600">重试</button>
            </div>
          )}
          {oauthState === 'ready' && (
            <div className="grid gap-3">
              {oauthItems.map(item => (
                <div key={item.provider} className="flex items-center justify-between border rounded-xl p-4" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                  <div className="min-w-0">
                    <span className="font-medium">{item.label}</span>
                    {item.connected && item.account && (
                      <span className="ml-2 break-all text-xs text-gray-500">({item.account})</span>
                    )}
                  </div>
                  <button
                    type="button"
                    onClick={() => item.connected ? handleUnbindOAuth(item.provider) : handleBindOAuth(item.provider)}
                    className={`ml-3 shrink-0 px-4 py-1.5 rounded-full text-xs font-medium transition-colors ${
                      item.connected
                        ? 'border border-red-300 text-red-500 hover:bg-red-50'
                        : 'bg-indigo-600 text-white hover:bg-indigo-700'
                    }`}
                  >
                    {item.connected ? '解绑' : '绑定'}
                  </button>
                </div>
              ))}
            </div>
          )}

          {/* 邮箱手机绑定 */}
          <div className="mt-8 space-y-6">
            <h3 className="text-lg font-semibold">绑定信息</h3>

            {/* 邮箱绑定 */}
            <div className="border rounded-lg p-4 space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h4 className="font-medium text-sm">邮箱</h4>
                {user?.emailVerified
                  ? <span className="rounded-full bg-emerald-500/10 px-2.5 py-1 text-xs font-medium text-emerald-600">已验证 · {user.email}</span>
                  : <span className="text-xs text-amber-600">{user?.email ? `历史资料 ${user.email} 尚未验证` : '尚未绑定'}</span>}
              </div>
              {!user?.emailVerified && <>
                <div className="flex flex-col gap-2 sm:flex-row">
                  <input
                    className="min-w-0 flex-1 border rounded-lg px-3 py-2 text-sm"
                    placeholder="输入需要验证的邮箱"
                    type="email"
                    autoComplete="email"
                    value={bindEmailForm.email}
                    onChange={e => setBindEmailForm({ ...bindEmailForm, email: e.target.value })}
                  />
                  <button
                    type="button"
                    onClick={() => void handleSendBindCode('email')}
                    disabled={bindCodeLoading === 'email' || bindCountdown.email > 0}
                    className="px-3 py-2 bg-gray-100 rounded-lg text-sm whitespace-nowrap disabled:opacity-50"
                  >
                    {bindCodeLoading === 'email' ? '发送中…' : bindCountdown.email > 0 ? `${bindCountdown.email}s 后重试` : '获取验证码'}
                  </button>
                </div>
                <div className="flex flex-col gap-2 sm:flex-row">
                  <input
                    className="min-w-0 flex-1 border rounded-lg px-3 py-2 text-sm"
                    placeholder="输入邮箱验证码"
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    value={bindEmailForm.code}
                    onChange={e => setBindEmailForm({ ...bindEmailForm, code: e.target.value })}
                  />
                  <button
                    type="button"
                    onClick={() => void handleBindEmail()}
                    disabled={!bindEmailForm.email || !bindEmailForm.code}
                    className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm whitespace-nowrap hover:bg-indigo-700 disabled:opacity-50"
                  >
                    验证并绑定邮箱
                  </button>
                </div>
              </>}
            </div>

            {/* 手机绑定 */}
            <div className="border rounded-lg p-4 space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h4 className="font-medium text-sm">手机号</h4>
                {user?.phoneVerified
                  ? <span className="rounded-full bg-emerald-500/10 px-2.5 py-1 text-xs font-medium text-emerald-600">已验证 · {user.phone}</span>
                  : <span className="text-xs text-amber-600">{user?.phone ? `历史资料 ${user.phone} 尚未验证` : '尚未绑定'}</span>}
              </div>
              {!user?.phoneVerified && <>
                <div className="flex flex-col gap-2 sm:flex-row">
                  <input
                    className="min-w-0 flex-1 border rounded-lg px-3 py-2 text-sm"
                    placeholder="输入手机号"
                    type="tel"
                    autoComplete="tel"
                    value={bindPhoneForm.phone}
                    onChange={e => setBindPhoneForm({ ...bindPhoneForm, phone: e.target.value })}
                  />
                  <button
                    type="button"
                    onClick={() => void handleSendBindCode('phone')}
                    disabled={bindCodeLoading === 'phone' || bindCountdown.phone > 0}
                    className="px-3 py-2 bg-gray-100 rounded-lg text-sm whitespace-nowrap disabled:opacity-50"
                  >
                    {bindCodeLoading === 'phone' ? '发送中…' : bindCountdown.phone > 0 ? `${bindCountdown.phone}s 后重试` : '获取验证码'}
                  </button>
                </div>
                <div className="flex flex-col gap-2 sm:flex-row">
                  <input
                    className="min-w-0 flex-1 border rounded-lg px-3 py-2 text-sm"
                    placeholder="输入短信验证码"
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    value={bindPhoneForm.code}
                    onChange={e => setBindPhoneForm({ ...bindPhoneForm, code: e.target.value })}
                  />
                  <button
                    type="button"
                    onClick={() => void handleBindPhone()}
                    disabled={!bindPhoneForm.phone || !bindPhoneForm.code}
                    className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm whitespace-nowrap hover:bg-indigo-700 disabled:opacity-50"
                  >
                    验证并绑定手机
                  </button>
                </div>
              </>}
            </div>
          </div>
        </div>
      )}

      {/* ---- 两步验证 ---- */}
      {tab === '2fa' && (
        <div className="space-y-4 max-w-md">
          <h3 className="text-lg font-semibold mb-2">两步验证</h3>
          {twoFAState === 'loading' && <p className="text-sm text-gray-500" role="status">正在读取两步验证状态…</p>}
          {twoFAState === 'error' && (
            <div className="flex flex-wrap items-center gap-3 rounded-xl border p-4 text-sm" role="alert">
              <span>{twoFAError}</span>
              <button type="button" onClick={() => void load2FAStatus()} className="font-medium text-indigo-600">重试</button>
            </div>
          )}
          {twoFAState === 'ready' && <>
          <p className="text-sm text-gray-600">当前状态: {twoFAEnabled ? <span className="text-green-600 font-medium">已启用</span> : <span className="text-gray-500">未启用</span>}</p>

          {show2FASetup && !twoFAEnabled && (
            <div className="border rounded-lg p-4 space-y-3 bg-gray-50">
              <p className="text-sm font-medium">设置两步验证</p>
              <p className="text-xs text-gray-500">在 Google Authenticator 或 Authy 中选择“手动输入密钥”，填入下方密钥；再输入应用生成的 6 位验证码确认。</p>
              <div className="flex items-center gap-2">
                <code className="bg-white border rounded px-3 py-1 text-sm break-all flex-1">{twoFASecret}</code>
                <button type="button" onClick={copySecret} className="px-3 py-1 bg-white border rounded text-xs hover:bg-gray-100">
                  复制
                </button>
              </div>
              <input
                className="w-full border rounded-lg px-3 py-2 text-sm"
                placeholder="输入动态验证码确认"
                value={twoFACode}
                onChange={e => setTwoFACode(e.target.value)}
              />
              <div className="flex gap-2">
                <button
                  onClick={handleVerify2FA}
                  disabled={twoFALoading || !twoFACode}
                  className="flex-1 bg-indigo-600 text-white py-2 rounded-lg text-sm hover:bg-indigo-700 disabled:opacity-50"
                >
                  {twoFALoading ? '验证中...' : '确认启用'}
                </button>
                <button
                  onClick={() => { setShow2FASetup(false); setTwoFACode('') }}
                  className="px-4 py-2 border rounded-lg text-sm"
                >
                  取消
                </button>
              </div>
            </div>
          )}

          {twoFAEnabled && !show2FADisable && (
            <div className="space-y-3">
              {twoFABackupCodes.length > 0 && (
                <div className="border border-amber-300 rounded-lg p-4 space-y-3 bg-amber-50">
                  <p className="text-sm font-medium text-amber-900">请立即保存一次性备用码</p>
                  <p className="text-xs text-amber-800">每个备用码只能用于一次登录。本页关闭后不会再次显示。</p>
                  <div className="grid grid-cols-2 gap-2">
                    {twoFABackupCodes.map(code => <code key={code} className="bg-white border rounded px-2 py-1 text-xs text-center">{code}</code>)}
                  </div>
                  <button onClick={copyBackupCodes} className="px-3 py-1.5 bg-white border rounded text-xs hover:bg-amber-100">
                    复制全部备用码
                  </button>
                </div>
              )}
              <button
                onClick={() => setShow2FADisable(true)}
                className="px-4 py-2 border border-red-300 text-red-600 rounded-lg text-sm hover:bg-red-50"
              >
                禁用两步验证
              </button>
            </div>
          )}

          {!twoFAEnabled && !show2FASetup && (
            <button
              onClick={handleEnable2FA}
              disabled={twoFALoading}
              className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm hover:bg-indigo-700 disabled:opacity-50"
            >
              {twoFALoading ? '处理中...' : '启用两步验证'}
            </button>
          )}

          {show2FADisable && (
            <div className="border rounded-lg p-4 space-y-3">
              <p className="text-sm text-red-600">确认禁用两步验证</p>
              <input
                className="w-full border rounded-lg px-3 py-2 text-sm"
                placeholder="输入动态验证码或未使用的备用码"
                value={twoFADisableCode}
                onChange={e => setTwoFADisableCode(e.target.value)}
              />
              <div className="flex gap-2">
                <button
                  onClick={handleDisable2FA}
                  disabled={twoFALoading || !twoFADisableCode}
                  className="flex-1 bg-red-600 text-white py-2 rounded-lg text-sm hover:bg-red-700 disabled:opacity-50"
                >
                  {twoFALoading ? '验证中...' : '确认禁用'}
                </button>
                <button
                  onClick={() => { setShow2FADisable(false); setTwoFADisableCode('') }}
                  className="px-4 py-2 border rounded-lg text-sm"
                >
                  取消
                </button>
              </div>
            </div>
          )}
          </>}
        </div>
      )}

      {/* ---- 登录设备 ---- */}
      {tab === 'devices' && (
        <div className="space-y-4">
          <h3 className="text-lg font-semibold mb-2">登录设备</h3>
          {devicesState === 'loading' && <p className="text-sm text-gray-500" role="status">正在读取当前会话…</p>}
          {devicesState === 'error' && (
            <div className="flex flex-wrap items-center gap-3 rounded-xl border p-4 text-sm" role="alert">
              <span>{devicesError}</span>
              <button type="button" onClick={() => void loadDevices()} className="font-medium text-indigo-600">重试</button>
            </div>
          )}
          {devicesState === 'ready' && devices.some(device => !device.isCurrent) && (
            <button
              type="button"
              onClick={handleRemoveAllDevices}
              className="px-4 py-2 text-sm border border-red-300 text-red-600 rounded-lg hover:bg-red-50"
            >
              下线所有其他设备
            </button>
          )}
          {devicesState === 'ready' && (devices.length === 0 ? (
            <p className="text-gray-500 text-sm">当前暂无有效登录会话，请重新登录后刷新。</p>
          ) : (
            <div className="space-y-3">
              <p className="text-sm text-gray-500">平台当前仅保留一个有效登录会话；在新设备登录后，之前的会话会自动失效。</p>
              {devices.map(device => (
                <div key={device.deviceId} className="flex items-center justify-between gap-3 border rounded-xl p-4" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                  <div>
                    <p className="text-sm font-medium">
                      {device.isCurrent ? '当前登录会话' : device.info || '登录设备'}
                      {device.isCurrent && <span className="ml-2 text-xs text-green-600 bg-green-50 rounded-full px-2 py-0.5">正在使用</span>}
                    </p>
                    <p className="text-xs text-gray-400 mt-0.5">
                      最近活跃：{device.lastActive ? new Date(device.lastActive).toLocaleString() : '未知'}
                    </p>
                  </div>
                  {!device.isCurrent && (
                    <button
                      type="button"
                      onClick={() => handleRemoveDevice(device.deviceId)}
                      className="px-3 py-1 text-xs border border-gray-300 rounded-full hover:bg-red-50 hover:border-red-300 hover:text-red-600"
                    >
                      移除
                    </button>
                  )}
                </div>
              ))}
            </div>
          ))}
        </div>
      )}

      {/* ---- 发票 ---- */}
      {tab === 'invoice' && (
        <div className="max-w-md space-y-4">
          <h3 className="text-lg font-semibold mb-2">发票申请</h3>
          <input
            className="w-full border rounded-lg px-4 py-2"
            placeholder="发票抬头"
            value={invoiceForm.title}
            onChange={e => setInvoiceForm({ ...invoiceForm, title: e.target.value })}
          />
          <input
            className="w-full border rounded-lg px-4 py-2"
            placeholder="税号 (选填)"
            value={invoiceForm.taxNumber}
            onChange={e => setInvoiceForm({ ...invoiceForm, taxNumber: e.target.value })}
          />
          <input
            className="w-full border rounded-lg px-4 py-2"
            placeholder="金额 (选填)"
            type="number"
            value={invoiceForm.amount}
            onChange={e => setInvoiceForm({ ...invoiceForm, amount: e.target.value })}
          />
          <button
            onClick={handleApplyInvoice}
            className="w-full bg-indigo-600 text-white py-2 rounded-lg hover:bg-indigo-700 transition-colors"
          >
            提交申请
          </button>
        </div>
      )}

      {/* ---- 充值记录 ---- */}
      {tab === 'recharge-history' && (
        <div className="space-y-4">
          <h3 className="text-lg font-semibold mb-2">充值记录</h3>
          {rechargeRecords.length === 0 ? (
            <p className="text-gray-500 text-sm">暂无充值记录</p>
          ) : (
            <div className="hide-scrollbar overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b bg-gray-50">
                    <th className="text-left px-3 py-2">金额(元)</th>
                    <th className="text-left px-3 py-2">支付方式</th>
                    <th className="text-left px-3 py-2">状态</th>
                    <th className="text-left px-3 py-2">时间</th>
                  </tr>
                </thead>
                <tbody>
                  {rechargeRecords.map((r: any, i: number) => (
                    <tr key={r.id || i} className="border-b">
                      <td className="px-3 py-2">{r.amount}</td>
                      <td className="px-3 py-2">{r.paymentMethod || r.payment_method || '-'}</td>
                      <td className="px-3 py-2">
                        <span className={`text-xs px-2 py-0.5 rounded-full ${
                          r.status === 'SUCCESS' || r.status === 1
                            ? 'bg-green-100 text-green-700'
                            : r.status === 'PENDING' || r.status === 0
                            ? 'bg-yellow-100 text-yellow-700'
                            : 'bg-red-100 text-red-700'
                        }`}>
                          {r.status === 'SUCCESS' || r.status === 1 ? '成功' :
                           r.status === 'PENDING' || r.status === 0 ? '待支付' : '失败'}
                        </span>
                      </td>
                      <td className="px-3 py-2 text-gray-400 text-xs">
                        {r.createTime || r.createdAt ? new Date(r.createTime || r.createdAt).toLocaleString() : '-'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* ---- 停用账号 ---- */}
      {tab === 'deactivate' && (
        <section className="max-w-xl rounded-2xl border border-red-200 bg-red-50/60 p-5 space-y-4 dark:border-red-900/60 dark:bg-red-950/20 sm:p-6">
          <div>
            <h3 className="text-lg font-semibold text-red-700 dark:text-red-300">停用账号</h3>
            <p className="mt-1 text-sm text-gray-600 dark:text-gray-300">停用后你将立即退出，且无法继续登录或使用 API。</p>
          </div>
          <div className="rounded-xl bg-white/70 p-4 text-sm leading-6 text-gray-700 dark:bg-black/10 dark:text-gray-300">
            此操作会撤销所有登录会话。账号资料、手机号与第三方账号绑定、历史对话、文件和账务记录会保留；停用不等同于删除账号，数据删除范围仍待确认。
          </div>
          <label className="block space-y-1.5 text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
            当前密码
            <input
              className="w-full rounded-lg border px-4 py-2.5 font-normal"
              type="password"
              autoComplete="current-password"
              placeholder="输入当前密码"
              value={deactivatePassword}
              onChange={e => setDeactivatePassword(e.target.value)}
              disabled={deactivateLoading}
            />
          </label>
          <button
            onClick={handleDeactivateAccount}
            disabled={deactivateLoading || !deactivatePassword}
            className="w-full rounded-lg bg-red-600 py-2.5 font-medium text-white transition-colors hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {deactivateLoading ? '正在停用…' : '确认停用并退出'}
          </button>
        </section>
      )}
    </div>
  )
}

export default SecurityPage
