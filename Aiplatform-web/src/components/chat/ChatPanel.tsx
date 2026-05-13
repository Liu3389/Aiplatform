import { useState, useRef, useEffect, useCallback, useId } from 'react'
import { chatApi, ensureApiKey } from '@/services/api'
import { motion } from 'framer-motion'

interface ChatMsg { role: 'user' | 'assistant'; content: string }

const STORAGE_KEY = 'lingdian_chat_history'

function loadHistory(): ChatMsg[] {
  try { const d = localStorage.getItem(STORAGE_KEY); return d ? JSON.parse(d) : [] }
  catch { return [] }
}

function saveHistory(msgs: ChatMsg[]) {
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(msgs.slice(-200))) }
  catch {}
}

export default function ChatPanel() {
  const gradientId = useId().replace(/:/g, '')
  const [messages, setMessages] = useState<ChatMsg[]>(() => loadHistory())
  const [input, setInput] = useState('')
  const [isStreaming, setIsStreaming] = useState(false)
  const [error, setError] = useState('')
  const abortRef = useRef<AbortController | null>(null)
  const apiKeyRef = useRef<string | null>(null)
  const bottomRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => { ensureApiKey().then(k => { if (k) apiKeyRef.current = k }) }, [])
  useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: 'smooth' }) }, [messages])

  const updateMessages = useCallback((newMsgs: ChatMsg[]) => { setMessages(newMsgs); saveHistory(newMsgs) }, [])
  const handleClear = useCallback(() => { updateMessages([]); setError('') }, [updateMessages])

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') { e.preventDefault(); inputRef.current?.focus() }
      if ((e.metaKey || e.ctrlKey) && e.key === 'l') { e.preventDefault(); handleClear() }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [handleClear])

  const handleSend = useCallback(async () => {
    const content = input.trim()
    if (!content || isStreaming) return
    if (!apiKeyRef.current) { setError('请先创建一个 API Key（侧边栏→令牌管理）'); return }

    setInput(''); setError('')
    const newMessages: ChatMsg[] = [...messages, { role: 'user' as const, content }]
    updateMessages(newMessages)
    setIsStreaming(true)

    let fullContent = ''
    abortRef.current = chatApi.stream(
      apiKeyRef.current,
      { model: 'deepseek-v4-pro', messages: newMessages, temperature: 0.7 },
      (chunk: string) => {
        fullContent += chunk
        updateMessages([...newMessages, { role: 'assistant', content: fullContent }])
      },
      () => setIsStreaming(false),
      (err: string) => { setError(err); setIsStreaming(false) }
    )
  }, [input, isStreaming, messages, updateMessages])

  const handleStop = useCallback(() => { abortRef.current?.abort(); setIsStreaming(false) }, [])

  return (
    <div className="flex flex-col h-full" style={{ background: 'rgba(7,11,23,0.3)' }}>
      <div className="h-[52px] flex-shrink-0 flex items-center justify-between px-5 border-b border-white/[0.04]">
        <div className="flex items-center gap-2">
          <div className="w-2 h-2 rounded-full bg-accent-emerald animate-glow-pulse" />
          <span className="text-sm font-semibold text-white">AI Chat Studio</span>
        </div>
        <div className="flex items-center gap-2">
          {messages.length > 0 && (
            <button onClick={handleClear} className="text-[10px] text-neutral-600 hover:text-accent-rose transition-colors" title="清空对话 (⌘L)">清空</button>
          )}
          <span className="text-[10px] text-neutral-500">DeepSeek V4 Pro</span>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-4 space-y-4">
        {messages.length === 0 && (
          <div className="flex flex-col items-center justify-center h-full text-center px-4">
            <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-brand-purple/20 to-brand-blue/10 border border-brand-purple/20 flex items-center justify-center mb-4">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke={`url(#${gradientId})`} strokeWidth="1.5">
                <defs><linearGradient id={gradientId} x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" stopColor="#8B5CF6"/><stop offset="100%" stopColor="#3B82F6"/></linearGradient></defs>
                <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
              </svg>
            </div>
            <p className="text-sm text-neutral-400 mb-1">AI Chat Studio</p>
            <p className="text-xs text-neutral-600 mb-6">流式对话 · 自动保存 · 快捷键支持</p>
            <div className="flex flex-wrap justify-center gap-x-4 gap-y-2 text-[10px] text-neutral-600 mb-1">
              <span><kbd className="px-1.5 py-0.5 bg-white/[0.04] border border-white/[0.06] rounded text-brand-purple">Enter</kbd> 发送</span>
              <span><kbd className="px-1.5 py-0.5 bg-white/[0.04] border border-white/[0.06] rounded text-brand-purple">Shift+Enter</kbd> 换行</span>
              <span><kbd className="px-1.5 py-0.5 bg-white/[0.04] border border-white/[0.06] rounded text-brand-purple">⌘K</kbd> 聚焦输入</span>
              <span><kbd className="px-1.5 py-0.5 bg-white/[0.04] border border-white/[0.06] rounded text-brand-purple">⌘L</kbd> 清空对话</span>
            </div>
          </div>
        )}

        {messages.map((msg, i) => (
          <motion.div
            key={i}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.2 }}
            className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
          >
            <div className={`max-w-[90%] px-4 py-2.5 rounded-2xl text-sm font-sans ${
              msg.role === 'user'
                ? 'bg-gradient-to-r from-brand-purple to-brand-purple-dark text-white rounded-br-md'
                : 'bg-white/[0.04] border border-white/[0.05] text-neutral-200 rounded-bl-md'
            }`}>
              <p className="whitespace-pre-wrap leading-relaxed break-words">{msg.content}</p>
              {isStreaming && i === messages.length - 1 && msg.role === 'assistant' && (
                <span className="inline-block w-0.5 h-4 bg-brand-purple ml-0.5 animate-pulse align-middle" />
              )}
            </div>
          </motion.div>
        ))}

        {error && (
          <div className="text-xs text-accent-rose bg-accent-rose/5 border border-accent-rose/10 rounded-lg px-3 py-2 flex items-center justify-between">
            <span>{error}</span>
            <button onClick={()=>setError('')} className="ml-2 text-neutral-500 hover:text-white">✕</button>
          </div>
        )}

        <div ref={bottomRef} />
      </div>

      <div className="flex-shrink-0 p-3 border-t border-white/[0.04]">
        <div className="flex items-center gap-2">
          <input
            ref={inputRef}
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend() } }}
            placeholder="输入消息... (⌘K 聚焦 · Enter 发送)"
            disabled={isStreaming}
            className="flex-1 px-4 py-2.5 bg-white/[0.03] border border-white/[0.05] rounded-2xl text-sm text-white placeholder-neutral-500 focus:outline-none focus:border-brand-purple/30 transition-all disabled:opacity-40 font-sans"
          />
          {isStreaming ? (
            <button onClick={handleStop} className="flex-shrink-0 w-10 h-10 flex items-center justify-center rounded-xl bg-accent-rose/10 text-accent-rose hover:bg-accent-rose/20 transition-colors">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="6" width="12" height="12" rx="1"/></svg>
            </button>
          ) : (
            <button
              onClick={handleSend}
              disabled={!input.trim()}
              className="btn-neon flex-shrink-0 w-10 h-10 flex items-center justify-center disabled:opacity-20 disabled:cursor-not-allowed"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
