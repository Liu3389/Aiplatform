import { useEffect, useState, type CSSProperties } from 'react'

interface UserAvatarProps {
  name: string
  avatarUrl?: string | null
  className: string
  style?: CSSProperties
  textClassName?: string
}

export default function UserAvatar({ name, avatarUrl, className, style, textClassName }: UserAvatarProps) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  useEffect(() => setFailedUrl(null), [avatarUrl])
  const showImage = Boolean(avatarUrl && failedUrl !== avatarUrl)

  return (
    <span className={`relative inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full ${className}`} style={style} aria-hidden="true">
      {showImage
        ? <img src={avatarUrl || undefined} alt="" referrerPolicy="no-referrer" className="absolute inset-0 h-full w-full object-cover" onError={() => setFailedUrl(avatarUrl || null)} />
        : <span className={textClassName}>{name[0]?.toUpperCase() || 'U'}</span>}
    </span>
  )
}
