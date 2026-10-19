import { useState, useEffect, useRef, useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import ChatSidebar from './ChatSidebar'
import ChatMessages from './ChatMessages'
import ChatInput, { isImageFile, type UploadedFile } from './ChatInput'
import WelcomeScreen from './WelcomeScreen'
import ChatSettingsPanel from './ChatSettingsPanel'
import ConfirmDialog from '@/components/ConfirmDialog'
import { modelApi, chatApi, chatFileApi, aiDocApi, userApi, conversationApi } from '@/services/api'
import { useAppStore } from '@/stores/appStore'
import { useI18nStore } from '@/stores/i18nStore'
import type { FrontendConversation, FrontendMessage, AvailableModel, SearchEngine, SearchInfo } from '@/types'
import { canUseChatModel, selectChatModel } from '@/utils/modelAvailability'

function genTitle(content: string) {
  return content.length > 30 ? content.slice(0, 30) + '...' : content
}

/**
 * 从后端消息的 metadata 字段解析文件元数据，恢复到前端 FrontendMessage.files
 * metadata 格式: {"files": [{fileId, fileName, fileSize, fileType, ossUrl, uploadTime, expireTime}]}
 * 兼容直接是 files 数组的旧格式
 */
function parseFilesFromMetadata(metadata?: string): FrontendMessage['files'] | undefined {
  if (!metadata) return undefined
  try {
    const parsed = JSON.parse(metadata)
    // 兼容两种格式: {files: [...]} 或 直接 [...]
    const files = Array.isArray(parsed) ? parsed : parsed?.files
    if (Array.isArray(files) && files.length > 0) {
      return files.map((f: any) => ({
        fileId: String(f.fileId || ''),
        fileName: String(f.fileName || ''),
        fileSize: Number(f.fileSize || 0),
        fileType: String(f.fileType || ''),
        ossUrl: f.ossUrl || '',
        uploadTime: f.uploadTime ? Number(f.uploadTime) : undefined,
        expireTime: f.expireTime ? Number(f.expireTime) : undefined,
      }))
    }
  } catch {}
  return undefined
}

type ConversationHistoryState = {
  loaded: boolean
  loading: boolean
  hasMore: boolean
  nextCursorId: number | null
  nextCursorTime: string | null
}

const EMPTY_HISTORY_STATE: ConversationHistoryState = {
  loaded: false,
  loading: false,
  hasMore: false,
  nextCursorId: null,
  nextCursorTime: null,
}

function cursorIdOf(value: unknown): number | null {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

function mapBackendMessages(messages: any[]): FrontendMessage[] {
  if (!Array.isArray(messages)) return []
  return messages.map((message, index) => {
    const files = parseFilesFromMetadata(message?.metadata)
    const id = cursorIdOf(message?.id) || Date.now() + index
    return {
      role: message?.role || 'user',
      content: message?.content || '',
      id,
      ...(message?.createTime ? { createTime: String(message.createTime) } : {}),
      ...(message?.tokens != null ? { tokens: String(message.tokens) } : {}),
      ...(message?.pointsConsumed != null ? { pointsConsumed: Number(message.pointsConsumed) } : {}),
      ...(message?.thinkingTime != null ? { thinkingTime: Number(message.thinkingTime) } : {}),
      ...(message?.searchInfo ? { searchInfo: message.searchInfo } : {}),
      ...(files ? { files } : {}),
    } as FrontendMessage
  })
}

function mergeMessages(existing: FrontendMessage[], incoming: FrontendMessage[]): FrontendMessage[] {
  const merged = [...existing]
  const matchedExisting = new Set<number>()

  for (const message of incoming) {
    // 服务端消息与本地流式消息的ID来源不同。先按服务端ID+内容匹配，
    // 再把同角色同内容的本地临时消息替换成服务端版本，避免刷新后重复一问一答。
    let matchIndex = merged.findIndex((candidate, index) => {
      if (matchedExisting.has(index) || candidate.role !== message.role) return false
      const sameServerMessage = candidate.id != null && message.id != null
        && candidate.id === message.id
        && candidate.content === message.content
      const sameLocalMessage = candidate.content === message.content
        && (candidate.localOnly === true || !candidate.createTime)
      return sameServerMessage || sameLocalMessage
    })

    if (matchIndex >= 0) {
      matchedExisting.add(matchIndex)
      const previous = merged[matchIndex]
      merged[matchIndex] = {
        ...previous,
        ...message,
        files: message.files?.length ? message.files : previous.files,
        searchInfo: message.searchInfo ?? previous.searchInfo,
        feedback: previous.feedback,
      }
    } else {
      merged.push(message)
    }
  }

  const timestampOf = (message: FrontendMessage) => {
    if (!message.createTime) return Number.NaN
    const normalized = message.createTime.includes(' ') && !message.createTime.includes('T')
      ? message.createTime.replace(' ', 'T')
      : message.createTime
    const timestamp = Date.parse(normalized)
    return Number.isFinite(timestamp) ? timestamp : Number.NaN
  }

  return merged.sort((a, b) => {
    const aTime = timestampOf(a)
    const bTime = timestampOf(b)
    if (Number.isFinite(aTime) && Number.isFinite(bTime) && aTime !== bTime) return aTime - bTime
    return (a.id || 0) - (b.id || 0)
  })
}

const STORAGE_KEY = 'ld_conversations'
const CURRENT_CONV_KEY = 'ld_current_conv'
const SERVER_VERSION_KEY = 'ld_conv_server_version'
const DEFAULT_MODEL = 'deepseek-v4-flash'
const DEFAULT_VISION_MODEL = 'deepseek-v4-flash-vision-exp'
const DEFAULT_MAX_TOKENS = 8192
const MIN_MAX_TOKENS = 256
const MAX_MAX_TOKENS = 32768
const MARKDOWN_SYSTEM_PROMPT = '你是凌点 AI 的智能助手。回答代码、技术文档或复杂逻辑时请使用规范 Markdown。标题标记与标题文字之间必须有空格，标题、列表、表格和代码块之间保留空行；代码块必须使用成对的 ``` 包围，语言标记后换行，结束围栏必须单独占一行并完整闭合，绝不能把后续标题、正文或请求示例放进代码块。HTTP 接口清单中每个请求方法和路径（GET/POST/PUT/PATCH/DELETE 等）必须独占一行，每个请求头独占一行，代码块结束后再输出下一个 Markdown 标题；不要把多个标题、接口或代码块挤在同一行，也不要省略文末内容。简单问答自然回复即可。'
const LS_DEBOUNCE_MS = 800
// 已删除会话ID列表（防止刷新后从服务器恢复已删除的会话）
const DELETED_KEY = 'ld_deleted_conv_ids'
const DELETED_TTL_MS = 24 * 60 * 60 * 1000 // 24小时过期

function normalizeMaxTokens(value: number): number {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed <= 0) return DEFAULT_MAX_TOKENS
  const stepped = Math.round(parsed / 256) * 256
  return Math.min(MAX_MAX_TOKENS, Math.max(MIN_MAX_TOKENS, stepped))
}

function loadMaxTokens(): number {
  try {
    const stored = Number(localStorage.getItem('ld_chat_max_tokens'))
    // 2048 was the old application default. Promote that legacy value so
    // existing users do not keep getting unexpectedly short completions;
    // all other user-selected values are preserved within the new bounds.
    if (!Number.isFinite(stored) || stored <= 0 || stored === 2048) return DEFAULT_MAX_TOKENS
    return normalizeMaxTokens(stored)
  } catch {
    return DEFAULT_MAX_TOKENS
  }
}

function getStoredUserId(): number | null {
  try {
    const value = Number(localStorage.getItem('lingdian_uid'))
    return Number.isFinite(value) && value > 0 ? value : null
  } catch {
    return null
  }
}

function getScopedStorageKey(base: string, userId: number | null) {
  return `${base}_${userId && userId > 0 ? userId : 'anonymous'}`
}

function createEmptyConversation(t: (zh: string, en: string) => string): FrontendConversation {
  const now = Date.now()
  // 草稿不能使用纯数字 ID，否则首条消息会把它误当作已有的后端会话。
  const id = `local-${globalThis.crypto?.randomUUID?.() ?? `${now}-${Math.random().toString(16).slice(2)}`}`
  return { id, title: t('新对话', 'New Chat'), messages: [], createdAt: now, updatedAt: now }
}

function loadStoredConversations(userId: number | null, t: (zh: string, en: string) => string): FrontendConversation[] {
  try {
    const raw = localStorage.getItem(getScopedStorageKey(STORAGE_KEY, userId))
    const parsed = raw ? JSON.parse(raw) : null
    if (Array.isArray(parsed)) {
      const valid = parsed.filter((item): item is FrontendConversation => Boolean(item && item.id && Array.isArray(item.messages)))
      if (valid.length > 0) return valid
    }
  } catch {}
  return [createEmptyConversation(t)]
}

function loadStoredCurrentConversation(userId: number | null, conversations: FrontendConversation[]) {
  try {
    const saved = localStorage.getItem(getScopedStorageKey(CURRENT_CONV_KEY, userId))
    if (saved && conversations.some(conversation => conversation.id === saved)) return saved
  } catch {}
  return conversations[0]?.id || ''
}

// 记录已删除的会话ID（刷新时 initLoader 会跳过这些ID，不从服务器恢复、不推送回服务器）
function markConvDeleted(id: string, userId: number | null) {
  try {
    const raw = localStorage.getItem(getScopedStorageKey(DELETED_KEY, userId))
    const list: Array<{ id: string; ts: number }> = raw ? JSON.parse(raw) : []
    if (!list.find(item => item.id === id)) list.push({ id, ts: Date.now() })
    // 清理过期记录
    const cutoff = Date.now() - DELETED_TTL_MS
    const cleaned = list.filter(item => item.ts > cutoff)
    localStorage.setItem(getScopedStorageKey(DELETED_KEY, userId), JSON.stringify(cleaned))
  } catch {}
}

// 获取已删除的会话ID集合（自动过滤过期记录）
function getDeletedConvIds(userId: number | null): Set<string> {
  try {
    const raw = localStorage.getItem(getScopedStorageKey(DELETED_KEY, userId))
    if (!raw) return new Set()
    const list: Array<{ id: string; ts: number }> = JSON.parse(raw)
    const cutoff = Date.now() - DELETED_TTL_MS
    return new Set(list.filter(item => item.ts > cutoff).map(item => item.id))
  } catch {
    return new Set()
  }
}

function toCloudPayload(conversations: FrontendConversation[], userId: number | null) {
  const deletedIds = getDeletedConvIds(userId)
  return conversations
    // 首轮成功后会用服务端返回的数字 ID 替换草稿 ID，再同步到云端。
    .filter(conversation => /^\d+$/.test(conversation.id) && !deletedIds.has(conversation.id))
    .map(conversation => ({
      id: conversation.id,
      title: conversation.title,
      messages: conversation.messages.map(message => ({
        role: message.role,
        content: message.content,
        ...(message.tokens ? { tokens: message.tokens } : {}),
        ...(message.pointsConsumed != null ? { pointsConsumed: message.pointsConsumed } : {}),
        ...(message.thinkingTime != null ? { thinkingTime: message.thinkingTime } : {}),
        ...(message.files?.length ? { files: message.files } : {}),
        ...(message.searchInfo ? { searchInfo: message.searchInfo } : {}),
      })),
      createdAt: conversation.createdAt,
      updatedAt: conversation.updatedAt,
      ...(conversation.favorited ? { favorited: true } : {}),
    }))
}

export default function ChatPage() {
  const navigate = useNavigate()
  const { token, user, settingsPanelOpen, closeSettingsPanel, toggleSettingsPanel } = useAppStore()
  const { t, lang } = useI18nStore()
  const activeUserId = user?.id ?? getStoredUserId()
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => {
    try { return localStorage.getItem('ld_sidebar_collapsed') === 'true' } catch { return false }
  })

  const [conversations, setConversations] = useState<FrontendConversation[]>(() => loadStoredConversations(activeUserId, t))

  const [currentConvId, setCurrentConvId] = useState<string>(() => {
    return loadStoredCurrentConversation(activeUserId, conversations)
  })

  const [model, setModel] = useState<string>(() => localStorage.getItem('ld_chat_model') || DEFAULT_MODEL)
  const [availableModels, setAvailableModels] = useState<AvailableModel[]>([])
  const [showModelSelector, setShowModelSelector] = useState(false)
  const [deleteConvId, setDeleteConvId] = useState<string | null>(null)
  const [searchEngine, setSearchEngine] = useState<SearchEngine>(
    () => (localStorage.getItem('ld_chat_search_engine') as SearchEngine) || 'auto'
  )
  const [temperature, setTemperature] = useState<number>(
    () => Number(localStorage.getItem('ld_chat_temperature')) || 0.7
  )
  const [maxTokens, setMaxTokens] = useState<number>(loadMaxTokens)
  const [isStreaming, setIsStreaming] = useState(false)
  const [fileProcessStatus, setFileProcessStatus] = useState('')
  const [searchInfo, setSearchInfo] = useState<SearchInfo | null>(null)
  // searchInfo 的 ref 副本：onDone 回调中需要读取最新值保存到消息，但 state 是异步的，用 ref 保证拿到最新
  const searchInfoRef = useRef<typeof searchInfo>(null)
  searchInfoRef.current = searchInfo
  // 思考开始时间戳：onDone 时计算耗时保存到消息（让用户完成后也能看到"思考耗时 Xs"）
  const thinkingStartRef = useRef<number>(0)

  const convRef = useRef(conversations)
  convRef.current = conversations
  const isStreamingRef = useRef(false)
  const streamControllerRef = useRef<AbortController | null>(null)
  // 每次请求一个单调递增的代号。旧请求即使在 abort 后又收到数据，也不能
  // 再写入当前会话、清空新请求状态或覆盖新模型的输出。
  const streamSequenceRef = useRef(0)
  // 记录当前流式请求所属的会话ID，防止切换对话后旧流式的动效串到新对话
  const streamingConvIdRef = useRef<string>('')
  // 当前显示会话ID的 ref 副本：onSearch 闭包中读取最新值，避免用旧 currentConvId 导致动效串台
  // （切换对话后 onSearch 闭包中的 currentConvId 仍是旧值，必须用 ref 才能拿到最新值）
  const currentConvIdRef = useRef<string>(currentConvId)
  currentConvIdRef.current = currentConvId
  const mountedRef = useRef(true)
  const latestModelRef = useRef(model)
  latestModelRef.current = model
  const activeUserIdRef = useRef<number | null>(activeUserId)
  const storageScopeKeyRef = useRef(getScopedStorageKey(STORAGE_KEY, activeUserId))
  const cloudSyncTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const cloudSyncPendingRef = useRef<FrontendConversation[] | null>(null)
  const cloudSyncInFlightRef = useRef(false)
  const [historyState, setHistoryState] = useState<Record<string, ConversationHistoryState>>({})
  const historyStateRef = useRef<Record<string, ConversationHistoryState>>({})
  const historyLoadInFlightRef = useRef(new Set<string>())
  const loadMoreRef = useRef<(() => Promise<void>) | undefined>(undefined)
  const [cloudSyncState, setCloudSyncState] = useState<'idle' | 'syncing' | 'synced' | 'error'>('idle')
  const [cloudSyncAt, setCloudSyncAt] = useState<number | null>(null)

  const currentConv = useMemo(() =>
    conversations.find((c) => c.id === currentConvId) || {
      id: currentConvId,
      title: t('新对话', 'New Chat'),
      messages: [],
      createdAt: Date.now(),
      updatedAt: Date.now(),
    },
  [conversations, currentConvId, t])

  const currentHistoryState = historyState[currentConvId] || EMPTY_HISTORY_STATE

  const currentModelLabel = useMemo(() => {
    const selected = availableModels.find(m => m.displayName === model || m.modelCode === model)
    return selected?.displayName || (availableModels.length === 0 && model
      ? model
      : t('暂无可用模型', 'No available model'))
  }, [availableModels, model, t])

  const hasUsableChatModel = useMemo(
    () => availableModels.some(canUseChatModel),
    [availableModels],
  )

  const selectedModelCode = useMemo(() => {
    const selected = availableModels.find(m => m.displayName === model || m.modelCode === model)
    return selected?.modelCode || model || DEFAULT_MODEL
  }, [availableModels, model])

  const writeLocalConversations = useCallback((updated: FrontendConversation[]) => {
    try { localStorage.setItem(storageScopeKeyRef.current, JSON.stringify(updated)) } catch {}
  }, [])

  const runCloudSync = useCallback(async () => {
    const snapshot = cloudSyncPendingRef.current
    const userId = activeUserIdRef.current
    if (!snapshot || !userId || !localStorage.getItem('lingdian_token') || cloudSyncInFlightRef.current) return

    cloudSyncPendingRef.current = null
    cloudSyncInFlightRef.current = true
    setCloudSyncState('syncing')
    try {
      const payload = toCloudPayload(snapshot, userId)
      if (payload.length > 0) {
        const result = await userApi.syncConversations(payload)
        if (mountedRef.current && activeUserIdRef.current === userId) {
          const version = typeof result === 'object' ? (result as any)?.version || (result as any)?.data?.version : null
          if (version) localStorage.setItem(getScopedStorageKey(SERVER_VERSION_KEY, userId), String(version))
          setCloudSyncState('synced')
          setCloudSyncAt(Date.now())
        }
      }
    } catch {
      if (mountedRef.current && activeUserIdRef.current === userId) setCloudSyncState('error')
    } finally {
      cloudSyncInFlightRef.current = false
      if (cloudSyncPendingRef.current && mountedRef.current) {
        if (cloudSyncTimerRef.current) clearTimeout(cloudSyncTimerRef.current)
        cloudSyncTimerRef.current = setTimeout(() => { void runCloudSync() }, 250)
      }
    }
  }, [])

  const scheduleCloudSync = useCallback((snapshot: FrontendConversation[], delay = LS_DEBOUNCE_MS) => {
    if (!activeUserIdRef.current || !localStorage.getItem('lingdian_token')) return
    cloudSyncPendingRef.current = snapshot
    if (cloudSyncTimerRef.current) clearTimeout(cloudSyncTimerRef.current)
    cloudSyncTimerRef.current = setTimeout(() => { void runCloudSync() }, delay)
  }, [runCloudSync])

  const persist = useCallback((updated: FrontendConversation[], skipStateUpdate = false, skipCloudSync = false) => {
    if (!skipStateUpdate) {
      setConversations(updated)
    }
    convRef.current = updated
    writeLocalConversations(updated)
    // 历史加载是服务端到本地的合并，不能把只加载了最近一页的快照
    // 反向同步回云端，否则会用不完整历史覆盖服务端完整数据。
    if (!skipCloudSync) scheduleCloudSync(updated)
  }, [scheduleCloudSync, writeLocalConversations])

  const patchHistoryState = useCallback((conversationId: string, patch: Partial<ConversationHistoryState>) => {
    const next = {
      ...historyStateRef.current,
      [conversationId]: {
        ...(historyStateRef.current[conversationId] || EMPTY_HISTORY_STATE),
        ...patch,
      },
    }
    historyStateRef.current = next
    setHistoryState(next)
  }, [])

  const loadConversationMessages = useCallback(async (conversationId: string, force = false) => {
    const loaderUserId = activeUserIdRef.current
    if (!/^\d+$/.test(conversationId) || !loaderUserId || !localStorage.getItem('lingdian_token')) return
    const currentState = historyStateRef.current[conversationId] || EMPTY_HISTORY_STATE
    if (currentState.loading || (!force && currentState.loaded)) return
    if (historyLoadInFlightRef.current.has(conversationId)) return

    historyLoadInFlightRef.current.add(conversationId)
    patchHistoryState(conversationId, { loading: true })
    try {
      const result = await userApi.getConversationMessages(conversationId, 50)
      if (!mountedRef.current || activeUserIdRef.current !== loaderUserId) return
      const incoming = mapBackendMessages(result?.messages || [])
      const updated = convRef.current.map(conversation => conversation.id === conversationId
        ? { ...conversation, messages: mergeMessages(conversation.messages, incoming) }
        : conversation)
      persist(updated, false, true)
      patchHistoryState(conversationId, {
        loaded: true,
        hasMore: Boolean(result?.hasMore),
        nextCursorId: cursorIdOf(result?.nextCursorId ?? (result as any)?.nextCursor),
        nextCursorTime: result?.nextCursorTime || null,
      })
    } catch (error) {
      console.warn('[ChatPage] 加载会话消息失败:', conversationId, error)
    } finally {
      historyLoadInFlightRef.current.delete(conversationId)
      if (activeUserIdRef.current === loaderUserId) patchHistoryState(conversationId, { loading: false })
    }
  }, [patchHistoryState, persist])

  const loadMoreMessages = useCallback(async () => {
    const conversationId = currentConvIdRef.current
    const loaderUserId = activeUserIdRef.current
    if (!loaderUserId) return
    const currentState = historyStateRef.current[conversationId] || EMPTY_HISTORY_STATE
    const lastId = currentState.nextCursorId
    const lastCreateTime = currentState.nextCursorTime
    if (!/^\d+$/.test(conversationId) || !currentState.hasMore || (!lastId && !lastCreateTime)) return
    if (currentState.loading || historyLoadInFlightRef.current.has(conversationId)) return

    historyLoadInFlightRef.current.add(conversationId)
    patchHistoryState(conversationId, { loading: true })
    try {
      const result = await conversationApi.getMessagesByCursor(
        Number(conversationId), lastId || undefined, 50, lastCreateTime || undefined)
      if (!mountedRef.current || activeUserIdRef.current !== loaderUserId) return
      const olderMessages = mapBackendMessages(result?.messages || [])
      const updated = convRef.current.map(conversation => conversation.id === conversationId
        ? { ...conversation, messages: mergeMessages(conversation.messages, olderMessages), updatedAt: conversation.updatedAt }
        : conversation)
      persist(updated, false, true)
      patchHistoryState(conversationId, {
        loaded: true,
        hasMore: Boolean(result?.hasMore),
        nextCursorId: cursorIdOf(result?.nextCursorId ?? (result as any)?.nextCursor),
        nextCursorTime: result?.nextCursorTime || null,
      })
    } catch (error) {
      console.warn('[ChatPage] 加载更多历史消息失败:', conversationId, error)
    } finally {
      historyLoadInFlightRef.current.delete(conversationId)
      if (activeUserIdRef.current === loaderUserId) patchHistoryState(conversationId, { loading: false })
    }
  }, [patchHistoryState, persist])

  loadMoreRef.current = loadMoreMessages

  const saveCurrentConvId = useCallback((id: string) => {
    setCurrentConvId(id)
    try { localStorage.setItem(getScopedStorageKey(CURRENT_CONV_KEY, activeUserIdRef.current), id) } catch {}
  }, [])

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      if (cloudSyncTimerRef.current) clearTimeout(cloudSyncTimerRef.current)
    }
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
      const selected = selectChatModel(allSorted, latestModelRef.current)
      if (!selected) {
        setModel('')
        latestModelRef.current = ''
        localStorage.removeItem('ld_chat_model')
        return
      }
      if (selected.modelCode !== latestModelRef.current && selected.displayName !== latestModelRef.current) {
        const selectedCode = selected.modelCode || selected.displayName || DEFAULT_MODEL
        setModel(selectedCode)
        latestModelRef.current = selectedCode
        localStorage.setItem('ld_chat_model', selectedCode)
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

  // 持久化设置到 localStorage
  useEffect(() => { localStorage.setItem('ld_chat_search_engine', searchEngine) }, [searchEngine])
  useEffect(() => { localStorage.setItem('ld_chat_temperature', String(temperature)) }, [temperature])
  useEffect(() => { localStorage.setItem('ld_chat_max_tokens', String(maxTokens)) }, [maxTokens])

  const stopStreaming = useCallback(() => {
    const activeConversationId = streamingConvIdRef.current
    // 先让所有旧回调失效，再 abort。浏览器/代理可能在 abort 后仍把
    // 已经排队的最后一个 SSE chunk 交给回调。
    streamSequenceRef.current += 1
    streamControllerRef.current?.abort()
    streamControllerRef.current = null
    if (activeConversationId) {
      const currentConvs = convRef.current
      const conversation = currentConvs.find(item => item.id === activeConversationId)
      const messages = conversation ? [...conversation.messages] : []
      const last = messages[messages.length - 1]
      if (last?.role === 'assistant' && last.streamStatus === 'streaming') {
        messages[messages.length - 1] = { ...last, streamStatus: 'cancelled' }
        persist(currentConvs.map(item => item.id === activeConversationId
          ? { ...item, messages, updatedAt: Date.now() }
          : item))
      }
    }
    streamingConvIdRef.current = ''
    isStreamingRef.current = false
    setIsStreaming(false)
    setFileProcessStatus('')
  }, [persist])

  // 登录账号发生变化时，立即切换到该账号自己的本地会话空间，避免沿用上一个账号的历史消息。
  useEffect(() => {
    const nextScopeKey = getScopedStorageKey(STORAGE_KEY, activeUserId)
    if (activeUserIdRef.current === activeUserId && storageScopeKeyRef.current === nextScopeKey) return

    stopStreaming()
    if (cloudSyncTimerRef.current) clearTimeout(cloudSyncTimerRef.current)
    cloudSyncPendingRef.current = null
    activeUserIdRef.current = activeUserId
    storageScopeKeyRef.current = nextScopeKey

    const nextConversations = loadStoredConversations(activeUserId, t)
    const nextCurrentConvId = loadStoredCurrentConversation(activeUserId, nextConversations)
    convRef.current = nextConversations
    currentConvIdRef.current = nextCurrentConvId
    setConversations(nextConversations)
    setCurrentConvId(nextCurrentConvId)
    setSearchInfo(null)
    searchInfoRef.current = null
    setFileProcessStatus('')
    setCloudSyncState('idle')
    setCloudSyncAt(null)
    historyLoadInFlightRef.current.clear()
    historyStateRef.current = {}
    setHistoryState({})
  }, [activeUserId, stopStreaming, t])

  const handleSend = useCallback(async (text: string, uploadedFiles: UploadedFile[]) => {
    if (!text.trim() && uploadedFiles.length === 0) return
    if (isStreamingRef.current) return
    setSearchInfo(null)
    const streamConvId = currentConvIdRef.current
    const firstUsableModel = availableModels.find(canUseChatModel)
    if (!firstUsableModel) {
      const hasConfiguredModel = availableModels.some(model => model.chatEnabled)
      setFileProcessStatus(hasConfiguredModel
        ? t('当前账号没有可用的已接入模型，请检查会员等级或联系管理员。', 'No connected model is available to this account. Check access level or contact an administrator.')
        : t('模型服务尚未接入，请联系管理员完成上游配置。', 'No model service is connected yet. Contact an administrator to configure an upstream.'))
      return
    }

    let selectedModel = selectChatModel(availableModels, selectedModelCode)
    if (!selectedModel) return
    let requestModelCode = selectedModel.modelCode || selectedModel.displayName
    if (selectedModelCode !== requestModelCode && selectedModelCode !== selectedModel.displayName) {
      setModel(requestModelCode)
      latestModelRef.current = requestModelCode
      localStorage.setItem('ld_chat_model', requestModelCode)
    }

    // 图片必须交给视觉模型；如果用户仍停留在普通文本模型，自动切换并同步选择器。
    const hasImageFiles = uploadedFiles.some(isImageFile)
    if (hasImageFiles) {
      if (!selectedModel || Number(selectedModel.supportsVision) !== 1) {
        const visionModel = availableModels.find(m =>
          canUseChatModel(m) && m.modelCode === DEFAULT_VISION_MODEL && Number(m.supportsVision) === 1
        ) || availableModels.find(m => canUseChatModel(m) && Number(m.supportsVision) === 1)

        if (!visionModel) {
          setFileProcessStatus(t('当前账号没有已接入且可用的视觉模型，请检查会员等级或联系管理员。', 'No connected vision model is available to this account. Check access level or contact an administrator.'))
          return
        }

        selectedModel = visionModel
        requestModelCode = visionModel.modelCode
        setModel(requestModelCode)
        latestModelRef.current = requestModelCode
        localStorage.setItem('ld_chat_model', requestModelCode)
      }
    }

    if (!canUseChatModel(selectedModel)) return

    const conv = convRef.current.find((c) => c.id === streamConvId)
    if (!conv) return

    const fileUrls = uploadedFiles.filter(f => f.url).map(f => f.url!)

    // 把文件信息附加到用户消息上，让消息上方的文件卡片可持久化显示（含下载链接）
    const filesMeta = uploadedFiles
      .filter(f => f.fileId || f.url)
      .map(f => ({
        fileId: f.fileId || '',
        fileName: f.name,
        fileSize: f.size,
        fileType: f.name.split('.').pop() || '',
        ossUrl: f.url || '',
        uploadTime: Date.now(),
      }))

    const userMsg: FrontendMessage = {
      role: 'user',
      content: text.trim(),
      id: Date.now(),
      localOnly: true,
      createTime: new Date().toISOString(),
      ...(filesMeta.length > 0 ? { files: filesMeta } : {}),
    }
    const idempotencyKey = globalThis.crypto?.randomUUID?.()
      ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`
    const assistantMsg: FrontendMessage = {
      role: 'assistant',
      content: '',
      id: Date.now() + 1,
      localOnly: true,
      createTime: new Date().toISOString(),
      streamStatus: 'streaming',
    }

    const updatedConv: FrontendConversation = {
      ...conv,
      title: conv.messages.length === 0 ? genTitle(text) : conv.title,
      messages: [...conv.messages, userMsg, assistantMsg],
      updatedAt: Date.now(),
    }

    const updatedConvs = convRef.current.map((c) => (c.id === streamConvId ? updatedConv : c))
    persist(updatedConvs)
    setFileProcessStatus('')

    streamControllerRef.current?.abort()
    const controller = new AbortController()
    streamControllerRef.current = controller
    const requestSequence = streamSequenceRef.current + 1
    streamSequenceRef.current = requestSequence
    const isActiveRequest = () => mountedRef.current && streamSequenceRef.current === requestSequence
    isStreamingRef.current = true
    // 关键：在闭包中捕获发起流式时的会话ID和请求序号，用于隔离旧流
    // 的迟到回调，避免切换会话或模型后发生串台。
    streamingConvIdRef.current = streamConvId
    thinkingStartRef.current = Date.now()  // 记录思考开始时间
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
              if (!isActiveRequest()) return
              // 防止动效串台：只有当前显示的对话 === 发起流式的对话时才更新文件处理状态
              if (streamConvId !== currentConvIdRef.current) return
              if (stage === 'reading_files') {
                setFileProcessStatus(t('正在读取文件内容...', 'Reading file...'))
              } else if (stage === 'processing') {
                setFileProcessStatus(t('正在与AI对话...', 'Chatting with AI...'))
              } else {
                setFileProcessStatus(message || stage || '')
              }
            },
            onSearch: (stage: string | undefined, data: SearchInfo) => {
              if (!isActiveRequest() || streamConvId !== currentConvIdRef.current) return
              const nextSearchInfo: SearchInfo = {
                query: data.query || '',
                results: stage === 'results' ? (data.results || []) : [],
                searching: stage === 'searching',
                stage: stage === 'searching' || stage === 'results' || stage === 'empty' ? stage : 'empty',
                engine: data.engine,
              }
              searchInfoRef.current = nextSearchInfo
              setSearchInfo(nextSearchInfo)
              setFileProcessStatus(stage === 'searching' ? t('正在搜索...', 'Searching...') : '')
            },
            onContent: (chunk) => {
              if (!isActiveRequest()) return
              // 修复竞态丢字：必须同步更新 convRef.current（与无文件分支 onMessage 一致）。
              // 原来用 queueMicrotask 延迟更新，一次 read 批量到达的多个 chunk 会连续读到
              // 同一个陈旧的 convRef.current，每个都基于旧 content 计算，导致每个 SSE 块
              // 只保留最后一个 chunk，表现为 AI 回复"均匀缺字"。
              const currentConvs = convRef.current
              const c = currentConvs.find(cv => cv.id === streamConvId)
              if (!c) return
              const msgs = [...c.messages]
              const lastMsg = msgs[msgs.length - 1]
              if (lastMsg && lastMsg.role === 'assistant') {
                msgs[msgs.length - 1] = { ...lastMsg, content: lastMsg.content + chunk }
              }
              const newConvs = currentConvs.map(cv =>
                cv.id === streamConvId ? { ...cv, messages: msgs, updatedAt: Date.now() } : cv
              )
              setConversations(newConvs)
              convRef.current = newConvs
              writeLocalConversations(newConvs)
            },
            onDone: (msg, conversationId?, inputTokens?, outputTokens?, cost?, pointsConsumed?) => {
              if (!isActiveRequest()) return
              console.log('[ChatPage] Stream done:', msg, 'conversationId:', conversationId)
              const currentConvs = convRef.current
              const c = currentConvs.find(cv => cv.id === streamConvId)
              if (c) {
                const msgs = [...c.messages]
                const lastMsg = msgs[msgs.length - 1]
                if (lastMsg && lastMsg.role === 'assistant') {
                  // 把搜索来源信息持久化到消息中（仅保存搜索完成态，非"正在搜索"），让用户在历史记录中可见
                  const si = searchInfoRef.current
                  const thinkSec = thinkingStartRef.current ? Math.round((Date.now() - thinkingStartRef.current) / 100) / 10 : 0
                  msgs[msgs.length - 1] = {
                    ...lastMsg,
                    content: lastMsg.content,
                    streamStatus: 'complete',
                    tokens: inputTokens != null || outputTokens != null
                      ? `Input: ${inputTokens || 0} | Output: ${outputTokens || 0} | Points: ${pointsConsumed || cost || 0}`
                      : lastMsg.tokens,
                    pointsConsumed: pointsConsumed || 0,
                    searchInfo: (si && !si.searching && si.results.length > 0) ? si : null,
                    thinkingTime: thinkSec,
                  }
                }
                // 后端返回真实 conversationId 时，替换本地临时 ID，确保跨设备同步能关联
                const newConvId = conversationId ? String(conversationId) : streamConvId
                const newConvs = currentConvs.map(cv =>
                  cv.id === streamConvId ? { ...cv, id: newConvId, messages: msgs, updatedAt: Date.now() } : cv
                )
                setConversations(newConvs)
                convRef.current = newConvs
                writeLocalConversations(newConvs)
                scheduleCloudSync(newConvs)
                if (newConvId !== streamConvId) {
                  setCurrentConvId(newConvId)
                  try { localStorage.setItem(getScopedStorageKey(CURRENT_CONV_KEY, activeUserIdRef.current), newConvId) } catch {}
                }
              }
              if (streamControllerRef.current === controller) streamControllerRef.current = null
              streamSequenceRef.current += 1
              streamingConvIdRef.current = ''
              setFileProcessStatus('')
              isStreamingRef.current = false
              setIsStreaming(false)
              void useAppStore.getState().fetchUserInfo()
              searchInfoRef.current = null
              setSearchInfo(null)
            },
            onError: (error) => {
              if (!isActiveRequest()) return
              console.error('[ChatPage] Stream error:', error)
              const currentConvs = convRef.current
              const c = currentConvs.find(cv => cv.id === streamConvId)
              if (c) {
                const msgs = [...c.messages]
                const lastMsg = msgs[msgs.length - 1]
                const errorText = t('发生错误：', 'Error: ') + error
                if (lastMsg && lastMsg.role === 'assistant') {
                  msgs[msgs.length - 1] = {
                    ...lastMsg,
                    content: lastMsg.content ? `${lastMsg.content}\n\n${errorText}` : errorText,
                    streamStatus: 'error',
                  }
                }
                persist(currentConvs.map(cv => cv.id === streamConvId ? { ...cv, messages: msgs, updatedAt: Date.now() } : cv))
              }
              if (streamControllerRef.current === controller) streamControllerRef.current = null
              streamSequenceRef.current += 1
              streamingConvIdRef.current = ''
              setFileProcessStatus('')
              isStreamingRef.current = false
              setIsStreaming(false)
              searchInfoRef.current = null
              setSearchInfo(null)
            },
            onCancelled: () => {
              if (!isActiveRequest()) return
              const currentConvs = convRef.current
              const c = currentConvs.find(cv => cv.id === streamConvId)
              if (c) {
                const messages = [...c.messages]
                const last = messages[messages.length - 1]
                if (last?.role === 'assistant') {
                  messages[messages.length - 1] = { ...last, streamStatus: 'cancelled' }
                  persist(currentConvs.map(cv => cv.id === streamConvId ? { ...cv, messages, updatedAt: Date.now() } : cv))
                }
              }
              if (streamControllerRef.current === controller) streamControllerRef.current = null
              streamSequenceRef.current += 1
              streamingConvIdRef.current = ''
              setFileProcessStatus('')
              isStreamingRef.current = false
              setIsStreaming(false)
              searchInfoRef.current = null
              setSearchInfo(null)
            },
          },
          controller.signal,
          requestModelCode,
          /^\d+$/.test(conv.id) ? conv.id : undefined,
          filesMeta.length > 0 ? filesMeta : undefined,
          fileUrls.length > 0 ? fileUrls : undefined,
          idempotencyKey
        )
      } else {
        const chatMessages = [
          { role: 'system', content: MARKDOWN_SYSTEM_PROMPT },
          ...[...conv.messages, userMsg]
            .filter(m => m.role !== 'system')
            .map(m => ({ role: m.role, content: m.content })),
        ]

        const payload: any = {
          model: requestModelCode,
          messages: chatMessages,
          idempotencyKey,
          stream: true,
          conversationId: /^\d+$/.test(conv.id) ? Number(conv.id) : undefined,
          searchEngine,
          temperature,
          maxTokens,
          mode: 'paid',
        }
        if (fileUrls.length > 0) payload.fileUrls = fileUrls
        // 透传文件元数据，让后端 saveChatMessages 持久化到消息 metadata（兼容无 fileId 但有 url 的场景）
        if (filesMeta.length > 0) payload.files = filesMeta

        await chatApi.stream(
          payload,
          {
            onMessage: (chunk: string) => {
              if (!isActiveRequest()) return
              const currentConvs = convRef.current
              const c = currentConvs.find(cv => cv.id === streamConvId)
              if (!c) return
              const msgs = [...c.messages]
              const lastMsg = msgs[msgs.length - 1]
              if (lastMsg && lastMsg.role === 'assistant') {
                msgs[msgs.length - 1] = { ...lastMsg, content: lastMsg.content + chunk }
              }
              const newConvs = currentConvs.map(cv => 
                cv.id === streamConvId ? { ...cv, messages: msgs, updatedAt: Date.now() } : cv
              )
              setConversations(newConvs)
              convRef.current = newConvs
              writeLocalConversations(newConvs)
            },
            onDone: (_finished, inputTokens?, outputTokens?, cost?, conversationId?, pointsConsumed?) => {
              if (!isActiveRequest()) return
              const currentConvs = convRef.current
              const c = currentConvs.find(cv => cv.id === streamConvId)
              if (c) {
                const msgs = [...c.messages]
                const lastMsg = msgs[msgs.length - 1]
                if (lastMsg && lastMsg.role === 'assistant') {
                  // 把搜索来源信息持久化到消息中（仅保存搜索完成态），让用户在历史记录中可见
                  const si = searchInfoRef.current
                  const thinkSec = thinkingStartRef.current ? Math.round((Date.now() - thinkingStartRef.current) / 100) / 10 : 0
                  msgs[msgs.length - 1] = {
                    ...lastMsg,
                    streamStatus: 'complete',
                    tokens: inputTokens != null || outputTokens != null
                      ? `Input: ${inputTokens || 0} | Output: ${outputTokens || 0} | Points: ${pointsConsumed || cost || 0}`
                      : lastMsg.tokens,
                    pointsConsumed: pointsConsumed || 0,
                    searchInfo: (si && !si.searching && si.results.length > 0) ? si : null,
                    thinkingTime: thinkSec,
                  }
                }
                // 后端返回真实 conversationId 时，替换本地临时 ID，确保跨设备同步能关联
                const newConvId = conversationId ? String(conversationId) : streamConvId
                persist(currentConvs.map(cv => cv.id === streamConvId ? { ...cv, id: newConvId, messages: msgs, updatedAt: Date.now() } : cv))
                if (newConvId !== streamConvId) {
                  setCurrentConvId(newConvId)
                  try { localStorage.setItem(getScopedStorageKey(CURRENT_CONV_KEY, activeUserIdRef.current), newConvId) } catch {}
                }
              }
              if (streamControllerRef.current === controller) streamControllerRef.current = null
              streamSequenceRef.current += 1
              streamingConvIdRef.current = ''
              isStreamingRef.current = false
              setIsStreaming(false)
              void useAppStore.getState().fetchUserInfo()
              searchInfoRef.current = null
              setSearchInfo(null)
            },
            onSearch: (stage: string | undefined, data: SearchInfo) => {
              if (!isActiveRequest()) return
              // 防止动效串台：只有当前显示的对话 === 发起流式的对话时才更新动效
              // streamConvId 是闭包变量（发起时捕获的不变量），currentConvIdRef.current 是最新的当前显示会话ID
              // 不能用 streamingConvIdRef.current（会被新流式覆盖），必须用闭包变量
              if (streamConvId !== currentConvIdRef.current) return
              if (stage === 'searching') {
                const newSi: SearchInfo = { query: data.query || '', results: [], searching: true, stage: 'searching', engine: data.engine }
                searchInfoRef.current = newSi  // 同步更新 ref，不依赖渲染周期（onDone 可能在渲染前执行）
                setSearchInfo(newSi)
                setFileProcessStatus(t('正在搜索...', 'Searching...'))
              } else if (stage === 'results') {
                const newSi: SearchInfo = { query: data.query || '', results: data.results || [], searching: false, stage: 'results', engine: data.engine }
                searchInfoRef.current = newSi  // 同步更新 ref
                setSearchInfo(newSi)
                setFileProcessStatus('')
              } else if (stage === 'empty') {
                const newSi: SearchInfo = { query: data.query || '', results: [], searching: false, stage: 'empty', engine: data.engine }
                searchInfoRef.current = newSi
                setSearchInfo(newSi)
                setFileProcessStatus('')
              }
            },
            onError: (error: string) => {
              if (!isActiveRequest()) return
              const currentConvs = convRef.current
              const c = currentConvs.find(cv => cv.id === streamConvId)
              if (c) {
                const msgs = [...c.messages]
                const lastMsg = msgs[msgs.length - 1]
                if (lastMsg && lastMsg.role === 'assistant') {
                  const errorText = t('发生错误：', 'Error: ') + error
                  msgs[msgs.length - 1] = {
                    ...lastMsg,
                    content: lastMsg.content ? `${lastMsg.content}\n\n${errorText}` : errorText,
                    streamStatus: 'error',
                  }
                }
                persist(currentConvs.map(cv => cv.id === streamConvId ? { ...cv, messages: msgs, updatedAt: Date.now() } : cv))
              }
              if (streamControllerRef.current === controller) streamControllerRef.current = null
              streamSequenceRef.current += 1
              streamingConvIdRef.current = ''
              isStreamingRef.current = false
              setIsStreaming(false)
              searchInfoRef.current = null
              setSearchInfo(null)
            },
            onCancelled: () => {
              if (!isActiveRequest()) return
              const currentConvs = convRef.current
              const c = currentConvs.find(cv => cv.id === streamConvId)
              if (c) {
                const messages = [...c.messages]
                const last = messages[messages.length - 1]
                if (last?.role === 'assistant') {
                  messages[messages.length - 1] = { ...last, streamStatus: 'cancelled' }
                  persist(currentConvs.map(cv => cv.id === streamConvId ? { ...cv, messages, updatedAt: Date.now() } : cv))
                }
              }
              if (streamControllerRef.current === controller) streamControllerRef.current = null
              streamSequenceRef.current += 1
              streamingConvIdRef.current = ''
              setFileProcessStatus('')
              isStreamingRef.current = false
              setIsStreaming(false)
              searchInfoRef.current = null
              setSearchInfo(null)
            },
          },
          controller.signal
        )
      }
    } catch (err: any) {
      if (!isActiveRequest()) return
      if (err?.name !== 'AbortError') {
        const currentConvs = convRef.current
        const c = currentConvs.find(cv => cv.id === streamConvId)
        if (c) {
          const msgs = [...c.messages]
          const lastMsg = msgs[msgs.length - 1]
          if (lastMsg && lastMsg.role === 'assistant') {
            const errorText = t('发生错误：', 'Error: ') + (err?.message || t('未知错误', 'Unknown error'))
            msgs[msgs.length - 1] = {
              ...lastMsg,
              content: lastMsg.content ? `${lastMsg.content}\n\n${errorText}` : errorText,
              streamStatus: 'error',
            }
          }
          persist(currentConvs.map(cv => cv.id === streamConvId ? { ...cv, messages: msgs, updatedAt: Date.now() } : cv))
        }
      }
      streamSequenceRef.current += 1
      streamingConvIdRef.current = ''
      setFileProcessStatus('')
      isStreamingRef.current = false
      setIsStreaming(false)
    }
  }, [currentConvId, persist, scheduleCloudSync, t, searchEngine, temperature, maxTokens, selectedModelCode, availableModels, writeLocalConversations])

  const handleSelectConv = useCallback((id: string) => {
    // 切换对话时清除上一个对话的搜索/文件处理动效状态，防止动效串台到新对话
    if (id !== currentConvId) {
      // 不让旧会话的流继续占用全局输入状态或把迟到的 chunk 写进新会话。
      if (isStreamingRef.current) stopStreaming()
      setSearchInfo(null)
      searchInfoRef.current = null
      setFileProcessStatus('')
    }
    saveCurrentConvId(id)
    void loadConversationMessages(id)
  }, [saveCurrentConvId, currentConvId, loadConversationMessages, stopStreaming])

  const handleNewConv = useCallback(() => {
    if (isStreamingRef.current) stopStreaming()
    const convs = convRef.current
    const newConv = createEmptyConversation(t)
    persist([newConv, ...convs])
    saveCurrentConvId(newConv.id)
  }, [saveCurrentConvId, persist, t, stopStreaming])

  const handleDeleteConv = useCallback((id: string) => {
    if (isStreamingRef.current) return
    setDeleteConvId(id)
  }, [])

  const confirmDeleteConv = useCallback(() => {
    const id = deleteConvId
    if (!id) return
    setDeleteConvId(null)

    // 1. 记录已删除ID（关键：防止刷新后 initLoader 从服务器恢复或把本地旧数据推送回服务器）
    markConvDeleted(id, activeUserIdRef.current)

    // 2. 立即更新 state + 同步写 localStorage（绕过 800ms 防抖，避免刷新读到旧数据导致会话复活）
    const convs = convRef.current.filter(c => c.id !== id)
    let newConvs = convs
    if (convs.length === 0) {
      const newConv = createEmptyConversation(t)
      newConvs = [newConv]
      saveCurrentConvId(newConv.id)
    } else {
      if (currentConvId === id) saveCurrentConvId(convs[0].id)
    }
    setConversations(newConvs)
    convRef.current = newConvs
    writeLocalConversations(newConvs)
    scheduleCloudSync(newConvs)

    // 3. 异步删除后端会话（不阻塞 UI；即使失败，已删除ID列表也能阻止刷新后恢复）
    userApi.deleteConversation(id).then(() => {
      console.log('[ChatPage] 后端会话删除成功:', id)
    }).catch((e) => {
      console.warn('[ChatPage] 后端会话删除失败:', id, e)
    })
  }, [deleteConvId, currentConvId, saveCurrentConvId, scheduleCloudSync, t, writeLocalConversations])

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

  const handleCopyMessage = useCallback((_msg: FrontendMessage) => {
    // MessageActions 内部已完成 clipboard 复制，这里预留扩展（如 toast 提示）
  }, [])

  // 重新生成最后一条 AI 回复：移除末尾 assistant + user 消息后重新发送
  const handleRegenerate = useCallback(() => {
    if (isStreamingRef.current) return
    const conv = convRef.current.find((c) => c.id === currentConvId)
    if (!conv || conv.messages.length === 0) return
    const msgs = [...conv.messages]
    // 移除末尾的 assistant 消息
    while (msgs.length > 0 && msgs[msgs.length - 1].role === 'assistant') {
      msgs.pop()
    }
    if (msgs.length === 0 || msgs[msgs.length - 1].role !== 'user') return
    const lastUserMsg = msgs[msgs.length - 1]
    // 同时移除这条 user 消息（handleSend 会重新添加）
    msgs.pop()
    const updatedConvs = convRef.current.map((c) => (c.id === currentConvId ? { ...c, messages: msgs, updatedAt: Date.now() } : c))
    // 同步更新 ref，避免 handleSend 读到旧数据导致消息重复
    convRef.current = updatedConvs
    persist(updatedConvs)
    handleSend(lastUserMsg.content, [])
  }, [currentConvId, persist, handleSend])

  // 点赞 / 点踩反馈（再次点击相同则取消）
  const handleFeedback = useCallback((msgIndex: number, type: 'like' | 'dislike') => {
    const conv = convRef.current.find((c) => c.id === currentConvId)
    if (!conv) return
    const msgs = [...conv.messages]
    if (msgIndex < 0 || msgIndex >= msgs.length) return
    const newFeedback = msgs[msgIndex].feedback === type ? null : type
    msgs[msgIndex] = { ...msgs[msgIndex], feedback: newFeedback }
    persist(convRef.current.map((c) => (c.id === currentConvId ? { ...c, messages: msgs, updatedAt: Date.now() } : c)))
  }, [currentConvId, persist])

  // 删除单条消息（可单独删除某条提问或回复）
  // 本地立即移除 + 异步同步到服务器（确保跨设备一致）
  const handleDeleteMessage = useCallback((msgIndex: number) => {
    const conv = convRef.current.find((c) => c.id === currentConvId)
    if (!conv) return
    const msgs = [...conv.messages]
    if (msgIndex < 0 || msgIndex >= msgs.length) return
    const msgToDelete = msgs[msgIndex]
    msgs.splice(msgIndex, 1)
    persist(convRef.current.map((c) => (c.id === currentConvId ? { ...c, messages: msgs, updatedAt: Date.now() } : c)))

    // 异步同步到服务器（best-effort，失败不阻塞 UI；服务器按 role+content 定位删除）
    if (msgToDelete && msgToDelete.content) {
      userApi.deleteMessage(currentConvId, msgToDelete.role, msgToDelete.content).catch((e) => {
        console.warn('同步删除消息到服务器失败:', e)
      })
    }
  }, [currentConvId, persist])

  const handleToggleFav = useCallback((id: string) => {
    const convs = convRef.current.map(c => c.id === id ? { ...c, favorited: !c.favorited } : c)
    persist(convs)
  }, [persist])

  const handleSyncToCloud = useCallback(() => {
    if (!activeUserIdRef.current || convRef.current.length === 0) return
    if (cloudSyncTimerRef.current) clearTimeout(cloudSyncTimerRef.current)
    cloudSyncPendingRef.current = convRef.current
    void runCloudSync()
  }, [runCloudSync])

  // 进入页面或切换账号时自动拉取对应用户的会话；请求完成后再次校验账号，防止旧账号响应覆盖新账号。
  useEffect(() => {
    if (!token || !activeUserId) return
    const loaderUserId = activeUserId
    let cancelled = false
    const isCurrent = () => !cancelled && mountedRef.current && activeUserIdRef.current === loaderUserId

    const initLoader = async () => {
      try {
        const fetchResult = await userApi.fetchConversations()
        if (!isCurrent()) return
        const data = fetchResult as { conversations?: any[]; redisConversations?: any[]; version?: number }
        const serverConvs = data?.conversations || data?.redisConversations || (Array.isArray(fetchResult) ? fetchResult as any[] : [])

        const serverMapped: FrontendConversation[] = (Array.isArray(serverConvs) ? serverConvs : []).map((c: any) => ({
          id: String(c.id || ''),
          title: c.title || t('新对话', 'New Chat'),
          messages: [],
          createdAt: c.createdAt || c.createTime || Date.now(),
          updatedAt: c.updatedAt || c.lastMessageTime || c.updatedAt || Date.now(),
          favorited: c.favorited || false,
        }))

        const deletedIds = getDeletedConvIds(loaderUserId)
        const localConvs = convRef.current
        const mergedMap = new Map<string, FrontendConversation>()
        for (const serverConversation of serverMapped) {
          if (deletedIds.has(serverConversation.id)) {
            userApi.deleteConversation(serverConversation.id).catch(() => {})
            continue
          }
          mergedMap.set(serverConversation.id, serverConversation)
        }
        for (const localConversation of localConvs) {
          if (deletedIds.has(localConversation.id)) continue
          const existing = mergedMap.get(localConversation.id)
          if (existing) {
            if (localConversation.updatedAt > existing.updatedAt) mergedMap.set(localConversation.id, { ...localConversation })
          } else {
            mergedMap.set(localConversation.id, localConversation)
          }
        }

        const merged = Array.from(mergedMap.values()).sort((a, b) => b.updatedAt - a.updatedAt)
        if (!isCurrent()) return
        persist(merged, false, true)

        // 只有当前选中的会话已不在新账号的数据集中时，才切换到该账号最近的会话。
        const savedConvId = loadStoredCurrentConversation(loaderUserId, merged)
        if (merged.length > 0 && !merged.some(conversation => conversation.id === currentConvIdRef.current)) {
          saveCurrentConvId(savedConvId || merged[0].id)
        }

        // 只预加载最近几条会话，选中其他会话时由 handleSelectConv
        // 按需加载；每个会话都保留自己的游标，避免“上翻”没有动作。
        for (const conversation of merged.slice(0, 5)) {
          if (isCurrent()) void loadConversationMessages(conversation.id)
        }

        if (data?.version) {
          localStorage.setItem(getScopedStorageKey(SERVER_VERSION_KEY, loaderUserId), String(data.version))
        }
      } catch { /* silent */ }
    }
    void initLoader()
    return () => { cancelled = true }
  }, [activeUserId, loadConversationMessages, persist, saveCurrentConvId, t, token])

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
                onSyncNow={handleSyncToCloud}
                syncState={cloudSyncState}
                syncAt={cloudSyncAt}
                onOpenMobileSidebar={() => setMobileSidebarOpen(false)}
                lang={lang}
                t={t}
                isMobile={true}
              />
            </motion.div>
          </>
        )}
      </AnimatePresence>

      {/* 手机端右边栏：全屏覆盖，打开时由设置面板接管内容区域。 */}
      <AnimatePresence>
        {settingsPanelOpen && (
          <>
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.2 }}
              className="fixed inset-0 bg-black/60 z-[100] lg:hidden"
              onClick={closeSettingsPanel}
            />
            <motion.div
              initial={{ x: '100%' }}
              animate={{ x: 0 }}
              exit={{ x: '100%' }}
              transition={{ type: 'spring', damping: 25, stiffness: 200 }}
              className="fixed right-0 top-0 bottom-0 z-[110] lg:hidden"
              style={{ width: '85%', maxWidth: '24rem' }}
            >
              <ChatSettingsPanel
                model={model}
                availableModels={availableModels}
                onModelChange={(code) => { if (isStreamingRef.current) return; setModel(code); localStorage.setItem('ld_chat_model', code); latestModelRef.current = code }}
                searchEngine={searchEngine}
                onSearchEngineChange={setSearchEngine}
                temperature={temperature}
                onTemperatureChange={setTemperature}
                maxTokens={maxTokens}
                onMaxTokensChange={(value) => setMaxTokens(normalizeMaxTokens(value))}
                onClose={closeSettingsPanel}
                lang={lang}
                t={t}
              />
            </motion.div>
          </>
        )}
      </AnimatePresence>

      <div className="hidden lg:flex h-full flex-col border-r flex-shrink-0 transition-all duration-300" style={{ width: sidebarCollapsed ? '52px' : '280px', borderColor: 'var(--border-color)', background: 'var(--bg-secondary)' }}>
        <ChatSidebar
          conversations={conversations}
          currentConvId={currentConvId}
          onSelectConv={handleSelectConv}
          onNewConv={handleNewConv}
          onDeleteConv={handleDeleteConv}
          onRenameConv={handleRenameConv}
          onExportConv={handleExportConv}
          onToggleFav={handleToggleFav}
          onSyncNow={handleSyncToCloud}
          syncState={cloudSyncState}
          syncAt={cloudSyncAt}
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

      <main className="flex min-w-0 flex-1 flex-col relative">
        <div className="sticky top-0 z-10 flex min-h-14 flex-shrink-0 items-center justify-between gap-3 border-b px-3 backdrop-blur-xl sm:px-4" style={{ borderColor: 'var(--border-color)', background: 'var(--bg-primary)' }}>
          <div className="flex min-w-0 flex-1 items-center gap-2">
            <button
              onClick={() => setMobileSidebarOpen(true)}
              className="motion-interactive -ml-1 rounded-lg p-1.5 lg:hidden"
              style={{ color: 'var(--text-dim)' }}
              aria-label={t('打开对话列表', 'Open conversations')}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>
            </button>
            <span className="hidden h-2 w-2 flex-shrink-0 rounded-full lg:block" style={{ background: 'var(--accent-emerald)', boxShadow: '0 0 0 4px var(--accent-emerald-soft)' }} />
            <div className="min-w-0">
              <h2 className="truncate text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                {currentConv.title || t('AI 对话', 'AI Chat')}
              </h2>
              <p className="hidden truncate text-[10px] sm:block" style={{ color: 'var(--text-dim)' }}>{t('AI 工作区 · 会话自动同步', 'AI workspace · conversations sync automatically')}</p>
            </div>
          </div>

          <div className="flex flex-shrink-0 items-center gap-1.5 sm:gap-2">
            <div className="relative">
            <button
              onClick={() => setShowModelSelector(!showModelSelector)}
              disabled={isStreaming}
              className="motion-interactive flex items-center gap-1.5 rounded-lg border px-2.5 py-1.5 text-xs"
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
      const isUnavailable = !m.chatEnabled
      const isDisabled = isLocked || isUnavailable || isStreaming
                      return (
                        <button
                          key={m.modelId}
          onClick={() => { if (isDisabled || isStreamingRef.current) return; setModel(modelCode); localStorage.setItem('ld_chat_model', modelCode); latestModelRef.current = modelCode; setShowModelSelector(false) }}
          disabled={isDisabled}
          className={`w-full text-left px-3 py-2 rounded-lg transition-all ${modelCode === model ? 'border' : ''} ${isDisabled ? 'opacity-50 cursor-not-allowed' : ''}`}
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
                                {isUnavailable && (
                                  <span className="text-[9px] px-1 rounded" style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)' }}>
                                    {t('暂未接入', 'Not connected')}
                                  </span>
                                )}
                              </div>
                              <div className="text-[9px] sm:text-[10px] mt-0.5" style={{ color: 'var(--text-dim)' }}>
                                {m.provider || t('未知', 'Unknown')}{m.fixedPoints > 0 ? <span style={{ color: 'var(--accent-purple)' }}> · {m.fixedPoints}点/次</span> : null}
                              </div>
                              {isLocked && m.upgradeHint && (
                                <div className="text-[9px] mt-0.5" style={{ color: '#F59E0B' }}>{m.upgradeHint}</div>
                              )}
                              {isUnavailable && (
                                <div className="text-[9px] mt-0.5" style={{ color: 'var(--text-dim)' }}>
                                  {t('模型服务尚未完成配置', 'Model service is not configured yet')}
                                </div>
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

            {/* 设置按钮（齿轮图标，点击切换右边栏） */}
            <button
              onClick={toggleSettingsPanel}
              className="motion-interactive rounded-lg border p-1.5"
              style={{ borderColor: 'var(--border-color)', color: settingsPanelOpen ? 'var(--accent-purple)' : 'var(--text-dim)', background: 'var(--bg-surface)' }}
              aria-label={t('设置', 'Settings')}
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="12" r="3" />
                <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" />
              </svg>
            </button>
          </div>
        </div>

        {!hasUsableChatModel && availableModels.length > 0 && (
          <div role="status" className="border-b px-4 py-2 text-xs" style={{ borderColor: 'var(--border-color)', background: 'var(--bg-surface)', color: 'var(--text-dim)' }}>
            {availableModels.some(m => m.chatEnabled)
              ? t('当前账号没有可用的已接入模型，请检查会员等级或联系管理员。', 'No connected model is available to this account. Check access level or contact an administrator.')
              : t('模型服务尚未接入，请联系管理员完成上游配置。', 'No model service is connected yet. Contact an administrator to configure an upstream.')}
          </div>
        )}

        <div className="page-scroll min-h-0 flex-1 overflow-y-auto" id="chat-messages-scroll">
          {currentConv.messages.length === 0 ? (
            <WelcomeScreen
              model={currentModelLabel}
              lang={lang}
              onSuggestionClick={(text) => handleSend(text, [])}
            />
          ) : (
            <ChatMessages
              key={currentConvId}
              conversationId={currentConvId}
              messages={currentConv.messages}
              isStreaming={isStreaming}
              tokenStats={null}
              fileProcessStatus={fileProcessStatus}
              lang={lang}
              t={t}
              onCopyMessage={handleCopyMessage}
              onRegenerate={handleRegenerate}
              onFeedback={handleFeedback}
              searchInfo={searchInfo}
              onDeleteMessage={handleDeleteMessage}
              onLoadMore={loadMoreRef}
              isLoadingMore={currentHistoryState.loading}
              hasMore={currentHistoryState.hasMore}
            />
          )}
        </div>

        <ChatInput
          onSend={handleSend}
          onStop={stopStreaming}
          isStreaming={isStreaming}
          disabled={!hasUsableChatModel}
          placeholder={
            fileProcessStatus
              ? fileProcessStatus
              : !hasUsableChatModel && availableModels.length > 0
                ? availableModels.some(m => m.chatEnabled)
                  ? t('当前账号没有可用的已接入模型', 'No connected model is available to this account')
                  : t('模型服务尚未接入，请联系管理员', 'No model service is connected; contact an administrator')
              : lang === 'zh'
                ? '输入消息，Enter 发送，Shift+Enter 换行'
                : 'Type a message, Enter to send, Shift+Enter for new line'
          }
          lang={lang}
          t={t}
        />
      </main>

      {/* 桌面端右边栏：三栏并存，打开时展开 w-80，关闭时收起 w-0 */}
      <aside
        className={`hidden lg:flex flex-shrink-0 border-l transition-all duration-300 overflow-hidden ${settingsPanelOpen ? 'w-80' : 'w-0'}`}
        style={{ borderColor: 'var(--border-color)', background: 'var(--bg-secondary)' }}
      >
        {settingsPanelOpen && (
          <div className="w-80 h-full flex-shrink-0">
            <ChatSettingsPanel
              model={model}
              availableModels={availableModels}
              onModelChange={(code) => { if (isStreamingRef.current) return; setModel(code); localStorage.setItem('ld_chat_model', code); latestModelRef.current = code }}
              searchEngine={searchEngine}
              onSearchEngineChange={setSearchEngine}
              temperature={temperature}
              onTemperatureChange={setTemperature}
              maxTokens={maxTokens}
              onMaxTokensChange={(value) => setMaxTokens(normalizeMaxTokens(value))}
              onClose={closeSettingsPanel}
              lang={lang}
              t={t}
            />
          </div>
        )}
      </aside>

      <ConfirmDialog
        open={Boolean(deleteConvId)}
        title={t('删除会话', 'Delete conversation')}
        message={t('确定删除这个对话？删除后不可恢复。', 'Delete this conversation? This cannot be undone.')}
        confirmText={t('删除', 'Delete')}
        cancelText={t('取消', 'Cancel')}
        variant="danger"
        onConfirm={confirmDeleteConv}
        onCancel={() => setDeleteConvId(null)}
      />
    </div>
  )
}
