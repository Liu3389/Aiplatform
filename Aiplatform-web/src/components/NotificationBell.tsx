import { useState, useEffect, useCallback, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { useI18nStore } from '@/stores/i18nStore'
import { useThemeStore } from '@/stores/themeStore'
import { notificationApi } from '@/services/api'
import { useToastStore } from '@/stores/toastStore'
import { useNotificationSse, type SseNotification } from '@/hooks/useNotificationSse'
import { Z } from '@/constants/zIndex'

interface NotifItem {
  id: number
  userId: number
  title: string
  content: string
  type: string
  isRead: boolean
  createTime: string
}

export default function NotificationBell() {
  const { lang } = useI18nStore()
  const { theme } = useThemeStore()
  const navigate = useNavigate()
  const dark = theme === 'dark'
  const [open, setOpen] = useState(false)
  const [list, setList] = useState<NotifItem[]>([])
  const [unread, setUnread] = useState(0)
  const openRef = useRef(false)
  const { showToast } = useToastStore()

  const fetchUnread = useCallback(async () => {
    try {
      const data = await notificationApi.unreadCount()
      setUnread(data.unreadCount || 0)
    } catch (e) {
      console.error('[NotificationBell] fetchUnread failed:', e)
    }
  }, [])

  const fetchList = useCallback(async () => {
    try {
      const data = await notificationApi.list()
      console.log('[NotificationBell] fetchList result:', data?.length, 'items')
      setList(Array.isArray(data) ? data : [])
    } catch (e) {
      console.error('[NotificationBell] fetchList failed:', e)
    }
  }, [])

  // SSE 实时推送回调：收到新通知时更新未读数、刷新列表（若面板打开）、弹 Toast
  const handleSseNotification = useCallback((n: SseNotification) => {
    setUnread(prev => prev + 1)
    // 面板打开时实时追加到列表头部，避免用户看到过期列表
    if (openRef.current) {
      setList(prev => [{
        id: n.id,
        userId: n.userId,
        title: n.title,
        content: n.content,
        type: n.type,
        isRead: false,
        createTime: n.createTime,
      }, ...prev])
    }
    // 弹出 Toast 提醒
    showToast({
      title: n.title,
      message: n.content,
      type: n.type === 'coupon' ? 'success' : 'info',
      duration: 5000,
    })
  }, [showToast])

  useNotificationSse({ onNotification: handleSseNotification, enabled: true })

  // 轮询兜底：SSE 断线时保证最终一致性，频率从 30s 放宽到 5min
  useEffect(() => {
    fetchUnread()
    const interval = setInterval(fetchUnread, 5 * 60 * 1000)
    return () => clearInterval(interval)
  }, [fetchUnread])

  // 同步 open 状态到 ref，供 SSE 回调读取
  useEffect(() => { openRef.current = open }, [open])

  const markAllRead = async () => {
    try {
      await notificationApi.markAllRead()
      setUnread(0)
      setList(list.map(n => ({ ...n, isRead: true })))
    } catch {}
  }

  const togglePanel = () => {
    if (!open) { fetchList(); fetchUnread() }
    setOpen(!open)
  }

  const typeLabel = (t: string) => {
    switch (t) {
      case 'coupon': return lang === 'zh' ? '代金券' : 'Coupon'
      case 'system': return lang === 'zh' ? '系统' : 'System'
      case 'promo': return lang === 'zh' ? '优惠' : 'Promo'
      default: return lang === 'zh' ? '通知' : 'Notice'
    }
  }

  return (
    <div className="relative">
      <button
        onClick={togglePanel}
        className={`relative p-1.5 rounded-lg transition-colors ${dark ? 'text-neutral-400 hover:text-white hover:bg-white/[0.06]' : 'text-gray-500 hover:text-gray-700 hover:bg-gray-100'}`}
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
          <path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9" />
          <path d="M13.73 21a2 2 0 01-3.46 0" />
        </svg>
        {unread > 0 && (
          <span className="absolute -top-0.5 -right-0.5 w-4 h-4 rounded-full bg-red-500 text-white text-[9px] font-bold flex items-center justify-center">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      <AnimatePresence>
        {open && (
          <>
            <div className="fixed inset-0" onClick={() => setOpen(false)} style={{ zIndex: Z.NOTIFICATION - 1 }} />
            <motion.div
              initial={{ opacity: 0, y: -8, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: -8, scale: 0.96 }}
              transition={{ duration: 0.15 }}
              className="absolute right-0 top-full mt-2 w-80 max-h-[420px] rounded-2xl border shadow-2xl flex flex-col"
              style={{
                zIndex: Z.NOTIFICATION,
                background: dark ? '#141A2E' : '#fff',
                borderColor: dark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.08)',
              }}
            >
              <div className="flex items-center justify-between px-4 py-3 border-b" style={{ borderColor: dark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)' }}>
                <h3 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                  {lang === 'zh' ? '消息通知' : 'Notifications'}
                </h3>
                <button onClick={markAllRead} className="text-[10px] font-medium" style={{ color: 'var(--accent-purple)' }}>
                  {lang === 'zh' ? '全部已读' : 'Read all'}
                </button>
              </div>
              <div className="flex-1 overflow-y-auto">
                {list.length === 0 ? (
                  <div className="py-12 text-center text-xs" style={{ color: 'var(--text-dim)' }}>
                    {lang === 'zh' ? '暂无消息' : 'No notifications'}
                  </div>
                ) : (
                  list.map(n => (
                    <div
                      key={n.id}
                      className="px-4 py-3 border-b transition-colors hover:bg-white/[0.02]"
                      style={{
                        borderColor: dark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)',
                        background: n.isRead ? 'transparent' : (dark ? 'rgba(139,92,246,0.06)' : 'rgba(139,92,246,0.04)'),
                      }}
                    >
                      <div className="flex items-center gap-2 mb-1">
                        {!n.isRead && <span className="w-1.5 h-1.5 rounded-full bg-brand-purple flex-shrink-0" />}
                        <span className="text-[10px] font-medium" style={{ color: 'var(--text-dim)' }}>{typeLabel(n.type)}</span>
                        <span className="text-[10px] ml-auto" style={{ color: 'var(--text-dim)', opacity: 0.5 }}>
                          {new Date(n.createTime).toLocaleDateString()}
                        </span>
                      </div>
                      <p className="text-xs font-medium mb-0.5" style={{ color: 'var(--text-primary)' }}>{n.title}</p>
                      <p className="text-[11px] leading-relaxed" style={{ color: 'var(--text-dim)' }}>{n.content}</p>
                    </div>
                  ))
                )}
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </div>
  )
}
