import type { ReactNode } from 'react'
import s from './DetailColumns.module.css'

export function DetailColumns({ actions, info, children }: { actions?: ReactNode; info: ReactNode; children: ReactNode }) {
  return <div className={s.layout}>
    <div className={s.actions}>{actions}</div>
    <div className={s.content}>{children}</div>
    <aside className={s.info}>{info}</aside>
  </div>
}
