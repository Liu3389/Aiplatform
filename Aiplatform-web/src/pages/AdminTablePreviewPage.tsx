import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from 'react'
import { useNavigate } from 'react-router-dom'
import UserAvatar from '@/components/UserAvatar'
import { useThemeStore } from '@/stores/themeStore'
import './AdminTablePreviewPage.css'

type PreviewStyle = 'calm' | 'dense' | 'cards'
type ColumnId = 'username' | 'id' | 'email' | 'membership' | 'role' | 'status' | 'points' | 'balance' | 'joined'

interface PreviewUser {
  id: string
  username: string
  email: string
  membership: string
  role: string
  status: string
  points: string
  balance: string
  joined: string
}

interface PreviewColumn {
  id: ColumnId
  label: string
  width: number
  min: number
  max: number
  grow: number
}

const columns: PreviewColumn[] = [
  { id: 'username', label: '用户', width: 210, min: 160, max: 310, grow: 2.2 },
  { id: 'id', label: '用户 ID', width: 96, min: 78, max: 150, grow: 0.3 },
  { id: 'email', label: '邮箱', width: 224, min: 160, max: 340, grow: 1.7 },
  { id: 'membership', label: '会员等级', width: 112, min: 95, max: 160, grow: 0.5 },
  { id: 'role', label: '角色', width: 110, min: 90, max: 145, grow: 0.3 },
  { id: 'status', label: '账户状态', width: 112, min: 95, max: 150, grow: 0.35 },
  { id: 'points', label: '点数余额', width: 122, min: 100, max: 165, grow: 0.4 },
  { id: 'balance', label: '账户金额', width: 122, min: 100, max: 165, grow: 0.4 },
  { id: 'joined', label: '注册时间', width: 145, min: 125, max: 180, grow: 0.5 },
]

const sampleUsers: PreviewUser[] = [
  { id: '10021', username: 'superadmin', email: 'admin@lingdian-ai.top', membership: '钻石', role: '超级管理员', status: '正常', points: '12,840', balance: '¥2,408.00', joined: '2025-10-14' },
  { id: '10386', username: '林间晚风', email: 'lin.wan@example.com', membership: '黄金', role: '用户', status: '正常', points: '2,160', balance: '¥80.00', joined: '2026-02-06' },
  { id: '10412', username: 'codewalker', email: 'cw.dev@example.com', membership: '白银', role: '用户', status: '正常', points: '780', balance: '¥35.50', joined: '2026-03-19' },
  { id: '10608', username: '北岛', email: 'beidao@example.com', membership: '普通', role: '用户', status: '禁用', points: '0', balance: '¥0.00', joined: '2026-05-22' },
  { id: '10837', username: 'Mira Chen', email: 'mira.chen@example.com', membership: '钻石', role: '管理员', status: '正常', points: '8,520', balance: '¥320.00', joined: '2026-07-03' },
  { id: '10914', username: '小满', email: 'xiaoman@example.com', membership: '白银', role: '用户', status: '正常', points: '460', balance: '¥12.00', joined: '2026-08-16' },
  { id: '11029', username: '夜航船', email: 'night.sail@example.com', membership: '普通', role: '用户', status: '正常', points: '90', balance: '¥0.00', joined: '2026-09-01' },
  { id: '11062', username: 'dev_lei', email: 'lei.build@example.com', membership: '黄金', role: '用户', status: '禁用', points: '1,240', balance: '¥66.00', joined: '2026-09-27' },
]

const styleOptions: Array<{ id: PreviewStyle; label: string; subtitle: string }> = [
  { id: 'calm', label: 'A · 清爽后台', subtitle: '留白适中，分隔轻，推荐日常管理' },
  { id: 'dense', label: 'B · 紧凑数据台', subtitle: '桌面一屏容纳更多记录，手机自动转为字段卡片' },
  { id: 'cards', label: 'C · 用户目录卡片', subtitle: '信息分组清晰，手机端转为卡片' },
]

function Icon({ name, size = 16 }: { name: 'search' | 'sliders' | 'reset' | 'filter' | 'back' | 'check' | 'close'; size?: number }) {
  const common = { width: size, height: size, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.8, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const, 'aria-hidden': true as const }
  if (name === 'search') return <svg {...common}><circle cx="10.8" cy="10.8" r="6.8" /><path d="m16 16 4.5 4.5" /></svg>
  if (name === 'sliders') return <svg {...common}><path d="M4 7h9M17 7h3M4 17h3M11 17h9" /><circle cx="15" cy="7" r="2" /><circle cx="9" cy="17" r="2" /></svg>
  if (name === 'reset') return <svg {...common}><path d="M20 11a8 8 0 0 0-14.7-4L4 9" /><path d="M4 4v5h5M4 13a8 8 0 0 0 14.7 4L20 15" /><path d="M20 20v-5h-5" /></svg>
  if (name === 'filter') return <svg {...common}><path d="M4 5h16l-6.2 7.2v5.4l-3.6 1.9v-7.3L4 5Z" /></svg>
  if (name === 'back') return <svg {...common}><path d="m15 18-6-6 6-6M9 12h11" /></svg>
  if (name === 'check') return <svg {...common}><path d="m5 12 4 4L19 6" /></svg>
  return <svg {...common}><path d="m6 6 12 12M18 6 6 18" /></svg>
}

function calculateDefaultWidths(containerWidth: number): Record<ColumnId, number> {
  const widths = Object.fromEntries(columns.map(column => [column.id, column.width])) as Record<ColumnId, number>
  let delta = containerWidth - columns.reduce((sum, column) => sum + column.width, 0)

  for (let pass = 0; pass < columns.length && Math.abs(delta) > 0.5; pass += 1) {
    const eligible = columns.filter(column => delta > 0 ? widths[column.id] < column.max : widths[column.id] > column.min)
    if (!eligible.length) break
    const totalGrow = eligible.reduce((sum, column) => sum + column.grow, 0)
    let consumed = 0
    eligible.forEach((column, index) => {
      const share = index === eligible.length - 1 ? delta - consumed : delta * column.grow / totalGrow
      const next = Math.max(column.min, Math.min(column.max, widths[column.id] + share))
      consumed += next - widths[column.id]
      widths[column.id] = next
    })
    if (Math.abs(consumed) < 0.5) break
    delta -= consumed
  }
  return widths
}

function avatarColor(username: string) {
  const palette = ['#7C3AED', '#2563EB', '#0891B2', '#059669', '#D97706', '#DB2777']
  const hash = Array.from(username).reduce((value, letter) => value + letter.charCodeAt(0), 0)
  return palette[hash % palette.length]
}

function membershipTone(value: string) {
  if (value === '钻石') return { color: '#7C3AED', background: 'rgba(124,58,237,.10)' }
  if (value === '黄金') return { color: '#B45309', background: 'rgba(217,119,6,.11)' }
  if (value === '白银') return { color: '#475569', background: 'rgba(100,116,139,.12)' }
  return { color: '#64748B', background: 'rgba(100,116,139,.08)' }
}

export default function AdminTablePreviewPage() {
  const navigate = useNavigate()
  const { theme, toggleTheme } = useThemeStore()
  const isDark = theme === 'dark'
  const tableScroller = useRef<HTMLDivElement>(null)
  const dragState = useRef<{ id: ColumnId; startX: number; startWidth: number } | null>(null)
  const hasManualWidth = useRef(false)
  const [style, setStyle] = useState<PreviewStyle>('dense')
  const [widths, setWidths] = useState<Record<ColumnId, number>>(() => calculateDefaultWidths(1320))
  const [visibleColumns, setVisibleColumns] = useState<ColumnId[]>(columns.map(column => column.id))
  const [search, setSearch] = useState('')
  const [filters, setFilters] = useState<Partial<Record<ColumnId, string>>>({})
  const [openFilter, setOpenFilter] = useState<ColumnId | null>(null)
  const [showColumnMenu, setShowColumnMenu] = useState(false)

  useEffect(() => {
    const element = tableScroller.current
    if (!element) return
    const update = () => {
      if (!hasManualWidth.current) setWidths(calculateDefaultWidths(Math.max(element.clientWidth - 2, 640)))
    }
    update()
    const observer = new ResizeObserver(update)
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    const move = (event: PointerEvent) => {
      const drag = dragState.current
      if (!drag) return
      const column = columns.find(item => item.id === drag.id)
      if (!column) return
      const next = Math.max(column.min, Math.min(column.max, drag.startWidth + event.clientX - drag.startX))
      hasManualWidth.current = true
      setWidths(current => ({ ...current, [drag.id]: next }))
    }
    const stop = () => { dragState.current = null }
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', stop)
    window.addEventListener('pointercancel', stop)
    return () => {
      window.removeEventListener('pointermove', move)
      window.removeEventListener('pointerup', stop)
      window.removeEventListener('pointercancel', stop)
    }
  }, [])

  const filteredUsers = useMemo(() => sampleUsers.filter(user => {
    const quickText = search.trim().toLowerCase()
    const matchesSearch = !quickText || Object.values(user).some(value => value.toLowerCase().includes(quickText))
    return matchesSearch && columns.every(column => {
      const value = filters[column.id]?.trim().toLowerCase()
      return !value || user[column.id].toLowerCase().includes(value)
    })
  }), [filters, search])

  const resetWidths = useCallback(() => {
    hasManualWidth.current = false
    const element = tableScroller.current
    setWidths(calculateDefaultWidths(Math.max((element?.clientWidth || 1320) - 2, 640)))
  }, [])

  const gridTemplate = visibleColumns.map(id => `${widths[id]}px`).join(' ')
  const totalWidth = visibleColumns.reduce((sum, id) => sum + widths[id], 0)
  const pageStyle = {
    '--atp-header-bg': isDark ? '#111827' : '#FFFFFF',
    '--atp-header-border': isDark ? 'rgba(255,255,255,.12)' : 'rgba(15,23,42,.12)',
    '--atp-head-muted': isDark ? '#AAB6C8' : '#526176',
  } as CSSProperties

  const renderCell = (user: PreviewUser, column: PreviewColumn) => {
    if (column.id === 'username') {
      return <div className="atp-account"><UserAvatar name={user.username} className="atp-avatar" textClassName="atp-avatar-letter" style={{ background: avatarColor(user.username) }} /><span className="atp-account-copy"><strong>{user.username}</strong><small>{user.email}</small></span></div>
    }
    if (column.id === 'membership') {
      const tone = membershipTone(user.membership)
      return <span className="atp-tag" style={{ color: tone.color, background: tone.background }}>{user.membership}会员</span>
    }
    if (column.id === 'role') return <span className={user.role === '超级管理员' ? 'atp-role atp-role-admin' : 'atp-role'}>{user.role}</span>
    if (column.id === 'status') return <span className={`atp-status ${user.status === '正常' ? 'is-active' : 'is-disabled'}`}><i />{user.status}</span>
    if (column.id === 'points') return <span className="atp-numeric">{user.points}<small> 点</small></span>
    return <span className={column.id === 'id' ? 'atp-mono' : ''}>{user[column.id]}</span>
  }

  return (
    <main className="atp-page page-scroll" style={pageStyle}>
      <div className="atp-shell">
        <header className="atp-topbar">
          <div className="atp-breadcrumb"><button type="button" onClick={() => navigate('/admin')}><Icon name="back" size={15} /> 管理后台</button><span>/</span><span>表格预览</span><span className="atp-preview-badge">临时预览 · 示例数据</span></div>
          <button type="button" className="atp-ghost-button" onClick={toggleTheme}>{isDark ? '切换明亮模式' : '切换暗色模式'}</button>
        </header>

        <section className="atp-heading">
          <div>
            <p className="atp-eyebrow">ADMIN DATA TABLES</p>
            <h1>管理表格 · 效果预览</h1>
            <p className="atp-description">同一组用户数据用于比较表格视觉。正式后台已采用紧凑型数据台；桌面支持列宽、字段筛选与字段显示，手机端自动改为可读卡片。</p>
          </div>
          <div className="atp-library-picks">
            <article className="atp-library-card is-recommended">
              <div className="atp-library-title"><strong>TanStack Table</strong><span>推荐</span></div>
              <p>开源、无 UI、可完整贴合凌点风格；适合统一现有后台，不强迫换整套设计。</p>
              <small>列宽 · 筛选 · 列显示状态由表格逻辑层管理</small>
            </article>
            <article className="atp-library-card">
              <div className="atp-library-title"><strong>AG Grid Community</strong><span>备选</span></div>
              <p>开箱即用的完整数据网格，适合大量 Excel 式操作；默认观感更偏企业数据工具。</p>
              <small>社区版 MIT；高级企业功能另有商业许可</small>
            </article>
          </div>
        </section>

        <section className="atp-style-section" aria-label="表格样式选择">
          <div className="atp-section-title"><div><strong>选择视觉方案</strong><span>切换时保留当前列宽和筛选</span></div><span className="atp-current-choice">当前：{styleOptions.find(option => option.id === style)?.label}</span></div>
          <div className="atp-style-options">
            {styleOptions.map(option => (
              <button key={option.id} type="button" className={`atp-style-option ${style === option.id ? 'is-selected' : ''}`} onClick={() => setStyle(option.id)} aria-pressed={style === option.id}>
                <span className="atp-option-radio">{style === option.id && <Icon name="check" size={12} />}</span>
                <span><strong>{option.label}</strong><small>{option.subtitle}</small></span>
              </button>
            ))}
          </div>
        </section>

        <section className="atp-table-panel">
          <div className="atp-table-heading">
            <div className="atp-table-title"><span className="atp-title-mark" /><div><h2>用户目录</h2><p>共 {filteredUsers.length} 位示例用户 · {visibleColumns.length} 个显示字段</p></div></div>
            <div className="atp-controls">
              <label className="atp-search"><Icon name="search" size={15} /><input value={search} onChange={event => setSearch(event.target.value)} placeholder="搜索用户、邮箱或 ID" /></label>
              <div className="atp-menu-anchor">
                <button type="button" className={`atp-ghost-button ${showColumnMenu ? 'is-active' : ''}`} onClick={() => setShowColumnMenu(value => !value)}><Icon name="sliders" size={15} /> 字段 <span>{visibleColumns.length}/{columns.length}</span></button>
                {showColumnMenu && <div className="atp-column-menu"><div className="atp-popover-heading"><strong>显示字段</strong><button type="button" onClick={() => setVisibleColumns(columns.map(column => column.id))}>全部显示</button></div>{columns.map(column => <label key={column.id}><input type="checkbox" checked={visibleColumns.includes(column.id)} onChange={() => setVisibleColumns(current => current.includes(column.id) ? current.length > 1 ? current.filter(id => id !== column.id) : current : [...current, column.id])} /><span>{column.label}</span></label>)}</div>}
              </div>
              <button type="button" className="atp-ghost-button" onClick={resetWidths} title="按当前区域重新计算默认列宽"><Icon name="reset" size={15} /><span className="atp-reset-label">默认列宽</span></button>
            </div>
          </div>

          {(Object.values(filters).some(Boolean) || search) && <div className="atp-active-filters"><Icon name="filter" size={14} /><span>筛选中</span>{search && <button type="button" onClick={() => setSearch('')}>关键词：{search}<Icon name="close" size={12} /></button>}{columns.filter(column => filters[column.id]).map(column => <button key={column.id} type="button" onClick={() => setFilters(current => ({ ...current, [column.id]: '' }))}>{column.label}：{filters[column.id]}<Icon name="close" size={12} /></button>)}<button className="atp-clear-filters" type="button" onClick={() => { setFilters({}); setSearch('') }}>清空全部</button></div>}

          <div className={`atp-grid atp-grid-${style}`}>
            <div ref={tableScroller} className="atp-scroller">
              <div className="atp-grid-inner" style={{ minWidth: `${Math.max(totalWidth, 700)}px` }}>
                <div className="atp-grid-head" style={{ gridTemplateColumns: gridTemplate }}>
                  {columns.filter(column => visibleColumns.includes(column.id)).map(column => (
                    <div className={`atp-head-cell ${filters[column.id] ? 'has-filter' : ''}`} key={column.id}>
                      <span className="atp-head-label">{column.label}</span>
                      <button type="button" className="atp-filter-trigger" aria-label={`筛选${column.label}`} title={`筛选${column.label}`} onClick={() => setOpenFilter(openFilter === column.id ? null : column.id)}><Icon name="filter" size={13} /></button>
                      {openFilter === column.id && <div className="atp-filter-popover"><strong>筛选：{column.label}</strong><input autoFocus value={filters[column.id] || ''} onChange={event => setFilters(current => ({ ...current, [column.id]: event.target.value }))} placeholder={`输入${column.label}关键词`} onKeyDown={event => { if (event.key === 'Escape' || event.key === 'Enter') setOpenFilter(null) }} /><div><button type="button" onClick={() => { setFilters(current => ({ ...current, [column.id]: '' })); setOpenFilter(null) }}>清除</button><button type="button" className="is-primary" onClick={() => setOpenFilter(null)}>完成</button></div></div>}
                      <button type="button" className="atp-resize-handle" aria-label={`调整${column.label}列宽`} title="拖动调整列宽；双击恢复该列默认宽度" onPointerDown={event => { event.preventDefault(); event.stopPropagation(); dragState.current = { id: column.id, startX: event.clientX, startWidth: widths[column.id] }; hasManualWidth.current = true }} onDoubleClick={() => { const element = tableScroller.current; setWidths(current => ({ ...current, [column.id]: calculateDefaultWidths(Math.max((element?.clientWidth || 1320) - 2, 640))[column.id] })) }} />
                    </div>
                  ))}
                </div>
                <div className="atp-grid-body">
                  {filteredUsers.map(user => (
                    <div className="atp-grid-row" key={user.id} style={{ gridTemplateColumns: gridTemplate }}>
                      {columns.filter(column => visibleColumns.includes(column.id)).map(column => <div className="atp-cell" key={column.id} data-label={column.label}>{renderCell(user, column)}</div>)}
                    </div>
                  ))}
                  {filteredUsers.length === 0 && <div className="atp-empty">没有符合当前筛选条件的用户</div>}
                </div>
              </div>
            </div>
            <footer className="atp-table-footer"><span>示例数据；正式后台表格支持服务端查询，列内筛选作用于当前页。</span><span>预览列宽仅在本次页面生效，不保存用户偏好</span></footer>
          </div>
        </section>

        <p className="atp-prototype-note">此页使用示例账号，不读取或修改真实用户资料。紧凑数据台已接入正式管理后台的用户、管理员、日志、财务与一致性表格。</p>
      </div>
    </main>
  )
}
