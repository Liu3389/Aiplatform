import { useState, useEffect, useRef, useCallback } from 'react'
import { adminApi, adminNotificationApi, adminCouponApi } from '@/services/api'
import { useThemeStore } from '@/stores/themeStore'
import * as echarts from 'echarts'
import PointsIcon from '@/components/PointsIcon'

type ECharts = echarts.ECharts
type EChartsOption = echarts.EChartsOption

interface AdminUser {
  id: number; username: string; email: string | null; balance: number
  role: string; status: string; membership: string
  apiKeyCount: number; totalCalls: number; totalCost: number; createTime: string
}
interface AdminInfo {
  id: number; username: string; email: string | null; role: string; status: string; createTime: string
}
interface AnalyticsData {
  totalUsers: number; activeUsers: number; totalApiKeys: number
  totalCalls: number; totalCost: number; todayCalls: number; todayCost: number
  users: { id: number; username: string; email: string | null; balance: number; role: string; status: string; apiKeyCount: number; totalCalls: number; totalCost: number; createTime: string }[]
  dailyUsage: Record<string, unknown>[]
  modelUsage: Record<string, unknown>[]
  hourlyTraffic: Record<string, unknown>[]
  topUsers: Record<string, unknown>[]
  revenueTrend: Record<string, unknown>[]
  userGrowth: Record<string, unknown>[]
}

const COLORS = ['#8B5CF6', '#3B82F6', '#06B6D4', '#10B981', '#F59E0B', '#EC4899', '#A78BFA', '#6366F1']

function chartDark(isDark: boolean) {
  return {
    textColor: isDark ? '#94A3B8' : '#64748B',
    splitLine: isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)',
    axisLine: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)',
  }
}

export default function AdminPage() {
  const { theme } = useThemeStore()
  const dark = theme === 'dark'
  const isDark = dark
  const ct = chartDark(isDark)
  const tx = dark ? { p:'#fff', d:'#9ca3af', m:'#6b7280' } : { p:'#333', d:'#666', m:'#999' }
  const bg = 'var(--bg-card)'
  const brd = dark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'

  const [users, setUsers] = useState<AdminUser[]>([])
  const [admins, setAdmins] = useState<AdminInfo[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [tab, setTab] = useState<'monitor' | 'users' | 'admins' | 'broadcast'>('monitor')
  const [filter, setFilter] = useState({ search: '', membership: '', status: '' })
  const [page, setPage] = useState(1); const [totalUsers, setTotalUsers] = useState(0)
  const [pageSize, setPageSizeState] = useState(20)
  const [analytics, setAnalytics] = useState<AnalyticsData | null>(null)
  const [timeRange, setTimeRange] = useState({ days: 7, months: 6 })
  const [bcfTab, setBcTab] = useState<'notify'|'coupon'>('notify')
  const [bcf, setBcf] = useState({ title:'', content:'', target:'all', membership:'free', days:'7', amount:'10', threshold:'0', plan:'', expire:'' })
  const [bcfSending, setBcfSending] = useState(false); const [bcfResult, setBcfResult] = useState('')


  const dailyChartRef = useRef<HTMLDivElement>(null)
  const revenueChartRef = useRef<HTMLDivElement>(null)
  const growthChartRef = useRef<HTMLDivElement>(null)
  const hourlyChartRef = useRef<HTMLDivElement>(null)
  const modelChartRef = useRef<HTMLDivElement>(null)
  const chartInst = useRef<{ daily?:ECharts; revenue?:ECharts; growth?:ECharts; hourly?:ECharts; model?:ECharts }>({})

  const loadUsers = useCallback(async () => {
    try {
      const [ulRes, al] = await Promise.all([
        adminApi.getUsers({ page, size: pageSize, search: filter.search, membership: filter.membership, status: filter.status }),
        adminApi.getAdmins().catch(() => [] as AdminInfo[])
      ])
      const r = ulRes as unknown as { records: AdminUser[]; total: number }
      setUsers(r.records); setTotalUsers(r.total); setAdmins(al as AdminInfo[])
    } catch (e) { setError((e as Error).message || '加载失败') }
  }, [page, pageSize, filter])

  const loadAnalytics = useCallback(async () => {
    try {
      const a = await adminApi.getAnalytics(timeRange.days, timeRange.months).catch(() => null)
      if (a) setAnalytics(a as unknown as AnalyticsData)
    } catch {}
  }, [timeRange])

  useEffect(() => { setLoading(true); Promise.all([loadUsers(), loadAnalytics()]).finally(() => setLoading(false)) }, [loadUsers, loadAnalytics])
  useEffect(() => { setPage(1) }, [filter])

  const toggleStatus = async (u: AdminUser) => {
    try { const s = u.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'; await adminApi.toggleUserStatus(u.id, s as 'ACTIVE'|'DISABLED'); setUsers(prev => prev.map(x => x.id===u.id ? {...x, status:s} : x)) }
    catch (e) { setError((e as Error).message) }
  }
  const revokeAdmin = async (id: number) => {
    try { await adminApi.revokeAdminRole(id); setAdmins(prev => prev.filter(a => a.id!==id)) }
    catch (e) { setError((e as Error).message) }
  }
  const doBroadcast = async () => {
    setBcfSending(true)
    try {
      let r: any
      if (bcfTab === 'notify') r = await adminNotificationApi.send({ title: bcf.title, content: bcf.content, target: bcf.target, membership: bcf.membership, days: Number(bcf.days) })
      else r = await adminCouponApi.send({ amount: Number(bcf.amount), thresholdAmount: Number(bcf.threshold), applicablePlan: bcf.plan, expireTime: bcf.expire, target: bcf.target, membership: bcf.membership, days: Number(bcf.days) })
      setBcfResult(bcfTab==='notify' ? `已发送给 ${r.sent} 位用户` : `已发送给 ${r.sent} 位用户，面额¥${r.amount}`)
    } catch (e) { setBcfResult('发送失败: ' + (e as Error).message) }
    setBcfSending(false)
  }

  const filtered = users.filter(u => {
    if (filter.search && !u.username.includes(filter.search) && String(u.id) !== filter.search) return false
    if (filter.membership && u.membership !== filter.membership) return false
    if (filter.status && u.status !== filter.status) return false
    return true
  })

  useEffect(() => {
    if (!analytics || tab !== 'monitor') return
    const daily = analytics.dailyUsage || []
    const rev = analytics.revenueTrend || []
    const grow = analytics.userGrowth || []
    const hourly = analytics.hourlyTraffic || []
    const models = analytics.modelUsage || []

    const makeLine = (data: any[], xKey: string, yKeys: {key:string;name:string;color:string}[], area?:boolean): EChartsOption => ({
      backgroundColor:'transparent', grid:{ top:10, right:20, bottom:30, left:50 },
      tooltip:{ trigger:'axis', backgroundColor: isDark?'rgba(17,24,39,0.95)':'rgba(255,255,255,0.95)', borderColor: ct.splitLine, textStyle:{ color:ct.textColor, fontSize:11 } },
      xAxis:{ type:'category', data: data.map(d=>d[xKey]), axisLine:{ lineStyle:{ color:ct.axisLine } }, axisTick:{ show:false }, axisLabel:{ color:ct.textColor, fontSize:9 } },
      yAxis:{ type:'value', splitLine:{ lineStyle:{ color:ct.splitLine } }, axisLabel:{ color:ct.textColor, fontSize:9 } },
      series: yKeys.map(y => ({
        name:y.name, type:'line', data: data.map(d=>d[y.key]),
        smooth:true, symbol:'none', lineStyle:{ color:y.color, width:2 },
        ...(area ? { areaStyle:{ color: new echarts.graphic.LinearGradient(0,0,0,1,[{offset:0,color:y.color+'40'},{offset:1,color:y.color+'05'}]) } } : {}),
      })),
      legend: { bottom:0, textStyle:{ color:ct.textColor, fontSize:9 }, itemWidth:10, itemHeight:6 },
    })

    const makeBar = (data: any[], xKey: string, yKeys: {key:string;name:string;color:string}[]): EChartsOption => ({
      backgroundColor:'transparent', grid:{ top:10, right:20, bottom:30, left:50 },
      tooltip:{ trigger:'axis', backgroundColor: isDark?'rgba(17,24,39,0.95)':'rgba(255,255,255,0.95)', borderColor: ct.splitLine, textStyle:{ color:ct.textColor, fontSize:11 } },
      xAxis:{ type:'category', data: data.map(d=>d[xKey]), axisLine:{ lineStyle:{ color:ct.axisLine } }, axisTick:{ show:false }, axisLabel:{ color:ct.textColor, fontSize:9 } },
      yAxis:{ type:'value', splitLine:{ lineStyle:{ color:ct.splitLine } }, axisLabel:{ color:ct.textColor, fontSize:9 } },
      series: yKeys.map((y,i) => ({
        name:y.name, type:'bar', data: data.map(d=>d[y.key]),
        itemStyle:{ borderRadius:[4,4,0,0], color: new echarts.graphic.LinearGradient(0,0,0,1,[{offset:0,color:y.color},{offset:1,color:y.color+'50'}]) },
        barGap:'20%',
      })),
      legend: { bottom:0, textStyle:{ color:ct.textColor, fontSize:9 }, itemWidth:10, itemHeight:6 },
    })

    const makeModelPie = (): EChartsOption => ({
      backgroundColor:'transparent',
      tooltip:{ trigger:'item', backgroundColor: isDark?'rgba(17,24,39,0.95)':'rgba(255,255,255,0.95)', borderColor: ct.splitLine, textStyle:{ color:ct.textColor, fontSize:11 } },
      series:[{ type:'pie', radius:['55%','78%'], center:['50%','50%'], avoidLabelOverlap:false,
        itemStyle:{ borderRadius:6, borderColor: isDark?'#0f172a':'#fff', borderWidth:2 },
        label:{ show:false }, emphasis:{ label:{ show:true, fontSize:12, fontWeight:'bold' } },
        data: models.map(m => ({ name:m.model, value:m.calls, itemStyle:{ color:m.color || COLORS[models.indexOf(m)%COLORS.length] } })),
      }],
    })

    const initChart = (el: HTMLDivElement|null, name: string, opt: EChartsOption) => {
      if (!el) return
      if ((chartInst.current as any)[name]) (chartInst.current as any)[name].dispose()
      const c = echarts.init(el, isDark ? 'dark' : undefined)
      c.setOption(opt)
      ;(chartInst.current as any)[name] = c
    }

    initChart(dailyChartRef.current, 'daily', makeLine(daily, 'date', [
      {key:'tokens', name:'Token', color:COLORS[1]}, {key:'calls', name:'调用', color:COLORS[0]}
    ], true))
    initChart(revenueChartRef.current, 'revenue', makeBar(rev, 'month', [
      {key:'revenue', name:'营收 ¥', color:COLORS[4]}, {key:'costs', name:'成本 ¥', color:COLORS[5]}
    ]))
    initChart(growthChartRef.current, 'growth', makeLine(grow, 'month', [
      {key:'total', name:'总用户', color:COLORS[0]}, {key:'active', name:'活跃', color:COLORS[3]}, {key:'new', name:'新增', color:COLORS[6]}
    ], false))
    initChart(hourlyChartRef.current, 'hourly', makeBar(hourly, 'hour', [
      {key:'calls', name:'调用量', color:COLORS[2]}
    ]))
    initChart(modelChartRef.current, 'model', makeModelPie())

    const h = () => { Object.values(chartInst.current).forEach((c:any) => c?.resize?.()) }
    window.addEventListener('resize', h)
    return () => { window.removeEventListener('resize', h); Object.values(chartInst.current).forEach((c:any) => c?.dispose?.()) }
  }, [analytics, tab, isDark])

  if (loading && !analytics) {
    return <div className="flex items-center justify-center h-full"><div className="w-8 h-8 border-2 border-brand-purple/30 border-t-brand-purple rounded-full animate-spin" /></div>
  }

  const tabs = [
    { k:'monitor' as const, l:'数据大屏', n:0 },
    { k:'users' as const, l:'用户管理', n:totalUsers },
    { k:'admins' as const, l:'管理员', n:admins.length },
    { k:'broadcast' as const, l:'消息广播', n:0 },
  ]

  const a = analytics
  const kpiCards = a ? [
    { l:'总用户', v:a.totalUsers, c:COLORS[0] },
    { l:'活跃用户', v:a.activeUsers, c:COLORS[3] },
    { l:'总API Keys', v:a.totalApiKeys, c:COLORS[2] },
    { l:'总调用', v:a.totalCalls.toLocaleString(), c:COLORS[1] },
    { l:'总营收 ¥', v:(a.totalCost||0).toFixed(2), c:COLORS[4] },
    { l:'今日调用', v:a.todayCalls.toLocaleString(), c:COLORS[5] },
    { l:'今日营收 ¥', v:(a.todayCost||0).toFixed(2), c:COLORS[6] },
    { l:'API Key数', v:a.totalApiKeys, c:COLORS[7] },
  ] : []

  return (
    <div className="p-4 lg:p-6 max-w-7xl mx-auto overflow-y-auto h-full">
      <h1 className="text-xl font-bold mb-4" style={{ color: tx.p }}>管理员后台</h1>
      {error && <div className="px-4 py-3 rounded-xl text-xs mb-4" style={{ background:'rgba(244,63,94,0.1)', color:'#F43F5E', border:'1px solid rgba(244,63,94,0.2)' }}>{error}</div>}

      <div className="flex flex-wrap gap-2 mb-6">
        {tabs.map(t => (
          <button key={t.k} onClick={() => setTab(t.k)}
            className="px-4 py-2 rounded-xl text-xs font-medium transition-all"
            style={{ background: tab===t.k ? 'var(--accent-purple)' : 'var(--bg-surface)', color: tab===t.k ? '#fff' : tx.d, border: `1px solid ${tab===t.k ? 'var(--accent-purple)' : brd}` }}>
            {t.l}{t.n>0 ? ` (${t.n})` : ''}
          </button>
        ))}
      </div>

      {/* ====================== MONITOR / 数据大屏 ====================== */}
      {tab === 'monitor' && a && (
        <div className="space-y-4">
          {/* Time Range */}
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-[10px]" style={{ color: tx.d }}>时间范围:</span>
            {[{ d:7, m:6, l:'7天/6月' }, { d:30, m:12, l:'30天/12月' }, { d:90, m:24, l:'90天/24月' }].map(r => (
              <button key={r.d} onClick={() => { setTimeRange({ days:r.d, months:r.m }); loadAnalytics() }}
                className="px-3 py-1 rounded-lg text-[10px] font-medium border transition-all"
                style={{ background: timeRange.days===r.d ? 'var(--accent-purple)' : 'transparent', borderColor: timeRange.days===r.d ? 'var(--accent-purple)' : brd, color: timeRange.days===r.d ? '#fff' : tx.d }}>
                {r.l}
              </button>
            ))}
          </div>

          {/* KPI Cards */}
          <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-8 gap-2">
            {kpiCards.map((m,i) => (
              <div key={i} className="rounded-xl p-3 border" style={{ background: bg, borderColor: brd }}>
                <div className="text-[10px] mb-1" style={{ color: tx.d }}>{m.l}</div>
                <div className="text-sm font-bold" style={{ color: m.c }}>{m.v}</div>
              </div>
            ))}
          </div>

          {/* System Status */}
          <div className="rounded-2xl p-5 border" style={{ background: bg, borderColor: brd }}>
            <h3 className="text-xs font-semibold mb-3" style={{ color: tx.p }}>系统状态</h3>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs" style={{ color: tx.d }}>
              {[{k:'后端服务',s:'运行中'},{k:'Redis缓存',s:'正常'},{k:'MySQL数据库',s:'正常'},{k:'API网关',s:'正常'}].map(x => (
                <div key={x.k} className="flex justify-between px-3 py-2 rounded-lg" style={{ background: 'var(--bg-surface)' }}>
                  <span>{x.k}</span><span style={{ color:'#10B981' }}>● {x.s}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Charts Grid */}
          <div className="grid lg:grid-cols-2 gap-4">
            <div className="rounded-2xl p-4 border" style={{ background: bg, borderColor: brd }}>
              <h3 className="text-xs font-semibold mb-2" style={{ color: tx.p }}>调用量与Token趋势</h3>
              <div ref={dailyChartRef} style={{ height: 260 }} />
            </div>
            <div className="rounded-2xl p-4 border" style={{ background: bg, borderColor: brd }}>
              <h3 className="text-xs font-semibold mb-2" style={{ color: tx.p }}>营收与成本对比</h3>
              <div ref={revenueChartRef} style={{ height: 260 }} />
            </div>
            <div className="rounded-2xl p-4 border" style={{ background: bg, borderColor: brd }}>
              <h3 className="text-xs font-semibold mb-2" style={{ color: tx.p }}>用户增长趋势</h3>
              <div ref={growthChartRef} style={{ height: 260 }} />
            </div>
            <div className="rounded-2xl p-4 border" style={{ background: bg, borderColor: brd }}>
              <h3 className="text-xs font-semibold mb-2" style={{ color: tx.p }}>24小时流量分布</h3>
              <div ref={hourlyChartRef} style={{ height: 260 }} />
            </div>
          </div>

          {/* Model Distribution + Top Users */}
          <div className="grid lg:grid-cols-2 gap-4">
            <div className="rounded-2xl p-4 border" style={{ background: bg, borderColor: brd }}>
              <h3 className="text-xs font-semibold mb-2" style={{ color: tx.p }}>模型使用分布</h3>
              <div ref={modelChartRef} style={{ height: 280 }} />
            </div>
            <div className="rounded-2xl p-4 border" style={{ background: bg, borderColor: brd }}>
              <h3 className="text-xs font-semibold mb-3" style={{ color: tx.p }}>用户排行榜 TOP 10</h3>
              {a.topUsers && a.topUsers.length > 0 ? (
                <div className="space-y-2">
                  {a.topUsers.slice(0,10).map((u:any, i:number) => (
                    <div key={i} className="flex items-center gap-3 px-3 py-2 rounded-lg" style={{ background: 'var(--bg-surface)' }}>
                      <span className="w-6 h-6 rounded-full flex items-center justify-center text-[10px] font-bold text-white" style={{ background: u.rank<=3 ? COLORS[u.rank-1] : tx.m }}>{u.rank}</span>
                      <span className="flex-1 text-xs font-medium" style={{ color: tx.p }}>{u.username}</span>
                      <span className="text-[10px]" style={{ color: tx.d }}>{u.calls} 次</span>
                      <span className="text-[10px] font-mono" style={{ color: COLORS[4] }}>¥{Number(u.cost||0).toFixed(2)}</span>
                    </div>
                  ))}
                </div>
              ) : <p className="text-xs" style={{ color: tx.d }}>暂无数据</p>}
            </div>
          </div>
        </div>
      )}

      {/* ====================== USERS ====================== */}
      {tab === 'users' && (
        <div>
          <div className="flex flex-wrap gap-3 mb-4">
            <input value={filter.search} onChange={e => setFilter({...filter, search:e.target.value})} placeholder="搜索ID/用户名..."
              className="px-3 py-2 rounded-xl text-xs border outline-none" style={{ background:'var(--bg-input)', borderColor:brd, color:tx.p, width:180 }} />
            <select value={filter.membership} onChange={e => setFilter({...filter, membership:e.target.value})}
              className="px-3 py-2 rounded-xl text-xs border outline-none" style={{ background:'var(--bg-input)', borderColor:brd, color:tx.p }}>
              <option value="">全部等级</option><option value="free">普通用户</option><option value="plus">畅享用户</option><option value="pro">尊享用户</option>
            </select>
            <select value={filter.status} onChange={e => setFilter({...filter, status:e.target.value})}
              className="px-3 py-2 rounded-xl text-xs border outline-none" style={{ background:'var(--bg-input)', borderColor:brd, color:tx.p }}>
              <option value="">全部状态</option><option value="ACTIVE">正常</option><option value="DISABLED">禁用</option>
            </select>
            <button onClick={() => setFilter({search:'',membership:'',status:''})}
              className="px-3 py-2 rounded-xl text-xs" style={{ color:tx.d, border:`1px solid ${brd}` }}>清除</button>
          </div>
          <div className="rounded-2xl border overflow-hidden" style={{ background:bg, borderColor:brd }}>
            <div className="overflow-x-auto">
              <table className="w-full text-xs">
                <thead><tr className="border-b" style={{ borderColor:brd }}>
                  {['ID','用户名','会员','角色','状态','余额 ¥','调用','时间','操作'].map(h => (
                    <th key={h} className="text-left px-3 py-2.5 font-medium" style={{ color:tx.d }}>{h}</th>
                  ))}
                </tr></thead>
                <tbody>
                  {filtered.map(u => (
                    <tr key={u.id} className="border-b hover:bg-white/[0.02]" style={{ borderColor:brd }}>
                      <td className="px-3 py-2.5 font-mono" style={{ color:tx.m }}>{u.id}</td>
                      <td className="px-3 py-2.5 font-medium" style={{ color:tx.p }}>{u.username}</td>
                      <td className="px-3 py-2.5"><span className="px-1.5 py-0.5 rounded-md text-[10px]" style={{ background: u.membership==='pro'?'rgba(139,92,246,0.15)':u.membership==='plus'?'rgba(59,130,246,0.15)':'rgba(156,163,175,0.15)', color: u.membership==='pro'?'#8B5CF6':u.membership==='plus'?'#3B82F6':'#9CA3AF' }}>{u.membership==='pro'?'尊享':u.membership==='plus'?'畅享':'普通'}</span></td>
                      <td className="px-3 py-2.5" style={{ color: u.role==='SUPER_ADMIN'?'#A78BFA':u.role==='ADMIN'?'#8B5CF6':tx.d }}>{u.role==='SUPER_ADMIN'?'超管':u.role==='ADMIN'?'管理员':'用户'}</td>
                      <td className="px-3 py-2.5"><span style={{ color: u.status==='ACTIVE'?'#10B981':'#F43F5E' }}>{u.status==='ACTIVE'?'正常':'禁用'}</span></td>
                      <td className="px-3 py-2.5 font-mono" style={{ color:tx.p }}>¥{Number(u.balance||0).toFixed(2)}</td>
                      <td className="px-3 py-2.5" style={{ color:tx.d }}>{u.totalCalls||0}</td>
                      <td className="px-3 py-2.5" style={{ color:tx.m, fontSize:10 }}>{u.createTime?new Date(u.createTime).toLocaleDateString():'-'}</td>
                      <td className="px-3 py-2.5">
                        <button onClick={() => toggleStatus(u)} className="px-2 py-1 rounded-lg text-[10px] font-medium"
                          style={{ background: u.status==='ACTIVE'?'rgba(244,63,94,0.1)':'rgba(16,185,129,0.1)', color: u.status==='ACTIVE'?'#F43F5E':'#10B981' }}>
                          {u.status==='ACTIVE'?'封禁':'解封'}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          {totalUsers > 0 && (
            <div className="flex items-center justify-between mt-3 text-xs flex-wrap gap-3">
              <div className="flex items-center gap-2">
                <span style={{ color:tx.d }}>每页</span>
                {[10,20,50].map(n => (
                  <button key={n} onClick={() => { setPageSizeState(n); setPage(1) }}
                    className="px-2 py-1 rounded-md border transition-all"
                    style={{ background: pageSize===n?'var(--accent-purple)':'transparent', borderColor: pageSize===n?'var(--accent-purple)':brd, color: pageSize===n?'#fff':tx.d }}>{n}</button>
                ))}
                <span style={{ color:tx.d }}>条</span>
              </div>
              <span style={{ color:tx.d }}>共 {totalUsers} 条，第 {page} / {Math.ceil(totalUsers/pageSize)} 页</span>
              <div className="flex gap-2">
                <button onClick={() => setPage(p=>Math.max(1,p-1))} disabled={page<=1}
                  className="px-3 py-1.5 rounded-lg border transition-all disabled:opacity-30" style={{ borderColor:brd, color:tx.d }}>上一页</button>
                <button onClick={() => setPage(p=>p+1)} disabled={page*pageSize>=totalUsers}
                  className="px-3 py-1.5 rounded-lg border transition-all disabled:opacity-30" style={{ borderColor:brd, color:tx.d }}>下一页</button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ====================== ADMINS ====================== */}
      {tab==='admins' && (
        <div className="rounded-2xl border overflow-hidden" style={{ background:bg, borderColor:brd }}>
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead><tr className="border-b" style={{ borderColor:brd }}>{['ID','用户名','角色','状态','时间','操作'].map(h => <th key={h} className="text-left px-3 py-2.5 font-medium" style={{ color:tx.d }}>{h}</th>)}</tr></thead>
              <tbody>
                {admins.map(a => (
                  <tr key={a.id} className="border-b hover:bg-white/[0.02]" style={{ borderColor:brd }}>
                    <td className="px-3 py-2.5 font-mono" style={{ color:tx.m }}>{a.id}</td>
                    <td className="px-3 py-2.5 font-medium" style={{ color:tx.p }}>{a.username}</td>
                    <td className="px-3 py-2.5" style={{ color: a.role==='SUPER_ADMIN'?'#A78BFA':'#8B5CF6' }}>{a.role==='SUPER_ADMIN'?'超管':'管理员'}</td>
                    <td className="px-3 py-2.5" style={{ color: a.status==='ACTIVE'?'#10B981':'#F43F5E' }}>{a.status==='ACTIVE'?'正常':'禁用'}</td>
                    <td className="px-3 py-2.5" style={{ color:tx.m, fontSize:10 }}>{a.createTime?new Date(a.createTime).toLocaleDateString():'-'}</td>
                    <td className="px-3 py-2.5">{a.role!=='SUPER_ADMIN' && <button onClick={() => revokeAdmin(a.id)} className="px-2 py-1 rounded-lg text-[10px] font-medium" style={{ background:'rgba(244,63,94,0.1)', color:'#F43F5E' }}>取消管理员</button>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* ====================== BROADCAST ====================== */}
      {tab==='broadcast' && (
        <div className="rounded-2xl p-5 border" style={{ background:bg, borderColor:brd }}>
          <div className="flex gap-2 mb-5">
            {[['notify','发送通知'],['coupon','发代金卷']].map(([k,l]) => (
              <button key={k} onClick={() => setBcTab(k as 'notify'|'coupon')}
                className="px-4 py-2 rounded-xl text-xs font-medium transition-all"
                style={{ background: bcfTab===k?'var(--accent-purple)':'var(--bg-surface)', color: bcfTab===k?'#fff':tx.d, border:`1px solid ${bcfTab===k?'var(--accent-purple)':brd}` }}>{l}</button>
            ))}
          </div>
          <div className="space-y-3">
            <div>
              <div className="text-[10px] mb-1.5" style={{ color:tx.d }}>目标用户</div>
              <div className="flex flex-wrap gap-2">
                {[{k:'all',l:'所有用户'},{k:'membership',l:'按会员等级'},{k:'new_users',l:'新注册用户'}].map(o => (
                  <button key={o.k} onClick={() => setBcf({...bcf, target:o.k})}
                    className="px-3 py-1.5 rounded-lg text-[10px] font-medium border transition-all"
                    style={{ background: bcf.target===o.k?'var(--accent-purple)15':'transparent', borderColor: bcf.target===o.k?'var(--accent-purple)':brd, color: bcf.target===o.k?'var(--accent-purple)':tx.d }}>{o.l}</button>
                ))}
              </div>
            </div>
            {bcf.target==='membership' && (
              <div>
                <div className="text-[10px] mb-1.5" style={{ color:tx.d }}>会员等级</div>
                <select value={bcf.membership} onChange={e => setBcf({...bcf, membership:e.target.value})}
                  className="px-3 py-2 rounded-xl text-xs border outline-none" style={{ background:'var(--bg-input)', borderColor:brd, color:tx.p }}>
                  <option value="free">普通用户</option><option value="plus">畅享用户</option><option value="pro">尊享用户</option>
                </select>
              </div>
            )}
            {bcf.target==='new_users' && (
              <div>
                <div className="text-[10px] mb-1.5" style={{ color:tx.d }}>注册天数内</div>
                <input value={bcf.days} onChange={e => setBcf({...bcf, days:e.target.value})}
                  className="px-3 py-2 rounded-xl text-xs border outline-none w-20" style={{ background:'var(--bg-input)', borderColor:brd, color:tx.p }} />
              </div>
            )}
            {bcfTab==='notify' ? (<>
              <div><div className="text-[10px] mb-1.5" style={{ color:tx.d }}>标题</div>
                <input value={bcf.title} onChange={e => setBcf({...bcf, title:e.target.value})}
                  className="w-full px-3 py-2 rounded-xl text-xs border outline-none" style={{ background:'var(--bg-input)', borderColor:brd, color:tx.p }} /></div>
              <div><div className="text-[10px] mb-1.5" style={{ color:tx.d }}>内容</div>
                <textarea value={bcf.content} onChange={e => setBcf({...bcf, content:e.target.value})} rows={3}
                  className="w-full px-3 py-2 rounded-xl text-xs border outline-none resize-none" style={{ background:'var(--bg-input)', borderColor:brd, color:tx.p }} /></div>
            </>) : (<>
              <div className="grid grid-cols-2 gap-3">
                <div><div className="text-[10px] mb-1.5" style={{ color:tx.d }}>面额 ¥</div>
                  <input value={bcf.amount} onChange={e => setBcf({...bcf, amount:e.target.value})} type="number"
                    className="w-full px-3 py-2 rounded-xl text-xs border outline-none" style={{ background:'var(--bg-input)', borderColor:brd, color:tx.p }} /></div>
                <div><div className="text-[10px] mb-1.5" style={{ color:tx.d }}>门槛 ¥ (0=无门槛)</div>
                  <input value={bcf.threshold} onChange={e => setBcf({...bcf, threshold:e.target.value})} type="number"
                    className="w-full px-3 py-2 rounded-xl text-xs border outline-none" style={{ background:'var(--bg-input)', borderColor:brd, color:tx.p }} /></div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div><div className="text-[10px] mb-1.5" style={{ color:tx.d }}>适用套餐</div>
                  <select value={bcf.plan} onChange={e => setBcf({...bcf, plan:e.target.value})}
                    className="w-full px-3 py-2 rounded-xl text-xs border outline-none" style={{ background:'var(--bg-input)', borderColor:brd, color:tx.p }}>
                    <option value="">通用</option><option value="plus">畅享</option><option value="pro">尊享</option></select></div>
                <div><div className="text-[10px] mb-1.5" style={{ color:tx.d }}>过期时间</div>
                  <input value={bcf.expire} onChange={e => setBcf({...bcf, expire:e.target.value})} type="datetime-local"
                    className="w-full px-3 py-2 rounded-xl text-xs border outline-none" style={{ background:'var(--bg-input)', borderColor:brd, color:tx.p }} /></div>
              </div>
            </>)}
            <button onClick={doBroadcast} disabled={bcfSending}
              className="w-full py-2.5 rounded-xl text-xs font-medium text-white transition-all" style={{ background: bcfSending?'var(--accent-purple)50':'var(--accent-purple)' }}>
              {bcfSending ? (bcfTab==='notify'?'发送中...':'发放中...') : (bcfTab==='notify'?'发送通知':'发放代金卷')}
            </button>
            {bcfResult && <div className="text-xs px-3 py-2 rounded-lg" style={{ background: bcfResult.includes('失败')?'rgba(244,63,94,0.1)':'rgba(16,185,129,0.1)', color: bcfResult.includes('失败')?'#F43F5E':'#10B981' }}>{bcfResult}</div>}
          </div>
        </div>
      )}
  )
}
