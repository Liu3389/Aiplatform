import { create } from 'zustand'
import type { UserInfo } from '@/types'
import { userApi } from '@/services/api'

interface AppState {
  token: string | null
  user: UserInfo | null
  isLoading: boolean
  sidebarCollapsed: boolean
  mobileSidebarOpen: boolean
  settingsPanelOpen: boolean

  setToken: (token: string | null) => void
  setUserId: (uid: number) => void
  setUser: (user: UserInfo | null) => void
  logout: () => void
  toggleSidebar: () => void
  closeMobileSidebar: () => void
  openMobileSidebar: () => void
  openSettingsPanel: () => void
  closeSettingsPanel: () => void
  toggleSettingsPanel: () => void
  fetchUserInfo: () => Promise<void>
}

export const useAppStore = create<AppState>((set, get) => ({
  token: localStorage.getItem('lingdian_token'),
  user: null,
  isLoading: false,
  sidebarCollapsed: false,
  mobileSidebarOpen: false,
  settingsPanelOpen: false,

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

    // 会话缓存按用户 ID 隔离，退出时保留各账号自己的历史，下一次登录可自动恢复。
    // 仅清理旧版本未隔离的全局键，避免旧账号消息继续泄漏到新账号。
    localStorage.removeItem('ld_conversations')
    localStorage.removeItem('ld_current_conv')
    localStorage.removeItem('ld_deleted_conv_ids')
    localStorage.removeItem('ld_conv_server_version')
    localStorage.removeItem('ld_sidebar_collapsed')
    localStorage.removeItem('ld_chat_model')

    set({ token: null, user: null, settingsPanelOpen: false, mobileSidebarOpen: false })
  },

  toggleSidebar: () => set((s) => ({ sidebarCollapsed: !s.sidebarCollapsed })),
  closeMobileSidebar: () => set({ mobileSidebarOpen: false }),
  openMobileSidebar: () => set({ mobileSidebarOpen: true }),

  // 右边栏设置面板：打开时同时关闭手机端左侧栏（避免遮挡）
  openSettingsPanel: () => set({ settingsPanelOpen: true, mobileSidebarOpen: false }),
  closeSettingsPanel: () => set({ settingsPanelOpen: false }),
  toggleSettingsPanel: () => {
    const open = !get().settingsPanelOpen
    set(open
      ? { settingsPanelOpen: true, mobileSidebarOpen: false }
      : { settingsPanelOpen: false })
  },

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
          emailVerified: info.emailVerified === true,
          phone: info.phone,
          phoneVerified: info.phoneVerified === true,
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
