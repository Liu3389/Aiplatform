import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import './styles/design-system.css'
import './styles/index.css'

const savedTheme = localStorage.getItem('lingdian_theme')
const theme = savedTheme === 'dark' || savedTheme === 'light' ? savedTheme : 'light'
document.documentElement.classList.add(theme)

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
