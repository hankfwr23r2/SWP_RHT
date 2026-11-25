import { useTranslation } from 'react-i18next'
import { formatDateTime } from '@shared/lib/format'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Modal } from '@shared/ui/Modal'
import { Skeleton } from '@shared/ui/Skeleton'
import { useHorseHealthHistory } from './useHorseHealthHistory'
import s from './Tracking.module.css'

export function HorseHealthHistory({ bookingHorseId, horseName, onClose }: {
  bookingHorseId: number
  horseName?: string
  onClose: () => void
}) {
  const { t } = useTranslation()
  const { data, loaded, error, reload } = useHorseHealthHistory(bookingHorseId)
  return <Modal title={t('stayHealth.history')} subtitle={horseName} onClose={onClose}>
    {!loaded ? <Skeleton variant="block" lines={3} />
      : error || !data ? <ErrorState onRetry={reload} />
        : !data.length ? <EmptyState title={t('stayHealth.empty')} description={t('stayHealth.emptyHint')} />
          : <ul className={s.list}>
            {data.map(log => (
              <li className={s.card} key={log.id}>
                <div className={s.head}>
                  <strong>{log.healthStatus && t(`myTrips.run.health.statusValue.${log.healthStatus}`)}</strong>
                  {log.recordedAt && <time dateTime={log.recordedAt}>{formatDateTime(new Date(log.recordedAt))}</time>}
                </div>
                <p className={s.line}>{[log.placeName, log.recordedByName].filter(Boolean).join(' · ')}</p>
                <dl>
                  {log.waterIntake && <><dt>{t('myTrips.run.health.water')}</dt><dd>{t(`myTrips.run.health.intakeValue.${log.waterIntake}`)}</dd></>}
                  {log.feedIntake && <><dt>{t('myTrips.run.health.feed')}</dt><dd>{t(`myTrips.run.health.intakeValue.${log.feedIntake}`)}</dd></>}
                  {log.temperatureCelsius != null && <><dt>{t('myTrips.run.health.temperature')}</dt><dd>{log.temperatureCelsius}</dd></>}
                  {log.heartRateBpm != null && <><dt>{t('stayHealth.heartRateBpm')}</dt><dd>{log.heartRateBpm}</dd></>}
                  {log.respirationRatePerMinute != null && <><dt>{t('stayHealth.respirationRatePerMinute')}</dt><dd>{log.respirationRatePerMinute}</dd></>}
                  {log.symptoms && <><dt>{t('stayHealth.symptoms')}</dt><dd>{log.symptoms}</dd></>}
                  {log.note && <><dt>{t('stayHealth.note')}</dt><dd className={s.healthNote}>{log.note}</dd></>}
                </dl>
              </li>
            ))}
          </ul>}
  </Modal>
}
