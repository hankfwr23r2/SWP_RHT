import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dispatchApi } from '@shared/api/dispatch'
import { useLoad } from '@shared/hooks/useLoad'
import { Button } from '@shared/ui/Button'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import s from './Incidents.module.css'

interface Props {
  hasRecord: boolean
  busy: boolean
  onSwap: (vehicleId: number) => Promise<boolean>
}

export function VehicleSwapControls({ hasRecord, busy, onSwap }: Props) {
  const { t } = useTranslation()
  const [vehicleId, setVehicleId] = useState('')
  const [confirming, setConfirming] = useState(false)
  const { data: vehicles, loaded, error, reload } = useLoad(dispatchApi.vehicles, [], { toastOnError: false })
  if (!loaded) return <Skeleton variant="block" lines={2} />
  if (error) return <ErrorState message={error.message} onRetry={reload} />

  return (
    <div className={s.form}>
      <p className={s.muted}>{t('incidents.swapVehicleHint')}</p>
      {!hasRecord && <p className={s.muted}>{t('incidents.vehicleSwapRecordNeeded')}</p>}
      <Field label={t('incidents.replacementVehicle')} required>
        {c => <Select {...c} value={vehicleId} onChange={setVehicleId} placeholder={t('incidents.replacementVehicle')}
          options={(vehicles ?? []).map(v => ({ value: String(v.id), label: v.licensePlate ?? '', disabled: v.status !== 'ACTIVE' }))} />}
      </Field>
      <Button variant="primary" disabled={!hasRecord || !vehicleId} loading={busy}
        onClick={() => setConfirming(true)}>{t('incidents.swapVehicle')}</Button>
      {confirming && <ConfirmDialog title={t('incidents.swapVehicle')}
        message={t('incidents.swapVehicleConfirm')} confirmLabel={t('incidents.swapVehicle')}
        confirmDisabled={!hasRecord || busy}
        onCancel={() => setConfirming(false)}
        onConfirm={async () => {
          if (await onSwap(Number(vehicleId))) setConfirming(false)
        }} />}
    </div>
  )
}
