import { prepareWithSegments, layoutWithLines, type PreparedTextWithSegments, type LayoutLine } from '@chenglou/pretext'

const getStyle = () => ({
  fontSize: 15,
  fontFamily: "'Inter', 'SF Pro Display', system-ui, sans-serif",
  fontMono: "'JetBrains Mono', 'Fira Code', monospace",
  lineHeight: 1.75,
  colorText: getComputedStyle(document.documentElement).getPropertyValue('--pretext-text').trim() || '#e2e8f0',
  colorMuted: getComputedStyle(document.documentElement).getPropertyValue('--pretext-muted').trim() || '#94a3b8',
  colorBold: getComputedStyle(document.documentElement).getPropertyValue('--pretext-bold').trim() || '#f1f5f9',
  colorCode: getComputedStyle(document.documentElement).getPropertyValue('--pretext-code').trim() || '#a5b4fc',
  colorCodeBg: getComputedStyle(document.documentElement).getPropertyValue('--pretext-code-bg').trim() || 'rgba(99, 102, 241, 0.15)',
  colorHeading: getComputedStyle(document.documentElement).getPropertyValue('--pretext-heading').trim() || '#f1f5f9',
  colorBlockBg: getComputedStyle(document.documentElement).getPropertyValue('--pretext-block-bg').trim() || 'rgba(15, 23, 42, 0.8)',
  colorBlockBorder: getComputedStyle(document.documentElement).getPropertyValue('--pretext-block-border').trim() || 'rgba(99, 102, 241, 0.2)',
  paddingX: 24,
  paddingY: 16,
  maxWidth: 800,
  blockRadius: 8,
})

export class PretextEngine {
  private canvas: HTMLCanvasElement
  private ctx: CanvasRenderingContext2D
  private dpr: number
  private cursorVisible = false
  private cursorTimer = 0
  private prepared: PreparedTextWithSegments | null = null
  private lines: LayoutLine[] = []
  private totalHeight = 0
  private style = getStyle()

  constructor(canvas: HTMLCanvasElement) {
    this.canvas = canvas
    const ctx = canvas.getContext('2d')
    if (!ctx) throw new Error('Canvas 2D context not available')
    this.ctx = ctx
    this.dpr = window.devicePixelRatio || 1
    this.refreshStyle()
  }

  private refreshStyle() {
    this.style = getStyle()
  }

  setContent(text: string) {
    this.refreshStyle()
    if (!text) {
      this.prepared = null
      this.lines = []
      this.totalHeight = 0
      this.render()
      return
    }

    const font = `${this.style.fontSize}px ${this.style.fontFamily}`
    this.prepared = prepareWithSegments(text, font, { whiteSpace: 'pre-wrap' })
    const maxW = this.style.maxWidth - this.style.paddingX * 2
    const lineH = this.style.fontSize * this.style.lineHeight
    const result = layoutWithLines(this.prepared, maxW, lineH)
    this.lines = result.lines
    this.totalHeight = result.height
    this.render()
  }

  private render() {
    const { ctx, dpr } = this
    const { maxWidth, paddingX, paddingY, fontSize, fontFamily, lineHeight } = this.style
    const lineH = fontSize * lineHeight
    const canvasH = Math.max(this.totalHeight + paddingY * 2 + 40, 200)

    this.canvas.width = (maxWidth + paddingX * 2) * dpr
    this.canvas.height = canvasH * dpr
    this.canvas.style.width = `${maxWidth + paddingX * 2}px`
    this.canvas.style.height = `${canvasH}px`
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)

    ctx.clearRect(0, 0, maxWidth + paddingX * 2, canvasH)

    const bgColor = this.style.colorBlockBg.replace(/rgba?\((\d+),\s*(\d+),\s*(\d+).*\)/, (_, r, g, b) =>
      `rgb(${r},${g},${b})`
    )
    ctx.fillStyle = bgColor
    this.roundRect(ctx, paddingX - 8, paddingY - 8, maxWidth + 16, canvasH - paddingY + 8, 12)
    ctx.fill()

    ctx.textBaseline = 'top'
    ctx.textAlign = 'left'
    ctx.font = `${fontSize}px ${fontFamily}`
    ctx.fillStyle = this.style.colorText

    let y = paddingY
    for (const line of this.lines) {
      ctx.fillText(line.text, paddingX, y)
      y += lineH
    }

    if (this.cursorVisible && this.lines.length > 0) {
      const lastLine = this.lines[this.lines.length - 1]
      const cx = paddingX + lastLine.width + 2
      const cy = paddingY + (this.lines.length - 1) * lineH
      ctx.fillStyle = '#818cf8'
      ctx.fillRect(cx, cy, 2, fontSize * 1.2)
    }
  }

  private roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
    if (w < 2 * r) r = w / 2
    if (h < 2 * r) r = h / 2
    ctx.beginPath()
    ctx.moveTo(x + r, y)
    ctx.arcTo(x + w, y, x + w, y + h, r)
    ctx.arcTo(x + w, y + h, x, y + h, r)
    ctx.arcTo(x, y + h, x, y, r)
    ctx.arcTo(x, y, x + w, y, r)
    ctx.closePath()
  }

  startCursor() {
    this.cursorVisible = true
    this.cursorTimer = window.setInterval(() => {
      this.cursorVisible = !this.cursorVisible
      this.render()
    }, 530)
  }

  stopCursor() {
    this.cursorVisible = false
    if (this.cursorTimer) {
      clearInterval(this.cursorTimer)
      this.cursorTimer = 0
    }
    this.render()
  }

  destroy() {
    this.stopCursor()
  }
}
