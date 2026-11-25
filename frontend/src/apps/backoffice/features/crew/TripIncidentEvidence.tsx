import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { incidentsApi, type Evidence, type Incident } from '@shared/api/incidentOps'
import { useLoad } from '@shared/hooks/useLoad'
import { Button } from '@shared/ui/Button'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { ErrorState } from '@shared/ui/ErrorState'
import { Skeleton } from '@shared/ui/Skeleton'
import { EvidencePanel } from '@backoffice/shared/EvidencePanel'
import s from './MyTrips.module.css'

// Vị trí hiện tại cho ảnh hiện trường; máy không cho lấy vị trí thì gửi ảnh không kèm tọa độ
const currentPosition = () => new Promise<{ latitude: number; longitude: number } | undefined>(resolve => {
  if (!navigator.geolocation) return resolve(undefined)
  navigator.geolocation.getCurrentPosition(({ coords }) => resolve({ latitude: Number(coords.latitude.toFixed(6)), longitude: Number(coords.longitude.toFixed(6)) }),
    () => resolve(undefined), { timeout: 5000, maximumAge: 60_000 })
})

function useTripIncidentEvidence(incidentId: number) {
  const query = useLoad(() => incidentsApi.photos(incidentId), [incidentId], { toastOnError: false })
  const save = async (upload: Promise<Evidence[]>) => {
    const evidence = await upload
    query.updateData(() => evidence)
    return evidence
  }
  return {
    ...query,
    uploadPhoto: async (file: File) => save(incidentsApi.attachPhoto(incidentId, file, await currentPosition())),
    uploadRecord: (file: File) => save(incidentsApi.attachPhoto(incidentId, file, undefined, 'VEHICLE_SWAP_RECORD')),
    uploadFitPhoto: (file: File) => save(incidentsApi.attachPhoto(incidentId, file, undefined, 'FIT_CHECK_PHOTO')),
  }
}

function useRestMinutesLeft(fitEligibleAt: Incident['fitEligibleAt']) {
  const [now, setNow] = useState(Date.now)
  const deadline = fitEligibleAt ? Date.parse(fitEligibleAt) : Number.NaN
  const countingDown = now < deadline
  useEffect(() => {
    if (!countingDown) return
    const tick = () => setNow(Date.now())
    tick()
    const interval = window.setInterval(tick, 1000)
    return () => window.clearInterval(interval)
  }, [countingDown, deadline])
  return Number.isFinite(deadline) ? Math.max(0, Math.ceil((deadline - now) / 60_000)) : undefined
}

interface Props {
  incident: Incident
  canUploadRecord: boolean
  fitActions?: {
    busy: boolean
    startRest: () => Promise<boolean>
    confirmFit: () => Promise<boolean>
  }
}

export function TripIncidentEvidence({ incident, canUploadRecord, fitActions }: Props) {
  const { t } = useTranslation()
  const { data, loaded, error, reload, uploadPhoto, uploadRecord, uploadFitPhoto } = useTripIncidentEvidence(incident.id ?? 0)
  const [pendingAction, setPendingAction] = useState<'startRest' | 'confirmFit'>()
  const canUploadFit = Boolean(fitActions && incident.restStartedAt)
  const minutesLeft = useRestMinutesLeft(canUploadFit ? incident.fitEligibleAt : undefined)
  const photos = data ?? []
  const fitPhotos = photos.filter(file => file.documentTypeCode === 'FIT_CHECK_PHOTO')
  const records = photos.filter(file => file.documentTypeCode === 'VEHICLE_SWAP_RECORD')
  const canConfirm = minutesLeft === 0 && fitPhotos.length > 0 && !error

  return (
    <>
      {fitActions && !incident.restStartedAt && (
        <Button variant="primary" size="lg" fullWidth loading={fitActions.busy} onClick={() => setPendingAction('startRest')}>
          {t('myTrips.run.incident.startRest')}
        </Button>
      )}
      {canUploadFit && minutesLeft !== undefined && (
        <p className={s.muted} role="status">{minutesLeft > 0
          ? t('myTrips.run.incident.restRemaining', { minutes: minutesLeft })
          : t('myTrips.run.incident.restComplete')}</p>
      )}
      {!loaded && <Skeleton variant="block" lines={2} />}
      {error && <ErrorState message={error.message} onRetry={reload} />}
      {loaded && !error && (
        <>
          {(canUploadFit || fitPhotos.length > 0) && (
            <EvidencePanel title={t('incidents.fitCheckPhoto')} evidence={fitPhotos} accept="image/*"
              fileLabel={t('incidents.fitCheckPhotoFile')} uploadLabel={t('incidents.uploadFitCheckPhoto')}
              upload={canUploadFit ? uploadFitPhoto : undefined} />
          )}
          {fitActions && incident.restStartedAt && (
            <Button variant="primary" size="lg" fullWidth loading={fitActions.busy} disabled={!canConfirm}
              onClick={() => setPendingAction('confirmFit')}>{t('myTrips.run.incident.confirmFit')}</Button>
          )}
          <EvidencePanel title={t('incidents.photos')}
            evidence={photos.filter(file => file.documentTypeCode !== 'VEHICLE_SWAP_RECORD' && file.documentTypeCode !== 'FIT_CHECK_PHOTO')}
            upload={uploadPhoto} />
          {(canUploadRecord || records.length > 0) && <EvidencePanel title={t('incidents.vehicleSwapRecord')} evidence={records}
            accept="image/*" fileLabel={t('incidents.vehicleSwapRecordFile')} uploadLabel={t('incidents.uploadVehicleSwapRecord')}
            upload={canUploadRecord ? uploadRecord : undefined} />}
        </>
      )}
      {fitActions && pendingAction && (
        <ConfirmDialog title={t(`myTrips.run.incident.${pendingAction}Title`)} message={t(`myTrips.run.incident.${pendingAction}Message`)}
          confirmLabel={t(`myTrips.run.incident.${pendingAction}`)}
          confirmDisabled={pendingAction === 'confirmFit' ? !canConfirm : !!incident.restStartedAt}
          onCancel={() => setPendingAction(undefined)}
          onConfirm={async () => { if (await fitActions[pendingAction]()) setPendingAction(undefined) }} />
      )}
    </>
  )
}
