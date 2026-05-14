import { useState, useRef, useEffect, useCallback } from 'react'
import { chatApi, ensureApiKey, userApi, userApiKeyApi, type UserApiKeyItem } from '@/services/api'
import MarkdownRenderer from '@/components/chat/MarkdownRenderer'
import ConfirmDialog from '@/components/ConfirmDialog'
import { motion } from 'framer-motion'
import { useI18nStore } from '@/stores/i18nStore'
import { useAppStore } from '@/stores/appStore'
import { useToastStore } from '@/stores/toastStore'

export interface ChatConversation {
  id: string
  title: string
  messages: Message[]
  createdAt: number
  updatedAt: number
  favorited?: boolean
}

export interface Message { role: 'user' | 'assistant' | 'system'; content: string }

const WELCOME_SUGGESTIONS = [
  '用一句话介绍凌点 AI 平台',
  '写一个 Python 快速排序',
  '帮我分析 AI 行业发展趋势',
  '解释 RESTful API 的原理',
]

const STORAGE_NS = 'lingdian_chat'

function storageKey(uid: number, suffix: string): string {
  return `${STORAGE_NS}_${suffix}_${uid}`
}

function loadLocal(uid: number): ChatConversation[] {
  try { const d = localStorage.getItem(storageKey(uid, 'convs')); return d ? JSON.parse(d) : [] } catch { return [] }
}
function saveLocal(uid: number, convs: ChatConversation[]) {
  try { localStorage.setItem(storageKey(uid, 'convs'), JSON.stringify(convs.slice(-200))) } catch {}
}
function loadCurrentId(uid: number): string | null {
  return localStorage.getItem(storageKey(uid, 'current'))
}
function saveCurrentId(uid: number, id: string | null) {
  if (id) localStorage.setItem(storageKey(uid, 'current'), id)
  else localStorage.removeItem(storageKey(uid, 'current'))
}
function loadVersion(uid: number): number {
  try { return parseInt(localStorage.getItem(storageKey(uid, 'version')) || '0') || 0 } catch { return 0 }
}
function saveVersion(uid: number, v: number) {
  localStorage.setItem(storageKey(uid, 'version'), String(v))
}
function clearChatData(uid: number) {
  ['convs', 'current', 'version'].forEach(k => localStorage.removeItem(storageKey(uid, k)))
}

function genId() { return Date.now().toString(36) + Math.random().toString(36).substring(2, 11) }
function genTitle(s: string) { return s.length > 30 ? s.slice(0, 30) + '...' : s }

export default function ChatPage() {
  const { lang } = useI18nStore()
  const user = useAppStore(s => s.user)
  const uid = user?.id

  const [freeModel, setFreeModel] = useState(() => localStorage.getItem('ld_free_model') || 'deepseek-v4-flash')
  const [paidModel, setPaidModel] = useState(() => localStorage.getItem('ld_paid_model') || 'deepseek-v4-flash')
  const [temperature] = useState(0.7)
  const [mode, setMode] = useState<'free' | 'paid'>('free')
  const [storedKeys, setStoredKeys] = useState<UserApiKeyItem[]>([])
  const [freeQuota, setFreeQuota] = useState<{ usedCalls: number; maxCalls: number; usedTokens: number; maxTokens: number } | null>(null)

  const [conversations, setConversations] = useState<ChatConversation[]>([])
  const [currentConvId, setCurrentConvId] = useState<string | null>(null)
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [isStreaming, setIsStreaming] = useState(false)
  const [error, setError] = useState('')
  const [tokenStats, setTokenStats] = useState<{ inputTokens: number; outputTokens: number; cost: number } | null>(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [showSidebar, setShowSidebar] = useState(true)
  const [isMobile, setIsMobile] = useState(false)
  const [editingTitle, setEditingTitle] = useState<string | null>(null)
  const [titleInput, setTitleInput] = useState('')
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null)
  const [pendingClear, setPendingClear] = useState(false)
  const [openMenuId, setOpenMenuId] = useState<string | null>(null)

  const abortRef = useRef<AbortController | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const apiKeyRef = useRef<string | null>(null)
  const apiKeyInitRef = useRef(false)
  const prevUid = useRef<number | undefined>(undefined)
  const initialized = useRef(false)

  const currentConv = conversations.find(c => c.id === currentConvId)

  const saveAndSync = useCallback((convs: ChatConversation[]) => {
    if (!uid) return
    setConversations(convs)
    saveLocal(uid, convs)
    saveVersion(uid, Date.now())
    userApi.syncConversations(convs).catch(() => {})
  }, [uid])

  const createConv = useCallback(() => {
    if (!uid) return
    const id = genId()
    const conv: ChatConversation = { id, title: '新对话', messages: [], createdAt: Date.now(), updatedAt: Date.now() }
    const updated = [conv, ...conversations]
    saveAndSync(updated)
    setCurrentConvId(id)
    setMessages([])
    setTokenStats(null)
    saveCurrentId(uid, id)
  }, [uid, conversations, saveAndSync])

  const selectConv = useCallback((id: string) => {
    const conv = conversations.find(c => c.id === id)
    if (conv) {
      setCurrentConvId(id)
      setMessages(conv.messages)
      setTokenStats(null)
      if (uid) saveCurrentId(uid, id)
    }
  }, [conversations, uid])

  const updateCurrentConv = useCallback((msgs: Message[]) => {
    if (!currentConvId) return
    const updated = conversations.map(c =>
      c.id === currentConvId ? {
        ...c, messages: msgs, updatedAt: Date.now(),
        title: msgs.length > 0 && c.title === '新对话' && msgs[0].role === 'user' ? genTitle(msgs[0].content) : c.title
      } : c
    )
    saveAndSync(updated)
  }, [conversations, currentConvId, saveAndSync])

  const doDeleteConv = useCallback(() => {
    if (!pendingDeleteId || !uid) return
    const filtered = conversations.filter(c => c.id !== pendingDeleteId)
    saveAndSync(filtered)
    setPendingDeleteId(null)
    if (currentConvId === pendingDeleteId) {
      if (filtered.length > 0) selectConv(filtered[0].id)
      else createConv()
    }
  }, [uid, conversations, currentConvId, pendingDeleteId, saveAndSync, selectConv, createConv])

  const renameConv = useCallback((id: string, title: string) => {
    saveAndSync(conversations.map(c => c.id === id ? { ...c, title } : c))
    setEditingTitle(null)
  }, [conversations, saveAndSync])

  const toggleFav = useCallback((id: string) => {
    saveAndSync(conversations.map(c => c.id === id ? { ...c, favorited: !c.favorited } : c))
  }, [conversations, saveAndSync])

  const handleClear = () => { setPendingClear(true) }
  const doClear = () => {
    abortRef.current?.abort()
    setMessages([]); setTokenStats(null); setIsStreaming(false); setPendingClear(false)
    if (currentConvId) saveAndSync(conversations.map(c => c.id === currentConvId ? { ...c, messages: [], updatedAt: Date.now() } : c))
  }

  // ========== 核心初始化：userId驱动的一次性加载 + 切换清缓存 ==========
  useEffect(() => {
    if (!uid) return

    // 首次初始化（页面刷新）
    if (!initialized.current) {
      initialized.current = true
      prevUid.current = uid
      const local = loadLocal(uid)
      const savedId = loadCurrentId(uid)
      setConversations(local)
      if (savedId && local.find(c => c.id === savedId)) {
        setCurrentConvId(savedId)
        setMessages(local.find(c => c.id === savedId)!.messages)
      } else if (local.length > 0) {
        setCurrentConvId(local[0].id)
        setMessages(local[0].messages)
      }
      // 后台异步拉取最新版本
      userApi.getConversationVersion().then(r => {
        const serverVersion = r?.version || 0
        const localVer = loadVersion(uid)
        if (serverVersion > localVer) {
          userApi.fetchConversations().then(f => {
            if (f?.conversations?.length) {
              setConversations(f.conversations as ChatConversation[])
              saveLocal(uid, f.conversations as ChatConversation[])
              saveVersion(uid, serverVersion)
              const cur = loadCurrentId(uid)
              if (cur && f.conversations.find((c: any) => c.id === cur)) {
                setCurrentConvId(cur)
                setMessages(f.conversations.find((c: any) => c.id === cur).messages)
              }
            }
          }).catch(() => {})
        } else {
          // 本地最新，主动推一次到服务端对齐
          if (local.length > 0) userApi.syncConversations(local).catch(() => {})
        }
      }).catch(() => {})
      return
    }

    // 账号切换
    if (prevUid.current !== uid) {
      if (prevUid.current) clearChatData(prevUid.current)
      prevUid.current = uid
      const local = loadLocal(uid)
      const savedId = loadCurrentId(uid)
      setConversations(local)
      setMessages([])
      setTokenStats(null)
      if (savedId && local.find(c => c.id === savedId)) {
        setCurrentConvId(savedId)
        setMessages(local.find(c => c.id === savedId)!.messages)
      } else if (local.length > 0) {
        setCurrentConvId(local[0].id)
        setMessages(local[0].messages)
      } else {
        setCurrentConvId(null)
      }
      userApi.getConversationVersion().then(r => {
        if ((r?.version || 0) > loadVersion(uid)) {
          userApi.fetchConversations().then(f => {
            if (f?.conversations?.length) {
              setConversations(f.conversations as ChatConversation[])
              saveLocal(uid, f.conversations as ChatConversation[])
              saveVersion(uid, r.version)
            }
          }).catch(() => {})
        }
      }).catch(() => {})
    }
  }, [uid])

  // ========== 当conversations有数据但无currentConvId时，自动创建 ==========
  useEffect(() => {
    if (!uid || !initialized.current) return
    if (conversations.length === 0 && initialized.current) {
      createConv()
    }
  }, [uid, conversations.length])

  // ========== API Key + Quota + Mobile ==========
  useEffect(() => {
    if (!apiKeyInitRef.current) { apiKeyInitRef.current = true; ensureApiKey().then(k => { if (k) apiKeyRef.current = k }) }
  }, [])
  useEffect(() => {
    userApiKeyApi.list().then(k => setStoredKeys(k || [])).catch(() => {})
    userApi.getFreeQuota().then(q => setFreeQuota(q)).catch(() => {})
    const chk = () => { const m = window.innerWidth < 768; setIsMobile(m); if (m) setShowSidebar(false) }
    chk(); window.addEventListener('resize', chk)
    return () => window.removeEventListener('resize', chk)
  }, [])

  useEffect(() => { return () => abortRef.current?.abort() }, [])
  useEffect(() => { if (!isStreaming) setTimeout(() => inputRef.current?.focus(), 50) }, [isStreaming])
  useEffect(() => { containerRef.current?.scrollTo({ top: containerRef.current.scrollHeight, behavior: 'smooth' }) }, [messages])

  const handleSend = async (overrideInput?: string) => {
    const content = (overrideInput || input).trim()
    if (!content || isStreaming) return
    if (!currentConvId && uid) createConv()
    setError(''); setInput(''); setTokenStats(null)
    const apiKey = apiKeyRef.current
    if (!apiKey) { setError('请先创建并启用一个API Key'); return }
    const newMsgs: Message[] = [...messages, { role: 'user' as const, content }]
    setMessages(newMsgs); updateCurrentConv(newMsgs); setIsStreaming(true)
    let fullContent = ''
    abortRef.current = chatApi.stream(
      apiKey,
      {
        model: mode === 'free' ? freeModel : paidModel,
        messages: [
          { role: 'system', content: 'You are a helpful AI assistant. Always respond in well-formatted Markdown. Use headers, lists, code blocks with language tags, tables, and bold text where appropriate. Structure your answers to be visually clear and scannable.' },
          ...newMsgs,
        ],
        temperature,
        mode,
      } as any,
      (chunk: string) => { fullContent += chunk; setMessages([...newMsgs, { role: 'assistant' as const, content: fullContent }]) },
      (result) => { setIsStreaming(false); setTokenStats(result); userApi.getFreeQuota().then(q => setFreeQuota(q)).catch(() => {}); const final: Message[] = [...newMsgs, { role: 'assistant' as const, content: fullContent }]; setMessages(final); updateCurrentConv(final) },
      (err: string) => { setIsStreaming(false); setError(err); useToastStore.getState().showToast({ title: '对话失败', message: err, type: 'error' }) }
    )
  }

  const handleStop = () => { abortRef.current?.abort(); setIsStreaming(false) }
  const handleKeyDown = (e: React.KeyboardEvent) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend() } }

  const cloudRestore = async () => {
    if (!uid) return
    try {
      const r = await userApi.fetchConversations()
      if (r?.conversations?.length) {
        const convs = r.conversations as ChatConversation[]
        setConversations(convs)
        saveLocal(uid, convs)
        saveVersion(uid, r.version)
        if (convs.length > 0) { setCurrentConvId(convs[0].id); setMessages(convs[0].messages) }
        useToastStore.getState().showToast({ title: lang === 'zh' ? '已从云端恢复' : 'Restored', message: `${convs.length} ${lang === 'zh' ? '个对话' : 'conversations'}`, type: 'success' })
      }
    } catch {
      useToastStore.getState().showToast({ title: lang === 'zh' ? '恢复失败' : 'Restore failed', message: '', type: 'error' })
    }
  }

  const exportConv = (format: 'markdown' | 'json' | 'txt') => {
    if (!currentConv) return
    let ct = ''; const c = currentConv
    if (format === 'markdown') { ct = `# ${c.title}\n\n`; c.messages.forEach(m => { ct += `**${m.role === 'user' ? '用户' : 'AI'}**: ${m.content}\n\n` }) }
    else if (format === 'json') { ct = JSON.stringify(c, null, 2) }
    else { c.messages.forEach(m => { ct += `${m.role}: ${m.content}\n\n` }) }
    const b = new Blob([ct], { type: 'text/plain' }); const u = URL.createObjectURL(b)
    const a = document.createElement('a'); a.href = u; a.download = `${c.title}.${format}`; a.click(); URL.revokeObjectURL(u)
  }
  const shareConv = () => {
    if (!currentConv) return
    const enc = btoa(encodeURIComponent(JSON.stringify({ title: currentConv.title, messages: currentConv.messages })))
    navigator.clipboard.writeText(`${window.location.origin}/chat?share=${enc}`)
    useToastStore.getState().showToast({ title: lang === 'zh' ? '已复制' : 'Copied', message: '', type: 'success' })
  }

  const filtered = conversations.filter(c =>
    c.title.toLowerCase().includes(searchQuery.toLowerCase()) || c.messages.some(m => m.content.toLowerCase().includes(searchQuery.toLowerCase()))
  )

  // If uid not yet resolved, show spinner
  if (!uid) {
    return <div className="flex items-center justify-center h-full"><div className="w-6 h-6 border-2 border-brand-purple/30 border-t-brand-purple rounded-full animate-spin" /></div>
  }

  return (
    <div className="flex h-full" style={{ background: 'var(--bg-primary)' }}>
      {/* Desktop sidebar */}
      {showSidebar && !isMobile && (
        <motion.div initial={{ width: 0, opacity: 0 }} animate={{ width: 280, opacity: 1 }} exit={{ width: 0, opacity: 0 }}
          className="h-full border-r flex flex-col" style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-color)' }}>
          <div className="p-4 border-b" style={{ borderColor: 'var(--border-color)' }}>
            <div className="flex items-center justify-between mb-3">
              <h2 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>对话历史</h2>
              <button onClick={createConv} className="w-7 h-7 rounded-lg flex items-center justify-center transition-colors" style={{ background: 'var(--accent-purple)', color: '#fff' }} title={lang === 'zh' ? '新建对话' : 'New Chat'}>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
              </button>
            </div>
            <input value={searchQuery} onChange={e => setSearchQuery(e.target.value)} placeholder={lang === 'zh' ? '搜索对话...' : 'Search...'}
              className="w-full px-3 py-2 text-xs rounded-lg border outline-none" style={{ background: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }} />
          </div>
          <div className="flex-1 overflow-y-auto p-2 space-y-1">
            {filtered.map(conv => (
              <div key={conv.id} onClick={() => selectConv(conv.id)}
                className={`group relative p-3 rounded-xl cursor-pointer transition-all ${conv.id === currentConvId ? 'border' : ''}`}
                style={{ background: conv.id === currentConvId ? 'var(--accent-purple)' + '20' : 'transparent', borderColor: conv.id === currentConvId ? 'var(--accent-purple)' + '40' : 'transparent' }}>
                {editingTitle === conv.id ? (
                  <input value={titleInput} onChange={e => setTitleInput(e.target.value)} onBlur={() => renameConv(conv.id, titleInput)} onKeyDown={e => { if (e.key === 'Enter') renameConv(conv.id, titleInput) }}
                    className="w-full bg-transparent border-b text-sm outline-none" style={{ color: 'var(--text-primary)' }} autoFocus onClick={e => e.stopPropagation()} />
                ) : (
                  <>
                    <div className="flex items-center justify-between">
                      <span className="text-sm font-medium truncate pr-6" style={{ color: 'var(--text-primary)' }}>{conv.title}</span>
                      <div className="absolute top-2 right-2">
                        <button onClick={(e) => { e.stopPropagation(); setOpenMenuId(openMenuId === conv.id ? null : conv.id) }}
                          className="w-6 h-6 rounded flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity" style={{ background: 'var(--bg-surface)' }}>
                          <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor" style={{ color: 'var(--text-dim)' }}><circle cx="12" cy="5" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="12" cy="19" r="1.5"/></svg>
                        </button>
                        {openMenuId === conv.id && (
                          <div className="absolute right-0 top-8 z-30 rounded-xl py-1 shadow-xl w-36" style={{ background: 'var(--bg-card)', border: '1px solid var(--border-color)' }}>
                            {[{ l: '重命名', a: () => { setEditingTitle(conv.id); setTitleInput(conv.title) } }, { l: lang === 'zh' ? '收藏' : 'Favorite', a: () => toggleFav(conv.id) }, { l: lang === 'zh' ? '导出MD' : 'Export MD', a: () => exportConv('markdown') }, { l: lang === 'zh' ? '删除' : 'Delete', a: () => { setPendingDeleteId(conv.id); setOpenMenuId(null) }, d: true }].map(item => (
                              <button key={item.l} onClick={(e) => { e.stopPropagation(); item.a(); setOpenMenuId(null) }}
                                className="w-full text-left px-3 py-1.5 text-xs transition-colors hover:bg-white/[0.05]"
                                style={{ color: item.d ? 'var(--accent-rose)' : 'var(--text-secondary)' }}>{item.l}</button>
                            ))}
                          </div>
                        )}
                      </div>
                    </div>
                    <p className="text-[10px] mt-1 truncate" style={{ color: 'var(--text-dim)' }}>
                      {conv.messages.length > 0 ? conv.messages[conv.messages.length - 1].content.slice(0, 40) : (lang === 'zh' ? '空对话' : 'Empty')}
                    </p>
                  </>
                )}
              </div>
            ))}
            {filtered.length === 0 && (
              <p className="text-center text-xs py-8" style={{ color: 'var(--text-dim)' }}>
                {searchQuery ? (lang === 'zh' ? '无搜索结果' : 'No results') : (lang === 'zh' ? '暂无对话' : 'No conversations')}
              </p>
            )}
          </div>
          <div className="p-3 border-t space-y-2" style={{ borderColor: 'var(--border-color)' }}>
            <button onClick={cloudRestore} className="w-full px-3 py-2 text-xs rounded-lg flex items-center gap-2 transition-colors" style={{ background: 'var(--bg-surface)', color: 'var(--text-secondary)' }}>
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
              {lang === 'zh' ? '云端恢复' : 'Cloud Restore'}
            </button>
          </div>
        </motion.div>
      )}

      {/* Mobile sidebar overlay */}
      {isMobile && showSidebar && (
        <div className="fixed inset-0 z-50 flex">
          <div className="absolute inset-0 bg-black/50" onClick={() => setShowSidebar(false)} />
          <div className="relative h-full" style={{ background: 'var(--bg-secondary)' }}>
            {/* Reuse same sidebar content */}
            {(() => { setShowSidebar(true); return null })()}
          </div>
        </div>
      )}

      {/* Main chat area */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Top bar */}
        <div className="flex-shrink-0 h-[52px] border-b flex items-center px-4 gap-3" style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-color)' }}>
          {isMobile && <button onClick={() => setShowSidebar(true)} className="w-8 h-8 rounded-lg flex items-center justify-center" style={{ background: 'var(--bg-surface)' }}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>
          </button>}
          {!isMobile && <button onClick={() => setShowSidebar(s => !s)} className="w-8 h-8 rounded-lg flex items-center justify-center" style={{ background: 'var(--bg-surface)' }}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>
          </button>}
          <div className="flex items-center gap-2 ml-auto">
            <button onClick={() => setMode('free')} className={`px-3 py-1.5 text-xs rounded-md transition-colors ${mode === 'free' ? 'text-white' : ''}`}
              style={{ background: mode === 'free' ? 'var(--accent-emerald)' : 'transparent', color: mode === 'free' ? '#fff' : 'var(--text-dim)' }}>
              {lang === 'zh' ? '免费' : 'Free'}
            </button>
            <button onClick={() => setMode('paid')} className={`px-3 py-1.5 text-xs rounded-md transition-colors ${mode === 'paid' ? 'text-white' : ''}`}
              style={{ background: mode === 'paid' ? 'var(--accent-purple)' : 'transparent', color: mode === 'paid' ? '#fff' : 'var(--text-dim)' }}>
              {lang === 'zh' ? '付费' : 'Paid'}
            </button>
            <button onClick={handleClear} className="w-8 h-8 rounded-lg flex items-center justify-center" style={{ background: 'var(--bg-surface)' }} title={lang === 'zh' ? '清空对话' : 'Clear chat'}>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>
            </button>
          </div>
        </div>

        {/* Messages */}
        <div ref={containerRef} className="flex-1 overflow-y-auto">
          {messages.length === 0 ? (
            <div className="flex flex-col items-center justify-center min-h-full py-16 px-4">
              <div className="w-12 h-12 rounded-2xl flex items-center justify-center mb-8 shadow-lg" style={{ background: 'linear-gradient(135deg, var(--accent-purple), var(--accent-blue))' }}>
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
              </div>
              <h2 className="text-xl font-semibold mb-2" style={{ color: 'var(--text-primary)' }}>{lang === 'zh' ? '有什么可以帮助你的？' : 'How can I help you?'}</h2>
              <p className="text-sm mb-8" style={{ color: 'var(--text-dim)' }}>
                {mode === 'free' ? (lang === 'zh' ? `免费模型 (${freeModel}) · 每日${freeQuota?.maxCalls || 30}次/50K tokens` : `Free (${freeModel}) · ${freeQuota?.maxCalls || 30} calls/day`)
                  : (lang === 'zh' ? '付费模型 · 按点数计费' : 'Paid model · Points billed')}
              </p>
              <div className="grid grid-cols-2 gap-3 w-full max-w-lg">
                {WELCOME_SUGGESTIONS.map((s, i) => (
                  <button key={i} onClick={() => handleSend(s)}
                    className="text-left p-4 rounded-xl border text-sm transition-all duration-200" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>
                    {s}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            <div className="py-6 px-4 space-y-6 max-w-4xl mx-auto">
              {messages.map((msg, idx) => (
                <motion.div key={idx} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}
                  className={`flex gap-4 ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                  {msg.role === 'assistant' && (
                    <div className="flex-shrink-0 w-8 h-8 rounded-lg flex items-center justify-center shadow-sm mt-0.5" style={{ background: 'linear-gradient(135deg, var(--accent-purple), var(--accent-blue))' }}>
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
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
                <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="flex items-center justify-center gap-4 text-xs py-2" style={{ color: 'var(--text-dim)' }}>
                  <span>Input {tokenStats.inputTokens.toLocaleString()} tokens</span>
                  <span>·</span>
                  <span>Output {tokenStats.outputTokens.toLocaleString()} tokens</span>
                </motion.div>
              )}
              <div ref={el => el?.scrollIntoView({ behavior: 'smooth' })} />
            </div>
          )}
        </div>

        {error && (
          <div className="flex-shrink-0 px-4 pb-2">
            <div className="flex items-center gap-2 text-sm rounded-lg px-4 py-2 max-w-2xl mx-auto" style={{ background: 'var(--accent-rose)' + '15', color: 'var(--accent-rose)' }}>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
              <span className="flex-1">{error}</span>
              <button onClick={() => setError('')}><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg></button>
            </div>
          </div>
        )}

        {/* Input area */}
        <div className="flex-shrink-0 p-4 border-t" style={{ background: 'var(--bg-secondary)', borderColor: 'var(--border-color)' }}>
          <div className="flex items-end gap-3 max-w-4xl mx-auto">
            <div className="flex-1">
              <textarea ref={inputRef} value={input} onChange={e => { setInput(e.target.value); const el = e.currentTarget; el.style.height = 'auto'; el.style.height = Math.min(el.scrollHeight, 200) + 'px' }}
                onKeyDown={handleKeyDown} placeholder={lang === 'zh' ? '输入消息... (Enter 发送, Shift+Enter 换行)' : 'Type a message... (Enter to send, Shift+Enter for new line)'}
                rows={1} disabled={isStreaming} className="w-full px-5 py-3.5 rounded-2xl text-sm outline-none resize-none transition-all"
                style={{ background: 'var(--bg-input)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', minHeight: 48 }} />
            </div>
            {isStreaming ? (
              <button onClick={handleStop} className="flex-shrink-0 w-10 h-10 flex items-center justify-center rounded-xl transition-all"
                style={{ background: 'var(--accent-rose)' + '20', color: 'var(--accent-rose)' }}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="6" width="12" height="12" rx="1"/></svg>
              </button>
            ) : (
              <button onClick={() => handleSend()} disabled={!input.trim()}
                className="flex-shrink-0 w-10 h-10 flex items-center justify-center rounded-xl transition-all disabled:opacity-30"
                style={{ background: 'linear-gradient(135deg, var(--accent-purple), var(--accent-blue))', color: '#fff' }}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
              </button>
            )}
          </div>
        </div>
      </div>

      <ConfirmDialog open={pendingDeleteId !== null} title={lang === 'zh' ? '删除对话' : 'Delete Conversation'}
        message={lang === 'zh' ? '删除后对话记录不可恢复，确定要删除吗？' : 'This conversation will be permanently deleted.'}
        confirmLabel={lang === 'zh' ? '删除' : 'Delete'} onConfirm={doDeleteConv} onCancel={() => setPendingDeleteId(null)} />
      <ConfirmDialog open={pendingClear} title={lang === 'zh' ? '清空对话' : 'Clear Chat'}
        message={lang === 'zh' ? '清空当前对话内容，确定继续？' : 'Clear current conversation content?'}
        confirmLabel={lang === 'zh' ? '清空' : 'Clear'} onConfirm={doClear} onCancel={() => setPendingClear(false)} />
    </div>
  )
}
