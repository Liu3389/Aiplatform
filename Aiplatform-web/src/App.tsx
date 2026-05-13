import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useEffect } from 'react'
import { useAppStore } from '@/stores/appStore'
import AppLayout from '@/components/layout/AppLayout'
import HomePage from '@/pages/HomePage'
import LoginPage from '@/pages/LoginPage'
import RegisterPage from '@/pages/RegisterPage'
import ChatPage from '@/pages/ChatPage'
import DashboardPage from '@/pages/DashboardPage'
import ApiKeysPage from '@/pages/ApiKeysPage'
import UserCenterPage from '@/pages/UserCenterPage'
import AdminPage from '@/pages/AdminPage'
import AdminAnalyticsPage from '@/pages/AdminAnalyticsPage'
import PretextWordCloudPage from '@/pages/PretextWordCloudPage'
import SecurityPage from '@/pages/SecurityPage'
import OAuthCallbackPage from '@/pages/OAuthCallbackPage'
import ModelStorePage from '@/pages/ModelStorePage'
import BillingPage from '@/pages/BillingPage'

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
          <Route index element={<HomePage />} />
          <Route path="chat" element={<ChatPage />} />
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="api-keys" element={<ApiKeysPage />} />
          <Route path="user" element={<UserCenterPage />} />
          <Route path="wordcloud" element={<PretextWordCloudPage />} />
          <Route path="store" element={<ModelStorePage />} />
          <Route path="billing" element={<BillingPage />} />
          <Route path="security" element={<SecurityPage />} />
          <Route path="analytics" element={<AdminRoute><AdminAnalyticsPage /></AdminRoute>} />
          <Route path="admin" element={<AdminRoute><AdminPage /></AdminRoute>} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
        <Route path="/oauth/callback" element={<OAuthCallbackPage />} />
      </Routes>
    </BrowserRouter>
  )
}
