// Dòng thời gian của một lượt xe: giờ từng điểm, thời gian lái và nghỉ giữa các điểm
import { useTranslation } from 'react-i18next'
import s from './Board.module.css'

export interface TimelineStop {
  key: string | number
  name: string
  arrivalAt?: string
  departureAt?: string
  driveMinutes?: number
  restMinutes?: number
  horses?: number
}

const ZONE = 'Asia/Ho_Chi_Minh'

export function TripTimeline({ stops, label }: { stops: TimelineStop[]; label: string }) {
  const { t, i18n } = useTranslation()
  const clock = new Intl.DateTimeFormat(i18n.language, { hour: '2-digit', minute: '2-digit', timeZone: ZONE })
  const date = new Intl.DateTimeFormat(i18n.language, { day: '2-digit', month: '2-digit', timeZone: ZONE })
  const time = (value?: string) => (value ? clock.format(new Date(value)) : '—')
  return (
    <ol className={s.timeline} aria-label={label}>
      {stops.map((stop, index) => (
        <li key={stop.key}>
          {index > 0 && !!stop.driveMinutes && (
            <div className={s.drive}>
              <i className="fa-solid fa-truck" aria-hidden="true" /> {t('dispatch.plan.drive', { minutes: stop.driveMinutes })}
              {!!stop.restMinutes && ` · ${t('dispatch.plan.rest', { minutes: stop.restMinutes })}`}
            </div>
          )}
          <div className={s.stop}>
            <span><time dateTime={stop.arrivalAt}>{time(stop.arrivalAt)}</time>{stop.departureAt && stop.departureAt !== stop.arrivalAt && ` – ${time(stop.departureAt)}`}
              <br />{stop.arrivalAt && date.format(new Date(stop.arrivalAt))}</span>
            <span className={s.name}><strong>{index + 1}. {stop.name}</strong>{!!stop.horses && <><br />{t('dispatch.plan.horses', { count: stop.horses })}</>}</span>
          </div>
        </li>
      ))}
    </ol>
  )
}
