import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useEffect, Suspense, lazy } from 'react'
import { useAppStore } from '@/stores/appStore'
import AppLayout from '@/components/layout/AppLayout'
import LoginPage from '@/pages/LoginPage'
import RegisterPage from '@/pages/RegisterPage'

const HomePage = lazy(() => import('@/pages/HomePage'))
const ChatPage = lazy(() => import('@/pages/ChatPage'))
const DashboardPage = lazy(() => import('@/pages/DashboardPage'))
const ApiKeysPage = lazy(() => import('@/pages/ApiKeysPage'))
const UserCenterPage = lazy(() => import('@/pages/UserCenterPage'))
const AdminPage = lazy(() => import('@/pages/AdminPage'))

const PretextWordCloudPage = lazy(() => import('@/pages/PretextWordCloudPage'))
const SecurityPage = lazy(() => import('@/pages/SecurityPage'))
const ModelStorePage = lazy(() => import('@/pages/ModelStorePage'))
const BillingPage = lazy(() => import('@/pages/BillingPage'))
const RechargePage = lazy(() => import('@/pages/RechargePage'))

function PageLoader() {
  return (
    <div className="flex items-center justify-center h-full">
      <div className="w-5 h-5 border-2 border-brand-purple/30 border-t-brand-purple rounded-full animate-spin" />
    </div>
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
  const { token, fetchUserInfo } = useAppStore()

  useEffect(() => {
    if (token) fetchUserInfo()
  }, [token])

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/" element={<ProtectedRoute><AppLayout /></ProtectedRoute>}>
          <Route index element={<Suspense fallback={<PageLoader />}><HomePage /></Suspense>} />
          <Route path="chat" element={<Suspense fallback={<PageLoader />}><ChatPage /></Suspense>} />
          <Route path="dashboard" element={<Suspense fallback={<PageLoader />}><DashboardPage /></Suspense>} />
          <Route path="api-keys" element={<Suspense fallback={<PageLoader />}><ApiKeysPage /></Suspense>} />
          <Route path="user" element={<Suspense fallback={<PageLoader />}><UserCenterPage /></Suspense>} />
          <Route path="wordcloud" element={<Suspense fallback={<PageLoader />}><PretextWordCloudPage /></Suspense>} />
          <Route path="store" element={<Suspense fallback={<PageLoader />}><ModelStorePage /></Suspense>} />
          <Route path="billing" element={<Suspense fallback={<PageLoader />}><BillingPage /></Suspense>} />
          <Route path="recharge" element={<Suspense fallback={<PageLoader />}><RechargePage /></Suspense>} />
          <Route path="security" element={<Suspense fallback={<PageLoader />}><SecurityPage /></Suspense>} />
          
          <Route path="admin" element={<AdminRoute><Suspense fallback={<PageLoader />}><AdminPage /></Suspense></AdminRoute>} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
        <Route path="/oauth/callback" element={<Suspense fallback={<PageLoader />}><OAuthCallbackPage /></Suspense>} />
      </Routes>
    </BrowserRouter>
  )
}

const OAuthCallbackPage = lazy(() => import('@/pages/OAuthCallbackPage'))
