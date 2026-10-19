import { useEffect, useRef } from 'react'
import { AnimatePresence, motion } from 'framer-motion'

export interface PreviewImage {
  src: string
  alt: string
}

interface ImagePreviewDialogProps {
  image: PreviewImage | null
  onClose: () => void
  t: (zh: string, en: string) => string
}

const IMAGE_EXTENSIONS = new Set(['png', 'jpg', 'jpeg', 'gif', 'webp'])

export function isImageAttachment(fileName?: string, fileType?: string) {
  const type = (fileType || '').replace(/^\./, '').toLowerCase()
  if (IMAGE_EXTENSIONS.has(type)) return true
  const extension = fileName?.split('.').pop()?.toLowerCase()
  return Boolean(extension && IMAGE_EXTENSIONS.has(extension))
}

/** 将历史数据中的本地存储引用兼容为浏览器可访问地址。 */
export function getImageSource(previewUrl?: string, fileUrl?: string) {
  const source = previewUrl || fileUrl
  if (!source) return undefined
  if (source.startsWith('local://')) return '/api/uploads/' + source.slice('local://'.length)
  return source
}

export default function ImagePreviewDialog({ image, onClose, t }: ImagePreviewDialogProps) {
  const closeButtonRef = useRef<HTMLButtonElement>(null)
  const onCloseRef = useRef(onClose)

  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  useEffect(() => {
    if (!image) return

    const previousActiveElement = document.activeElement as HTMLElement | null
    closeButtonRef.current?.focus()

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        onCloseRef.current()
        return
      }
      // 预览层只有关闭按钮，保持 Tab 焦点在弹层内，避免误操作底层聊天界面。
      if (event.key === 'Tab') {
        event.preventDefault()
        closeButtonRef.current?.focus()
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      previousActiveElement?.focus()
    }
  }, [image?.src])

  return (
    <AnimatePresence>
      {image && (
        <>
          <motion.div
            aria-hidden="true"
            className="fixed inset-0 z-[110]"
            style={{ background: 'rgba(3, 7, 18, 0.78)', backdropFilter: 'blur(8px)' }}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
          />
          <motion.div
            className="fixed inset-0 z-[111] flex items-center justify-center p-4 sm:p-8"
            role="dialog"
            aria-modal="true"
            aria-label={t('图片预览', 'Image preview')}
            initial={{ opacity: 0, scale: 0.96, y: 8 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: 8 }}
            transition={{ duration: 0.18, ease: 'easeOut' }}
            onClick={onClose}
          >
            <div
              className="relative flex max-h-full max-w-full flex-col overflow-hidden rounded-2xl"
              style={{
                background: 'var(--bg-card)',
                border: '1px solid var(--border-color)',
                boxShadow: '0 24px 80px rgba(0,0,0,0.4)',
              }}
              onClick={event => event.stopPropagation()}
            >
              <div
                className="flex items-center justify-between gap-4 px-4 py-3"
                style={{ borderBottom: '1px solid var(--border-color)' }}
              >
                <span className="min-w-0 truncate text-xs font-medium" style={{ color: 'var(--text-secondary)' }}>
                  {image.alt}
                </span>
                <button
                  ref={closeButtonRef}
                  type="button"
                  onClick={onClose}
                  className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg transition-colors hover:bg-black/5 focus:outline-none focus:ring-2 focus:ring-violet-400 dark:hover:bg-white/10"
                  style={{ color: 'var(--text-secondary)' }}
                  aria-label={t('关闭图片预览', 'Close image preview')}
                  title={t('关闭', 'Close')}
                >
                  <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
                    <path d="M18 6 6 18M6 6l12 12" />
                  </svg>
                </button>
              </div>
              <div className="flex max-h-[calc(100vh-8rem)] min-h-[120px] min-w-[120px] items-center justify-center p-3 sm:p-5">
                <img
                  src={image.src}
                  alt={image.alt}
                  className="block max-h-[calc(100vh-10rem)] max-w-[min(90vw,1100px)] rounded-xl object-contain"
                />
              </div>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  )
}
