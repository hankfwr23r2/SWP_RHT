import { useState } from 'react'
import { MIN_LEAD_DAYS } from '@shared/config/business-rules'
import { fromIsoDay, toIsoDay } from '@shared/lib/booking'
import { formatDate } from '@shared/lib/format'
import { CalendarIcon, ChevronDownIcon, ChevronLeftIcon, ChevronRightIcon } from './BookingIcons'
import s from './Booking.module.css'

const WEEK = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN']
const pad = (n: number) => String(n).padStart(2, '0')
const dayKey = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
const firstOfMonth = (d: Date) => new Date(d.getFullYear(), d.getMonth(), 1)

interface Props {
  value: string
  min: number
  onChange: (value: string) => void
  invalid?: boolean
}

export function DepartureDatePicker({ value, min, onChange, invalid }: Props) {
  const selected = value ? fromIsoDay(value) : null
  const [open, setOpen] = useState(false)
  const [cursor, setCursor] = useState(() => firstOfMonth(new Date(selected ?? Math.max(min, Date.now()))))
  const minDay = new Date(min)
  minDay.setHours(0, 0, 0, 0)

  const year = cursor.getFullYear()
  const month = cursor.getMonth()
  const offset = (new Date(year, month, 1).getDay() + 6) % 7
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const cells: (Date | null)[] = [
    ...Array.from({ length: offset }, () => null),
    ...Array.from({ length: daysInMonth }, (_, i) => new Date(year, month, i + 1)),
  ]
  const canPrev = firstOfMonth(cursor).getTime() > firstOfMonth(minDay).getTime()
  const weekday = selected ? new Date(selected).toLocaleDateString('vi-VN', { weekday: 'long' }) : ''

  const select = (d: Date) => {
    onChange(dayKey(d))
    setOpen(false)
  }

  return (
    <div className={s.datePicker}>
      <button
        type="button"
        className={`${s.dateDisplay} ${invalid ? s.dateInvalid : ''}`}
        onClick={() => setOpen(v => !v)}
        aria-expanded={open}
        aria-haspopup="dialog"
      >
        <span className={s.dateDisplayIcon}><CalendarIcon /></span>
        <span className={s.dateDisplayBody}>
          <span className={selected ? s.dateValue : `${s.dateValue} ${s.dateValueEmpty}`}>
            {selected ? formatDate(selected) : 'Chọn ngày khởi hành'}
          </span>
          <span className={s.dateMeta}>
            {selected ? weekday : `Sớm nhất ${formatDate(min)}`}
          </span>
        </span>
        <span className={`${s.dateChevron} ${open ? s.dateChevronOpen : ''}`}><ChevronDownIcon /></span>
      </button>

      {open && (
        <div className={s.datePanel} role="dialog" aria-label="Chọn ngày khởi hành">
          <div className={s.datePanelHead}>
            <button
              type="button"
              className={s.dateNav}
              onClick={() => setCursor(new Date(year, month - 1, 1))}
              disabled={!canPrev}
              aria-label="Tháng trước"
            >
              <ChevronLeftIcon />
            </button>
            <b>Tháng {month + 1} / {year}</b>
            <button
              type="button"
              className={s.dateNav}
              onClick={() => setCursor(new Date(year, month + 1, 1))}
              aria-label="Tháng sau"
            >
              <ChevronRightIcon />
            </button>
          </div>

          <div className={s.dateWeek} aria-hidden="true">
            {WEEK.map(d => <span key={d}>{d}</span>)}
          </div>

          <div className={s.dateGrid}>
            {cells.map((d, i) => d ? (
              <button
                key={dayKey(d)}
                type="button"
                className={[
                  s.dateDay,
                  d.getTime() === selected ? s.dateDaySelected : '',
                  d.getTime() === minDay.getTime() ? s.dateDayEarliest : '',
                ].filter(Boolean).join(' ')}
                disabled={d.getTime() < minDay.getTime()}
                onClick={() => select(d)}
                aria-label={formatDate(d)}
                aria-pressed={d.getTime() === selected}
              >
                {d.getDate()}
              </button>
            ) : <span key={`empty-${i}`} />)}
          </div>

          <div className={s.dateFoot}>
            <span>Đặt trước tối thiểu {MIN_LEAD_DAYS} ngày</span>
            <button
              type="button"
              onClick={() => {
                onChange(toIsoDay(min))
                setCursor(firstOfMonth(minDay))
                setOpen(false)
              }}
            >
              Chọn ngày sớm nhất
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
