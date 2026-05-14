import { Component, type ReactNode } from 'react'

interface Props { children: ReactNode; fallback?: ReactNode }
interface State { hasError: boolean; error: Error | null; retryCount: number }

export default class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false, error: null, retryCount: 0 }
  private retryTimer: ReturnType<typeof setTimeout> | null = null

  static getDerivedStateFromError(error: Error): Partial<State> {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error('ErrorBoundary caught:', error, info)
  }

  componentWillUnmount() {
    if (this.retryTimer) clearTimeout(this.retryTimer)
  }

  handleRetry = () => {
    this.setState({ hasError: false, error: null, retryCount: this.state.retryCount + 1 })
  }

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) return this.props.fallback

      return (
        <div className="flex flex-col items-center justify-center h-full p-8 text-center">
          <div className="w-16 h-16 rounded-2xl bg-rose-500/10 border border-rose-500/20 flex items-center justify-center mb-6">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#F43F5E" strokeWidth="2" strokeLinecap="round">
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="8" x2="12" y2="12" />
              <line x1="12" y1="16" x2="12.01" y2="16" />
            </svg>
          </div>
          <h2 className="text-lg font-semibold mb-2" style={{ color: 'var(--text-primary)' }}>页面加载异常</h2>
          <p className="text-sm mb-4" style={{ color: 'var(--text-dim)' }}>
            {this.state.error?.message || '渲染过程中发生错误'}
          </p>
          <div className="flex gap-3">
            <button
              onClick={this.handleRetry}
              className="px-5 py-2.5 rounded-xl text-sm font-medium text-white transition-all"
              style={{ background: 'var(--accent-purple)' }}
            >
              重试加载
            </button>
            <button
              onClick={() => window.location.reload()}
              className="px-5 py-2.5 rounded-xl text-sm font-medium transition-all border"
              style={{ color: 'var(--text-secondary)', borderColor: 'var(--border-color)' }}
            >
              刷新页面
            </button>
          </div>
        </div>
      )
    }

    return this.props.children
  }
}
