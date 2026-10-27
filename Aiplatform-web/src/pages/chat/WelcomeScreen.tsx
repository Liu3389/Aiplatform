import PlatformMark from '@/components/PlatformMark'

interface WelcomeScreenProps {
  model: string
  lang: 'zh' | 'en'
  onSuggestionClick: (text: string) => void
}

const SUGGESTIONS = [
  { zh: '用一句话介绍 AI Gateway 平台', en: 'Introduce the AI Gateway platform in one sentence' },
  { zh: '写一个 Python 快速排序算法', en: 'Write a Python quicksort algorithm' },
  { zh: '解释 Transformer 架构的原理', en: 'Explain the Transformer architecture' },
  { zh: '帮我设计一个 RESTful API 接口', en: 'Help me design a RESTful API' },
  { zh: '对比 React 和 Vue 的优缺点', en: 'Compare the pros and cons of React and Vue' },
  { zh: '写一份 Docker 部署的最佳实践', en: 'Write Docker deployment best practices' },
]

export default function WelcomeScreen({ model, lang, onSuggestionClick }: WelcomeScreenProps) {
  const t = (zh: string, en: string) => lang === 'zh' ? zh : en

  return (
    <div className="flex flex-col items-center justify-center flex-1 py-10 px-4">
      {/* Logo */}
      <PlatformMark className="mb-6 h-20 w-20 rounded-3xl shadow-lg sm:mb-8" />

      {/* Heading */}
      <h1 className="text-xl sm:text-2xl font-bold mb-2 text-center tracking-tight"
        style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-display)' }}>
        {t('有什么可以帮助你的？', 'How can I help you?')}
      </h1>

      {/* Model indicator */}
      <div className="flex items-center gap-2 mb-1.5 px-3 py-1 rounded-full text-[11px]"
        style={{ background: 'rgba(139,92,246,0.08)', border: '1px solid rgba(139,92,246,0.1)' }}>
        <span className="w-1.5 h-1.5 rounded-full animate-pulse-glow"
          style={{ background: '#8B5CF6' }} />
        <span style={{ color: 'var(--text-dim)' }}>
          {t(`当前模型: ${model}`, `Model: ${model}`)}
        </span>
      </div>

      {/* Features */}
      <p className="text-[12px] mb-8 sm:mb-10 text-center max-w-md"
        style={{ color: 'var(--text-dim)', opacity: 0.7 }}>
        {t('支持 Markdown 渲染 · 代码高亮 · 流式输出 · 文件对话 · 点数计费', 'Markdown · Code Highlighting · Streaming · File Chat · Points Billing')}
      </p>

      {/* Suggestion cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 w-full max-w-xl stagger-fade">
        {SUGGESTIONS.map((s, i) => (
          <button
            key={i}
            onClick={() => onSuggestionClick(lang === 'zh' ? s.zh : s.en)}
            className="text-left p-3.5 sm:p-4 rounded-2xl border transition-all duration-300 group"
            style={{
              background: 'var(--surface-glass)',
              borderColor: 'var(--glass-border)',
              color: 'var(--text-secondary)',
              backdropFilter: 'blur(8px)',
              WebkitBackdropFilter: 'blur(8px)',
            }}
          >
            <div className="flex items-start gap-3">
              <div className="w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0 transition-transform duration-300 group-hover:scale-110 group-hover:rotate-6"
                style={{ background: 'rgba(139,92,246,0.08)' }}>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#8B5CF6" strokeWidth="2">
                  <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
                </svg>
              </div>
              <span className="text-[12px] sm:text-[13px] leading-relaxed group-hover:text-[var(--text-primary)] transition-colors">
                {lang === 'zh' ? s.zh : s.en}
              </span>
            </div>
          </button>
        ))}
      </div>
    </div>
  )
}
