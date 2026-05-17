import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  userApi, authApi, couponApi, bindApi, twoFAApi, loginDeviceApi,
  accountApi, rechargeApi, invoiceApi, request
} from '../services/api'
import { useAppStore } from '../stores/appStore'
import { useToastStore } from '../stores/toastStore'

type ProviderKey = 'github' | 'google' | 'wechat'

interface OAuthItem {
  provider: ProviderKey
  label: string
  connected: boolean
  account?: string
}

interface LoginDevice {
  id: string
  ip: string
  region?: string
  browser: string
  os: string
  loginTime: string
  isCurrent: boolean
}

const OAUTH_PROVIDERS: { key: ProviderKey; label: string }[] = [
  { key: 'github', label: 'GitHub' },
  { key: 'google', label: 'Google' },
  { key: 'wechat', label: '微信' },
]

function SecurityPage() {
  const navigate = useNavigate()
  const { token } = useAppStore()
  const { showToast } = useToastStore()

  const showSuccess = (msg: string) => showToast({ title: '成功', message: msg, type: 'success' })
  const showError = (msg: string) => showToast({ title: '错误', message: msg, type: 'error' })

  // ---- Tab ----
  const [tab, setTab] = useState<
    'password' | 'recharge' | 'coupons' | 'bind' | '2fa' | 'devices' |
    'invoice' | 'delete' | 'recharge-history'
  >('password')

  // ---- 修改密码 ----
  const [pwd, setPwd] = useState({ oldPassword: '', newPassword: '', confirmPassword: '' })
  const [pwdMsg, setPwdMsg] = useState('')

  // ---- 点数充值 ----
  const [rechargeAmount, setRechargeAmount] = useState<number>(10)

  // ---- 优惠券 ----
  const [coupons, setCoupons] = useState<any[]>([])

  // ---- 第三方绑定 ----
  const [oauthItems, setOauthItems] = useState<OAuthItem[]>([])

  // ---- 两步验证 ----
  const [twoFAEnabled, setTwoFAEnabled] = useState(false)
  const [twoFALoading, setTwoFALoading] = useState(false)
  const [show2FASetup, setShow2FASetup] = useState(false)
  const [twoFACode, setTwoFACode] = useState('')
  const [twoFASecret, setTwoFASecret] = useState('')
  const [show2FADisable, setShow2FADisable] = useState(false)
  const [twoFADisableCode, setTwoFADisableCode] = useState('')

  // ---- 登录设备 ----
  const [devices, setDevices] = useState<LoginDevice[]>([])

  // ---- 发票 ----
  const [invoiceForm, setInvoiceForm] = useState({ title: '', taxNumber: '', amount: '' })

  // ---- 充值记录 ----
  const [rechargeRecords, setRechargeRecords] = useState<any[]>([])

  // ---- 绑定 ----
  const [bindEmailForm, setBindEmailForm] = useState({ email: '', code: '' })
  const [bindPhoneForm, setBindPhoneForm] = useState({ phone: '', code: '' })
  const [bindCodeLoading, setBindCodeLoading] = useState<'email' | 'phone' | null>(null)
  const [bindCountdown, setBindCountdown] = useState(0)

  // ---- 删除账号 ----
  const [deletePassword, setDeletePassword] = useState('')

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

  // ==================== 充值 ====================
  const handleRecharge = async () => {
    try {
      await userApi.recharge(rechargeAmount)
      showSuccess(`成功充值 ${rechargeAmount} 元`)
    } catch (e: any) {
      showError(e.message || '充值失败')
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
    const items: OAuthItem[] = OAUTH_PROVIDERS.map(p => ({
      provider: p.key,
      label: p.label,
      connected: false,
    }))
    try {
      const info = await userApi.getInfo()
      type OAuthInfo = { providers?: Record<string, string>; oauth_providers?: Record<string, string>; oauthProviders?: Record<string, string> }
      const typedInfo = info as unknown as OAuthInfo | null
      const providers = typedInfo?.providers || typedInfo?.oauth_providers || typedInfo?.oauthProviders || {}
      for (const item of items) {
        if (providers[item.provider]) {
          item.connected = true
          item.account = providers[item.provider]
        }
      }
    } catch {
      // ignore
    }
    setOauthItems(items)
  }

  const handleBindOAuth = async (provider: string) => {
    try {
      await authApi.oauthBind(provider, '')
      showSuccess(`${OAUTH_PROVIDERS.find(p => p.key === provider)?.label || provider} 绑定成功`)
      await loadOAuthBindings()
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
    try {
      const data = await twoFAApi.getStatus()
      setTwoFAEnabled(data.enabled === true)
    } catch {
      // ignore
    }
  }

  const handleEnable2FA = async () => {
    try {
      setTwoFALoading(true)
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
      await twoFAApi.verify(twoFACode)
      setTwoFAEnabled(true)
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
      showSuccess('两步验证已禁用')
    } catch (e: any) {
      showError(e.message || '验证码错误')
    } finally {
      setTwoFALoading(false)
    }
  }

  // ==================== 登录设备 ====================
  const loadDevices = async () => {
    try {
      const list = await loginDeviceApi.list()
      setDevices(Array.isArray(list) ? list : [])
    } catch {
      setDevices([])
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
      setRechargeRecords(Array.isArray(records) ? records : [])
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
      setBindCountdown(60)
      const timer = setInterval(() => {
        setBindCountdown(prev => {
          if (prev <= 1) {
            clearInterval(timer)
            return 0
          }
          return prev - 1
        })
      }, 1000)
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
    } catch (e: any) {
      showError(e.message || '绑定失败')
    }
  }

  // ==================== 删除账号 ====================
  const handleDeleteAccount = async () => {
    if (!deletePassword) {
      showError('请输入密码确认')
      return
    }
    try {
      await accountApi.deleteAccount(deletePassword)
      showSuccess('账号已提交注销申请')
      authApi.logout()
      navigate('/login')
    } catch (e: any) {
      showError(e.message || '注销失败')
    }
  }

  // ==================== 复制 2FA 密钥 ====================
  const copySecret = () => {
    navigator.clipboard.writeText(twoFASecret).then(() => {
      showSuccess('密钥已复制')
    })
  }

  if (!token) return null

  // ==================== Render ====================
  const tabs = [
    { key: 'password', label: '修改密码' },
    { key: 'recharge', label: '点数充值' },
    { key: 'coupons', label: '优惠券' },
    { key: 'bind', label: '三方绑定' },
    { key: '2fa', label: '两步验证' },
    { key: 'invoice', label: '发票' },
    { key: 'devices', label: '登录设备' },
    { key: 'recharge-history', label: '充值记录' },
    { key: 'delete', label: '账号注销' },
  ] as const

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      <h2 className="text-2xl font-bold mb-6">账户安全</h2>

      {/* Tab 导航 */}
      <div className="flex flex-wrap gap-2 mb-8 border-b border-gray-200 pb-3">
        {tabs.map(t => (
          <button
            key={t.key}
            onClick={() => setTab(t.key as typeof tab)}
            className={`px-4 py-2 rounded-full text-sm font-medium transition-colors ${
              tab === t.key
                ? 'bg-indigo-600 text-white shadow'
                : 'text-gray-600 hover:bg-gray-100'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* ---- 修改密码 ---- */}
      {tab === 'password' && (
        <div className="max-w-md space-y-4">
          <h3 className="text-lg font-semibold mb-2">修改密码</h3>
          <input
            className="w-full border rounded-lg px-4 py-2"
            type="password"
            placeholder="旧密码"
            value={pwd.oldPassword}
            onChange={e => setPwd({ ...pwd, oldPassword: e.target.value })}
          />
          <input
            className="w-full border rounded-lg px-4 py-2"
            type="password"
            placeholder="新密码"
            value={pwd.newPassword}
            onChange={e => setPwd({ ...pwd, newPassword: e.target.value })}
          />
          <input
            className="w-full border rounded-lg px-4 py-2"
            type="password"
            placeholder="确认新密码"
            value={pwd.confirmPassword}
            onChange={e => setPwd({ ...pwd, confirmPassword: e.target.value })}
          />
          {pwdMsg && <p className={`text-sm ${pwdMsg.includes('成功') ? 'text-green-600' : 'text-red-500'}`}>{pwdMsg}</p>}
          <button onClick={handleChangePassword} className="w-full bg-indigo-600 text-white py-2 rounded-lg hover:bg-indigo-700 transition-colors">
            确认修改
          </button>
        </div>
      )}

      {/* ---- 点数充值 ---- */}
      {tab === 'recharge' && (
        <div className="max-w-md space-y-4">
          <h3 className="text-lg font-semibold mb-2">点数充值</h3>
          <label className="block text-sm font-medium text-gray-700">充值金额 (元)</label>
          <input
            className="w-full border rounded-lg px-4 py-2"
            type="number"
            min={1}
            value={rechargeAmount}
            onChange={e => setRechargeAmount(Number(e.target.value))}
          />
          <button onClick={handleRecharge} className="w-full bg-indigo-600 text-white py-2 rounded-lg hover:bg-indigo-700 transition-colors">
            确认充值 {rechargeAmount} 元
          </button>
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
          <div className="grid gap-3">
            {oauthItems.map(item => (
              <div key={item.provider} className="flex items-center justify-between border rounded-lg p-4">
                <div>
                  <span className="font-medium">{item.label}</span>
                  {item.connected && item.account && (
                    <span className="ml-2 text-xs text-gray-500">({item.account})</span>
                  )}
                </div>
                <button
                  onClick={() => item.connected ? handleUnbindOAuth(item.provider) : handleBindOAuth(item.provider)}
                  className={`px-4 py-1.5 rounded-full text-xs font-medium transition-colors ${
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

          {/* 邮箱手机绑定 */}
          <div className="mt-8 space-y-6">
            <h3 className="text-lg font-semibold">绑定信息</h3>

            {/* 邮箱绑定 */}
            <div className="border rounded-lg p-4 space-y-3">
              <h4 className="font-medium text-sm">邮箱绑定</h4>
              <div className="flex gap-2">
                <input
                  className="flex-1 border rounded-lg px-3 py-2 text-sm"
                  placeholder="输入邮箱"
                  type="email"
                  value={bindEmailForm.email}
                  onChange={e => setBindEmailForm({ ...bindEmailForm, email: e.target.value })}
                />
                <button
                  onClick={() => handleSendBindCode('email')}
                  disabled={bindCodeLoading === 'email' || bindCountdown > 0}
                  className="px-3 py-2 bg-gray-100 rounded-lg text-sm whitespace-nowrap disabled:opacity-50"
                >
                  {bindCountdown > 0 ? `${bindCountdown}s` : '获取验证码'}
                </button>
              </div>
              <div className="flex gap-2">
                <input
                  className="flex-1 border rounded-lg px-3 py-2 text-sm"
                  placeholder="输入验证码"
                  value={bindEmailForm.code}
                  onChange={e => setBindEmailForm({ ...bindEmailForm, code: e.target.value })}
                />
                <button
                  onClick={handleBindEmail}
                  className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm whitespace-nowrap hover:bg-indigo-700"
                >
                  绑定邮箱
                </button>
              </div>
            </div>

            {/* 手机绑定 */}
            <div className="border rounded-lg p-4 space-y-3">
              <h4 className="font-medium text-sm">手机绑定</h4>
              <div className="flex gap-2">
                <input
                  className="flex-1 border rounded-lg px-3 py-2 text-sm"
                  placeholder="输入手机号"
                  type="tel"
                  value={bindPhoneForm.phone}
                  onChange={e => setBindPhoneForm({ ...bindPhoneForm, phone: e.target.value })}
                />
                <button
                  onClick={() => handleSendBindCode('phone')}
                  disabled={bindCodeLoading === 'phone' || bindCountdown > 0}
                  className="px-3 py-2 bg-gray-100 rounded-lg text-sm whitespace-nowrap disabled:opacity-50"
                >
                  {bindCountdown > 0 ? `${bindCountdown}s` : '获取验证码'}
                </button>
              </div>
              <div className="flex gap-2">
                <input
                  className="flex-1 border rounded-lg px-3 py-2 text-sm"
                  placeholder="输入验证码"
                  value={bindPhoneForm.code}
                  onChange={e => setBindPhoneForm({ ...bindPhoneForm, code: e.target.value })}
                />
                <button
                  onClick={handleBindPhone}
                  className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm whitespace-nowrap hover:bg-indigo-700"
                >
                  绑定手机
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ---- 两步验证 ---- */}
      {tab === '2fa' && (
        <div className="space-y-4 max-w-md">
          <h3 className="text-lg font-semibold mb-2">两步验证</h3>
          <p className="text-sm text-gray-600">当前状态: {twoFAEnabled ? <span className="text-green-600 font-medium">已启用</span> : <span className="text-gray-500">未启用</span>}</p>

          {show2FASetup && !twoFAEnabled && (
            <div className="border rounded-lg p-4 space-y-3 bg-gray-50">
              <p className="text-sm font-medium">设置两步验证</p>
              <p className="text-xs text-gray-500">请使用 Google Authenticator 或 Authy 扫描下方密钥：</p>
              <div className="flex items-center gap-2">
                <code className="bg-white border rounded px-3 py-1 text-sm break-all flex-1">{twoFASecret}</code>
                <button onClick={copySecret} className="px-3 py-1 bg-white border rounded text-xs hover:bg-gray-100">
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
            <button
              onClick={() => setShow2FADisable(true)}
              className="px-4 py-2 border border-red-300 text-red-600 rounded-lg text-sm hover:bg-red-50"
            >
              禁用两步验证
            </button>
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
                placeholder="输入动态验证码"
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
        </div>
      )}

      {/* ---- 登录设备 ---- */}
      {tab === 'devices' && (
        <div className="space-y-4">
          <h3 className="text-lg font-semibold mb-2">登录设备</h3>
          {devices.length > 0 && (
            <button
              onClick={handleRemoveAllDevices}
              className="px-4 py-2 text-sm border border-red-300 text-red-600 rounded-lg hover:bg-red-50"
            >
              下线所有其他设备
            </button>
          )}
          {devices.length === 0 ? (
            <p className="text-gray-500 text-sm">暂无设备记录</p>
          ) : (
            <div className="space-y-3">
              {devices.map((d: any, i: number) => (
                <div key={d.id || i} className="flex items-center justify-between border rounded-lg p-3">
                  <div>
                    <p className="text-sm font-medium">
                      {d.os || 'Unknown'} / {d.browser || 'Unknown'}
                      {d.isCurrent && <span className="ml-2 text-xs text-green-600 bg-green-50 rounded-full px-2 py-0.5">当前设备</span>}
                    </p>
                    <p className="text-xs text-gray-500 mt-0.5">
                      {d.ip || ''} {d.region ? `(${d.region})` : ''}
                    </p>
                    <p className="text-xs text-gray-400 mt-0.5">
                      {d.loginTime ? new Date(d.loginTime).toLocaleString() : ''}
                    </p>
                  </div>
                  {!d.isCurrent && (
                    <button
                      onClick={() => handleRemoveDevice(d.id)}
                      className="px-3 py-1 text-xs border border-gray-300 rounded-full hover:bg-red-50 hover:border-red-300 hover:text-red-600"
                    >
                      移除
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
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
            <div className="overflow-x-auto">
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

      {/* ---- 账号注销 ---- */}
      {tab === 'delete' && (
        <div className="max-w-md space-y-4">
          <h3 className="text-lg font-semibold mb-2 text-red-600">账号注销</h3>
          <p className="text-xs text-gray-500">注销后相关数据将被清除，请谨慎操作</p>
          <input
            className="w-full border rounded-lg px-4 py-2"
            type="password"
            placeholder="输入密码确认"
            value={deletePassword}
            onChange={e => setDeletePassword(e.target.value)}
          />
          <button
            onClick={handleDeleteAccount}
            className="w-full bg-red-600 text-white py-2 rounded-lg hover:bg-red-700 transition-colors"
          >
            确认注销
          </button>
        </div>
      )}
    </div>
  )
}

export default SecurityPage