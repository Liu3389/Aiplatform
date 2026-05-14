import { useI18nStore } from '@/stores/i18nStore'
import { motion } from 'framer-motion'

interface ConfirmDialogProps {
  open: boolean
  title: string
  message: string
  confirmLabel?: string
  cancelLabel?: string
  danger?: boolean
  onConfirm: () => void
  onCancel: () => void
}

export default function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel,
  cancelLabel,
  danger = true,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const { lang } = useI18nStore()

  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" onClick={onCancel}>
      <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="absolute inset-0 bg-black/60 backdrop-blur-sm" />
      <motion.div
        initial={{ opacity: 0, scale: 0.92, y: 10 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        transition={{ type: 'spring', damping: 25, stiffness: 350 }}
        className="relative z-10 w-full max-w-sm rounded-2xl p-6 border"
        style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-start gap-3 mb-3">
          <div className="w-9 h-9 rounded-full flex items-center justify-center flex-shrink-0" style={{ background: danger ? 'rgba(244,63,94,0.12)' : 'rgba(139,92,246,0.12)' }}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={danger ? '#F43F5E' : '#8B5CF6'} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              {danger ? (
                <><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></>
              ) : (
                <><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></>
              )}
            </svg>
          </div>
          <div className="flex-1 min-w-0">
            <h3 className="text-sm font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>{title}</h3>
            <p className="text-xs leading-relaxed" style={{ color: 'var(--text-dim)' }}>{message}</p>
          </div>
        </div>
        <div className="flex gap-2 mt-4">
          <button onClick={onCancel} className="flex-1 py-2 rounded-xl text-xs font-medium transition-colors" style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)', border: '1px solid var(--border-color)' }}>
            {cancelLabel || (lang === 'zh' ? '取消' : 'Cancel')}
          </button>
          <button onClick={onConfirm} className="flex-1 py-2 rounded-xl text-xs font-medium text-white transition-colors" style={{ background: danger ? '#F43F5E' : 'var(--accent-purple)' }}>
            {confirmLabel || (lang === 'zh' ? '确认' : 'Confirm')}
          </button>
        </div>
      </motion.div>
    </div>
  )
}
