// Kết quả tra cứu công khai một mã đơn: bước của đơn và nơi từng con ngựa đang ở
import { useTranslation } from 'react-i18next'
import type { PublicTracking } from '@shared/api/tracking'
import { formatDateTime } from '@shared/lib/format'
import s from './HomePage.module.css'

const cx = (...c: (string | false | undefined)[]) => c.filter(Boolean).join(' ')

// Năm bước ở mục "Quy trình" của trang chủ; 5 = đã giao xong
const STEP: Record<string, number> = {
  PENDING_INTAKE: 0, UNDER_REVIEW: 1, NEEDS_RESUBMISSION: 1, QUOTING: 2, AWAITING_DEPOSIT: 2, QUOTE_EXPIRED: 2,
  CONFIRMED: 3, SCHEDULE_CHANGE_REQUIRED: 3, IN_OPERATION: 4, DELIVERED: 5, COMPLETED: 5,
}
const TONE: Record<string, string | undefined> = { CANCELLED: 'red', REJECTED: 'red', DELIVERED: 'green', COMPLETED: 'green' }

export function TrackResult({ result, delay }: { result: PublicTracking; delay: number }) {
  const { t } = useTranslation()
  // check-style: dynamic kết quả hiện lần lượt theo thứ tự mã đơn
  const appear = { animationDelay: `${delay}ms` }
  if (!result.found) {
    return <div className={cx(s.trackItem, s.trackMiss)} style={appear}><b>{result.code}</b>: {t('home.lookup.notFound')}</div>
  }
  const status = result.status ?? ''
  const step = STEP[status]
  const steps = [1, 2, 3, 4, 5].map(n => t(`home.process.step${n}Title`))
  return (
    <div className={s.trackItem} style={appear}>
      <div className={s.trackHead}>
        <span className={s.trackCode}>{result.code}</span>
        <span className={cx(s.trackStatus, TONE[status] && s[TONE[status]!])}>{t(`journey.bookingStatus.${status}`)}</span>
      </div>
      {step !== undefined && (
        <div className={s.trackSteps}>
          {steps.map((label, k) => {
            const state = k < step ? 'done' : k === step ? 'current' : ''
            return (
              <div key={label} className={cx(s.trackStep, state && s[state])}>
                <span className={s.dot}>{state === 'done' && <i className="fa-solid fa-check" />}</span>
                <div>{label}</div>
              </div>
            )
          })}
        </div>
      )}
      {(result.horses ?? []).map(horse => (
        <div key={horse.horseName} className={s.trackNow}>
          <i className="fa-solid fa-horse-head" aria-hidden="true" /> <b>{horse.horseName}</b>: {t(`journey.holder.${horse.holder ?? 'SHIPPER'}`, { party: '' })}
          {horse.stayType && ` · ${t(`journey.stay.${horse.stayType}`)}`}
          {horse.legType && ` · ${t(`journey.leg.${horse.legType}`)}`}
          {horse.locationName && ` · ${horse.locationName}`}
          {horse.updatedAt && <><br />{t('home.lookup.updatedAt')} {formatDateTime(new Date(horse.updatedAt))}</>}
        </div>
      ))}
      {result.plannedDeliveryAt && step !== 5 && (
        <div className={s.trackMeta}>{t('home.lookup.estimatedDelivery')} {formatDateTime(new Date(result.plannedDeliveryAt))}</div>
      )}
    </div>
  )
}
