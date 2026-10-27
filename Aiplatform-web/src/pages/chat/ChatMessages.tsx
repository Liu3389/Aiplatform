import { lazy, Suspense, useEffect, useRef, useCallback, useState } from 'react'
import MessageActions from './MessageActions'
import ImagePreviewDialog, { getImageSource, isImageAttachment, type PreviewImage } from '@/components/chat/ImagePreview'
import { useToastStore } from '@/stores/toastStore'
import PointsIcon from '@/components/PointsIcon'
import type { FrontendMessage, TokenStats, SearchInfo } from '@/types'

const MarkdownRenderer = lazy(() => import('@/components/chat/MarkdownRenderer'))

interface Props {
    conversationId?: string
    messages: FrontendMessage[]
    isStreaming: boolean
    tokenStats: TokenStats | null
    fileProcessStatus: string
    lang: string
    t: (zh: string, en: string) => string
    onLoadMore?: React.MutableRefObject<(() => Promise<void>) | undefined>
    isLoadingMore?: boolean
    hasMore?: boolean
    pendingFiles?: Array<{ fileId: string; fileName: string; fileSize: number; fileType: string; ossUrl?: string; uploadTime: number }>
    onCopyMessage?: (msg: FrontendMessage) => void
    onRegenerate?: () => void
    onFeedback?: (msgIndex: number, type: 'like' | 'dislike') => void
    searchInfo?: SearchInfo | null
    onDeleteMessage?: (msgIndex: number) => void
}

function parseTokenInfo(tokensStr?: string | number) {
    if (!tokensStr) return null
    const str = String(tokensStr)
    const inputMatch = str.match(/Input:\s*(\d+)/)
    const outputMatch = str.match(/Output:\s*(\d+)/)
    const costMatch = str.match(/Cost:\s*([\d.]+)/)
    const pointsMatch = str.match(/Points:\s*([\d.]+)/)
    return {
        inputTokens: inputMatch ? parseInt(inputMatch[1]) : 0,
        outputTokens: outputMatch ? parseInt(outputMatch[1]) : 0,
        cost: costMatch ? parseFloat(costMatch[1]) : 0,
        pointsConsumed: pointsMatch ? parseFloat(pointsMatch[1]) : 0,
    }
}

function resultDomain(url: string) {
    try {
        return new URL(url).hostname.replace(/^www\./, '')
    } catch {
        return url
    }
}

export default function ChatMessages({
                                         conversationId, messages, isStreaming, fileProcessStatus, lang, t,
                                         onLoadMore, isLoadingMore = false, hasMore = false, pendingFiles,
                                         onCopyMessage, onRegenerate, onFeedback, searchInfo, onDeleteMessage
                                     }: Props) {
    const containerRef = useRef<HTMLDivElement>(null)
    const listRef = useRef<HTMLDivElement>(null)
    const msgRefs = useRef<(HTMLDivElement | null)[]>([])
    const userScrolledUpRef = useRef(false)
    const initialScrollDoneRef = useRef(false)
    const prevMessageCountRef = useRef(0)
    const scrollRestoreRef = useRef({ scrollHeight: 0, scrollTop: 0 })
    const loadMoreInFlightRef = useRef(false)
    const [userNodes, setUserNodes] = useState<{ index: number; top: number; content: string }[]>([])
    const [hoveredNode, setHoveredNode] = useState<number | null>(null)
    const [showBackToBottom, setShowBackToBottom] = useState(false)
    const [thinkingTime, setThinkingTime] = useState(0)
    const [previewImage, setPreviewImage] = useState<PreviewImage | null>(null)
    const showToast = useToastStore(state => state.showToast)
    const viewportIndicatorRef = useRef<HTMLDivElement>(null)

    // 思考计时：isStreaming 开始时计时，结束时归零（显示"思考中... X.Xs"）
    useEffect(() => {
        if (!isStreaming) { setThinkingTime(0); return }
        const start = Date.now()
        const timer = setInterval(() => setThinkingTime((Date.now() - start) / 1000), 100)
        return () => clearInterval(timer)
    }, [isStreaming])

    const scrollToBottom = useCallback((smooth = false) => {
        const el = containerRef.current
        if (!el) return
        if (smooth) {
            el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
        } else {
            el.scrollTop = el.scrollHeight
        }
    }, [])

    // 更新右侧视口位置指示器（直接操作 DOM，避免滚动高频触发 re-render）
    const updateViewportIndicator = useCallback(() => {
        const el = containerRef.current
        const indicator = viewportIndicatorRef.current
        if (!el || !indicator) return
        if (el.scrollHeight > el.clientHeight) {
            const progress = (el.scrollTop / (el.scrollHeight - el.clientHeight)) * 100
            const height = (el.clientHeight / el.scrollHeight) * 100
            indicator.style.top = `calc(${progress}% - ${height / 2}%)`
            indicator.style.height = `${Math.max(height, 3)}%`
            indicator.style.opacity = '1'
        } else {
            indicator.style.opacity = '0'
        }
    }, [])

    // Handle scroll: detect top for loading more, track user scroll position
    const handleScroll = useCallback(() => {
        const el = containerRef.current
        if (!el) return
        const nearTop = el.scrollTop < 60
        const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 120
        userScrolledUpRef.current = !nearBottom
        setShowBackToBottom(!nearBottom && el.scrollHeight > el.clientHeight + 60)
        updateViewportIndicator()

        if (nearTop && hasMore && !isLoadingMore && onLoadMore?.current && !loadMoreInFlightRef.current) {
            const loadMore = onLoadMore.current
            loadMoreInFlightRef.current = true
            scrollRestoreRef.current = { scrollHeight: el.scrollHeight, scrollTop: el.scrollTop }
            void loadMore().finally(() => {
                requestAnimationFrame(() => {
                    if (el) {
                        // Keep the message that was at the top under the
                        // user's viewport after older rows are prepended.
                        el.scrollTop = scrollRestoreRef.current.scrollTop
                            + el.scrollHeight - scrollRestoreRef.current.scrollHeight
                    }
                    loadMoreInFlightRef.current = false
                })
            })
        }
    }, [hasMore, isLoadingMore, onLoadMore, updateViewportIndicator])

    // 计算每个用户提问节点在内容中的位置比例
    const measureNodes = useCallback(() => {
        const container = containerRef.current
        const list = listRef.current
        if (!container || !list) return
        const contentHeight = list.offsetHeight
        if (contentHeight <= 0) return
        const nodes: { index: number; top: number; content: string }[] = []
        messages.forEach((msg, idx) => {
            if (msg.role !== 'user') return
            const el = msgRefs.current[idx]
            if (!el) return
            const pos = el.offsetTop + el.offsetHeight / 2
            nodes.push({ index: idx, top: Math.min(Math.max((pos / contentHeight) * 100, 0), 100), content: msg.content })
        })
        setUserNodes(nodes)
    }, [messages])

    useEffect(() => {
        const raf = requestAnimationFrame(measureNodes)
        return () => cancelAnimationFrame(raf)
    }, [measureNodes])

    useEffect(() => {
        const container = containerRef.current
        if (!container) return
        const ro = new ResizeObserver(() => {
          measureNodes()
          updateViewportIndicator()
        })
        ro.observe(container)
        return () => ro.disconnect()
    }, [measureNodes, updateViewportIndicator])

    // 点击节点跳转到对应提问位置
    const jumpToMessage = useCallback((idx: number) => {
        const el = msgRefs.current[idx]
        const container = containerRef.current
        if (!el || !container) return
        const target = el.offsetTop - container.clientHeight * 0.35
        container.scrollTo({ top: Math.max(target, 0), behavior: 'smooth' })
        userScrolledUpRef.current = true
        setShowBackToBottom(true)
    }, [])

    // 一键返回底部
    const goToBottom = useCallback(() => {
        userScrolledUpRef.current = false
        setShowBackToBottom(false)
        scrollToBottom(true)
    }, [scrollToBottom])

    // 文件下载：使用 fetch + Blob 方式，避免 <a target="_blank"> 导航请求被 PWA Service Worker
    // 的 navigateFallback 拦截后回退到 index.html，导致点击文件卡片跳转到平台首页
    const handleFileDownload = useCallback(async (ossUrl: string, fileName: string) => {
        try {
            const response = await fetch(ossUrl)
            if (!response.ok) throw new Error('下载失败: HTTP ' + response.status)
            const blob = await response.blob()
            const blobUrl = URL.createObjectURL(blob)
            const a = document.createElement('a')
            a.href = blobUrl
            a.download = fileName
            document.body.appendChild(a)
            a.click()
            document.body.removeChild(a)
            // 延迟释放，避免某些浏览器在下载未启动前就 revoke 导致下载失败
            setTimeout(() => URL.revokeObjectURL(blobUrl), 1000)
        } catch (e) {
            console.error('文件下载失败:', e)
            showToast({
                title: t('下载失败', 'Download failed'),
                message: t('文件下载失败，请稍后重试', 'File download failed, please try again later'),
                type: 'error',
            })
        }
    }, [showToast, t])

    // Initial scroll to bottom when messages first load. This is keyed by the
    // conversation lifecycle rather than the first message ID, because older
    // history is prepended and must preserve the user's viewport.
    useEffect(() => {
        if (messages.length === 0 || initialScrollDoneRef.current) return
        const timer = setTimeout(() => {
            scrollToBottom()
            initialScrollDoneRef.current = true
        }, 180)
        return () => clearTimeout(timer)
    }, [messages.length])

    // Auto-scroll on new messages / streaming
    useEffect(() => {
        if (messages.length > prevMessageCountRef.current && !userScrolledUpRef.current) {
            requestAnimationFrame(() => scrollToBottom())
        }
        prevMessageCountRef.current = messages.length
    }, [messages])

    useEffect(() => {
        if (isStreaming && !userScrolledUpRef.current) {
            scrollToBottom()
        }
    }, [messages, isStreaming])

    // Attach scroll listener
    useEffect(() => {
        const el = containerRef.current
        if (!el) return
        el.addEventListener('scroll', handleScroll, { passive: true })
        return () => el.removeEventListener('scroll', handleScroll)
    }, [handleScroll])

    // Reset only on an actual conversation switch. Using messages[0].id here
    // makes loading older history look like a conversation switch and jumps
    // the viewport back to the bottom.
    useEffect(() => {
        initialScrollDoneRef.current = false
        // 修复：切换会话时重置为 0（而非 messages.length），让上面的 auto-scroll effect 能识别为"新增"并滚动
        // 同时立即滚动到底部，避免停留在旧会话的滚动偏移位置
        prevMessageCountRef.current = 0
        userScrolledUpRef.current = false
        if (messages.length > 0) {
            // 延迟滚动，等待新会话消息渲染完成
            requestAnimationFrame(() => scrollToBottom())
        }
    }, [conversationId])

    // Empty state
    if (messages.length === 0) {
        return (
            <div className="flex-1 flex items-center justify-center px-6">
                <div className="flex flex-col items-center gap-4" style={{ animation: 'fade-in-up 0.6s ease-out' }}>
                    <div className="w-16 h-16 rounded-2xl flex items-center justify-center"
                         style={{
                             background: 'linear-gradient(135deg, rgba(139,92,246,0.15), rgba(59,130,246,0.1))',
                             border: '1px solid rgba(139,92,246,0.12)',
                         }}
                    >
                        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="rgba(139,92,246,0.7)" strokeWidth="1.6">
                            <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z" />
                        </svg>
                    </div>
                    <p className="text-sm font-medium" style={{ color: 'var(--text-dim)' }}>
                        {t('开始和 AI 对话吧', 'Start a conversation with AI')}
                    </p>
                </div>
            </div>
        )
    }

    return (
        <div className="relative h-full overflow-hidden">
            <div ref={containerRef} className="h-full overflow-y-auto">
                <div ref={listRef} className="relative px-4 sm:px-6 lg:px-8 py-6 space-y-6 max-w-4xl xl:max-w-5xl 2xl:max-w-6xl mx-auto">
                    {hasMore && (
                        <div className="flex justify-center py-2">
                            {isLoadingMore ? (
                                <div className="flex items-center gap-2.5 px-4 py-2 rounded-full"
                                     style={{ background: 'rgba(139,92,246,0.06)', border: '1px solid rgba(139,92,246,0.1)' }}>
                                    <div className="typing-indicator">
                                        <span /><span /><span />
                                    </div>
                                    <span className="text-[11px] font-medium" style={{ color: 'var(--text-dim)' }}>
                    {t('加载历史消息...', 'Loading history...')}
                  </span>
                                </div>
                            ) : (
                                <span className="text-[11px] tracking-wide" style={{ color: 'var(--text-dim)', opacity: 0.5 }}>
                  {t('上翻加载更多', 'Scroll up for more')}
                </span>
                            )}
                        </div>
                    )}

                    {pendingFiles && pendingFiles.length > 0 && (
                        <div className="flex flex-wrap gap-2 mb-4">
                            {pendingFiles.map((file, i) => {
                                const ext = file.fileType || file.fileName.split('.').pop() || ''
                                const sizeStr = file.fileSize < 1024
                                    ? file.fileSize + ' B'
                                    : file.fileSize < 1024 * 1024
                                        ? (file.fileSize / 1024).toFixed(1) + ' KB'
                                        : (file.fileSize / (1024 * 1024)).toFixed(1) + ' MB'
                                return (
                                    <div
                                        key={i}
                                        className="inline-flex items-center gap-2 px-3 py-2 rounded-xl text-[11px]"
                                        style={{
                                            background: 'rgba(139,92,246,0.06)',
                                            border: '1px solid rgba(139,92,246,0.12)',
                                            color: 'var(--text-secondary)',
                                        }}
                                    >
                                        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                            <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" />
                                            <polyline points="14 2 14 8 20 8" />
                                        </svg>
                                        <span className="truncate max-w-[120px] font-medium">{file.fileName}</span>
                                        <span style={{ color: 'var(--text-dim)', fontSize: '10px' }}>
                      {sizeStr}
                    </span>
                                        <span
                                            className="px-1.5 py-0.5 rounded text-[9px] font-medium"
                                            style={{
                                                background: 'rgba(139,92,246,0.1)',
                                                color: 'var(--accent-purple)',
                                            }}
                                        >
                      {ext.toUpperCase()}
                    </span>
                                    </div>
                                )
                            })}
                        </div>
                    )}

                    {/* Message list */}
                    {messages.map((msg, idx) => {
                        const isUser = msg.role === 'user'
                        const isLast = idx === messages.length - 1
                        const isEmpty = !msg.content
                        const tokenInfo = parseTokenInfo(msg.tokens)
                        const pointsConsumed = msg.pointsConsumed ?? tokenInfo?.pointsConsumed ?? 0
                        // 搜索来源标识：流式时用实时 prop searchInfo，历史消息用持久化的 msg.searchInfo
                        const si = (isLast && isStreaming) ? searchInfo : msg.searchInfo

                        return (
                            <div
                                key={msg.id || idx}
                                ref={el => { msgRefs.current[idx] = el }}
                                className={`flex gap-2 sm:gap-3 ${isUser ? 'flex-row-reverse' : 'flex-row'}`}
                                style={{
                                    animation: `fade-in-up 0.35s ease-out both`,
                                    animationDelay: `${Math.min(idx * 0.03, 0.3)}s`,
                                }}
                            >
                                {/* Message Bubble */}
                                <div className={`group max-w-[85%] sm:max-w-[75%] lg:max-w-[80%] ${isUser ? 'items-end' : 'items-start'}`}>
                                    {/* File attachments — 用户提问消息上方的文件卡片（简化名+后缀标签，可点击下载） */}
                                    {isUser && msg.files && msg.files.length > 0 && (
                                        <div className="mb-2 flex flex-wrap gap-2 justify-end">
                                            {msg.files.map((file, fi) => {
                                                // 过期判断：优先用后端写入的 expireTime，回退到 uploadTime + 15天（兼容旧数据）
                                                const isExpired = file.expireTime
                                                    ? Date.now() > file.expireTime
                                                    : file.uploadTime
                                                        ? (Date.now() - file.uploadTime > 15 * 24 * 60 * 60 * 1000)
                                                        : false
                                                const fullExt = (file.fileType || file.fileName.split('.').pop() || '').toUpperCase()
                                                // 简化文件名：去掉后缀，主体截断到 8 字符，超长用省略号
                                                const baseName = file.fileName.replace(/\.[^.]+$/, '')
                                                const shortName = baseName.length > 8 ? baseName.slice(0, 8) + '…' : baseName
                                                const sizeStr = file.fileSize < 1024 ? file.fileSize + ' B'
                                                    : file.fileSize < 1024 * 1024 ? (file.fileSize / 1024).toFixed(1) + ' KB'
                                                    : (file.fileSize / (1024 * 1024)).toFixed(1) + ' MB'
                                                const imageSrc = isImageAttachment(file.fileName, file.fileType)
                                                    ? getImageSource(undefined, file.ossUrl)
                                                    : undefined

                                                if (imageSrc && !isExpired) {
                                                    return (
                                                        <button
                                                            key={fi}
                                                            type="button"
                                                            className="group relative block overflow-hidden rounded-2xl border p-0 text-left transition-transform duration-200 hover:scale-[1.02] focus:outline-none focus:ring-2 focus:ring-violet-400"
                                                            style={{
                                                                background: 'var(--bg-surface)',
                                                                borderColor: 'rgba(139,92,246,0.18)',
                                                            }}
                                                            onClick={() => setPreviewImage({ src: imageSrc, alt: file.fileName })}
                                                            aria-label={t(`预览图片 ${file.fileName}`, `Preview image ${file.fileName}`)}
                                                            title={t('点击放大图片', 'Click to enlarge image')}
                                                        >
                                                            <img
                                                                src={imageSrc}
                                                                alt={file.fileName}
                                                                className="block max-h-[220px] max-w-[280px] min-h-[76px] min-w-[76px] object-cover"
                                                            />
                                                            <span
                                                                className="absolute inset-x-0 bottom-0 truncate px-2 py-1.5 text-[10px]"
                                                                style={{ background: 'linear-gradient(transparent, rgba(3,7,18,0.78))', color: '#fff' }}
                                                            >
                                                                {shortName}.{fullExt.toLowerCase()}
                                                            </span>
                                                        </button>
                                                    )
                                                }

                                                return (
                                                    <div
                                                        key={fi}
                                                        className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-[11px] transition-all duration-200 hover:scale-[1.03] no-underline select-none"
                                                        style={{
                                                            background: isExpired ? 'rgba(244,63,94,0.06)' : 'rgba(139,92,246,0.06)',
                                                            border: `1px solid ${isExpired ? 'rgba(244,63,94,0.15)' : 'rgba(139,92,246,0.12)'}`,
                                                            color: 'var(--text-secondary)',
                                                            opacity: isExpired ? 0.5 : 1,
                                                            cursor: isExpired || !file.ossUrl ? 'not-allowed' : 'pointer',
                                                        }}
                                                        onClick={() => {
                                                            if (isExpired || !file.ossUrl) return
                                                            handleFileDownload(file.ossUrl, file.fileName)
                                                        }}
                                                        title={isExpired ? t('文件已过期', 'File expired') : (file.ossUrl ? t('点击下载', 'Click to download') : t('文件不可用', 'File unavailable'))}
                                                    >
                                                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="flex-shrink-0">
                                                            <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" />
                                                            <polyline points="14 2 14 8 20 8" />
                                                        </svg>
                                                        <span className="font-medium truncate max-w-[80px]">{shortName}</span>
                                                        <span className="px-1 py-0.5 rounded text-[9px] font-semibold flex-shrink-0" style={{ background: 'rgba(139,92,246,0.12)', color: 'var(--accent-purple)' }}>
                                                            {fullExt}
                                                        </span>
                                                        <span style={{ color: 'var(--text-dim)', fontSize: '9px' }} className="flex-shrink-0">{sizeStr}</span>
                                                        {!isExpired && file.ossUrl && (
                                                            <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="flex-shrink-0" style={{ color: 'var(--text-dim)' }}>
                                                                <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4" />
                                                                <polyline points="7 10 12 15 17 10" />
                                                                <line x1="12" y1="15" x2="12" y2="3" />
                                                            </svg>
                                                        )}
                                                    </div>
                                                )
                                            })}
                                        </div>
                                    )}

                                    {/* 搜索来源卡片：展示实际搜索状态、来源域名和可回溯链接 */}
                                    {!isUser && si && (
                                        <section
                                            className="mb-3 overflow-hidden rounded-2xl"
                                            aria-label={t('联网搜索来源', 'Web search sources')}
                                            style={{ background: 'var(--bg-surface)', border: '1px solid var(--border-color)' }}
                                        >
                                            <div className="flex items-center justify-between gap-3 px-3.5 py-2.5" style={{ borderBottom: si.searching || si.results.length === 0 ? 'none' : '1px solid var(--border-light)' }}>
                                                <div className="flex min-w-0 items-center gap-2.5">
                                                    {si.searching ? (
                                                        <div className="typing-indicator" aria-hidden="true"><span /><span /><span /></div>
                                                    ) : (
                                                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="var(--accent-blue)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                                                            <circle cx="11" cy="11" r="8" />
                                                            <line x1="21" y1="21" x2="16.65" y2="16.65" />
                                                        </svg>
                                                    )}
                                                    <div className="min-w-0">
                                                        <div className="truncate text-xs font-semibold" style={{ color: 'var(--text-primary)' }}>
                                                            {si.searching
                                                                ? t('正在查找相关资料', 'Finding relevant sources')
                                                                : si.results.length > 0
                                                                    ? t(`已找到 ${si.results.length} 条来源`, `${si.results.length} sources found`)
                                                                    : t('未找到可靠来源', 'No reliable sources found')}
                                                        </div>
                                                        <div className="mt-0.5 truncate text-[11px]" style={{ color: 'var(--text-muted)' }}>
                                                            {si.query}
                                                        </div>
                                                    </div>
                                                </div>
                                                {si.engine && <span className="flex-shrink-0 rounded-full px-2 py-0.5 text-[10px] font-medium" style={{ color: 'var(--accent-blue)', background: 'var(--accent-blue-soft)' }}>{si.engine}</span>}
                                            </div>
                                            {!si.searching && si.results.length === 0 && (
                                                <div className="px-3.5 pb-3 text-[11px]" style={{ color: 'var(--text-muted)' }}>
                                                    {t('本次回答不会引用联网资料，请结合回答内容自行核验。', 'This answer does not include web citations; verify important details independently.')}
                                                </div>
                                            )}
                                            {!si.searching && si.results.length > 0 && (
                                                <div className="space-y-1.5 px-2.5 pb-2.5 pt-1">
                                                    {si.results.slice(0, 5).map((r, i) => (
                                                        <a
                                                            key={`${r.url}-${i}`}
                                                            href={r.url}
                                                            target="_blank"
                                                            rel="noopener noreferrer"
                                                            className="group block rounded-xl px-3 py-2 transition-colors hover:bg-[var(--bg-hover)] focus-visible:bg-[var(--bg-hover)]"
                                                        >
                                                            <div className="flex items-start gap-2">
                                                                <span className="mt-0.5 flex h-4 w-4 flex-shrink-0 items-center justify-center rounded-full text-[10px] font-semibold" style={{ color: 'var(--accent-blue)', background: 'var(--accent-blue-soft)' }}>{i + 1}</span>
                                                                <div className="min-w-0 flex-1">
                                                                    <div className="flex items-center gap-1.5">
                                                                        <span className="truncate text-xs font-semibold" style={{ color: 'var(--text-primary)' }}>{r.title || r.url}</span>
                                                                        <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="var(--text-dim)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="flex-shrink-0 opacity-0 transition-opacity group-hover:opacity-100" aria-hidden="true">
                                                                            <path d="M14 3h7v7" /><path d="M10 14 21 3" /><path d="M21 14v5a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5" />
                                                                        </svg>
                                                                    </div>
                                                                    <div className="mt-0.5 truncate text-[11px]" style={{ color: 'var(--accent-blue)' }}>{r.source || resultDomain(r.url)}</div>
                                                                    {r.snippet && <p className="mt-1 line-clamp-2 text-[11px] leading-relaxed" style={{ color: 'var(--text-muted)' }}>{r.snippet}</p>}
                                                                </div>
                                                            </div>
                                                        </a>
                                                    ))}
                                                </div>
                                            )}
                                        </section>
                                    )}

                                    {/* Bubble */}
                                    <div
                                        className="px-4 py-3 text-sm leading-relaxed break-words"
                                        style={{
                                            background: isUser
                                                ? 'linear-gradient(135deg, rgba(139,92,246,0.18), rgba(99,102,241,0.12))'
                                                : 'var(--surface-glass)',
                                            color: 'var(--text-primary)',
                                            borderRadius: isUser
                                                ? '20px 20px 8px 20px'
                                                : '20px 20px 20px 8px',
                                            border: isUser
                                                ? '1px solid rgba(139,92,246,0.18)'
                                                : '1px solid var(--glass-border)',
                                            boxShadow: isUser
                                                ? '0 4px 16px rgba(139,92,246,0.06)'
                                                : '0 2px 12px rgba(0,0,0,0.04)',
                                            backdropFilter: 'blur(8px)',
                                            WebkitBackdropFilter: 'blur(8px)',
                                        }}
                                    >
                                        {/* 内容渲染：用户消息直接显示，AI 空消息显示思考动效，AI 有内容渲染 Markdown */}
                                        {isUser ? (
                                            <p className="whitespace-pre-wrap">{msg.content}</p>
                                        ) : isEmpty ? (
                                            // AI 消息为空：流式中显示三点浮动+计时，非流式不显示（历史空消息）
                                            isLast && isStreaming ? (
                                                <div className="flex items-center gap-2 py-0.5">
                                                    <div className="typing-indicator"><span /><span /><span /></div>
                                                    <span className="text-[12px] tabular-nums" style={{ color: 'var(--text-dim)' }}>
                                                        {fileProcessStatus
                                                            ? fileProcessStatus
                                                            : thinkingTime > 0
                                                                ? t(`思考中... ${thinkingTime.toFixed(1)}s`, `Thinking... ${thinkingTime.toFixed(1)}s`)
                                                                : t('AI 思考中...', 'Thinking...')}
                                                    </span>
                                                </div>
                                            ) : null
                                        ) : (
                                            <Suspense fallback={
                                                <div className="min-h-4 whitespace-pre-wrap break-words" aria-busy="true">
                                                    {msg.content}
                                                </div>
                                            }>
                                                <MarkdownRenderer content={msg.content} isStreaming={isLast && isStreaming} />
                                            </Suspense>
                                        )}

                                        {/* 计时：AI 开始输出文字后，三点动效消失，只保留计时 */}
                                        {isLast && isStreaming && !isEmpty && thinkingTime > 0 && (
                                            <div className="flex items-center gap-1 mt-1.5 text-[10px] tabular-nums" style={{ color: 'var(--text-dim)' }}>
                                                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                                    <circle cx="12" cy="12" r="10" />
                                                    <polyline points="12 6 12 12 16 14" />
                                                </svg>
                                                <span>{t('思考中', 'Thinking')} {thinkingTime.toFixed(1)}s</span>
                                            </div>
                                        )}
                                    </div>

                                    {/* 完成后的耗时与点数放在同一行，避免消息卡片元信息纵向拉长。 */}
                                    {!isUser && !(isLast && isStreaming) && ((msg.thinkingTime ?? 0) > 0 || pointsConsumed > 0) && (
                                        <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-[10px] tabular-nums" style={{ color: 'var(--text-dim)' }}>
                                            {(msg.thinkingTime ?? 0) > 0 && <span className="inline-flex items-center gap-1">
                                                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                                                    <circle cx="12" cy="12" r="10" />
                                                    <polyline points="12 6 12 12 16 14" />
                                                </svg>
                                                <span>{t('思考耗时', 'Thinking')} {msg.thinkingTime!.toFixed(1)}s</span>
                                            </span>}
                                            {pointsConsumed > 0 && <span className="inline-flex items-center gap-1">
                                                <PointsIcon size={10} color="#F59E0B" variant="solid" />
                                                <span>{t('本次消耗', 'Consumed')} {pointsConsumed.toFixed(2)} {t('点', 'pts')}</span>
                                            </span>}
                                        </div>
                                    )}

                                    {/* 消息操作栏：复制 / 重新生成 / 点赞点踩（hover 显示） */}
                                    <MessageActions
                                        content={msg.content}
                                        role={msg.role}
                                        isLast={isLast}
                                        isStreaming={isStreaming}
                                        feedback={msg.feedback}
                                        onCopy={() => onCopyMessage?.(msg)}
                                        onRegenerate={() => onRegenerate?.()}
                                        onFeedback={(type) => onFeedback?.(idx, type)}
                                        onDelete={() => onDeleteMessage?.(idx)}
                                        lang={lang}
                                        t={t}
                                    />
                                </div>
                            </div>
                        )
                    })}
                </div>
            </div>

            {/* 右侧导航轨道（参考 Google AI Studio 设计，仅桌面端显示） */}
            {userNodes.length > 1 && (
                <div
                    className="hidden lg:block absolute right-[9px] top-2 bottom-2 w-[3px] rounded-full z-[5] transition-colors duration-300"
                    style={{
                        background: hoveredNode !== null ? 'rgba(139,92,246,0.2)' : 'rgba(139,92,246,0.1)',
                    }}
                >
                    {/* 视口位置指示器 */}
                    <div
                        ref={viewportIndicatorRef}
                        className="absolute left-0 w-full rounded-full"
                        style={{
                            height: '8%',
                            top: '0%',
                            background: 'linear-gradient(180deg, rgba(139,92,246,0.45), rgba(139,92,246,0.25))',
                            opacity: '0',
                            transition: 'opacity 0.2s',
                        }}
                    />
                    {/* 用户提问节点 */}
                    {userNodes.map((n, i) => {
                        const active = hoveredNode === i
                        return (
                            <button
                                key={n.index}
                                onMouseEnter={() => setHoveredNode(i)}
                                onMouseLeave={() => setHoveredNode(null)}
                                onClick={() => jumpToMessage(n.index)}
                                className="absolute left-1/2 -translate-x-1/2 rounded-full cursor-pointer transition-all duration-200"
                                style={{
                                    top: `calc(${n.top}% - ${active ? 5 : 3}px)`,
                                    width: active ? 10 : 6,
                                    height: active ? 10 : 6,
                                    background: active ? 'var(--accent-purple)' : 'rgba(139,92,246,0.55)',
                                    boxShadow: active
                                        ? '0 0 0 3px rgba(139,92,246,0.18), 0 2px 10px rgba(139,92,246,0.5)'
                                        : '0 1px 4px rgba(0,0,0,0.2)',
                                }}
                            />
                        )
                    })}
                </div>
            )}

            {/* 节点悬停提示卡片（仅桌面端显示） */}
            {hoveredNode !== null && userNodes[hoveredNode] && (
                <div
                    className="hidden lg:block absolute right-[24px] z-[6] pointer-events-none -translate-y-1/2"
                    style={{ top: `${userNodes[hoveredNode].top}%` }}
                >
                    <div
                        className="relative px-3.5 py-2.5 rounded-xl text-[11px] leading-relaxed max-w-[260px]"
                        style={{
                            background: 'var(--bg-surface)',
                            border: '1px solid var(--border-color)',
                            color: 'var(--text-primary)',
                            boxShadow: '0 8px 32px rgba(0,0,0,0.2), 0 2px 8px rgba(0,0,0,0.1)',
                        }}
                    >
                        <div className="flex items-center gap-1.5 mb-1">
                            <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="#10B981" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                                <path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2" />
                                <circle cx="12" cy="7" r="4" />
                            </svg>
                            <span className="text-[10px] font-semibold tracking-wide" style={{ color: '#10B981' }}>
                                {t('我的提问', 'My question')}
                            </span>
                        </div>
                        <div className="line-clamp-3 break-all whitespace-pre-wrap" style={{ color: 'var(--text-secondary)' }}>
                            {userNodes[hoveredNode].content || t('(空)', '(empty)')}
                        </div>
                        {/* 指向节点的箭头 */}
                        <div
                            className="absolute -right-[5px] top-1/2 -translate-y-1/2 w-2.5 h-2.5 rotate-45"
                            style={{
                                background: 'var(--bg-surface)',
                                borderTop: '1px solid var(--border-color)',
                                borderRight: '1px solid var(--border-color)',
                            }}
                        />
                    </div>
                </div>
            )}

            {/* 返回底部按钮 */}
            {showBackToBottom && (
                <button
                    onClick={goToBottom}
                    className="absolute bottom-5 right-5 w-10 h-10 rounded-full flex items-center justify-center z-[5] transition-all duration-200 hover:scale-105 active:scale-95"
                    style={{
                        background: 'var(--bg-surface)',
                        border: '1px solid var(--border-color)',
                        color: 'var(--text-primary)',
                        backdropFilter: 'blur(8px)',
                        WebkitBackdropFilter: 'blur(8px)',
                        boxShadow: '0 4px 20px rgba(0,0,0,0.15)',
                    }}
                    title={t('返回底部', 'Back to bottom')}
                >
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <polyline points="6 9 12 15 18 9" />
                    </svg>
                </button>
            )}
            <ImagePreviewDialog image={previewImage} onClose={() => setPreviewImage(null)} t={t} />
        </div>
    )
}
