interface MembershipIconProps {
  kind: string
  size?: number
  color?: string
  className?: string
}

/** 会员等级图标：使用内联 SVG，避免依赖字符或表情符号。 */
export default function MembershipIcon({ kind, size = 18, color = 'currentColor', className }: MembershipIconProps) {
  const common = {
    width: size,
    height: size,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: color,
    strokeWidth: 1.8,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    className,
    'aria-hidden': true,
  }

  switch ((kind || 'free').toLowerCase()) {
    case 'diamond':
      return <svg {...common}><path d="M4 8.5 7.5 4h9L20 8.5 12 20 4 8.5Z" /><path d="m4 8.5 8 .5 8-.5M8 4l4 5 4-5" /></svg>
    case 'gold':
      return <svg {...common}><path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-2.9-5.6 2.9 1.1-6.2L3 9.6l6.2-.9L12 3Z" /></svg>
    case 'silver':
      return <svg {...common}><circle cx="12" cy="12" r="7.5" /><circle cx="12" cy="12" r="3.5" /></svg>
    default:
      return <svg {...common}><circle cx="12" cy="12" r="7.5" /></svg>
  }
}
