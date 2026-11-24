// Lịch tuần theo xe: mỗi xe một hàng, mỗi lượt một khối trải theo ngày. Điện thoại thì xếp dọc theo xe
import { useTranslation } from 'react-i18next'
import type { DispatchVehicle, VehicleTrip } from '@shared/api/dispatch'
import { addDays, boardRows, daySpan, lacksDriver } from './dispatchModel'
import s from './Board.module.css'

const ICON: Record<string, string> = { PICKUP: 'fa-arrow-up', LINEHAUL: 'fa-road', DELIVERY: 'fa-arrow-down', DIRECT: 'fa-arrows-left-right', TRANSFER: 'fa-rotate' }
const FROM = [s.from0, s.from1, s.from2, s.from3, s.from4, s.from5, s.from6]
const SPAN = [s.span1, s.span1, s.span2, s.span3, s.span4, s.span5, s.span6, s.span7]

export function WeekBoard({ weekStart, vehicles, trips, onOpen }: {
  weekStart: string
  vehicles: DispatchVehicle[]
  trips: VehicleTrip[]
  onOpen: (trip: VehicleTrip) => void
}) {
  const { t, i18n } = useTranslation()
  const days = Array.from({ length: 7 }, (_, i) => addDays(weekStart, i))
  const dayName = new Intl.DateTimeFormat(i18n.language, { weekday: 'short', day: 'numeric', month: 'numeric', timeZone: 'UTC' })
  const clock = new Intl.DateTimeFormat(i18n.language, { hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Ho_Chi_Minh' })
  return (
    <section className={s.board} aria-label={t('dispatch.trips.caption')}>
      <div className={s.header} aria-hidden="true">
        <span />
        <div className={s.days}>{days.map(day => <span key={day}>{dayName.format(new Date(`${day}T12:00:00Z`))}</span>)}</div>
      </div>
      {boardRows(vehicles, trips).map(({ vehicle, trips: own }) => (
        <div key={vehicle.id} className={s.row}>
          <div className={s.vehicle}>
            <strong>{vehicle.licensePlate}</strong>
            <span>{t('dispatch.board.seats', { count: (vehicle.standardStallCount ?? 0) + 2 * (vehicle.wideStallCount ?? 0) })}</span>
            {vehicle.status !== 'ACTIVE' && <span className={s.warn}>{t(`staffCatalog.status.${vehicle.status}`, { defaultValue: vehicle.status })}</span>}
          </div>
          <div className={s.lane}>
            {!own.length && <span className={s.free}>{t('dispatch.board.free')}</span>}
            {own.map(trip => {
              const span = daySpan(trip, weekStart)
              if (!span) return null
              return (
                <button key={trip.id} type="button" onClick={() => onOpen(trip)}
                  className={`${s.block} ${s[trip.tripType ?? 'TRANSFER']} ${FROM[span.from]} ${SPAN[span.days]}`}>
                  <span className={s.blockTitle}><i className={`fa-solid ${ICON[trip.tripType ?? ''] ?? 'fa-truck'}`} aria-hidden="true" /> {t(`tripWords.type.${trip.tripType}`)} · {trip.endLocationName}</span>
                  <span className={s.blockMeta}>{[trip.plannedStartAt, trip.plannedEndAt].flatMap(at => (at ? [clock.format(new Date(at))] : [])).join(' – ')}</span>
                  <span className={s.blockMeta}>{trip.code} · {t(`tripWords.status.${trip.status}`)}</span>
                  {lacksDriver(trip) && <span className={s.warn}><i className="fa-solid fa-triangle-exclamation" aria-hidden="true" /> {t('dispatch.board.noDriver')}</span>}
                </button>
              )
            })}
          </div>
        </div>
      ))}
    </section>
  )
}
