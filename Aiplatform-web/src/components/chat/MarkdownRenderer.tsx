import { useMemo, useState, useCallback, useEffect, useId, useRef } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import rehypeRaw from 'rehype-raw'
import hljs from 'highlight.js/lib/core'
import 'highlight.js/styles/github-dark.min.css'
import { useThemeStore } from '@/stores/themeStore'
import { normalizeCompactListLine, normalizeCompactTableLine } from '@/utils/markdownStructure'

interface Props { content: string; isStreaming?: boolean }

const COPY_TIMEOUT = 1500

/**
 * 预处理数学公式定界符
 * 将 AI 输出的非标准公式定界符转换为 KaTeX 兼容格式：
 * - \[ ... \] → $$ ... $$  (LaTeX display math)
 * - \( ... \) → $ ... $     (LaTeX inline math)
 * - [ LaTeX formula ] → $$ ... $$  (当方括号内容包含 LaTeX 命令且不是 Markdown 链接时)
 * - ( LaTeX formula ) → $$ ... $$  (当圆括号包裹整行且包含 LaTeX 命令时)
 *
 * 注意：负向前瞻 (?!\() 确保 ] 后不跟 ( ，避免误伤 [text](url) 链接语法
 */
function preprocessMathDelimiters(content: string): string {
    if (!content) return content

    // 1. \[ ... \] → $$ ... $$  (标准 LaTeX display math)
    let result = content.replace(/\\\[([\s\S]*?)\\\]/g, (_, math) => `$$${math}$$`)

    // 2. \( ... \) → $ ... $  (标准 LaTeX inline math)
    result = result.replace(/\\\(([\s\S]*?)\\\)/g, (_, math) => `$${math}$`)

    // 3. [ 包含 LaTeX 命令的内容 ] → $$ ... $$
    //    仅当方括号内容包含反斜杠 LaTeX 命令（如 \frac \sin \sum \text 等）时转换
    //    (?!\() 排除 Markdown 链接 [text](url)
    result = result.replace(/\[([^\]]*\\[a-zA-Z][^\]]*)\](?!\()/g, (_, math) => `$$${math.trim()}$$`)

    // 4. ( 包含 LaTeX 命令的整行内容 ) → $$ ... $$
    //    匹配行首或冒号后的 ( ... ) ，内容必须包含 \ + 2个以上字母的 LaTeX 命令
    //    贪婪匹配 .+ 处理嵌套圆括号（匹配到最后一个 ) ）
    //    \\\\>[a-zA-Z]{2,} 排除 \n \t \r 等单字母转义
    result = result.replace(/(^|[\:：])\s*\((.+\\[a-zA-Z]{2,}.+)\)\s*$/gm, (_, prefix, math) => `${prefix} $$${math.trim()}$$`)

    return result
}

/**
 * 兼容部分模型返回的轻微 Markdown 格式错误。
 *
 * 常见情况是模型输出 "##标题" 或把下一个标题接在上一句话后面，
 * 这会让 GFM 把它当作普通文本，后续的目录、间距和表格结构也会一起失效。
 * 只在围栏代码块之外修复标题，绝不修改代码内容。
 */
/**
 * 修复“标题 + 表头”被压在同一行、下一行才出现分隔线的情况：
 * `## 状态码规范|状态码 |语义 |使用场景 |` + `|---|---|---|`。
 * 如果不拆开，GFM 会把整行当成标题，并生成一个空表头。
 */
function splitHeadingAndTableHeader(line: string): string[] | null {
    const match = line.match(/^(\s*#{1,6}\s*[^|\n]+?)\s*\|(.+\|)\s*$/)
    if (!match) return null

    const headers = match[2].split('|').map(cell => cell.trim()).filter(Boolean)
    if (headers.length < 2) return null
    return [match[1].trimEnd(), `| ${headers.join(' | ')} |`]
}

const HTTP_METHOD_PATTERN = '(?:GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)'
const HTTP_FENCE_LANGUAGES = new Set(['http', 'https', 'text', 'plaintext', 'txt'])
const NUMBERED_HEADING_BODY_CUES = [
    '文本', '原始', '自注意力', '单一', '每个', '通常', '编码器', '解码器',
    '训练时', '推理时', '标准', '主要', '核心', '给定', '因此', '后续', '注意力',
    '模型', '其中', '通过', '先被', '下面', '当前',
]

/**
 * 修复接口文档中常见的“代码块压成一行”问题。
 *
 * 只对 HTTP/文本代码块启用，避免误伤 Java、JSON 等真正需要保留原始换行的代码。
 * 模型有时会把多个请求、请求头或注释直接拼在一起，浏览器即使正确渲染 Markdown
 * 也只能得到一大段不可读的灰色文本，所以这里按明确的 HTTP 语义边界补回换行。
 */
function normalizeHttpCodeLine(line: string): string {
    if (!line) return line

    return line
        .replace(/\s+(?=#\s*--)/g, '\n')
        .replace(/([^\s])(?=#\s*--)/g, '$1\n')
        .replace(new RegExp(`\\s+(?=${HTTP_METHOD_PATTERN}\\s+/)`, 'g'), '\n')
        .replace(new RegExp(`([^\\s])(?=${HTTP_METHOD_PATTERN}\\s+/)`, 'g'), '$1\n')
        .replace(/\s+(?=(?:Content-Type|Accept|Authorization|Idempotency-Key|HTTP\/\d(?:\.\d)?):)/g, '\n')
        .replace(/([^\s])(?=(?:Content-Type|Accept|Authorization|Idempotency-Key|HTTP\/\d(?:\.\d)?):)/g, '$1\n')
}

/**
 * 有些模型会漏掉 HTTP 代码块的结束围栏，导致后面的 Markdown 标题、请求示例
 * 全部被当成代码。只从 HTTP/文本围栏中识别“明显的 Markdown 标题”并提前收口；
 * `# --用户` 这类 HTTP 注释只有一个 #，不会被误判。
 */
function findEmbeddedMarkdownHeading(line: string, language: string | null, isHttpLike: boolean): number {
    if (!isHttpLike && (!language || !HTTP_FENCE_LANGUAGES.has(language.toLowerCase()))) return -1
    const match = line.match(/#{2,6}(?=\s*[\u4e00-\u9fff\d])/)
    return match?.index ?? -1
}

/**
 * 将“## 2.标题正文”这类缺少换行的编号标题拆开。
 * 仅处理带编号的标题，并要求正文以常见的句子起始词开头，避免把普通中文段落
 * 过度切成标题。标题本身无法从模型的原始字符串中百分之百恢复时，保守优先。
 */
function splitCompactNumberedHeading(line: string): string {
    const match = line.match(/^(\s*#{1,6}\s*\d+[.、)]\s*)(.*)$/)
    if (!match) return line

    const titleAndBody = match[2]
    const boundary = NUMBERED_HEADING_BODY_CUES
        .map(cue => titleAndBody.indexOf(cue, 4))
        .filter(index => index >= 4)
        .sort((a, b) => a - b)[0]
    if (boundary === undefined || boundary <= 0 || boundary >= titleAndBody.length) return line

    const title = titleAndBody.slice(0, boundary).trimEnd()
    const body = titleAndBody.slice(boundary).trimStart()
    if (!title || !body) return line
    return `${match[1]}${title}\n\n${body}`
}

/**
 * 修复显示公式的结束围栏被模型拼到公式末尾的情况：`...W^O$$`。
 * remark-math 要求显示公式围栏独占一行，否则会把后续标题继续解析为公式。
 */
function normalizeDisplayMathFences(line: string): string {
    return line
        .replace(/(\S)(\${2,})/g, '$1\n$2')
        .replace(/(\${2,})(?=\S)/g, '$1\n')
}

/**
 * 兼容模型把标题、表格和代码围栏挤到同一行的情况。
 * 这类内容仍然是模型的原始文本，前端只做结构恢复，不修改代码围栏内部。
 */
function normalizeMarkdownStructure(content: string, isStreaming: boolean): string {
    if (!content) return content

    const lines = content.replace(/\r\n?/g, '\n').split('\n')
    const normalizedLines: string[] = []
    let fenceCharacter: '`' | '~' | null = null
    let fenceLanguage: string | null = null
    let fenceIsHttpLike = false
    const skippedTableSpacerLines = new Set<number>()
    const isTableSeparatorLine = (line: string | undefined): boolean => Boolean(
        line && /^\s*\|?\s*:?-{3,}:?\s*(?:\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(line),
    )

    const processOutsideFenceLine = (rawLine: string) => {
        // ATX 标题要求 # 与标题文字之间至少有一个空格。
        let line = rawLine.replace(/^(\s{0,3}#{1,6})(?!#)([^\s#])/, '$1 $2')
        // 标题可能紧接在上一句话、列表项或另一个标题后面。
        line = line.replace(/([^\n#])\s*(#{2,6})\s*(?=[\u4e00-\u9fff\d])/g, '$1\n$2 ')
        line = line.replace(/([。！？.!?])\s*(#{1,6})\s*(?=[\u4e00-\u9fff\d])/g, '$1\n$2 ')
        line = normalizeDisplayMathFences(line)
        line = splitCompactNumberedHeading(line)
        line = normalizeCompactListLine(line)
        // 标题后的引用符有时也会被模型挤在同一行。
        line = line.replace(/^(#{1,6}\s+[^>\n]+)>(?=\S)/, '$1\n\n> ')
        // 把紧贴正文的代码围栏拆成独立行，并修复 ```javaimport 这类缺少换行的语言标记。
        line = line.replace(/([^\n])(`{3,}|~{3,})/g, '$1\n$2')
        line = line.replace(/(`{3,}|~{3,})(mermaid|javascript|typescript|python|java|kotlin|swift|rust|golang|go|cpp|csharp|sql|bash|shell|json|yaml|xml|html|css|http|diff|text|plaintext)(?=\S)/gi, '$1$2\n')
        return line.split('\n')
    }

    for (let lineIndex = 0; lineIndex < lines.length; lineIndex += 1) {
        if (skippedTableSpacerLines.has(lineIndex)) continue
        const rawLine = lines[lineIndex]
        const headingTableLines = !fenceCharacter
            ? splitHeadingAndTableHeader(rawLine)
            : null
        if (headingTableLines) {
            let separatorIndex = lineIndex + 1
            while (separatorIndex < lines.length && !lines[separatorIndex].trim()) separatorIndex += 1
            if (separatorIndex > lineIndex + 1 && isTableSeparatorLine(lines[separatorIndex])) {
                for (let index = lineIndex + 1; index < separatorIndex; index += 1) {
                    skippedTableSpacerLines.add(index)
                }
            }
        }
        const candidateLines = fenceCharacter
            ? [rawLine]
            : headingTableLines
                ? headingTableLines.flatMap(processOutsideFenceLine)
                : processOutsideFenceLine(rawLine)
        let pendingOutsideLines = candidateLines

        while (pendingOutsideLines.length > 0) {
            const line = pendingOutsideLines.shift() || ''
            const fence = line.match(/^\s*(`{3,}|~{3,})([A-Za-z0-9_+-]*)/)

            if (fenceCharacter) {
                const headingIndex = findEmbeddedMarkdownHeading(line, fenceLanguage, fenceIsHttpLike)
                if (headingIndex >= 0) {
                    const codePrefix = line.slice(0, headingIndex).trimEnd()
                    if (codePrefix) {
                        normalizedLines.push(...normalizeHttpCodeLine(codePrefix).split('\n'))
                    }
                    normalizedLines.push(fenceCharacter.repeat(3))
                    fenceCharacter = null
                    fenceLanguage = null
                    fenceIsHttpLike = false
                    pendingOutsideLines.unshift(line.slice(headingIndex))
                    continue
                }

                // 如果模型直接用 ```mermaid 开启了下一个围栏，先补齐上一个代码块。
                if (fence && fence[2]) {
                    normalizedLines.push(fenceCharacter.repeat(3))
                    fenceCharacter = null
                    fenceLanguage = null
                    fenceIsHttpLike = false
                    pendingOutsideLines.unshift(line)
                    continue
                }

                if (!fenceIsHttpLike && /(?:#\s*--|(?:GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\s+\/|(?:Content-Type|Accept|Authorization|Idempotency-Key):)/i.test(line)) {
                    fenceIsHttpLike = true
                }
                const codeLine = fenceIsHttpLike
                    ? normalizeHttpCodeLine(line)
                    : line
                normalizedLines.push(...codeLine.split('\n'))
                if (fence && fence[1][0] === fenceCharacter) {
                    fenceCharacter = null
                    fenceLanguage = null
                    fenceIsHttpLike = false
                }
                continue
            }

            const tableLine = normalizeCompactTableLine(line)
            const tableLines = tableLine.split('\n')
            for (const tableRow of tableLines) {
                normalizedLines.push(tableRow)
                const openingFence = tableRow.match(/^\s*(`{3,}|~{3,})([A-Za-z0-9_+-]*)/)
                if (openingFence) {
                    fenceCharacter = openingFence[1][0] as '`' | '~'
                    fenceLanguage = openingFence[2] || null
                    fenceIsHttpLike = Boolean(fenceLanguage && HTTP_FENCE_LANGUAGES.has(fenceLanguage.toLowerCase()))
                }
            }
        }
    }

    if (fenceCharacter && !isStreaming) {
        normalizedLines.push(fenceCharacter.repeat(3))
    }
    return normalizedLines.join('\n')
}

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

const HIGHLIGHT_LANGUAGE_ALIASES: Record<string, string> = {
    js: 'javascript', jsx: 'javascript',
    ts: 'typescript', tsx: 'typescript',
    py: 'python',
    cs: 'csharp',
    golang: 'go',
    rs: 'rust',
    rb: 'ruby',
    kt: 'kotlin',
    sh: 'bash', zsh: 'bash',
    yml: 'yaml',
    docker: 'dockerfile',
}

const HIGHLIGHT_LANGUAGE_LOADERS: Record<string, () => Promise<{ default: any }>> = {
    javascript: () => import('highlight.js/lib/languages/javascript'),
    typescript: () => import('highlight.js/lib/languages/typescript'),
    python: () => import('highlight.js/lib/languages/python'),
    java: () => import('highlight.js/lib/languages/java'),
    cpp: () => import('highlight.js/lib/languages/cpp'),
    c: () => import('highlight.js/lib/languages/c'),
    csharp: () => import('highlight.js/lib/languages/csharp'),
    go: () => import('highlight.js/lib/languages/go'),
    rust: () => import('highlight.js/lib/languages/rust'),
    ruby: () => import('highlight.js/lib/languages/ruby'),
    php: () => import('highlight.js/lib/languages/php'),
    swift: () => import('highlight.js/lib/languages/swift'),
    kotlin: () => import('highlight.js/lib/languages/kotlin'),
    sql: () => import('highlight.js/lib/languages/sql'),
    bash: () => import('highlight.js/lib/languages/bash'),
    shell: () => import('highlight.js/lib/languages/shell'),
    json: () => import('highlight.js/lib/languages/json'),
    xml: () => import('highlight.js/lib/languages/xml'),
    yaml: () => import('highlight.js/lib/languages/yaml'),
    css: () => import('highlight.js/lib/languages/css'),
    scss: () => import('highlight.js/lib/languages/scss'),
    less: () => import('highlight.js/lib/languages/less'),
    html: () => import('highlight.js/lib/languages/xml'),
    markdown: () => import('highlight.js/lib/languages/markdown'),
    dockerfile: () => import('highlight.js/lib/languages/dockerfile'),
    lua: () => import('highlight.js/lib/languages/lua'),
    r: () => import('highlight.js/lib/languages/r'),
    matlab: () => import('highlight.js/lib/languages/matlab'),
    perl: () => import('highlight.js/lib/languages/perl'),
    dart: () => import('highlight.js/lib/languages/dart'),
    scala: () => import('highlight.js/lib/languages/scala'),
    groovy: () => import('highlight.js/lib/languages/groovy'),
    powershell: () => import('highlight.js/lib/languages/powershell'),
    ini: () => import('highlight.js/lib/languages/ini'),
    toml: () => import('highlight.js/lib/languages/ini'),
    diff: () => import('highlight.js/lib/languages/diff'),
    protobuf: () => import('highlight.js/lib/languages/protobuf'),
    graphql: () => import('highlight.js/lib/languages/graphql'),
    makefile: () => import('highlight.js/lib/languages/makefile'),
    nginx: () => import('highlight.js/lib/languages/nginx'),
}

function escapeCodeHtml(code: string): string {
    return code.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

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
                    行号
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
        let cancelled = false
        const normalizedLanguage = language.toLowerCase()
        const languageName = HIGHLIGHT_LANGUAGE_ALIASES[normalizedLanguage] || normalizedLanguage
        const loadLanguage = HIGHLIGHT_LANGUAGE_LOADERS[languageName]
        setHighlightedHtml(escapeCodeHtml(code))

        if (!loadLanguage) return () => { cancelled = true }

        loadLanguage()
            .then(({ default: languageDefinition }) => {
                if (cancelled) return
                if (!hljs.getLanguage(languageName)) hljs.registerLanguage(languageName, languageDefinition)
                setHighlightedHtml(hljs.highlight(code, { language: languageName, ignoreIllegals: true }).value)
            })
            .catch(() => { if (!cancelled) setHighlightedHtml(escapeCodeHtml(code)) })

        return () => { cancelled = true }
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
// mermaid 图表渲染：支持 图表/代码 视图切换，支持下载 PNG/SVG；流式输出中显示占位
// 视图状态持久化：主题切换会导致 components 重建→MermaidBlock 重新挂载，用模块级 Map 保留用户的视图选择
const mermaidViewMode = new Map<string, boolean>()

function normalizeMermaidCode(source: string): string {
    return source
        // Mermaid 的图表声明与第一条边不能挤在同一行，否则部分版本会报语法错误。
        .replace(/^(\s*(?:flowchart|graph)\s+(?:TB|TD|LR|RL|BT))\s+(?=\S)/i, '$1\n')
        // 模型偶尔会省略相邻节点之间的换行，补回常见的有向边起点。
        .replace(/([\]}])\s+(?=[A-Za-z][A-Za-z0-9_-]*\s+(?:-->|==>))/g, '$1\n')
}

function MermaidBlock({ code, isDark, isStreaming }: { code: string; isDark: boolean; isStreaming?: boolean }) {
    const baseId = useId().replace(/[^a-zA-Z0-9]/g, '')
    const [svg, setSvg] = useState('')
    const [failed, setFailed] = useState(false)
    const [showCode, setShowCodeState] = useState(() => mermaidViewMode.get(code) ?? false)
    const [collapsed, setCollapsed] = useState(false)
    const [isTall, setIsTall] = useState(false)
    const svgWrapperRef = useRef<HTMLDivElement>(null)

    // SVG 渲染后检测高度，超过阈值自动折叠大图（小图不受影响）
    useEffect(() => {
        if (!svg || !svgWrapperRef.current) return
        const el = svgWrapperRef.current
        const check = () => {
            const h = el.scrollHeight
            if (h > 450 && !isTall) { setIsTall(true); setCollapsed(true) }
            if (h <= 450 && isTall) { setIsTall(false); setCollapsed(false) }
        }
        check()
        const ro = new ResizeObserver(check)
        ro.observe(el)
        return () => ro.disconnect()
    }, [svg, isTall])

    useEffect(() => {
        if (isStreaming) return
        let cancelled = false
        const normalizedCode = normalizeMermaidCode(code)
        setSvg('')
        setFailed(false)
        const renderId = 'mmd-' + baseId + '-' + Date.now()
        import('mermaid')
            .then(({ default: mermaid }) => {
                if (cancelled) return undefined
                mermaid.initialize({
                    startOnLoad: false,
                    securityLevel: 'loose',
                    theme: isDark ? 'dark' : 'default',
                })
                return mermaid.render(renderId, normalizedCode)
            })
            .then(result => { if (result && !cancelled) setSvg(result.svg) })
            .catch(() => { if (!cancelled) setFailed(true) })
        return () => { cancelled = true }
    }, [code, isDark, isStreaming, baseId])

    const dimColor = isDark ? '#94A3B8' : '#64748B'

    // 切换视图时同步到模块级 Map（主题切换重建组件后仍能保持用户选择）
    const setShowCode = useCallback((v: boolean) => {
        setShowCodeState(v)
        mermaidViewMode.set(code, v)
    }, [code])

    // 复制 mermaid 源代码
    const [copied, setCopied] = useState(false)
    const copyCode = useCallback(() => {
        navigator.clipboard.writeText(code).then(() => {
            setCopied(true)
            setTimeout(() => setCopied(false), COPY_TIMEOUT)
        }).catch(() => {})
    }, [code])

    // 修正 SVG：补 xmlns 命名空间 + 修正尺寸 + 替换外部字体（Image 加载 SVG 时无法渲染外部字体，是 PNG 导出失败的主因）
    const normalizeSvg = useCallback((raw: string): { content: string; w: number; h: number } => {
        const doc = new DOMParser().parseFromString(raw, 'image/svg+xml')
        const svgEl = doc.querySelector('svg')
        let w = 1200
        let h = 800
        if (svgEl) {
            const vb = svgEl.getAttribute('viewBox')
            if (vb) {
                const parts = vb.trim().split(/[\s,]+/).map(Number)
                if (parts.length === 4 && parts[2] > 0 && parts[3] > 0) { w = parts[2]; h = parts[3] }
            }
            svgEl.setAttribute('xmlns', 'http://www.w3.org/2000/svg')
            svgEl.setAttribute('xmlns:xlink', 'http://www.w3.org/1999/xlink')
            svgEl.setAttribute('width', String(w))
            svgEl.setAttribute('height', String(h))
            // 注入内联样式：把所有外部字体替换为系统通用字体，确保 Image 加载 SVG 时能正常渲染文字
            const style = doc.createElement('style')
            style.textContent = 'text, tspan, foreignObject, div, p, span { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif !important; } foreignObject { overflow: visible; }'
            svgEl.insertBefore(style, svgEl.firstChild)
        }
        return { content: svgEl ? new XMLSerializer().serializeToString(svgEl) : raw, w, h }
    }, [])

    const triggerDownload = useCallback((href: string, fileName: string) => {
        const a = document.createElement('a')
        a.href = href
        a.download = fileName
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
    }, [])

    // SVG 矢量图下载（最可靠）
    const downloadSvg = useCallback(() => {
        if (!svg) return
        try {
            const { content } = normalizeSvg(svg)
            const blob = new Blob([content], { type: 'image/svg+xml;charset=utf-8' })
            const url = URL.createObjectURL(blob)
            triggerDownload(url, 'mermaid-diagram-' + Date.now() + '.svg')
            setTimeout(() => URL.revokeObjectURL(url), 2000)
        } catch { /* ignore */ }
    }, [svg, normalizeSvg, triggerDownload])

    // PNG 下载（失败自动降级为 SVG）
    const downloadPng = useCallback(() => {
        if (!svg) return
        try {
            const { content, w, h } = normalizeSvg(svg)
            const scale = 2
            const canvas = document.createElement('canvas')
            canvas.width = w * scale
            canvas.height = h * scale
            const ctx = canvas.getContext('2d')
            if (!ctx) throw new Error('canvas not supported')
            ctx.fillStyle = isDark ? '#1E2128' : '#F6F8FA'
            ctx.fillRect(0, 0, canvas.width, canvas.height)
            const img = new Image()
            const svgUrl = URL.createObjectURL(new Blob([content], { type: 'image/svg+xml;charset=utf-8' }))
            img.onload = () => {
                try {
                    if (img.naturalWidth === 0 || img.naturalHeight === 0) throw new Error('svg image size is 0')
                    ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
                    triggerDownload(canvas.toDataURL('image/png'), 'mermaid-diagram-' + Date.now() + '.png')
                } catch (e) {
                    console.warn('[Mermaid] PNG 导出失败，降级为 SVG：', e)
                    downloadSvg()
                } finally {
                    URL.revokeObjectURL(svgUrl)
                }
            }
            img.onerror = (e) => {
                console.warn('[Mermaid] SVG 图片加载失败：', e)
                URL.revokeObjectURL(svgUrl)
                downloadSvg()
            }
            img.src = svgUrl
        } catch (e) {
            console.warn('[Mermaid] PNG 导出异常：', e)
            downloadSvg()
        }
    }, [svg, isDark, normalizeSvg, triggerDownload, downloadSvg])

    const toolBtn = (active: boolean): React.CSSProperties => ({
        color: active ? 'var(--accent-purple)' : dimColor,
        background: active ? 'rgba(139,92,246,0.1)' : 'transparent',
    })

    if (isStreaming) {
        return (
            <div className="my-3 px-4 py-3 rounded-xl border text-[12px]" style={{
                borderColor: 'rgba(139,92,246,0.15)',
                background: 'rgba(139,92,246,0.04)',
                color: 'var(--text-dim)',
            }}>
                图表生成中...
            </div>
        )
    }

    const renderCode = () => (
        <pre className="m-0 p-3 text-[13px] leading-[1.6] overflow-x-auto font-mono" style={{
            background: isDark ? '#1E2128' : '#F6F8FA',
            color: isDark ? '#E2E8F0' : '#1E293B',
        }}>
      <code>{code}</code>
    </pre>
    )

    return (
        <div className="my-3 rounded-xl overflow-hidden border" style={{
            borderColor: isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.08)',
            background: isDark ? '#1E2128' : '#F6F8FA',
            boxShadow: isDark ? '0 4px 24px rgba(0,0,0,0.4)' : '0 4px 24px rgba(0,0,0,0.08)',
        }}>
            {/* 工具栏：图表/代码切换 + 下载 */}
            <div className="flex items-center justify-between px-4 py-2 select-none border-b" style={{
                borderColor: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)',
                background: isDark ? 'rgba(255,255,255,0.02)' : 'rgba(0,0,0,0.01)',
            }}>
                <div className="flex items-center gap-2">
                    <div className="w-2.5 h-2.5 rounded-[3px]" style={{ background: 'var(--accent-purple)' }} />
                    <span className="text-[11px] font-mono" style={{ color: dimColor }}>Mermaid</span>
                    {failed && <span className="text-[10px]" style={{ color: '#F59E0B' }}>渲染失败</span>}
                </div>
                <div className="flex items-center gap-1">
                    <button
                        onClick={() => setShowCode(false)}
                        className="text-[10px] px-2 py-1 rounded transition-all hover:bg-white/10 cursor-pointer font-mono"
                        style={toolBtn(!showCode)}
                    >
                        图表
                    </button>
                    <button
                        onClick={() => setShowCode(true)}
                        className="text-[10px] px-2 py-1 rounded transition-all hover:bg-white/10 cursor-pointer font-mono"
                        style={toolBtn(showCode)}
                    >
                        代码
                    </button>
                    <button
                        onClick={copyCode}
                        className="text-[10px] px-2 py-1 rounded transition-all hover:bg-white/10 cursor-pointer font-mono"
                        style={{ color: copied ? '#10B981' : dimColor }}
                        title="复制源代码"
                    >
                        {copied ? <span className="inline-flex items-center gap-1"><svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"><path d="m5 12 4 4L19 6" /></svg>已复制</span> : '复制'}
                    </button>
                    <button
                        onClick={downloadPng}
                        disabled={!svg}
                        className="text-[10px] px-2 py-1 rounded transition-all hover:bg-white/10 cursor-pointer font-mono disabled:opacity-40 disabled:cursor-not-allowed"
                        style={{ color: dimColor }}
                        title="下载 PNG 图片"
                    >
                        PNG
                    </button>
                    <button
                        onClick={downloadSvg}
                        disabled={!svg}
                        className="text-[10px] px-2 py-1 rounded transition-all hover:bg-white/10 cursor-pointer font-mono disabled:opacity-40 disabled:cursor-not-allowed"
                        style={{ color: dimColor }}
                        title="下载 SVG 矢量图"
                    >
                        SVG
                    </button>
                </div>
            </div>

            {showCode || failed ? renderCode() : (
                <div className="mermaid-container overflow-x-auto relative" style={{ padding: '12px' }}>
                    {svg ? (
                        <div
                            ref={svgWrapperRef}
                            style={{
                                maxHeight: collapsed ? '320px' : 'none',
                                overflow: 'hidden',
                                transition: 'max-height 0.3s ease',
                            }}
                        >
                            <div dangerouslySetInnerHTML={{ __html: svg }} />
                        </div>
                    ) : <div style={{ color: 'var(--text-dim)' }}>图表生成中...</div>}
                    {/* 折叠/展开按钮（仅图形高度超过阈值时显示） */}
                    {svg && collapsed && isTall && (
                        <div
                            className="absolute left-0 right-0 flex items-center justify-center cursor-pointer"
                            style={{
                                bottom: 0,
                                height: '72px',
                                background: `linear-gradient(to bottom, transparent, ${isDark ? '#1E2128' : '#F6F8FA'} 70%)`,
                            }}
                            onClick={() => setCollapsed(false)}
                        >
                            <span className="text-[11px] px-3 py-1 rounded-full" style={{ color: dimColor, background: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.04)' }}>
                                展开全图 ▼
                            </span>
                        </div>
                    )}
                </div>
            )}
            {/* 折叠控制条（展开状态下，图形较高时显示"收起"按钮） */}
            {(!showCode && !failed && svg && !collapsed && isTall) && (
                <div
                    className="flex items-center justify-center py-1 cursor-pointer hover:bg-white/5 border-t"
                    style={{ borderColor: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)' }}
                    onClick={() => setCollapsed(true)}
                >
                    <span className="text-[10px]" style={{ color: dimColor }}>收起 ▲</span>
                </div>
            )}
        </div>
    )
}

export default function MarkdownRenderer({ content, isStreaming }: Props) {
    const isDark = useThemeStore(s => s.theme) === 'dark'
    const dimColor = isDark ? '#94A3B8' : '#64748B'
    const preprocessedContent = useMemo(() => preprocessMathDelimiters(content), [content])
    const hasMath = /\$\$|\$(?=\S)[^$\n]+\$/.test(preprocessedContent)
    const [mathPlugins, setMathPlugins] = useState<{ remark: any; rehype: any } | null>(null)
    const [mathLoadFailed, setMathLoadFailed] = useState(false)

    useEffect(() => {
        if (!hasMath || mathPlugins || mathLoadFailed) return
        let cancelled = false
        Promise.all([
            import('remark-math'),
            import('rehype-katex'),
            import('katex/dist/katex.min.css'),
        ])
            .then(([remarkMathModule, rehypeKatexModule]) => {
                if (!cancelled) {
                    setMathPlugins({ remark: remarkMathModule.default, rehype: rehypeKatexModule.default })
                }
            })
            .catch(() => { if (!cancelled) setMathLoadFailed(true) })
        return () => { cancelled = true }
    }, [hasMath, mathPlugins, mathLoadFailed])

    const remarkPlugins = useMemo(
        () => mathPlugins ? [remarkGfm, mathPlugins.remark] : [remarkGfm],
        [mathPlugins],
    )
    const rehypePlugins = useMemo(
        () => mathPlugins ? [rehypeRaw, mathPlugins.rehype] : [rehypeRaw],
        [mathPlugins],
    )

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
      .chat-markdown .mermaid-container svg { max-width: 100% !important; height: auto !important; }
      .chat-markdown .katex-display { overflow-x: auto; overflow-y: hidden; padding: 8px 0; margin: 12px 0; }
      .chat-markdown .katex { font-size: 1.05em; }
      .chat-markdown .katex-error { color: ${isDark ? '#F87171' : '#DC2626'}; font-style: italic; }
    `
        document.head.appendChild(style)
        return () => { document.head.removeChild(style) }
    }, [isDark])

    const components = useMemo(() => ({
        code({ node, inline, className, children, ...props }: any) {
            // react-markdown v10 的 `inline` 标志与旧版行为不一致。行内 code
            // 和 fenced code 都会进入这里，因此不能再只依赖 `!inline` 判断。
            // fenced code 会带 language-* class，或者由 mdast-to-hast 以换行结尾；
            // 这能避免 field、ETag、kebab-case 被错误渲染成整行代码框。
            const childrenText = String(children)
            const rawText = childrenText.replace(/\n$/, '')
            const nodeClassName = Array.isArray(node?.properties?.className)
                ? node.properties.className.join(' ')
                : String(node?.properties?.className || '')
            const effectiveClassName = className || nodeClassName
            const isFencedCode = Boolean(effectiveClassName) || childrenText.endsWith('\n')
            if (isFencedCode && rawText) {
                const match = /language-(\w+)/.exec(effectiveClassName)
                const language = match ? match[1] : ''
                const langLower = language.toLowerCase()

                if (langLower === 'mermaid') {
                    return <MermaidBlock code={rawText} isDark={isDark} isStreaming={isStreaming} />
                }

                const isCodeLanguage = CODE_LANGUAGES.has(langLower)

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
            return <p className="my-2 leading-7">{children}</p>
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
    }), [isDark, dimColor, isStreaming])

    const processedContent = useMemo(
        () => normalizeMarkdownStructure(preprocessedContent, Boolean(isStreaming)),
        [preprocessedContent, isStreaming],
    )

    return (
        <div className="chat-markdown">
            <ReactMarkdown
                remarkPlugins={remarkPlugins}
                rehypePlugins={rehypePlugins}
                components={components}
            >
                {processedContent || (isStreaming ? '' : '')}
            </ReactMarkdown>
        </div>
    )
}
