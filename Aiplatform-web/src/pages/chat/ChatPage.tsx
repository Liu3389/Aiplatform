import { useState, useEffect, useRef, useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import ChatSidebar from './ChatSidebar'
import ChatMessages from './ChatMessages'
import ChatInput, { type UploadedFile } from './ChatInput'
import WelcomeScreen from './WelcomeScreen'
import { modelApi, chatApi, chatFileApi, aiDocApi, userApi } from '@/services/api'
import { useAppStore } from '@/stores/appStore'
import { useI18nStore } from '@/stores/i18nStore'
import type { FrontendConversation, FrontendMessage, AvailableModel } from '@/types'

function genTitle(content: string) {
  return content.length > 30 ? content.slice(0, 30) + '...' : content
}

const STORAGE_KEY = 'ld_conversations'
const DEFAULT_MODEL = 'deepseek-v4-flash'
const LS_DEBOUNCE_MS = 800

export default function ChatPage() {
  const navigate = useNavigate()
  const { token } = useAppStore()
  const { t, lang } = useI18nStore()
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => {
    try { return localStorage.getItem('ld_sidebar_collapsed') === 'true' } catch { return false }
  })

  const [conversations, setConversations] = useState<FrontendConversation[]>(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (raw) return JSON.parse(raw)
    } catch {}
    const id = Date.now().toString(36)
    return [{ id, title: t('新对话', 'New Chat'), messages: [], createdAt: Date.now(), updatedAt: Date.now() }]
  })

  const [currentConvId, setCurrentConvId] = useState<string>(() => {
    try {
      const saved = localStorage.getItem('ld_current_conv')
      return saved || conversations[0]?.id || ''
    } catch {
      return conversations[0]?.id || ''
    }
  })

  const [model, setModel] = useState<string>(() => localStorage.getItem('ld_chat_model') || DEFAULT_MODEL)
  const [availableModels, setAvailableModels] = useState<AvailableModel[]>([])
  const [showModelSelector, setShowModelSelector] = useState(false)
  const [isStreaming, setIsStreaming] = useState(false)
  const [fileProcessStatus, setFileProcessStatus] = useState('')

  const convRef = useRef(conversations)
  convRef.current = conversations
  const isStreamingRef = useRef(false)
  const streamControllerRef = useRef<AbortController | null>(null)
  const lsTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const mountedRef = useRef(true)
  const latestModelRef = useRef(model)
  latestModelRef.current = model

  const currentConv = useMemo(() =>
    conversations.find((c) => c.id === currentConvId) || {
      id: currentConvId,
      title: t('新对话', 'New Chat'),
      messages: [],
      createdAt: Date.now(),
      updatedAt: Date.now(),
    },
  [conversations, currentConvId, t])

  const currentModelLabel = useMemo(() =>
    availableModels.find(m => m.displayName === model || m.modelCode === model)?.displayName || model,
  [availableModels, model])

  const persist = useCallback((updated: FrontendConversation[], skipStateUpdate = false) => {
    if (!skipStateUpdate) {
      setConversations(updated)
    }
    if (lsTimerRef.current) clearTimeout(lsTimerRef.current)
    lsTimerRef.current = setTimeout(() => {
      try { localStorage.setItem(STORAGE_KEY, JSON.stringify(updated)) } catch {}
    }, LS_DEBOUNCE_MS)
  }, [])

  const saveCurrentConvId = useCallback((id: string) => {
    setCurrentConvId(id)
    localStorage.setItem('ld_current_conv', id)
  }, [])

  useEffect(() => {
    mountedRef.current = true
    return () => { mountedRef.current = false }
  }, [])

  useEffect(() => {
    if (!token) { navigate('/login'); return }
  }, [token, navigate])

  useEffect(() => {
    let cancelled = false
    modelApi.getAvailable().then((models: AvailableModel[]) => {
      if (cancelled || !mountedRef.current) return
      const allSorted = (models || []).sort((a, b) => a.sortOrder - b.sortOrder)
      setAvailableModels(allSorted)
      const active = allSorted.filter((m) => m.accessible)
      if (active.length > 0 && !active.find((m) => m.modelCode === latestModelRef.current || m.displayName === latestModelRef.current)) {
        const firstModel = active[0].displayName || DEFAULT_MODEL
        setModel(firstModel)
        localStorage.setItem('ld_chat_model', firstModel)
      }
    }).catch(() => {})
    return () => { cancelled = true }
  }, [])

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setShowModelSelector(false)
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [])

  const stopStreaming = useCallback(() => {
    streamControllerRef.current?.abort()
    streamControllerRef.current = null
    isStreamingRef.current = false
    setIsStreaming(false)
    setFileProcessStatus('')
  }, [])

  const handleSend = useCallback(async (text: string, uploadedFiles: UploadedFile[]) => {
    if (!text.trim() && uploadedFiles.length === 0) return
    if (isStreamingRef.current) return

    const conv = convRef.current.find((c) => c.id === currentConvId)
    if (!conv) return

    const fileUrls = uploadedFiles.filter(f => f.url).map(f => f.url!)

    const userMsg: FrontendMessage = {
      role: 'user',
      content: text.trim(),
      id: Date.now(),
    }
    const assistantMsg: FrontendMessage = {
      role: 'assistant',
      content: '',
      id: Date.now() + 1,
    }

    const updatedConv: FrontendConversation = {
      ...conv,
      title: conv.messages.length === 0 ? genTitle(text) : conv.title,
      messages: [...conv.messages, userMsg, assistantMsg],
      updatedAt: Date.now(),
    }

    const updatedConvs = convRef.current.map((c) => (c.id === currentConvId ? updatedConv : c))
    persist(updatedConvs)
    setFileProcessStatus('')

    streamControllerRef.current?.abort()
    const controller = new AbortController()
    streamControllerRef.current = controller
    isStreamingRef.current = true
    setIsStreaming(true)

    try {
      const hasFiles = uploadedFiles.length > 0 && uploadedFiles.some(f => f.fileId)
      if (hasFiles) {
        const fileIds = uploadedFiles.filter(f => f.fileId).map(f => f.fileId!)
        console.log('[ChatPage] Starting file chat with fileIds:', fileIds)
        await aiDocApi.chatStreamWithFileId(
          fileIds,
          text.trim(),
          {
            onStatus: (stage, message) => {
              if (stage === 'reading_files') {
                setFileProcessStatus(t('正在读取文件内容...', 'Reading file...'))
              } else if (stage === 'processing') {
                setFileProcessStatus(t('正在与AI对话...', 'Chatting with AI...'))
              } else {
                setFileProcessStatus(message || stage || '')
              }
            },
            onContent: (chunk) => {
              console.log('[ChatPage] Received chunk:', chunk.slice(0, 50))
              const currentConvs = convRef.current
              const c = currentConvs.find(cv => cv.id === currentConvId)
              if (!c) return
              const msgs = [...c.messages]
              const lastMsg = msgs[msgs.length - 1]
              if (lastMsg && lastMsg.role === 'assistant') {
                msgs[msgs.length - 1] = { ...lastMsg, content: lastMsg.content + chunk }
              }
              queueMicrotask(() => {
                const newConvs = currentConvs.map(cv => 
                  cv.id === currentConvId ? { ...cv, messages: msgs, updatedAt: Date.now() } : cv
                )
                setConversations(newConvs)
                convRef.current = newConvs
                try { localStorage.setItem(STORAGE_KEY, JSON.stringify(newConvs)) } catch {}
              })
            },
            onDone: (msg) => {
              console.log('[ChatPage] Stream done:', msg)
              const currentConvs = convRef.current
              const c = currentConvs.find(cv => cv.id === currentConvId)
              if (c) {
                const msgs = [...c.messages]
                const lastMsg = msgs[msgs.length - 1]
                if (lastMsg && lastMsg.role === 'assistant') {
                  msgs[msgs.length - 1] = { ...lastMsg, content: lastMsg.content }
                }
                const newConvs = currentConvs.map(cv => 
                  cv.id === currentConvId ? { ...cv, messages: msgs, updatedAt: Date.now() } : cv
                )
                setConversations(newConvs)
                convRef.current = newConvs
                try { localStorage.setItem(STORAGE_KEY, JSON.stringify(newConvs)) } catch {}
              }
              if (streamControllerRef.current === controller) streamControllerRef.current = null
              setFileProcessStatus('')
              isStreamingRef.current = false
              setIsStreaming(false)
            },
            onError: (error) => {
              console.error('[ChatPage] Stream error:', error)
              const currentConvs = convRef.current
              const c = currentConvs.find(cv => cv.id === currentConvId)
              if (!c) return
              const msgs = [...c.messages]
              const lastMsg = msgs[msgs.length - 1]
              const errorText = t('发生错误：', 'Error: ') + error
              if (lastMsg && lastMsg.role === 'assistant') {
                msgs[msgs.length - 1] = {
                  ...lastMsg,
                  content: lastMsg.content ? `${lastMsg.content}\n\n${errorText}` : errorText,
                }
              }
              persist(currentConvs.map(cv => cv.id === currentConvId ? { ...cv, messages: msgs, updatedAt: Date.now() } : cv))
              if (streamControllerRef.current === controller) streamControllerRef.current = null
              setFileProcessStatus('')
              isStreamingRef.current = false
              setIsStreaming(false)
            },
          },
          controller.signal,
          latestModelRef.current,
          /^\d+$/.test(conv.id) ? conv.id : undefined
        )
      } else {
        const chatMessages = [...conv.messages, userMsg]
          .filter(m => m.role !== 'system')
          .map(m => ({ role: m.role, content: m.content }))

        const payload: any = {
          model: latestModelRef.current,
          messages: chatMessages,
          stream: true,
          conversationId: /^\d+$/.test(conv.id) ? Number(conv.id) : undefined,
        }
        if (fileUrls.length > 0) payload.fileUrls = fileUrls

        await chatApi.stream(
          payload,
          {
            onMessage: (chunk: string) => {
              const currentConvs = convRef.current
              const c = currentConvs.find(cv => cv.id === currentConvId)
              if (!c) return
              const msgs = [...c.messages]
              const lastMsg = msgs[msgs.length - 1]
              if (lastMsg && lastMsg.role === 'assistant') {
                msgs[msgs.length - 1] = { ...lastMsg, content: lastMsg.content + chunk }
              }
              const newConvs = currentConvs.map(cv => 
                cv.id === currentConvId ? { ...cv, messages: msgs, updatedAt: Date.now() } : cv
              )
              setConversations(newConvs)
              convRef.current = newConvs
              try {
                localStorage.setItem(STORAGE_KEY, JSON.stringify(newConvs))
              } catch {}
            },
            onDone: (_finished, inputTokens?: number, outputTokens?: number, cost?: number) => {
              const currentConvs = convRef.current
              const c = currentConvs.find(cv => cv.id === currentConvId)
              if (!c) return
              const msgs = [...c.messages]
              const lastMsg = msgs[msgs.length - 1]
              if (lastMsg && lastMsg.role === 'assistant') {
                msgs[msgs.length - 1] = {
                  ...lastMsg,
                  tokens: inputTokens != null || outputTokens != null ? `Input: ${inputTokens || 0} | Output: ${outputTokens || 0} | Cost: ${cost || 0}` : undefined,
                }
              }
              persist(currentConvs.map(cv => cv.id === currentConvId ? { ...cv, messages: msgs, updatedAt: Date.now() } : cv))
              if (streamControllerRef.current === controller) streamControllerRef.current = null
              isStreamingRef.current = false
              setIsStreaming(false)
            },
            onError: (error: string) => {
              const currentConvs = convRef.current
              const c = currentConvs.find(cv => cv.id === currentConvId)
              if (!c) return
              const msgs = [...c.messages]
              const lastMsg = msgs[msgs.length - 1]
              if (lastMsg && lastMsg.role === 'assistant' && !lastMsg.content) {
                msgs[msgs.length - 1] = { ...lastMsg, content: t('发生错误：', 'Error: ') + error }
              }
              persist(currentConvs.map(cv => cv.id === currentConvId ? { ...cv, messages: msgs, updatedAt: Date.now() } : cv))
              if (streamControllerRef.current === controller) streamControllerRef.current = null
              isStreamingRef.current = false
              setIsStreaming(false)
            },
          },
          controller.signal
        )
      }
    } catch (err: any) {
      if (err?.name !== 'AbortError') {
        const currentConvs = convRef.current
        const c = currentConvs.find(cv => cv.id === currentConvId)
        if (c) {
          const msgs = [...c.messages]
          const lastMsg = msgs[msgs.length - 1]
          if (lastMsg && lastMsg.role === 'assistant' && !lastMsg.content) {
            msgs[msgs.length - 1] = { ...lastMsg, content: t('发生错误：', 'Error: ') + (err?.message || t('未知错误', 'Unknown error')) }
          }
          persist(currentConvs.map(cv => cv.id === currentConvId ? { ...cv, messages: msgs, updatedAt: Date.now() } : cv))
        }
      }
      setFileProcessStatus('')
      isStreamingRef.current = false
      setIsStreaming(false)
    }
  }, [currentConvId, persist, t])

  const handleSelectConv = useCallback((id: string) => {
    saveCurrentConvId(id)
  }, [saveCurrentConvId])

  const handleNewConv = useCallback(() => {
    const convs = convRef.current
    const newConv: FrontendConversation = {
      id: Date.now().toString(36),
      title: t('新对话', 'New Chat'),
      messages: [],
      createdAt: Date.now(),
      updatedAt: Date.now(),
    }
    persist([newConv, ...convs])
    saveCurrentConvId(newConv.id)
  }, [saveCurrentConvId, persist, t])

  const handleDeleteConv = useCallback((id: string) => {
    if (isStreamingRef.current) return
    const convs = convRef.current.filter(c => c.id !== id)
    if (convs.length === 0) {
      const newConv: FrontendConversation = {
        id: Date.now().toString(36),
        title: t('新对话', 'New Chat'),
        messages: [],
        createdAt: Date.now(),
        updatedAt: Date.now(),
      }
      persist([newConv])
      saveCurrentConvId(newConv.id)
    } else {
      persist(convs)
      if (currentConvId === id) saveCurrentConvId(convs[0].id)
    }
  }, [currentConvId, saveCurrentConvId, persist, t])

  const handleRenameConv = useCallback((id: string, title: string) => {
    const convs = convRef.current.map(c => c.id === id ? { ...c, title } : c)
    persist(convs)
  }, [persist])

  const handleExportConv = useCallback(async (conv: FrontendConversation) => {
    try {
      const text = conv.messages.map(m => `### ${m.role === 'user' ? 'User' : 'AI'}\n\n${m.content}\n`).join('\n---\n\n')
      const fileName = `${conv.title.replace(/[^a-zA-Z0-9\u4e00-\u9fa5]/g, '_')}.md`
      const resp = await chatFileApi.exportContent(text, fileName)
      const blob = await resp.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = fileName
      a.click()
      URL.revokeObjectURL(url)
    } catch { /* silent */ }
  }, [])

  const handleToggleFav = useCallback((id: string) => {
    const convs = convRef.current.map(c => c.id === id ? { ...c, favorited: !c.favorited } : c)
    persist(convs)
  }, [persist])

  const handleSyncToCloud = useCallback(async () => {
    try {
      const convs = convRef.current
      if (convs.length === 0) return
      // Map to backend-compatible format with all required fields
      const payload = convs.map(c => ({
        id: c.id,
        title: c.title,
        messages: c.messages.map(m => ({
          role: m.role,
          content: m.content,
          ...(m.tokens ? { tokens: m.tokens } : {}),
        })),
        createdAt: c.createdAt,
        updatedAt: c.updatedAt,
        ...(c.favorited ? { favorited: c.favorited } : {}),
      }))
      const result = await userApi.syncConversations(payload)
      if (result) {
        const ver = typeof result === 'object' ? (result as any).version || (result as any).data?.version : null
        if (ver) {
          localStorage.setItem('ld_conv_server_version', String(ver))
        }
      }
    } catch { /* silent */ }
  }, [])

  const handleCloudRestore = useCallback(async () => {
    try {
      const result = await userApi.fetchConversations()
      const data = result as { conversations?: any[]; redisConversations?: any[]; version?: number }
      const serverConvs = data?.conversations || data?.redisConversations || (Array.isArray(result) ? result : [])

      if (!Array.isArray(serverConvs) || serverConvs.length === 0) {
        return
      }

      // Map server conversations to FrontendConversation format
      const mapped: FrontendConversation[] = serverConvs.map((c: any) => ({
        id: String(c.id || ''),
        title: c.title || t('新对话', 'New Chat'),
        messages: [], // 先置空，后续加载完整消息
        createdAt: c.createdAt || c.createTime || Date.now(),
        updatedAt: c.updatedAt || c.lastMessageTime || c.updatedAt || Date.now(),
        favorited: c.favorited || false,
      }))

      // 异步加载每个对话的完整消息（最多加载前 5 个对话，避免请求过多）
      const loadMessages = async () => {
        const conversationsToLoad = mapped.slice(0, 5)
        for (const conv of conversationsToLoad) {
          try {
            const msgResult = await userApi.getConversationMessages(conv.id, 50)
            const messages = (msgResult as any)?.messages || []
            conv.messages = messages.map((m: any) => ({
              role: m.role || 'user',
              content: m.content || '',
              id: m.id || Date.now(),
              ...(m.tokens ? { tokens: m.tokens } : {}),
            }))
          } catch (e) {
            console.warn('加载对话消息失败:', conv.id, e)
          }
        }
        persist([...mapped])
      }
      loadMessages()

      if (mapped.length > 0) {
        persist(mapped)
        if (mapped[0]?.id) saveCurrentConvId(mapped[0].id)
        if (data?.version) {
          localStorage.setItem('ld_conv_server_version', String(data.version))
        }
      }
    } catch { /* silent */ }
  }, [persist, saveCurrentConvId, t])

  // On mount: pull from server + version check
  useEffect(() => {
    if (!token) return
    const initLoader = async () => {
      try {
        // 1. Check server version
        const verResult = await userApi.getConversationVersion()
        const serverVersion = (verResult as any)?.version || (verResult as any)?.data?.version || 0
        const localVersion = parseInt(localStorage.getItem('ld_conv_server_version') || '0') || 0

        if (serverVersion > localVersion) {
          // Server has newer data → pull it
          const fetchResult = await userApi.fetchConversations()
          const data = fetchResult as { conversations?: any[]; redisConversations?: any[]; version?: number }
          const serverConvs = data?.conversations || data?.redisConversations || (Array.isArray(fetchResult) ? fetchResult as any[] : [])

          if (Array.isArray(serverConvs) && serverConvs.length > 0) {
            const mapped: FrontendConversation[] = serverConvs.map((c: any) => ({
              id: String(c.id || ''),
              title: c.title || t('新对话', 'New Chat'),
              messages: [], // 先置空，后续加载完整消息
              createdAt: c.createdAt || c.createTime || Date.now(),
              updatedAt: c.updatedAt || c.lastMessageTime || c.updatedAt || Date.now(),
              favorited: c.favorited || false,
            }))

            // 异步加载每个对话的完整消息（最多加载前 5 个对话，避免请求过多）
            const loadMessages = async () => {
              const conversationsToLoad = mapped.slice(0, 5)
              for (const conv of conversationsToLoad) {
                try {
                  const msgResult = await userApi.getConversationMessages(conv.id, 50)
                  const messages = (msgResult as any)?.messages || []
                  conv.messages = messages.map((m: any) => ({
                    role: m.role || 'user',
                    content: m.content || '',
                    id: m.id || Date.now(),
                    ...(m.tokens ? { tokens: m.tokens } : {}),
                  }))
                } catch (e) {
                  console.warn('加载对话消息失败:', conv.id, e)
                }
              }
              persist([...mapped])
            }
            loadMessages()

            persist(mapped)
            saveCurrentConvId(mapped[0]?.id || convRef.current[0]?.id || '')
          }

          if (data?.version) {
            localStorage.setItem('ld_conv_server_version', String(data.version))
          } else if (serverVersion > 0) {
            localStorage.setItem('ld_conv_server_version', String(serverVersion))
          }
        } else if (convRef.current.length > 0 && serverVersion === 0) {
          // No server data yet → push local to server
          const convs = convRef.current
          const payload = convs.map(c => ({
            id: c.id,
            title: c.title,
            messages: c.messages.map(m => ({ role: m.role, content: m.content })),
            createdAt: c.createdAt,
            updatedAt: c.updatedAt,
          }))
          const syncResult = await userApi.syncConversations(payload)
          const newVer = (syncResult as any)?.version || (syncResult as any)?.data?.version
          if (newVer) localStorage.setItem('ld_conv_server_version', String(newVer))
        }
      } catch { /* silent */ }
    }
    initLoader()
  }, [token])

  return (
    <div className="flex h-full w-full overflow-hidden relative" style={{ background: 'var(--bg-primary)' }}>
      <AnimatePresence>
        {mobileSidebarOpen && (
          <>
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.2 }}
              className="fixed inset-0 bg-black/60 z-[100] lg:hidden"
              onClick={() => setMobileSidebarOpen(false)}
            />
            <motion.div
              initial={{ x: '-100%' }}
              animate={{ x: 0 }}
              exit={{ x: '-100%' }}
              transition={{ type: 'spring', damping: 25, stiffness: 200 }}
              className="fixed left-0 top-0 bottom-0 z-[110] lg:hidden"
            >
              <ChatSidebar
                conversations={conversations}
                currentConvId={currentConvId}
                onSelectConv={(id) => { handleSelectConv(id); setMobileSidebarOpen(false) }}
                onNewConv={() => { handleNewConv(); setMobileSidebarOpen(false) }}
                onDeleteConv={handleDeleteConv}
                onRenameConv={handleRenameConv}
                onExportConv={handleExportConv}
                onToggleFav={handleToggleFav}
                onSyncToCloud={handleSyncToCloud}
                onCloudRestore={handleCloudRestore}
                onOpenMobileSidebar={() => setMobileSidebarOpen(false)}
                lang={lang}
                t={t}
                isMobile={true}
              />
            </motion.div>
          </>
        )}
      </AnimatePresence>

      <div className="hidden lg:flex flex-col border-r flex-shrink-0 transition-all duration-300" style={{ width: sidebarCollapsed ? '52px' : '280px', borderColor: 'var(--border-color)', background: 'var(--bg-secondary)' }}>
        <ChatSidebar
          conversations={conversations}
          currentConvId={currentConvId}
          onSelectConv={handleSelectConv}
          onNewConv={handleNewConv}
          onDeleteConv={handleDeleteConv}
          onRenameConv={handleRenameConv}
          onExportConv={handleExportConv}
          onToggleFav={handleToggleFav}
          onSyncToCloud={handleSyncToCloud}
          onCloudRestore={handleCloudRestore}
          lang={lang}
          t={t}
          collapsed={sidebarCollapsed}
          onToggleCollapsed={() => {
            const next = !sidebarCollapsed
            setSidebarCollapsed(next)
            try { localStorage.setItem('ld_sidebar_collapsed', String(next)) } catch {}
          }}
        />
      </div>

      <main className="flex-1 flex flex-col min-w-0 relative">
        <div className="flex items-center justify-between px-4 h-12 border-b flex-shrink-0" style={{ borderColor: 'var(--border-color)', background: 'var(--bg-primary)' }}>
          <div className="flex items-center gap-2 min-w-0">
            <button
              onClick={() => setMobileSidebarOpen(true)}
              className="lg:hidden p-1.5 rounded-lg -ml-1"
              style={{ color: 'var(--text-dim)' }}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>
            </button>
            <h2 className="text-sm font-medium truncate" style={{ color: 'var(--text-primary)' }}>
              {currentConv.title || t('AI 对话', 'AI Chat')}
            </h2>
          </div>

          <div className="relative">
            <button
              onClick={() => setShowModelSelector(!showModelSelector)}
              className="flex items-center gap-1.5 px-2.5 py-1.5 text-xs rounded-lg border transition-colors"
              style={{ borderColor: 'var(--border-color)', color: 'var(--text-primary)', background: 'var(--bg-surface)' }}
            >
              <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ background: 'var(--accent-purple)' }} />
              <span className="truncate max-w-[120px]">{currentModelLabel}</span>
              <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="6 9 12 15 18 9"/></svg>
            </button>

            {showModelSelector && (
              <>
                <div className="fixed inset-0 z-[80]" onClick={() => setShowModelSelector(false)} />
                <div className="absolute right-0 top-full mt-1 w-64 max-h-80 overflow-y-auto rounded-xl border shadow-2xl z-[90]" style={{ borderColor: 'var(--border-color)', background: 'var(--bg-primary)' }}>
                  <div className="p-1.5">
                    {availableModels.length === 0 ? (
                      <div className="text-center py-4 text-xs" style={{ color: 'var(--text-dim)' }}>{t('加载模型中...', 'Loading...')}</div>
                    ) : availableModels.map((m) => {
                      const modelCode = m.modelCode || m.displayName
                      const isLocked = !m.accessible
                      return (
                        <button
                          key={m.modelId}
                          onClick={() => { if (isLocked) return; setModel(modelCode); localStorage.setItem('ld_chat_model', modelCode); latestModelRef.current = modelCode; setShowModelSelector(false) }}
                          disabled={isLocked}
                          className={`w-full text-left px-3 py-2 rounded-lg transition-all ${modelCode === model ? 'border' : ''} ${isLocked ? 'opacity-50 cursor-not-allowed' : ''}`}
                          style={{
                            background: modelCode === model ? 'var(--accent-purple)15' : 'transparent',
                            borderColor: modelCode === model ? 'var(--accent-purple)30' : 'transparent',
                          }}
                        >
                          <div className="flex items-center justify-between">
                            <div className="min-w-0 flex-1 mr-2">
                              <div className="flex items-center gap-1.5">
                                <span className="text-[11px] sm:text-xs font-medium truncate" style={{ color: 'var(--text-primary)' }}>{m.displayName}</span>
                                {isLocked && (
                                  <span className="text-[9px] px-1 rounded" style={{ background: 'rgba(245,158,11,0.15)', color: '#F59E0B' }}>
                                    {m.requiredLevelName || t('需升级', 'Upgrade')}
                                  </span>
                                )}
                              </div>
                              <div className="text-[9px] sm:text-[10px] mt-0.5" style={{ color: 'var(--text-dim)' }}>
                                {m.provider || t('未知', 'Unknown')}{m.fixedPoints > 0 ? <span style={{ color: 'var(--accent-purple)' }}> · {m.fixedPoints}点/次</span> : null}
                              </div>
                              {isLocked && m.upgradeHint && (
                                <div className="text-[9px] mt-0.5" style={{ color: '#F59E0B' }}>{m.upgradeHint}</div>
                              )}
                            </div>
                            {modelCode === model && (
                              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--accent-purple)" strokeWidth="3" className="flex-shrink-0">
                                <polyline points="20 6 9 17 4 12" />
                              </svg>
                            )}
                          </div>
                        </button>
                      )
                    })}
                  </div>
                </div>
              </>
            )}
          </div>
        </div>

        <div className="flex-1 overflow-y-auto" id="chat-messages-scroll">
          {currentConv.messages.length === 0 ? (
            <WelcomeScreen
              model={currentModelLabel}
              lang={lang}
              onSuggestionClick={(text) => handleSend(text, [])}
            />
          ) : (
            <ChatMessages
              messages={currentConv.messages}
              isStreaming={isStreaming}
              tokenStats={null}
              fileProcessStatus={fileProcessStatus}
              lang={lang}
              t={t}
            />
          )}
        </div>

        <ChatInput
          onSend={handleSend}
          onStop={stopStreaming}
          isStreaming={isStreaming}
          disabled={availableModels.length === 0}
          placeholder={
            fileProcessStatus
              ? fileProcessStatus
              : lang === 'zh'
                ? '输入消息，Enter 发送，Shift+Enter 换行'
                : 'Type a message, Enter to send, Shift+Enter for new line'
          }
          lang={lang}
          t={t}
        />
      </main>
    </div>
  )
}
