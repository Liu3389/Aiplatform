import React from 'react'

interface PointsIconProps {
  size?: number
  className?: string
  color?: string
  variant?: 'solid' | 'outline'
}

const PointsIcon: React.FC<PointsIconProps> = ({ size = 16, className, color = '#F59E0B', variant = 'solid' }) => {
  if (variant === 'outline') {
    return (
      <svg xmlns="http://www.w3.org/2000/svg" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} style={{ flexShrink: 0 }}>
        <path d="M12 2L14.5 9.5L22 12L14.5 14.5L12 22L9.5 14.5L2 12L9.5 9.5L12 2Z"/>
      </svg>
    )
  }
  return (
    <svg xmlns="http://www.w3.org/2000/svg" width={size} height={size} viewBox="0 0 24 24" fill={color} className={className} style={{ flexShrink: 0 }}>
      <path d="M12 2L14.86 9.14L22 12L14.86 14.86L12 22L9.14 14.86L2 12L9.14 9.14L12 2Z" />
    </svg>
  )
}

export default PointsIcon
