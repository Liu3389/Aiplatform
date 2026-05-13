import { create } from 'zustand'

type Theme = 'dark' | 'light'

interface ThemeState {
  theme: Theme
  toggleTheme: () => void
  setTheme: (theme: Theme) => void
}

const applyTheme = (theme: Theme) => {
  const root = document.documentElement
  if (theme === 'dark') {
    root.classList.remove('light')
    root.classList.add('dark')
    root.style.setProperty('--bg-primary', '#0B1020')
    root.style.setProperty('--bg-secondary', 'rgba(10,15,30,0.72)')
    root.style.setProperty('--bg-card', 'rgba(17,24,39,0.7)')
    root.style.setProperty('--bg-input', 'rgba(17,24,39,0.6)')
    root.style.setProperty('--bg-surface', 'rgba(17,24,39,0.5)')
    root.style.setProperty('--bg-glass', 'rgba(17,24,39,0.6)')
    root.style.setProperty('--border-color', 'rgba(255,255,255,0.06)')
    root.style.setProperty('--border-light', 'rgba(255,255,255,0.04)')
    root.style.setProperty('--text-primary', '#F8FAFC')
    root.style.setProperty('--text-secondary', '#E2E8F0')
    root.style.setProperty('--text-muted', '#94A3B8')
    root.style.setProperty('--text-dim', '#64748B')
    root.style.setProperty('--text-surface', '#94A3B8')
    root.style.setProperty('--accent-purple', '#8B5CF6')
    root.style.setProperty('--accent-blue', '#3B82F6')
    root.style.setProperty('--accent-cyan', '#06B6D4')
    root.style.setProperty('--accent-emerald', '#10B981')
    root.style.setProperty('--accent-rose', '#F43F5E')
    root.style.setProperty('--accent-amber', '#F59E0B')
    root.style.setProperty('--pretext-bg', '#0f172a')
    root.style.setProperty('--pretext-text', '#e2e8f0')
    root.style.setProperty('--pretext-muted', '#94a3b8')
    root.style.setProperty('--pretext-bold', '#f1f5f9')
    root.style.setProperty('--pretext-code', '#a5b4fc')
    root.style.setProperty('--pretext-code-bg', 'rgba(99, 102, 241, 0.15)')
    root.style.setProperty('--pretext-heading', '#f1f5f9')
    root.style.setProperty('--pretext-block-bg', 'rgba(15, 23, 42, 0.8)')
    root.style.setProperty('--pretext-block-border', 'rgba(99, 102, 241, 0.2)')
    document.body.style.background = '#0B1020'
    document.body.style.color = '#E2E8F0'
  } else {
    root.classList.remove('dark')
    root.classList.add('light')
    root.style.setProperty('--bg-primary', '#F8FAFC')
    root.style.setProperty('--bg-secondary', 'rgba(255,255,255,0.9)')
    root.style.setProperty('--bg-card', 'rgba(255,255,255,0.85)')
    root.style.setProperty('--bg-input', 'rgba(255,255,255,0.8)')
    root.style.setProperty('--bg-surface', 'rgba(255,255,255,0.7)')
    root.style.setProperty('--bg-glass', 'rgba(255,255,255,0.8)')
    root.style.setProperty('--border-color', 'rgba(0,0,0,0.08)')
    root.style.setProperty('--border-light', 'rgba(0,0,0,0.04)')
    root.style.setProperty('--text-primary', '#0F172A')
    root.style.setProperty('--text-secondary', '#1E293B')
    root.style.setProperty('--text-muted', '#475569')
    root.style.setProperty('--text-dim', '#94A3B8')
    root.style.setProperty('--text-surface', '#64748B')
    root.style.setProperty('--accent-purple', '#7C3AED')
    root.style.setProperty('--accent-blue', '#2563EB')
    root.style.setProperty('--accent-cyan', '#0891B2')
    root.style.setProperty('--accent-emerald', '#059669')
    root.style.setProperty('--accent-rose', '#E11D48')
    root.style.setProperty('--accent-amber', '#D97706')
    root.style.setProperty('--pretext-bg', '#F1F5F9')
    root.style.setProperty('--pretext-text', '#1E293B')
    root.style.setProperty('--pretext-muted', '#64748B')
    root.style.setProperty('--pretext-bold', '#0F172A')
    root.style.setProperty('--pretext-code', '#4F46E5')
    root.style.setProperty('--pretext-code-bg', 'rgba(99, 102, 241, 0.1)')
    root.style.setProperty('--pretext-heading', '#0F172A')
    root.style.setProperty('--pretext-block-bg', 'rgba(241,245,249,0.9)')
    root.style.setProperty('--pretext-block-border', 'rgba(99, 102, 241, 0.15)')
    document.body.style.background = '#F8FAFC'
    document.body.style.color = '#1E293B'
  }
}

const getInitialTheme = (): Theme => {
  const saved = localStorage.getItem('lingdian_theme') as Theme
  if (saved) return saved
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'dark'
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
