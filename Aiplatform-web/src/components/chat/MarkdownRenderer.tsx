import { useMemo, useState, useCallback } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { useThemeStore } from '@/stores/themeStore'

interface Props { content: string; isStreaming?: boolean }

const COPY_TIMEOUT = 1500

function doCopy(text: string, setter: (v: string) => void) {
  navigator.clipboard.writeText(text).catch(() => {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.left = '-9999px'
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
  })
  setter('COPIED')
  setTimeout(() => setter('COPY'), COPY_TIMEOUT)
}

function doDownload(text: string, language: string, setter: (v: string) => void) {
  const extensionMap: Record<string, string> = {
    js: 'js', ts: 'ts', jsx: 'jsx', tsx: 'tsx',
    py: 'py', python: 'py',
    java: 'java', cpp: 'cpp', c: 'c', cs: 'cs', go: 'go', rs: 'rust', rb: 'rb',
    html: 'html', css: 'css', scss: 'scss', less: 'less',
    json: 'json', xml: 'xml', yaml: 'yaml', yml: 'yml', toml: 'toml',
    sql: 'sql', sh: 'sh', bash: 'sh', zsh: 'sh',
    md: 'md', markdown: 'md',
    txt: 'txt', text: 'txt', plaintext: 'txt',
    dockerfile: 'Dockerfile', docker: 'Dockerfile',
  }
  const ext = extensionMap[language.toLowerCase()] || 'txt'
  const fileName = `code_${Date.now()}.${ext}`
  const blob = new Blob([text], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  a.click()
  URL.revokeObjectURL(url)
  setter('DOWNLOADED')
  setTimeout(() => setter('DOWNLOAD'), COPY_TIMEOUT)
}

function CodeBlockHeader({ language, codeText, dimColor }: { language: string; codeText: string; dimColor: string }) {
  const [copyLabel, setCopyLabel] = useState('COPY')
  const [downloadLabel, setDownloadLabel] = useState('DOWNLOAD')

  const handleCopy = useCallback((e: React.MouseEvent) => {
    e.stopPropagation()
    doCopy(codeText, setCopyLabel)
  }, [codeText])

  const handleDownload = useCallback((e: React.MouseEvent) => {
    e.stopPropagation()
    doDownload(codeText, language, setDownloadLabel)
  }, [codeText, language])

  return (
    <div className="flex items-center justify-between px-4 py-1.5 select-none" style={{ borderBottom: '1px solid var(--code-block-border)' }}>
      <span className="text-[10px] font-mono uppercase tracking-wider" style={{ color: dimColor }}>
        {language || 'code'}
      </span>
      <div className="flex items-center gap-1">
        <button
          onClick={handleCopy}
          className="text-[10px] px-2 py-0.5 rounded transition-colors hover:bg-white/5 cursor-pointer font-mono"
          style={{ color: dimColor }}
        >
          {copyLabel}
        </button>
        <button
          onClick={handleDownload}
          className="text-[10px] px-2 py-0.5 rounded transition-colors hover:bg-white/5 cursor-pointer font-mono"
          style={{ color: dimColor }}
        >
          {downloadLabel}
        </button>
      </div>
    </div>
  )
}

export default function MarkdownRenderer({ content, isStreaming }: Props) {
  const isDark = useThemeStore(s => s.theme) === 'dark'
  const dimColor = isDark ? '#94A3B8' : '#64748B'

  const components = useMemo(() => ({
    code({ node, inline, className, children, ...props }: any) {
      const rawText = String(children).replace(/\n$/, '')
      if (!inline && rawText) {
        const match = /language-(\w+)/.exec(className || '')
        const language = match ? match[1] : ''
        return (
          <div className="my-3 rounded-xl overflow-hidden border" style={{
            borderColor: 'var(--code-block-border)',
            background: isDark ? '#0F172A' : '#F1F5F9',
          }}>
            <CodeBlockHeader language={language} codeText={rawText} dimColor={dimColor} />
            <pre className="!m-0 !p-4 overflow-x-auto text-xs sm:text-sm leading-relaxed" style={{
              background: isDark ? '#0F172A' : '#F1F5F9',
            }}>
              <code className={className} {...props}>
                {rawText}
              </code>
            </pre>
          </div>
        )
      }
      return (
        <code className="px-1 py-0.5 rounded text-[0.85em] font-mono" style={{
          background: isDark ? 'rgba(148,163,184,0.12)' : 'rgba(71,85,105,0.08)',
          color: isDark ? '#E2E8F0' : '#334155',
        }}>
          {children}
        </code>
      )
    },
    p({ children }: any) {
      if (typeof children === 'string' || (Array.isArray(children) && children.every((c: any) => typeof c === 'string' || c?.type === 'code'))) {
        return <p className="my-2 leading-7">{children}</p>
      }
      return <div className="my-2 leading-7">{children}</div>
    },
    ul({ children }: any) {
      return <ul className="list-disc pl-6 my-2 space-y-1">{children}</ul>
    },
    ol({ children }: any) {
      return <ol className="list-decimal pl-6 my-2 space-y-1">{children}</ol>
    },
    a({ href, children }: any) {
      return <a href={href} target="_blank" rel="noopener noreferrer" className="underline underline-offset-2" style={{ color: 'var(--accent-purple)' }}>{children}</a>
    },
    blockquote({ children }: any) {
      return <blockquote className="border-l-3 pl-4 my-2 italic opacity-80" style={{ borderColor: 'var(--accent-purple)' }}>{children}</blockquote>
    },
    table({ children }: any) {
      return <div className="overflow-x-auto my-3"><table className="min-w-full border-collapse text-xs sm:text-sm" style={{ border: '1px solid var(--border-color)' }}>{children}</table></div>
    },
    th({ children }: any) {
      return <th className="border px-3 py-2 text-left font-medium" style={{ borderColor: 'var(--border-color)', background: 'var(--bg-surface)' }}>{children}</th>
    },
    td({ children }: any) {
      return <td className="border px-3 py-2" style={{ borderColor: 'var(--border-color)' }}>{children}</td>
    },
    hr() {
      return <hr className="my-4" style={{ borderColor: 'var(--border-color)' }} />
    },
    h1({ children }: any) { return <h1 className="text-xl font-semibold mt-5 mb-2" style={{ color: 'var(--text-primary)' }}>{children}</h1> },
    h2({ children }: any) { return <h2 className="text-lg font-semibold mt-4 mb-2" style={{ color: 'var(--text-primary)' }}>{children}</h2> },
    h3({ children }: any) { return <h3 className="text-base font-semibold mt-3 mb-1.5" style={{ color: 'var(--text-primary)' }}>{children}</h3> },
    strong({ children }: any) { return <strong style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{children}</strong> },
    em({ children }: any) { return <em className="italic opacity-90">{children}</em> },
  }), [isDark, dimColor])

  return (
    <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
      {content || (isStreaming ? '' : '')}
    </ReactMarkdown>
  )
}