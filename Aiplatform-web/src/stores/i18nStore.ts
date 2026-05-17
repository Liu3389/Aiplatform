import { create } from 'zustand'

type Lang = 'zh' | 'en'

interface I18nState {
  lang: Lang
  toggleLang: () => void
  t: (key: string, fallbackEn?: string) => string
}

const zh: Record<string, string> = {
  'nav.overview': '概览',
  'nav.dashboard': '控制台',
  'nav.tokens': '令牌管理',
  'nav.chat': 'AI 对话',
  'nav.wordcloud': '词云可视化',
  'nav.users': '用户管理',
  'header.greeting': '晚上好',
  'header.status': '系统正常',
  'header.search': '搜索功能、文档、模型...',
  'chat.title': 'AI Chat Studio',
  'chat.placeholder': '输入消息... (⌘K 聚焦 · Enter 发送)',
  'chat.empty': '流式对话 · 自动保存 · 快捷键支持',
  'chat.send': '发送',
  'chat.clear': '清空',
  'chat.history': '对话历史自动保存',
  'kpi.requests': '今日请求量',
  'kpi.tokens': 'Token 消耗',
  'kpi.revenue': '今日收入',
  'kpi.users': '在线用户',
  'chart.trend': '请求趋势',
  'chart.models': '模型占比',
  'chart.hourly': '24小时流量分布',
  'actions.playground': 'AI Playground',
  'actions.keys': 'API Keys',
  'actions.docs': '查看文档',
  'theme.dark': '暗黑模式',
  'theme.light': '明亮模式',
  'lang.switch': 'English',
  'user.center': '个人中心',
  'user.balance': '账户余额',
  'user.recharge': '账户充值',
  'user.password': '修改密码',
  'user.logout': '退出登录',
  'user.oldPassword': '旧密码',
  'user.newPassword': '新密码',
  'user.confirmPassword': '确认密码',
  'user.changePassword': '修改密码',
  'user.settings': '个性化设置',
  'admin.search': '搜索用户...',
  'admin.batch': '批量操作',
  'overview.title': '概览',
  'overview.subtitle': 'AI Gateway · 模型调度控制台',
}

const en: Record<string, string> = {
  'nav.overview': 'Overview',
  'nav.dashboard': 'Dashboard',
  'nav.tokens': 'API Keys',
  'nav.chat': 'AI Chat',
  'nav.wordcloud': 'Word Cloud',
  'nav.users': 'Users',
  'header.greeting': 'Good evening',
  'header.status': 'System Normal',
  'header.search': 'Search features, docs, models...',
  'chat.title': 'AI Chat Studio',
  'chat.placeholder': 'Type a message... (⌘K focus · Enter send)',
  'chat.empty': 'Streaming · Auto-save · Shortcuts',
  'chat.send': 'Send',
  'chat.clear': 'Clear',
  'chat.history': 'Chat history auto-saved',
  'kpi.requests': 'Requests Today',
  'kpi.tokens': 'Token Usage',
  'kpi.revenue': 'Revenue Today',
  'kpi.users': 'Online Users',
  'chart.trend': 'Request Trend',
  'chart.models': 'Model Distribution',
  'chart.hourly': '24h Traffic',
  'actions.playground': 'AI Playground',
  'actions.keys': 'API Keys',
  'actions.docs': 'Documentation',
  'theme.dark': 'Dark Mode',
  'theme.light': 'Light Mode',
  'lang.switch': '中文',
  'user.center': 'User Center',
  'user.balance': 'Balance',
  'user.recharge': 'Recharge',
  'user.password': 'Change Password',
  'user.logout': 'Logout',
  'user.oldPassword': 'Old Password',
  'user.newPassword': 'New Password',
  'user.confirmPassword': 'Confirm Password',
  'user.changePassword': 'Change Password',
  'user.settings': 'Settings',
  'admin.search': 'Search users...',
  'admin.batch': 'Batch Actions',
  'overview.title': 'Overview',
  'overview.subtitle': 'AI Gateway · Model Scheduling Console',
}

const texts: Record<Lang, Record<string, string>> = { zh, en }

export const useI18nStore = create<I18nState>((set, get) => ({
  lang: (localStorage.getItem('lingdian_lang') as Lang) || 'zh',
  toggleLang: () => {
    const next = get().lang === 'zh' ? 'en' : 'zh'
    localStorage.setItem('lingdian_lang', next)
    set({ lang: next })
  },
  t: (key: string, fallbackEn?: string) => {
    const dict = texts[get().lang]
    if (dict?.[key]) return dict[key]
    if (get().lang === 'zh') return key
    return fallbackEn || key
  },
}))
