import { useEffect, useRef, useState, type FormEvent } from 'react'
import { adminApi, adminUserApi, type AdminUserDetail, type AdminUserInfo } from '@/services/api'
import type { AdminUserAction } from '@/components/AdminUserActionDialog'
import UserAvatar from '@/components/UserAvatar'

interface Props {
  user: AdminUserInfo | null
  dark: boolean
  isSuperAdmin: boolean
  onClose: () => void
  onAction: (user: AdminUserInfo, action: AdminUserAction) => void
  onToggleStatus: (user: AdminUserInfo) => void
  onGrantAdmin: (user: AdminUserInfo) => void
  onProfileChanged: () => void | Promise<void>
}

const providerLabels: Record<string, string> = { qq: 'QQ', google: 'Google', github: 'GitHub', microsoft: 'Microsoft' }
const membershipLabels: Record<string, string> = { free: '普通用户', silver: '白银会员', gold: '黄金会员', diamond: '钻石会员' }

function formatDate(value?: string | null) {
  if (!value) return '暂无记录'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

export default function AdminUserDetailDrawer({ user, dark, isSuperAdmin, onClose, onAction, onToggleStatus, onGrantAdmin, onProfileChanged }: Props) {
  const [detail, setDetail] = useState<AdminUserDetail | null>(null)
  const [nickname, setNickname] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [loading, setLoading] = useState(false)
  const [savingProfile, setSavingProfile] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const fileRef = useRef<HTMLInputElement>(null)

  const loadDetail = async () => {
    if (!user) return
    setLoading(true)
    setError('')
    try {
      const value = await adminApi.getUserDetail(user.id)
      setDetail(value)
      setNickname(value.nickname || '')
      setEmail(value.email || '')
      setPhone(value.phone || '')
    } catch (cause) {
      setError((cause as Error).message || '读取用户详情失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void loadDetail() }, [user?.id])

  if (!user) return null
  const activeUser = detail || user
  const userName = detail?.displayName || detail?.nickname || user.nickname || user.displayName || user.username
  const fieldStyle = { background: 'var(--bg-input)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }

  const saveProfile = async (event: FormEvent) => {
    event.preventDefault()
    setSavingProfile(true)
    setError('')
    setMessage('')
    try {
      await adminUserApi.updateProfile(user.id, {
        nickname: nickname.trim() || null,
        email: email.trim() || null,
        phone: phone.trim() || null,
      })
      setMessage('资料已保存；变更后的邮箱或手机号需要用户重新验证。')
      await Promise.all([loadDetail(), onProfileChanged()])
    } catch (cause) {
      setError((cause as Error).message || '保存资料失败')
    } finally {
      setSavingProfile(false)
    }
  }

  const uploadAvatar = async (file?: File) => {
    if (!file) return
    if (!file.type.startsWith('image/')) { setError('请选择图片文件'); return }
    if (file.size > 5 * 1024 * 1024) { setError('头像不能超过 5 MB'); return }
    setError('')
    setMessage('')
    setLoading(true)
    try {
      await adminUserApi.uploadAvatar(user.id, file)
      setMessage('头像已更新。')
      await Promise.all([loadDetail(), onProfileChanged()])
    } catch (cause) {
      setError((cause as Error).message || '上传头像失败')
    } finally {
      setLoading(false)
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  const clearAvatar = async () => {
    setError('')
    setMessage('')
    setLoading(true)
    try {
      await adminUserApi.clearAvatar(user.id)
      setMessage('已恢复第三方头像或默认头像。')
      await Promise.all([loadDetail(), onProfileChanged()])
    } catch (cause) {
      setError((cause as Error).message || '恢复头像失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-[9000] flex justify-end" role="presentation">
      <button type="button" className="absolute inset-0 cursor-default bg-slate-950/45 backdrop-blur-[2px]" aria-label="关闭用户详情" onClick={onClose} />
      <section role="dialog" aria-modal="true" aria-label={`${userName} 用户详情`} className="relative z-10 flex h-full w-full max-w-[580px] flex-col overflow-hidden border-l shadow-2xl" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
        <header className="flex shrink-0 items-center justify-between gap-3 border-b px-5 py-4" style={{ borderColor: 'var(--border-color)', background: 'var(--bg-card)' }}>
          <div className="flex min-w-0 items-center gap-3">
            <UserAvatar name={userName} avatarUrl={detail?.avatarUrl || user.avatarUrl} className="h-12 w-12 border border-black/5 text-base font-semibold text-white" style={{ background: 'linear-gradient(135deg,#8B5CF6,#4F46E5)' }} textClassName="text-white" />
            <div className="min-w-0">
              <h2 className="truncate text-base font-semibold" style={{ color: 'var(--text-primary)' }}>{userName}</h2>
              <p className="truncate text-[11px]" style={{ color: 'var(--text-muted)' }}>登录名 {user.username} · ID {user.id}</p>
            </div>
          </div>
          <button type="button" aria-label="关闭" onClick={onClose} className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg" style={{ background: 'var(--bg-surface)', color: 'var(--text-secondary)' }}><svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18" /></svg></button>
        </header>

        <div className="page-scroll min-h-0 flex-1 space-y-4 overflow-y-auto p-4 sm:p-5">
          {loading && !detail && <div className="rounded-xl border p-5 text-center text-xs" style={{ borderColor: 'var(--border-color)', color: 'var(--text-muted)' }}>正在加载用户资料...</div>}
          {error && <div role="alert" className="rounded-lg px-3 py-2 text-xs" style={{ background: 'rgba(244,63,94,.1)', color: '#F43F5E' }}>{error}</div>}
          {message && <div role="status" className="rounded-lg px-3 py-2 text-xs" style={{ background: 'rgba(16,185,129,.1)', color: '#059669' }}>{message}</div>}

          <section className="rounded-xl border p-4" style={{ borderColor: 'var(--border-color)', background: 'var(--bg-card)' }}>
            <div className="mb-3 flex items-center justify-between gap-3"><div><h3 className="text-xs font-semibold" style={{ color: 'var(--text-primary)' }}>账号概览</h3><p className="mt-1 text-[10px]" style={{ color: 'var(--text-muted)' }}>登录方式、会员和使用情况</p></div><span className="rounded-full px-2 py-1 text-[10px] font-medium" style={{ color: activeUser.status === 'ACTIVE' ? '#059669' : '#E11D48', background: activeUser.status === 'ACTIVE' ? 'rgba(16,185,129,.1)' : 'rgba(244,63,94,.1)' }}>{activeUser.status === 'ACTIVE' ? '正常' : '已封禁'}</span></div>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
              {[
                ['会员等级', membershipLabels[detail?.membership || user.membership] || '普通用户'],
                ['余额', `¥${Number(detail?.balance ?? user.balance ?? 0).toFixed(2)}`],
                ['点数', `${Number(detail?.points ?? user.points ?? 0).toFixed(2)} 点`],
                ['AI 调用', String(detail?.totalCalls ?? user.totalCalls ?? 0)],
              ].map(([label, value]) => <div key={label} className="rounded-lg px-2.5 py-2" style={{ background: 'var(--bg-surface)' }}><div className="text-[9px]" style={{ color: 'var(--text-muted)' }}>{label}</div><div className="mt-1 truncate text-[11px] font-semibold tabular-nums" style={{ color: 'var(--text-primary)' }}>{value}</div></div>)}
            </div>
            <div className="mt-3 grid grid-cols-1 gap-2 text-[10px] sm:grid-cols-2" style={{ color: 'var(--text-muted)' }}>
              <span>注册时间：{formatDate(detail?.createTime || user.createTime)}</span>
              <span>最近登录：{formatDate(detail?.lastLoginTime)}</span>
              <span>会员到期：{formatDate(detail?.membershipExpireTime)}</span>
              <span>API Key：{detail?.apiKeyCount ?? user.apiKeyCount ?? 0} 个</span>
            </div>
          </section>

          <form onSubmit={event => void saveProfile(event)} className="rounded-xl border p-4" style={{ borderColor: 'var(--border-color)' }}>
            <div className="mb-3"><h3 className="text-xs font-semibold" style={{ color: 'var(--text-primary)' }}>用户资料</h3><p className="mt-1 text-[10px]" style={{ color: 'var(--text-muted)' }}>邮箱或手机号被修改后会重置验证状态。</p></div>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <label className="text-[10px]" style={{ color: 'var(--text-secondary)' }}>展示昵称<input value={nickname} onChange={event => setNickname(event.target.value)} maxLength={64} placeholder="未设置时使用登录名" className="mt-1.5 w-full rounded-lg px-3 py-2 text-xs outline-none" style={fieldStyle} /></label>
              <label className="text-[10px]" style={{ color: 'var(--text-secondary)' }}>邮箱<input type="email" value={email} onChange={event => setEmail(event.target.value)} maxLength={128} placeholder="未绑定" className="mt-1.5 w-full rounded-lg px-3 py-2 text-xs outline-none" style={fieldStyle} /></label>
              <label className="text-[10px]" style={{ color: 'var(--text-secondary)' }}>手机号<input inputMode="tel" value={phone} onChange={event => setPhone(event.target.value)} maxLength={32} placeholder="未绑定" className="mt-1.5 w-full rounded-lg px-3 py-2 text-xs outline-none" style={fieldStyle} /></label>
              <div className="flex items-end"><button type="submit" disabled={savingProfile || loading} className="w-full rounded-lg px-3 py-2 text-xs font-medium text-white disabled:opacity-50" style={{ background: dark ? '#7C3AED' : '#6D3BE8' }}>{savingProfile ? '保存中...' : '保存资料'}</button></div>
            </div>
            <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-[10px]" style={{ color: 'var(--text-muted)' }}><span>邮箱验证：{detail?.emailVerified ? '已验证' : '未验证'}</span><span>手机验证：{detail?.phoneVerified ? '已验证' : '未验证'}</span></div>
          </form>

          <section className="rounded-xl border p-4" style={{ borderColor: 'var(--border-color)' }}>
            <div className="mb-3 flex items-center justify-between gap-3"><div><h3 className="text-xs font-semibold" style={{ color: 'var(--text-primary)' }}>头像</h3><p className="mt-1 text-[10px]" style={{ color: 'var(--text-muted)' }}>上传的图片保存在平台本地存储，未来可切换 OSS。</p></div><div className="flex gap-2"><input ref={fileRef} type="file" accept="image/*" className="hidden" onChange={event => void uploadAvatar(event.target.files?.[0])} /><button type="button" disabled={loading} onClick={() => fileRef.current?.click()} className="rounded-lg px-2.5 py-1.5 text-[10px] font-medium" style={{ color: '#7C3AED', background: 'rgba(124,58,237,.1)' }}>更换头像</button>{detail?.customAvatar && <button type="button" disabled={loading} onClick={() => void clearAvatar()} className="rounded-lg px-2.5 py-1.5 text-[10px]" style={{ color: 'var(--text-secondary)', background: 'var(--bg-surface)' }}>恢复默认</button>}</div></div>
            <div className="flex items-center gap-3"><UserAvatar name={userName} avatarUrl={detail?.avatarUrl || user.avatarUrl} className="h-14 w-14 border border-black/5 text-lg font-semibold text-white" style={{ background: 'linear-gradient(135deg,#8B5CF6,#4F46E5)' }} textClassName="text-white" /><div><div className="text-xs font-medium" style={{ color: 'var(--text-primary)' }}>{detail?.customAvatar ? '平台自定义头像' : '第三方头像或默认头像'}</div><div className="mt-1 max-w-[340px] break-all text-[10px]" style={{ color: 'var(--text-muted)' }}>{detail?.avatarUrl || '尚未设置头像'}</div></div></div>
          </section>

          <section className="rounded-xl border p-4" style={{ borderColor: 'var(--border-color)' }}>
            <div className="mb-3"><h3 className="text-xs font-semibold" style={{ color: 'var(--text-primary)' }}>第三方登录</h3><p className="mt-1 text-[10px]" style={{ color: 'var(--text-muted)' }}>绑定账号资料只读；解除绑定仍由用户本人在安全设置操作。</p></div>
            {!detail?.oauthAccounts?.length ? <p className="rounded-lg px-3 py-3 text-[10px]" style={{ background: 'var(--bg-surface)', color: 'var(--text-muted)' }}>未绑定第三方登录</p> : <div className="space-y-2">{detail.oauthAccounts.map((account, index) => <div key={`${account.provider}-${index}`} className="flex items-center gap-3 rounded-lg border p-2.5" style={{ borderColor: 'var(--border-color)', background: 'var(--bg-surface)' }}><UserAvatar name={account.displayName || account.provider} avatarUrl={account.avatarUrl} className="h-9 w-9 text-xs font-semibold text-white" style={{ background: 'linear-gradient(135deg,#64748B,#475569)' }} textClassName="text-white" /><div className="min-w-0 flex-1"><div className="flex items-center gap-2"><strong className="text-[11px]" style={{ color: 'var(--text-primary)' }}>{providerLabels[account.provider.toLowerCase()] || account.provider}</strong><span className="truncate text-[10px]" style={{ color: 'var(--text-secondary)' }}>{account.displayName || '未提供昵称'}</span></div><div className="truncate text-[10px]" style={{ color: 'var(--text-muted)' }}>{account.email || '未提供邮箱'} · 绑定于 {formatDate(account.createTime)}</div></div></div>)}</div>}
          </section>
        </div>

        <footer className="shrink-0 border-t p-3 sm:p-4" style={{ borderColor: 'var(--border-color)', background: 'var(--bg-card)' }}>
          <div className="mb-2 grid grid-cols-2 gap-2 sm:grid-cols-4">
            <button type="button" onClick={() => onAction(user, 'points')} className="rounded-lg px-2 py-2 text-[10px] font-medium" style={{ color: '#B45309', background: 'rgba(245,158,11,.11)' }}>调整点数</button>
            {isSuperAdmin && <button type="button" onClick={() => onAction(user, 'balance')} className="rounded-lg px-2 py-2 text-[10px] font-medium" style={{ color: '#047857', background: 'rgba(16,185,129,.11)' }}>调整余额</button>}
            <button type="button" onClick={() => onAction(user, 'membership')} className="rounded-lg px-2 py-2 text-[10px] font-medium" style={{ color: '#6D28D9', background: 'rgba(139,92,246,.11)' }}>调整会员</button>
            <button type="button" onClick={() => onAction(user, 'password')} className="rounded-lg px-2 py-2 text-[10px] font-medium" style={{ color: '#1D4ED8', background: 'rgba(59,130,246,.11)' }}>重置密码</button>
          </div>
          <div className="flex gap-2">
            {isSuperAdmin && user.role === 'USER' && <button type="button" onClick={() => onGrantAdmin(user)} className="flex-1 rounded-lg border px-3 py-2 text-xs" style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>设为管理员</button>}
            <button type="button" onClick={() => onToggleStatus(user)} className="flex-1 rounded-lg border px-3 py-2 text-xs font-medium" style={{ borderColor: user.status === 'ACTIVE' ? 'rgba(244,63,94,.24)' : 'rgba(16,185,129,.24)', color: user.status === 'ACTIVE' ? '#E11D48' : '#059669', background: user.status === 'ACTIVE' ? 'rgba(244,63,94,.06)' : 'rgba(16,185,129,.06)' }}>{user.status === 'ACTIVE' ? '封禁用户' : '解除封禁'}</button>
          </div>
        </footer>
      </section>
    </div>
  )
}
