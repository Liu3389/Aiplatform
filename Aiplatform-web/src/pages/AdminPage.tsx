import { useState, useEffect } from 'react'
import { adminApi } from '@/services/api'

interface AdminUser {
  id: number
  username: string
  email: string | null
  balance: number
  role: string
  status: string
  apiKeyCount: number
  totalCalls: number
  totalCost: number
  createTime: string
}

interface AdminInfo {
  id: number
  username: string
  email: string | null
  role: string
  status: string
  createTime: string
}

export default function AdminPage() {
  const [users, setUsers] = useState<AdminUser[]>([])
  const [admins, setAdmins] = useState<AdminInfo[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [tab, setTab] = useState<'users' | 'admins'>('users')

  const loadData = async () => {
    setLoading(true)
    setError('')
    try {
      const [userList, adminList] = await Promise.all([
        adminApi.getUsers(),
        adminApi.getAdmins().catch(() => [] as AdminInfo[]),
      ])
      setUsers(userList)
      setAdmins(adminList)
    } catch (err) {
      setError((err as Error).message || '加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { loadData() }, [])

  const handleToggleStatus = async (user: AdminUser) => {
    try {
      const newStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
      await adminApi.toggleUserStatus(user.id, newStatus as 'ACTIVE' | 'DISABLED')
      setUsers(users.map((u) => u.id === user.id ? { ...u, status: newStatus } : u))
    } catch (err) {
      setError((err as Error).message || '操作失败')
    }
  }

  const handleRevokeAdmin = async (userId: number) => {
    try {
      await adminApi.revokeAdminRole(userId)
      setAdmins(admins.filter((a) => a.id !== userId))
    } catch (err) {
      setError((err as Error).message || '操作失败')
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <svg className="animate-spin w-8 h-8 text-primary-400" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2" strokeDasharray="32" strokeLinecap="round" opacity="0.2"/>
          <path d="M12 2a10 10 0 019.95 9" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
        </svg>
      </div>
    )
  }

  return (
    <div className="p-4 lg:p-8 max-w-7xl mx-auto animate-fade-in">
      <h1 className="text-2xl font-bold text-white mb-6">管理后台</h1>

      {error && (
        <div className="flex items-center gap-2 text-sm text-red-400 bg-red-500/5 border border-red-500/10 rounded-lg px-4 py-3 mb-4">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
          {error}
        </div>
      )}

      {/* Tabs */}
      <div className="flex gap-2 mb-6">
        {[
          { key: 'users' as const, label: '用户管理', count: users.length },
          { key: 'admins' as const, label: '管理员', count: admins.length },
        ].map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={`px-4 py-2 rounded-xl text-sm font-medium transition-all ${
              tab === t.key
                ? 'bg-primary-500/15 text-primary-400 border border-primary-500/20'
                : 'text-surface-400 hover:text-white hover:bg-white/[0.04]'
            }`}
          >
            {t.label} ({t.count})
          </button>
        ))}
      </div>

      {/* Users table */}
      {tab === 'users' && (
        <div className="glass rounded-2xl overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-white/[0.06]">
                  <th className="text-left px-4 py-3 text-surface-400 font-medium text-xs">ID</th>
                  <th className="text-left px-4 py-3 text-surface-400 font-medium text-xs">用户名</th>
                  <th className="text-left px-4 py-3 text-surface-400 font-medium text-xs">角色</th>
                  <th className="text-left px-4 py-3 text-surface-400 font-medium text-xs">状态</th>
                  <th className="text-right px-4 py-3 text-surface-400 font-medium text-xs">余额</th>
                  <th className="text-right px-4 py-3 text-surface-400 font-medium text-xs">调用次数</th>
                  <th className="text-right px-4 py-3 text-surface-400 font-medium text-xs">操作</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u) => (
                  <tr key={u.id} className="border-b border-white/[0.03] hover:bg-white/[0.02] transition-colors">
                    <td className="px-4 py-3 text-surface-500 font-mono">{u.id}</td>
                    <td className="px-4 py-3 text-white font-medium">{u.username}</td>
                    <td className="px-4 py-3">
                      <span className={`px-2 py-0.5 rounded-md text-xs font-medium ${
                        u.role === 'SUPER_ADMIN' ? 'bg-purple-500/10 text-purple-400' :
                        u.role === 'ADMIN' ? 'bg-primary-500/10 text-primary-400' :
                        'bg-surface-700 text-surface-400'
                      }`}>
                        {u.role === 'SUPER_ADMIN' ? '超管' : u.role === 'ADMIN' ? '管理员' : '用户'}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`px-2 py-0.5 rounded-md text-xs font-medium ${
                        u.status === 'ACTIVE' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-red-500/10 text-red-400'
                      }`}>
                        {u.status === 'ACTIVE' ? '正常' : '禁用'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right text-white font-mono">${u.balance?.toFixed(4)}</td>
                    <td className="px-4 py-3 text-right text-surface-400">{u.totalCalls || 0}</td>
                    <td className="px-4 py-3 text-right">
                      <button
                        onClick={() => handleToggleStatus(u)}
                        className={`px-3 py-1 text-xs font-medium rounded-lg transition-colors ${
                          u.status === 'ACTIVE'
                            ? 'bg-red-500/10 text-red-400 hover:bg-red-500/20'
                            : 'bg-emerald-500/10 text-emerald-400 hover:bg-emerald-500/20'
                        }`}
                      >
                        {u.status === 'ACTIVE' ? '禁用' : '启用'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Admins table */}
      {tab === 'admins' && (
        <div className="glass rounded-2xl overflow-hidden">
          {admins.length === 0 ? (
            <div className="p-8 text-center text-surface-400 text-sm">暂无管理员数据</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-white/[0.06]">
                    <th className="text-left px-4 py-3 text-surface-400 font-medium text-xs">ID</th>
                    <th className="text-left px-4 py-3 text-surface-400 font-medium text-xs">用户名</th>
                    <th className="text-left px-4 py-3 text-surface-400 font-medium text-xs">角色</th>
                    <th className="text-left px-4 py-3 text-surface-400 font-medium text-xs">状态</th>
                    <th className="text-right px-4 py-3 text-surface-400 font-medium text-xs">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {admins.map((a) => (
                    <tr key={a.id} className="border-b border-white/[0.03] hover:bg-white/[0.02] transition-colors">
                      <td className="px-4 py-3 text-surface-500 font-mono">{a.id}</td>
                      <td className="px-4 py-3 text-white font-medium">{a.username}</td>
                      <td className="px-4 py-3">
                        <span className={`px-2 py-0.5 rounded-md text-xs font-medium ${
                          a.role === 'SUPER_ADMIN' ? 'bg-purple-500/10 text-purple-400' : 'bg-primary-500/10 text-primary-400'
                        }`}>
                          {a.role === 'SUPER_ADMIN' ? '超管' : '管理员'}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span className={`px-2 py-0.5 rounded-md text-xs font-medium ${
                          a.status === 'ACTIVE' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-red-500/10 text-red-400'
                        }`}>
                          {a.status === 'ACTIVE' ? '正常' : '禁用'}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right">
                        {a.role !== 'SUPER_ADMIN' && (
                          <button
                            onClick={() => handleRevokeAdmin(a.id)}
                            className="px-3 py-1 text-xs font-medium rounded-lg bg-red-500/10 text-red-400 hover:bg-red-500/20 transition-colors"
                          >
                            取消管理员
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
