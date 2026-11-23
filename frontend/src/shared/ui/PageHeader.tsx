import type { ReactNode } from 'react'
import s from './PageHeader.module.css'

interface PageHeaderProps {
  title: ReactNode
  description?: ReactNode
  actions?: ReactNode
}

// Đầu trang: tiêu đề h1, mô tả ngắn và các nút thao tác chính
export function PageHeader({ title, description, actions }: PageHeaderProps) {
  return (
    <header className={s.header}>
      <div className={s.text}>
        <h1 className={s.title}>{title}</h1>
        {description && <p className={s.description}>{description}</p>}
      </div>
      {actions && <div className={s.actions}>{actions}</div>}
    </header>
  )
}
