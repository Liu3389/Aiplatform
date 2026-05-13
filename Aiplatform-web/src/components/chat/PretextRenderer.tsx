import { useRef, useEffect, useCallback, useImperativeHandle, forwardRef } from 'react'
import { PretextEngine } from '@/engine/PretextEngine'

export interface PretextRendererRef {
  setContent: (text: string) => void
  startCursor: () => void
  stopCursor: () => void
}

const PretextRenderer = forwardRef<PretextRendererRef, { className?: string }>(
  function PretextRenderer({ className = '' }, ref) {
    const canvasRef = useRef<HTMLCanvasElement>(null)
    const engineRef = useRef<PretextEngine | null>(null)

    useEffect(() => {
      const canvas = canvasRef.current
      if (!canvas) return
      const engine = new PretextEngine(canvas)
      engineRef.current = engine
      return () => { engine.destroy() }
    }, [])

    useImperativeHandle(ref, () => ({
      setContent: (text: string) => {
        engineRef.current?.setContent(text)
      },
      startCursor: () => {
        engineRef.current?.startCursor()
      },
      stopCursor: () => {
        engineRef.current?.stopCursor()
      },
    }), [])

    return (
      <canvas
        ref={canvasRef}
        className={`pretext-canvas mx-auto ${className}`}
        style={{ maxWidth: '100%' }}
      />
    )
  }
)

export { PretextEngine }
export default PretextRenderer
