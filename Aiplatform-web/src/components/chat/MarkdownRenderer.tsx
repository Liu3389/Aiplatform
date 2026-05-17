import { useMemo, useState, useCallback, useEffect } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import rehypeRaw from 'rehype-raw'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.min.css'
import 'highlight.js/lib/common'
import { useThemeStore } from '@/stores/themeStore'

interface Props { content: string; isStreaming?: boolean }

const COPY_TIMEOUT = 1500

hljs.configure({
  languages: ['javascript', 'typescript', 'python', 'java', 'cpp', 'c', 'csharp', 'go', 'rust', 'ruby', 'php', 'swift', 'kotlin', 'sql', 'bash', 'shell', 'json', 'xml', 'yaml', 'css', 'scss', 'html', 'markdown', 'dockerfile', 'lua', 'r', 'matlab', 'perl', 'dart', 'scala', 'groovy', 'powershell', 'ini', 'toml', 'diff', 'protobuf', 'graphql', 'makefile', 'nginx', 'nginx.conf', 'properties'],
})

const CODE_LANGUAGES = new Set([
  'javascript', 'typescript', 'jsx', 'tsx', 'js', 'ts',
  'python', 'py',
  'java',
  'cpp', 'c', 'csharp', 'cs',
  'go', 'golang',
  'rust', 'rs',
  'ruby', 'rb',
  'php',
  'swift',
  'kotlin', 'kt',
  'sql',
  'bash', 'sh', 'shell', 'zsh',
  'json',
  'xml', 'html',
  'yaml', 'yml',
  'css', 'scss', 'less',
  'dockerfile', 'docker',
  'lua',
  'r',
  'matlab',
  'perl',
  'dart',
  'scala',
  'groovy',
  'powershell',
  'ini', 'toml',
  'diff',
  'protobuf',
  'graphql',
  'makefile',
  'nginx',
])

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
  setter('已复制')
  setTimeout(() => setter('复制'), COPY_TIMEOUT)
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
  setter('已下载')
  setTimeout(() => setter('下载'), COPY_TIMEOUT)
}

function getLanguageName(lang: string): string {
  const nameMap: Record<string, string> = {
    js: 'JavaScript', javascript: 'JavaScript',
    ts: 'TypeScript', typescript: 'TypeScript',
    jsx: 'React JSX', tsx: 'React TSX',
    py: 'Python', python: 'Python',
    java: 'Java',
    cpp: 'C++', c: 'C', cs: 'C#',
    go: 'Go', golang: 'Go',
    rs: 'Rust', rust: 'Rust',
    rb: 'Ruby', ruby: 'Ruby',
    php: 'PHP',
    swift: 'Swift',
    kt: 'Kotlin', kotlin: 'Kotlin',
    sql: 'SQL',
    sh: 'Shell', bash: 'Bash', shell: 'Shell', zsh: 'Zsh',
    json: 'JSON',
    xml: 'XML', html: 'HTML',
    yaml: 'YAML', yml: 'YAML',
    css: 'CSS', scss: 'SCSS', less: 'Less',
    md: 'Markdown', markdown: 'Markdown',
    dockerfile: 'Dockerfile', docker: 'Dockerfile',
    lua: 'Lua',
    r: 'R',
    matlab: 'MATLAB',
    perl: 'Perl',
    dart: 'Dart',
    scala: 'Scala',
    groovy: 'Groovy',
    powershell: 'PowerShell',
    ini: 'INI', toml: 'TOML',
    diff: 'Diff',
    protobuf: 'Protobuf',
    graphql: 'GraphQL',
    makefile: 'Makefile',
    nginx: 'Nginx',
    plaintext: 'Plain Text', text: 'Plain Text', code: 'Code',
  }
  return nameMap[lang.toLowerCase()] || lang.charAt(0).toUpperCase() + lang.slice(1)
}

function CodeBlockHeader({ language, codeText, dimColor, isDark }: { language: string; codeText: string; dimColor: string; isDark: boolean }) {
  const [copyLabel, setCopyLabel] = useState('复制')
  const [downloadLabel, setDownloadLabel] = useState('下载')
  const [showLineNumbers, setShowLineNumbers] = useState(true)

  const handleCopy = useCallback((e: React.MouseEvent) => {
    e.stopPropagation()
    doCopy(codeText, setCopyLabel)
  }, [codeText])

  const handleDownload = useCallback((e: React.MouseEvent) => {
    e.stopPropagation()
    doDownload(codeText, language, setDownloadLabel)
  }, [codeText, language])

  const handleToggleLineNumbers = useCallback((e: React.MouseEvent) => {
    e.stopPropagation()
    setShowLineNumbers(prev => !prev)
  }, [])

  return (
    <div className="flex items-center justify-between px-4 py-2 select-none border-b" style={{
      borderColor: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)',
      background: isDark ? 'rgba(255,255,255,0.02)' : 'rgba(0,0,0,0.01)',
    }}>
      <div className="flex items-center gap-2">
        <div className="flex gap-1.5">
          <div className="w-3 h-3 rounded-full" style={{ background: '#FF5F57' }} />
          <div className="w-3 h-3 rounded-full" style={{ background: '#FEBC2E' }} />
          <div className="w-3 h-3 rounded-full" style={{ background: '#28C840' }} />
        </div>
        <span className="text-[11px] font-mono ml-2" style={{ color: dimColor }}>
          {getLanguageName(language || 'code')}
        </span>
      </div>
      <div className="flex items-center gap-1">
        <button
          onClick={handleToggleLineNumbers}
          className="text-[10px] px-2 py-1 rounded transition-all hover:bg-white/10 cursor-pointer font-mono"
          style={{
            color: showLineNumbers ? 'var(--accent-purple)' : dimColor,
            background: showLineNumbers ? 'rgba(139,92,246,0.1)' : 'transparent',
          }}
          title={showLineNumbers ? '隐藏行号' : '显示行号'}
        >
          {showLineNumbers ? '行号' : '行号'}
        </button>
        <button
          onClick={handleCopy}
          className="text-[10px] px-2 py-1 rounded transition-all hover:bg-white/10 cursor-pointer font-mono"
          style={{ color: copyLabel === '已复制' ? '#10B981' : dimColor }}
        >
          {copyLabel}
        </button>
        <button
          onClick={handleDownload}
          className="text-[10px] px-2 py-1 rounded transition-all hover:bg-white/10 cursor-pointer font-mono"
          style={{ color: downloadLabel === '已下载' ? '#10B981' : dimColor }}
        >
          {downloadLabel}
        </button>
      </div>
    </div>
  )
}

function HighlightedCodeBlock({ code, language, showLineNumbers, isDark }: { code: string; language: string; showLineNumbers: boolean; isDark: boolean }) {
  const [highlightedHtml, setHighlightedHtml] = useState('')

  useEffect(() => {
    try {
      const lang = language && hljs.getLanguage(language) ? language : undefined
      const result = lang 
        ? hljs.highlight(code, { language: lang, ignoreIllegals: true })
        : hljs.highlightAuto(code)
      setHighlightedHtml(result.value)
    } catch {
      setHighlightedHtml(hljs.highlightAuto(code).value)
    }
  }, [code, language])

  const lines = highlightedHtml.split('\n')

  return (
    <div className="relative">
      <pre className="!m-0 !p-0 overflow-x-auto text-[13px] leading-[1.6]" style={{
        background: 'transparent',
        fontFamily: '"JetBrains Mono", "Fira Code", "Cascadia Code", "SF Mono", Monaco, Menlo, Consolas, "Liberation Mono", "Courier New", monospace',
        fontVariantLigatures: 'common-ligatures',
      }}>
        <code className="hljs" style={{ display: 'block', minWidth: 'fit-content' }}>
          {lines.map((line, i) => (
            <div key={i} className="flex" style={{
              background: showLineNumbers && (i % 2 === 0) ? (isDark ? 'rgba(255,255,255,0.015)' : 'rgba(0,0,0,0.015)') : 'transparent',
            }}>
              {showLineNumbers && (
                <span className="inline-block text-right select-none pr-4 pl-4 flex-shrink-0" style={{
                  color: isDark ? 'rgba(255,255,255,0.2)' : 'rgba(0,0,0,0.2)',
                  minWidth: '3.5em',
                  borderRight: `1px solid ${isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)'}`,
                }}>
                  {i + 1}
                </span>
              )}
              <span className="px-4" dangerouslySetInnerHTML={{ __html: line || ' ' }} />
            </div>
          ))}
        </code>
      </pre>
    </div>
  )
}

export default function MarkdownRenderer({ content, isStreaming }: Props) {
  const isDark = useThemeStore(s => s.theme) === 'dark'
  const dimColor = isDark ? '#94A3B8' : '#64748B'

  useEffect(() => {
    const style = document.createElement('style')
    style.textContent = `
      .hljs { background: transparent !important; padding: 0 !important; }
      .hljs-keyword { color: ${isDark ? '#C678DD' : '#A626A4'}; font-weight: 500; }
      .hljs-built_in { color: ${isDark ? '#E5C07B' : '#986801'}; }
      .hljs-type { color: ${isDark ? '#E5C07B' : '#986801'}; }
      .hljs-literal { color: ${isDark ? '#D19A66' : '#986801'}; }
      .hljs-number { color: ${isDark ? '#D19A66' : '#986801'}; }
      .hljs-string { color: ${isDark ? '#98C379' : '#50A14F'}; }
      .hljs-comment { color: ${isDark ? '#5C6370' : '#A0A1A7'}; font-style: italic; }
      .hljs-doctag { color: ${isDark ? '#C678DD' : '#A626A4'}; }
      .hljs-function { color: ${isDark ? '#61AFEF' : '#4078F2'}; }
      .hljs-title { color: ${isDark ? '#61AFEF' : '#4078F2'}; }
      .hljs-params { color: ${isDark ? '#ABB2BF' : '#383A42'}; }
      .hljs-meta { color: ${isDark ? '#D19A66' : '#986801'}; }
      .hljs-operator { color: ${isDark ? '#56B6C2' : '#0184BC'}; }
      .hljs-punctuation { color: ${isDark ? '#ABB2BF' : '#383A42'}; }
      .hljs-property { color: ${isDark ? '#E06C75' : '#E45649'}; }
      .hljs-attribute { color: ${isDark ? '#D19A66' : '#986801'}; }
      .hljs-variable { color: ${isDark ? '#E06C75' : '#E45649'}; }
      .hljs-symbol { color: ${isDark ? '#56B6C2' : '#0184BC'}; }
      .hljs-bullet { color: ${isDark ? '#D19A66' : '#986801'}; }
      .hljs-tag { color: ${isDark ? '#E06C75' : '#E45649'}; }
      .hljs-name { color: ${isDark ? '#E06C75' : '#E45649'}; font-weight: 500; }
      .hljs-section { color: ${isDark ? '#61AFEF' : '#4078F2'}; }
      .hljs-attr { color: ${isDark ? '#D19A66' : '#986801'}; }
      .hljs-addition { color: ${isDark ? '#98C379' : '#50A14F'}; background: rgba(80,161,79,0.1); }
      .hljs-deletion { color: ${isDark ? '#E06C75' : '#E45649'}; background: rgba(224,86,73,0.1); }
      .hljs-template-variable { color: ${isDark ? '#C678DD' : '#A626A4'}; }
      .hljs-link { color: ${isDark ? '#61AFEF' : '#4078F2'}; text-decoration: underline; }
      .hljs-regexp { color: ${isDark ? '#56B6C2' : '#0184BC'}; }
      .hljs-selector-tag { color: ${isDark ? '#E06C75' : '#E45649'}; }
      .hljs-selector-id { color: ${isDark ? '#61AFEF' : '#4078F2'}; }
      .hljs-selector-class { color: ${isDark ? '#98C379' : '#50A14F'}; }
      .hljs-selector-pseudo { color: ${isDark ? '#D19A66' : '#986801'}; }
      .hljs-subst { color: ${isDark ? '#ABB2BF' : '#383A42'}; }
      .hljs-emphasis { font-style: italic; }
      .hljs-strong { font-weight: bold; }
    `
    document.head.appendChild(style)
    return () => { document.head.removeChild(style) }
  }, [isDark])

  const components = useMemo(() => ({
    code({ node, inline, className, children, ...props }: any) {
      const rawText = String(children).replace(/\n$/, '')
      if (!inline && rawText) {
        const match = /language-(\w+)/.exec(className || '')
        const language = match ? match[1] : ''
        const isCodeLanguage = CODE_LANGUAGES.has(language.toLowerCase())
        
        if (isCodeLanguage) {
          return (
            <div className="my-3 rounded-xl overflow-hidden border" style={{
              borderColor: isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.08)',
              background: isDark ? '#1E2128' : '#F6F8FA',
              boxShadow: isDark ? '0 4px 24px rgba(0,0,0,0.4)' : '0 4px 24px rgba(0,0,0,0.08)',
            }}>
              <CodeBlockHeader language={language} codeText={rawText} dimColor={dimColor} isDark={isDark} />
              <HighlightedCodeBlock code={rawText} language={language} showLineNumbers={true} isDark={isDark} />
            </div>
          )
        }
        
        return (
          <pre className="my-2 p-3 rounded-lg text-[13px] leading-[1.6] overflow-x-auto font-mono" style={{
            background: isDark ? 'rgba(148,163,184,0.08)' : 'rgba(71,85,105,0.05)',
            color: isDark ? '#E2E8F0' : '#1E293B',
            border: `1px solid ${isDark ? 'rgba(148,163,184,0.12)' : 'rgba(71,85,105,0.1)'}`,
          }}>
            <code {...props} className={className}>
              {children}
            </code>
          </pre>
        )
      }
      return (
        <code className="px-1.5 py-0.5 rounded text-[0.85em] font-mono" style={{
          background: isDark ? 'rgba(148,163,184,0.15)' : 'rgba(71,85,105,0.1)',
          color: isDark ? '#E2E8F0' : '#1E293B',
          border: isDark ? '1px solid rgba(148,163,184,0.2)' : '1px solid rgba(71,85,105,0.15)',
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
    <div className="chat-markdown">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        rehypePlugins={[rehypeRaw]}
        components={components}
      >
        {content || (isStreaming ? '' : '')}
      </ReactMarkdown>
    </div>
  )
}
