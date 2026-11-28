import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { CrewTrip, DeliveryRecipient, Handover, HandoverScanRequest } from '@shared/api/tripExecution'
import { formatDateTime } from '@shared/lib/format'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { FilePreviewButton } from '@shared/ui/FilePreviewButton'
import { Input } from '@shared/ui/Input'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { HandoverChipForm } from './HandoverChipForm'
import { HandoverPapersPanel } from './HandoverPapersPanel'
import { HorseFeedingDetails } from './HorseFeedingDetails'
import { canRefuse, isStableHandover, needsRecord, receivesOriginals } from './tripRunModel'
import s from './MyTrips.module.css'

interface Props {
  handover: Handover
  horses: CrewTrip['horses']
  busy: boolean
  onScan: (request: HandoverScanRequest, onFields: (fields: Record<string, string>) => void) => Promise<boolean>
  onPhoto: (legId: number, file: File) => void
  onComplete: (recipients?: Record<string, DeliveryRecipient>) => void
  onStartWaiting: () => void
  onTrip: (trip: CrewTrip) => void
  onRefuse: (horse: { handoverId: number; legId: number; name: string }) => void
  onConfirmStay: (handover: Handover) => void
  onFail: (handoverId: number) => void
}

export function TripHandoverPanel({ handover, horses, busy, onScan, onPhoto, onComplete, onStartWaiting,
  onTrip, onRefuse, onConfirmStay, onFail }: Props) {
  const { t } = useTranslation()
  const [recipients, setRecipients] = useState<Record<string, DeliveryRecipient>>({})
  const [photos, setPhotos] = useState<Record<number, File>>({})
  const handoverId = handover.id ?? 0
  const deliveryCodes = handover.handoverType === 'DELIVERY' ? [...new Set(
    (handover.horses ?? []).filter(horse => horse.microchipMatched).map(horse => horse.bookingCode ?? ''),
  )] : []
  const setRecipient = (code: string, change: Partial<DeliveryRecipient>) =>
    setRecipients(current => ({ ...current, [code]: { ...current[code], ...change } }))
  const stallOf = (legId: number) => horses?.find(horse => horse.leg?.id === legId)?.leg?.stallPosition

  return (
    <div className={s.body}>
      <StatusBadge tone="info" label={`${t(`myTrips.run.handover.${handover.handoverType}`)} · ${t(`myTrips.run.handoverStatus.${handover.status}`)}`} />
      {handover.partyName && <p className={s.line}>{t('myTrips.run.stayIn.party', { name: handover.partyName })}</p>}
      <ul className={s.list}>
        {(handover.horses ?? []).map(horse => {
          const legId = horse.horseLegId ?? 0
          const needsPhoto = handover.handoverType === 'PICKUP' || handover.handoverType === 'DELIVERY'
          return (
            <li key={`${handoverId}:${legId}`} className={s.body}>
              <p className={s.line}>{horse.horseName} · {horse.bookingCode}</p>
              <HorseFeedingDetails horse={horse} />
              {/* Chip đúng thì chỉ chỗ dẫn ngựa vào (MAN_HINH 4.2, 6.1) */}
              {horse.microchipMatched && stallOf(legId) && <p className={s.line}><strong>{t('myTrips.run.stall', { n: stallOf(legId) })}</strong></p>}
              <StatusBadge
                tone={horse.microchipMatched === undefined || horse.microchipMatched === null ? 'neutral' : horse.microchipMatched ? 'success' : 'danger'}
                label={horse.microchipMatched === undefined || horse.microchipMatched === null ? t('myTrips.run.notScanned')
                  : horse.microchipMatched ? t('myTrips.run.matched') : t('myTrips.run.mismatched')}
              />
              {horse.entryMethod === 'MANUAL' && <StatusBadge tone="neutral" label={t('handoverEvidence.manualEntry')} />}
              <HandoverChipForm horse={horse} needsCondition={needsPhoto} busy={busy} onSave={onScan} />
              {horse.photoAttached && <StatusBadge tone="success" label={t('handoverEvidence.photoAttached')} />}
              {needsPhoto && horse.microchipMatched && <Field label={t('handoverEvidence.photo')} required>
                {c => <>
                  <Input {...c} type="file" accept="image/*" disabled={busy} onChange={e => {
                    const photo = e.target.files?.[0]
                    if (photo) {
                      setPhotos(current => ({ ...current, [legId]: photo }))
                      onPhoto(legId, photo)
                    }
                  }} />
                  {photos[legId] && <FilePreviewButton fileName={photos[legId].name} file={photos[legId]}
                    label={t('ui.previewFile')} />}
                </>}
              </Field>}
              {canRefuse(handover) && (
                <Button variant="danger" size="lg" fullWidth
                  onClick={() => onRefuse({ handoverId: handover.id ?? 0, legId, name: horse.horseName ?? '' })}>
                  {t('myTrips.run.refusal.refuse')}
                </Button>
              )}
            </li>
          )
        })}
      </ul>
      {deliveryCodes.map(code => <div key={code} className={s.body}>
        <Field label={`${t('handoverEvidence.recipient')} · ${code}`} required>
          {c => <Input {...c} maxLength={120} value={recipients[code]?.recipientName ?? ''}
            onChange={e => setRecipient(code, { recipientName: e.target.value })} />}
        </Field>
        <Field label={t('handoverEvidence.code')}>
          {c => <Input {...c} inputMode="numeric" maxLength={6} value={recipients[code]?.collectionCode ?? ''}
            onChange={e => setRecipient(code, { collectionCode: e.target.value || undefined })} />}
        </Field>
      </div>)}
      {needsRecord(handover) && <HandoverPapersPanel handover={handover} receiving={receivesOriginals(handover)} onTrip={onTrip} />}
      {needsRecord(handover) && !handover.recordAttached && <p className={s.muted}>{t('myTrips.run.papers.needRecord')}</p>}
      <Button variant="primary" size="lg" fullWidth loading={busy} disabled={needsRecord(handover) && !handover.recordAttached}
        onClick={() => isStableHandover(handover.handoverType) ? onConfirmStay(handover)
          : onComplete(handover.handoverType === 'DELIVERY' ? recipients : undefined)}>
        {t('myTrips.run.completeHandover')}
      </Button>
      {handover.handoverType === 'DELIVERY' && (handover.waitStartedAt
        ? (
          <>
            <p className={s.muted}>{t('myTrips.run.delivery.waitingSince', { time: formatDateTime(new Date(handover.waitStartedAt)) })}</p>
            <Button variant="danger" size="lg" fullWidth onClick={() => onFail(handoverId)}>{t('myTrips.run.delivery.noRecipient')}</Button>
          </>
        )
        : (
          <Button size="lg" fullWidth loading={busy}
            onClick={onStartWaiting}>
            {t('myTrips.run.delivery.startWaiting')}
          </Button>
        ))}
    </div>
  )
}
