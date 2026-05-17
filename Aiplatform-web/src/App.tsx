import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { useEffect, Suspense, lazy } from 'react'
import { useAppStore } from '@/stores/appStore'
import AppLayout from '@/components/layout/AppLayout'
import ErrorBoundary from '@/components/ErrorBoundary'
import LoginPage from '@/pages/LoginPage'
import RegisterPage from '@/pages/RegisterPage'

const HomePage = lazy(() => import('@/pages/HomePage'))
const ChatPage = lazy(() => import('@/pages/chat/ChatPage'))
const DashboardPage = lazy(() => import('@/pages/DashboardPage'))
const ApiKeysPage = lazy(() => import('@/pages/ApiKeysPage'))
const UserCenterPage = lazy(() => import('@/pages/UserCenterPage'))
const AdminPage = lazy(() => import('@/pages/AdminPage'))
const PretextWordCloudPage = lazy(() => import('@/pages/PretextWordCloudPage'))
const ModelStorePage = lazy(() => import('@/pages/ModelStorePage'))
const BillingPage = lazy(() => import('@/pages/BillingPage'))
const RechargePage = lazy(() => import('@/pages/RechargePage'))
const OAuthCallbackPage = lazy(() => import('@/pages/OAuthCallbackPage'))

function PageLoader() {
  return (
    <div className="flex items-center justify-center h-full min-h-[200px]">
      <div className="flex flex-col items-center gap-3">
        <div className="w-6 h-6 border-2 border-brand-purple/30 border-t-brand-purple rounded-full animate-spin" />
        <span className="text-xs" style={{ color: 'var(--text-dim)' }}>加载中...</span>
      </div>
    </div>
  )
}

function LazyRoute({ children }: { children: React.ReactNode }) {
  const location = useLocation()
  return (
    <ErrorBoundary key={location.pathname}>
      <Suspense fallback={<PageLoader />}>
        {children}
      </Suspense>
    </ErrorBoundary>
  )
}

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const token = useAppStore((s) => s.token)
  if (!token) return <Navigate to="/login" replace />
  return <>{children}</>
}

function AdminRoute({ children }: { children: React.ReactNode }) {
  const { token, user } = useAppStore()
  if (!token) return <Navigate to="/login" replace />
  if (user && user.role !== 'SUPER_ADMIN' && user.role !== 'ADMIN') {
    return <Navigate to="/" replace />
  }
  return <>{children}</>
}

export default function App() {
  const { token, fetchUserInfo, logout } = useAppStore()

  useEffect(() => {
    if (token) fetchUserInfo()
  }, [token])

  useEffect(() => {
    const onStorageChange = (e: StorageEvent) => {
      if (e.key === 'lingdian_token' && !e.newValue) {
        logout()
      }
    }
    window.addEventListener('storage', onStorageChange)
    return () => window.removeEventListener('storage', onStorageChange)
  }, [logout])

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/" element={<ProtectedRoute><AppLayout /></ProtectedRoute>}>
          <Route index element={<LazyRoute><HomePage /></LazyRoute>} />
          <Route path="chat" element={<LazyRoute><ChatPage /></LazyRoute>} />
          <Route path="dashboard" element={<LazyRoute><DashboardPage /></LazyRoute>} />
          <Route path="api-keys" element={<LazyRoute><ApiKeysPage /></LazyRoute>} />
          <Route path="user" element={<LazyRoute><UserCenterPage /></LazyRoute>} />
          <Route path="wordcloud" element={<LazyRoute><PretextWordCloudPage /></LazyRoute>} />
          <Route path="store" element={<LazyRoute><ModelStorePage /></LazyRoute>} />
          <Route path="billing" element={<LazyRoute><BillingPage /></LazyRoute>} />
          <Route path="recharge" element={<LazyRoute><RechargePage /></LazyRoute>} />
          <Route path="admin" element={<AdminRoute><LazyRoute><AdminPage /></LazyRoute></AdminRoute>} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
        <Route path="/oauth/callback" element={<LazyRoute><OAuthCallbackPage /></LazyRoute>} />
      </Routes>
    </BrowserRouter>
  )
}
