import { useState, useEffect, useRef, useCallback, useMemo } from 'react'
import { adminApi, adminNotificationApi, adminCouponApi, adminFinanceApi, adminStatsApi, systemControlApi, adminLogApi, adminUserApi } from '@/services/api'
import type { ContainerStatus } from '@/services/api'
import { useThemeStore } from '@/stores/themeStore'
import { useAppStore } from '@/stores/appStore'
import * as echarts from 'echarts'
import PointsIcon from '@/components/PointsIcon'
import ConfirmDialog from '@/components/ConfirmDialog'
import AdminUserActionDialog, { type AdminUserAction } from '@/components/AdminUserActionDialog'
import ManualChargeReviewPanel from '@/components/admin/ManualChargeReviewPanel'
import AdminDataTable from '@/components/admin/AdminDataTable'
import AdminUserDetailDrawer from '@/components/admin/AdminUserDetailDrawer'
import UserAvatar from '@/components/UserAvatar'
import type { ColumnDef } from '@tanstack/react-table'

type ECharts = echarts.ECharts
type EChartsOption = echarts.EChartsOption

interface AdminUser {
  id: number; username: string; nickname?: string | null; displayName?: string | null; avatarUrl?: string | null
  email: string | null; phone?: string | null; balance: number; points?: number
  role: string; status: string; membership: string
  oauthProviders?: string[]; apiKeyCount: number; totalCalls: number; totalPointsConsumed: number; membershipExpireTime?: string; createTime: string
}
interface AdminInfo {
  id: number; username: string; nickname?: string | null; displayName?: string | null; avatarUrl?: string | null
  email: string | null; role: string; status: string; createTime: string
}
interface AnalyticsData {
  totalUsers: number; activeUsers: number; totalApiKeys: number
  totalCalls: number; totalPointsConsumed: number; todayCalls: number; todayPointsConsumed: number
  users: { id: number; username: string; email: string | null; balance: number; role: string; status: string; apiKeyCount: number; totalCalls: number; totalPointsConsumed: number; createTime: string }[]
  dailyUsage: Record<string, unknown>[]
  modelUsage: Record<string, unknown>[]
  hourlyTraffic: Record<string, unknown>[]
  topUsers: Record<string, unknown>[]
  revenueTrend: Record<string, unknown>[]
  userGrowth: Record<string, unknown>[]
}

interface PlatformRevenue {
  totalRevenue: number; todayRevenue: number; monthRevenue: number;
  totalRechargeAmount: number; todayRechargeAmount: number;
  totalPackageSales: number; monthPackageSales: number;
  subscriptionRevenue: number; activeSubscriptionCount: number;
  totalSubscriptionCount: number; packageSalesBreakdown: Record<string, number>;
}

const COLORS = ['#8B5CF6', '#3B82F6', '#06B6D4', '#10B981', '#F59E0B', '#EC4899', '#A78BFA', '#6366F1']

type AdminTab = 'monitor' | 'users' | 'admins' | 'broadcast' | 'finance' | 'logs' | 'system'
type AdminNavKind = 'overview' | 'users' | 'admins' | 'broadcast' | 'finance' | 'logs' | 'system' | 'refresh' | 'close'

const LOG_TYPE_LABELS: Record<string, string> = {
  USER_MANAGE: '用户管理',
  POINTS_MANAGE: '点数管理',
  BALANCE_MANAGE: '余额管理',
  MEMBERSHIP_MANAGE: '会员管理',
  NOTIFICATION_MANAGE: '通知管理',
  SYSTEM_MANAGE: '系统管理',
  STATISTICS_MANAGE: '统计管理',
}

function chartDark(isDark: boolean) {
  return {
    textColor: isDark ? '#94A3B8' : '#64748B',
    splitLine: isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)',
    axisLine: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)',
  }
}

function ControlIcon({ kind, size = 14 }: { kind: 'lock' | 'refresh' | 'play' | 'stop' | 'database' | 'cache' | 'backend' | 'frontend'; size?: number }) {
  const common = { width: size, height: size, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.8, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const, 'aria-hidden': true }
  switch (kind) {
    case 'lock': return <svg {...common}><rect x="5" y="10" width="14" height="10" rx="2" /><path d="M8 10V7a4 4 0 0 1 8 0v3" /></svg>
    case 'refresh': return <svg {...common}><path d="M20 11a8 8 0 0 0-14.7-4L4 9" /><path d="M4 4v5h5" /><path d="M4 13a8 8 0 0 0 14.7 4L20 15" /><path d="M20 20v-5h-5" /></svg>
    case 'play': return <svg {...common}><path d="m8 5 11 7-11 7V5Z" /></svg>
    case 'stop': return <svg {...common}><rect x="6" y="6" width="12" height="12" rx="2" /></svg>
    case 'database': return <svg {...common}><ellipse cx="12" cy="5" rx="7" ry="3" /><path d="M5 5v7c0 1.7 3.1 3 7 3s7-1.3 7-3V5M5 12v7c0 1.7 3.1 3 7 3s7-1.3 7-3v-7" /></svg>
    case 'cache': return <svg {...common}><path d="m4 7 8-4 8 4-8 4-8-4Z" /><path d="m4 12 8 4 8-4M4 17l8 4 8-4" /></svg>
    case 'backend': return <svg {...common}><rect x="4" y="4" width="16" height="16" rx="3" /><path d="M8 8h8M8 12h8M8 16h5" /></svg>
    default: return <svg {...common}><circle cx="12" cy="12" r="8" /><path d="M8 12h8M12 8v8" /></svg>
  }
}

function AdminNavIcon({ kind, size = 16 }: { kind: AdminNavKind; size?: number }) {
  const common = { width: size, height: size, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.8, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const, 'aria-hidden': true }
  switch (kind) {
    case 'overview': return <svg {...common}><rect x="4" y="4" width="6" height="6" rx="1" /><rect x="14" y="4" width="6" height="6" rx="1" /><rect x="4" y="14" width="6" height="6" rx="1" /><rect x="14" y="14" width="6" height="6" rx="1" /></svg>
    case 'users': return <svg {...common}><circle cx="9" cy="8" r="3" /><path d="M3.5 19a5.5 5.5 0 0 1 11 0" /><path d="M16 5.5a3 3 0 0 1 0 5.8M16 14a5 5 0 0 1 4.5 5" /></svg>
    case 'admins': return <svg {...common}><path d="m12 3 7 3v5c0 4.5-3 7.8-7 10-4-2.2-7-5.5-7-10V6l7-3Z" /><path d="m9 12 2 2 4-4" /></svg>
    case 'broadcast': return <svg {...common}><path d="M4 11v2" /><path d="M7 9v6" /><path d="M10 7v10" /><path d="M13 5v14" /><path d="M17 8.5a5 5 0 0 1 0 7" /><path d="M20 6a9 9 0 0 1 0 12" /></svg>
    case 'finance': return <svg {...common}><rect x="3.5" y="5" width="17" height="14" rx="2" /><path d="M3.5 9h17M7 14h.01M11 14h5" /></svg>
    case 'logs': return <svg {...common}><path d="M5 5h14M5 10h14M5 15h9M5 20h7" /><circle cx="18" cy="17" r="3" /><path d="m20.2 19.2 1.3 1.3" /></svg>
    case 'system': return <svg {...common}><path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1" /><circle cx="12" cy="12" r="4" /></svg>
    case 'refresh': return <svg {...common}><path d="M20 11a8 8 0 0 0-14.7-4L4 9" /><path d="M4 4v5h5" /><path d="M4 13a8 8 0 0 0 14.7 4L20 15" /><path d="M20 20v-5h-5" /></svg>
    default: return <svg {...common}><path d="m6 6 12 12M18 6 6 18" /></svg>
  }
}

export default function AdminPage() {
  const { theme } = useThemeStore()
  const dark = theme === 'dark'
  const isDark = dark
  const ct = chartDark(isDark)
  const isSuperAdmin = useAppStore(s => s.user?.role === 'SUPER_ADMIN')
  const currentAdminId = useAppStore(s => s.user?.id)
  const tx = { p:'var(--text-primary)', d:'var(--text-secondary)', m:'var(--text-muted)' }
  const bg = 'var(--bg-card)'
  const brd = 'var(--border-color)'

  const [users, setUsers] = useState<AdminUser[]>([])
  const [admins, setAdmins] = useState<AdminInfo[]>([])
  const [loading, setLoading] = useState(true)
  const [usersLoading, setUsersLoading] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date | null>(null)
  const [error, setError] = useState('')
  const [tab, setTab] = useState<AdminTab>('monitor')
  const [filter, setFilter] = useState({ search: '', membership: '', status: '', minBalance: '', maxBalance: '' })
  const [page, setPage] = useState(1); const [totalUsers, setTotalUsers] = useState(0)
  const [pageSize, setPageSizeState] = useState(20)
  const [pendingStatusUser, setPendingStatusUser] = useState<AdminUser | null>(null)
  const [statusUpdating, setStatusUpdating] = useState(false)
  const [pendingRoleUser, setPendingRoleUser] = useState<AdminUser | null>(null)
  const [roleUpdating, setRoleUpdating] = useState(false)
  const [userAction, setUserAction] = useState<{ user: AdminUser; action: AdminUserAction } | null>(null)
  const [selectedUser, setSelectedUser] = useState<AdminUser | null>(null)

  // 管理员操作日志
  const [operationLogs, setOperationLogs] = useState<any[]>([])
  const [logPage, setLogPage] = useState(1)
  const [logTotal, setLogTotal] = useState(0)
  const [logSize] = useState(20)
  const [logLoading, setLogLoading] = useState(false)
  const [logFilter, setLogFilter] = useState({ adminId: '', operationType: '', startDate: '', endDate: '' })
  const [analytics, setAnalytics] = useState<AnalyticsData | null>(null)
  const [platformRevenue, setPlatformRevenue] = useState<PlatformRevenue | null>(null)
  const [timeRange, setTimeRange] = useState({ days: 7, months: 6 })
  const [bcfTab, setBcTab] = useState<'notify'|'coupon'>('notify')
  const [bcf, setBcf] = useState({ title:'', content:'', target:'all', membership:'free', days:'7', amount:'10', threshold:'0', plan:'', expire:'' })
  const [bcfSending, setBcfSending] = useState(false); const [bcfResult, setBcfResult] = useState('')

  // 财务管理状态
  const [finTab, setFinTab] = useState<'recharge' | 'consumption' | 'orders' | 'manual-review'>('recharge')
  const [finData, setFinData] = useState<{ records: any[]; total: number; totalPaid?: number }>({ records: [], total: 0 })
  const [finPage, setFinPage] = useState(1)
  const [finSize] = useState(15)
  const [finDate, setFinDate] = useState({ start: '', end: '' })
  const [finStatus, setFinStatus] = useState('')
  const [finLoading, setFinLoading] = useState(false)

  // 系统控制状态
  const [sysVerified, setSysVerified] = useState(false)
  const [sysPassword, setSysPassword] = useState('')
  const [sysVerifying, setSysVerifying] = useState(false)
  const [containers, setContainers] = useState<ContainerStatus[]>([])
  const [sysLoading, setSysLoading] = useState(false)
  const [sysMsg, setSysMsg] = useState('')
  const [expandedLogs, setExpandedLogs] = useState<string | null>(null)
  const [containerLogs, setContainerLogs] = useState<string[]>([])

  // 统计对账状态
  const [consistencyData, setConsistencyData] = useState<{
    items: Array<{ metric: string; source: string; sourceValue: string; rawTable: string; rawValue: string; diff: string; diffPercent: string; status: string; message: string }>;
    summary: { total: number; ok: number; warn: number; error: number; overallStatus: string; elapsedMs: number };
  } | null>(null)
  const [consistencyLoading, setConsistencyLoading] = useState(false)
  const [consistencyMsg, setConsistencyMsg] = useState('')

  // 系统组件实时状态
  const [systemStatus, setSystemStatus] = useState<{
    backend: { status: string; uptimeSeconds: number; usedMemoryMB: number; maxMemoryMB: number };
    mysql: { status: string; latencyMs?: number; error?: string };
    redis: { status: string; latencyMs?: number; error?: string };
    docker: { status: string; latencyMs?: number; error?: string };
  } | null>(null)


  const dailyChartRef = useRef<HTMLDivElement>(null)
  const revenueChartRef = useRef<HTMLDivElement>(null)
  const growthChartRef = useRef<HTMLDivElement>(null)
  const hourlyChartRef = useRef<HTMLDivElement>(null)
  const modelChartRef = useRef<HTMLDivElement>(null)
  const chartInst = useRef<{ daily?:ECharts; revenue?:ECharts; growth?:ECharts; hourly?:ECharts; model?:ECharts }>({})

  const loadUsers = useCallback(async () => {
    setUsersLoading(true)
    try {
      const [ulRes, al] = await Promise.all([
        adminUserApi.getUsersAdvanced({
          page,
          size: pageSize,
          search: filter.search,
          membership: filter.membership,
          status: filter.status,
          minBalance: filter.minBalance ? Number(filter.minBalance) : undefined,
          maxBalance: filter.maxBalance ? Number(filter.maxBalance) : undefined,
        }),
        adminApi.getAdmins().catch(() => [] as AdminInfo[])
      ])
      const r = ulRes as unknown as { records: any[]; total: number }
      const normalized = (r.records || []).map((u: any) => ({
        ...u,
        status: (u.status === 1 || u.status === 'ACTIVE' ? 'ACTIVE' : 'DISABLED') as 'ACTIVE' | 'DISABLED',
      }))
      setUsers(normalized as AdminUser[]); setTotalUsers(r.total); setAdmins(al as AdminInfo[])
    } catch (e) { setError((e as Error).message || '加载失败') }
    finally { setUsersLoading(false) }
  }, [page, pageSize, filter])

  const loadAnalytics = useCallback(async () => {
    try {
      const a = await adminApi.getAnalytics(timeRange.days, timeRange.months).catch(() => null)
      if (a) setAnalytics(a as unknown as AnalyticsData)
    } catch {}
  }, [timeRange])

  const loadPlatformRevenue = useCallback(async () => {
    try {
      const r = await adminApi.getPlatformRevenue().catch(() => null)
      if (r) setPlatformRevenue(r as unknown as PlatformRevenue)
    } catch {}
  }, [])

  const loadFinance = useCallback(async () => {
    if (finTab === 'manual-review') return
    setFinLoading(true)
    try {
      const params = { page: finPage, size: finSize, startDate: finDate.start || undefined, endDate: finDate.end || undefined }
      let r: any
      if (finTab === 'recharge') r = await adminFinanceApi.rechargeList(params)
      else if (finTab === 'consumption') r = await adminFinanceApi.consumptionList(params)
      else r = await adminFinanceApi.ordersList({ ...params, status: finStatus || undefined })
      setFinData({ records: r.records || [], total: r.total || 0, totalPaid: r.totalPaid })
    } catch (e) { setError((e as Error).message) }
    setFinLoading(false)
  }, [finTab, finPage, finSize, finDate, finStatus])

  useEffect(() => { if (tab === 'finance') loadFinance() }, [tab, loadFinance])

  const loadOperationLogs = useCallback(async () => {
    setLogLoading(true)
    try {
      const result = await adminLogApi.list({
        page: logPage,
        size: logSize,
        adminId: logFilter.adminId ? Number(logFilter.adminId) : undefined,
        operationType: logFilter.operationType || undefined,
        startDate: logFilter.startDate || undefined,
        endDate: logFilter.endDate || undefined,
      })
      setOperationLogs(result?.records || [])
      setLogTotal(Number(result?.total || 0))
    } catch (e) {
      setError((e as Error).message || '操作日志加载失败')
      setOperationLogs([])
      setLogTotal(0)
    } finally {
      setLogLoading(false)
    }
  }, [logPage, logSize, logFilter])

  useEffect(() => { if (tab === 'logs') loadOperationLogs() }, [tab, loadOperationLogs])

  // 统计对账
  const loadConsistency = useCallback(async () => {
    setConsistencyLoading(true); setConsistencyMsg('')
    try {
      const r = await adminStatsApi.consistencyCheck()
      setConsistencyData(r)
    } catch (e) { setConsistencyMsg('对账失败: ' + (e as Error).message) }
    setConsistencyLoading(false)
  }, [])

  const refreshAdminData = async () => {
    if (refreshing) return
    setRefreshing(true)
    setError('')
    try {
      await Promise.all([
        loadUsers(),
        loadAnalytics(),
        loadPlatformRevenue(),
        adminApi.getSystemStatus().then(setSystemStatus),
      ])
      setLastUpdatedAt(new Date())
    } catch (e) {
      setError((e as Error).message || '刷新管理数据失败')
    } finally {
      setRefreshing(false)
    }
  }

  const recalculateStats = async () => {
    setConsistencyMsg('')
    try {
      const r = await adminStatsApi.recalculate()
      setConsistencyMsg(`重算完成，已清除 ${r.clearedKeys} 个缓存键`)
      await loadConsistency()
    } catch (e) { setConsistencyMsg('重算失败: ' + (e as Error).message) }
  }

  useEffect(() => {
    setLoading(true)
    Promise.all([loadUsers(), loadAnalytics(), loadPlatformRevenue()])
      .then(() => setLastUpdatedAt(new Date()))
      .finally(() => setLoading(false))
  }, [loadUsers, loadAnalytics, loadPlatformRevenue])
  useEffect(() => { setPage(1) }, [filter])

  // 系统组件状态轮询（每 10 秒实时检测）
  useEffect(() => {
    const load = () => adminApi.getSystemStatus().then(setSystemStatus).catch(() => {})
    load()
    const timer = setInterval(load, 10000)
    return () => clearInterval(timer)
  }, [])

  const requestToggleStatus = (u: AdminUser) => setPendingStatusUser(u)

  const toggleStatus = async () => {
    if (!pendingStatusUser) return
    const user = pendingStatusUser
    const status = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
    setStatusUpdating(true)
    try {
      await adminApi.toggleUserStatus(user.id, status as 'ACTIVE' | 'DISABLED')
      setUsers(prev => prev.map(x => x.id === user.id ? { ...x, status } : x))
      setPendingStatusUser(null)
    } catch (e) { setError((e as Error).message) }
    finally { setStatusUpdating(false) }
  }
  const assignAdmin = async () => {
    if (!pendingRoleUser) return
    setRoleUpdating(true)
    try {
      await adminApi.assignAdminRole(pendingRoleUser.id, 'ADMIN')
      setPendingRoleUser(null)
      await loadUsers()
    } catch (e) { setError((e as Error).message) }
    finally { setRoleUpdating(false) }
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

  // ==================== 系统控制 ====================
  const verifySecondary = async () => {
    setSysVerifying(true); setSysMsg('')
    try {
      const r = await systemControlApi.verify(sysPassword)
      sessionStorage.setItem('lingdian_secondary_token', r.token)
      setSysVerified(true)
      setSysPassword('')
      await loadContainerStatus()
    } catch (e) { setSysMsg('二级密码错误: ' + (e as Error).message) }
    setSysVerifying(false)
  }

  const loadContainerStatus = async () => {
    setSysLoading(true)
    try {
      const list = await systemControlApi.getStatus()
      setContainers(list)
    } catch (e) {
      if ((e as Error).message?.includes('过期')) { setSysVerified(false); sessionStorage.removeItem('lingdian_secondary_token') }
      setSysMsg('加载状态失败: ' + (e as Error).message)
    }
    setSysLoading(false)
  }

  const handleContainerAction = async (name: string, action: 'start'|'stop'|'restart') => {
    setSysMsg('')
    try {
      if (action === 'start') await systemControlApi.startContainer(name)
      else if (action === 'stop') await systemControlApi.stopContainer(name)
      else await systemControlApi.restartContainer(name)
      setSysMsg(`容器 ${name} ${action === 'start' ? '启动' : action === 'stop' ? '停止' : '重启'}成功`)
      setTimeout(() => loadContainerStatus(), 1000)
    } catch (e) { setSysMsg('操作失败: ' + (e as Error).message) }
  }

  const handleEnvironmentAction = async (action: 'start'|'stop') => {
    setSysMsg('')
    try {
      if (action === 'start') await systemControlApi.startEnvironment()
      else await systemControlApi.stopEnvironment()
      setSysMsg(action === 'start' ? '环境启动完成' : '环境停止完成')
      setTimeout(() => loadContainerStatus(), 2000)
    } catch (e) { setSysMsg('操作失败: ' + (e as Error).message) }
  }

  const toggleLogs = async (name: string) => {
    if (expandedLogs === name) { setExpandedLogs(null); return }
    setExpandedLogs(name); setContainerLogs([])
    try {
      const logs = await systemControlApi.getLogs(name, 100)
      setContainerLogs(logs)
    } catch (e) { setContainerLogs(['获取日志失败: ' + (e as Error).message]) }
  }

  // 用户筛选已由后端分页完成，前端不再二次过滤，避免出现“总数有值但当前页空白”。
  const filtered = users
  const formatLogDetail = (value: unknown) => {
    if (value == null || value === '') return '-'
    if (typeof value === 'string') return value
    try { return JSON.stringify(value) } catch { return String(value) }
  }

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
        data: models.map(m => ({ name: String(m.model || ''), value: Number(m.calls || 0), itemStyle:{ color:m.color || COLORS[models.indexOf(m)%COLORS.length] } })),
      }] as any,
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
      {key:'revenue', name:'营收 ¥', color:COLORS[4]}, {key:'points', name:'点数消耗', color:COLORS[5]}
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

  const userColumns = useMemo<ColumnDef<AdminUser, any>[]>(() => [
    {
      id: 'account', header: '用户', accessorFn: user => user.displayName || user.nickname || user.username,
      size: 210, minSize: 160, meta: { grow: 1.5 },
      cell: ({ row }) => {
        const user = row.original
        const name = user.displayName || user.nickname || user.username
        return <button type="button" className="flex min-w-0 items-center gap-2.5 text-left" onClick={() => setSelectedUser(user)} aria-label={`查看${name}详情`}>
          <UserAvatar name={name} avatarUrl={user.avatarUrl} className="h-8 w-8 border border-black/5 text-[10px] font-semibold text-white" style={{ background: 'linear-gradient(135deg,#8B5CF6,#4F46E5)' }} textClassName="text-white" />
          <span className="flex min-w-0 flex-col"><strong className="truncate text-[11px]" style={{ color: tx.p }}>{name}</strong><small className="truncate text-[9px]" style={{ color: tx.m }}>{user.username}</small></span>
        </button>
      },
    },
    { accessorKey: 'id', header: '用户 ID', size: 84, minSize: 72, cell: ({ getValue }) => <span className="font-mono" style={{ color: tx.m }}>{getValue<number>()}</span> },
    { accessorKey: 'email', header: '邮箱', size: 170, minSize: 130 },
    { accessorKey: 'phone', header: '手机号', size: 112, minSize: 96 },
    { id: 'oauth', header: '第三方登录', accessorFn: user => user.oauthProviders?.join(' ') || '未绑定', size: 120, minSize: 100, cell: ({ row }) => <span className="flex gap-1">{row.original.oauthProviders?.length ? row.original.oauthProviders.map(provider => <span key={provider} className="rounded px-1.5 py-0.5 text-[9px]" style={{ background: 'rgba(124,58,237,.1)', color: '#7C3AED' }}>{provider.toUpperCase()}</span>) : <span style={{ color: tx.m }}>未绑定</span>}</span> },
    { accessorKey: 'membership', header: '会员', size: 90, minSize: 78, cell: ({ getValue }) => { const v = String(getValue() || 'free'); return <span className="rounded px-1.5 py-0.5 text-[9px]" style={{ color: v === 'diamond' ? '#7C3AED' : v === 'gold' ? '#B45309' : v === 'silver' ? '#475569' : tx.m, background: v === 'diamond' ? 'rgba(124,58,237,.1)' : v === 'gold' ? 'rgba(217,119,6,.1)' : 'var(--bg-surface)' }}>{({ diamond: '钻石', gold: '黄金', silver: '白银', free: '普通' } as Record<string, string>)[v] || v}</span> } },
    { accessorKey: 'role', header: '角色', size: 96, minSize: 84, cell: ({ getValue }) => { const role = String(getValue()); return <span style={{ color: role === 'SUPER_ADMIN' ? '#7C3AED' : role === 'ADMIN' ? '#4F46E5' : tx.d }}>{role === 'SUPER_ADMIN' ? '超级管理员' : role === 'ADMIN' ? '管理员' : '用户'}</span> } },
    { accessorKey: 'status', header: '状态', size: 78, minSize: 70, cell: ({ getValue }) => { const active = getValue() === 'ACTIVE'; return <span style={{ color: active ? '#059669' : '#E11D48' }}>{active ? '正常' : '封禁'}</span> } },
    { accessorKey: 'points', header: '点数余额', size: 104, minSize: 90, cell: ({ getValue }) => <span className="tabular-nums" style={{ color: '#B45309' }}>{Number(getValue() || 0).toFixed(2)} 点</span> },
    { accessorKey: 'balance', header: '账户余额', size: 104, minSize: 90, cell: ({ getValue }) => <span className="tabular-nums" style={{ color: tx.p }}>¥{Number(getValue() || 0).toFixed(2)}</span> },
    { accessorKey: 'totalCalls', header: 'AI 调用', size: 80, minSize: 70, cell: ({ getValue }) => <span className="tabular-nums">{Number(getValue() || 0).toLocaleString()}</span> },
    { accessorKey: 'createTime', header: '注册时间', size: 116, minSize: 100, cell: ({ getValue }) => <span className="text-[10px]">{getValue<string>() ? new Date(getValue<string>()).toLocaleDateString() : '-'}</span> },
    { id: 'details', header: '详情', enableColumnFilter: false, size: 72, minSize: 64, cell: ({ row }) => <button type="button" onClick={() => setSelectedUser(row.original)} className="rounded-md px-2 py-1 text-[10px] font-medium" style={{ color: '#6D3BE8', background: 'rgba(124,58,237,.1)' }}>查看</button> },
  ], [tx.p, tx.d, tx.m])

  const adminColumns = useMemo<ColumnDef<AdminInfo, any>[]>(() => [
    { accessorKey: 'id', header: 'ID', size: 76, minSize: 65 },
    { id: 'username', header: '管理员', accessorFn: admin => admin.displayName || admin.nickname || admin.username, size: 190, minSize: 140, meta: { grow: 1.5 }, cell: ({ row }) => <span className="flex min-w-0 items-center gap-2"><UserAvatar name={row.original.displayName || row.original.nickname || row.original.username} avatarUrl={row.original.avatarUrl} className="h-7 w-7 border border-black/5 text-[9px] font-semibold text-white" style={{ background: 'linear-gradient(135deg,#8B5CF6,#4F46E5)' }} textClassName="text-white" /><span className="truncate">{row.original.displayName || row.original.nickname || row.original.username}</span></span> },
    { accessorKey: 'email', header: '邮箱', size: 190, minSize: 140 },
    { accessorKey: 'role', header: '角色', size: 120, minSize: 100, cell: ({ getValue }) => getValue() === 'SUPER_ADMIN' ? '超级管理员' : '管理员' },
    { accessorKey: 'status', header: '状态', size: 85, minSize: 72, cell: ({ getValue }) => getValue() === 'ACTIVE' ? '正常' : '封禁' },
    { accessorKey: 'createTime', header: '创建时间', size: 125, minSize: 105, cell: ({ getValue }) => getValue<string>() ? new Date(getValue<string>()).toLocaleDateString() : '-' },
    { id: 'action', header: '操作', enableColumnFilter: false, size: 110, minSize: 95, cell: ({ row }) => row.original.role !== 'SUPER_ADMIN' ? <button type="button" onClick={() => void revokeAdmin(row.original.id)} className="rounded-md px-2 py-1 text-[10px]" style={{ color: '#E11D48', background: 'rgba(244,63,94,.1)' }}>取消管理员</button> : <span style={{ color: tx.m }}>保护账户</span> },
  ], [tx.m])

  const consistencyColumns = useMemo<ColumnDef<NonNullable<typeof consistencyData>['items'][number], any>[]>(() => [
    { accessorKey: 'metric', header: '指标', size: 190, minSize: 130, meta: { grow: 1.4 } },
    { accessorKey: 'sourceValue', header: '缓存/聚合值', size: 140, minSize: 110 },
    { accessorKey: 'rawValue', header: '原始表值', size: 140, minSize: 110 },
    { accessorKey: 'diff', header: '偏差', size: 110, minSize: 90 },
    { accessorKey: 'diffPercent', header: '偏差率', size: 100, minSize: 82 },
    { accessorKey: 'status', header: '状态', size: 82, minSize: 70, cell: ({ getValue }) => { const value = String(getValue()); return <span className="rounded px-1.5 py-0.5 text-[10px] font-medium" style={{ background: value === 'OK' ? 'rgba(16,185,129,.15)' : value === 'WARN' ? 'rgba(245,158,11,.15)' : 'rgba(244,63,94,.15)', color: value === 'OK' ? '#059669' : value === 'WARN' ? '#B45309' : '#E11D48' }}>{value === 'OK' ? '正常' : value === 'WARN' ? '警告' : '错误'}</span> } },
    { accessorKey: 'message', header: '说明', size: 250, minSize: 160, meta: { grow: 1.5 } },
  ], [])

  const logColumns = useMemo<ColumnDef<Record<string, any>, any>[]>(() => [
    { id: 'created', header: '时间', accessorFn: row => row.createTime ? new Date(row.createTime).toLocaleString() : '-', size: 160, minSize: 135 },
    { id: 'admin', header: '管理员', accessorFn: row => row.adminUsername || row.operatorName || row.adminId || '-', size: 120, minSize: 100 },
    { id: 'type', header: '类型', accessorFn: row => LOG_TYPE_LABELS[row.operationType] || row.operationType || '-', size: 115, minSize: 95 },
    { id: 'operation', header: '动作', accessorFn: row => row.operation || row.operationName || '-', size: 140, minSize: 110 },
    { id: 'target', header: '目标', accessorFn: row => formatLogDetail(row.targetUserIds || row.targetIds || row.targetId), size: 120, minSize: 90 },
    { id: 'result', header: '结果', accessorFn: row => row.result === 'SUCCESS' || row.status === 'SUCCESS' || row.success === true ? '成功' : '失败', size: 76, minSize: 65 },
    { id: 'detail', header: '详情', accessorFn: row => formatLogDetail(row.operationDetail || row.errorMessage), size: 240, minSize: 140, meta: { grow: 1.5 } },
    { id: 'ip', header: 'IP', accessorFn: row => row.ipAddress || row.ip || '-', size: 130, minSize: 95 },
  ], [])

  const financeColumns = useMemo<ColumnDef<Record<string, any>, any>[]>(() => finTab === 'orders' ? [
    { id: 'orderId', header: '订单 ID', accessorFn: row => row.orderId || row.id, size: 130, minSize: 105 },
    { accessorKey: 'userId', header: '用户 ID', size: 96, minSize: 78 },
    { id: 'package', header: '套餐', accessorFn: row => row.packageName || row.packageCode, size: 160, minSize: 120, meta: { grow: 1.3 } },
    { id: 'paid', header: '实付金额', accessorFn: row => Number(row.pricePaid || 0).toFixed(2), size: 110, minSize: 95, cell: ({ getValue }) => `¥${getValue()}` },
    { id: 'discount', header: '优惠金额', accessorFn: row => Number(row.discountAmount || 0).toFixed(2), size: 110, minSize: 95, cell: ({ getValue }) => `¥${getValue()}` },
    { accessorKey: 'status', header: '状态', size: 100, minSize: 82 },
    { id: 'start', header: '生效时间', accessorFn: row => row.startTime ? new Date(row.startTime).toLocaleString() : '-', size: 160, minSize: 130 },
    { id: 'expire', header: '到期时间', accessorFn: row => row.expireTime ? new Date(row.expireTime).toLocaleString() : '永久', size: 160, minSize: 130 },
  ] : [
    { accessorKey: 'id', header: '记录 ID', size: 100, minSize: 82 },
    { accessorKey: 'userId', header: '用户 ID', size: 100, minSize: 82 },
    { id: 'type', header: '类型', accessorFn: row => row.type === 2 ? '充值' : '扣费', size: 92, minSize: 75 },
    { id: 'amount', header: finTab === 'consumption' ? '点数变动' : '金额', accessorFn: row => Number(row.amount || 0).toFixed(2), size: 110, minSize: 95, cell: ({ getValue }) => finTab === 'consumption' ? `${getValue()} 点` : `¥${getValue()}` },
    { id: 'before', header: finTab === 'consumption' ? '变动前点数' : '变动前余额', accessorFn: row => Number(row.balanceBefore || 0).toFixed(2), size: 125, minSize: 105, cell: ({ getValue }) => finTab === 'consumption' ? `${getValue()} 点` : `¥${getValue()}` },
    { id: 'after', header: finTab === 'consumption' ? '变动后点数' : '变动后余额', accessorFn: row => Number(row.balanceAfter || 0).toFixed(2), size: 125, minSize: 105, cell: ({ getValue }) => finTab === 'consumption' ? `${getValue()} 点` : `¥${getValue()}` },
    { id: 'created', header: '时间', accessorFn: row => row.createTime ? new Date(row.createTime).toLocaleString() : '-', size: 160, minSize: 130 },
  ], [finTab])

  if (loading && !analytics) {
    return <div className="flex items-center justify-center h-full"><div className="w-8 h-8 border-2 border-brand-purple/30 border-t-brand-purple rounded-full animate-spin" /></div>
  }

  const tabs: Array<{ k: AdminTab; l: string; d: string; n: number; icon: AdminNavKind }> = [
    { k:'monitor', l:'平台总览', d:'核心指标与系统健康度', n:0, icon:'overview' },
    { k:'users', l:'用户目录', d:'账户、会员与点数运营', n:totalUsers, icon:'users' },
    { k:'admins', l:'角色与权限', d:'管理员角色和状态', n:admins.length, icon:'admins' },
    { k:'finance', l:'计费与订单', d:'充值、消费与会员订单', n:0, icon:'finance' },
    { k:'broadcast', l:'运营触达', d:'通知与代金券发放', n:0, icon:'broadcast' },
    { k:'logs', l:'审计日志', d:'管理员操作追踪', n:logTotal, icon:'logs' },
    { k:'system', l:'系统运维', d:'环境状态与容器控制', n:0, icon:'system' },
  ]
  const navGroups: Array<{ label: string; keys: AdminTab[] }> = [
    { label: '监控', keys: ['monitor'] },
    { label: '用户与商业', keys: ['users', 'admins', 'finance'] },
    { label: '运营', keys: ['broadcast'] },
    { label: '治理与运维', keys: ['logs', 'system'] },
  ]

  const a = analytics
  const r = platformRevenue
  const kpiCards = a ? [
    { l:'总用户', v:a.totalUsers, c:COLORS[0] },
    { l:'活跃用户', v:a.activeUsers, c:COLORS[3] },
    { l:'总API Keys', v:a.totalApiKeys, c:COLORS[2] },
    { l:'总调用', v:a.totalCalls.toLocaleString(), c:COLORS[1] },
    { l:'总营收 ¥', v:r ? (r.totalRevenue||0).toFixed(2) : '0.00', c:COLORS[4] },
    { l:'今日营收 ¥', v:r ? (r.todayRevenue||0).toFixed(2) : '0.00', c:COLORS[5] },
    { l:'本月营收 ¥', v:r ? (r.monthRevenue||0).toFixed(2) : '0.00', c:COLORS[6] },
    { l:'订阅收入 ¥', v:r ? (r.subscriptionRevenue||0).toFixed(2) : '0.00', c:COLORS[7] },
  ] : []

  const activeTab = tabs.find(item => item.k === tab) || tabs[0]
  const systemHealthy = systemStatus
    ? [systemStatus.backend.status, systemStatus.mysql.status, systemStatus.redis.status, systemStatus.docker.status].every(status => status === 'UP')
    : null
  const updatedLabel = lastUpdatedAt
    ? lastUpdatedAt.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    : '等待首次同步'

  return (
    <div className="page-scroll h-full overflow-y-auto" style={{ background: 'var(--bg-primary)' }}>
      <div className="mx-auto max-w-[1480px] px-4 py-5 sm:px-6 lg:px-8 lg:py-7">
        <header className="flex flex-col gap-5 border-b pb-6 lg:flex-row lg:items-end lg:justify-between" style={{ borderColor: brd }}>
          <div className="min-w-0">
            <nav aria-label="页面层级" className="flex items-center gap-2 text-[11px]" style={{ color: tx.m }}>
              <span>平台工作台</span>
              <span aria-hidden="true" style={{ color: tx.m }}>/</span>
              <span aria-current="page" style={{ color: tx.d }}>管理控制台</span>
            </nav>
            <div className="mt-3 flex items-center gap-3">
              <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl text-white" style={{ background: 'linear-gradient(135deg, var(--accent-purple), var(--accent-blue))', boxShadow: '0 10px 24px var(--accent-purple-glow)' }}>
                <AdminNavIcon kind="overview" size={20} />
              </div>
              <div className="min-w-0">
                <h1 className="truncate text-2xl font-semibold tracking-tight sm:text-3xl" style={{ color: tx.p }}>管理控制台</h1>
                <p className="mt-1 text-xs sm:text-sm" style={{ color: tx.d }}>运营、计费、用户和基础设施的一致视图</p>
              </div>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2.5 lg:justify-end">
            <div className="flex items-center gap-2 rounded-xl px-3 py-2 text-[11px]" style={{ background: 'var(--bg-card)', border: `1px solid ${brd}`, color: tx.d }}>
              <span className="h-2 w-2 rounded-full" style={{ background: systemHealthy === false ? '#F43F5E' : systemHealthy === true ? '#10B981' : '#F59E0B', boxShadow: `0 0 0 4px ${(systemHealthy === false ? '#F43F5E' : systemHealthy === true ? '#10B981' : '#F59E0B')}18` }} />
              {systemHealthy === false ? '存在异常组件' : systemHealthy === true ? '平台运行正常' : '状态检测中'}
            </div>
            <div className="hidden text-[11px] xl:block" style={{ color: tx.m }}>数据更新于 {updatedLabel}</div>
            <button type="button" onClick={() => void refreshAdminData()} disabled={refreshing} aria-label="刷新管理数据"
              className="inline-flex items-center gap-2 rounded-xl px-3 py-2 text-[11px] font-medium transition-colors disabled:cursor-wait disabled:opacity-60"
              style={{ background: 'var(--accent-purple)', color: '#fff' }}>
              <AdminNavIcon kind="refresh" size={14} />
              {refreshing ? '同步中...' : '刷新数据'}
            </button>
          </div>
        </header>

        <div className="mt-6 grid items-start gap-6 xl:grid-cols-[232px_minmax(0,1fr)]">
          <aside className="sticky top-4 hidden rounded-2xl p-3 xl:block" style={{ background: 'var(--bg-card)', border: `1px solid ${brd}` }}>
            <div className="px-3 pb-3 pt-2">
              <div className="text-[10px] font-semibold uppercase tracking-[0.18em]" style={{ color: tx.m }}>工作区</div>
              <div className="mt-1 text-xs" style={{ color: tx.d }}>平台运营中心</div>
            </div>
            <div className="space-y-4">
              {navGroups.map(group => (
                <div key={group.label}>
                  <div className="px-3 pb-1.5 text-[10px] font-medium" style={{ color: tx.m }}>{group.label}</div>
                  <div className="space-y-1">
                    {group.keys.map(key => {
                      const item = tabs.find(candidate => candidate.k === key)!
                      const selected = tab === item.k
                      return (
                        <button key={item.k} type="button" onClick={() => setTab(item.k)} aria-current={selected ? 'page' : undefined}
                          className="group flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left transition-colors"
                          style={{ background: selected ? 'var(--bg-active)' : 'transparent', color: selected ? 'var(--accent-purple)' : tx.d }}>
                          <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg" style={{ background: selected ? 'var(--accent-purple-soft)' : 'var(--bg-surface)' }}><AdminNavIcon kind={item.icon} size={15} /></span>
                          <span className="min-w-0 flex-1"><span className="block truncate text-xs font-medium">{item.l}</span><span className="mt-0.5 block truncate text-[10px]" style={{ color: selected ? 'var(--accent-purple)' : tx.m }}>{item.d}</span></span>
                          {item.n > 0 && <span className="rounded-md px-1.5 py-0.5 text-[10px] tabular-nums" style={{ background: selected ? 'var(--accent-purple)' : 'var(--bg-surface)', color: selected ? '#fff' : tx.m }}>{item.n > 9999 ? '9,999+' : item.n}</span>}
                        </button>
                      )
                    })}
                  </div>
                </div>
              ))}
            </div>
          </aside>

          <main className="min-w-0">
            <nav aria-label="后台模块" className="mb-4 grid grid-cols-2 gap-2 sm:grid-cols-3 xl:hidden">
              {tabs.map(item => {
                const selected = tab === item.k
                return <button key={item.k} type="button" onClick={() => setTab(item.k)} aria-current={selected ? 'page' : undefined}
                  className="inline-flex min-h-10 w-full items-center justify-start gap-2 rounded-xl px-3 py-2 text-left text-xs font-medium"
                  style={{ background: selected ? 'var(--accent-purple)' : 'var(--bg-card)', color: selected ? '#fff' : tx.d, border: `1px solid ${selected ? 'var(--accent-purple)' : brd}` }}>
                  <AdminNavIcon kind={item.icon} size={14} />{item.l}{item.n > 0 ? ` ${item.n}` : ''}
                </button>
              })}
            </nav>

            <div className="mb-5 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <div className="text-[10px] font-semibold uppercase tracking-[0.18em]" style={{ color: 'var(--accent-purple)' }}>当前模块</div>
                <h2 className="mt-1 text-xl font-semibold tracking-tight" style={{ color: tx.p }}>{activeTab.l}</h2>
                <p className="mt-1 text-xs" style={{ color: tx.d }}>{activeTab.d}</p>
              </div>
              <div className="text-[11px] sm:text-right" style={{ color: tx.m }}>
                {tab === 'monitor' ? `统计范围：${timeRange.days} 天 / ${timeRange.months} 个月` : tab === 'users' ? `当前筛选返回 ${totalUsers} 个账户` : tab === 'logs' ? `审计记录 ${logTotal} 条` : '服务端数据为最终准则'}
              </div>
            </div>

            {error && <div role="alert" className="mb-4 flex items-start gap-2 rounded-xl px-4 py-3 text-xs" style={{ background:'rgba(244,63,94,0.1)', color:'#F43F5E', border:'1px solid rgba(244,63,94,0.2)' }}><span className="mt-0.5 h-2 w-2 shrink-0 rounded-full" style={{ background: '#F43F5E' }} />{error}</div>}

      {/* ====================== MONITOR / 数据大屏 ====================== */}
      {tab === 'monitor' && a && (
        <div className="space-y-4">
          {/* Time Range */}
          <div className="flex flex-col gap-3 rounded-2xl border p-4 sm:flex-row sm:items-center sm:justify-between" style={{ background: bg, borderColor: brd }}>
            <div>
              <div className="text-xs font-semibold" style={{ color: tx.p }}>业务概览</div>
              <div className="mt-1 text-[11px]" style={{ color: tx.d }}>选择统计窗口，查看平台使用、增长和收入趋势。</div>
            </div>
            <div className="flex items-center gap-2 flex-wrap">
            <span className="text-[10px]" style={{ color: tx.d }}>统计窗口</span>
            {[{ d:7, m:6, l:'7天/6月' }, { d:30, m:12, l:'30天/12月' }, { d:90, m:24, l:'90天/24月' }].map(r => (
              <button key={r.d} onClick={() => { setTimeRange({ days:r.d, months:r.m }); loadAnalytics() }}
                className="px-3 py-1 rounded-lg text-[10px] font-medium border transition-all"
                style={{ background: timeRange.days===r.d ? 'var(--accent-purple)' : 'transparent', borderColor: timeRange.days===r.d ? 'var(--accent-purple)' : brd, color: timeRange.days===r.d ? '#fff' : tx.d }}>
                {r.l}
              </button>
            ))}
            </div>
          </div>

          {/* KPI Cards */}
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
            {kpiCards.map((m,i) => (
              <div key={i} className="rounded-2xl border p-4" style={{ background: bg, borderColor: brd }}>
                <div className="text-[11px]" style={{ color: tx.d }}>{m.l}</div>
                <div className="mt-2 text-lg font-semibold tabular-nums" style={{ color: m.c }}>{m.v}</div>
              </div>
            ))}
          </div>

          {/* System Status */}
          <div className="rounded-2xl p-5 border" style={{ background: bg, borderColor: brd }}>
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-xs font-semibold" style={{ color: tx.p }}>系统状态</h3>
              <span className="text-[10px]" style={{ color: tx.d }}>每10秒实时检测</span>
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs" style={{ color: tx.d }}>
              {(() => {
                const comps = systemStatus ? [
                  { k: '后端服务', s: systemStatus.backend.status, detail: `${Math.floor(systemStatus.backend.uptimeSeconds / 60)}分钟 · ${systemStatus.backend.usedMemoryMB}MB内存` },
                  { k: 'MySQL数据库', s: systemStatus.mysql.status, detail: systemStatus.mysql.latencyMs != null ? `${systemStatus.mysql.latencyMs}ms` : (systemStatus.mysql.error || '-') },
                  { k: 'Redis缓存', s: systemStatus.redis.status, detail: systemStatus.redis.latencyMs != null ? `${systemStatus.redis.latencyMs}ms` : (systemStatus.redis.error || '-') },
                  { k: 'Docker引擎', s: systemStatus.docker.status, detail: systemStatus.docker.latencyMs != null ? `${systemStatus.docker.latencyMs}ms` : (systemStatus.docker.error || '-') },
                ] : [
                  { k: '后端服务', s: '检测中', detail: '' },
                  { k: 'MySQL数据库', s: '检测中', detail: '' },
                  { k: 'Redis缓存', s: '检测中', detail: '' },
                  { k: 'Docker引擎', s: '检测中', detail: '' },
                ]
                return comps.map(x => {
                  const up = x.s === 'UP'
                  const color = up ? '#10B981' : (x.s === '检测中' ? '#F59E0B' : '#EF4444')
                  return (
                    <div key={x.k} className="px-3 py-2 rounded-lg" style={{ background: 'var(--bg-surface)' }}>
                      <div className="flex justify-between items-center">
                        <span>{x.k}</span>
                        <span style={{ color }}><span className="inline-block w-1.5 h-1.5 rounded-full mr-1" style={{ background: color }} />{x.s === 'UP' ? '运行中' : x.s === 'DOWN' ? '异常' : '检测中'}</span>
                      </div>
                      {x.detail && <div className="text-[10px] mt-1" style={{ color: tx.d }}>{x.detail}</div>}
                    </div>
                  )
                })
              })()}
            </div>
          </div>

          {/* Charts Grid */}
          <div className="grid lg:grid-cols-2 gap-4">
            <div className="rounded-2xl p-4 border" style={{ background: bg, borderColor: brd }}>
              <h3 className="text-xs font-semibold mb-2" style={{ color: tx.p }}>调用量与Token趋势</h3>
              <div ref={dailyChartRef} style={{ height: 260 }} />
            </div>
            <div className="rounded-2xl p-4 border" style={{ background: bg, borderColor: brd }}>
              <h3 className="text-xs font-semibold mb-2" style={{ color: tx.p }}>营收与点数消耗</h3>
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
                      <UserAvatar name={u.displayName || u.username} avatarUrl={u.avatarUrl} className="h-7 w-7 border border-black/5 text-[9px] font-semibold text-white" style={{ background: 'linear-gradient(135deg,#8B5CF6,#4F46E5)' }} textClassName="text-white" />
                      <span className="min-w-0 flex-1 truncate text-xs font-medium" style={{ color: tx.p }}>{u.displayName || u.username}</span>
                      <span className="text-[10px]" style={{ color: tx.d }}>{u.calls} 次</span>
                      <span className="text-[10px] font-mono" style={{ color: COLORS[4] }}>{Number(u.points || 0).toFixed(2)} 点</span>
                    </div>
                  ))}
                </div>
              ) : <p className="text-xs" style={{ color: tx.d }}>暂无数据</p>}
            </div>
          </div>

          {/* ====================== 统计对账验证 ====================== */}
          <div className="rounded-2xl p-5 border" style={{ background: bg, borderColor: brd }}>
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-xs font-semibold" style={{ color: tx.p }}>统计对账验证</h3>
              <div className="flex gap-2">
                <button onClick={loadConsistency} disabled={consistencyLoading}
                  className="px-3 py-1.5 rounded-lg text-[11px] font-medium disabled:opacity-40"
                  style={{ background: 'var(--accent-purple)', color: '#fff' }}>
                  {consistencyLoading ? '对账中...' : '运行对账'}
                </button>
                <button onClick={recalculateStats} disabled={consistencyLoading}
                  className="px-3 py-1.5 rounded-lg text-[11px] font-medium disabled:opacity-40"
                  style={{ background: 'var(--bg-surface)', color: tx.d, border: `1px solid ${brd}` }}>
                  重算缓存
                </button>
              </div>
            </div>

            {consistencyMsg && (
              <div className="mb-3 px-3 py-2 rounded-lg text-[11px]" style={{ background: 'rgba(59,130,246,0.08)', color: '#3B82F6' }}>{consistencyMsg}</div>
            )}

            {consistencyData && (
              <>
                {/* 汇总卡片 */}
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 mb-3">
                  {[
                    { l: '总检查项', v: consistencyData.summary.total, c: tx.p },
                    { l: '正常', v: consistencyData.summary.ok, c: '#10B981' },
                    { l: '警告', v: consistencyData.summary.warn, c: '#F59E0B' },
                    { l: '错误', v: consistencyData.summary.error, c: '#F43F5E' },
                  ].map((k, i) => (
                    <div key={i} className="rounded-lg px-3 py-2" style={{ background: 'var(--bg-surface)' }}>
                      <div className="text-[10px]" style={{ color: tx.m }}>{k.l}</div>
                      <div className="text-lg font-bold tabular-nums" style={{ color: k.c }}>{k.v}</div>
                    </div>
                  ))}
                </div>

                {/* 对账明细表 */}
                <AdminDataTable
                  columns={consistencyColumns}
                  data={consistencyData.items}
                  getRowId={item => item.metric}
                  className="rounded-lg border-0"
                  sizingKey="admin-consistency-dense"
                />
                <div className="mt-2 text-[10px]" style={{ color: tx.m }}>
                  耗时 {consistencyData.summary.elapsedMs}ms · 偏差≤1% 正常，1%-5% 警告，&gt;5% 错误
                </div>
              </>
            )}
            {!consistencyData && !consistencyLoading && (
              <p className="text-xs text-center py-6" style={{ color: tx.m }}>点击「运行对账」检查缓存与原始表数据一致性</p>
            )}
          </div>
        </div>
      )}

      {/* ====================== USERS ====================== */}
      {tab === 'users' && (
        <div className="space-y-4">
          <div className="flex flex-col gap-3 rounded-2xl border p-4 sm:flex-row sm:items-start sm:justify-between" style={{ background: bg, borderColor: brd }}>
            <div>
              <div className="flex items-center gap-2"><AdminNavIcon kind="users" size={16} /><h3 className="text-sm font-semibold" style={{ color: tx.p }}>用户目录</h3></div>
              <p className="mt-1 text-[11px]" style={{ color: tx.d }}>按账户、会员等级、状态和余额范围检索，操作结果以服务端记录为准。</p>
            </div>
            <button type="button" onClick={() => void loadUsers()} disabled={usersLoading} className="inline-flex shrink-0 items-center justify-center gap-2 rounded-xl px-3 py-2 text-[11px] font-medium disabled:opacity-50" style={{ background: 'var(--bg-surface)', color: tx.d, border: `1px solid ${brd}` }}>
              <AdminNavIcon kind="refresh" size={13} />{usersLoading ? '加载中...' : '刷新列表'}
            </button>
          </div>

          <div className="flex flex-wrap items-end gap-3 rounded-2xl border p-4" style={{ background: 'var(--bg-card)', borderColor: brd }}>
            <label className="relative block w-full sm:w-[240px]">
              <span className="mb-1.5 block text-[10px] font-medium" style={{ color: tx.m }}>关键词</span>
              <input value={filter.search} onChange={e => setFilter({...filter, search:e.target.value})} placeholder="ID、昵称、用户名、邮箱、手机或第三方昵称"
                className="w-full rounded-xl border px-3 py-2.5 pr-9 text-xs outline-none" style={{ background:'var(--bg-input)', borderColor:brd, color:tx.p }} />
              {filter.search && <button type="button" aria-label="清除关键词" onClick={() => setFilter({...filter, search:''})} className="absolute bottom-2.5 right-2 flex h-5 w-5 items-center justify-center rounded-md" style={{ color: tx.m }}><AdminNavIcon kind="close" size={12} /></button>}
            </label>
            <select value={filter.membership} onChange={e => setFilter({...filter, membership:e.target.value})}
              aria-label="会员等级" className="rounded-xl border px-3 py-2.5 text-xs outline-none" style={{ background:'var(--bg-input)', borderColor:brd, color:tx.p }}>
              <option value="">全部等级</option><option value="free">普通用户</option><option value="silver">白银会员</option><option value="gold">黄金会员</option><option value="diamond">钻石会员</option>
            </select>
            <select value={filter.status} onChange={e => setFilter({...filter, status:e.target.value})}
              aria-label="账户状态" className="rounded-xl border px-3 py-2.5 text-xs outline-none" style={{ background:'var(--bg-input)', borderColor:brd, color:tx.p }}>
              <option value="">全部状态</option><option value="ACTIVE">正常</option><option value="DISABLED">禁用</option>
            </select>
            <label className="block w-28">
              <span className="mb-1.5 block text-[10px] font-medium" style={{ color: tx.m }}>最低余额</span>
              <input type="number" min="0" step="0.01" value={filter.minBalance} onChange={e => setFilter({...filter, minBalance:e.target.value})} placeholder="¥ 0"
                className="w-full rounded-xl border px-3 py-2.5 text-xs outline-none" style={{ background:'var(--bg-input)', borderColor:brd, color:tx.p }} />
            </label>
            <label className="block w-28">
              <span className="mb-1.5 block text-[10px] font-medium" style={{ color: tx.m }}>最高余额</span>
              <input type="number" min="0" step="0.01" value={filter.maxBalance} onChange={e => setFilter({...filter, maxBalance:e.target.value})} placeholder="不限"
                className="w-full rounded-xl border px-3 py-2.5 text-xs outline-none" style={{ background:'var(--bg-input)', borderColor:brd, color:tx.p }} />
            </label>
            <button type="button" onClick={() => setFilter({search:'',membership:'',status:'',minBalance:'',maxBalance:''})}
              className="rounded-xl px-3 py-2.5 text-xs" style={{ color:tx.d, border:`1px solid ${brd}` }}>重置条件</button>
          </div>
          <div className="flex h-[min(62vh,640px)] min-h-[420px] flex-col overflow-hidden rounded-2xl border" style={{ background:bg, borderColor:brd }}>
            <AdminDataTable
              columns={userColumns}
              data={filtered}
              loading={usersLoading}
              emptyMessage={filter.search || filter.membership || filter.status || filter.minBalance || filter.maxBalance ? '没有符合条件的账户' : '暂无用户数据'}
              getRowId={user => String(user.id)}
              onRowClick={user => setSelectedUser(user)}
              className="min-h-0 flex-1 rounded-none border-0"
              sizingKey="admin-users-dense"
              toolbar={<div className="flex min-w-0 flex-wrap items-center justify-between gap-2"><span className="text-[10px]" style={{ color: tx.d }}>用户目录 · 共 {totalUsers} 个账户</span><span className="text-[9px]" style={{ color: tx.m }}>关键词和条件查询全库；列筛选作用于当前页</span></div>}
            />
            <div className="flex min-h-[68px] shrink-0 flex-col items-stretch justify-center gap-2 border-t px-3 py-3 text-xs sm:flex-row sm:items-center sm:justify-between" style={{ background: 'var(--bg-card)', borderColor: brd }}>
              <div className="flex items-center justify-center gap-2 sm:justify-start">
                <span style={{ color:tx.d }}>每页</span>
                {[10,20,50].map(n => (
                  <button key={n} onClick={() => { setPageSizeState(n); setPage(1) }}
                    className="px-2 py-1 rounded-md border transition-all"
                    style={{ background: pageSize===n?'var(--accent-purple)':'transparent', borderColor: pageSize===n?'var(--accent-purple)':brd, color: pageSize===n?'#fff':tx.d }}>{n}</button>
                ))}
                <span style={{ color:tx.d }}>条</span>
              </div>
              <span className="text-center sm:text-left" style={{ color:tx.d }}>{totalUsers > 0 ? `共 ${totalUsers} 条，第 ${page} / ${Math.ceil(totalUsers/pageSize)} 页` : (usersLoading ? '正在加载用户目录' : '暂无用户数据')}</span>
              <div className="flex justify-end gap-2">
                <button onClick={() => setPage(p=>Math.max(1,p-1))} disabled={page<=1 || totalUsers === 0}
                  className="px-3 py-1.5 rounded-lg border transition-all disabled:opacity-30" style={{ borderColor:brd, color:tx.d }}>上一页</button>
                <button onClick={() => setPage(p=>p+1)} disabled={page*pageSize>=totalUsers || totalUsers === 0}
                  className="px-3 py-1.5 rounded-lg border transition-all disabled:opacity-30" style={{ borderColor:brd, color:tx.d }}>下一页</button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ====================== ADMINS ====================== */}
      {tab==='admins' && (
        <div className="space-y-4">
          <div className="flex flex-col gap-2 rounded-2xl border p-4 sm:flex-row sm:items-center sm:justify-between" style={{ background: bg, borderColor: brd }}>
            <div>
              <div className="flex items-center gap-2"><AdminNavIcon kind="admins" size={16} /><h3 className="text-sm font-semibold" style={{ color: tx.p }}>角色与权限</h3></div>
              <p className="mt-1 text-[11px]" style={{ color: tx.d }}>管理员角色变更会写入审计日志；超级管理员不可从自身账户撤销。</p>
            </div>
            <span className="text-[11px]" style={{ color: tx.m }}>当前管理员 {admins.length} 人</span>
          </div>
          <AdminDataTable columns={adminColumns} data={admins} emptyMessage="暂无管理员记录" getRowId={admin => String(admin.id)} sizingKey="admin-roles-dense" />
        </div>
      )}

      {/* ====================== LOGS / 操作日志 ====================== */}
      {tab === 'logs' && (
        <div className="space-y-3">
          <div className="flex flex-col gap-2 rounded-2xl border p-4 sm:flex-row sm:items-center sm:justify-between" style={{ background: bg, borderColor: brd }}>
            <div>
              <div className="flex items-center gap-2"><AdminNavIcon kind="logs" size={16} /><h3 className="text-sm font-semibold" style={{ color: tx.p }}>管理员操作日志</h3></div>
              <p className="mt-1 text-[11px]" style={{ color: tx.d }}>按操作者、动作和日期回溯后台变更，记录来自服务端审计表。</p>
            </div>
            <span className="text-[11px]" style={{ color: tx.m }}>共 {logTotal} 条记录</span>
          </div>
          <div className="flex flex-wrap items-center gap-2 rounded-2xl border p-4" style={{ background: 'var(--bg-card)', borderColor: brd }}>
            <input
              value={logFilter.adminId}
              onChange={e => { setLogFilter(f => ({ ...f, adminId: e.target.value })); setLogPage(1) }}
              inputMode="numeric"
              placeholder="管理员 ID"
              className="px-3 py-2 rounded-xl text-xs border outline-none w-28"
              style={{ background:'var(--bg-input)', borderColor:brd, color:tx.p }}
            />
            <select
              value={logFilter.operationType}
              onChange={e => { setLogFilter(f => ({ ...f, operationType: e.target.value })); setLogPage(1) }}
              className="px-3 py-2 rounded-xl text-xs border outline-none"
              style={{ background:'var(--bg-input)', borderColor:brd, color:tx.p }}
            >
              <option value="">全部操作类型</option>
              <option value="USER_MANAGE">用户管理</option>
              <option value="POINTS_MANAGE">点数管理</option>
              <option value="BALANCE_MANAGE">余额管理</option>
              <option value="MEMBERSHIP_MANAGE">会员管理</option>
              <option value="SYSTEM_MANAGE">系统管理</option>
            </select>
            <input
              type="date"
              value={logFilter.startDate}
              onChange={e => { setLogFilter(f => ({ ...f, startDate: e.target.value })); setLogPage(1) }}
              className="px-3 py-2 rounded-xl text-xs border outline-none"
              style={{ background:'var(--bg-input)', borderColor:brd, color:tx.p }}
            />
            <span className="text-xs" style={{ color:tx.m }}>至</span>
            <input
              type="date"
              value={logFilter.endDate}
              onChange={e => { setLogFilter(f => ({ ...f, endDate: e.target.value })); setLogPage(1) }}
              className="px-3 py-2 rounded-xl text-xs border outline-none"
              style={{ background:'var(--bg-input)', borderColor:brd, color:tx.p }}
            />
            <button onClick={loadOperationLogs} disabled={logLoading} className="px-3 py-2 rounded-xl text-xs font-medium text-white disabled:opacity-50" style={{ background:'var(--accent-purple)' }}>{logLoading ? '加载中...' : '刷新日志'}</button>
            <button onClick={() => { setLogFilter({ adminId:'', operationType:'', startDate:'', endDate:'' }); setLogPage(1) }} className="px-3 py-2 rounded-xl text-xs" style={{ color:tx.d, border:`1px solid ${brd}` }}>清除</button>
          </div>

          <div className="flex h-[min(62vh,640px)] min-h-[420px] flex-col overflow-hidden rounded-2xl border" style={{ background:bg, borderColor:brd }}>
            <AdminDataTable columns={logColumns} data={operationLogs} loading={logLoading} emptyMessage="暂无符合条件的管理员操作日志" getRowId={(row, index) => String(row.id || index)} className="min-h-0 flex-1 rounded-none border-0" sizingKey="admin-audit-dense" toolbar={<span className="text-[10px]" style={{ color: tx.m }}>审计日志 · {logTotal} 条 · 列筛选作用于当前页</span>} />

            <div className="flex min-h-[68px] shrink-0 flex-col items-stretch justify-center gap-2 border-t px-3 py-3 text-xs sm:flex-row sm:items-center sm:justify-between" style={{ background: 'var(--bg-card)', borderColor: brd, color:tx.d }}>
              <span className="text-center sm:text-left">{logTotal > 0 ? `共 ${logTotal} 条，第 ${logPage}/${Math.ceil(logTotal / logSize)} 页` : (logLoading ? '正在加载日志' : '暂无日志记录')}</span>
              <div className="flex justify-end gap-2">
                <button disabled={logPage <= 1 || logTotal === 0} onClick={() => setLogPage(p => Math.max(1, p - 1))} className="px-3 py-1.5 rounded-lg border disabled:opacity-30" style={{ borderColor:brd, color:tx.d }}>上一页</button>
                <button disabled={logPage * logSize >= logTotal || logTotal === 0} onClick={() => setLogPage(p => p + 1)} className="px-3 py-1.5 rounded-lg border disabled:opacity-30" style={{ borderColor:brd, color:tx.d }}>下一页</button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ====================== BROADCAST ====================== */}
      {tab==='broadcast' && (
        <div className="rounded-2xl p-5 border" style={{ background:bg, borderColor:brd }}>
          <div className="flex gap-2 mb-5">
            {[['notify','发送通知'],['coupon','发代金券']].map(([k,l]) => (
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
              {bcfSending ? (bcfTab==='notify'?'发送中...':'发放中...') : (bcfTab==='notify'?'发送通知':'发放代金券')}
            </button>
            {bcfResult && <div className="text-xs px-3 py-2 rounded-lg" style={{ background: bcfResult.includes('失败')?'rgba(244,63,94,0.1)':'rgba(16,185,129,0.1)', color: bcfResult.includes('失败')?'#F43F5E':'#10B981' }}>{bcfResult}</div>}
          </div>
        </div>
      )}

      {/* ====================== FINANCE / 财务管理 ====================== */}
      {tab === 'finance' && (
        <div className="space-y-4">
          {/* 概览卡片（复用已加载的 platformRevenue） */}
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
            {[
              { l: '总营收 ¥', v: r ? (r.totalRevenue || 0).toFixed(2) : '0.00', c: COLORS[4] },
              { l: '今日营收 ¥', v: r ? (r.todayRevenue || 0).toFixed(2) : '0.00', c: COLORS[5] },
              { l: '本月营收 ¥', v: r ? (r.monthRevenue || 0).toFixed(2) : '0.00', c: COLORS[6] },
              { l: '订阅收入 ¥', v: r ? (r.subscriptionRevenue || 0).toFixed(2) : '0.00', c: COLORS[7] },
            ].map((k, i) => (
              <div key={i} className="rounded-2xl p-4" style={{ background: bg, border: `1px solid ${brd}` }}>
                <div className="text-[10px] mb-1" style={{ color: tx.m }}>{k.l}</div>
                <div className="text-xl font-bold tabular-nums" style={{ color: k.c }}>{k.v}</div>
              </div>
            ))}
          </div>

          {/* 子 tab + 筛选 */}
          <div className="flex flex-wrap items-center gap-2">
            {([['recharge', '充值记录'], ['consumption', '消费明细'], ['orders', '订单管理'], ['manual-review', 'AI 人工对账']] as const).map(([k, l]) => (
              <button key={k} onClick={() => { setFinTab(k); setFinPage(1) }}
                className="px-3 py-1.5 rounded-lg text-[11px] font-medium transition-all"
                style={{ background: finTab === k ? 'var(--accent-purple)' : 'var(--bg-surface)', color: finTab === k ? '#fff' : tx.d, border: `1px solid ${finTab === k ? 'var(--accent-purple)' : brd}` }}>
                {l}
              </button>
            ))}
            {finTab !== 'manual-review' && <div className="flex items-center gap-1 ml-auto">
              <input type="date" value={finDate.start} onChange={e => { setFinDate(d => ({ ...d, start: e.target.value })); setFinPage(1) }}
                className="px-2 py-1 rounded-lg text-[11px] outline-none" style={{ background: 'var(--bg-surface)', color: tx.p, border: `1px solid ${brd}` }} />
              <span className="text-[10px]" style={{ color: tx.m }}>~</span>
              <input type="date" value={finDate.end} onChange={e => { setFinDate(d => ({ ...d, end: e.target.value })); setFinPage(1) }}
                className="px-2 py-1 rounded-lg text-[11px] outline-none" style={{ background: 'var(--bg-surface)', color: tx.p, border: `1px solid ${brd}` }} />
              {finTab === 'orders' && (
                <select value={finStatus} onChange={e => { setFinStatus(e.target.value); setFinPage(1) }}
                  className="px-2 py-1 rounded-lg text-[11px] outline-none" style={{ background: 'var(--bg-surface)', color: tx.p, border: `1px solid ${brd}` }}>
                  <option value="">全部状态</option>
                  <option value="ACTIVE">生效中</option>
                  <option value="EXPIRED">已过期</option>
                  <option value="CANCELLED">已取消</option>
                </select>
              )}
              <button onClick={() => loadFinance()} className="px-3 py-1 rounded-lg text-[11px] font-medium" style={{ background: 'var(--accent-purple)', color: '#fff' }}>查询</button>
            </div>}
          </div>

          {finTab === 'manual-review' ? <ManualChargeReviewPanel canResolve={isSuperAdmin} adminId={currentAdminId} /> : <>
          {/* 订单汇总 */}
          {finTab === 'orders' && finData.totalPaid !== undefined && (
            <div className="text-xs" style={{ color: tx.d }}>筛选范围内实收合计：<span className="font-bold" style={{ color: COLORS[4] }}>¥{Number(finData.totalPaid).toFixed(2)}</span>　共 {finData.total} 笔</div>
          )}

          {/* 数据表格 */}
          <div className="flex h-[min(62vh,640px)] min-h-[420px] flex-col overflow-hidden rounded-2xl" style={{ background: bg, border: `1px solid ${brd}` }}>
            <AdminDataTable columns={financeColumns} data={finData.records} loading={finLoading} emptyMessage="暂无数据" getRowId={(row, index) => String(row.id || row.orderId || index)} className="min-h-0 flex-1 rounded-none border-0" sizingKey={`admin-finance-${finTab}`} toolbar={<span className="text-[10px]" style={{ color: tx.m }}>计费与订单 · {finData.total} 条 · 列筛选作用于当前页</span>} />

          {/* 分页 */}
            <div className="flex min-h-[68px] shrink-0 flex-col items-stretch justify-center gap-2 border-t px-3 py-3 sm:flex-row sm:items-center sm:justify-between" style={{ background: 'var(--bg-card)', borderColor: brd }}>
              <span className="text-center text-[10px] sm:text-left" style={{ color: finData.total > 0 ? tx.m : tx.d }}>{finData.total > 0 ? `共 ${finData.total} 条，第 ${finPage}/${Math.ceil(finData.total / finSize)} 页` : (finLoading ? '正在加载数据' : '暂无数据')}</span>
              <div className="flex justify-end gap-1">
                <button disabled={finPage <= 1 || finData.total === 0} onClick={() => setFinPage(p => p - 1)} className="px-3 py-1 rounded-lg text-[11px] disabled:opacity-30" style={{ background: 'var(--bg-surface)', color: tx.d, border: `1px solid ${brd}` }}>上一页</button>
                <button disabled={finPage * finSize >= finData.total || finData.total === 0} onClick={() => setFinPage(p => p + 1)} className="px-3 py-1 rounded-lg text-[11px] disabled:opacity-30" style={{ background: 'var(--bg-surface)', color: tx.d, border: `1px solid ${brd}` }}>下一页</button>
              </div>
            </div>
          </div>
          </>}
        </div>
      )}

      {/* ====================== SYSTEM / 系统控制 ====================== */}
      {tab === 'system' && (
        <div className="space-y-4">
          {/* 二级密码验证 */}
          {!sysVerified ? (
            <div className="rounded-2xl p-6 border max-w-md mx-auto" style={{ background: bg, borderColor: brd }}>
              <h3 className="text-sm font-semibold mb-2 text-center flex items-center justify-center gap-2" style={{ color: tx.p }}><ControlIcon kind="lock" size={15} />系统控制二级验证</h3>
              <p className="text-[10px] mb-4 text-center" style={{ color: tx.d }}>请输入二级密码以访问系统控制功能</p>
              <input type="password" value={sysPassword} onChange={e => setSysPassword(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && verifySecondary()}
                placeholder="二级密码" autoFocus
                className="w-full px-4 py-2.5 rounded-xl text-sm text-center border outline-none mb-3" style={{ background:'var(--bg-input)', borderColor:brd, color:tx.p }} />
              <button onClick={verifySecondary} disabled={sysVerifying || !sysPassword}
                className="w-full py-2.5 rounded-xl text-xs font-medium text-white transition-all" style={{ background: sysVerifying?'var(--accent-purple)50':'var(--accent-purple)' }}>
                {sysVerifying ? '验证中...' : '验证'}
              </button>
              {sysMsg && <div className="text-xs px-3 py-2 rounded-lg mt-3" style={{ background:'rgba(244,63,94,0.1)', color:'#F43F5E' }}>{sysMsg}</div>}
            </div>
          ) : (
            <>
              {/* 操作提示消息 */}
              {sysMsg && <div className="px-4 py-3 rounded-xl text-xs" style={{ background: sysMsg.includes('失败')||sysMsg.includes('错误')?'rgba(244,63,94,0.1)':'rgba(16,185,129,0.1)', color: sysMsg.includes('失败')||sysMsg.includes('错误')?'#F43F5E':'#10B981' }}>{sysMsg}</div>}

              {/* 环境控制按钮 */}
              <div className="rounded-2xl p-4 border" style={{ background: bg, borderColor: brd }}>
                <div className="flex items-center justify-between mb-3">
                  <h3 className="text-xs font-semibold" style={{ color: tx.p }}>环境控制</h3>
                  <button onClick={loadContainerStatus} disabled={sysLoading}
                    className="px-3 py-1 rounded-lg text-[10px] font-medium border transition-all" style={{ borderColor:brd, color:tx.d }}>
                    <span className="inline-flex items-center gap-1.5">{sysLoading ? null : <ControlIcon kind="refresh" size={12} />}{sysLoading ? '刷新中...' : '刷新状态'}</span>
                  </button>
                </div>
                <div className="flex gap-2">
                  <button onClick={() => handleEnvironmentAction('start')}
                    className="flex-1 py-2.5 rounded-xl text-xs font-medium text-white transition-all" style={{ background:'#10B981' }}>
                    <span className="inline-flex items-center justify-center gap-1.5"><ControlIcon kind="play" size={13} />一键启动环境</span>
                  </button>
                  <button onClick={() => handleEnvironmentAction('stop')}
                    className="flex-1 py-2.5 rounded-xl text-xs font-medium text-white transition-all" style={{ background:'#F43F5E' }}>
                    <span className="inline-flex items-center justify-center gap-1.5"><ControlIcon kind="stop" size={13} />一键停止环境</span>
                  </button>
                </div>
                <p className="text-[10px] mt-2" style={{ color: tx.m }}>环境 = MySQL + Redis + 前端（不含后端）</p>
              </div>

              {/* 容器状态卡片 */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {containers.map(c => (
                  <div key={c.name} className="rounded-2xl p-4 border" style={{ background: bg, borderColor: brd }}>
                    {/* 容器头部 */}
                    <div className="flex items-center justify-between mb-3">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-semibold" style={{ color: tx.p }}>
                          <span className="inline-flex items-center gap-1.5">{c.name === 'mysql' ? <><ControlIcon kind="database" size={14} />MySQL</> : c.name === 'redis' ? <><ControlIcon kind="cache" size={14} />Redis</> : c.name === 'backend' ? <><ControlIcon kind="backend" size={14} />后端</> : <><ControlIcon kind="frontend" size={14} />前端</>}</span>
                        </span>
                        <span className="text-[10px] px-2 py-0.5 rounded-full" style={{
                          background: c.running ? 'rgba(16,185,129,0.15)' : 'rgba(244,63,94,0.15)',
                          color: c.running ? '#10B981' : '#F43F5E'
                        }}>
                          <span className="inline-block w-1.5 h-1.5 rounded-full mr-1" style={{ background: c.running ? '#10B981' : '#F43F5E' }} />{c.state}
                        </span>
                      </div>
                      <button onClick={() => toggleLogs(c.name)} className="text-[10px] px-2 py-1 rounded-lg border transition-all" style={{ borderColor:brd, color:tx.d }}>
                        {expandedLogs === c.name ? '收起日志' : '查看日志'}
                      </button>
                    </div>

                    {/* 容器信息 */}
                    <div className="text-[10px] space-y-1 mb-3" style={{ color: tx.d }}>
                      <div>容器名: <span style={{ color:tx.p }}>{c.containerName}</span></div>
                      {c.startedAt && <div>启动时间: {c.startedAt}</div>}
                      {c.image && <div className="truncate">镜像: {c.image}</div>}
                    </div>

                    {/* 控制按钮 */}
                    <div className="flex gap-2">
                      {c.name !== 'backend' && (
                        <>
                          <button onClick={() => handleContainerAction(c.name, 'start')} disabled={!c.running ? false : true}
                            className="flex-1 py-1.5 rounded-lg text-[10px] font-medium transition-all"
                            style={{ background: c.running ? 'rgba(16,185,129,0.1)' : '#10B981', color: c.running ? '#10B98180' : '#fff', border: c.running ? '1px solid rgba(16,185,129,0.2)' : 'none' }}>
                            启动
                          </button>
                          <button onClick={() => handleContainerAction(c.name, 'stop')} disabled={c.running ? false : true}
                            className="flex-1 py-1.5 rounded-lg text-[10px] font-medium transition-all"
                            style={{ background: !c.running ? 'rgba(244,63,94,0.1)' : '#F43F5E', color: !c.running ? '#F43F5E80' : '#fff', border: !c.running ? '1px solid rgba(244,63,94,0.2)' : 'none' }}>
                            停止
                          </button>
                        </>
                      )}
                      <button onClick={() => handleContainerAction(c.name, 'restart')}
                        className="flex-1 py-1.5 rounded-lg text-[10px] font-medium text-white transition-all" style={{ background:'#F59E0B' }}>
                        重启
                      </button>
                      {c.name === 'backend' && (
                        <span className="text-[10px] flex items-center" style={{ color: tx.m }}>后端仅支持重启</span>
                      )}
                    </div>

                    {/* 日志展开区 */}
                    {expandedLogs === c.name && (
                      <div className="mt-3 p-2 rounded-lg max-h-48 overflow-auto" style={{ background: dark ? 'rgba(0,0,0,0.3)' : 'rgba(0,0,0,0.05)' }}>
                        {containerLogs.length === 0 ? (
                          <div className="text-[10px] text-center" style={{ color: tx.m }}>暂无日志</div>
                        ) : (
                          containerLogs.slice(-50).map((log, i) => (
                            <div key={i} className="text-[10px] font-mono whitespace-pre-wrap break-all" style={{ color: dark ? '#94a3b8' : '#475569' }}>{log}</div>
                          ))
                        )}
                      </div>
                    )}
                  </div>
                ))}
              </div>

              {/* 退出二级验证 */}
              <div className="text-center">
                <button onClick={() => { setSysVerified(false); sessionStorage.removeItem('lingdian_secondary_token'); setContainers([]) }}
                  className="text-[10px] px-3 py-1 rounded-lg border transition-all" style={{ borderColor:brd, color:tx.m }}>
                  <span className="inline-flex items-center gap-1.5"><ControlIcon kind="lock" size={12} />退出二级验证</span>
                </button>
              </div>
            </>
          )}
        </div>
      )}

          </main>
        </div>
      </div>

      <ConfirmDialog
        open={pendingStatusUser !== null}
        title={pendingStatusUser?.status === 'ACTIVE' ? '确认封禁用户' : '确认解封用户'}
        message={pendingStatusUser ? `${pendingStatusUser.status === 'ACTIVE' ? '封禁' : '解封'}用户「${pendingStatusUser.username}」？` : ''}
        confirmText={pendingStatusUser?.status === 'ACTIVE' ? '确认封禁' : '确认解封'}
        cancelText="取消"
        variant={pendingStatusUser?.status === 'ACTIVE' ? 'danger' : 'info'}
        onConfirm={() => { if (!statusUpdating) void toggleStatus() }}
        onCancel={() => { if (!statusUpdating) setPendingStatusUser(null) }}
      />

      <ConfirmDialog
        open={pendingRoleUser !== null}
        title="授予管理员角色"
        message={pendingRoleUser ? `确认将用户「${pendingRoleUser.username}」设为管理员？该变更会立即影响后台访问权限，并记录到审计日志。` : ''}
        confirmText="确认授予"
        cancelText="取消"
        variant="info"
        onConfirm={() => { if (!roleUpdating) void assignAdmin() }}
        onCancel={() => { if (!roleUpdating) setPendingRoleUser(null) }}
      />

      <AdminUserDetailDrawer
        user={selectedUser}
        dark={dark}
        isSuperAdmin={isSuperAdmin}
        onClose={() => setSelectedUser(null)}
        onAction={(user, action) => { setSelectedUser(null); setUserAction({ user: user as AdminUser, action }) }}
        onToggleStatus={user => { setSelectedUser(null); requestToggleStatus(user as AdminUser) }}
        onGrantAdmin={user => { setSelectedUser(null); setPendingRoleUser(user as AdminUser) }}
        onProfileChanged={loadUsers}
      />

      <AdminUserActionDialog
        user={userAction?.user || null}
        action={userAction?.action || null}
        dark={dark}
        onClose={() => setUserAction(null)}
        onSuccess={async () => { await loadUsers() }}
      />
    </div>
  )
}
