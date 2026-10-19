import { useToastStore } from '@/stores/toastStore'
import { motion, AnimatePresence } from 'framer-motion'
import { Z } from '@/constants/zIndex'

export default function ToastContainer() {
  const { toasts, removeToast } = useToastStore()

  const bgMap: Record<string, string> = {
    error: 'rgba(244,63,94,0.95)',
    warning: 'rgba(245,158,11,0.95)',
    info: 'rgba(59,130,246,0.95)',
    success: 'rgba(16,185,129,0.95)',
  }

  const iconMap: Record<string, string> = {
    error: 'M12 9v2m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z',
    warning: 'M12 9v2m0 4h.01M5.07 19H18.93a2 2 0 001.73-3L13.73 4.99a2 2 0 00-3.46 0L3.34 16a2 2 0 001.73 3z',
    info: 'M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z',
    success: 'M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z',
  }

  return (
    <div className="fixed top-4 right-4 flex flex-col gap-2 max-w-sm w-full pointer-events-none" style={{ zIndex: Z.TOAST }}>
      <AnimatePresence>
        {toasts.map((t) => (
          <motion.div
            key={t.id}
            initial={{ opacity: 0, x: 80, scale: 0.95 }}
            animate={{ opacity: 1, x: 0, scale: 1 }}
            exit={{ opacity: 0, x: 80, scale: 0.95 }}
            transition={{ type: 'spring', damping: 25, stiffness: 300 }}
            className="pointer-events-auto rounded-xl px-4 py-3 shadow-2xl flex items-start gap-3 cursor-pointer"
            style={{ background: bgMap[t.type] || bgMap.info, backdropFilter: 'blur(12px)' }}
            onClick={() => removeToast(t.id)}
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" className="flex-shrink-0 mt-0.5">
              <path d={iconMap[t.type] || iconMap.info} />
            </svg>
            <div className="min-w-0">
              <div className="text-xs font-semibold text-white">{t.title}</div>
              <div className="text-[11px] text-white/80 mt-0.5">{t.message}</div>
            </div>
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  )
}
