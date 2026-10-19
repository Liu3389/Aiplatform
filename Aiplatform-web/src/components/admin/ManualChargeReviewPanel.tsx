import { useCallback, useEffect, useState } from 'react'
import {
  adminAiChargeReviewApi,
  type AiChargeReviewAudit,
  type AiChargeReviewQueueItem,
} from '@/services/api'

type Props = { canResolve: boolean; adminId?: number }
type ResolutionAction = 'REFUND' | 'SETTLE'
type Confirmation = { item: AiChargeReviewQueueItem; action: ResolutionAction } | null

const inputStyle = {
  background: 'var(--bg-input)',
  border: '1px solid var(--border-color)',
  color: 'var(--text-primary)',
}

function formatDate(value?: string | null) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN')
}

function statusLabel(status: AiChargeReviewAudit['status']) {
  if (status === 'RESOLVED') return { label: '已完成', color: '#10b981', bg: 'rgba(16,185,129,.12)' }
  if (status === 'FAILED') return { label: '需检查', color: '#f43f5e', bg: 'rgba(244,63,94,.12)' }
  return { label: '处理中', color: '#f59e0b', bg: 'rgba(245,158,11,.13)' }
}

export default function ManualChargeReviewPanel({ canResolve, adminId }: Props) {
  const [queue, setQueue] = useState<AiChargeReviewQueueItem[]>([])
  const [total, setTotal] = useState(0)
  const [queuePage, setQueuePage] = useState(1)
  const [history, setHistory] = useState<AiChargeReviewAudit[]>([])
  const [evidence, setEvidence] = useState<Record<string, string>>({})
  const [actualPoints, setActualPoints] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(false)
  const [busyId, setBusyId] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [confirmation, setConfirmation] = useState<Confirmation>(null)

  const refresh = useCallback(async (showSpinner = true) => {
    if (showSpinner) setLoading(true)
    setError('')
    const [queueResult, historyResult] = await Promise.allSettled([
      adminAiChargeReviewApi.queue({ page: queuePage, size: 20 }),
      adminAiChargeReviewApi.history({ page: 1, size: 10 }),
    ])
    if (queueResult.status === 'fulfilled') {
      setQueue(queueResult.value.records || [])
      setTotal(Number(queueResult.value.total || 0))
    } else {
      setError((queueResult.reason as Error)?.message || '人工对账队列加载失败')
    }
    if (historyResult.status === 'fulfilled') setHistory(historyResult.value.records || [])
    if (showSpinner) setLoading(false)
  }, [queuePage])

  useEffect(() => { void refresh() }, [refresh])

  const submit = async () => {
    if (!confirmation) return
    const { item, action } = confirmation
    const note = (evidence[item.requestId] || '').trim()
    if (note.length < 8 || note.length > 1000) {
      setError('请填写 8 到 1000 个字符的供应商日志引用和核验结论。不要粘贴密钥或完整敏感日志。')
      setConfirmation(null)
      return
    }
    let points: number | undefined
    if (action === 'SETTLE') {
      points = Number(actualPoints[item.requestId])
      if (!Number.isFinite(points) || points <= 0 || points > Number(item.reservedPoints)
          || !/^\d+(\.\d{1,3})?$/.test(actualPoints[item.requestId] || '')) {
        setError(`请输入大于 0 且不超过预扣 ${Number(item.reservedPoints).toFixed(3)} 点的实际点数（最多 3 位小数）。`)
        setConfirmation(null)
        return
      }
    }
    setBusyId(item.requestId)
    setError('')
    setNotice('')
    try {
      const result = await adminAiChargeReviewApi.resolve(item.requestId, {
        action,
        ...(points === undefined ? {} : { actualPoints: points }),
        evidence: note,
      })
      setNotice(result.status === 'RESOLVED'
        ? `${item.requestId} 已${action === 'REFUND' ? '退款' : '结算'}完成。`
        : `${item.requestId} 已登记；恢复扫描器会继续处理，请稍后刷新状态。`)
      setEvidence(values => ({ ...values, [item.requestId]: '' }))
      setActualPoints(values => ({ ...values, [item.requestId]: '' }))
      await refresh(false)
    } catch (cause) {
      setError((cause as Error)?.message || '处理失败，请刷新后核对对账记录。')
    } finally {
      setBusyId('')
      setConfirmation(null)
    }
  }

  return (
    <div className="space-y-4">
      <section className="rounded-2xl border p-4 sm:p-5" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h3 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>AI 点数人工对账</h3>
            <p className="mt-1 max-w-3xl text-xs leading-5" style={{ color: 'var(--text-secondary)' }}>
              上游结果不明确时，系统保留预扣并暂停自动退款。先核对供应商调用/账单记录，再选择退款或按实际点数结算；每次决定都会保存证据和操作人。
            </p>
          </div>
          <button onClick={() => void refresh()} disabled={loading}
            className="shrink-0 rounded-xl px-3 py-2 text-xs font-medium disabled:opacity-50"
            style={{ background: 'var(--bg-surface)', border: '1px solid var(--border-color)', color: 'var(--text-primary)' }}>
            {loading ? '刷新中…' : '刷新队列'}
          </button>
        </div>
        {!canResolve && <div className="mt-3 rounded-xl px-3 py-2 text-xs" style={{ color: '#b45309', background: 'rgba(245,158,11,.10)' }}>当前账号可查看队列；只有超级管理员能提交退款或结算决定。</div>}
        {error && <div role="alert" className="mt-3 rounded-xl px-3 py-2 text-xs" style={{ color: '#e11d48', background: 'rgba(244,63,94,.10)' }}>{error}</div>}
        {notice && <div role="status" className="mt-3 rounded-xl px-3 py-2 text-xs" style={{ color: '#059669', background: 'rgba(16,185,129,.10)' }}>{notice}</div>}
      </section>

      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h4 className="text-xs font-semibold" style={{ color: 'var(--text-primary)' }}>待核验请求 <span style={{ color: 'var(--text-muted)' }}>· {total}</span></h4>
          {total > 20 && <div className="flex items-center gap-2 text-[11px]" style={{ color: 'var(--text-secondary)' }}>
            <button disabled={queuePage <= 1 || loading} onClick={() => setQueuePage(p => p - 1)} className="rounded-lg border px-2 py-1 disabled:opacity-40" style={{ borderColor: 'var(--border-color)' }}>上一页</button>
            <span>{queuePage} / {Math.ceil(total / 20)}</span>
            <button disabled={queuePage >= Math.ceil(total / 20) || loading} onClick={() => setQueuePage(p => p + 1)} className="rounded-lg border px-2 py-1 disabled:opacity-40" style={{ borderColor: 'var(--border-color)' }}>下一页</button>
          </div>}
        </div>
        {loading && queue.length === 0 ? (
          <div className="rounded-2xl border px-4 py-10 text-center text-xs" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)', color: 'var(--text-muted)' }}>正在加载对账请求…</div>
        ) : queue.length === 0 ? (
          <div className="rounded-2xl border px-4 py-10 text-center" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
            <div className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>当前没有待核验的预扣</div>
            <p className="mt-1 text-xs" style={{ color: 'var(--text-muted)' }}>新出现的上游歧义请求会在这里保留，直到管理员依据供应商记录处理。</p>
          </div>
        ) : queue.map(item => (
          <article key={item.requestId} className="overflow-hidden rounded-2xl border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
            <div className="flex flex-col gap-3 p-4 sm:p-5">
              <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="rounded-md px-2 py-1 text-[10px] font-semibold" style={{ background: 'rgba(245,158,11,.13)', color: '#b45309' }}>需人工核验</span>
                    <span className="text-xs font-medium" style={{ color: 'var(--text-primary)' }}>{item.modelName}</span>
                    <span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>用户 #{item.userId}</span>
                  </div>
                  <code className="mt-2 block break-all text-[10px]" style={{ color: 'var(--text-secondary)' }}>{item.requestId}</code>
                </div>
                <div className="shrink-0 rounded-xl px-3 py-2 text-right" style={{ background: 'var(--bg-surface)' }}>
                  <div className="text-[10px]" style={{ color: 'var(--text-muted)' }}>保留预扣</div>
                  <div className="text-sm font-semibold tabular-nums" style={{ color: 'var(--text-primary)' }}>{Number(item.reservedPoints).toFixed(3)} 点</div>
                </div>
              </div>
              <div className="grid gap-2 text-[11px] sm:grid-cols-2" style={{ color: 'var(--text-secondary)' }}>
                <div>上游开始：{formatDate(item.upstreamStartedAt)}</div>
                <div>进入待核验：{formatDate(item.updateTime)}</div>
                <div className="sm:col-span-2">系统记录：{item.reason || '上游结果未知，需核对供应商账单'}</div>
              </div>
              {canResolve && <div className="grid gap-3 border-t pt-3" style={{ borderColor: 'var(--border-color)' }}>
                <label className="grid gap-1 text-[11px] font-medium" style={{ color: 'var(--text-secondary)' }}>
                  供应商日志引用与结论
                  <textarea value={evidence[item.requestId] || ''}
                    onChange={event => setEvidence(values => ({ ...values, [item.requestId]: event.target.value }))}
                    placeholder="例如：DeepSeek 控制台请求 ID 123…，核对账单后确认未产生费用。请勿粘贴 API Key 或完整敏感日志。"
                    rows={2} maxLength={1000} className="w-full resize-y rounded-xl px-3 py-2 text-xs outline-none" style={inputStyle} />
                  <span className="text-right text-[10px] font-normal" style={{ color: 'var(--text-muted)' }}>{(evidence[item.requestId] || '').length}/1000</span>
                </label>
                <div className="grid gap-2 sm:grid-cols-[minmax(180px,1fr)_auto_auto] sm:items-end">
                  <label className="grid gap-1 text-[11px] font-medium" style={{ color: 'var(--text-secondary)' }}>
                    已确认上游计费时的实际点数
                    <input type="number" min="0.001" max={item.reservedPoints} step="0.001"
                      value={actualPoints[item.requestId] || ''}
                      onChange={event => setActualPoints(values => ({ ...values, [item.requestId]: event.target.value }))}
                      placeholder={`大于 0，最多 ${Number(item.reservedPoints).toFixed(3)}`}
                      className="w-full rounded-xl px-3 py-2 text-xs outline-none" style={inputStyle} />
                  </label>
                  <button disabled={busyId === item.requestId} onClick={() => setConfirmation({ item, action: 'REFUND' })}
                    className="rounded-xl px-3 py-2 text-xs font-semibold disabled:opacity-50"
                    style={{ background: 'rgba(16,185,129,.12)', color: '#059669', border: '1px solid rgba(16,185,129,.25)' }}>
                    确认未计费并退款
                  </button>
                  <button disabled={busyId === item.requestId} onClick={() => setConfirmation({ item, action: 'SETTLE' })}
                    className="rounded-xl px-3 py-2 text-xs font-semibold text-white disabled:opacity-50"
                    style={{ background: 'var(--accent-purple)' }}>
                    确认计费并结算
                  </button>
                </div>
              </div>}
            </div>
          </article>
        ))}
      </section>

      <section className="overflow-hidden rounded-2xl border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
        <div className="border-b px-4 py-3" style={{ borderColor: 'var(--border-color)' }}>
          <h4 className="text-xs font-semibold" style={{ color: 'var(--text-primary)' }}>最近处理记录</h4>
        </div>
        {history.length === 0 ? <div className="px-4 py-6 text-center text-xs" style={{ color: 'var(--text-muted)' }}>暂无人工对账记录</div> : (
          <div className="divide-y" style={{ borderColor: 'var(--border-color)' }}>
            {history.map(item => {
              const badge = statusLabel(item.status)
              const canRetry = canResolve && item.adminId === adminId && item.status === 'PENDING'
              return <div key={item.requestId} className="grid gap-2 px-4 py-3 text-[11px] sm:grid-cols-[minmax(180px,1.3fr)_auto_auto_minmax(180px,2fr)_auto] sm:items-center">
                <div className="min-w-0"><code className="block break-all" style={{ color: 'var(--text-secondary)' }}>{item.requestId}</code><span style={{ color: 'var(--text-muted)' }}>用户 #{item.userId} · 管理员 #{item.adminId}</span></div>
                <span className="font-medium" style={{ color: 'var(--text-primary)' }}>{item.action === 'REFUND' ? '退款' : `结算 ${Number(item.actualPoints || 0).toFixed(3)} 点`}</span>
                <span className="w-fit rounded-full px-2 py-1 text-[10px] font-medium" style={{ color: badge.color, background: badge.bg }}>{badge.label}</span>
                <div className="min-w-0" title={item.evidence}><span className="block truncate" style={{ color: 'var(--text-secondary)' }}>{item.evidence}</span>{item.lastError && <span className="block text-[10px]" style={{ color: '#b45309' }}>{item.lastError}</span>}</div>
                {canRetry ? <button disabled={busyId === item.requestId} onClick={async () => {
                  setBusyId(item.requestId); setError(''); setNotice('')
                  try {
                    const result = await adminAiChargeReviewApi.resolve(item.requestId, {
                      action: item.action,
                      ...(item.actualPoints == null ? {} : { actualPoints: Number(item.actualPoints) }),
                      evidence: item.evidence,
                    })
                    setNotice(result.status === 'RESOLVED' ? `${item.requestId} 已处理完成。` : `${item.requestId} 仍在恢复队列中。`)
                    await refresh(false)
                  } catch (cause) { setError((cause as Error)?.message || '重试失败') }
                  finally { setBusyId('') }
                }} className="rounded-lg border px-2 py-1 font-medium disabled:opacity-50" style={{ color: 'var(--text-primary)', borderColor: 'var(--border-color)' }}>重试</button> : <span className="text-[10px]" style={{ color: 'var(--text-muted)' }}>{formatDate(item.createTime)}</span>}
              </div>
            })}
          </div>
        )}
      </section>

      {confirmation && <div role="dialog" aria-modal="true" aria-labelledby="charge-review-confirm-title" className="fixed inset-0 z-[1200] flex items-center justify-center bg-black/50 p-4">
        <div className="w-full max-w-md rounded-2xl border p-5 shadow-2xl" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
          <h3 id="charge-review-confirm-title" className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>确认人工对账决定</h3>
          <p className="mt-2 text-xs leading-5" style={{ color: 'var(--text-secondary)' }}>
            {confirmation.action === 'REFUND'
              ? `你将确认供应商没有计费，并向用户 #${confirmation.item.userId} 退还 ${Number(confirmation.item.reservedPoints).toFixed(3)} 点预扣。`
              : `你将确认供应商已计费，并向用户 #${confirmation.item.userId} 结算 ${actualPoints[confirmation.item.requestId] || '—'} 点。`}
            {' '}此决定会写入不可覆盖的对账审计记录。
          </p>
          <div className="mt-4 flex justify-end gap-2">
            <button onClick={() => setConfirmation(null)} className="rounded-xl border px-3 py-2 text-xs" style={{ color: 'var(--text-secondary)', borderColor: 'var(--border-color)' }}>返回核对</button>
            <button onClick={() => void submit()} disabled={busyId === confirmation.item.requestId} className="rounded-xl px-3 py-2 text-xs font-semibold text-white disabled:opacity-50" style={{ background: confirmation.action === 'REFUND' ? '#059669' : 'var(--accent-purple)' }}>
              {busyId === confirmation.item.requestId ? '提交中…' : '确认并执行'}
            </button>
          </div>
        </div>
      </div>}
    </div>
  )
}
