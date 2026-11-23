import type { ReactNode } from 'react'
import s from './Card.module.css'

interface CardProps {
  title?: ReactNode
  actions?: ReactNode
  className?: string
  children: ReactNode
}

// Khối nội dung có tiêu đề và vùng thao tác tùy chọn
export function Card({ title, actions, className, children }: CardProps) {
  return (
    <section className={[s.card, className].filter(Boolean).join(' ')}>
      {(title || actions) && (
        <header className={s.header}>
          {title && <h2 className={s.title}>{title}</h2>}
          {actions && <div className={s.actions}>{actions}</div>}
        </header>
      )}
      <div className={s.body}>{children}</div>
    </section>
  )
}
