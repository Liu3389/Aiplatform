import { useEffect, useRef, useCallback } from 'react'

export interface SseNotification {
  id: number
  userId: number
  title: string
  content: string
  type: string
  isRead: number
  createTime: string
  relatedId?: number | null
  metadata?: string | null
}

interface UseNotificationSseOptions {
  /** 收到新通知时的回调 */
  onNotification: (n: SseNotification) => void
  /** 是否启用（默认 true），未登录或未渲染时设为 false */
  enabled?: boolean
}

/**
 * 订阅通知实时推送（SSE）。
 *
 * 通过 EventSource 连接后端 /user/notifications/sse 端点，收到 'notification' 事件
 * 即时回调。EventSource 不支持自定义头，token 通过 query 传递。
 *
 * 自动重连：连接异常关闭时按指数退避（1s → 2s → 4s → … 最大 30s）重试，
 * 连接成功后重置退避值。组件卸载或 token 失效时清理连接。
 */
export function useNotificationSse({ onNotification, enabled = true }: UseNotificationSseOptions) {
  const eventSourceRef = useRef<EventSource | null>(null)
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const reconnectDelayRef = useRef(1000)
  const onNotificationRef = useRef(onNotification)

  // 保持回调引用最新，避免重连时使用旧闭包
  useEffect(() => {
    onNotificationRef.current = onNotification
  }, [onNotification])

  const cleanup = useCallback(() => {
    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current)
      reconnectTimerRef.current = null
    }
    if (eventSourceRef.current) {
      eventSourceRef.current.close()
      eventSourceRef.current = null
    }
  }, [])

  const connect = useCallback(() => {
    const token = localStorage.getItem('lingdian_token')
    if (!token || !enabled) return

    cleanup()

    try {
      const es = new EventSource(`/api/user/notifications/sse?token=${encodeURIComponent(token)}`)
      eventSourceRef.current = es

      es.addEventListener('notification', (e: MessageEvent) => {
        try {
          const data = JSON.parse(e.data) as SseNotification
          onNotificationRef.current(data)
        } catch (err) {
          console.warn('[NotificationSSE] 解析通知数据失败:', err)
        }
      })

      es.addEventListener('open', () => {
        // 连接成功，重置退避
        reconnectDelayRef.current = 1000
      })

      es.addEventListener('error', () => {
        // EventSource 在 onerror 后会自动尝试重连，但若 readyState=CLOSED 说明服务端关闭了连接，需手动重连
        if (es.readyState === EventSource.CLOSED) {
          cleanup()
          const delay = Math.min(reconnectDelayRef.current, 30000)
          reconnectTimerRef.current = setTimeout(() => {
            reconnectDelayRef.current = Math.min(reconnectDelayRef.current * 2, 30000)
            connect()
          }, delay)
        }
      })
    } catch (err) {
      console.warn('[NotificationSSE] 创建连接失败:', err)
    }
  }, [cleanup, enabled])

  useEffect(() => {
    if (!enabled) {
      cleanup()
      return
    }
    connect()
    return cleanup
  }, [connect, cleanup, enabled])
}
