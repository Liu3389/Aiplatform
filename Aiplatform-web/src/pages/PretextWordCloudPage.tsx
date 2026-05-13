import { useRef, useEffect, useState } from 'react'
import PretextWordCloud, { WordCloudWord, PretextWordCloudRef } from '@/components/PretextWordCloud'

const WORDS = [
  'Pretext', 'Layout', 'Canvas', 'Reflow', 'DOM', 'Engine', 'Text', 'Measure',
  'Render', 'TypeScript', 'React', 'Vite', 'Zero', 'Fast', 'Stream', 'SSE',
  'Chat', 'API', 'Token', 'Model', 'DeepSeek', 'OpenAI', 'Claude', 'GPT',
  'GPU', 'Neural', 'Vector', 'Embed', 'Prompt', 'Agent', 'Chain', 'Tool',
  'Cloud', 'Edge', 'Infer', 'Train', 'Data', 'Scale', 'Safe', 'Align',
  'Python', 'Rust', 'Node', 'Docker', 'K8s', 'Redis', 'SQL', 'Graph',
  'Arch', 'Design', 'System', 'Flow', 'Pipe', 'Cache', 'Queue', 'Batch',
  'Auth', 'JWT', 'OAuth', 'CORS', 'TLS', 'Hash', 'Salt', 'Cipher',
  'Bento', 'Grid', 'Glass', 'Neon', 'Glow', 'Dark', 'Aurora', 'Noise',
]

const COLORS = [
  '#8B5CF6','#3B82F6','#06B6D4','#F59E0B','#10B981','#F43F5E',
  '#A78BFA','#60A5FA','#22D3EE','#FBBF24','#34D399','#FB7185',
  '#7C3AED','#2563EB','#0891B2','#D97706','#059669','#E11D48',
]

function generateDenseCloud(count: number): WordCloudWord[] {
  const words: WordCloudWord[] = []
  for (let i = 0; i < count; i++) {
    const word = WORDS[i % WORDS.length]
    const freq = Math.random()
    const size = 10 + freq * 48
    words.push({ text: word, size, color: COLORS[i % COLORS.length] })
  }
  return words
}

export default function PretextWordCloudPage() {
  const wordCloudRef = useRef<PretextWordCloudRef>(null)
  const [textInput, setTextInput] = useState('')
  const [fps, setFps] = useState(60)
  const fpsRef = useRef({ frames: 0, last: performance.now() })

  useEffect(() => {
    const words = generateDenseCloud(80)
    wordCloudRef.current?.setWords(words)

    // FPS meter
    const measure = () => {
      fpsRef.current.frames++
      const now = performance.now()
      if (now - fpsRef.current.last >= 1000) {
        setFps(fpsRef.current.frames)
        fpsRef.current.frames = 0
        fpsRef.current.last = now
      }
      requestAnimationFrame(measure)
    }
    const id = requestAnimationFrame(measure)
    return () => cancelAnimationFrame(id)
  }, [])

  const handleGenerateFromText = () => {
    if (!textInput.trim()) return
    const rawWords = textInput.toLowerCase().replace(/[^\w\s\u4e00-\u9fff]/g,'').split(/\s+/).filter(w=>w.length>1)
    const freq = new Map<string,number>()
    rawWords.forEach(w=>freq.set(w,(freq.get(w)||0)+1))
    const sorted = [...freq.entries()].sort((a,b)=>b[1]-a[1]).slice(0,60)
    const maxF = sorted[0]?.[1]||1
    const generated: WordCloudWord[] = sorted.map(([text,c],i)=>({
      text, size:12+(c/maxF)*48, color:COLORS[i%COLORS.length]
    }))
    wordCloudRef.current?.setWords(generated)
  }

  return (
    <div className="p-6 max-w-5xl mx-auto overflow-y-auto h-full">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-white">Pretext 词云引擎</h1>
        <p className="text-sm text-neutral-400 mt-1">
          基于 @chenglou/pretext 文本测量 + Canvas 2D 渲染的高性能词云
        </p>
      </div>

      {/* FPS + Stats */}
      <div className="flex items-center gap-4 mb-4">
        <div className="glass-panel-sm px-4 py-2 flex items-center gap-3">
          <span className="text-[10px] text-neutral-500">FPS</span>
          <span className={`text-sm font-bold font-mono ${fps>=55?'text-accent-emerald':'text-accent-amber'}`}>{fps}</span>
          <span className="w-1.5 h-1.5 rounded-full bg-accent-emerald animate-glow-pulse"/>
        </div>
        <div className="glass-panel-sm px-4 py-2 flex items-center gap-3">
          <span className="text-[10px] text-neutral-500">引擎</span>
          <span className="text-sm font-bold text-brand-purple">Canvas 2D + Pretext</span>
        </div>
        <div className="glass-panel-sm px-4 py-2 flex items-center gap-3">
          <span className="text-[10px] text-neutral-500">交互</span>
          <span className="text-sm text-neutral-300">鼠标推开 · 弹簧回弹</span>
        </div>
        <div className="text-[10px] text-neutral-600 ml-auto">零 DOM 重排 · 60FPS 丝滑</div>
      </div>

      {/* Cloud */}
      <div className="glass-panel p-4 mb-6">
        <PretextWordCloud ref={wordCloudRef} className="mb-3" />
        <div className="flex items-center justify-center gap-3">
          <button onClick={()=>wordCloudRef.current?.setWords(generateDenseCloud(80))} className="btn-neon px-4 py-2 text-xs">刷新词云</button>
          <button onClick={()=>wordCloudRef.current?.clear()} className="px-4 py-2 bg-white/[0.04] text-neutral-300 text-xs rounded-xl hover:bg-white/[0.08] transition-colors">清空</button>
        </div>
      </div>

      {/* Generate from text */}
      <div className="glass-panel p-5 mb-6">
        <h3 className="text-sm font-semibold text-white mb-3">自定义词云</h3>
        <textarea value={textInput} onChange={e=>setTextInput(e.target.value)} placeholder="输入文本，自动分析词频生成词云..." rows={3}
          className="input-glow w-full px-4 py-3 text-sm resize-none mb-3"/>
        <button onClick={handleGenerateFromText} disabled={!textInput.trim()} className="btn-neon px-5 py-2 text-xs disabled:opacity-40">生成词云</button>
      </div>

      {/* Tech explanation */}
      <div className="glass-panel-sm p-5">
        <h3 className="text-sm font-semibold text-white mb-3">Pretext 引擎技术说明</h3>
        <div className="grid grid-cols-2 gap-4 text-xs text-neutral-400">
          <div className="space-y-2">
            <div className="flex items-center gap-2"><span className="w-1 h-1 rounded-full bg-brand-purple"/> <code className="text-brand-purple">prepare(text, font)</code> 一次性字形测量 + Unicode分词</div>
            <div className="flex items-center gap-2"><span className="w-1 h-1 rounded-full bg-brand-blue"/> <code className="text-brand-blue">layout(prepared, width, lineH)</code> 纯算术换行计算</div>
            <div className="flex items-center gap-2"><span className="w-1 h-1 rounded-full bg-brand-cyan"/> 零 DOM 重排 · 微秒级布局 · 全语言支持</div>
          </div>
          <div className="space-y-2">
            <div className="flex items-center gap-2"><span className="w-1 h-1 rounded-full bg-accent-emerald"/> Canvas 2D 离屏测量 + 碰撞检测螺旋布局</div>
            <div className="flex items-center gap-2"><span className="w-1 h-1 rounded-full bg-accent-amber"/> 鼠标斥力场 · 弹簧回弹动画 · requestAnimationFrame</div>
            <div className="flex items-center gap-2"><span className="w-1 h-1 rounded-full bg-accent-rose"/> 80+ 词密集排布 · 120px 斥力半径 · 实时重排</div>
          </div>
        </div>
      </div>
    </div>
  )
}
