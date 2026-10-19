/**
 * AI 厂商品牌图标组件 — 按模型 provider 匹配品牌 Logo 与品牌色
 */

interface ProviderIconProps {
  provider: string
  size?: number
}

const BRAND_COLORS: Record<string, string> = {
  openai: '#10A37F',
  anthropic: '#D97757',
  google: '#4285F4',
  deepseek: '#4D6BFE',
  xai: '#000000',
  alibaba: '#615CED',
  zhipu: '#3859FF',
  moonshot: '#16191E',
  bytedance: '#325AB4',
  minimax: '#FF4D4F',
}

/** 品牌图形（白色/单色，叠在品牌色背景上） */
function BrandMark({ provider, size }: { provider: string; size: number }) {
  const s = size * 0.62
  switch (provider) {
    case 'openai':
      // OpenAI 结形标志（简化）
      return (
        <svg width={s} height={s} viewBox="0 0 24 24" fill="currentColor">
          <path d="M22.282 9.821a5.985 5.985 0 0 0-.516-4.91 6.046 6.046 0 0 0-6.51-2.9A6.065 6.065 0 0 0 4.981 4.18a5.985 5.985 0 0 0-3.998 2.9 6.046 6.046 0 0 0 .743 7.097 5.98 5.98 0 0 0 .51 4.911 6.051 6.051 0 0 0 6.515 2.9A5.985 5.985 0 0 0 13.26 24a6.056 6.056 0 0 0 5.772-4.206 5.99 5.99 0 0 0 3.997-2.9 6.056 6.056 0 0 0-.747-7.073zM13.26 22.43a4.476 4.476 0 0 1-2.876-1.04l.141-.081 4.779-2.758a.795.795 0 0 0 .392-.681v-6.737l2.02 1.168a.071.071 0 0 1 .038.052v5.583a4.504 4.504 0 0 1-4.494 4.494zM3.6 18.304a4.47 4.47 0 0 1-.535-3.014l.142.085 4.783 2.759a.771.771 0 0 0 .78 0l5.843-3.369v2.332a.08.08 0 0 1-.033.062L9.74 19.95a4.5 4.5 0 0 1-6.14-1.646zM2.34 7.896a4.485 4.485 0 0 1 2.366-1.973V11.6a.766.766 0 0 0 .388.676l5.815 3.355-2.02 1.168a.076.076 0 0 1-.071 0l-4.83-2.786A4.504 4.504 0 0 1 2.34 7.872zm16.597 3.855l-5.833-3.387L15.119 7.2a.076.076 0 0 1 .071 0l4.83 2.791a4.494 4.494 0 0 1-.676 8.105v-5.678a.79.79 0 0 0-.407-.667zm2.01-3.023l-.141-.085-4.774-2.782a.776.776 0 0 0-.785 0L9.409 9.23V6.897a.066.066 0 0 1 .028-.061l4.83-2.787a4.5 4.5 0 0 1 6.68 4.66zm-12.64 4.135l-2.02-1.164a.08.08 0 0 1-.038-.057V6.075a4.5 4.5 0 0 1 7.375-3.453l-.142.08L8.704 5.46a.795.795 0 0 0-.393.681zm1.097-2.365l2.602-1.5 2.607 1.5v2.999l-2.597 1.5-2.612-1.5z"/>
        </svg>
      )
    case 'anthropic':
      // Anthropic "A" 标志
      return (
        <svg width={s} height={s} viewBox="0 0 24 24" fill="currentColor">
          <path d="M13.827 3.52h3.603L24 20h-3.603l-6.57-16.48zm-7.258 0h3.604L16.742 20h-3.603l-1.579-3.99H6.412L4.834 20H1.23L6.57 3.52zm2.663 9.134h3.03L11.995 7.884l-.762 2.137z"/>
        </svg>
      )
    case 'google':
      // Gemini 四角星形标志
      return (
        <svg width={s} height={s} viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 2c.6 5.2 4.6 9.2 10 10-5.4.8-9.4 4.8-10 10-.6-5.2-4.6-9.2-10-10 5.4-.8 9.4-4.8 10-10z"/>
        </svg>
      )
    case 'deepseek':
      // DeepSeek 鲸鱼标志（简化剪影）
      return (
        <svg width={s} height={s} viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 3c-2.5 0-4.6 1.3-5.8 3.2C3.9 6.6 2 8.8 2 11.5c0 2.1 1.1 3.9 2.8 4.9.2 2.6 2.4 4.6 5 4.6 1.2 0 2.3-.4 3.2-1.1.9.7 2 1.1 3.2 1.1 2.6 0 4.8-2 5-4.6 1.7-1 2.8-2.8 2.8-4.9 0-2.7-1.9-4.9-4.2-5.3C16.6 4.3 14.5 3 12 3zm-2.5 6.5c.8 0 1.5.7 1.5 1.5s-.7 1.5-1.5 1.5S8 11.8 8 11s.7-1.5 1.5-1.5zm5 0c.8 0 1.5.7 1.5 1.5s-.7 1.5-1.5 1.5-1.5-.7-1.5-1.5.7-1.5 1.5-1.5zM12 14c1.4 0 2.6.8 3.2 2-.9.5-2 .8-3.2.8s-2.3-.3-3.2-.8c.6-1.2 1.8-2 3.2-2z"/>
        </svg>
      )
    case 'xai':
      // xAI X 标志
      return (
        <svg width={s} height={s} viewBox="0 0 24 24" fill="currentColor">
          <path d="M18.9 1.15h3.68l-8.04 9.19L24 22.85h-7.41l-5.8-7.58-6.64 7.58H.47l8.6-9.83L0 1.15h7.59l5.24 6.93zm-1.29 19.5h2.04L6.49 3.24H4.3z"/>
        </svg>
      )
    case 'alibaba':
      // 通义千问
      return (
        <svg width={s} height={s} viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm-1.5 14.5H8l1.2-3H7l1.4-3.5h2.6l-1.2 3h1.9zm5.5 0h-2.5l1.2-3h-1.8l1.3-3.5H17l-1.2 3h1.8z"/>
        </svg>
      )
    case 'zhipu':
      // 智谱 GLM
      return (
        <svg width={s} height={s} viewBox="0 0 24 24" fill="currentColor">
          <path d="M4 4h7v2.5H8.2l2.1 5H16l2-4.5h-2V4h6v3h-1.8l-8.4 17H8l3-7.5H6L3 7H1.5V4z" transform="translate(0.5 0) scale(0.92)"/>
        </svg>
      )
    case 'moonshot':
      // 月之暗面 Kimi 月亮标志
      return (
        <svg width={s} height={s} viewBox="0 0 24 24" fill="currentColor">
          <path d="M20.3 14.2A8.5 8.5 0 0 1 9.8 3.7a.5.5 0 0 0-.7-.6A9 9 0 1 0 21 15a.5.5 0 0 0-.6-.7z"/>
        </svg>
      )
    case 'bytedance':
      // 豆包
      return (
        <svg width={s} height={s} viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 2C7.6 2 4 5.1 4 9c0 2.2 1.2 4.1 3 5.4V19a1 1 0 0 0 1 1h1v2h2v-2h2v2h2v-2h1a1 1 0 0 0 1-1v-4.6c1.8-1.3 3-3.2 3-5.4 0-3.9-3.6-7-8-7zm-3 8a2 2 0 1 1 2-2 2 2 0 0 1-2 2zm6 0a2 2 0 1 1 2-2 2 2 0 0 1-2 2z"/>
        </svg>
      )
    case 'minimax':
      // MiniMax
      return (
        <svg width={s} height={s} viewBox="0 0 24 24" fill="currentColor">
          <path d="M3 5h4l3 6 3-6h4l-5 9v5h-4v-5z" transform="translate(0.8 0) scale(0.93)"/>
        </svg>
      )
    default:
      return (
        <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="12" cy="12" r="9"/>
          <path d="M12 7v5l3 3" strokeLinecap="round"/>
        </svg>
      )
  }
}

export function ProviderIcon({ provider, size = 36 }: ProviderIconProps) {
  const key = (provider || '').toLowerCase().trim()
  const color = BRAND_COLORS[key] || '#8B5CF6'
  const isLight = key === 'xai' || key === 'moonshot'

  return (
    <div
      className="rounded-xl flex items-center justify-center flex-shrink-0"
      style={{
        width: size,
        height: size,
        background: isLight ? '#F5F5F5' : color + '18',
        color: color,
        border: '1px solid ' + (isLight ? '#E0E0E0' : color + '30'),
      }}
    >
      <BrandMark provider={key} size={size} />
    </div>
  )
}

export function providerDisplayName(provider: string): string {
  const map: Record<string, string> = {
    openai: 'OpenAI',
    anthropic: 'Anthropic',
    google: 'Google',
    deepseek: 'DeepSeek',
    xai: 'xAI',
    alibaba: '阿里通义',
    zhipu: '智谱 AI',
    moonshot: '月之暗面',
    bytedance: '字节跳动',
    minimax: 'MiniMax',
  }
  return map[(provider || '').toLowerCase()] || provider
}

export default ProviderIcon
