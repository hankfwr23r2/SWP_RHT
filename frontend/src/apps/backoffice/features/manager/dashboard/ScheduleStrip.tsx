// Lịch khởi hành 7 ngày trên trang Tổng quan: mỗi đơn là một nhãn ở ngày xe bốc ngựa. Nút "Xem lịch" mở popup tháng / năm.
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { DAY } from '@shared/config/business-rules'
import type { Booking } from '@shared/types/booking'
import { placeShort } from '../../../shared/place'
import s from './Board.module.css'

const MAX_EVENTS = 3
const startOfDay = (t: number) => { const d = new Date(t); d.setHours(0, 0, 0, 0); return d.getTime() }

export function ScheduleStrip({ orders, onOpen, onCalendar }: { orders: Booking[]; onOpen: (b: Booking) => void; onCalendar: () => void }) {
  const { t, i18n } = useTranslation()
  const today = startOfDay(Date.now())
  const label = (tDate: number) => new Date(tDate).toLocaleDateString(i18n.language === 'en' ? 'en-US' : 'vi-VN', { weekday: 'short', day: '2-digit', month: '2-digit' })
  // Mở ở ngày có chuyến sắp tới gần nhất để không phải lật lịch tìm
  const next = orders.map(b => startOfDay(b.departAt)).filter(d => d >= today).sort((a, z) => a - z)[0]
  const [start, setStart] = useState(next ?? today)
  const [wide, setWide] = useState<number | null>(null) // ngày đang mở hết các chuyến
  const days = Array.from({ length: 7 }, (_, i) => start + i * DAY)
  return (
    <div className="card">
      <div className="card-header">
        <h3><i className="fa-solid fa-calendar-days" /> {t('scheduleStrip.title', 'Lịch khởi hành')}</h3>
        <span className={s.nav}>
          <button className="btn btn-ghost btn-sm" aria-label={t('scheduleStrip.prevWeek', 'Tuần trước')} onClick={() => setStart(start - 7 * DAY)}><i className="fa-solid fa-chevron-left" /></button>
          <button className="btn btn-ghost btn-sm" onClick={() => setStart(today)}>{t('scheduleStrip.today', 'Hôm nay')}</button>
          <button className="btn btn-ghost btn-sm" aria-label={t('scheduleStrip.nextWeek', 'Tuần sau')} onClick={() => setStart(start + 7 * DAY)}><i className="fa-solid fa-chevron-right" /></button>
          <button className="btn btn-outline btn-sm" onClick={onCalendar}><i className="fa-regular fa-calendar" /> {t('scheduleStrip.viewMonthYear', 'Xem lịch tháng, năm')}</button>
        </span>
      </div>
      <div className={s.week}>
        {days.map(d => {
          const events = orders
            .filter(b => startOfDay(b.departAt) === d)
            .sort((a, z) => a.departAt - z.departAt || a.id.localeCompare(z.id, undefined, { numeric: true }))
          return (
            <div key={d} className={`${s.day} ${d === today ? s.dayToday : ''}`}>
              <b>{label(d)}</b>
              {(wide === d ? events : events.slice(0, MAX_EVENTS)).map(b => (
                <button key={b.id} className={s.event} onClick={() => onOpen(b)} title={`${b.id} · ${b.customer}`}><b>{b.id}</b> {placeShort(b.dest.name)}</button>
              ))}
              {events.length > MAX_EVENTS && <button className={s.moreEvents} onClick={() => setWide(wide === d ? null : d)}>{wide === d ? t('scheduleStrip.collapse', 'Thu gọn') : t('scheduleStrip.moreTrips', '+{{count}} chuyến nữa', { count: events.length - MAX_EVENTS })}</button>}
              {!events.length && <span className={s.noEvent}>{t('scheduleStrip.noTrips', 'Không có chuyến')}</span>}
            </div>
          )
        })}
      </div>
    </div>
  )
}
