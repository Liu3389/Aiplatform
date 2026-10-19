import { create } from 'zustand'

type Theme = 'dark' | 'light'

interface ThemeState {
  theme: Theme
  toggleTheme: () => void
  setTheme: (theme: Theme) => void
}

const darkTokens: Record<string, string> = {
  '--bg-primary': '#080B16',
  '--bg-secondary': 'rgba(10,15,30,0.75)',
  '--bg-card': 'rgba(15,20,40,0.72)',
  '--bg-input': 'rgba(20,28,48,0.65)',
  '--bg-surface': 'rgba(18,25,44,0.55)',
  '--bg-hover': 'rgba(139,92,246,0.06)',
  '--bg-active': 'rgba(139,92,246,0.1)',

  '--surface-glass': 'rgba(18,25,44,0.5)',
  '--glass-bg': 'rgba(16,24,42,0.6)',
  '--glass-border': 'rgba(255,255,255,0.07)',
  '--glass-border-subtle': 'rgba(255,255,255,0.04)',

  '--border-color': 'rgba(255,255,255,0.06)',
  '--border-light': 'rgba(255,255,255,0.03)',
  '--border-focus': 'rgba(139,92,246,0.35)',
  '--border-accent': 'rgba(139,92,246,0.2)',

  '--text-primary': '#F1F5F9',
  '--text-secondary': '#CBD5E1',
  '--text-muted': '#94A3B8',
  '--text-dim': '#64748B',
  '--text-surface': '#94A3B8',
  '--text-inverse': '#0F172A',

  '--accent-purple': '#8B5CF6',
  '--accent-purple-soft': 'rgba(139,92,246,0.15)',
  '--accent-purple-glow': 'rgba(139,92,246,0.4)',
  '--accent-blue': '#3B82F6',
  '--accent-blue-soft': 'rgba(59,130,246,0.12)',
  '--accent-cyan': '#06B6D4',
  '--accent-cyan-soft': 'rgba(6,182,212,0.1)',
  '--accent-emerald': '#10B981',
  '--accent-emerald-soft': 'rgba(16,185,129,0.1)',
  '--accent-rose': '#F43F5E',
  '--accent-rose-soft': 'rgba(244,63,94,0.1)',
  '--accent-amber': '#F59E0B',
  '--accent-amber-soft': 'rgba(245,158,11,0.1)',

  '--hover-bg': 'rgba(255,255,255,0.04)',
  '--skeleton-from': 'rgba(255,255,255,0.04)',
  '--skeleton-to': 'rgba(255,255,255,0.08)',
  '--grid-dot-color': 'rgba(255,255,255,0.03)',

  '--pretext-bg': '#0f172a',
  '--pretext-text': '#e2e8f0',
  '--pretext-muted': '#94a3b8',
  '--pretext-bold': '#f1f5f9',
  '--pretext-code': '#a5b4fc',
  '--pretext-code-bg': 'rgba(99, 102, 241, 0.15)',
  '--pretext-heading': '#f1f5f9',
  '--pretext-block-bg': 'rgba(15, 23, 42, 0.8)',
  '--pretext-block-border': 'rgba(99, 102, 241, 0.2)',

  '--brand-purple': '#8B5CF6',
  '--brand-blue': '#3B82F6',
  '--brand-purple-dark': '#7C3AED',
  '--brand-cyan': '#06B6D4',
}

const lightTokens: Record<string, string> = {
  '--bg-primary': '#F8FAFC',
  '--bg-secondary': 'rgba(255,255,255,0.9)',
  '--bg-card': 'rgba(255,255,255,0.88)',
  '--bg-input': 'rgba(255,255,255,0.82)',
  '--bg-surface': 'rgba(255,255,255,0.75)',
  '--bg-hover': 'rgba(124,58,237,0.04)',
  '--bg-active': 'rgba(124,58,237,0.08)',

  '--surface-glass': 'rgba(255,255,255,0.65)',
  '--glass-bg': 'rgba(255,255,255,0.7)',
  '--glass-border': 'rgba(0,0,0,0.06)',
  '--glass-border-subtle': 'rgba(0,0,0,0.03)',

  '--border-color': 'rgba(0,0,0,0.07)',
  '--border-light': 'rgba(0,0,0,0.04)',
  '--border-focus': 'rgba(124,58,237,0.25)',
  '--border-accent': 'rgba(124,58,237,0.15)',

  '--text-primary': '#0F172A',
  '--text-secondary': '#1E293B',
  '--text-muted': '#475569',
  '--text-dim': '#64748B',
  '--text-surface': '#64748B',
  '--text-inverse': '#F8FAFC',

  '--accent-purple': '#7C3AED',
  '--accent-purple-soft': 'rgba(124,58,237,0.1)',
  '--accent-purple-glow': 'rgba(124,58,237,0.3)',
  '--accent-blue': '#2563EB',
  '--accent-blue-soft': 'rgba(37,99,235,0.08)',
  '--accent-cyan': '#0891B2',
  '--accent-cyan-soft': 'rgba(8,145,178,0.08)',
  '--accent-emerald': '#059669',
  '--accent-emerald-soft': 'rgba(5,150,105,0.08)',
  '--accent-rose': '#E11D48',
  '--accent-rose-soft': 'rgba(225,29,72,0.08)',
  '--accent-amber': '#D97706',
  '--accent-amber-soft': 'rgba(217,119,6,0.08)',

  '--hover-bg': 'rgba(0,0,0,0.03)',
  '--skeleton-from': 'rgba(0,0,0,0.04)',
  '--skeleton-to': 'rgba(0,0,0,0.08)',
  '--grid-dot-color': 'rgba(0,0,0,0.03)',

  '--pretext-bg': '#F1F5F9',
  '--pretext-text': '#1E293B',
  '--pretext-muted': '#64748B',
  '--pretext-bold': '#0F172A',
  '--pretext-code': '#4F46E5',
  '--pretext-code-bg': 'rgba(99, 102, 241, 0.1)',
  '--pretext-heading': '#0F172A',
  '--pretext-block-bg': 'rgba(241,245,249,0.9)',
  '--pretext-block-border': 'rgba(99, 102, 241, 0.15)',

  '--brand-purple': '#7C3AED',
  '--brand-blue': '#2563EB',
  '--brand-purple-dark': '#6D28D9',
  '--brand-cyan': '#0891B2',
}

const applyTheme = (theme: Theme) => {
  const root = document.documentElement
  const tokens = theme === 'dark' ? darkTokens : lightTokens

  root.classList.remove('dark', 'light')
  root.classList.add(theme)

  Object.entries(tokens).forEach(([key, value]) => {
    root.style.setProperty(key, value)
  })

  if (theme === 'dark') {
    document.body.style.background = '#080B16'
    document.body.style.color = '#CBD5E1'
  } else {
    document.body.style.background = '#F8FAFC'
    document.body.style.color = '#1E293B'
  }
}

const getInitialTheme = (): Theme => {
  const saved = localStorage.getItem('lingdian_theme') as Theme
  if (saved === 'dark' || saved === 'light') return saved
  // 首次访问固定使用明亮主题；用户主动切换后才读取并持久化选择。
  return 'light'
}

export const useThemeStore = create<ThemeState>((set, get) => {
  const initial = getInitialTheme()
  applyTheme(initial)
  return {
    theme: initial,
    toggleTheme: () => {
      const next = get().theme === 'dark' ? 'light' : 'dark'
      localStorage.setItem('lingdian_theme', next)
      applyTheme(next)
      set({ theme: next })
    },
    setTheme: (theme: Theme) => {
      localStorage.setItem('lingdian_theme', theme)
      applyTheme(theme)
      set({ theme })
    },
  }
})
