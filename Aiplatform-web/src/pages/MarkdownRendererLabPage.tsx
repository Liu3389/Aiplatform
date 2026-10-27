import { useEffect, useMemo, useRef, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import remarkMath from 'remark-math'
import rehypeKatex from 'rehype-katex'
import rehypeHighlight from 'rehype-highlight'
import { Streamdown } from 'streamdown'
import { code } from '@streamdown/code'
import { math } from '@streamdown/math'
import { mermaid } from '@streamdown/mermaid'
import { cjk } from '@streamdown/cjk'
import MarkdownRenderer from '@/components/chat/MarkdownRenderer'
import { useThemeStore } from '@/stores/themeStore'
import './MarkdownRendererLabPage.css'
import 'katex/dist/katex.min.css'
import 'highlight.js/styles/github.min.css'
import 'streamdown/styles.css'

const markdownFixture = `# AI 对话 Markdown 渲染实验

同一份模型输出，在三种 React 渲染方案中对照查看。默认展示推荐候选 **Streamdown**，也可以切换到平台现有渲染器和标准插件组合。

## GFM 表格：首行回归样例

| 首列表头 | 解析能力 | 当前状态 |
|:--|:--:|--:|
| **第一行数据** | 表头与数据行分离 | ✅ 正常 |
| DeepSeek Flash | 文本、图像与工具调用 | 已接入 |
| 模型可用性 | 权限、配置和能力提示 | 可验证 |

## 常见 Markdown 元素

- **粗体**、*斜体*、~~删除线~~、\`行内代码\`与[产品链接](https://lingdian-ai.top)
- 有序列表会保持编号：
  1. 选择模型
  2. 上传输入
  3. 检查结果
- [x] GFM 任务列表
- [ ] 流式输出结束后保留结构

> 引用内容应与正文有清楚的层级和边线。

---

## 代码块与公式

\`\`\`typescript title="streaming.ts"
type ChatEvent = { type: 'delta'; text: string } | { type: 'done' }

function append(current: string, event: ChatEvent) {
  return event.type === 'delta' ? current + event.text : current
}
\`\`\`

行内公式 $E = mc^2$，以及独立公式：

$$
P(\\text{success}) = \\frac{\\text{valid output}}{\\text{valid output} + \\text{errors}}
$$

## 图表与流程图

\`\`\`mermaid
xychart-beta
  title "Markdown checks"
  x-axis [table, code, math, chart]
  y-axis "Pass rate" 0 --> 100
  bar [100, 100, 100, 100]
\`\`\`

\`\`\`mermaid
flowchart LR
  A[选择模型] --> B{支持视觉?}
  B -->|是| C[上传图片]
  B -->|否| D[提示切换模型]
  C --> E[发送到模型]
\`\`\`

## 图片与链接

![本地 Markdown 渲染示意图](/markdown-lab-sample.svg)

图片来自本地静态样例，不会请求第三方图片服务。
`

const candidates = [
  {
    id: 'streamdown',
    name: 'Vercel Streamdown',
    label: '推荐候选',
    summary: '面向 AI 流式输出，支持未闭合 Markdown、GFM、KaTeX、Mermaid、Shiki 代码高亮，并默认做 HTML 安全处理。',
    features: ['增量输出', 'GFM 表格', '数学公式', 'Mermaid 图表', 'Shiki 高亮', '安全处理'],
  },
  {
    id: 'platform',
    name: '凌点 AI 当前渲染器',
    label: '当前线上实现',
    summary: '基于 react-markdown 和插件组合，包含平台代码块、公式、Mermaid 与模型格式修复逻辑。',
    features: ['增量输出', 'GFM 表格', '数学公式', 'Mermaid 图表', '代码高亮', '平台样式'],
  },
  {
    id: 'remark',
    name: 'react-markdown + 插件',
    label: '标准组合基线',
    summary: '使用 CommonMark/GFM 解析与 KaTeX、高亮插件；Mermaid 通常需要自行接入图表组件。',
    features: ['静态解析', 'GFM 表格', '数学公式', '代码高亮', '安全默认', '插件扩展'],
  },
] as const

type CandidateId = typeof candidates[number]['id']

function PlainReactMarkdown({ children }: { children: string }) {
  return (
    <div className="renderer-lab-markdown renderer-lab-remark">
      <ReactMarkdown remarkPlugins={[remarkGfm, remarkMath]} rehypePlugins={[rehypeKatex, rehypeHighlight]}>
        {children}
      </ReactMarkdown>
    </div>
  )
}

export default function MarkdownRendererLabPage() {
  const isDark = useThemeStore(state => state.theme === 'dark')
  const [selected, setSelected] = useState<CandidateId>('streamdown')
  const [visibleMarkdown, setVisibleMarkdown] = useState(markdownFixture)
  const [isStreaming, setIsStreaming] = useState(false)
  const [headerStatus, setHeaderStatus] = useState('检查中')
  const previewRef = useRef<HTMLDivElement>(null)
  const streamTimerRef = useRef<number | null>(null)
  const selectedCandidate = useMemo(() => candidates.find(item => item.id === selected)!, [selected])

  useEffect(() => {
    const frame = window.requestAnimationFrame(() => {
      const firstHeader = previewRef.current?.querySelector('thead th')?.textContent?.trim()
      setHeaderStatus(firstHeader === '首列表头' ? '首行表头已渲染' : firstHeader ? `表头：${firstHeader}` : '未检测到表头')
    })
    return () => window.cancelAnimationFrame(frame)
  }, [selected, visibleMarkdown, isStreaming])

  useEffect(() => () => {
    if (streamTimerRef.current !== null) window.clearInterval(streamTimerRef.current)
  }, [])

  const simulateStream = () => {
    if (isStreaming) return
    setIsStreaming(true)
    setVisibleMarkdown('')
    if (streamTimerRef.current !== null) window.clearInterval(streamTimerRef.current)
    let cursor = 0
    streamTimerRef.current = window.setInterval(() => {
      cursor = Math.min(cursor + 36, markdownFixture.length)
      setVisibleMarkdown(markdownFixture.slice(0, cursor))
      if (cursor >= markdownFixture.length) {
        if (streamTimerRef.current !== null) window.clearInterval(streamTimerRef.current)
        streamTimerRef.current = null
        setIsStreaming(false)
      }
    }, 18)
  }

  return (
    <main className="renderer-lab min-h-screen px-4 py-5 sm:px-8 sm:py-8">
      <div className="mx-auto max-w-5xl">
        <header className="mb-6 flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.2em] text-violet-500">Developer preview · Markdown</p>
            <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">AI 输出渲染器对照</h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 opacity-70">
              模拟凌点 AI 的对话气泡，使用同一份 Markdown 同时验证首行表格、代码、公式、图表和流式过程。
            </p>
          </div>
          <div className="flex gap-2">
            <button className="renderer-lab-action" onClick={simulateStream} disabled={isStreaming}>
              {isStreaming ? '正在模拟输出…' : '模拟流式输出'}
            </button>
            <button className="renderer-lab-action" onClick={() => { setVisibleMarkdown(markdownFixture); setIsStreaming(false) }}>
              重置样例
            </button>
          </div>
        </header>

        <section className="renderer-lab-card mb-5 p-4 sm:p-5" aria-label="渲染器选择">
          <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
            <div>
              <h2 className="text-sm font-semibold">选择渲染方案</h2>
              <p className="mt-1 text-xs opacity-60">每个方案都在相同的 AI 助手消息样式中运行。</p>
            </div>
            <span className="rounded-full bg-violet-500/10 px-2.5 py-1 text-[10px] font-medium text-violet-500">首行表格专项验收</span>
          </div>
          <div className="grid gap-2 md:grid-cols-3">
            {candidates.map(candidate => (
              <button
                key={candidate.id}
                type="button"
                onClick={() => setSelected(candidate.id)}
                aria-pressed={selected === candidate.id}
                className={`renderer-lab-option ${selected === candidate.id ? 'is-active' : ''}`}
              >
                <span className="flex items-center justify-between gap-2">
                  <span className="font-semibold">{candidate.name}</span>
                  <span className="renderer-lab-tag">{candidate.label}</span>
                </span>
                <span className="mt-2 block text-left text-xs leading-5 opacity-65">{candidate.summary}</span>
              </button>
            ))}
          </div>
          <div className="mt-3 flex flex-wrap gap-1.5">
            {selectedCandidate.features.map(feature => <span key={feature} className="renderer-lab-feature">{feature}</span>)}
          </div>
        </section>

        <section className="renderer-lab-chat" aria-label="模拟对话">
          <div className="renderer-lab-user-message">
            请用 Markdown 展示表格、代码、公式和一个图表，重点确认表格第一行不要丢。
          </div>
          <article className="renderer-lab-assistant-message">
            <div className="mb-4 flex items-center gap-2 border-b pb-3" style={{ borderColor: 'var(--border-color)' }}>
              <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-gradient-to-br from-violet-500 to-blue-500 text-xs font-bold text-white">凌</div>
              <div className="min-w-0 flex-1">
                <div className="text-xs font-semibold">凌点 AI</div>
                <div className="text-[10px] opacity-55">{selectedCandidate.name}{isStreaming ? ' · 正在生成' : ' · 完成'}</div>
              </div>
              <span className={`renderer-lab-header-status ${headerStatus === '首行表头已渲染' ? 'is-good' : ''}`}>
                {headerStatus}
              </span>
            </div>
            <div className="renderer-lab-preview" ref={previewRef}>
              {selected === 'platform' && <MarkdownRenderer content={visibleMarkdown} isStreaming={isStreaming} />}
              {selected === 'remark' && <PlainReactMarkdown>{visibleMarkdown}</PlainReactMarkdown>}
              {selected === 'streamdown' && (
                <div className={`renderer-lab-streamdown ${isDark ? 'dark' : ''}`}>
                  <Streamdown
                    mode={isStreaming ? 'streaming' : 'static'}
                    isAnimating={isStreaming}
                    plugins={{ code, math, mermaid, cjk }}
                    shikiTheme={['github-light', 'github-dark']}
                  >
                    {visibleMarkdown}
                  </Streamdown>
                </div>
              )}
            </div>
          </article>
        </section>

        <footer className="mt-5 flex flex-wrap items-center justify-between gap-2 text-[11px] opacity-55">
          <span>本地预览 · 不会发起模型请求，也不会上传样例图片</span>
          <span>样例覆盖：标题、段落、链接、强调、列表、任务项、引用、分隔线、表格、代码、公式、图表与图片</span>
        </footer>
      </div>
    </main>
  )
}
