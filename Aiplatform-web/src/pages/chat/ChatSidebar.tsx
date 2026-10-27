import { useState, useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import type { FrontendConversation } from '@/types'
import { useAppStore } from '@/stores/appStore'
import { useI18nStore } from '@/stores/i18nStore'
import { useThemeStore } from '@/stores/themeStore'
import NotificationBell from '@/components/NotificationBell'
import { getUserDisplayName } from '@/utils/userDisplay'
import UserAvatar from '@/components/UserAvatar'

interface Props {
  conversations: FrontendConversation[]
  currentConvId: string
  onSelectConv: (id: string) => void
  onNewConv: () => void
  onDeleteConv: (id: string) => void
  onRenameConv: (id: string, title: string) => void
  onExportConv: (conv: FrontendConversation) => void
  onToggleFav: (id: string) => void
  onSyncNow: () => void
  syncState?: 'idle' | 'syncing' | 'synced' | 'error'
  syncAt?: number | null
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
  onRenameConv, onExportConv, onToggleFav, onSyncNow, syncState = 'idle', syncAt, onOpenMobileSidebar,
  collapsed = false, onToggleCollapsed, isLoading = false, lang, t, isMobile = false
}: Props) {
  const navigate = useNavigate()
  const { user, logout } = useAppStore()
  const { toggleLang } = useI18nStore()
  const { theme, toggleTheme } = useThemeStore()
  const isDark = theme === 'dark'
  const userDisplayName = getUserDisplayName(user, t('第三方账号', 'External account'))
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
    // 防御 title 为空的情况（旧数据/新建未命名会话），避免 toLowerCase 崩溃白屏
    return sorted.filter(c => (c.title || '').toLowerCase().includes(q))
  }, [sorted, search])

  const handleStartRename = useCallback((e: React.MouseEvent, conv: FrontendConversation) => {
    e.stopPropagation()
    setShowMore(null)  // 关闭下拉菜单，避免遮挡编辑输入框
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

  const handleLogout = useCallback(() => {
    if (!window.confirm(t('确定退出登录吗？', 'Are you sure you want to log out?'))) return
    logout()
    navigate('/login', { replace: true })
  }, [logout, navigate, t])

  return (
    <aside
      className={`flex flex-col border-r flex-shrink-0 transition-all duration-300 overflow-hidden ${isMobile ? 'h-screen' : 'hidden lg:flex h-full'}`}
      style={{
        width: collapsed ? '52px' : '280px',
        borderColor: 'var(--border-color)',
        background: 'var(--bg-secondary)',
      }}
    >
      {collapsed ? (
        <div className="flex min-h-0 flex-1 flex-col items-center gap-2 py-3">
          <button onClick={onToggleCollapsed} className="motion-interactive rounded-lg p-2"
            style={{ color: 'var(--text-dim)' }} title={t('展开侧边栏', 'Expand sidebar')} aria-label={t('展开侧边栏', 'Expand sidebar')}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="9 18 15 12 9 6" /></svg>
          </button>
          <button onClick={onNewConv} className="motion-interactive flex h-9 w-9 items-center justify-center rounded-xl"
            style={{ background: 'var(--accent-purple-soft)', color: 'var(--accent-purple)' }}
            title={t('新对话', 'New Chat')} aria-label={t('新对话', 'New Chat')}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" /></svg>
          </button>
          <div className="flex w-full flex-1 flex-col gap-1.5 overflow-y-auto px-2 pt-2">
            {sorted.slice(0, 8).map(c => (
              <button key={c.id} onClick={() => onSelectConv(c.id)}
                className="motion-interactive mx-auto h-2 w-2 rounded-full"
                style={{ background: c.id === currentConvId ? 'var(--accent-purple)' : 'var(--text-dim)', opacity: c.id === currentConvId ? 1 : 0.35 }}
                title={c.title} aria-label={c.title} />
            ))}
          </div>
        </div>
      ) : (
        <>
          <div className="flex h-14 flex-shrink-0 items-center justify-between border-b px-4" style={{ borderColor: 'var(--border-color)' }}>
            <div className="flex items-center gap-2">
              <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="var(--accent-purple)" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" /></svg>
              <span className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>{t('对话', 'Conversations')}</span>
            </div>
            <div className="flex items-center gap-1">
              <button onClick={onNewConv} className="motion-interactive rounded-lg p-1.5"
                style={{ color: 'var(--text-dim)' }} title={t('新对话', 'New Chat')} aria-label={t('新对话', 'New Chat')}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" /></svg>
              </button>
              {isMobile ? (
                <button onClick={onOpenMobileSidebar} className="motion-interactive rounded-lg p-1.5"
                  style={{ color: 'var(--text-dim)' }} title={t('关闭', 'Close')} aria-label={t('关闭', 'Close')}>
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" /></svg>
                </button>
              ) : onToggleCollapsed ? (
                <button onClick={onToggleCollapsed} className="motion-interactive rounded-lg p-1.5"
                  style={{ color: 'var(--text-dim)' }} title={t('收起侧边栏', 'Collapse sidebar')} aria-label={t('收起侧边栏', 'Collapse sidebar')}>
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="15 18 9 12 15 6" /></svg>
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

          <div className="page-scroll flex-1 overflow-y-auto px-2 py-1 min-h-0">
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
            <div className="flex flex-shrink-0 flex-col border-t" style={{ borderColor: 'var(--border-color)', background: 'var(--bg-secondary)' }}>
              <div className="px-3 py-2.5" style={{ borderColor: 'var(--border-light)' }}>
                <div className="flex items-center gap-2">
                  <span className="h-1.5 w-1.5 shrink-0 rounded-full" style={{ background: syncState === 'error' ? 'var(--accent-rose)' : syncState === 'syncing' ? 'var(--accent-amber)' : 'var(--accent-emerald)' }} />
                  <span className="min-w-0 flex-1 truncate text-[10px]" style={{ color: 'var(--text-dim)' }}>
                    {syncState === 'syncing'
                      ? t('正在自动同步…', 'Syncing automatically…')
                      : syncState === 'error'
                        ? t('自动同步失败', 'Automatic sync failed')
                        : syncState === 'synced'
                          ? `${t('已自动同步', 'Synced automatically')}${syncAt ? ` · ${formatSyncTime(syncAt)}` : ''}`
                          : t('自动同步已开启', 'Automatic sync is on')}
                  </span>
                  <button
                    type="button"
                    onClick={onSyncNow}
                    disabled={syncState === 'syncing'}
                    className="motion-interactive flex h-7 w-7 shrink-0 items-center justify-center rounded-lg disabled:opacity-40"
                    style={{ color: 'var(--accent-purple)' }}
                    aria-label={t('立即同步', 'Sync now')}
                    title={t('立即同步', 'Sync now')}
                  >
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M20 11a8 8 0 0 0-14.8-4L3 9" /><path d="M3 4v5h5" /><path d="M4 13a8 8 0 0 0 14.8 4L21 15" /><path d="M21 20v-5h-5" /></svg>
                  </button>
                </div>
                <p className="mt-1 pl-3.5 text-[9px]" style={{ color: 'var(--text-dim)' }}>{t('切换账号后自动加载对应会话', 'Each account loads its own conversations')}</p>
              </div>
              <div className="flex items-center gap-1 border-t px-3 py-2" style={{ borderColor: 'var(--border-light)' }}>
                <button type="button" onClick={() => navigate('/user')} className="motion-interactive flex min-w-0 flex-1 items-center gap-2 rounded-lg px-1 py-1 text-left hover:bg-[var(--hover-bg)]" title={t('个人中心', 'Profile')}>
                  <UserAvatar name={userDisplayName} avatarUrl={user?.avatarUrl} className="h-7 w-7 text-[10px] font-bold text-white" style={{ background: 'linear-gradient(135deg, #10B981, #059669)' }} />
                  <span className="min-w-0">
                    <span className="block truncate text-[11px] font-medium" style={{ color: 'var(--text-secondary)' }} title={userDisplayName}>{userDisplayName || t('当前账户', 'Account')}</span>
                    <span className="block truncate text-[9px]" style={{ color: 'var(--text-dim)' }}>{user?.membership || t('个人工作区', 'Personal workspace')}</span>
                  </span>
                </button>
                <NotificationBell />
                <button type="button" onClick={toggleTheme} className="motion-interactive rounded-lg p-1.5" style={{ color: 'var(--text-dim)' }} aria-label={isDark ? t('切换明亮模式', 'Use light mode') : t('切换深色模式', 'Use dark mode')} title={isDark ? t('切换明亮模式', 'Use light mode') : t('切换深色模式', 'Use dark mode')}>
                  {isDark ? <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" /></svg> : <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79Z" /></svg>}
                </button>
                <button type="button" onClick={toggleLang} className="motion-interactive rounded-lg px-1.5 py-1 text-[9px] font-medium" style={{ color: 'var(--text-dim)' }} aria-label={lang === 'zh' ? 'English' : '中文'}>{lang === 'zh' ? 'EN' : '中'}</button>
                <button type="button" onClick={handleLogout} className="motion-interactive rounded-lg p-1.5" style={{ color: 'var(--text-dim)' }} aria-label={t('退出登录', 'Log out')} title={t('退出登录', 'Log out')}>
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M10 17l5-5-5-5" /><path d="M15 12H3" /><path d="M21 19V5a2 2 0 0 0-2-2h-6" /></svg>
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

function formatSyncTime(ts: number): string {
  return new Date(ts).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}
