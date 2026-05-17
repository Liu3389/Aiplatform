import { useEffect, useRef, useCallback } from 'react'
import MarkdownRenderer from '@/components/chat/MarkdownRenderer'
import type { FrontendMessage, TokenStats } from '@/types'

interface Props {
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
}

function parseTokenInfo(tokensStr?: string | number) {
  if (!tokensStr) return null
  const str = String(tokensStr)
  const inputMatch = str.match(/Input:\s*(\d+)/)
  const outputMatch = str.match(/Output:\s*(\d+)/)
  const costMatch = str.match(/Cost:\s*([\d.]+)/)
  return {
    inputTokens: inputMatch ? parseInt(inputMatch[1]) : 0,
    outputTokens: outputMatch ? parseInt(outputMatch[1]) : 0,
    cost: costMatch ? parseFloat(costMatch[1]) : 0,
  }
}

export default function ChatMessages({
  messages, isStreaming, fileProcessStatus, lang, t,
  onLoadMore, isLoadingMore = false, hasMore = true, pendingFiles
}: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const userScrolledUpRef = useRef(false)
  const initialScrollDoneRef = useRef(false)
  const prevMessageCountRef = useRef(0)
  const scrollRestoreRef = useRef(0)

  const scrollToBottom = useCallback((smooth = false) => {
    const el = containerRef.current
    if (!el) return
    if (smooth) {
      el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
    } else {
      el.scrollTop = el.scrollHeight
    }
  }, [])

  // Handle scroll: detect top for loading more, track user scroll position
  const handleScroll = useCallback(() => {
    const el = containerRef.current
    if (!el) return
    const nearTop = el.scrollTop < 60
    const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 120
    userScrolledUpRef.current = !nearBottom

    if (nearTop && hasMore && !isLoadingMore && onLoadMore?.current) {
      scrollRestoreRef.current = el.scrollHeight
      onLoadMore.current().then(() => {
        requestAnimationFrame(() => {
          if (el) el.scrollTop = el.scrollHeight - scrollRestoreRef.current
        })
      })
    }
  }, [hasMore, isLoadingMore, onLoadMore])

  // Initial scroll to bottom when messages first load
  useEffect(() => {
    if (messages.length === 0 || initialScrollDoneRef.current) return
    const timer = setTimeout(() => {
      scrollToBottom()
      initialScrollDoneRef.current = true
    }, 180)
    return () => clearTimeout(timer)
  }, [messages.length > 0])

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

  // Reset on conversation switch (first message id changes)
  useEffect(() => {
    initialScrollDoneRef.current = false
    prevMessageCountRef.current = messages.length
  }, [messages.length > 0 && messages[0]?.id])

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
    <div ref={containerRef} className="flex-1 overflow-y-auto">
      <div className="px-4 sm:px-6 lg:px-8 py-6 space-y-6 max-w-4xl xl:max-w-5xl 2xl:max-w-6xl mx-auto">
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
          const isFirstOfGroup = idx === 0 || messages[idx - 1].role !== msg.role

          return (
            <div
              key={msg.id || idx}
              className={`flex gap-3 ${isUser ? 'flex-row-reverse' : 'flex-row'}`}
              style={{
                animation: `fade-in-up 0.35s ease-out both`,
                animationDelay: `${Math.min(idx * 0.03, 0.3)}s`,
              }}
            >
              {/* Avatar */}
              {isFirstOfGroup && (
                <div
                  className="flex-shrink-0 w-9 h-9 rounded-xl flex items-center justify-center"
                  style={{
                    background: isUser
                      ? 'linear-gradient(135deg, #10B981, #059669)'
                      : 'linear-gradient(135deg, rgba(139,92,246,0.85), rgba(99,102,241,0.8))',
                    border: '1px solid rgba(255,255,255,0.1)',
                    boxShadow: isUser
                      ? '0 2px 8px rgba(16,185,129,0.2)'
                      : '0 2px 8px rgba(139,92,246,0.2)',
                  }}
                >
                  {isUser ? (
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="1.8">
                      <path d="M12 4a3 3 0 100 6 3 3 0 000-6z" />
                      <path d="M5.5 20.5a6.5 6.5 0 0113 0" />
                    </svg>
                  ) : (
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="1.8">
                      <path d="M12 3c-4.971 0-9 4.029-9 9s4.029 9 9 9 9-4.029 9-9-4.029-9-9-9z" />
                      <path d="M8 12.5c0-2.21 1.79-4 4-4s4 1.79 4 4" />
                      <path d="M12 7.5v3" strokeLinecap="round" />
                      <path d="M8 17.5c0-2 2-3 4-3s4 1 4 3" />
                    </svg>
                  )}
                </div>
              )}
              {!isFirstOfGroup && <div className="w-9 flex-shrink-0" />}

              {/* Message Bubble */}
              <div className={`max-w-[85%] sm:max-w-[75%] lg:max-w-[80%] ${isUser ? 'items-end' : 'items-start'}`}>
                {/* File attachments */}
                {isUser && msg.files && msg.files.length > 0 && (
                  <div className="mb-2 flex flex-wrap gap-2">
                    {msg.files.map((file, fi) => {
                      const isExpired = file.uploadTime && (Date.now() - file.uploadTime > 15 * 24 * 60 * 60 * 1000)
                      const daysLeft = file.uploadTime
                        ? Math.max(0, Math.ceil((15 * 24 * 60 * 60 * 1000 - (Date.now() - file.uploadTime)) / (24 * 60 * 60 * 1000)))
                        : 0
                      return (
                        <div
                          key={fi}
                          className="inline-flex items-center gap-2 px-3 py-2 rounded-xl text-[11px] cursor-pointer transition-all duration-200 hover:scale-[1.02]"
                          style={{
                            background: isExpired ? 'rgba(244,63,94,0.06)' : 'rgba(139,92,246,0.06)',
                            border: `1px solid ${isExpired ? 'rgba(244,63,94,0.15)' : 'rgba(139,92,246,0.12)'}`,
                            color: 'var(--text-secondary)',
                            opacity: isExpired ? 0.5 : 1,
                          }}
                          onClick={() => { if (!isExpired && file.ossUrl) window.open(file.ossUrl, '_blank') }}
                          title={isExpired ? t('文件已过期', 'File expired') : `${t('剩余', 'Remaining')} ${daysLeft}${t('天', 'd')}`}
                        >
                          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                            <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" />
                            <polyline points="14 2 14 8 20 8" />
                          </svg>
                          <span className="truncate max-w-[120px] font-medium">{file.fileName}</span>
                          <span style={{ color: 'var(--text-dim)', fontSize: '10px' }}>
                            {(file.fileSize / 1024).toFixed(0)}KB
                          </span>
                        </div>
                      )
                    })}
                  </div>
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
                  {/* Processing indicator (before response) */}
                  {isLast && isStreaming && isEmpty && fileProcessStatus ? (
                    <div className="flex items-center gap-3 py-0.5">
                      <div className="typing-indicator"><span /><span /><span /></div>
                      <span className="text-[12px]" style={{ color: 'var(--text-dim)' }}>
                        {fileProcessStatus}
                      </span>
                    </div>
                  ) : isUser ? (
                    <p className="whitespace-pre-wrap">{msg.content}</p>
                  ) : (
                    <MarkdownRenderer content={msg.content} isStreaming={isLast && isStreaming} />
                  )}

                  {/* Streaming cursor & indicator */}
                  {isLast && isStreaming && !isEmpty && !fileProcessStatus && (
                    <div className="flex items-center gap-2 mt-2">
                      <div className="typing-indicator"><span /><span /><span /></div>
                      <span className="text-[11px] opacity-70" style={{ color: 'var(--text-dim)' }}>
                        {t('AI 思考中...', 'Thinking...')}
                      </span>
                    </div>
                  )}
                </div>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}