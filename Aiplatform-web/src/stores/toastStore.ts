import { create } from 'zustand'

export interface ToastItem {
  id: string
  title: string
  message: string
  type: 'error' | 'warning' | 'info' | 'success'
  duration?: number
}

interface ToastState {
  toasts: ToastItem[]
  showToast: (t: Omit<ToastItem, 'id'>) => void
  removeToast: (id: string) => void
}

let toastId = 0

export const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  showToast: (t) => {
    const id = String(++toastId)
    const duration = t.duration ?? (t.type === 'error' ? 6000 : 4000)
    set((s) => ({ toasts: [...s.toasts, { ...t, id }] }))
    if (duration > 0) setTimeout(() => {
      set((s) => ({ toasts: s.toasts.filter(x => x.id !== id) }))
    }, duration)
  },
  removeToast: (id) => set((s) => ({ toasts: s.toasts.filter(x => x.id !== id) })),
}))
