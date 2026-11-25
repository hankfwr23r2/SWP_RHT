// Timeline hành trình từng con ngựa (B6): chặng, lưu trú, giao nhận và ngựa đang trong tay ai. Cả hai ứng dụng dùng.
import { useTranslation } from 'react-i18next'
import { trackingApi, type HorseJourney as HorseJourneyData } from '@shared/api/tracking'
import { useLoad } from '@shared/hooks/useLoad'
import { formatClock, formatDate, formatDateTime } from '@shared/lib/format'
import { Badge } from '@shared/ui/Badge'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Skeleton } from '@shared/ui/Skeleton'
import { journeyEntries, type JourneyEntry } from './horseJourneyModel'
import s from './HorseJourney.module.css'

const ICON: Record<JourneyEntry['kind'], string> = { leg: 'fa-truck', stay: 'fa-warehouse', handover: 'fa-handshake' }
const HOLDER_TONE = { SHIPPER: 'neutral', COMPANY: 'info', PARTY: 'warning', CONSIGNEE: 'success' } as const

const time = (at?: string) => (at ? formatDateTime(new Date(at)) : '')

export function HorseJourney({ horse }: { horse: HorseJourneyData }) {
  const { t } = useTranslation()
  const entries = journeyEntries(horse)
  const holder = horse.holder ?? 'SHIPPER'
  return (
    <section className={s.horse} aria-label={t('journey.horseLabel', { name: horse.horseName })}>
      <header className={s.head}>
        <div>
          <b>{horse.horseName}</b>
          {horse.microchip && <span className={s.muted}> · {horse.microchip}</span>}
        </div>
        <Badge tone={HOLDER_TONE[holder]}>{t(`journey.holder.${holder}`, { party: horse.holderPartyName ?? '' })}</Badge>
      </header>
      {!entries.length
        ? <p className={s.muted}>{t('journey.empty')}</p>
        : (
          <ol className={s.list}>
            {entries.map(entry => (
              <li key={entry.key} className={`${s.item} ${s[entry.state]}`}>
                <span className={s.dot} aria-hidden="true"><i className={`fa-solid ${ICON[entry.kind]}`} /></span>
                <div>
                  <div className={s.top}>
                    <b>{t(`journey.${entry.kind}.${entry.type}`)}</b>
                    {entry.state !== 'done' && <Badge tone={entry.state === 'problem' ? 'danger' : entry.state === 'current' ? 'warning' : 'neutral'}>
                      {t(`journey.state.${entry.state}`)}</Badge>}
                    {entry.entryMethod === 'MANUAL' && <Badge tone="neutral">{t('handoverEvidence.manualEntry')}</Badge>}
                  </div>
                  <div className={s.muted}>{entry.place}{entry.detail ? ` · ${entry.detail}` : ''}</div>
                  {entry.entryMethod === 'MANUAL' && entry.manualReason && (
                    <div className={s.manualReason}>{t('handoverEvidence.manualReason')} · {entry.manualReason}</div>
                  )}
                  {entry.windows?.map(window => (
                    <div key={window.side} className={s.window}>
                      {t(`journey.window.${window.side}`, {
                        from: formatClock(new Date(window.from)), to: formatClock(new Date(window.to)), date: formatDate(new Date(window.from)),
                      })}
                    </div>
                  ))}
                  {entry.at && (
                    <div className={s.time}>
                      {entry.planned ? t('journey.plannedAt', { time: time(entry.at) }) : time(entry.at)}
                      {entry.endAt && entry.kind !== 'handover' ? ` → ${time(entry.endAt)}` : ''}
                    </div>
                  )}
                  {!!entry.evidence?.length && (
                    <ul>
                      {entry.evidence.map(file => (
                        <li key={file.id}>
                          <a href={file.fileUrl} target="_blank" rel="noopener noreferrer">
                            {t(file.type === 'HANDOVER_RECORD' ? 'journey.evidence.record' : 'journey.evidence.photo', { name: file.fileName })}
                          </a>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              </li>
            ))}
          </ol>
        )}
      {horse.healthLogs && (
        <div>
          <h3>{t('journey.health.title')}</h3>
          {horse.healthLogs.length === 0 ? <p className={s.muted}>{t('journey.health.noLogs')}</p> : (
            <ul>
              {horse.healthLogs.map(log => (
                <li key={log.id}>
                  <b>{time(log.recordedAt)}</b>
                  <div>{log.placeName ?? (log.vehicleTripCode
                    ? t('journey.health.inTransit', { code: log.vehicleTripCode }) : t('journey.health.unknown'))}</div>
                  {log.healthStatus && <div>{t(`journey.health.status.${log.healthStatus}`)}</div>}
                  <div>{t('journey.health.water')} · {t(log.waterIntake ? `journey.health.intake.${log.waterIntake}` : 'journey.health.unknown')}</div>
                  <div>{t('journey.health.feed')} · {t(log.feedIntake ? `journey.health.intake.${log.feedIntake}` : 'journey.health.unknown')}</div>
                  {log.temperatureCelsius != null && <div>{t('journey.health.temperature', { value: log.temperatureCelsius })}</div>}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </section>
  )
}

// Hành trình mọi con của một đơn, tự tải theo mã đơn
export function BookingJourney({ bookingId }: { bookingId: number }) {
  const { t } = useTranslation()
  const { data, loaded, error, reload } = useLoad(() => trackingApi.bookingJourney(bookingId), [bookingId], { toastOnError: false })
  if (!loaded) return <Skeleton variant="block" lines={3} />
  if (error || !data) return <ErrorState title={t('journey.loadError')} message={error?.message} onRetry={reload} />
  if (!data.horses?.length) return <EmptyState title={t('journey.noHorses')} description={t('journey.noHorsesHint')} />
  return <div className={s.wrap}>{data.horses.map(horse => <HorseJourney key={horse.bookingHorseId} horse={horse} />)}</div>
}
