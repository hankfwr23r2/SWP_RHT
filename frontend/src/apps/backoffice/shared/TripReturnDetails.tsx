import { useTranslation } from 'react-i18next'
import type { TripReturnProgress } from '@shared/api/dispatch'
import { formatDateTime } from '@shared/lib/format'
import { StatusBadge } from '@shared/ui/StatusBadge'
import s from './TripReturnDetails.module.css'

export function TripReturnDetails({ trip }: { trip: TripReturnProgress }) {
  const { t } = useTranslation()
  if (!trip.returning && !trip.returnExpectedAt && !trip.returnLate) return null
  return (
    <span className={s.details}>
      {trip.returning && <StatusBadge tone="info" label={t('tripWords.returning')} />}
      {trip.returnExpectedAt && <time dateTime={trip.returnExpectedAt}>
        {t('tripWords.returnExpectedAt', { time: formatDateTime(new Date(trip.returnExpectedAt)) })}
      </time>}
      {trip.returnLate && <StatusBadge tone="warning" label={t('tripWords.returnLate')} />}
    </span>
  )
}
