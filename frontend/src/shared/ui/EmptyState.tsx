import type { ReactNode } from 'react'
import s from './State.module.css'

interface EmptyStateProps {
  compact?: boolean
  icon?: ReactNode
  title: ReactNode
  // Nói rõ bước tiếp theo, ví dụ "Bấm Thêm ngựa để tạo hồ sơ đầu tiên" (AGENTS.md mục 8.6)
  description: ReactNode
  action?: ReactNode
}

export function EmptyState({ icon, title, description, action, compact }: EmptyStateProps) {
  return (
    <div className={compact ? `${s.state} ${s.compact}` : s.state}>
      {icon && <div className={s.icon} aria-hidden="true">{icon}</div>}
      <h2 className={s.title}>{title}</h2>
      <p className={s.description}>{description}</p>
      {action && <div className={s.action}>{action}</div>}
    </div>
  )
}
