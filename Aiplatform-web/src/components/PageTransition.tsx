import { useEffect, useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { useNavigate } from 'react-router-dom'
import PlatformMark from '@/components/PlatformMark'

interface PageTransitionProps {
  targetPath: string
  children?: React.ReactNode
}

export function usePageTransition() {
  const [isTransitioning, setIsTransitioning] = useState(false)
  const navigate = useNavigate()

  const transitionTo = (path: string) => {
    setIsTransitioning(true)
    setTimeout(() => {
      navigate(path)
    }, 600)
  }

  return { isTransitioning, transitionTo }
}

export default function PageTransition({ targetPath, children }: PageTransitionProps) {
  const [show, setShow] = useState(true)
  const navigate = useNavigate()

  useEffect(() => {
    const timer = setTimeout(() => {
      setShow(false)
      setTimeout(() => {
        navigate(targetPath, { replace: true })
      }, 400)
    }, 100)

    return () => clearTimeout(timer)
  }, [targetPath, navigate])

  return (
    <AnimatePresence>
      {show && (
        <motion.div
          className="fixed inset-0 z-[9999] flex items-center justify-center"
          style={{
            background: 'var(--bg-primary)',
          }}
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          exit={{ opacity: 0, scale: 1.05 }}
          transition={{ duration: 0.4, ease: [0.34, 1.56, 0.64, 1] }}
        >
          <div className="flex flex-col items-center gap-6">
            <motion.div
              className="h-16 w-16 rounded-2xl"
              initial={{ scale: 0, rotate: -180 }}
              animate={{ scale: 1, rotate: 0 }}
              exit={{ scale: 0, rotate: 180 }}
              transition={{ duration: 0.6, ease: [0.34, 1.56, 0.64, 1] }}
            >
              <PlatformMark className="h-16 w-16 rounded-2xl" />
            </motion.div>

            <motion.div
              className="h-1 rounded-full overflow-hidden"
              style={{
                width: 200,
                background: 'var(--border-color)',
              }}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.2 }}
            >
              <motion.div
                className="h-full rounded-full"
                style={{
                  background: 'linear-gradient(90deg, #8B5CF6, #6366F1, #3B82F6)',
                }}
                initial={{ width: '0%' }}
                animate={{ width: '100%' }}
                transition={{ duration: 0.8, ease: 'easeInOut' }}
              />
            </motion.div>

            <motion.p
              className="text-sm font-medium"
              style={{ color: 'var(--text-secondary)' }}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.3 }}
            >
              正在进入 AI 对话...
            </motion.p>
          </div>

          {children}
        </motion.div>
      )}
    </AnimatePresence>
  )
}
