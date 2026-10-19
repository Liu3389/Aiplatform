import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react'
import * as echarts from 'echarts'
import type { ECharts, EChartsOption } from 'echarts'
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import type { UsageAnalytics } from '@/services/api'

type UsageRange = 7 | 30 | 90 | 365
type UsageMetric = 'tokens' | 'calls' | 'points'

interface Props {
  data: UsageAnalytics | null
  loading: boolean
  isDark: boolean
  lang: string
  accountBalance: number
  pointsInfo: any
  consumptionSummary: { todayConsumed: number; monthConsumed: number; totalConsumed: number; remainingPoints: number } | null
  onRecharge: () => void
}

interface DayMetric {
  date: string
  calls: number
  inputTokens: number
  outputTokens: number
  points: number
}

interface ModelMetric {
  model: string
  calls: number
  inputTokens: number
  outputTokens: number
  points: number
}

const COLORS = {
  purple: '#8B5CF6',
  blue: '#3B82F6',
  cyan: '#06B6D4',
  amber: '#F59E0B',
  green: '#10B981',
  rose: '#F43F5E',
}

const rangeLabels: Record<UsageRange, { zh: string; en: string }> = {
  7: { zh: '近 7 天', en: '7 days' },
  30: { zh: '近 30 天', en: '30 days' },
  90: { zh: '近 90 天', en: '90 days' },
  365: { zh: '近 1 年', en: '1 year' },
}

function numberValue(value: unknown) {
  const result = Number(value)
  return Number.isFinite(result) ? result : 0
}

function parseDay(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(Date.UTC(year, (month || 1) - 1, day || 1))
}

function formatDay(value: Date) {
  return value.toISOString().slice(0, 10)
}

function shiftDay(value: string, offset: number) {
  const date = parseDay(value)
  date.setUTCDate(date.getUTCDate() + offset)
  return formatDay(date)
}

function getTodayInShanghai() {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date())
}

function formatCompact(value: number) {
  return new Intl.NumberFormat('zh-CN', { notation: 'compact', maximumFractionDigits: 1 }).format(value)
}

function HeatmapCell({
  day,
  selected,
  maxCalls,
  onSelect,
  isDark,
}: {
  day: DayMetric | null
  selected: boolean
  maxCalls: number
  onSelect: () => void
  isDark: boolean
}) {
  if (!day) return <span aria-hidden="true" className="usage-heatmap-cell block w-full rounded-[4px]" />
  const calls = day.calls
  const level = calls === 0 ? 0 : Math.min(4, Math.ceil((calls / Math.max(maxCalls, 1)) * 4))
  const colors = isDark
    ? ['rgba(148,163,184,0.10)', 'rgba(139,92,246,0.28)', 'rgba(139,92,246,0.48)', 'rgba(99,102,241,0.72)', '#8B5CF6']
    : ['rgba(100,116,139,0.10)', 'rgba(139,92,246,0.20)', 'rgba(139,92,246,0.38)', 'rgba(99,102,241,0.60)', '#7C3AED']
  const label = `${day.date}：${calls} 次调用，${formatCompact(day.inputTokens + day.outputTokens)} tokens，${day.points.toFixed(2)} 点`
  return (
    <button
      type="button"
      title={label}
      aria-label={label}
      onClick={onSelect}
      className="usage-heatmap-cell h-3.5 w-full rounded-[4px] transition-transform sm:hover:scale-110 focus:outline-none focus:ring-2 focus:ring-violet-400/70"
      style={{
        background: colors[level],
        boxShadow: selected ? `0 0 0 2px ${isDark ? '#0B1120' : '#FFFFFF'}, 0 0 0 4px ${COLORS.purple}` : undefined,
      }}
    />
  )
}

export default function UsageAnalyticsPanel({
  data,
  loading,
  isDark,
  lang,
  accountBalance,
  pointsInfo,
  consumptionSummary,
  onRecharge,
}: Props) {
  const reducedMotion = useReducedMotion()
  const [range, setRange] = useState<UsageRange>(30)
  const [metric, setMetric] = useState<UsageMetric>('tokens')
  const [selectedDate, setSelectedDate] = useState<string | null>(null)
  const [compactLayout, setCompactLayout] = useState(false)
  const chartRef = useRef<HTMLDivElement>(null)
  const chartInstance = useRef<ECharts | null>(null)

  useEffect(() => {
    const media = window.matchMedia('(max-width: 640px)')
    const update = () => setCompactLayout(media.matches)
    update()
    media.addEventListener?.('change', update)
    return () => media.removeEventListener?.('change', update)
  }, [])

  const endDate = data?.endDate || getTodayInShanghai()
  const rangeStart = shiftDay(endDate, -(range - 1))

  const { daily, models, totals } = useMemo(() => {
    const dayMap = new Map<string, DayMetric>()
    const modelMap = new Map<string, ModelMetric>()
    const getDay = (date: string) => {
      const existing = dayMap.get(date)
      if (existing) return existing
      const next: DayMetric = { date, calls: 0, inputTokens: 0, outputTokens: 0, points: 0 }
      dayMap.set(date, next)
      return next
    }
    const getModel = (model: string) => {
      const key = model || (lang === 'zh' ? '未命名模型' : 'Unnamed model')
      const existing = modelMap.get(key)
      if (existing) return existing
      const next: ModelMetric = { model: key, calls: 0, inputTokens: 0, outputTokens: 0, points: 0 }
      modelMap.set(key, next)
      return next
    }
    const inRange = (date: string) => date >= rangeStart && date <= endDate

    for (const row of data?.calls || []) {
      if (!inRange(row.date)) continue
      const day = getDay(row.date)
      const model = getModel(row.model)
      const calls = numberValue(row.calls)
      const inputTokens = numberValue(row.inputTokens)
      const outputTokens = numberValue(row.outputTokens)
      day.calls += calls
      day.inputTokens += inputTokens
      day.outputTokens += outputTokens
      model.calls += calls
      model.inputTokens += inputTokens
      model.outputTokens += outputTokens
    }
    for (const row of data?.points || []) {
      if (!inRange(row.date)) continue
      const points = numberValue(row.points)
      getDay(row.date).points += points
      getModel(row.model).points += points
    }

    const days: DayMetric[] = []
    for (let i = 0; i < range; i += 1) {
      const date = shiftDay(rangeStart, i)
      days.push(dayMap.get(date) || { date, calls: 0, inputTokens: 0, outputTokens: 0, points: 0 })
    }
    const modelRows = [...modelMap.values()].sort((a, b) => {
      const aValue = metric === 'tokens' ? a.inputTokens + a.outputTokens : metric === 'calls' ? a.calls : a.points
      const bValue = metric === 'tokens' ? b.inputTokens + b.outputTokens : metric === 'calls' ? b.calls : b.points
      return bValue - aValue || b.calls - a.calls
    })
    return {
      daily: days,
      models: modelRows,
      totals: days.reduce((sum, day) => ({
        calls: sum.calls + day.calls,
        tokens: sum.tokens + day.inputTokens + day.outputTokens,
        points: sum.points + day.points,
      }), { calls: 0, tokens: 0, points: 0 }),
    }
  }, [data, endDate, lang, metric, range, rangeStart])

  const heatmap = useMemo(() => {
    const heatmapDays = compactLayout ? 90 : 365
    const heatmapStart = compactLayout ? shiftDay(endDate, -(heatmapDays - 1)) : data?.startDate || shiftDay(endDate, -364)
    const callMap = new Map<string, DayMetric>()
    const pointMap = new Map<string, number>()
    for (const row of data?.calls || []) {
      const current = callMap.get(row.date) || { date: row.date, calls: 0, inputTokens: 0, outputTokens: 0, points: 0 }
      current.calls += numberValue(row.calls)
      current.inputTokens += numberValue(row.inputTokens)
      current.outputTokens += numberValue(row.outputTokens)
      callMap.set(row.date, current)
    }
    for (const row of data?.points || []) {
      pointMap.set(row.date, (pointMap.get(row.date) || 0) + numberValue(row.points))
    }
    const days: DayMetric[] = []
    for (let i = 0; i < heatmapDays; i += 1) {
      const date = shiftDay(heatmapStart, i)
      const call = callMap.get(date)
      days.push({
        date,
        calls: call?.calls || 0,
        inputTokens: call?.inputTokens || 0,
        outputTokens: call?.outputTokens || 0,
        points: pointMap.get(date) || 0,
      })
    }
    const startPadding = (parseDay(heatmapStart).getUTCDay() + 6) % 7
    const cells: Array<DayMetric | null> = [...Array(startPadding).fill(null), ...days]
    const columns = Math.ceil(cells.length / 7)
    const maxCalls = Math.max(...days.map(day => day.calls), 1)
    const monthLabels = days.reduce<Array<{ label: string; column: number }>>((labels, day, index) => {
      if (day.date.slice(8) === '01' || index === 0) {
        labels.push({ label: day.date.slice(0, 7), column: Math.floor((index + startPadding) / 7) })
      }
      return labels
    }, [])
    return {
      cells,
      days,
      columns,
      maxCalls,
      monthLabels,
      startDate: heatmapStart,
      endDate: shiftDay(heatmapStart, heatmapDays - 1),
    }
  }, [compactLayout, data, endDate])

  useEffect(() => {
    if (selectedDate && (selectedDate < heatmap.startDate || selectedDate > heatmap.endDate)) setSelectedDate(null)
  }, [heatmap.endDate, heatmap.startDate, selectedDate])

  useEffect(() => {
    if (!chartRef.current) return
    chartInstance.current?.dispose()
    const chart = echarts.init(chartRef.current)
    chartInstance.current = chart
    const textColor = isDark ? '#CBD5E1' : '#475569'
    const splitColor = isDark ? 'rgba(148,163,184,0.12)' : 'rgba(100,116,139,0.12)'
    const labels = daily.map(day => day.date.slice(5))
    const series = metric === 'tokens'
      ? [
        { name: lang === 'zh' ? '输入 Token' : 'Input tokens', type: 'line', smooth: true, symbol: 'none', data: daily.map(day => day.inputTokens), lineStyle: { color: COLORS.blue, width: 2 }, areaStyle: { color: 'rgba(59,130,246,0.10)' } },
        { name: lang === 'zh' ? '输出 Token' : 'Output tokens', type: 'line', smooth: true, symbol: 'none', data: daily.map(day => day.outputTokens), lineStyle: { color: COLORS.purple, width: 2 }, areaStyle: { color: 'rgba(139,92,246,0.12)' } },
      ]
      : [{
        name: metric === 'calls' ? (lang === 'zh' ? '调用次数' : 'Calls') : (lang === 'zh' ? '点数消耗' : 'Points used'),
        type: 'line',
        smooth: true,
        symbol: 'none',
        data: daily.map(day => metric === 'calls' ? day.calls : day.points),
        lineStyle: { color: metric === 'calls' ? COLORS.cyan : COLORS.amber, width: 2 },
        areaStyle: { color: metric === 'calls' ? 'rgba(6,182,212,0.12)' : 'rgba(245,158,11,0.12)' },
      }]
    const option: EChartsOption = {
      animation: !reducedMotion,
      grid: { top: 18, right: 18, bottom: 42, left: 52 },
      tooltip: {
        trigger: 'axis',
        backgroundColor: isDark ? 'rgba(15,23,42,0.96)' : 'rgba(255,255,255,0.96)',
        borderColor: splitColor,
        textStyle: { color: isDark ? '#F8FAFC' : '#0F172A', fontSize: 11 },
      },
      legend: { show: metric === 'tokens', top: 0, right: 0, textStyle: { color: textColor, fontSize: 10 }, itemWidth: 12, itemHeight: 6 },
      xAxis: { type: 'category', boundaryGap: false, data: labels, axisLine: { lineStyle: { color: splitColor } }, axisTick: { show: false }, axisLabel: { color: textColor, fontSize: 9, interval: Math.max(0, Math.floor(labels.length / 8) - 1) } },
      yAxis: { type: 'value', min: 0, splitLine: { lineStyle: { color: splitColor, type: 'dashed' } }, axisLabel: { color: textColor, fontSize: 9, formatter: (value: number) => formatCompact(value) } },
      dataZoom: [{ type: 'inside', xAxisIndex: 0, filterMode: 'none' }],
      series: series as EChartsOption['series'],
    }
    chart.setOption(option)
    const resize = () => chart.resize()
    window.addEventListener('resize', resize)
    return () => {
      window.removeEventListener('resize', resize)
      chart.dispose()
      if (chartInstance.current === chart) chartInstance.current = null
    }
  }, [daily, isDark, lang, metric, reducedMotion])

  const selectedDay = selectedDate ? heatmap.days.find(day => day.date === selectedDate) : null
  const hasHeatmapUsage = heatmap.days.some(day => day.calls > 0 || day.inputTokens > 0 || day.outputTokens > 0 || day.points > 0)
  const maxModelValue = Math.max(...models.map(model => metric === 'tokens' ? model.inputTokens + model.outputTokens : metric === 'calls' ? model.calls : model.points), 1)
  const stats = [
    { label: lang === 'zh' ? '账户余额' : 'Account balance', value: `¥${accountBalance.toFixed(2)}`, detail: lang === 'zh' ? '仅用于购买会员' : 'Membership only', color: COLORS.blue, action: true },
    { label: lang === 'zh' ? '点数余额' : 'Points balance', value: `${numberValue(pointsInfo?.totalPoints).toFixed(2)} 点`, detail: lang === 'zh' ? '用于 AI 对话消耗' : 'Used for AI chat', color: COLORS.amber },
    { label: lang === 'zh' ? `${range} 日调用` : `${range}d calls`, value: `${formatCompact(totals.calls)} 次`, detail: lang === 'zh' ? '成功调用次数' : 'Successful calls', color: COLORS.cyan },
    { label: lang === 'zh' ? `${range} 日 Token` : `${range}d tokens`, value: formatCompact(totals.tokens), detail: lang === 'zh' ? '输入 + 输出' : 'Input + output', color: COLORS.purple },
  ]

  const motionProps = reducedMotion ? {} : { initial: { opacity: 0, y: 10 }, animate: { opacity: 1, y: 0 } }
  const panelStyle = { background: 'var(--bg-card)', border: '1px solid var(--border-color)' }
  const title = (zh: string, en: string) => lang === 'zh' ? zh : en

  return (
    <div className="space-y-4">
      <motion.div {...motionProps} className="grid grid-cols-2 xl:grid-cols-4 gap-3">
        {stats.map(item => (
          <div key={item.label} className="relative overflow-hidden rounded-2xl p-4" style={panelStyle}>
            <div className="absolute -right-8 -top-8 h-24 w-24 rounded-full opacity-10" style={{ background: item.color }} />
            <div className="relative flex items-start justify-between gap-2">
              <div>
                <div className="text-[11px]" style={{ color: 'var(--text-dim)' }}>{item.label}</div>
                <div className="mt-2 text-xl font-semibold tracking-tight tabular-nums" style={{ color: 'var(--text-primary)' }}>{item.value}</div>
                <div className="mt-1 text-[10px]" style={{ color: item.color }}>{item.detail}</div>
              </div>
              {item.action && <button type="button" onClick={onRecharge} className="rounded-lg px-2 py-1 text-[10px]" style={{ color: item.color, background: `${item.color}14` }}>{title('充值', 'Add')}</button>}
            </div>
          </div>
        ))}
      </motion.div>

      <motion.section {...motionProps} transition={{ delay: reducedMotion ? 0 : 0.05 }} className="rounded-2xl p-4 sm:p-5" style={panelStyle}>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <div className="flex items-center gap-2">
              <span className="h-2 w-2 rounded-full" style={{ background: COLORS.green, boxShadow: `0 0 0 4px ${COLORS.green}18` }} />
              <h2 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>{title('使用活动', 'Usage activity')}</h2>
            </div>
            <p className="mt-1 text-[11px]" style={{ color: 'var(--text-dim)' }}>{title(compactLayout ? '近 90 天活动日历，点击某一天查看明细' : '近一年活动日历，点击某一天查看调用、Token 和点数明细', compactLayout ? 'Last 90 days; select a day for details' : 'One-year activity calendar; select a day for calls, tokens, and points')}</p>
          </div>
        </div>

        <div className="mt-5 overflow-hidden pb-1">
          <div className="w-full min-w-0">
            <div className="mb-2 hidden gap-2 sm:flex">
              <span className="w-7 shrink-0" aria-hidden="true" />
              <div className="usage-heatmap-grid grid min-w-0 flex-1 text-[9px]" style={{ '--usage-heatmap-columns': heatmap.columns } as CSSProperties}>
              {heatmap.monthLabels.map(month => <span key={`${month.label}-${month.column}`} style={{ gridColumnStart: month.column + 1, color: 'var(--text-dim)' }}>{month.label.slice(5)}月</span>)}
              </div>
            </div>
            <div className="flex gap-1.5 sm:gap-2">
              <div className="grid w-5 shrink-0 grid-rows-7 gap-1 pt-0.5 text-[9px] sm:w-7" style={{ color: 'var(--text-dim)' }}>
                <span>一</span><span></span><span>三</span><span></span><span>五</span><span></span><span>日</span>
              </div>
              <div className="usage-heatmap-grid grid min-w-0 flex-1 grid-rows-7 grid-flow-col gap-1" style={{ '--usage-heatmap-columns': heatmap.columns } as CSSProperties}>
                {heatmap.cells.map((day, index) => <HeatmapCell key={day ? day.date : `padding-${index}`} day={day} selected={day?.date === selectedDate} maxCalls={heatmap.maxCalls} onSelect={() => day && setSelectedDate(day.date)} isDark={isDark} />)}
              </div>
            </div>
            <div className="mt-2 flex items-center justify-end gap-1.5 text-[9px]" style={{ color: 'var(--text-dim)' }}>
              <span>{title('少', 'Less')}</span>
              {[0, 1, 2, 3, 4].map(level => <span key={level} className="h-3.5 w-3.5 rounded-[4px]" style={{ background: isDark ? ['rgba(148,163,184,0.10)', 'rgba(139,92,246,0.28)', 'rgba(139,92,246,0.48)', 'rgba(99,102,241,0.72)', '#8B5CF6'][level] : ['rgba(100,116,139,0.10)', 'rgba(139,92,246,0.20)', 'rgba(139,92,246,0.38)', 'rgba(99,102,241,0.60)', '#7C3AED'][level] }} />)}
              <span>{title('多', 'More')}</span>
            </div>
          </div>
        </div>

        <div className="relative mt-4 h-24 sm:h-[72px]">
          <AnimatePresence initial={false} mode="wait">
            {selectedDay ? (
              <motion.div
                key={selectedDay.date}
                initial={reducedMotion ? false : { opacity: 0, y: 5 }}
                animate={{ opacity: 1, y: 0 }}
                exit={reducedMotion ? undefined : { opacity: 0, y: -5 }}
                transition={{ duration: reducedMotion ? 0 : 0.18 }}
                className="absolute inset-0 grid grid-cols-2 gap-2 rounded-xl p-3 sm:grid-cols-4"
                style={{ background: 'var(--bg-surface)' }}
              >
                <div><div className="text-[10px]" style={{ color: 'var(--text-dim)' }}>{selectedDay.date}</div><div className="mt-1 text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>{selectedDay.calls} 次</div></div>
                <div><div className="text-[10px]" style={{ color: 'var(--text-dim)' }}>Input Token</div><div className="mt-1 text-sm font-semibold" style={{ color: COLORS.blue }}>{formatCompact(selectedDay.inputTokens)}</div></div>
                <div><div className="text-[10px]" style={{ color: 'var(--text-dim)' }}>Output Token</div><div className="mt-1 text-sm font-semibold" style={{ color: COLORS.purple }}>{formatCompact(selectedDay.outputTokens)}</div></div>
                <div><div className="text-[10px]" style={{ color: 'var(--text-dim)' }}>{title('点数消耗', 'Points used')}</div><div className="mt-1 text-sm font-semibold" style={{ color: COLORS.amber }}>{selectedDay.points.toFixed(2)} 点</div></div>
              </motion.div>
            ) : (
              <motion.div
                key={hasHeatmapUsage ? 'select-a-day' : 'no-usage'}
                initial={reducedMotion ? false : { opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={reducedMotion ? undefined : { opacity: 0 }}
                className="absolute inset-0 flex items-center justify-center rounded-xl px-3 text-center text-[11px]"
                style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)' }}
              >
                {hasHeatmapUsage ? title('点击活动方块查看当天明细', 'Select an active day to view details') : title('暂无调用记录', 'No usage recorded yet')}
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </motion.section>

      <div className="grid gap-4 lg:grid-cols-[1.5fr_1fr]">
        <motion.section {...motionProps} transition={{ delay: reducedMotion ? 0 : 0.1 }} className="rounded-2xl p-4 sm:p-5" style={panelStyle}>
          <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <h2 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>{title('趋势分析', 'Trend analysis')}</h2>
              <p className="mt-1 text-[11px]" style={{ color: 'var(--text-dim)' }}>{title('按时间范围切换查看 Token、调用或点数', 'Switch between tokens, calls, and points')}</p>
            </div>
            <div className="flex flex-wrap items-center justify-start gap-1.5 sm:justify-end">
              {(Object.keys(rangeLabels).map(Number) as UsageRange[]).map(value => (
                <button key={value} type="button" onClick={() => setRange(value)} className="rounded-lg px-2.5 py-1.5 text-[10px] font-medium transition-colors" style={{ background: range === value ? `${COLORS.purple}18` : 'var(--bg-surface)', color: range === value ? COLORS.purple : 'var(--text-dim)', border: `1px solid ${range === value ? `${COLORS.purple}38` : 'var(--border-color)'}` }}>
                  {title(rangeLabels[value].zh, rangeLabels[value].en)}
                </button>
              ))}
              <select value={metric} onChange={event => setMetric(event.target.value as UsageMetric)} className="rounded-lg px-2.5 py-1.5 text-[10px] outline-none" style={{ background: 'var(--bg-surface)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }} aria-label={title('趋势指标', 'Trend metric')}>
                <option value="tokens">{title('Token 使用量', 'Token usage')}</option>
                <option value="calls">{title('调用次数', 'Call count')}</option>
                <option value="points">{title('点数消耗', 'Points used')}</option>
              </select>
            </div>
          </div>
          <div ref={chartRef} className="mt-3 h-[245px] w-full" aria-label={title('用量趋势图', 'Usage trend chart')} />
          {loading && <div className="text-center text-[11px]" style={{ color: 'var(--text-dim)' }}>{title('正在同步用量数据…', 'Syncing usage data…')}</div>}
        </motion.section>

        <motion.section {...motionProps} transition={{ delay: reducedMotion ? 0 : 0.15 }} className="rounded-2xl p-4 sm:p-5" style={panelStyle}>
          <div className="flex items-start justify-between gap-3">
            <div>
              <h2 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>{title('按模型分类', 'By model')}</h2>
              <p className="mt-1 text-[11px]" style={{ color: 'var(--text-dim)' }}>{title('当前时间范围内的成功调用', 'Successful calls in this range')}</p>
            </div>
            <span className="rounded-md px-2 py-1 text-[9px]" style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)' }}>{models.length} {title('个模型', 'models')}</span>
          </div>
          <div className="mt-4 max-h-[245px] space-y-3 overflow-y-auto pr-1">
            {models.length === 0 ? <div className="py-12 text-center text-[11px]" style={{ color: 'var(--text-dim)' }}>{title('暂无模型调用记录', 'No model usage yet')}</div> : models.map(model => {
              const value = metric === 'tokens' ? model.inputTokens + model.outputTokens : metric === 'calls' ? model.calls : model.points
              return (
                <div key={model.model}>
                  <div className="mb-1 flex items-center justify-between gap-3 text-[10px]">
                    <span className="min-w-0 truncate font-medium" style={{ color: 'var(--text-primary)' }} title={model.model}>{model.model}</span>
                    <span className="shrink-0 tabular-nums" style={{ color: 'var(--text-dim)' }}>{metric === 'points' ? `${value.toFixed(2)} 点` : `${formatCompact(value)} ${metric === 'calls' ? '次' : 'tokens'}`}</span>
                  </div>
                  <div className="h-1.5 overflow-hidden rounded-full" style={{ background: 'var(--bg-surface)' }}><div className="h-full rounded-full transition-all" style={{ width: `${Math.max(value > 0 ? 3 : 0, (value / maxModelValue) * 100)}%`, background: `linear-gradient(90deg, ${COLORS.purple}, ${COLORS.blue})` }} /></div>
                </div>
              )
            })}
          </div>
        </motion.section>
      </div>

      <motion.section {...motionProps} transition={{ delay: reducedMotion ? 0 : 0.2 }} className="rounded-2xl p-4 sm:p-5" style={panelStyle}>
        <div className="flex items-center justify-between gap-3">
          <div>
            <h2 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>{title('点数结构', 'Points balance')}</h2>
            <p className="mt-1 text-[11px]" style={{ color: 'var(--text-dim)' }}>{title('金额与点数严格分离，模型调用只扣点数', 'Money and points are separate; model calls use points only')}</p>
          </div>
          <button type="button" onClick={onRecharge} className="rounded-lg px-3 py-1.5 text-[10px] font-medium" style={{ background: `${COLORS.purple}16`, color: COLORS.purple }}>{title('管理会员', 'Manage membership')}</button>
        </div>
        <div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-4">
          {[
            { label: title('永久点数', 'Permanent'), value: `${numberValue(pointsInfo?.permanentPoints).toFixed(2)} 点`, color: COLORS.green },
            { label: title('限时点数', 'Temporary'), value: `${numberValue(pointsInfo?.temporaryPoints).toFixed(2)} 点`, color: COLORS.blue },
            { label: title('今日已用', 'Used today'), value: `${numberValue(consumptionSummary?.todayConsumed).toFixed(2)} 点`, color: COLORS.rose },
            { label: title('本月已用', 'Used this month'), value: `${numberValue(consumptionSummary?.monthConsumed).toFixed(2)} 点`, color: COLORS.amber },
          ].map(item => <div key={item.label} className="rounded-xl p-3" style={{ background: 'var(--bg-surface)' }}><div className="text-[10px]" style={{ color: 'var(--text-dim)' }}>{item.label}</div><div className="mt-1 text-sm font-semibold tabular-nums" style={{ color: item.color }}>{item.value}</div></div>)}
        </div>
      </motion.section>
    </div>
  )
}
