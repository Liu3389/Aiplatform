import { useState, useEffect, useRef, useCallback, ReactNode } from 'react'
import { motion, AnimatePresence } from 'framer-motion'

interface Section {
  id: string
  content: ReactNode
}

interface FullPageScrollProps {
  sections: Section[]
  onSectionChange?: (index: number) => void
}

export default function FullPageScroll({ sections, onSectionChange }: FullPageScrollProps) {
  const [currentIndex, setCurrentIndex] = useState(0)
  const [isScrolling, setIsScrolling] = useState(false)
  const [touchStart, setTouchStart] = useState<number | null>(null)
  const [touchEnd, setTouchEnd] = useState<number | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const scrollTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const minSwipeDistance = 50

  const scrollToSection = useCallback((index: number) => {
    if (index < 0 || index >= sections.length || isScrolling) return

    setIsScrolling(true)
    setCurrentIndex(index)
    onSectionChange?.(index)

    if (scrollTimeoutRef.current) {
      clearTimeout(scrollTimeoutRef.current)
    }

    scrollTimeoutRef.current = setTimeout(() => {
      setIsScrolling(false)
    }, 500)
  }, [sections.length, isScrolling, onSectionChange])

  const handleWheel = useCallback((e: WheelEvent) => {
    e.preventDefault()
    
    if (isScrolling) return

    if (e.deltaY > 0) {
      scrollToSection(currentIndex + 1)
    } else if (e.deltaY < 0) {
      scrollToSection(currentIndex - 1)
    }
  }, [currentIndex, isScrolling, scrollToSection])

  const handleKeyDown = useCallback((e: KeyboardEvent) => {
    if (e.key === 'ArrowDown' || e.key === 'PageDown') {
      e.preventDefault()
      scrollToSection(currentIndex + 1)
    } else if (e.key === 'ArrowUp' || e.key === 'PageUp') {
      e.preventDefault()
      scrollToSection(currentIndex - 1)
    } else if (e.key === 'Home') {
      e.preventDefault()
      scrollToSection(0)
    } else if (e.key === 'End') {
      e.preventDefault()
      scrollToSection(sections.length - 1)
    }
  }, [currentIndex, sections.length, scrollToSection])

  const onTouchStart = useCallback((e: React.TouchEvent) => {
    setTouchEnd(null)
    setTouchStart(e.targetTouches[0].clientY)
  }, [])

  const onTouchMove = useCallback((e: React.TouchEvent) => {
    setTouchEnd(e.targetTouches[0].clientY)
  }, [])

  const onTouchEnd = useCallback(() => {
    if (!touchStart || !touchEnd) return
    
    const distance = touchStart - touchEnd
    const isUpSwipe = distance > minSwipeDistance
    const isDownSwipe = distance < -minSwipeDistance

    if (isUpSwipe) {
      scrollToSection(currentIndex + 1)
    } else if (isDownSwipe) {
      scrollToSection(currentIndex - 1)
    }
  }, [touchStart, touchEnd, currentIndex, scrollToSection])

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    container.addEventListener('wheel', handleWheel, { passive: false })
    window.addEventListener('keydown', handleKeyDown)

    return () => {
      container.removeEventListener('wheel', handleWheel)
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [handleWheel, handleKeyDown])

  useEffect(() => {
    return () => {
      if (scrollTimeoutRef.current) {
        clearTimeout(scrollTimeoutRef.current)
      }
    }
  }, [])

  return (
    <div ref={containerRef} className="relative overflow-hidden h-screen">
      <AnimatePresence initial={false} mode="wait">
        <motion.div
          key={currentIndex}
          initial={{ opacity: 0, y: 50 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -50 }}
          transition={{ 
            duration: 0.5, 
            ease: [0.25, 0.46, 0.45, 0.94]
          }}
          className="h-full overflow-y-auto"
          onTouchStart={onTouchStart}
          onTouchMove={onTouchMove}
          onTouchEnd={onTouchEnd}
        >
          {sections[currentIndex].content}
        </motion.div>
      </AnimatePresence>

      {/* 滚动指示器 */}
      <div className="fixed right-6 top-1/2 -translate-y-1/2 z-50 flex flex-col gap-3">
        {sections.map((section, index) => (
          <button
            key={section.id}
            onClick={() => scrollToSection(index)}
            className={`w-2.5 h-2.5 rounded-full transition-all duration-300 ${
              index === currentIndex 
                ? 'bg-[var(--accent-purple)] scale-125' 
                : 'bg-[var(--text-dim)] opacity-40 hover:opacity-70'
            }`}
            aria-label={`Go to section ${index + 1}`}
          />
        ))}
      </div>
    </div>
  )
}
