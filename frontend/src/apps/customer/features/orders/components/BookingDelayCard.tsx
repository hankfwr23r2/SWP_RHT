import { useTranslation } from 'react-i18next'
import type { BookingDelay } from '@shared/api/bookingOrders'
import { Card } from '@shared/ui/Card'
import { ErrorState } from '@shared/ui/ErrorState'
import { Skeleton } from '@shared/ui/Skeleton'
import { moment } from '../format'
import s from '../Orders.module.css'

interface Props {
  delay?: BookingDelay
  loaded: boolean
  error?: string
  onRetry: () => void
}

export function BookingDelayCard({ delay, loaded, error, onRetry }: Props) {
  const { t } = useTranslation()
  if (loaded && !delay && !error) return null
  return (
    <Card title={t(delay?.autoCancelled ? 'myOrders.delay.cancelled' : 'myOrders.delay.title')}>
      {error ? <ErrorState message={error} onRetry={onRetry} /> : !loaded ? <Skeleton lines={2} /> : delay && (
        <div className={s.stack}>
          {delay.plannedDepartureAt && <p>{t('myOrders.delay.original', { at: moment(delay.plannedDepartureAt) })}</p>}
          {delay.revisedDepartureAt
            ? <p>{t('myOrders.delay.revised', { at: moment(delay.revisedDepartureAt) })}</p>
            : !delay.autoCancelled && <p>{t('myOrders.delay.pending')}</p>}
          {delay.delayReason && <p>{t('myOrders.delay.reason', { reason: delay.delayReason })}</p>}
          {delay.requiresManagerContact && <p role="status">{t('myOrders.delay.contact')}</p>}
          {delay.canRescheduleFree && <p>{t('myOrders.delay.freeChange')}</p>}
        </div>
      )}
    </Card>
  )
}
