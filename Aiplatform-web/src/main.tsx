import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import './styles/design-system.css'
import './styles/index.css'

const savedTheme = localStorage.getItem('lingdian_theme')
const theme = savedTheme === 'dark' || savedTheme === 'light' ? savedTheme : 'light'
document.documentElement.classList.add(theme)

// 新的 Service Worker 接管页面后自动刷新一次，避免用户长时间停留在旧版登录页。
if ('serviceWorker' in navigator && navigator.serviceWorker.controller) {
  let refreshing = false
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (refreshing) return
    refreshing = true
    window.location.reload()
  })
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
