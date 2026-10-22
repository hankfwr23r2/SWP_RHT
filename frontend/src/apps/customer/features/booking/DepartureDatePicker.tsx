import { useState } from 'react'
import { fromIsoDay, toIsoDay } from '@shared/lib/booking'
import { formatDate } from '@shared/lib/format'
import { CalendarIcon, ChevronDownIcon, ChevronLeftIcon, ChevronRightIcon } from './BookingIcons'
import s from './Booking.module.css'

const WEEK = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN']
const pad = (n: number) => String(n).padStart(2, '0')
const dayKey = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
const firstOfMonth = (d: Date) => new Date(d.getFullYear(), d.getMonth(), 1)
const completeDate = (text: string) =>
  /^\d{1,2}[/.-]\d{1,2}[/.-]\d{4}$/.test(text) || /^\d{8}$/.test(text)

function parseTyped(text: string): number | null {
  const clean = text.trim()
  const separated = /^(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})$/.exec(clean)
  const compact = /^(\d{2})(\d{2})(\d{4})$/.exec(clean)
  const match = separated ?? compact
  if (!match) return null

  const day = Number(match[1])
  const month = Number(match[2])
  const year = Number(match[3])
  const date = new Date(year, month - 1, day)
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) return null
  return date.getTime()
}

interface Props {
  value: string
  min: number
  onChange: (value: string) => void
  invalid?: boolean
}

export function DepartureDatePicker({ value, min, onChange, invalid }: Props) {
  const selected = value ? fromIsoDay(value) : null
  const [open, setOpen] = useState(false)
  const [text, setText] = useState(() => (value ? formatDate(fromIsoDay(value)) : ''))
  const [textError, setTextError] = useState('')
  const [cursor, setCursor] = useState(() => firstOfMonth(new Date(selected ?? Math.max(min, Date.now()))))

  const minDay = new Date(min)
  minDay.setHours(0, 0, 0, 0)
  const minAt = minDay.getTime()

  const year = cursor.getFullYear()
  const month = cursor.getMonth()
  const offset = (new Date(year, month, 1).getDay() + 6) % 7
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const cells: (Date | null)[] = [
    ...Array.from({ length: offset }, () => null),
    ...Array.from({ length: daysInMonth }, (_, i) => new Date(year, month, i + 1)),
  ]
  const canPrev = firstOfMonth(cursor).getTime() > firstOfMonth(minDay).getTime()
  const dateInvalid = invalid || !!textError

  const toggle = () => {
    if (!open && selected) setCursor(firstOfMonth(new Date(selected)))
    setOpen(v => !v)
  }

  const updateText = (raw: string) => {
    const next = raw.slice(0, 10)
    setText(next)

    if (!next.trim()) {
      setTextError('')
      onChange('')
      return
    }

    const at = parseTyped(next)
    if (at === null) {
      setTextError(completeDate(next) ? 'Ngày không hợp lệ.' : '')
      onChange('')
      return
    }
    if (at < minAt) {
      setTextError(`Ngày phải từ ${formatDate(min)}.`)
      onChange('')
      return
    }

    setTextError('')
    onChange(toIsoDay(at))
  }

  const normalizeText = () => {
    if (!text.trim()) return
    const at = parseTyped(text)
    if (at === null) {
      setTextError('Ngày không hợp lệ.')
      return
    }
    if (at < minAt) {
      setTextError(`Ngày phải từ ${formatDate(min)}.`)
      return
    }
    setText(formatDate(at))
  }

  const select = (date: Date) => {
    setText(formatDate(date))
    setTextError('')
    onChange(dayKey(date))
    setOpen(false)
  }

  return (
    <div className={s.datePicker}>
      <div className={`${s.dateDisplay} ${dateInvalid ? s.dateInvalid : ''}`}>
        <span className={s.dateDisplayIcon}><CalendarIcon /></span>
        <input
          className={s.dateInput}
          value={text}
          onChange={e => updateText(e.target.value)}
          onBlur={normalizeText}
          onKeyDown={e => {
            if (e.key === 'Enter') {
              e.preventDefault()
              toggle()
            }
          }}
          placeholder="dd/mm/yyyy"
          maxLength={10}
          autoComplete="off"
          spellCheck={false}
          aria-label="Ngày khởi hành"
          aria-invalid={dateInvalid || undefined}
        />
        <button
          type="button"
          className={`${s.dateToggle} ${open ? s.dateToggleOpen : ''}`}
          onClick={toggle}
          aria-expanded={open}
          aria-haspopup="dialog"
          aria-label="Mở lịch chọn ngày"
        >
          <ChevronDownIcon />
        </button>
      </div>

      {textError && !invalid && <div className="form-error">{textError}</div>}

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
                  d.getTime() === minAt ? s.dateDayEarliest : '',
                ].filter(Boolean).join(' ')}
                disabled={d.getTime() < minAt}
                onClick={() => select(d)}
                aria-label={formatDate(d)}
                aria-pressed={d.getTime() === selected}
              >
                {d.getDate()}
              </button>
            ) : <span key={`empty-${i}`} />)}
          </div>

          <div className={s.dateFoot}>
            <button
              type="button"
              onClick={() => {
                onChange(toIsoDay(min))
                setText(formatDate(min))
                setTextError('')
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
