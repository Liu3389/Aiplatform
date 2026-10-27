import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import {
  flexRender,
  getCoreRowModel,
  getFilteredRowModel,
  useReactTable,
  type ColumnDef,
  type ColumnFiltersState,
  type RowData,
  type VisibilityState,
} from '@tanstack/react-table'
import './AdminDataTable.css'

interface AdminColumnMeta {
  grow?: number
}

declare module '@tanstack/react-table' {
  interface ColumnMeta<TData extends RowData, TValue> {
    grow?: number
  }
}

interface Props<T extends RowData> {
  columns: ColumnDef<T, any>[]
  data: T[]
  loading?: boolean
  emptyMessage?: string
  getRowId?: (row: T, index: number) => string
  onRowClick?: (row: T) => void
  toolbar?: ReactNode
  className?: string
  sizingKey?: string
}

function readGrow<TData extends RowData, TValue>(column: ColumnDef<TData, TValue>) {
  return Math.max(0.1, (column.meta as AdminColumnMeta | undefined)?.grow ?? 1)
}

export default function AdminDataTable<T extends RowData>({
  columns,
  data,
  loading = false,
  emptyMessage = '暂无数据',
  getRowId,
  onRowClick,
  toolbar,
  className = '',
  sizingKey = '',
}: Props<T>) {
  const viewportRef = useRef<HTMLDivElement>(null)
  const manuallyResized = useRef(false)
  const [columnSizing, setColumnSizing] = useState<Record<string, number>>({})
  const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>([])
  const [columnVisibility, setColumnVisibility] = useState<VisibilityState>({})
  const [filteringColumn, setFilteringColumn] = useState<string | null>(null)
  const [showColumns, setShowColumns] = useState(false)
  const [showMobileFilters, setShowMobileFilters] = useState(false)
  const sizingSignature = useMemo(
    () => `${sizingKey}:${columns.map(column => column.id || ('accessorKey' in column ? String(column.accessorKey) : '')).join('|')}`,
    [columns, sizingKey],
  )

  const table = useReactTable({
    data,
    columns,
    getRowId: getRowId ? (row, index) => getRowId(row, index) : undefined,
    state: { columnSizing, columnFilters, columnVisibility },
    onColumnSizingChange: updater => {
      manuallyResized.current = true
      setColumnSizing(previous => typeof updater === 'function' ? updater(previous) : updater)
    },
    onColumnFiltersChange: setColumnFilters,
    onColumnVisibilityChange: setColumnVisibility,
    columnResizeMode: 'onEnd',
    defaultColumn: { minSize: 88, maxSize: 560, size: 150, filterFn: 'includesString' },
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
  })

  const applyComputedWidths = () => {
    const containerWidth = viewportRef.current?.clientWidth || 0
    if (!containerWidth) return
    const visible = table.getVisibleLeafColumns()
    const values = visible.map(column => ({
      id: column.id,
      size: column.columnDef.size ?? 150,
      min: column.columnDef.minSize ?? 88,
      max: column.columnDef.maxSize ?? 560,
      grow: readGrow(column.columnDef),
    }))
    let delta = containerWidth - values.reduce((sum, item) => sum + item.size, 0)
    for (let pass = 0; pass < values.length && Math.abs(delta) > 0.5; pass += 1) {
      const eligible = values.filter(item => delta > 0 ? item.size < item.max : item.size > item.min)
      if (!eligible.length) break
      const totalGrow = eligible.reduce((sum, item) => sum + item.grow, 0)
      let consumed = 0
      eligible.forEach((item, index) => {
        const share = index === eligible.length - 1 ? delta - consumed : delta * item.grow / totalGrow
        const next = Math.min(item.max, Math.max(item.min, item.size + share))
        consumed += next - item.size
        item.size = next
      })
      if (Math.abs(consumed) < 0.5) break
      delta -= consumed
    }
    manuallyResized.current = false
    setColumnSizing(Object.fromEntries(values.map(item => [item.id, item.size])))
  }

  useEffect(() => {
    manuallyResized.current = false
    const element = viewportRef.current
    if (!element) return
    applyComputedWidths()
    const observer = new ResizeObserver(() => {
      if (!manuallyResized.current) applyComputedWidths()
    })
    observer.observe(element)
    return () => observer.disconnect()
    // Column IDs define a table variant; visual changes to those columns must not erase a user's drag.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sizingSignature])

  const visibleRows = table.getRowModel().rows
  const tableWidth = Math.max(table.getTotalSize(), viewportRef.current?.clientWidth || 0)

  return (
    <section className={`admin-data-table ${className}`}>
      {(toolbar || table.getAllLeafColumns().length > 0) && (
        <div className="admin-data-table__toolbar">
          <div className="min-w-0 flex-1">{toolbar}</div>
          <div className="relative shrink-0 admin-data-table__mobile-filter-control">
            <button type="button" className="admin-data-table__tool" aria-expanded={showMobileFilters} onClick={() => setShowMobileFilters(value => !value)}>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M4 5h16l-6 7v6l-4 2v-8L4 5Z"/></svg>
              筛选
            </button>
            {showMobileFilters && (
              <div className="admin-data-table__mobile-filter-menu">
                <div className="admin-data-table__menu-title">按字段筛选当前页</div>
                {table.getAllLeafColumns().filter(column => column.getCanFilter()).map(column => (
                  <label key={column.id} className="admin-data-table__mobile-filter-field">
                    <span>{typeof column.columnDef.header === 'string' ? column.columnDef.header : column.id}</span>
                    <input
                      value={(column.getFilterValue() as string) || ''}
                      onChange={event => column.setFilterValue(event.target.value)}
                      placeholder="输入关键词"
                    />
                  </label>
                ))}
                <button type="button" className="admin-data-table__reset" onClick={() => { setColumnFilters([]); setShowMobileFilters(false) }}>清空并关闭</button>
              </div>
            )}
          </div>
          <div className="relative shrink-0">
            <button type="button" className="admin-data-table__tool" aria-expanded={showColumns} onClick={() => setShowColumns(value => !value)}>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16"/><path d="M8 4v4M15 10v4M10 16v4"/></svg>
              字段 {table.getVisibleLeafColumns().length}/{table.getAllLeafColumns().length}
            </button>
            {showColumns && (
              <div className="admin-data-table__column-menu" role="menu">
                <div className="admin-data-table__menu-title">显示字段</div>
                {table.getAllLeafColumns().map(column => (
                  <label key={column.id} className="admin-data-table__column-option">
                    <input
                      type="checkbox"
                      checked={column.getIsVisible()}
                      onChange={() => {
                        if (column.getIsVisible() && table.getVisibleLeafColumns().length <= 1) return
                        column.toggleVisibility()
                      }}
                    />
                    <span>{typeof column.columnDef.header === 'string' ? column.columnDef.header : column.id}</span>
                  </label>
                ))}
                <button type="button" className="admin-data-table__reset" onClick={applyComputedWidths}>按当前区域重算默认列宽</button>
              </div>
            )}
          </div>
        </div>
      )}
      <div ref={viewportRef} className="admin-data-table__viewport page-scroll">
        <table style={{ width: tableWidth }}>
          <colgroup>
            {table.getVisibleLeafColumns().map(column => <col key={column.id} style={{ width: column.getSize() }} />)}
          </colgroup>
          <thead>
            {table.getHeaderGroups().map(group => (
              <tr key={group.id}>
                {group.headers.map(header => {
                  const column = header.column
                  const canFilter = column.getCanFilter()
                  const hasFilter = column.getFilterValue() != null && column.getFilterValue() !== ''
                  return (
                    <th key={header.id} colSpan={header.colSpan} style={{ width: header.getSize() }}>
                      <div className="admin-data-table__header-cell">
                        <span className="min-w-0 truncate">{header.isPlaceholder ? null : flexRender(column.columnDef.header, header.getContext())}</span>
                        {canFilter && <button type="button" className={`admin-data-table__filter ${hasFilter ? 'is-active' : ''}`} aria-label={`筛选${String(column.columnDef.header ?? column.id)}`} onClick={() => setFilteringColumn(filteringColumn === column.id ? null : column.id)}><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" aria-hidden="true"><path d="M4 5h16l-6.2 7.2v5.4l-3.6 1.9v-7.3L4 5Z"/></svg></button>}
                        {filteringColumn === column.id && (
                          <div className="admin-data-table__filter-popover">
                            <strong>筛选：{String(column.columnDef.header ?? column.id)}</strong>
                            <input autoFocus value={(column.getFilterValue() as string) || ''} onChange={event => column.setFilterValue(event.target.value)} placeholder="输入关键词" onKeyDown={event => { if (event.key === 'Escape' || event.key === 'Enter') setFilteringColumn(null) }} />
                            <div><button type="button" onClick={() => { column.setFilterValue(undefined); setFilteringColumn(null) }}>清除</button><button type="button" className="is-primary" onClick={() => setFilteringColumn(null)}>完成</button></div>
                          </div>
                        )}
                        {header.column.getCanResize() && <button type="button" className="admin-data-table__resize" aria-label={`调整${String(column.columnDef.header ?? column.id)}列宽`} title="拖动调整列宽" onDoubleClick={() => { manuallyResized.current = false; table.resetColumnSizing(); applyComputedWidths() }} onMouseDown={header.getResizeHandler()} onTouchStart={header.getResizeHandler()} />}
                      </div>
                    </th>
                  )
                })}
              </tr>
            ))}
          </thead>
          <tbody>
            {loading ? <tr><td colSpan={table.getVisibleLeafColumns().length} className="admin-data-table__empty">加载中...</td></tr>
              : visibleRows.length === 0 ? <tr><td colSpan={table.getVisibleLeafColumns().length} className="admin-data-table__empty">{emptyMessage}</td></tr>
                : visibleRows.map(row => <tr key={row.id} onClick={() => onRowClick?.(row.original)} className={onRowClick ? 'is-clickable' : ''}>{row.getVisibleCells().map(cell => <td key={cell.id} data-label={typeof cell.column.columnDef.header === 'string' ? cell.column.columnDef.header : cell.column.id} style={{ width: cell.column.getSize() }}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</td>)}</tr>)}
          </tbody>
        </table>
      </div>
      {columnFilters.length > 0 && <div className="admin-data-table__filter-summary"><span>当前页筛选</span>{columnFilters.map(filter => <button type="button" key={filter.id} onClick={() => table.getColumn(filter.id)?.setFilterValue(undefined)}>{filter.id}：{String(filter.value)} <span aria-hidden="true">×</span></button>)}<button type="button" className="clear" onClick={() => setColumnFilters([])}>清空筛选</button></div>}
    </section>
  )
}
