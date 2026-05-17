import { create } from 'zustand'
import type { UserInfo } from '@/types'
import { userApi } from '@/services/api'

interface AppState {
  token: string | null
  user: UserInfo | null
  isLoading: boolean
  sidebarCollapsed: boolean
  mobileSidebarOpen: boolean

  setToken: (token: string | null) => void
  setUserId: (uid: number) => void
  setUser: (user: UserInfo | null) => void
  logout: () => void
  toggleSidebar: () => void
  closeMobileSidebar: () => void
  openMobileSidebar: () => void
  fetchUserInfo: () => Promise<void>
}

export const useAppStore = create<AppState>((set, get) => ({
  token: localStorage.getItem('lingdian_token'),
  user: null,
  isLoading: false,
  sidebarCollapsed: false,
  mobileSidebarOpen: false,

  setToken: (token) => {
    if (token) {
      localStorage.setItem('lingdian_token', token)
    } else {
      localStorage.removeItem('lingdian_token')
      localStorage.removeItem('lingdian_uid')
    }
    set({ token })
  },

  setUserId: (uid: number) => {
    localStorage.setItem('lingdian_uid', String(uid))
  },

  setUser: (user) => set({ user }),

  logout: () => {
    // 清除认证信息
    localStorage.removeItem('lingdian_token')
    localStorage.removeItem('lingdian_uid')
    sessionStorage.removeItem('ld_active_apikey')
    sessionStorage.removeItem('lingdian_chat_version')
    
    // 清除用户相关的聊天数据
    const uid = get().user?.id
    if (uid) {
      localStorage.removeItem('lingdian_chat_conversations_' + uid)
      localStorage.removeItem('lingdian_chat_current_' + uid)
    }
    
    // 清除所有按token隔离的本地缓存
    const keysToRemove = []
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i)
      if (key && (
        key.startsWith('ld_conversations_') || 
        key.startsWith('ld_current_conv_') ||
        key.startsWith('lingdian_chat_')
      )) {
        keysToRemove.push(key)
      }
    }
    keysToRemove.forEach(key => localStorage.removeItem(key))
    
    // 清除其他用户相关缓存
    localStorage.removeItem('ld_sidebar_collapsed')
    localStorage.removeItem('ld_chat_model')
    localStorage.removeItem('ld_conv_server_version')
    
    set({ token: null, user: null })
  },

  toggleSidebar: () => set((s) => ({ sidebarCollapsed: !s.sidebarCollapsed })),
  closeMobileSidebar: () => set({ mobileSidebarOpen: false }),
  openMobileSidebar: () => set({ mobileSidebarOpen: true }),

  fetchUserInfo: async () => {
    const { token, setUserId } = get()
    if (!token) return
    set({ isLoading: true })
    try {
      const info = await userApi.getInfo()
      setUserId(info.id)
      set({
        user: {
          id: info.id,
          username: info.username,
          email: info.email,
          balance: info.balance,
          role: info.role as UserInfo['role'],
          status: (String(info.status) === '1' || info.status === 'ACTIVE' ? 'ACTIVE' : 'DISABLED') as UserInfo['status'],
          membership: (info.membership?.toLowerCase() || 'free') as UserInfo['membership'],
          freeApiStrategy: (info.freeApiStrategy || 'NONE') as UserInfo['freeApiStrategy'],
          freeQuota: 0,
          dailyCallLimit: 0,
          monthlyCallLimit: 0,
          points: info.points || 0,
          membershipExpireTime: info.membershipExpireTime,
          createTime: info.createTime,
        },
      })
    } catch {
      get().logout()
    } finally {
      set({ isLoading: false })
    }
  },
}))
