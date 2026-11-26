import type { ReactNode } from 'react'
import { Link } from 'react-router'
import s from './StatTile.module.css'

export function StatRow({ children }: { children: ReactNode }) {
  return <div className={s.row}>{children}</div>
}

export function StatTile({ icon, value, label, link }: {
  icon: ReactNode
  value: ReactNode
  label: ReactNode
  link?: { to: string; label: string }
}) {
  return <div className={s.tile}>
    <span className={s.icon} aria-hidden="true">{icon}</span>
    <div>
      <div className={s.value}>{value}</div>
      <div className={s.label}>{label}</div>
      {link && <Link to={link.to}>{link.label}</Link>}
    </div>
  </div>
}
