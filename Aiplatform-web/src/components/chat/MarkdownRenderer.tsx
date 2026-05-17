import { useMemo } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { useThemeStore } from '@/stores/themeStore'

interface Props { content: string; isStreaming?: boolean }

export default function MarkdownRenderer({ content, isStreaming }: Props) {
  const { theme } = useThemeStore()
  const isDark = theme === 'dark'

  const doCopy = (e: React.MouseEvent<HTMLButtonElement>) => {
    const wrapper = (e.currentTarget as HTMLElement).closest('.code-block-wrapper')
    const pre = wrapper?.querySelector('pre')
    const text = pre?.textContent || ''
    try {
      navigator.clipboard.writeText(text)
    } catch {
      const ta = document.createElement('textarea')
      ta.value = text; ta.style.position = 'fixed'; ta.style.left = '-9999px'
      document.body.appendChild(ta); ta.select(); document.execCommand('copy'); document.body.removeChild(ta)
    }
    e.currentTarget.textContent = 'COPIED'
    setTimeout(() => { e.currentTarget.textContent = 'COPY' }, 1500)
  }

  const themeStyles = useMemo(() => ({
    text: isDark ? '#E2E8F0' : '#334155',
    dim: isDark ? '#94A3B8' : '#64748B',
    code: isDark ? '#1E293B' : '#F1F5F9',
    codeText: isDark ? '#E2E8F0' : '#1E293B',
    codeBorder: isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.08)',
    tableBorder: isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.1)',
    tableHead: isDark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.03)',
    blockquote: isDark ? 'rgba(139,92,246,0.15)' : 'rgba(139,92,246,0.08)',
    blockquoteBorder: isDark ? '#8B5CF6' : '#7C3AED',
  }), [isDark])

  return (
    <div
      className="prose prose-sm max-w-none"
      style={{
        color: themeStyles.text,
        '--tw-prose-headings': themeStyles.text,
        '--tw-prose-links': '#8B5CF6',
        '--tw-prose-bold': themeStyles.text,
      } as React.CSSProperties}
    >
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          h1: ({ children, ...props }) => (
            <h1 className="text-xl font-bold mt-6 mb-3 pb-2" style={{ borderBottom: `2px solid ${themeStyles.blockquoteBorder}40`, color: themeStyles.text }} {...props}>{children}</h1>
          ),
          h2: ({ children, ...props }) => (
            <h2 className="text-lg font-bold mt-5 mb-2" style={{ color: themeStyles.text }} {...props}>{children}</h2>
          ),
          h3: ({ children, ...props }) => (
            <h3 className="text-base font-semibold mt-4 mb-2" style={{ color: themeStyles.text }} {...props}>{children}</h3>
          ),
          p: ({ children, ...props }) => (
            <p className="my-2 leading-relaxed" style={{ color: themeStyles.text }} {...props}>{children}</p>
          ),
          ul: ({ children, ...props }) => (
            <ul className="my-2 pl-5 space-y-1 list-disc" style={{ color: themeStyles.text }} {...props}>{children}</ul>
          ),
          ol: ({ children, ...props }) => (
            <ol className="my-2 pl-5 space-y-1 list-decimal" style={{ color: themeStyles.text }} {...props}>{children}</ol>
          ),
          li: ({ children, ...props }) => (
            <li className="leading-relaxed" style={{ color: themeStyles.text }} {...props}>{children}</li>
          ),
          strong: ({ children, ...props }) => (
            <strong className="font-semibold" style={{ color: '#8B5CF6' }} {...props}>{children}</strong>
          ),
          a: ({ children, href, ...props }) => (
            <a href={href} target="_blank" rel="noopener noreferrer" className="underline underline-offset-2" style={{ color: '#8B5CF6' }} {...props}>{children}</a>
          ),
          code: ({ className, children, ...props }: any) => {
            const isInline = !className
            if (isInline) {
              return (
                <code className="px-1.5 py-0.5 rounded text-xs font-mono" style={{ background: themeStyles.code, border: `1px solid ${themeStyles.codeBorder}`, color: '#8B5CF6' }} {...props}>{children}</code>
              )
            }
            const language = className?.replace('language-', '') || ''
            return (
              <div className="code-block-wrapper my-3 rounded-xl overflow-hidden border" style={{ borderColor: themeStyles.codeBorder, background: themeStyles.code }}>
                <div className="flex items-center justify-between px-4 py-1.5" style={{ borderBottom: `1px solid ${themeStyles.codeBorder}` }}>
                  <span className="text-[10px] font-mono uppercase tracking-wider" style={{ color: themeStyles.dim }}>{language || 'code'}</span>
                  <button
                    onClick={doCopy}
                    className="text-[10px] px-2 py-0.5 rounded transition-colors hover:bg-white/5 cursor-pointer select-none"
                    style={{ color: themeStyles.dim }}
                  >
                    COPY
                  </button>
                </div>
                <pre className="p-4 overflow-x-auto text-xs font-mono leading-relaxed" style={{ color: themeStyles.codeText }}><code className={className}>{children}</code></pre>
              </div>
            )
          },
          table: ({ children, ...props }) => (
            <div className="my-3 overflow-x-auto rounded-xl border" style={{ borderColor: themeStyles.tableBorder }}>
              <table className="w-full text-sm border-collapse" {...props}>{children}</table>
            </div>
          ),
          thead: ({ children, ...props }) => (
            <thead style={{ background: themeStyles.tableHead }} {...props}>{children}</thead>
          ),
          th: ({ children, ...props }) => (
            <th className="px-3 py-2 text-left text-xs font-semibold border-b" style={{ borderColor: themeStyles.tableBorder, color: themeStyles.dim }} {...props}>{children}</th>
          ),
          td: ({ children, ...props }) => (
            <td className="px-3 py-2 text-xs border-b" style={{ borderColor: themeStyles.tableBorder, color: themeStyles.text }} {...props}>{children}</td>
          ),
          blockquote: ({ children, ...props }) => (
            <blockquote className="my-3 px-4 py-2 rounded-r-lg border-l-4" style={{ background: themeStyles.blockquote, borderColor: themeStyles.blockquoteBorder, color: themeStyles.text }} {...props}>{children}</blockquote>
          ),
          hr: (props) => (
            <hr className="my-4" style={{ borderColor: themeStyles.tableBorder }} {...props} />
          ),
          img: ({ src, alt, ...props }) => (
            <img src={src} alt={alt} className="my-3 rounded-xl max-w-full" style={{ border: `1px solid ${themeStyles.tableBorder}` }} {...props} />
          ),
        }}
      >
        {content}
      </ReactMarkdown>
      {isStreaming && (
        <span className="inline-block w-2 h-4 ml-0.5 bg-brand-purple animate-pulse align-middle rounded-sm" />
      )}
    </div>
  )
}
