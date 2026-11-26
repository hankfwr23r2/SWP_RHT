import { useTranslation } from 'react-i18next'
import { StayHealthAction } from '@backoffice/shared/StayHealthAction'
import { useMyHorseStays } from './useMyHorseStays'
import { Card } from '@shared/ui/Card'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Skeleton } from '@shared/ui/Skeleton'
import s from './MyTrips.module.css'

export function MyStayHealthPanel() {
  const { t } = useTranslation()
  const { data, loaded, error, reload } = useMyHorseStays()
  return <Card title={t('stayHealth.mine')}>
    {!loaded ? <Skeleton variant="block" lines={3} />
      : error || !data ? <ErrorState onRetry={reload} />
        : !data.length ? <EmptyState title={t('stayHealth.mineEmpty')} description={t('stayHealth.mineHint')} />
          : <ul className={s.list}>
            {data.map(stay => stay.id != null && stay.bookingHorseId != null && (
              <li key={stay.id} className={s.body}>
                <strong>{stay.horseName} · {stay.bookingCode}</strong>
                <span>{stay.locationName}</span>
                <StayHealthAction horseStayId={stay.id} bookingHorseId={stay.bookingHorseId} horseName={stay.horseName} onSaved={reload} />
              </li>
            ))}
          </ul>}
  </Card>
}
