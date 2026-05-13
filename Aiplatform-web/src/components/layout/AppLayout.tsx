import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAppStore } from '@/stores/appStore'
import { useThemeStore } from '@/stores/themeStore'
import { useI18nStore } from '@/stores/i18nStore'
import { motion, AnimatePresence } from 'framer-motion'
import ErrorBoundary from '@/components/ErrorBoundary'

type NavKey = 'overview' | 'dashboard' | 'tokens' | 'chat' | 'store' | 'wordcloud' | 'users' | 'security'

const menuItems: { path: string; key: NavKey; icon: string }[] = [
  { path: '/', key: 'overview', icon: 'M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-4 0h4' },
  { path: '/dashboard', key: 'dashboard', icon: 'M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z' },
  { path: '/api-keys', key: 'tokens', icon: 'M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z' },
  { path: '/chat', key: 'chat', icon: 'M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z' },
  { path: '/store', key: 'store', icon: 'M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z' },
  { path: '/wordcloud', key: 'wordcloud', icon: 'M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-4-4h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z' },
  { path: '/admin', key: 'users', icon: 'M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z' },
  { path: '/security', key: 'security', icon: 'M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z' },
]

const navLabels: Record<string, Record<NavKey, string>> = {
  zh: { overview:'概览', dashboard:'控制台', tokens:'令牌管理', chat:'AI 对话', store:'模型商城', wordcloud:'词云可视化', users:'用户管理', security:'账户安全' },
  en: { overview:'Overview', dashboard:'Dashboard', tokens:'API Keys', chat:'AI Chat', store:'Model Store', wordcloud:'Word Cloud', users:'Users', security:'Security' },
}

export default function AppLayout() {
  const { user, logout, sidebarCollapsed, toggleSidebar } = useAppStore()
  const { theme, toggleTheme } = useThemeStore()
  const { lang, toggleLang, t } = useI18nStore()
  const navigate = useNavigate()

  const dark = theme === 'dark'
  const textColor = dark ? 'text-neutral-400' : 'text-gray-500'
  const borderColor = dark ? 'border-white/[0.04]' : 'border-gray-200'
  const headerBg = dark ? 'rgba(10,15,26,0.5)' : 'rgba(255,255,255,0.8)'

  return (
    <div className={`flex h-screen overflow-hidden ${dark ? 'bg-surface-bg noise-bg' : 'bg-gray-50'}`}>
      <motion.aside
        animate={{ width: sidebarCollapsed ? 0 : 260 }}
        className={`relative z-30 h-screen flex-shrink-0 overflow-hidden border-r ${dark ? 'border-surface-border' : 'border-gray-200'}`}
        style={{ background: dark ? 'rgba(10,15,30,0.72)' : 'rgba(255,255,255,0.9)', backdropFilter: 'blur(20px)', WebkitBackdropFilter: 'blur(20px)' }}
      >
        <div className="flex flex-col h-full w-[260px]">
          <div className={`h-[72px] flex items-center gap-3 px-6 border-b ${dark ? 'border-white/[0.05]' : 'border-gray-200'}`}>
            <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-brand-purple to-brand-blue flex items-center justify-center shadow-lg shadow-brand-purple/25">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
            </div>
            <div>
              <div className={`text-sm font-bold tracking-tight ${dark ? 'text-white' : 'text-gray-900'}`}>AI Gateway</div>
              <div className={`text-[10px] ${dark ? 'text-neutral-400' : 'text-gray-500'} -mt-0.5`}>{lang==='zh'?'模型调度网关':'Model Gateway'}</div>
            </div>
          </div>

          <nav className="flex-1 overflow-y-auto py-4 px-3 space-y-0.5">
            {menuItems.map((item) => (
              <NavLink key={item.path} to={item.path} end={item.path === '/'}
                className={({ isActive }) =>
                  `flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm transition-all duration-300 group border-l-[3px] ${
                    isActive
                      ? `bg-gradient-to-r from-brand-purple/20 to-brand-purple/5 border-brand-purple shadow-[0_0_15px_rgba(139,92,246,0.15)] ${dark ? 'text-white' : 'text-brand-purple-dark'}`
                      : `${dark ? 'text-neutral-400 hover:text-white' : 'text-gray-500 hover:text-gray-900'} hover:bg-black/5 hover:translate-x-1 border-transparent`
                  }`
                }
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d={item.icon}/></svg>
                {navLabels[lang]?.[item.key] || item.key}
              </NavLink>
            ))}
          </nav>

          <div className={`p-4 border-t ${dark ? 'border-white/[0.05]' : 'border-gray-200'}`}>
            <div className={`p-4 rounded-xl ${dark ? 'bg-white/[0.02]' : 'bg-gray-50'}`}>
              <div className={`text-[10px] uppercase tracking-wider mb-1 ${dark ? 'text-neutral-400' : 'text-gray-500'}`}>Token {lang==='zh'?'额度':'Quota'}</div>
              <div className={`text-sm font-bold mb-2 ${dark ? 'text-white' : 'text-gray-900'}`}>{user?.balance!=null?`$${Number(user.balance).toFixed(2)}`:'--'}</div>
              <div className={`w-full h-1.5 rounded-full overflow-hidden mb-2 ${dark ? 'bg-white/[0.06]' : 'bg-gray-200'}`}>
                <div className="h-full w-3/4 bg-gradient-to-r from-brand-purple to-brand-blue rounded-full" />
              </div>
              <button onClick={logout} className={`w-full py-2 text-xs font-medium rounded-lg transition-colors ${dark ? 'text-white/60 hover:text-white bg-white/[0.04] hover:bg-white/[0.08]' : 'text-gray-500 hover:text-gray-900 bg-gray-100 hover:bg-gray-200'}`}>
                {lang==='zh'?'退出登录':'Logout'}
              </button>
            </div>
          </div>
        </div>
      </motion.aside>

      <div className="flex-1 flex flex-col overflow-hidden">
        <header className={`h-[72px] flex-shrink-0 flex items-center justify-between px-6 border-b ${borderColor}`}
          style={{ background: headerBg, backdropFilter: 'blur(12px)', WebkitBackdropFilter: 'blur(12px)' }}
        >
          <div className="flex items-center gap-4">
            <button onClick={toggleSidebar} className={`transition-colors p-1 ${dark ? 'text-neutral-400 hover:text-white' : 'text-gray-500 hover:text-gray-900'}`}>
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="18" x2="21" y2="18"/></svg>
            </button>
            <div>
              <div className={`text-sm font-medium ${dark ? 'text-white' : 'text-gray-900'}`}>{t('header.greeting')}，{user?.username || 'Admin'}</div>
              <span className={`inline-block px-2 py-0.5 rounded-full text-[10px] font-medium ${dark ? 'bg-brand-purple/15 text-brand-purple border border-brand-purple/20' : 'bg-brand-purple/10 text-brand-purple border border-brand-purple/20'}`}>
                {user?.role === 'SUPER_ADMIN' ? (lang==='zh'?'超级管理员':'Super Admin') : user?.role === 'ADMIN' ? (lang==='zh'?'管理员':'Admin') : (lang==='zh'?'用户':'User')}
              </span>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-accent-emerald/10 border border-accent-emerald/20">
              <span className="w-1.5 h-1.5 rounded-full bg-accent-emerald animate-glow-pulse"/><span className="text-[11px] text-accent-emerald font-medium">{t('header.status')}</span></div>

            <button onClick={toggleTheme} className={`p-2 rounded-lg transition-all ${dark ? 'text-neutral-400 hover:text-white' : 'text-gray-500 hover:text-gray-900'}`} title={dark ? t('theme.light') : t('theme.dark')}>
              {dark ? <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg>
              : <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 12.79A9 9 0 1111.21 3 7 7 0 0021 12.79z"/></svg>}
            </button>
            <button onClick={toggleLang} className={`px-2 py-1 rounded-lg text-xs font-medium transition-all ${dark ? 'text-neutral-400 hover:text-white bg-white/[0.03] hover:bg-white/[0.06]' : 'text-gray-500 hover:text-gray-900 bg-gray-100 hover:bg-gray-200'}`}>
              {lang === 'zh' ? 'EN' : '中'}
            </button>

            <div className="flex items-center gap-2 cursor-pointer" onClick={() => navigate('/user')}>
              <div className="w-8 h-8 rounded-full bg-gradient-to-br from-brand-purple to-brand-blue flex items-center justify-center text-xs font-bold text-white shadow-lg shadow-brand-purple/25">{(user?.username||'A')[0].toUpperCase()}</div>
            </div>
          </div>
        </header>

        <main className={`flex-1 overflow-y-auto ${dark ? 'bg-aurora' : 'bg-gradient-to-br from-gray-50 via-white to-gray-50'}`}>
          <ErrorBoundary>
            <AnimatePresence mode="wait">
              <motion.div key={location.pathname} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -12 }} transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }} className="h-full">
                <Outlet />
              </motion.div>
            </AnimatePresence>
          </ErrorBoundary>
        </main>
      </div>
    </div>
  )
}
