import type { ReactNode } from 'react'
import s from './PageHeader.module.css'

interface PageHeaderProps {
  title: ReactNode
  description?: ReactNode
  actions?: ReactNode
}

export function PageTitle({ children }: { children: ReactNode }) {
  return <h1 className={s.title}>{children}</h1>
}

export function PageTitleCode({ children }: { children?: ReactNode }) {
  return <span className={s.code}>{children}</span>
}

// Đầu trang: tiêu đề h1, mô tả ngắn và các nút thao tác chính
export function PageHeader({ title, description, actions }: PageHeaderProps) {
  return (
    <header className={s.header}>
      <div className={s.text}>
        <PageTitle>{title}</PageTitle>
        {description && <p className={s.description}>{description}</p>}
      </div>
      {actions && <div className={s.actions}>{actions}</div>}
    </header>
  )
}
