import { useRef, useEffect, forwardRef, useImperativeHandle } from 'react'
import { prepare, layout } from '@chenglou/pretext'

export interface WordCloudWord {
  text: string
  size: number
  color: string
}

export interface PretextWordCloudRef {
  setWords: (words: WordCloudWord[]) => void
  clear: () => void
}

const CANVAS_W = 900
const CANVAS_H = 600
const FONT_FAM = "'Inter','system-ui',sans-serif"
const MOUSE_REPEL_RADIUS = 120
const MOUSE_REPEL_FORCE = 0.08

interface Placed { text: string; size: number; color: string; x: number; y: number; w: number; h: number; ox: number; oy: number }

function measureWord(text: string, fontSize: number): number {
  const font = `700 ${fontSize}px ${FONT_FAM}`
  const mCanvas = document.createElement('canvas')
  const ctx = mCanvas.getContext('2d')
  if (!ctx) return fontSize * text.length * 0.6
  ctx.font = font
  return ctx.measureText(text).width + 4
}

function spiralLayout(words: WordCloudWord[]): Placed[] {
  const cx = CANVAS_W / 2
  const cy = CANVAS_H / 2
  words.sort((a, b) => b.size - a.size)

  const placed: { x: number; y: number; w: number; h: number }[] = []
  const result: Placed[] = []

  for (const word of words) {
    const w = measureWord(word.text, word.size)
    const h = word.size * 1.25
    let bestX = cx - w / 2
    let bestY = cy - h / 2
    let bestDist = Infinity
    const pad = word.size * 0.12 + 2

    // spiral search
    for (let t = 0; t < 3000; t++) {
      const angle = t * 0.12
      const r = angle * 2.2
      const tx = cx + Math.cos(angle) * r - w / 2
      const ty = cy + Math.sin(angle) * r - h / 2

      if (tx < 8 || ty < 8 || tx + w > CANVAS_W - 8 || ty + h > CANVAS_H - 8) continue

      let hit = false
      for (const p of placed) {
        if (tx + pad < p.x + p.w && tx + w - pad > p.x && ty + pad < p.y + p.h && ty + h - pad > p.y) { hit = true; break }
      }
      if (!hit) {
        const d = Math.abs(tx + w / 2 - cx) + Math.abs(ty + h / 2 - cy)
        if (d < bestDist) { bestDist = d; bestX = tx; bestY = ty }
        if (d < 30) break
      }
    }

    const pw = measureWord(word.text, word.size)
    result.push({ text: word.text, size: word.size, color: word.color, x: bestX + pw / 2, y: bestY + h / 2, w: pw, h, ox: bestX + pw / 2, oy: bestY + h / 2 })
    placed.push({ x: bestX, y: bestY, w: pw, h })
  }
  return result
}

type Props = { className?: string }

function PretextWordCloudInner({ className = '' }: Props, ref: React.Ref<PretextWordCloudRef>) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const wordsRef = useRef<Placed[]>([])
  const mouseRef = useRef({ x: -999, y: -999 })
  const frameRef = useRef(0)

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    const dpr = window.devicePixelRatio || 1
    canvas.width = CANVAS_W * dpr
    canvas.height = CANVAS_H * dpr
    canvas.style.width = `${CANVAS_W}px`
    canvas.style.height = `${CANVAS_H}px`
    ctx.scale(dpr, dpr)

    const animate = () => {
      ctx.clearRect(0, 0, CANVAS_W, CANVAS_H)
      const grad = ctx.createLinearGradient(0, 0, CANVAS_W, CANVAS_H)
      grad.addColorStop(0, '#0f172a')
      grad.addColorStop(0.5, '#1e1b4b')
      grad.addColorStop(1, '#0f172a')
      ctx.fillStyle = grad
      ctx.fillRect(0, 0, CANVAS_W, CANVAS_H)

      const mx = mouseRef.current.x
      const my = mouseRef.current.y

      ctx.textAlign = 'center'
      ctx.textBaseline = 'middle'

      for (const w of wordsRef.current) {
        // mouse repulsion
        let tx = w.ox, ty = w.oy
        const dx = w.x - mx
        const dy = w.y - my
        const dist = Math.sqrt(dx * dx + dy * dy)
        if (dist < MOUSE_REPEL_RADIUS && dist > 0.1) {
          const force = (MOUSE_REPEL_RADIUS - dist) / MOUSE_REPEL_RADIUS * MOUSE_REPEL_FORCE * 300
          tx = w.x + (dx / dist) * force
          ty = w.y + (dy / dist) * force
        }

        // smooth spring back
        w.x += (tx - w.x) * 0.15
        w.y += (ty - w.y) * 0.15

        ctx.save()
        ctx.translate(w.x, w.y)
        ctx.shadowColor = w.color
        ctx.shadowBlur = w.size * 0.35
        ctx.fillStyle = w.color
        ctx.font = `700 ${w.size}px ${FONT_FAM}`
        ctx.fillText(w.text, 0, 0)
        ctx.shadowBlur = 0
        ctx.fillStyle = w.color
        ctx.fillText(w.text, 0, 0)
        ctx.restore()
      }

      frameRef.current = requestAnimationFrame(animate)
    }

    frameRef.current = requestAnimationFrame(animate)
    return () => cancelAnimationFrame(frameRef.current)
  }, [])

  useImperativeHandle(ref, () => ({
    setWords: (raw: WordCloudWord[]) => { wordsRef.current = spiralLayout(raw) },
    clear: () => { wordsRef.current = [] },
  }), [])

  return (
    <div className={`relative flex justify-center ${className}`}>
      <canvas
        ref={canvasRef}
        className="word-cloud-canvas rounded-2xl shadow-2xl cursor-crosshair"
        onMouseMove={e => {
          const r = canvasRef.current?.getBoundingClientRect()
          if (r) { const s = CANVAS_W / r.width; mouseRef.current = { x: (e.clientX - r.left) * s, y: (e.clientY - r.top) * s } }
        }}
        onMouseLeave={() => { mouseRef.current = { x: -999, y: -999 } }}
      />
    </div>
  )
}

const PretextWordCloud = forwardRef(PretextWordCloudInner)
export default PretextWordCloud
