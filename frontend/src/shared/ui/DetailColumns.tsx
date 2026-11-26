import type { ReactNode } from 'react'
import s from './DetailColumns.module.css'

export function DetailColumns({ actions, info, children, wideInfo }: { actions?: ReactNode; info: ReactNode; children: ReactNode; wideInfo?: boolean }) {
  return <div className={wideInfo ? `${s.layout} ${s.wideInfo}` : s.layout}>
    <div className={s.actions}>{actions}</div>
    {wideInfo ? <div className={s.content}>{info}{children}</div> : <>
      <div className={s.content}>{children}</div>
      <aside className={s.info}>{info}</aside>
    </>}
  </div>
}
