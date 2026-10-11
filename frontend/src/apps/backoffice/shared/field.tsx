// Thành phần chung cho app di động của Tài xế và Hộ tống: chọn chuyến, tiêu đề chuyến, xem Lệnh điều xe.
import type { ReactNode } from 'react'
import { formatDate } from '@shared/lib/format'
import type { Booking, VehicleTrip } from '@shared/types/booking'
import { BookingStatusBadge } from '@shared/ui/BookingStatusBadge'
import { placeShort } from './place'
import s from './field.module.css'
import { FormSelect } from '@shared/ui/FormSelect'

// Một chuyến của tôi = một xe của một đơn
export interface FieldTrip { b: Booking; t: VehicleTrip }

export function TripPicker({ trips, value, onChange }: { trips: FieldTrip[]; value?: string; onChange: (tripId: string) => void }) {
  if (trips.length < 2) return null
  return (
    <FormSelect className="form-control" style={{ marginBottom: 14 }} aria-label="Chọn chuyến" value={value ?? ""} onChange={e => onChange(e.target.value)}>
      {trips.map(({ b, t }) => <option key={t.tripId} value={t.tripId}>{t.tripId} · {placeShort(b.origin.name)} → {placeShort(b.dest.name)}</option>)}
    </FormSelect>
  )
}

export function TripHeader({ b, t }: FieldTrip) {
  return (
    <div className={s.head}>
      <div className={s.headTop}><span className={s.trip}>{t.tripId}</span><BookingStatusBadge status={b.status} audience="staff" /></div>
      <div className={s.route}>{placeShort(b.origin.name)}<i className="fa-solid fa-arrow-right" aria-hidden="true" />{placeShort(b.dest.name)}</div>
      <div className={s.meta}><span><i className="fa-solid fa-calendar-day" aria-hidden="true" /> {formatDate(b.departAt)}</span><span><i className="fa-solid fa-horse-head" aria-hidden="true" /> {t.horseIds.length} ngựa</span>{b.gate && <span><i className="fa-solid fa-flag" aria-hidden="true" /> {b.gate}</span>}</div>
    </div>
  )
}

export function Segmented<T extends string>({ value, onChange, tabs }: { value: T; onChange: (v: T) => void; tabs: [T, string, string][] }) {
  return (
    <div className={s.segmented} role="tablist">
      {tabs.map(([k, label, icon]) => <button key={k} role="tab" aria-selected={value === k} className={value === k ? s.active : ''} onClick={() => onChange(k)}><i className={`fa-solid ${icon}`} aria-hidden="true" /> {label}</button>)}
    </div>
  )
}

export function Empty({ text }: { text: string }) {
  return <div className={s.empty}><i className="fa-solid fa-truck" aria-hidden="true" /><p>{text}</p></div>
}

export function Panel({ title, icon, children, tone }: { title: string; icon: string; children: ReactNode; tone?: 'ok' | 'warn' }) {
  return (
    <section className={`${s.panel} ${tone === 'ok' ? s.panelOk : tone === 'warn' ? s.panelWarn : ''}`}>
      <h2><i className={`fa-solid ${icon}`} aria-hidden="true" /> {title}</h2>
      {children}
    </section>
  )
}

export function Checklist({ items, checked, onChange }: { items: string[]; checked: string[]; onChange: (next: string[]) => void }) {
  return (
    <ul className={s.checklist}>
      {items.map(it => (
        <li key={it}><label><input type="checkbox" checked={checked.includes(it)} onChange={e => { const on = e.target.checked; onChange(on ? [...checked, it] : checked.filter(x => x !== it)) }} /><span>{it}</span></label></li>
      ))}
    </ul>
  )
}

