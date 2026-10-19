import { useState } from 'react'

interface Props {
    content: string
    role: 'user' | 'assistant' | 'system'
    isLast: boolean
    isStreaming: boolean
    feedback?: 'like' | 'dislike' | null
    onCopy?: (content: string) => void
    onRegenerate?: () => void
    onFeedback?: (type: 'like' | 'dislike') => void
    onDelete?: () => void
    lang: string
    t: (zh: string, en: string) => string
}

/**
 * 消息操作栏组件（参考 ChatGPT / Claude 的设计）
 * - hover 消息气泡时显示
 * - 复制：所有消息可用，复制后显示"已复制"反馈
 * - 重新生成 / 点赞 / 点踩：仅 AI 消息可用
 */
export default function MessageActions({
    content, role, isLast, isStreaming, feedback,
    onCopy, onRegenerate, onFeedback, onDelete, t,
}: Props) {
    const [copied, setCopied] = useState(false)
    const isAssistant = role === 'assistant'

    const handleCopy = async () => {
        try {
            await navigator.clipboard.writeText(content)
        } catch {
            // 降级方案：兼容非 HTTPS 环境
            const ta = document.createElement('textarea')
            ta.value = content
            ta.style.position = 'fixed'
            ta.style.opacity = '0'
            document.body.appendChild(ta)
            ta.select()
            try { document.execCommand('copy') } catch {}
            document.body.removeChild(ta)
        }
        setCopied(true)
        onCopy?.(content)
        setTimeout(() => setCopied(false), 1500)
    }

    // 流式输出中的最后一条 AI 消息不显示操作栏（等输出完再显示）
    if (isLast && isStreaming && isAssistant) return null
    // 空内容不显示
    if (!content) return null

    const btnClass = 'flex items-center justify-center w-7 h-7 rounded-lg transition-all duration-150 hover:bg-black/5 dark:hover:bg-white/10'
    const btnStyle = (active: boolean) => ({
        color: active ? 'var(--accent-purple)' : 'var(--text-dim)',
    })

    return (
        <div
            className="flex items-center gap-0.5 mt-1.5 opacity-100 lg:opacity-0 lg:group-hover:opacity-100 lg:focus-within:opacity-100 transition-opacity duration-150"
            style={{ minHeight: '28px' }}
        >
            {/* 复制 */}
            <button
                onClick={handleCopy}
                className={btnClass}
                style={btnStyle(copied)}
                title={copied ? t('已复制', 'Copied') : t('复制', 'Copy')}
                aria-label={t('复制', 'Copy')}
            >
                {copied ? (
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                        <polyline points="20 6 9 17 4 12" />
                    </svg>
                ) : (
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                        <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
                        <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1" />
                    </svg>
                )}
            </button>

            {/* 仅 AI 消息显示：重新生成 + 点赞 + 点踩 */}
            {isAssistant && (
                <>
                    <button
                        onClick={() => onRegenerate?.()}
                        className={`${btnClass} ${isStreaming ? 'cursor-not-allowed opacity-40' : ''}`}
                        style={btnStyle(false)}
                        disabled={isStreaming}
                        title={t('重新生成', 'Regenerate')}
                        aria-label={t('重新生成', 'Regenerate')}
                    >
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                            <polyline points="23 4 23 10 17 10" />
                            <path d="M20.49 15a9 9 0 11-2.12-9.36L23 10" />
                        </svg>
                    </button>

                    <button
                        onClick={() => onFeedback?.('like')}
                        className={btnClass}
                        style={btnStyle(feedback === 'like')}
                        title={t('赞', 'Good response')}
                        aria-label={t('赞', 'Good response')}
                    >
                        <svg width="14" height="14" viewBox="0 0 24 24" fill={feedback === 'like' ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                            <path d="M14 9V5a3 3 0 00-3-3l-4 9v11h11.28a2 2 0 002-1.7l1.38-9a2 2 0 00-2-2.3zM7 22H4a2 2 0 01-2-2v-7a2 2 0 012-2h3" />
                        </svg>
                    </button>

                    <button
                        onClick={() => onFeedback?.('dislike')}
                        className={btnClass}
                        style={btnStyle(feedback === 'dislike')}
                        title={t('踩', 'Bad response')}
                        aria-label={t('踩', 'Bad response')}
                    >
                        <svg width="14" height="14" viewBox="0 0 24 24" fill={feedback === 'dislike' ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                            <path d="M10 15v4a3 3 0 003 3l4-9V2H5.72a2 2 0 00-2 1.7l-1.38 9a2 2 0 002 2.3zM17 2h3a2 2 0 012 2v7a2 2 0 01-2 2h-3" />
                        </svg>
                    </button>
                </>
            )}

            {/* 删除消息（所有消息可用，点击需确认，防止误删） */}
            <button
                onClick={() => { if (window.confirm(t('确定删除这条消息？', 'Delete this message?'))) onDelete?.() }}
                className={btnClass}
                style={{ color: 'var(--text-dim)' }}
                title={t('删除', 'Delete')}
                aria-label={t('删除', 'Delete')}
            >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                    <polyline points="3 6 5 6 21 6" />
                    <path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2" />
                </svg>
            </button>
        </div>
    )
}
