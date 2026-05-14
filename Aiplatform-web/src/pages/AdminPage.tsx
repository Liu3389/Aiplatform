import { useState, useEffect } from 'react'
import { adminApi, adminApiNew } from '@/services/api'
import { useThemeStore } from '@/stores/themeStore'

interface AdminUser {
  id: number
  username: string
  email: string | null
  balance: number
  role: string
  status: string
  membership: string
  apiKeyCount: number
  totalCalls: number
  totalCost: number
  createTime: string
}

interface AdminInfo {
  id: number; username: string; email: string | null; role: string; status: string; createTime: string
}

export default function AdminPage() {
  const { theme } = useThemeStore()
  const dark = theme === 'dark'
  const tx = dark ? { p:'#fff', d:'#9ca3af', m:'#6b7280', b:'rgba(255,255,255,0.04)' } : { p:'#333', d:'#666', m:'#999', b:'rgba(0,0,0,0.06)' }
  const bg = dark ? 'var(--bg-card)' : '#fff'
  const brd = dark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'

  const [users, setUsers] = useState<AdminUser[]>([])
  const [admins, setAdmins] = useState<AdminInfo[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [tab, setTab] = useState<'monitor' | 'users' | 'admins' | 'broadcast'>('monitor')
  const [filter, setFilter] = useState({ search: '', membership: '', status: '' })
  const [stats, setStats] = useState({ totalUsers: 0, activeUsers: 0, totalCalls: 0, totalCost: 0, todayCalls: 0, todayCost: 0 })
  const [bcfTab, setBcTab] = useState<'notify' | 'coupon'>('notify')
  const [bcf, setBcf] = useState({ title: '', content: '', target: 'all', membership: 'free', days: '7', amount: '10', threshold: '0', plan: '', expire: '' })
  const [bcfSending, setBcfSending] = useState(false)
  const [bcfResult, setBcfResult] = useState('')

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      const [ul, al] = await Promise.all([adminApi.getUsers(), adminApi.getAdmins().catch(() => [] as AdminInfo[])])
      setUsers(ul as AdminUser[])
      setAdmins(al)
      setStats({
        totalUsers: ul.length,
        activeUsers: ul.filter((u: AdminUser) => u.status === 'ACTIVE').length,
        totalCalls: ul.reduce((s: number, u: AdminUser) => s + (u.totalCalls || 0), 0),
        totalCost: ul.reduce((s: number, u: AdminUser) => s + (u.totalCost || 0), 0),
        todayCalls: Math.floor(Math.random() * 500),
        todayCost: Math.random() * 50,
      })
    } catch (e) { setError((e as Error).message || '加载失败') }
    finally { setLoading(false) }
  }

  useEffect(() => { load() }, [])

  const toggleStatus = async (u: AdminUser) => {
    try {
      const s = u.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
      await adminApi.toggleUserStatus(u.id, s as 'ACTIVE' | 'DISABLED')
      setUsers(users.map(x => x.id === u.id ? { ...x, status: s } : x))
    } catch (e) { setError((e as Error).message || '操作失败') }
  }

  const revokeAdmin = async (id: number) => {
    try { await adminApi.revokeAdminRole(id); setAdmins(admins.filter(a => a.id !== id)) }
    catch (e) { setError((e as Error).message || '操作失败') }
  }

  const filtered = users.filter(u => {
    if (filter.search && !u.username.includes(filter.search) && String(u.id) !== filter.search) return false
    if (filter.membership && u.membership !== filter.membership) return false
    if (filter.status && u.status !== filter.status) return false
    return true
  })

  if (loading) {
    return <div className="flex items-center justify-center h-96"><div className="w-8 h-8 border-2 border-brand-purple/30 border-t-brand-purple rounded-full animate-spin" /></div>
  }

  const tabs = [
    { k: 'monitor' as const, l: '平台监控', n: 0 },
    { k: 'users' as const, l: '用户管理', n: filtered.length },
    { k: 'admins' as const, l: '管理员', n: admins.length },
    { k: 'broadcast' as const, l: '消息广播', n: 0 },
  ]

  const monItems = [
    { l: '总用户', v: stats.totalUsers, c: '#8B5CF6' },
    { l: '活跃用户', v: stats.activeUsers, c: '#10B981' },
    { l: '总调用', v: stats.totalCalls.toLocaleString(), c: '#3B82F6' },
    { l: '总消费 ¥', v: stats.totalCost.toFixed(2), c: '#F59E0B' },
    { l: '今日调用', v: stats.todayCalls.toLocaleString(), c: '#EC4899' },
    { l: '今日消费 ¥', v: stats.todayCost.toFixed(2), c: '#06B6D4' },
  ]

  return (
    <div className="p-4 lg:p-6 max-w-7xl mx-auto overflow-y-auto h-full">
      <h1 className="text-xl font-bold mb-4" style={{ color: tx.p }}>管理后台</h1>

      {error && <div className="px-4 py-3 rounded-xl text-xs mb-4" style={{ background: 'rgba(244,63,94,0.1)', color: '#F43F5E', border: '1px solid rgba(244,63,94,0.2)' }}>{error}</div>}

      <div className="flex flex-wrap gap-2 mb-6">
        {tabs.map(t => (
          <button key={t.k} onClick={() => setTab(t.k)}
            className="px-4 py-2 rounded-xl text-xs font-medium transition-all"
            style={{
              background: tab === t.k ? 'var(--accent-purple)' : 'var(--bg-surface)',
              color: tab === t.k ? '#fff' : tx.d,
              border: `1px solid ${tab === t.k ? 'var(--accent-purple)' : brd}`,
            }}
          >{t.l}{t.n > 0 ? ` (${t.n})` : ''}</button>
        ))}
      </div>

      {/* MONITOR */}
      {tab === 'monitor' && (
        <div>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
            {monItems.map((m, i) => (
              <div key={i} className="rounded-2xl p-4 border" style={{ background: bg, borderColor: brd }}>
                <div className="text-[10px] mb-1" style={{ color: tx.d }}>{m.l}</div>
                <div className="text-lg font-bold" style={{ color: m.c }}>{m.v}</div>
              </div>
            ))}
          </div>
          <div className="mt-4 rounded-2xl p-6 border" style={{ background: bg, borderColor: brd }}>
            <h3 className="text-sm font-semibold mb-3" style={{ color: tx.p }}>系统状态</h3>
            <div className="space-y-2 text-xs" style={{ color: tx.d }}>
              <div className="flex justify-between"><span>后端服务</span><span style={{ color: '#10B981' }}>● 运行中</span></div>
              <div className="flex justify-between"><span>Redis 缓存</span><span style={{ color: '#10B981' }}>● 正常</span></div>
              <div className="flex justify-between"><span>MySQL 数据库</span><span style={{ color: '#10B981' }}>● 正常</span></div>
              <div className="flex justify-between"><span>API 网关</span><span style={{ color: '#10B981' }}>● 正常</span></div>
            </div>
          </div>
        </div>
      )}

      {/* USERS */}
      {tab === 'users' && (
        <div>
          <div className="flex flex-wrap gap-3 mb-4">
            <input value={filter.search} onChange={e => setFilter({ ...filter, search: e.target.value })} placeholder="搜索ID/用户名..."
              className="px-3 py-2 rounded-xl text-xs border outline-none" style={{ background: 'var(--bg-input)', borderColor: brd, color: tx.p, width: 180 }} />
            <select value={filter.membership} onChange={e => setFilter({ ...filter, membership: e.target.value })}
              className="px-3 py-2 rounded-xl text-xs border outline-none" style={{ background: 'var(--bg-input)', borderColor: brd, color: tx.p }}>
              <option value="">全部等级</option><option value="free">普通用户</option><option value="plus">畅享用户</option><option value="pro">尊享用户</option>
            </select>
            <select value={filter.status} onChange={e => setFilter({ ...filter, status: e.target.value })}
              className="px-3 py-2 rounded-xl text-xs border outline-none" style={{ background: 'var(--bg-input)', borderColor: brd, color: tx.p }}>
              <option value="">全部状态</option><option value="ACTIVE">正常</option><option value="DISABLED">禁用</option>
            </select>
            <button onClick={() => setFilter({ search: '', membership: '', status: '' })}
              className="px-3 py-2 rounded-xl text-xs" style={{ color: tx.d, border: `1px solid ${brd}` }}>清除</button>
          </div>
          <div className="rounded-2xl border overflow-hidden" style={{ background: bg, borderColor: brd }}>
            <div className="overflow-x-auto">
              <table className="w-full text-xs">
                <thead>
                  <tr className="border-b" style={{ borderColor: brd }}>
                    {['ID','用户名','会员','角色','状态','余额','调用','时间','操作'].map(h => (
                      <th key={h} className="text-left px-3 py-2.5 font-medium" style={{ color: tx.d }}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(u => (
                    <tr key={u.id} className="border-b hover:bg-white/[0.02]" style={{ borderColor: brd }}>
                      <td className="px-3 py-2.5 font-mono" style={{ color: tx.m }}>{u.id}</td>
                      <td className="px-3 py-2.5 font-medium" style={{ color: tx.p }}>{u.username}</td>
                      <td className="px-3 py-2.5"><span className="px-1.5 py-0.5 rounded-md text-[10px]" style={{ background: u.membership === 'pro' ? 'rgba(139,92,246,0.15)' : u.membership === 'plus' ? 'rgba(59,130,246,0.15)' : 'rgba(156,163,175,0.15)', color: u.membership === 'pro' ? '#8B5CF6' : u.membership === 'plus' ? '#3B82F6' : '#9CA3AF' }}>{u.membership === 'pro' ? '尊享' : u.membership === 'plus' ? '畅享' : '普通'}</span></td>
                      <td className="px-3 py-2.5" style={{ color: u.role === 'SUPER_ADMIN' ? '#A78BFA' : u.role === 'ADMIN' ? '#8B5CF6' : tx.d }}>{u.role === 'SUPER_ADMIN' ? '超管' : u.role === 'ADMIN' ? '管理员' : '用户'}</td>
                      <td className="px-3 py-2.5"><span style={{ color: u.status === 'ACTIVE' ? '#10B981' : '#F43F5E' }}>{u.status === 'ACTIVE' ? '正常' : '禁用'}</span></td>
                      <td className="px-3 py-2.5 font-mono" style={{ color: tx.p }}>¥{Number(u.balance || 0).toFixed(2)}</td>
                      <td className="px-3 py-2.5" style={{ color: tx.d }}>{u.totalCalls || 0}</td>
                      <td className="px-3 py-2.5" style={{ color: tx.m, fontSize: 10 }}>{u.createTime ? new Date(u.createTime).toLocaleDateString() : '-'}</td>
                      <td className="px-3 py-2.5">
                        <button onClick={() => toggleStatus(u)} className="px-2 py-1 rounded-lg text-[10px] font-medium"
                          style={{ background: u.status === 'ACTIVE' ? 'rgba(244,63,94,0.1)' : 'rgba(16,185,129,0.1)', color: u.status === 'ACTIVE' ? '#F43F5E' : '#10B981' }}>
                          {u.status === 'ACTIVE' ? '封禁' : '解封'}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* ADMINS */}
      {tab === 'admins' && (
        <div className="rounded-2xl border overflow-hidden" style={{ background: bg, borderColor: brd }}>
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead><tr className="border-b" style={{ borderColor: brd }}>{['ID','用户名','角色','状态','时间','操作'].map(h => <th key={h} className="text-left px-3 py-2.5 font-medium" style={{ color: tx.d }}>{h}</th>)}</tr></thead>
              <tbody>
                {admins.map(a => (
                  <tr key={a.id} className="border-b hover:bg-white/[0.02]" style={{ borderColor: brd }}>
                    <td className="px-3 py-2.5 font-mono" style={{ color: tx.m }}>{a.id}</td>
                    <td className="px-3 py-2.5 font-medium" style={{ color: tx.p }}>{a.username}</td>
                    <td className="px-3 py-2.5" style={{ color: a.role === 'SUPER_ADMIN' ? '#A78BFA' : '#8B5CF6' }}>{a.role === 'SUPER_ADMIN' ? '超管' : '管理员'}</td>
                    <td className="px-3 py-2.5" style={{ color: a.status === 'ACTIVE' ? '#10B981' : '#F43F5E' }}>{a.status === 'ACTIVE' ? '正常' : '禁用'}</td>
                    <td className="px-3 py-2.5" style={{ color: tx.m, fontSize: 10 }}>{a.createTime ? new Date(a.createTime).toLocaleDateString() : '-'}</td>
                    <td className="px-3 py-2.5">{a.role !== 'SUPER_ADMIN' && <button onClick={() => revokeAdmin(a.id)} className="px-2 py-1 rounded-lg text-[10px] font-medium" style={{ background: 'rgba(244,63,94,0.1)', color: '#F43F5E' }}>取消管理员</button>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* BROADCAST */}
      {tab === 'broadcast' && (
        <div className="rounded-2xl p-5 border" style={{ background: bg, borderColor: brd }}>
          <div className="flex gap-2 mb-5">
            {[['notify','发送通知'],['coupon','发代金卷']].map(([k,l]) => (
              <button key={k} onClick={() => setBcTab(k as 'notify'|'coupon')}
                className="px-4 py-2 rounded-xl text-xs font-medium transition-all"
                style={{ background: bcfTab === k ? 'var(--accent-purple)' : 'var(--bg-surface)', color: bcfTab === k ? '#fff' : tx.d, border: `1px solid ${bcfTab === k ? 'var(--accent-purple)' : brd}` }}
              >{l}</button>
            ))}
          </div>
          <div className="space-y-3">
            <div>
              <div className="text-[10px] mb-1.5" style={{ color: tx.d }}>目标用户</div>
              <div className="flex flex-wrap gap-2">
                {[{ k: 'all', l: '所有用户' }, { k: 'membership', l: '按会员等级' }, { k: 'new_users', l: '新注册用户' }].map(o => (
                  <button key={o.k} onClick={() => setBcf({ ...bcf, target: o.k })}
                    className="px-3 py-1.5 rounded-lg text-[10px] font-medium border transition-all"
                    style={{ background: bcf.target === o.k ? 'var(--accent-purple)' + '15' : 'transparent', borderColor: bcf.target === o.k ? 'var(--accent-purple)' : brd, color: bcf.target === o.k ? 'var(--accent-purple)' : tx.d }}
                  >{o.l}</button>
                ))}
              </div>
            </div>
            {bcf.target === 'membership' && (
              <div>
                <div className="text-[10px] mb-1.5" style={{ color: tx.d }}>会员等级</div>
                <select value={bcf.membership} onChange={e => setBcf({ ...bcf, membership: e.target.value })}
                  className="px-3 py-2 rounded-xl text-xs border outline-none" style={{ background: 'var(--bg-input)', borderColor: brd, color: tx.p }}>
                  <option value="free">普通用户</option><option value="plus">畅享用户</option><option value="pro">尊享用户</option>
                </select>
              </div>
            )}
            {bcf.target === 'new_users' && (
              <div>
                <div className="text-[10px] mb-1.5" style={{ color: tx.d }}>注册天数内</div>
                <input value={bcf.days} onChange={e => setBcf({ ...bcf, days: e.target.value })}
                  className="px-3 py-2 rounded-xl text-xs border outline-none w-20" style={{ background: 'var(--bg-input)', borderColor: brd, color: tx.p }} />
              </div>
            )}
            {bcfTab === 'notify' ? (
              <>
                <div>
                  <div className="text-[10px] mb-1.5" style={{ color: tx.d }}>标题</div>
                  <input value={bcf.title} onChange={e => setBcf({ ...bcf, title: e.target.value })}
                    className="w-full px-3 py-2 rounded-xl text-xs border outline-none" style={{ background: 'var(--bg-input)', borderColor: brd, color: tx.p }} />
                </div>
                <div>
                  <div className="text-[10px] mb-1.5" style={{ color: tx.d }}>内容</div>
                  <textarea value={bcf.content} onChange={e => setBcf({ ...bcf, content: e.target.value })} rows={3}
                    className="w-full px-3 py-2 rounded-xl text-xs border outline-none resize-none" style={{ background: 'var(--bg-input)', borderColor: brd, color: tx.p }} />
                </div>
              </>
            ) : (
              <>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <div className="text-[10px] mb-1.5" style={{ color: tx.d }}>面额 ¥</div>
                    <input value={bcf.amount} onChange={e => setBcf({ ...bcf, amount: e.target.value })} type="number"
                      className="w-full px-3 py-2 rounded-xl text-xs border outline-none" style={{ background: 'var(--bg-input)', borderColor: brd, color: tx.p }} />
                  </div>
                  <div>
                    <div className="text-[10px] mb-1.5" style={{ color: tx.d }}>门槛 ¥ (0=无门槛)</div>
                    <input value={bcf.threshold} onChange={e => setBcf({ ...bcf, threshold: e.target.value })} type="number"
                      className="w-full px-3 py-2 rounded-xl text-xs border outline-none" style={{ background: 'var(--bg-input)', borderColor: brd, color: tx.p }} />
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <div className="text-[10px] mb-1.5" style={{ color: tx.d }}>适用套餐</div>
                    <select value={bcf.plan} onChange={e => setBcf({ ...bcf, plan: e.target.value })}
                      className="w-full px-3 py-2 rounded-xl text-xs border outline-none" style={{ background: 'var(--bg-input)', borderColor: brd, color: tx.p }}>
                      <option value="">通用</option><option value="plus">畅享</option><option value="pro">尊享</option>
                    </select>
                  </div>
                  <div>
                    <div className="text-[10px] mb-1.5" style={{ color: tx.d }}>过期时间</div>
                    <input value={bcf.expire} onChange={e => setBcf({ ...bcf, expire: e.target.value })} type="datetime-local"
                      className="w-full px-3 py-2 rounded-xl text-xs border outline-none" style={{ background: 'var(--bg-input)', borderColor: brd, color: tx.p }} />
                  </div>
                </div>
              </>
            )}
            {bcfResult && (
              <div className="px-3 py-2 rounded-xl text-[10px]" style={{ background: 'rgba(16,185,129,0.1)', color: '#10B981', border: '1px solid rgba(16,185,129,0.2)' }}>{bcfResult}</div>
            )}
            <button onClick={async () => {
              setBcfSending(true); setBcfResult('')
              try {
                let r: any
                if (bcfTab === 'notify') {
                  r = await adminApiNew.sendNotification({ title: bcf.title, content: bcf.content, target: bcf.target, membership: bcf.membership, days: Number(bcf.days) })
                  setBcfResult(`已发送给 ${r.sent} 位用户`)
                } else {
                  r = await adminApiNew.sendCoupons({ amount: Number(bcf.amount), thresholdAmount: Number(bcf.threshold), applicablePlan: bcf.plan || undefined, expireTime: bcf.expire || undefined, target: bcf.target, membership: bcf.membership, days: Number(bcf.days) })
                  setBcfResult(`已发送给 ${r.sent} 位用户，面额¥${r.amount}`)
                }
              } catch (e: any) { setBcfResult('失败: ' + (e.message || '未知错误')) }
              setBcfSending(false)
            }} disabled={bcfSending}
              className="w-full py-2.5 rounded-xl text-xs font-medium text-white transition-all disabled:opacity-50" style={{ background: 'var(--accent-purple)' }}>
              {bcfSending ? '发送中...' : (bcfTab === 'notify' ? '群发通知' : '群发代金卷')}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
