import type { ReactNode } from 'react'
import s from './Badge.module.css'

export type BadgeTone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger' | 'info'

interface BadgeProps {
  tone?: BadgeTone
  icon?: ReactNode
  className?: string
  children: ReactNode
}

// Nhãn ngắn. Trạng thái nghiệp vụ dùng StatusBadge.
export function Badge({ tone = 'neutral', icon, className, children }: BadgeProps) {
  return (
    <span className={[s.badge, s[tone], className].filter(Boolean).join(' ')}>
      {icon && <span className={s.icon} aria-hidden="true">{icon}</span>}
      {children}
    </span>
  )
}
