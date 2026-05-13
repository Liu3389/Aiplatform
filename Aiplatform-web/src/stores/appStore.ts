import { create } from 'zustand'
import type { UserInfo } from '@/types'
import { userApi } from '@/services/api'

interface AppState {
  token: string | null
  user: UserInfo | null
  isLoading: boolean
  sidebarCollapsed: boolean

  setToken: (token: string | null) => void
  setUserId: (uid: number) => void
  setUser: (user: UserInfo | null) => void
  logout: () => void
  toggleSidebar: () => void
  fetchUserInfo: () => Promise<void>
}

export const useAppStore = create<AppState>((set, get) => ({
  token: localStorage.getItem('lingdian_token'),
  user: null,
  isLoading: false,
  sidebarCollapsed: false,

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
    set({ token: null, user: null })
  },

  toggleSidebar: () => set((s) => ({ sidebarCollapsed: !s.sidebarCollapsed })),

  fetchUserInfo: async () => {
    const { token } = get()
    if (!token) return
    set({ isLoading: true })
    try {
      const info = await userApi.getInfo()
      set({
        user: {
          id: info.id,
          username: info.username,
          email: info.email,
          balance: info.balance,
          role: info.role as UserInfo['role'],
          status: info.status as UserInfo['status'],
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
