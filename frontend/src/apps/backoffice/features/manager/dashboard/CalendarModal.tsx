// Lịch khởi hành dạng popup kiểu calendar: xem theo Tháng, Năm hoặc từng Ngày. Mỗi chuyến là một đơn có ngày xe bốc ngựa.
import { useState } from 'react'
import { formatDate } from '@shared/lib/format'
import type { Booking } from '@shared/types/booking'
import { BookingStatusBadge } from '@shared/ui/BookingStatusBadge'
import { Modal } from '@shared/ui/Modal'
import { placeShort } from '../../../shared/place'
import s from './Calendar.module.css'

type View = 'day' | 'month' | 'year'
const VIEWS: [View, string][] = [['day', 'Ngày'], ['month', 'Tháng'], ['year', 'Năm']]
const WEEK = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN']
const MAX_CHIPS = 2

const key = (d: Date) => `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`
const today = () => new Date()
// Lưới 6 tuần của một tháng, tuần bắt đầu từ thứ Hai
const gridOf = (year: number, month: number) => {
  const offset = (new Date(year, month, 1).getDay() + 6) % 7
  return Array.from({ length: 42 }, (_, i) => new Date(year, month, 1 - offset + i))
}
const monthTitle = (d: Date) => `Tháng ${d.getMonth() + 1}, ${d.getFullYear()}`

export function CalendarModal({ orders, onOpen, onClose }: { orders: Booking[]; onOpen: (b: Booking) => void; onClose: () => void }) {
  // Mở ở ngày có chuyến sắp tới gần nhất để không phải lật lịch tìm
  const upcoming = orders.map(b => new Date(b.departAt)).filter(d => d.getTime() >= new Date().setHours(0, 0, 0, 0)).sort((a, z) => a.getTime() - z.getTime())[0]
  const [view, setView] = useState<View>('month')
  const [cursor, setCursor] = useState(upcoming ?? today())

  const byDay = new Map<string, Booking[]>()
  orders.forEach(b => { const k = key(new Date(b.departAt)); byDay.set(k, [...(byDay.get(k) ?? []), b]) })
  const at = (d: Date) => byDay.get(key(d)) ?? []
  const isToday = (d: Date) => key(d) === key(today())

  const step = (dir: 1 | -1) => setCursor(c => view === 'day' ? new Date(c.getFullYear(), c.getMonth(), c.getDate() + dir) : view === 'month' ? new Date(c.getFullYear(), c.getMonth() + dir, 1) : new Date(c.getFullYear() + dir, c.getMonth(), 1))
  const title = view === 'day' ? cursor.toLocaleDateString('vi-VN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }) : view === 'month' ? monthTitle(cursor) : String(cursor.getFullYear())
  const goDay = (d: Date) => { setCursor(d); setView('day') }

  return (
    <Modal wide onClose={onClose} title="Lịch khởi hành" subtitle="Ngày xe bốc ngựa của các đơn">
      <div className={s.bar}>
        <b className={s.title}>{title}</b>
        <div className={s.seg} role="tablist" aria-label="Kiểu xem">
          {VIEWS.map(([k, label]) => <button key={k} role="tab" aria-selected={view === k} className={view === k ? s.segOn : undefined} onClick={() => setView(k)}>{label}</button>)}
        </div>
        <span className={s.nav}>
          <button aria-label="Trước" onClick={() => step(-1)}><i className="fa-solid fa-chevron-left" /></button>
          <button onClick={() => setCursor(today())}>Hôm nay</button>
          <button aria-label="Sau" onClick={() => step(1)}><i className="fa-solid fa-chevron-right" /></button>
        </span>
      </div>

      {view === 'month' && (
        <div className={s.month}>
          {WEEK.map(w => <span key={w} className={s.wk}>{w}</span>)}
          {gridOf(cursor.getFullYear(), cursor.getMonth()).map(d => {
            const list = at(d)
            return (
              <button key={key(d)} className={`${s.cell} ${d.getMonth() !== cursor.getMonth() ? s.out : ''}`} onClick={() => goDay(d)} aria-label={`${formatDate(d)}: ${list.length} chuyến`}>
                <span className={`${s.num} ${isToday(d) ? s.today : ''}`}>{d.getDate()}</span>
                {list.slice(0, MAX_CHIPS).map(b => <span key={b.id} className={s.chip}><b>{b.id.slice(-4)}</b> {placeShort(b.dest.name)}</span>)}
                {list.length > MAX_CHIPS && <span className={s.more}>+{list.length - MAX_CHIPS} chuyến</span>}
              </button>
            )
          })}
        </div>
      )}

      {view === 'year' && (
        <div className={s.year}>
          {Array.from({ length: 12 }, (_, m) => (
            <div key={m} className={s.mini}>
              <button className={s.miniTitle} onClick={() => { setCursor(new Date(cursor.getFullYear(), m, 1)); setView('month') }}>Tháng {m + 1}</button>
              <div className={s.miniGrid}>
                {WEEK.map(w => <i key={w}>{w[w.length - 1] === 'N' ? 'C' : w[1]}</i>)}
                {gridOf(cursor.getFullYear(), m).map(d => (
                  <button key={key(d)} disabled={d.getMonth() !== m} onClick={() => goDay(d)} className={`${d.getMonth() !== m ? s.miniOut : ''} ${isToday(d) ? s.miniToday : ''} ${at(d).length && d.getMonth() === m ? s.miniHas : ''}`} aria-label={`${formatDate(d)}: ${at(d).length} chuyến`}>{d.getDate()}</button>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      {view === 'day' && (
        <div className={s.dayList}>
          {at(cursor).map(b => (
            <button key={b.id} className={s.dayItem} onClick={() => onOpen(b)}>
              <b>{b.id}</b>
              <span>{b.customer}</span>
              <span className={s.route}>{placeShort(b.origin.name)} → {placeShort(b.dest.name)}</span>
              <BookingStatusBadge status={b.status} audience="staff" />
            </button>
          ))}
          {!at(cursor).length && <p className={s.empty}><i className="fa-regular fa-calendar" />Không có chuyến khởi hành trong ngày này</p>}
        </div>
      )}
    </Modal>
  )
}
