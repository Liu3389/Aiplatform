import { useState, useRef, useEffect, useCallback } from 'react'
import { chatApi, ensureApiKey, userApi, userApiKeyApi, type UserApiKeyItem } from '@/services/api'
import MarkdownRenderer from '@/components/chat/MarkdownRenderer'
import { motion } from 'framer-motion'
import { useI18nStore } from '@/stores/i18nStore'

export interface ChatConversation {
  id: string
  title: string
  messages: Message[]
  createdAt: number
  updatedAt: number
  favorited?: boolean
}

export interface Message { role: 'user' | 'assistant' | 'system'; content: string }

interface CustomModel {
  model: string
  apiKey: string
  baseUrl: string
  format: 'openai' | 'anthropic'
}

const WELCOME_SUGGESTIONS = [
  '用一句话介绍凌点 AI 平台',
  '写一个 Python 快速排序',
  '帮我分析 AI 行业发展趋势',
  '解释 RESTful API 的原理',
]

const STORAGE_KEY_CONVERSATIONS = 'lingdian_chat_conversations'
const STORAGE_KEY_CURRENT = 'lingdian_chat_current'

function loadConversations(): ChatConversation[] {
  try {
    const d = localStorage.getItem(STORAGE_KEY_CONVERSATIONS)
    return d ? JSON.parse(d) : []
  } catch { return [] }
}

function saveConversations(convs: ChatConversation[]) {
  try { localStorage.setItem(STORAGE_KEY_CONVERSATIONS, JSON.stringify(convs.slice(-100))) } catch {}
}

function generateId() { return Date.now().toString(36) + Math.random().toString(36).substring(2, 11) }

function generateTitle(content: string) {
  return content.length > 30 ? content.slice(0, 30) + '...' : content
}

export default function ChatPage() {
  const { lang } = useI18nStore()

  const [freeModel, setFreeModel] = useState(() => localStorage.getItem('ld_free_model') || 'deepseek-v4-flash')
  const [temperature] = useState(0.7)
  const [mode, setMode] = useState<'free' | 'custom' | 'paid'>('free')
  const [selectedKeyId, setSelectedKeyId] = useState<number | null>(null)
  const [storedKeys, setStoredKeys] = useState<UserApiKeyItem[]>([])
  const [freeQuota, setFreeQuota] = useState<{ usedCalls: number; maxCalls: number; usedTokens: number; maxTokens: number } | null>(null)

  const [conversations, setConversations] = useState<ChatConversation[]>(() => loadConversations())
  const [currentConvId, setCurrentConvId] = useState<string | null>(() => localStorage.getItem(STORAGE_KEY_CURRENT))
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [isStreaming, setIsStreaming] = useState(false)
  const [error, setError] = useState('')
  const [tokenStats, setTokenStats] = useState<{ inputTokens: number; outputTokens: number; cost: number } | null>(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [showSidebar, setShowSidebar] = useState(true)
  const [editingTitle, setEditingTitle] = useState<string | null>(null)
  const [titleInput, setTitleInput] = useState('')

  const abortRef = useRef<AbortController | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const apiKeyRef = useRef<string | null>(null)
  const apiKeyInitRef = useRef(false)

  const currentConv = conversations.find(c => c.id === currentConvId)

  const persistConversations = useCallback((convs: ChatConversation[]) => {
    setConversations(convs)
    saveConversations(convs)
  }, [])

  const createNewConversation = useCallback(() => {
    const id = generateId()
    const conv: ChatConversation = { id, title: '新对话', messages: [], createdAt: Date.now(), updatedAt: Date.now() }
    persistConversations([conv, ...conversations])
    setCurrentConvId(id)
    setMessages([])
    setTokenStats(null)
    localStorage.setItem(STORAGE_KEY_CURRENT, id)
  }, [conversations, persistConversations])

  const selectConversation = useCallback((id: string) => {
    const conv = conversations.find(c => c.id === id)
    if (conv) {
      setCurrentConvId(id)
      setMessages(conv.messages)
      setTokenStats(null)
      localStorage.setItem(STORAGE_KEY_CURRENT, id)
    }
  }, [conversations])

  const updateCurrentConversation = useCallback((msgs: Message[]) => {
    if (!currentConvId) return
    const updated = conversations.map(c =>
      c.id === currentConvId
        ? { ...c, messages: msgs, updatedAt: Date.now(), title: msgs.length > 0 && c.title === '新对话' && msgs[0].role === 'user' ? generateTitle(msgs[0].content) : c.title }
        : c
    )
    persistConversations(updated)
  }, [conversations, currentConvId, persistConversations])

  const deleteConversation = useCallback((id: string) => {
    const filtered = conversations.filter(c => c.id !== id)
    persistConversations(filtered)
    if (currentConvId === id) {
      if (filtered.length > 0) selectConversation(filtered[0].id)
      else createNewConversation()
    }
  }, [conversations, currentConvId, persistConversations, selectConversation, createNewConversation])

  const renameConversation = useCallback((id: string, title: string) => {
    const updated = conversations.map(c => c.id === id ? { ...c, title } : c)
    persistConversations(updated)
    setEditingTitle(null)
  }, [conversations, persistConversations])

  const exportConversation = useCallback((format: 'markdown' | 'json' | 'txt') => {
    if (!currentConv) return
    let content = ''
    if (format === 'markdown') {
      content = `# ${currentConv.title}\n\n`
      currentConv.messages.forEach(m => {
        content += `**${m.role === 'user' ? '用户' : 'AI'}**: ${m.content}\n\n`
      })
    } else if (format === 'json') {
      content = JSON.stringify(currentConv, null, 2)
    } else {
      currentConv.messages.forEach(m => { content += `${m.role}: ${m.content}\n\n` })
    }
    const blob = new Blob([content], { type: 'text/plain' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${currentConv.title}.${format}`
    a.click()
    URL.revokeObjectURL(url)
  }, [currentConv])

  const shareConversation = useCallback(() => {
    if (!currentConv) return
    const shareData = { title: currentConv.title, messages: currentConv.messages }
    const encoded = btoa(encodeURIComponent(JSON.stringify(shareData)))
    navigator.clipboard.writeText(`${window.location.origin}/chat?share=${encoded}`)
    alert(lang === 'zh' ? '分享链接已复制到剪贴板' : 'Share link copied to clipboard')
  }, [currentConv, lang])

  const toggleFavorite = useCallback((id: string) => {
    const updated = conversations.map(c =>
      c.id === id ? { ...c, favorited: !c.favorited } : c
    )
    persistConversations(updated)
  }, [conversations, persistConversations])

  const cloudSync = useCallback(async () => {
    try {
      await userApi.syncConversations(conversations)
      alert(lang === 'zh' ? '同步成功' : 'Sync successful')
    } catch {
      alert(lang === 'zh' ? '同步失败' : 'Sync failed')
    }
  }, [conversations, lang])

  const cloudFetch = useCallback(async () => {
    try {
      const cloud = await userApi.fetchConversations()
      if (cloud && cloud.length > 0) {
        const merged = [...cloud, ...conversations.filter(
          c => !cloud.find((cl: any) => cl.id === c.id)
        )]
        persistConversations(merged)
        alert(lang === 'zh' ? '已同步云端数据' : 'Synced from cloud')
      }
    } catch {
      alert(lang === 'zh' ? '获取云端数据失败' : 'Failed to fetch')
    }
  }, [conversations, persistConversations, lang])

  const filteredConversations = conversations.filter(c =>
    c.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
    c.messages.some(m => m.content.toLowerCase().includes(searchQuery.toLowerCase()))
  )

  useEffect(() => {
    if (!apiKeyInitRef.current) {
      apiKeyInitRef.current = true
      ensureApiKey().then(key => { if (key) apiKeyRef.current = key })
    }
  }, [])

  useEffect(() => {
    if (currentConvId && conversations.length > 0) {
      const conv = conversations.find(c => c.id === currentConvId)
      if (conv) setMessages(conv.messages)
      else if (conversations.length > 0) selectConversation(conversations[0].id)
    } else if (conversations.length === 0) {
      createNewConversation()
    }
  }, [currentConvId, conversations])

  useEffect(() => {
    userApiKeyApi.list().then(k => setStoredKeys(k || [])).catch(() => {})
    userApi.getFreeQuota().then(q => setFreeQuota(q)).catch(() => {})
  }, [])

  const handleSend = async (overrideInput?: string) => {
    const content = (overrideInput || input).trim()
    if (!content || isStreaming) return

    if (!currentConvId) createNewConversation()

    setError('')
    setInput('')
    setTokenStats(null)

    const newMessages: Message[] = [...messages, { role: 'user' as const, content }]
    setMessages(newMessages)
    updateCurrentConversation(newMessages)
    setIsStreaming(true)

    const model = mode === 'free' ? freeModel : (mode === 'paid' ? freeModel : (storedKeys.find(k => k.id === selectedKeyId)?.displayName || 'custom'))
    const extra: any = { mode }
    if (mode === 'custom' && selectedKeyId) {
      extra.userApiKeyId = selectedKeyId
    }

    const apiKey = apiKeyRef.current
    if (!apiKey) {
      setError('请先创建并启用一个API Key')
      setIsStreaming(false)
      return
    }

    let fullContent = ''

    abortRef.current = chatApi.stream(
      apiKey,
      {
        model,
        messages: [
          { role: 'system', content: 'You are a helpful AI assistant. Always respond in well-formatted Markdown. Use headers, lists, code blocks with language tags, tables, and bold text where appropriate. Structure your answers to be visually clear and scannable.' },
          ...newMessages,
        ],
        temperature,
        ...extra
      },
      (chunk: string) => {
        fullContent += chunk
        setMessages([...newMessages, { role: 'assistant' as const, content: fullContent }])
      },
      (result: { inputTokens: number; outputTokens: number; cost: number }) => {
        setIsStreaming(false)
        setTokenStats(result)
        setFreeQuota(prev => prev ? { ...prev } : null)
        userApi.getFreeQuota().then(q => setFreeQuota(q)).catch(() => {})
        const finalMessages: Message[] = [...newMessages, { role: 'assistant' as const, content: fullContent }]
        setMessages(finalMessages)
        updateCurrentConversation(finalMessages)
      },
      (err: string) => {
        setIsStreaming(false)
        setError(err)
      }
    )
  }

  const handleStop = () => {
    abortRef.current?.abort()
    setIsStreaming(false)
  }

  const handleClear = () => {
    abortRef.current?.abort()
    setMessages([])
    setTokenStats(null)
    setIsStreaming(false)
    if (currentConvId) {
      const updated = conversations.map(c =>
        c.id === currentConvId ? { ...c, messages: [], updatedAt: Date.now() } : c
      )
      persistConversations(updated)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  useEffect(() => {
    return () => abortRef.current?.abort()
  }, [])

  useEffect(() => {
    if (!isStreaming) setTimeout(() => inputRef.current?.focus(), 50)
  }, [isStreaming])

  useEffect(() => {
    containerRef.current?.scrollTo({ top: containerRef.current.scrollHeight, behavior: 'smooth' })
  }, [messages])

  return (
    <div className="flex h-full" style={{ background: 'var(--bg-primary)' }}>
      {showSidebar && (
        <motion.div
          initial={{ width: 0, opacity: 0 }}
          animate={{ width: 280, opacity: 1 }}
          exit={{ width: 0, opacity: 0 }}
          className="h-full border-r flex flex-col"
          style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-color)' }}
        >
          <div className="p-4 border-b" style={{ borderColor: 'var(--border-color)' }}>
            <div className="flex items-center justify-between mb-3">
              <h2 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>对话历史</h2>
              <button
                onClick={createNewConversation}
                className="w-7 h-7 rounded-lg flex items-center justify-center transition-colors"
                style={{ background: 'var(--accent-purple)', color: '#fff' }}
                title={lang === 'zh' ? '新建对话' : 'New Chat'}
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
              </button>
            </div>
            <input
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              placeholder={lang === 'zh' ? '搜索对话...' : 'Search chats...'}
              className="w-full px-3 py-2 text-xs rounded-lg border outline-none transition-all"
              style={{ background: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
            />
          </div>

          <div className="flex-1 overflow-y-auto p-2 space-y-1">
            {filteredConversations.map(conv => (
              <div
                key={conv.id}
                className={`group relative p-3 rounded-xl cursor-pointer transition-all ${
                  conv.id === currentConvId ? 'border' : ''
                }`}
                style={{
                  background: conv.id === currentConvId ? 'var(--accent-purple)' + '20' : 'transparent',
                  borderColor: conv.id === currentConvId ? 'var(--accent-purple)' + '40' : 'transparent',
                }}
                onClick={() => selectConversation(conv.id)}
              >
                {editingTitle === conv.id ? (
                  <input
                    value={titleInput}
                    onChange={e => setTitleInput(e.target.value)}
                    onBlur={() => renameConversation(conv.id, titleInput)}
                    onKeyDown={e => { if (e.key === 'Enter') renameConversation(conv.id, titleInput) }}
                    className="w-full bg-transparent border-b text-sm outline-none"
                    style={{ color: 'var(--text-primary)' }}
                    autoFocus
                    onClick={e => e.stopPropagation()}
                  />
                ) : (
                  <>
                    <div className="flex items-center justify-between">
                      <span className="text-sm font-medium truncate" style={{ color: 'var(--text-primary)' }}>{conv.title}</span>
                      <span className="text-[10px]" style={{ color: 'var(--text-dim)' }}>
                        {new Date(conv.updatedAt).toLocaleDateString()}
                      </span>
                    </div>
                    <p className="text-xs mt-1 truncate" style={{ color: 'var(--text-dim)' }}>
                      {conv.messages[conv.messages.length - 1]?.content.slice(0, 40) || (lang === 'zh' ? '空对话' : 'Empty')}
                    </p>
                    <div className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 flex gap-1 transition-opacity">
                      <button
                        onClick={(e) => { e.stopPropagation(); toggleFavorite(conv.id) }}
                        className="w-6 h-6 rounded flex items-center justify-center"
                        style={{ background: 'var(--bg-surface)', color: conv.favorited ? '#F59E0B' : 'var(--text-dim)' }}
                        title={lang === 'zh' ? '收藏' : 'Favorite'}
                      >
                        <svg width="10" height="10" viewBox="0 0 24 24" fill={conv.favorited ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
                      </button>
                      <button
                        onClick={(e) => { e.stopPropagation(); setEditingTitle(conv.id); setTitleInput(conv.title) }}
                        className="w-6 h-6 rounded flex items-center justify-center"
                        style={{ background: 'var(--bg-surface)' }}
                        title={lang === 'zh' ? '重命名' : 'Rename'}
                      >
                        <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                      </button>
                      <button
                        onClick={(e) => { e.stopPropagation(); deleteConversation(conv.id) }}
                        className="w-6 h-6 rounded flex items-center justify-center"
                        style={{ background: 'var(--accent-rose)' + '20', color: 'var(--accent-rose)' }}
                        title={lang === 'zh' ? '删除' : 'Delete'}
                      >
                        <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>
                      </button>
                    </div>
                  </>
                )}
              </div>
            ))}
            {filteredConversations.length === 0 && (
              <p className="text-center text-xs py-8" style={{ color: 'var(--text-dim)' }}>
                {searchQuery ? (lang === 'zh' ? '无搜索结果' : 'No results') : (lang === 'zh' ? '暂无对话' : 'No conversations')}
              </p>
            )}
          </div>

          <div className="p-3 border-t space-y-2" style={{ borderColor: 'var(--border-color)' }}>
            <button
              onClick={cloudSync}
              className="w-full px-3 py-2 text-xs rounded-lg flex items-center gap-2 transition-colors"
              style={{ background: 'var(--bg-surface)', color: 'var(--text-secondary)' }}
            >
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
              {lang === 'zh' ? '云端备份' : 'Cloud Backup'}
            </button>
            <button
              onClick={cloudFetch}
              className="w-full px-3 py-2 text-xs rounded-lg flex items-center gap-2 transition-colors"
              style={{ background: 'var(--bg-surface)', color: 'var(--text-secondary)' }}
            >
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
              {lang === 'zh' ? '云端恢复' : 'Cloud Restore'}
            </button>
            <button
              onClick={() => exportConversation('markdown')}
              className="w-full px-3 py-2 text-xs rounded-lg flex items-center gap-2 transition-colors"
              style={{ background: 'var(--bg-surface)', color: 'var(--text-secondary)' }}
            >
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
              {lang === 'zh' ? '导出 MD' : 'Export MD'}
            </button>
            <button
              onClick={shareConversation}
              className="w-full px-3 py-2 text-xs rounded-lg flex items-center gap-2 transition-colors"
              style={{ background: 'var(--bg-surface)', color: 'var(--text-secondary)' }}
            >
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/></svg>
              {lang === 'zh' ? '分享对话' : 'Share'}
            </button>
          </div>
        </motion.div>
      )}

      <div className="flex-1 flex flex-col h-full">
        <div className="flex-shrink-0 h-14 flex items-center justify-between px-4 border-b" style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-color)' }}>
          <div className="flex items-center gap-3">
            <button
              onClick={() => setShowSidebar(!showSidebar)}
              className="w-8 h-8 rounded-lg flex items-center justify-center transition-colors"
              style={{ color: 'var(--text-secondary)', background: 'var(--bg-surface)' }}
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="18" x2="21" y2="18"/></svg>
            </button>
            <div className="flex rounded-lg p-0.5" style={{ background: 'var(--bg-surface)' }}>
              <button onClick={() => setMode('free')} className={`px-3 py-1.5 text-xs rounded-md transition-colors ${mode === 'free' ? 'text-white' : ''}`}
                style={mode === 'free' ? { background: 'var(--accent-emerald)' } : { color: 'var(--text-dim)' }}>
                {lang === 'zh' ? '免费' : 'Free'}
              </button>
              <button onClick={() => setMode('custom')} className={`px-3 py-1.5 text-xs rounded-md transition-colors ${mode === 'custom' ? 'text-white' : ''}`}
                style={mode === 'custom' ? { background: 'var(--accent-purple)' } : { color: 'var(--text-dim)' }}>
                {lang === 'zh' ? '自定义' : 'Custom'}
              </button>
              <button onClick={() => setMode('paid')} className={`px-3 py-1.5 text-xs rounded-md transition-colors ${mode === 'paid' ? 'text-white' : ''}`}
                style={mode === 'paid' ? { background: 'var(--accent-amber)' } : { color: 'var(--text-dim)' }}>
                {lang === 'zh' ? '付费' : 'Paid'}
              </button>
            </div>

            <select
              value={freeModel}
              onChange={e => setFreeModel(e.target.value)}
              className="px-3 py-1.5 text-xs rounded-lg border outline-none"
              style={{ background: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
            >
              <option value="deepseek-v4-pro">DeepSeek V4 Pro</option>
              <option value="deepseek-v4-flash">DeepSeek V4 Flash</option>
              <option value="gpt-3.5-turbo">GPT-3.5 Turbo</option>
              <option value="gpt-4o">GPT-4o</option>
            </select>

            {mode === 'free' && freeQuota && (
              <div className="flex items-center gap-1.5 text-[10px]" style={{ color: 'var(--text-dim)' }}>
                <span style={{ color: 'var(--accent-emerald)' }}>{freeQuota.maxCalls - freeQuota.usedCalls}</span>
                <span>/ {freeQuota.maxCalls}</span>
                <span className="opacity-40">{lang === 'zh' ? '次' : 'calls'}</span>
              </div>
            )}

            {mode === 'custom' && (
              <select
                value={selectedKeyId || ''}
                onChange={e => setSelectedKeyId(e.target.value ? Number(e.target.value) : null)}
                className="px-3 py-1.5 text-xs rounded-lg border outline-none max-w-[160px]"
                style={{ background: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
              >
                <option value="">{lang === 'zh' ? '选择 Key...' : 'Select Key...'}</option>
                {storedKeys.map(k => (
                  <option key={k.id} value={k.id}>{k.displayName || k.provider}</option>
                ))}
              </select>
            )}

            {mode === 'paid' && (
              <span className="text-[10px] px-2 py-1 rounded-md" style={{ background: 'rgba(245,158,11,0.1)', color: '#F59E0B' }}>
                {lang === 'zh' ? '扣余额' : 'Deducts Balance'}
              </span>
            )}
          </div>
        </div>

        <div ref={containerRef} className="flex-1 overflow-y-auto">
          {messages.length === 0 ? (
            <div className="flex flex-col items-center justify-center min-h-full py-16 px-4">
              <div className="w-12 h-12 rounded-2xl flex items-center justify-center mb-8 shadow-lg" style={{ background: 'linear-gradient(135deg, var(--accent-purple), var(--accent-blue))' }}>
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
                </svg>
              </div>
              <h2 className="text-xl font-semibold mb-2" style={{ color: 'var(--text-primary)' }}>
                {lang === 'zh' ? '有什么可以帮助你的？' : 'How can I help you?'}
              </h2>
              <p className="text-sm mb-8" style={{ color: 'var(--text-dim)' }}>
                {mode === 'free' ? (lang === 'zh' ? `免费模型 (${freeModel}) · 每日${freeQuota?.maxCalls||30}次/50K tokens` : `Free (${freeModel}) · ${freeQuota?.maxCalls||30} calls/day`)
                : mode === 'custom' ? (lang === 'zh' ? '自定义模型 · 使用你存储的 API Key' : 'Custom model · Your stored API key')
                : (lang === 'zh' ? `付费模型 · 按量扣余额` : 'Paid model · Balance deducted')}
              </p>
              <div className="grid grid-cols-2 gap-3 w-full max-w-lg">
                {WELCOME_SUGGESTIONS.map((s, i) => (
                  <button
                    key={i}
                    onClick={() => handleSend(s)}
                    className="text-left p-4 rounded-xl border text-sm transition-all duration-200"
                    style={{
                      background: 'var(--bg-card)',
                      borderColor: 'var(--border-color)',
                      color: 'var(--text-secondary)',
                    }}
                  >
                    {s}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            <div className="py-6 px-4 space-y-6 max-w-4xl mx-auto">
              {messages.map((msg, idx) => (
                <motion.div
                  key={idx}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  className={`flex gap-4 ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
                >
                  {msg.role === 'assistant' && (
                    <div className="flex-shrink-0 w-8 h-8 rounded-lg flex items-center justify-center shadow-sm mt-0.5" style={{ background: 'linear-gradient(135deg, var(--accent-purple), var(--accent-blue))' }}>
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2">
                        <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
                      </svg>
                    </div>
                  )}
                  <div className={`max-w-[85%] ${msg.role === 'user' ? 'order-first' : ''}`}>
                    {msg.role === 'user' ? (
                      <div className="px-5 py-3 rounded-2xl rounded-br-md border" style={{ background: 'var(--accent-purple)', borderColor: 'var(--accent-purple)' }}>
                        <p className="text-sm leading-relaxed whitespace-pre-wrap" style={{ color: '#fff' }}>{msg.content}</p>
                      </div>
                    ) : idx === messages.length - 1 && isStreaming ? (
                      <div className="rounded-xl overflow-hidden border p-4 min-h-[60px]" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                        <MarkdownRenderer content={msg.content} isStreaming />
                      </div>
                    ) : (
                      <div className="rounded-xl overflow-hidden border p-4" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                        <MarkdownRenderer content={msg.content} />
                      </div>
                    )}
                  </div>
                </motion.div>
              ))}
              {tokenStats && (
                <motion.div
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  className="flex items-center justify-center gap-4 text-xs py-2"
                  style={{ color: 'var(--text-dim)' }}
                >
                  <span>Input {tokenStats.inputTokens.toLocaleString()} tokens</span>
                  <span>·</span>
                  <span>Output {tokenStats.outputTokens.toLocaleString()} tokens</span>
                  <span>·</span>
                  <span style={{ color: 'var(--accent-amber)' }}>${tokenStats.cost.toFixed(6)}</span>
                </motion.div>
              )}
              <div ref={el => el?.scrollIntoView({ behavior: 'smooth' })} />
            </div>
          )}
        </div>

        {error && (
          <div className="flex-shrink-0 px-4 pb-2">
            <div className="flex items-center gap-2 text-sm rounded-lg px-4 py-2 max-w-2xl mx-auto" style={{ background: 'var(--accent-rose)' + '15', color: 'var(--accent-rose)' }}>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="12" r="10" />
                <line x1="12" y1="8" x2="12" y2="12" />
                <line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
              <span className="flex-1">{error}</span>
              <button onClick={() => setError('')} style={{ color: 'inherit' }}>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <line x1="18" y1="6" x2="6" y2="18" />
                  <line x1="6" y1="6" x2="18" y2="18" />
                </svg>
              </button>
            </div>
          </div>
        )}

        <div className="flex-shrink-0 p-4 border-t" style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-color)' }}>
          <div className="flex items-end gap-3 max-w-4xl mx-auto">
            <div className="flex-1">
              <textarea
                ref={inputRef}
                value={input}
                onChange={e => {
                  setInput(e.target.value)
                  const el = e.currentTarget
                  el.style.height = 'auto'
                  el.style.height = Math.min(el.scrollHeight, 200) + 'px'
                }}
                onKeyDown={handleKeyDown}
                placeholder={lang === 'zh' ? '输入消息... (Enter 发送, Shift+Enter 换行)' : 'Type a message... (Enter to send, Shift+Enter for new line)'}
                rows={1}
                disabled={isStreaming}
                className="w-full px-5 py-3.5 rounded-2xl text-sm outline-none resize-none transition-all"
                style={{
                  background: 'var(--bg-input)',
                  border: '1px solid var(--border-color)',
                  color: 'var(--text-primary)',
                  minHeight: 48,
                }}
              />
            </div>
            {isStreaming ? (
              <button
                onClick={handleStop}
                className="flex-shrink-0 w-10 h-10 flex items-center justify-center rounded-xl transition-all"
                style={{ background: 'var(--accent-rose)' + '20', color: 'var(--accent-rose)' }}
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                  <rect x="6" y="6" width="12" height="12" rx="1" />
                </svg>
              </button>
            ) : (
              <button
                onClick={() => handleSend()}
                disabled={!input.trim()}
                className="flex-shrink-0 w-10 h-10 flex items-center justify-center rounded-xl transition-all disabled:opacity-30"
                style={{ background: 'linear-gradient(135deg, var(--accent-purple), var(--accent-blue))', color: '#fff' }}
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M5 12h14M12 5l7 7-7 7" />
                </svg>
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
