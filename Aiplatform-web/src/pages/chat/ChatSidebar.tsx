import { useState, useCallback, useMemo } from 'react'
import type { FrontendConversation } from '@/types'

interface Props {
  conversations: FrontendConversation[]
  currentConvId: string
  onSelectConv: (id: string) => void
  onNewConv: () => void
  onDeleteConv: (id: string) => void
  onRenameConv: (id: string, title: string) => void
  onExportConv: (conv: FrontendConversation) => void
  onToggleFav: (id: string) => void
  onSyncToCloud: () => void
  onCloudRestore: () => void
  onOpenMobileSidebar?: () => void
  collapsed?: boolean
  onToggleCollapsed?: () => void
  isLoading?: boolean
  lang: string
  t: (zh: string, en: string) => string
  isMobile?: boolean
}

export default function ChatSidebar({
  conversations, currentConvId, onSelectConv, onNewConv, onDeleteConv,
  onRenameConv, onExportConv, onToggleFav, onSyncToCloud, onCloudRestore, onOpenMobileSidebar,
  collapsed = false, onToggleCollapsed, isLoading = false, lang, t, isMobile = false
}: Props) {
  const [search, setSearch] = useState('')
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editValue, setEditValue] = useState('')
  const [showMore, setShowMore] = useState<string | null>(null)

  const sorted = useMemo(() => {
    const copy = [...conversations]
    copy.sort((a, b) => {
      if (a.favorited && !b.favorited) return -1
      if (!a.favorited && b.favorited) return 1
      return (b.updatedAt || 0) - (a.updatedAt || 0)
    })
    return copy
  }, [conversations])

  const filtered = useMemo(() => {
    if (!search.trim()) return sorted
    const q = search.toLowerCase()
    return sorted.filter(c => c.title.toLowerCase().includes(q))
  }, [sorted, search])

  const handleStartRename = useCallback((e: React.MouseEvent, conv: FrontendConversation) => {
    e.stopPropagation()
    setEditingId(conv.id)
    setEditValue(conv.title)
  }, [])

  const handleConfirmRename = useCallback((id: string) => {
    if (editValue.trim()) onRenameConv(id, editValue.trim())
    setEditingId(null)
  }, [editValue, onRenameConv])

  const handleDelete = useCallback((e: React.MouseEvent, id: string) => {
    e.stopPropagation()
    setShowMore(null)
    onDeleteConv(id)
  }, [onDeleteConv])

  const handleToggleFav = useCallback((e: React.MouseEvent, id: string) => {
    e.stopPropagation()
    onToggleFav(id)
  }, [onToggleFav])

  const handleExport = useCallback((e: React.MouseEvent, conv: FrontendConversation) => {
    e.stopPropagation()
    setShowMore(null)
    onExportConv(conv)
  }, [onExportConv])

  return (
    <aside
      className={`flex flex-col border-r flex-shrink-0 transition-all duration-300 overflow-hidden ${isMobile ? 'h-screen' : 'hidden lg:flex'}`}
      style={{
        width: collapsed ? '52px' : '280px',
        borderColor: 'var(--border-color)',
        background: 'var(--bg-secondary)',
      }}
    >
      {collapsed ? (
        <div className="flex flex-col items-center gap-2 py-4 h-full">
          <button onClick={onToggleCollapsed} className="p-2 rounded-lg transition-all hover:scale-110"
            style={{ color: 'var(--text-dim)' }} title={t('展开侧边栏', 'Expand sidebar')}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="9 18 15 12 9 6"/></svg>
          </button>
          <button onClick={onNewConv} className="w-9 h-9 rounded-xl flex items-center justify-center transition-all duration-200 hover:scale-105"
            style={{ background: 'var(--accent-purple-soft)', color: 'var(--accent-purple)' }}
            title={t('新对话', 'New Chat')}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          </button>
          <div className="flex-1 flex flex-col gap-1.5 overflow-y-auto w-full px-2 pt-2">
            {sorted.slice(0, 8).map(c => (
              <button key={c.id} onClick={() => onSelectConv(c.id)}
                className="w-2 h-2 rounded-full mx-auto transition-all hover:scale-150"
                style={{ background: c.id === currentConvId ? 'var(--accent-purple)' : 'var(--text-dim)', opacity: c.id === currentConvId ? 1 : 0.35 }}
                title={c.title} />
            ))}
          </div>
        </div>
      ) : (
        <>
          <div className="flex items-center justify-between px-4 h-12 border-b flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
            <div className="flex items-center gap-2">
              <div className="w-7 h-7 rounded-lg flex items-center justify-center"
                style={{ background: 'linear-gradient(135deg, #6366F1, #3B82F6)' }}>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/>
                </svg>
              </div>
              <span className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                {t('对话', 'Conversations')}
              </span>
            </div>
            <div className="flex items-center gap-1">
              <button onClick={onNewConv} className="p-1.5 rounded-lg transition-all hover:scale-110"
                style={{ color: 'var(--text-dim)' }} title={t('新对话', 'New Chat')}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
              </button>
              {isMobile ? (
                <button onClick={onOpenMobileSidebar} className="p-1.5 rounded-lg transition-all hover:scale-110"
                  style={{ color: 'var(--text-dim)' }} title={t('关闭', 'Close')}>
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                </button>
              ) : onToggleCollapsed ? (
                <button onClick={onToggleCollapsed} className="p-1.5 rounded-lg transition-all hover:scale-110"
                  style={{ color: 'var(--text-dim)' }} title={t('收起侧边栏', 'Collapse sidebar')}>
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="15 18 9 12 15 6"/></svg>
                </button>
              ) : null}
            </div>
          </div>

          <div className="px-3 py-2 flex-shrink-0">
            <div className="flex items-center gap-2 px-3 py-2 rounded-xl text-xs transition-all duration-200"
              style={{ background: 'var(--bg-input)', border: '1px solid var(--border-color)' }}>
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="var(--text-dim)" strokeWidth="2">
                <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
              </svg>
              <input
                value={search}
                onChange={e => setSearch(e.target.value)}
                placeholder={t('搜索对话...', 'Search conversations...')}
                className="flex-1 bg-transparent outline-none"
                style={{ color: 'var(--text-primary)' }}
              />
              {search && (
                <button onClick={() => setSearch('')} className="opacity-40 hover:opacity-80">
                  <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                </button>
              )}
            </div>
          </div>

          <div className="flex-1 overflow-y-auto px-2 py-1 min-h-0">
            {isLoading ? (
              <div className="flex flex-col items-center justify-center py-12 gap-3">
                <div className="typing-indicator"><span /><span /><span /></div>
                <p className="text-xs" style={{ color: 'var(--text-dim)' }}>
                  {t('加载对话...', 'Loading conversations...')}
                </p>
              </div>
            ) : filtered.length === 0 ? (
              <div className="flex flex-col items-center py-12 gap-2">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="var(--text-dim)" strokeWidth="1.5" opacity={0.3}>
                  <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z" />
                </svg>
                <p className="text-[11px]" style={{ color: 'var(--text-dim)' }}>
                  {search ? t('无匹配对话', 'No matches') : t('暂无对话', 'No conversations')}
                </p>
              </div>
            ) : (
              filtered.map(conv => {
                const isActive = conv.id === currentConvId
                const isEditing = editingId === conv.id

                return (
                  <div key={conv.id} className="mb-0.5 relative">
                    {isEditing ? (
                      <div className="flex items-center gap-2 px-2 py-1.5">
                        <input
                          value={editValue}
                          onChange={e => setEditValue(e.target.value)}
                          onKeyDown={e => {
                            if (e.key === 'Enter') handleConfirmRename(conv.id)
                            if (e.key === 'Escape') setEditingId(null)
                          }}
                          className="flex-1 px-2 py-1 rounded-lg text-xs outline-none"
                          style={{ background: 'var(--bg-input)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }}
                          autoFocus
                          onBlur={() => handleConfirmRename(conv.id)}
                        />
                      </div>
                    ) : (
                      <div
                        onClick={() => onSelectConv(conv.id)}
                        className={`w-full text-left px-3 py-2.5 rounded-xl transition-all duration-200 group cursor-pointer ${isActive ? '' : 'hover:bg-opacity-5'}`}
                        style={{
                          background: isActive ? 'var(--bg-active)' : 'transparent',
                          border: isActive ? '1px solid var(--border-accent)' : '1px solid transparent',
                        }}
                      >
                        <div className="flex items-start gap-2">
                          {conv.favorited && (
                            <svg width="12" height="12" viewBox="0 0 24 24" fill="var(--accent-amber)"
                              className="flex-shrink-0 mt-0.5">
                              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
                            </svg>
                          )}
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center justify-between gap-2">
                              <span className="text-[13px] font-medium truncate" style={{ color: isActive ? 'var(--accent-purple)' : 'var(--text-secondary)' }}>
                                {conv.title}
                              </span>
                              <span className="text-[10px] flex-shrink-0" style={{ color: 'var(--text-dim)' }}>
                                {formatRelativeTime(conv.updatedAt || conv.createdAt, lang, t)}
                              </span>
                            </div>
                            <div className="flex items-center gap-2 mt-0.5">
                              <span className="text-[10px]" style={{ color: 'var(--text-dim)' }}>
                                {conv.messages?.length || 0} {t('条消息', ' msgs')}
                              </span>
                            </div>
                          </div>

                          <div className="relative flex-shrink-0" onClick={e => e.stopPropagation()}>
                            <button
                              onClick={() => setShowMore(showMore === conv.id ? null : conv.id)}
                              className="p-1 rounded-md opacity-0 group-hover:opacity-60 hover:opacity-100 transition-opacity"
                              style={{ color: 'var(--text-dim)' }}
                            >
                              <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                                <circle cx="5" cy="12" r="2" /><circle cx="12" cy="12" r="2" /><circle cx="19" cy="12" r="2" />
                              </svg>
                            </button>

                            {showMore === conv.id && (
                              <>
                                <div className="fixed inset-0 z-[60]" onClick={() => setShowMore(null)} />
                                <div className="absolute right-0 top-full mt-1 z-[70] w-36 rounded-xl border py-1 shadow-2xl"
                                  style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
                                  <button onClick={e => handleToggleFav(e, conv.id)}
                                    className="w-full text-left px-3 py-1.5 text-[11px] transition-colors hover:bg-[var(--hover-bg)]"
                                    style={{ color: 'var(--text-secondary)' }}>
                                    {conv.favorited ? t('取消收藏', 'Unfavorite') : t('收藏', 'Favorite')}
                                  </button>
                                  <button onClick={e => handleStartRename(e, conv)}
                                    className="w-full text-left px-3 py-1.5 text-[11px] transition-colors hover:bg-[var(--hover-bg)]"
                                    style={{ color: 'var(--text-secondary)' }}>
                                    {t('重命名', 'Rename')}
                                  </button>
                                  <button onClick={e => handleExport(e, conv)}
                                    className="w-full text-left px-3 py-1.5 text-[11px] transition-colors hover:bg-[var(--hover-bg)]"
                                    style={{ color: 'var(--text-secondary)' }}>
                                    {t('导出', 'Export')}
                                  </button>
                                  <button onClick={e => handleDelete(e, conv.id)}
                                    className="w-full text-left px-3 py-1.5 text-[11px] transition-colors hover:bg-[var(--hover-bg)]"
                                    style={{ color: 'var(--accent-rose)' }}>
                                    {t('删除', 'Delete')}
                                  </button>
                                </div>
                              </>
                            )}
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                )
              })
            )}
          </div>

          {!isLoading && (
            <div className="flex-shrink-0 px-3 py-2.5 border-t" style={{ borderColor: 'var(--border-color)', background: 'var(--bg-secondary)' }}>
              <div className="flex items-center gap-2">
                <button onClick={onSyncToCloud} className="flex-1 py-2 rounded-xl text-[10px] font-medium transition-all hover:scale-[1.02] active:scale-[0.98]"
                  style={{ background: 'var(--accent-purple-soft)', color: 'var(--accent-purple)' }}>
                  <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="inline mr-1"><path d="M23 6l-9.5 9.5-5-5L1 18"/><path d="M17 6h6v6"/></svg>
                  {t('同步到云端', 'Sync to Cloud')}
                </button>
                <button onClick={onCloudRestore} className="flex-1 py-2 rounded-xl text-[10px] font-medium transition-all hover:scale-[1.02] active:scale-[0.98]"
                  style={{ background: 'var(--accent-blue-soft)', color: 'var(--accent-blue)' }}>
                  <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="inline mr-1"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                  {t('云端恢复', 'Cloud Restore')}
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </aside>
  )
}

function formatRelativeTime(ts: number, lang: string, _t: (zh: string, en: string) => string): string {
  const now = Date.now()
  const diff = now - ts
  const mins = Math.floor(diff / 60000)
  const hrs = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (mins < 1) return lang === 'zh' ? '刚刚' : 'now'
  if (mins < 60) return `${mins}${lang === 'zh' ? '分前' : 'm ago'}`
  if (hrs < 24) return `${hrs}${lang === 'zh' ? '时前' : 'h ago'}`
  if (days < 7) return `${days}${lang === 'zh' ? '天前' : 'd ago'}`
  const d = new Date(ts)
  return `${d.getMonth() + 1}/${d.getDate()}`
}
