import { useMemo, useCallback, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAppStore } from '@/stores/appStore'
import { useThemeStore } from '@/stores/themeStore'
import { useI18nStore } from '@/stores/i18nStore'
import { motion, AnimatePresence } from 'framer-motion'
import NotificationBell from '@/components/NotificationBell'
import ConfirmDialog from '@/components/ConfirmDialog'

const ADMIN_ROLES = ['SUPER_ADMIN', 'ADMIN', 'admin']

export default function AppLayout() {
  const { user, logout } = useAppStore()
  const { theme, toggleTheme } = useThemeStore()
  const { lang, toggleLang, t } = useI18nStore()
  const location = useLocation()
  const navigate = useNavigate()
  const isDark = theme === 'dark'
  const isAdmin = user?.role && ADMIN_ROLES.includes(user.role)

  const navItems = useMemo(() => {
    const items = [
      { path: '/chat', label: t('AI 对话', 'AI Chat'), icon: 'M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z' },
      { path: '/recharge', label: t('充值/升级', 'Recharge'), icon: 'M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z' },
      { path: '/dashboard', label: t('用量', 'Usage'), icon: 'M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z' },
      { path: '/store', label: t('模型商城', 'Store'), icon: 'M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4' },
      { path: '/user', label: t('个人中心', 'Profile'), icon: 'M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z' },
    ]
    if (isAdmin) {
      items.push({ path: '/admin', label: t('管理', 'Admin'), icon: 'M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z' })
    }
    return items
  }, [t, isAdmin])

  const isActive = useCallback((path: string) => {
    if (path === '/welcome') return location.pathname === '/welcome'
    return location.pathname.startsWith(path)
  }, [location.pathname])

  const handleLogout = useCallback(() => {
    setLogoutConfirmOpen(true)
  }, [])

  const confirmLogout = useCallback(() => {
    setLogoutConfirmOpen(false)
    logout()
    navigate('/login', { replace: true })
  }, [logout, navigate])

  const cancelLogout = useCallback(() => {
    setLogoutConfirmOpen(false)
  }, [])

  const [logoutConfirmOpen, setLogoutConfirmOpen] = useState(false)

  const handleLangToggle = useCallback(() => {
    toggleLang()
  }, [toggleLang])

  return (
    <div className="flex flex-col h-screen overflow-hidden" style={{ background: 'var(--bg-primary)' }}>
      {/* Top Navigation */}
      <header className="flex-shrink-0 h-14 flex items-center justify-between px-4 lg:px-6 border-b"
        style={{
          borderColor: 'var(--border-color)',
          background: 'var(--bg-secondary)',
          backdropFilter: 'blur(16px)',
          WebkitBackdropFilter: 'blur(16px)',
        }}>
        {/* Logo */}
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate('/welcome')}
            className="w-8 h-8 rounded-lg flex items-center justify-center shadow-lg transition-all hover:scale-105 active:scale-95"
            style={{
              background: 'linear-gradient(135deg, #8B5CF6, #6366F1)',
              boxShadow: '0 4px 16px rgba(139,92,246,0.25)',
            }}
            title={t('返回欢迎页', 'Back to Welcome')}
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
          </button>
          <span className="hidden sm:inline text-[15px] font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
            凌点 AI
          </span>
        </div>

        {/* Nav links — desktop */}
        <nav className="hidden md:flex items-center gap-1">
          {navItems.map(item => {
            const active = isActive(item.path)
            return (
              <button
                key={item.path}
                onClick={() => navigate(item.path)}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[12px] font-medium transition-all duration-200"
                style={{
                  background: active ? 'var(--bg-active)' : 'transparent',
                  color: active ? 'var(--accent-purple)' : 'var(--text-dim)',
                  border: active ? '1px solid var(--border-accent)' : '1px solid transparent',
                }}
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
                  strokeLinecap="round" strokeLinejoin="round"><path d={item.icon}/></svg>
                <span className="hidden lg:inline">{item.label}</span>
              </button>
            )
          })}
        </nav>

        {/* Right actions */}
        <div className="flex items-center gap-1.5">
          <NotificationBell />
          <button onClick={handleLangToggle} className="p-2 rounded-lg text-[11px] font-medium transition-all hover:scale-105"
            style={{ color: 'var(--text-dim)', background: 'var(--hover-bg)' }}>
            {lang === 'zh' ? 'EN' : '中'}
          </button>
          <button onClick={toggleTheme} className="p-2 rounded-lg transition-all hover:scale-110" style={{ color: 'var(--text-dim)' }}>
            {isDark ? (
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/></svg>
            ) : (
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 12.79A9 9 0 1111.21 3 7 7 0 0021 12.79z"/></svg>
            )}
          </button>
          <button onClick={() => navigate('/user')} className="hidden sm:flex items-center gap-2 p-1 rounded-lg transition-all hover:bg-[var(--hover-bg)]" title={t('个人中心', 'Profile')}>
            <div className="w-7 h-7 rounded-full flex items-center justify-center text-[10px] font-bold text-white"
              style={{ background: 'linear-gradient(135deg, #10B981, #059669)' }}>
              {user?.username?.[0]?.toUpperCase() || 'U'}
            </div>
            <span className="text-[12px] font-medium hidden lg:inline" style={{ color: 'var(--text-secondary)' }}>
              {user?.username}
            </span>
          </button>
          <button onClick={handleLogout}
            className="px-3 py-1.5 rounded-lg text-[11px] font-medium transition-all hover:opacity-80 active:scale-95"
            style={{ color: 'var(--accent-rose)', background: 'var(--accent-rose-soft)' }}>
            {t('退出', 'Logout')}
          </button>
        </div>
      </header>

      {/* Page content */}
      <main className="flex-1 overflow-hidden">
        <AnimatePresence mode="wait">
          <motion.div
            key={location.pathname}
            initial={{ opacity: 0, scale: 0.98 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 1.02 }}
            transition={{ duration: 0.25, ease: [0.34, 1.56, 0.64, 1] }}
            className="h-full"
          >
            <Outlet />
          </motion.div>
        </AnimatePresence>
      </main>

      {/* Mobile bottom nav - 5 items max for small screens */}
      <nav className="md:hidden flex-shrink-0 h-14 flex items-center justify-around border-t safe-area-inset-bottom"
        style={{
          borderColor: 'var(--border-color)',
          background: 'var(--bg-secondary)',
          backdropFilter: 'blur(16px)',
          WebkitBackdropFilter: 'blur(16px)',
          paddingBottom: 'env(safe-area-inset-bottom, 0)',
        }}>
        {[
          { path: '/chat', label: t('对话', 'Chat'), icon: 'M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z' },
          { path: '/recharge', label: t('充值', 'Recharge'), icon: 'M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z' },
          { path: '/dashboard', label: t('用量', 'Usage'), icon: 'M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z' },
          ...(isAdmin ? [{ path: '/admin', label: t('管理', 'Admin'), icon: 'M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z' }] : []),
          { path: '/user', label: t('我的', 'Me'), icon: 'M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z' },
        ].map(item => {
          const active = isActive(item.path)
          return (
            <button
              key={item.path}
              onClick={() => navigate(item.path)}
              className="flex flex-col items-center justify-center gap-0.5 transition-all active:scale-90 min-w-0 flex-1 py-1"
              style={{ color: active ? 'var(--accent-purple)' : 'var(--text-dim)' }}
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
                strokeLinecap="round" strokeLinejoin="round"><path d={item.icon}/></svg>
              <span className="text-[10px] font-medium truncate max-w-full leading-none">{item.label}</span>
            </button>
          )
        })}
      </nav>

      <ConfirmDialog
        open={logoutConfirmOpen}
        title={lang === 'zh' ? '确认退出' : 'Confirm Logout'}
        message={lang === 'zh' ? '确定要退出登录吗？退出后需要重新登录才能继续使用。' : 'Are you sure you want to logout? You will need to login again to continue.'}
        confirmText={lang === 'zh' ? '退出登录' : 'Logout'}
        cancelText={lang === 'zh' ? '取消' : 'Cancel'}
        variant="warning"
        onConfirm={confirmLogout}
        onCancel={cancelLogout}
      />
    </div>
  )
}