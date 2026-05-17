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
    localStorage.removeItem('lingdian_token')
    localStorage.removeItem('lingdian_uid')
    sessionStorage.removeItem('ld_active_apikey')
    sessionStorage.removeItem('lingdian_chat_version')
    const uid = get().user?.id
    if (uid) {
      localStorage.removeItem('lingdian_chat_conversations_' + uid)
      localStorage.removeItem('lingdian_chat_current_' + uid)
    }
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
          status: info.status as UserInfo['status'],
          membership: (info.membership?.toLowerCase() || 'free') as UserInfo['membership'],
          freeApiStrategy: (info.freeApiStrategy || 'NONE') as UserInfo['freeApiStrategy'],
          freeQuota: 0,
          dailyCallLimit: 0,
          monthlyCallLimit: 0,
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
