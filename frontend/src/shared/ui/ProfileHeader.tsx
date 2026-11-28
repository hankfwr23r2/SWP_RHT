import type { ReactNode } from 'react'
import { initialsOf } from '@shared/lib/initialsOf'
import { Badge } from './Badge'
import { PageTitle } from './PageHeader'
import s from './ProfileHeader.module.css'

export function ProfileHeader({ name, role, detail, facts, children }: {
  name: string
  role?: string
  detail?: string
  facts?: { label: string; value: ReactNode }[]
  children?: ReactNode
}) {
  return (
    <header className={s.header}>
      <div className={s.identity}>
        <span className={s.avatar} aria-hidden="true">{initialsOf(name)}</span>
        <div className={s.name}>
          <PageTitle>{name}</PageTitle>
          <div className={s.meta}>{role && <Badge tone="info">{role}</Badge>}{detail && <span>{detail}</span>}</div>
        </div>
      </div>
      {facts && <dl className={s.facts}>
        {facts.map(({ label, value }) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}
      </dl>}
      {children}
    </header>
  )
}
