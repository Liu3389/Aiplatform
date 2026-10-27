interface PlatformMarkProps {
  className?: string
}

/** Single source of truth for the platform's brand icon. */
export default function PlatformMark({ className = '' }: PlatformMarkProps) {
  return (
    <img
      src="/brand/icon-macos-default.png"
      alt="凌点 AI"
      className={`block shrink-0 object-contain ${className}`}
      draggable={false}
    />
  )
}
