import { useEffect, useState } from 'react'
import { adminBalanceApi, adminMembershipApi, adminPointsApi, adminUserApi } from '@/services/api'

export type AdminUserAction = 'points' | 'balance' | 'membership' | 'password'

interface TargetUser {
  id: number
  username: string
  membership?: string
}

interface Props {
  user: TargetUser | null
  action: AdminUserAction | null
  dark: boolean
  onClose: () => void
  onSuccess: () => void | Promise<void>
}

const MEMBERSHIP_LABELS: Record<string, string> = {
  free: '普通用户',
  silver: '白银会员',
  gold: '黄金会员',
  diamond: '钻石会员',
}

export default function AdminUserActionDialog({ user, action, dark, onClose, onSuccess }: Props) {
  const [points, setPoints] = useState('')
  const [reason, setReason] = useState('')
  const [membership, setMembership] = useState('free')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!user || !action) return
    setPoints('')
    setReason('')
    setMembership(user.membership || 'free')
    setPassword('')
    setError('')
  }, [user, action])

  if (!user || !action) return null

  const submit = async () => {
    setError('')
    if ((action === 'points' || action === 'balance') && (!points || Number.isNaN(Number(points)) || Number(points) === 0)) {
      setError('请输入不为 0 的数值，负数表示扣减')
      return
    }
    if (action === 'password' && (password.length < 8 || !/[a-zA-Z]/.test(password) || !/\d/.test(password))) {
      setError('密码至少 8 位，且必须同时包含字母和数字')
      return
    }

    setLoading(true)
    try {
      if (action === 'points') {
        await adminPointsApi.adjust(user.id, Number(points), reason || '管理员手动调整')
      } else if (action === 'balance') {
        await adminBalanceApi.adjust(user.id, Number(points), reason || '管理员手动调整余额')
      } else if (action === 'membership') {
        await adminMembershipApi.update(user.id, membership)
      } else {
        await adminUserApi.resetPassword(user.id, password)
      }
      await onSuccess()
      onClose()
    } catch (e) {
      setError((e as Error).message || '操作失败')
    } finally {
      setLoading(false)
    }
  }

  const title = action === 'points' ? '调整用户点数' : action === 'balance' ? '调整用户余额' : action === 'membership' ? '调整会员等级' : '重置用户密码'
  const description = action === 'points'
    ? '正数为发放，负数为扣减；调整会写入点数流水和管理员日志。'
    : action === 'balance'
      ? '正数为增加，负数为扣减；仅超级管理员可操作，调整会写入管理员操作日志。'
      : action === 'membership'
        ? '这里只调整账户标识；会员有效期如需单独变更，请在会员服务中处理。'
        : '重置后用户需要使用新密码登录。'
  const fieldStyle = { background: 'var(--bg-input)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }

  return (
    <div className="fixed inset-0 z-[10000] flex items-center justify-center p-4" style={{ background: 'rgba(0,0,0,0.55)', backdropFilter: 'blur(5px)' }} onClick={onClose}>
      <div className="w-full max-w-md rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)', boxShadow: '0 24px 64px rgba(0,0,0,0.25)' }} onClick={e => e.stopPropagation()}>
        <div className="flex items-start justify-between gap-3 mb-5">
          <div>
            <h3 className="text-base font-semibold" style={{ color: 'var(--text-primary)' }}>{title}</h3>
            <p className="text-xs mt-1" style={{ color: 'var(--text-dim)' }}>用户：{user.username}（ID {user.id}）</p>
          </div>
          <button type="button" onClick={onClose} className="w-8 h-8 rounded-lg flex items-center justify-center" style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)' }}><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><path d="m6 6 12 12M18 6 6 18" /></svg></button>
        </div>

        <p className="text-xs leading-relaxed mb-4" style={{ color: 'var(--text-dim)' }}>{description}</p>

        <form noValidate onSubmit={e => { e.preventDefault(); void submit() }} className="space-y-3">
          {(action === 'points' || action === 'balance') && (
            <>
              <label className="block text-xs" style={{ color: 'var(--text-dim)' }}>
                {action === 'points' ? '点数变动' : '余额变动（¥）'}
                <input autoFocus value={points} onChange={e => setPoints(e.target.value)} type="number" step="0.01" placeholder="例如 100 或 -20" className="w-full mt-1.5 px-3 py-2.5 rounded-xl text-sm outline-none" style={fieldStyle} />
              </label>
              <label className="block text-xs" style={{ color: 'var(--text-dim)' }}>
                调整原因
                <input value={reason} onChange={e => setReason(e.target.value)} placeholder="例如：活动补偿" className="w-full mt-1.5 px-3 py-2.5 rounded-xl text-sm outline-none" style={fieldStyle} />
              </label>
            </>
          )}

          {action === 'membership' && (
            <label className="block text-xs" style={{ color: 'var(--text-dim)' }}>
              会员等级
              <select autoFocus value={membership} onChange={e => setMembership(e.target.value)} className="w-full mt-1.5 px-3 py-2.5 rounded-xl text-sm outline-none" style={fieldStyle}>
                {Object.entries(MEMBERSHIP_LABELS).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
              </select>
            </label>
          )}

          {action === 'password' && (
            <label className="block text-xs" style={{ color: 'var(--text-dim)' }}>
              新密码
              <input autoFocus value={password} onChange={e => setPassword(e.target.value)} type="password" placeholder="至少 8 位，含字母和数字" className="w-full mt-1.5 px-3 py-2.5 rounded-xl text-sm outline-none" style={fieldStyle} />
            </label>
          )}

          {error && <div className="px-3 py-2 rounded-lg text-xs" style={{ color: '#F43F5E', background: 'rgba(244,63,94,0.1)' }}>{error}</div>}
          <div className="flex gap-2 pt-2">
            <button type="button" onClick={onClose} disabled={loading} className="flex-1 py-2.5 rounded-xl text-xs" style={{ color: 'var(--text-dim)', background: 'var(--bg-surface)', border: '1px solid var(--border-color)' }}>取消</button>
            <button type="submit" disabled={loading} className="flex-1 py-2.5 rounded-xl text-xs font-medium text-white disabled:opacity-50" style={{ background: dark ? 'linear-gradient(135deg,#8B5CF6,#6366F1)' : '#7C3AED' }}>{loading ? '提交中...' : '确认保存'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}
