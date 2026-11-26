import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Modal } from '@shared/ui/Modal'
import { Skeleton } from '@shared/ui/Skeleton'
import { ReasonDialog } from '@backoffice/shared/ReasonDialog'
import { useDepartureVehicles } from './useDepartureVehicles'
import s from '@backoffice/shared/StaffPanels.module.css'

export function DepartureVehiclesDialog({ id, name, onChanged, onClose }: {
  id: number
  name: string
  onChanged: () => void
  onClose: () => void
}) {
  const { t } = useTranslation()
  const { data, loadError, loaded, reload, busy, error, remove, clearError } = useDepartureVehicles(id)
  const [removing, setRemoving] = useState<number>()
  const vehicles = data?.vehicles ?? []
  const status = data?.departure?.status
  const canRemove = vehicles.length > 1 && (status === 'OPEN' || status === 'CLOSED')

  if (removing != null) {
    return <ReasonDialog title={t('mgr.departures.fleet.remove')} confirmLabel={t('mgr.departures.fleet.remove')}
      busy={busy} error={error} onCancel={() => { clearError(); setRemoving(undefined) }}
      onConfirm={reason => { void remove(removing, reason).then(saved => {
        if (saved) {
          setRemoving(undefined)
          onChanged()
        }
      }) }}>
      <p>{name}</p>
      <p>{t('mgr.departures.fleet.removeHint')}</p>
    </ReasonDialog>
  }

  return <Modal title={t('mgr.departures.fleet.title')} onClose={onClose}
    footer={<Button onClick={onClose}>{t('mgr.close')}</Button>}>
    <div className={s.stack}>
      <strong>{name}</strong>
      {loadError ? <ErrorState message={loadError.message} onRetry={reload} /> : !loaded ? <Skeleton lines={3} />
        : !vehicles.length ? <EmptyState compact title={t('mgr.departures.fleet.empty')} description={t('mgr.departures.fleet.emptyHint')} />
        : <ul className={s.stack}>{vehicles.map(vehicle => <li key={vehicle.id}>
          <div>{t('mgr.departures.fleet.vehicle', {
            count: vehicle.vehicleCount, vehicleClass: vehicle.vehicleClassCode,
            type: t(`mgr.departures.fleet.${vehicle.allotmentType}`),
          })}</div>
          {vehicle.reason && <p>{vehicle.reason}</p>}
          {canRemove && vehicle.id != null && <Button size="sm" onClick={() => setRemoving(vehicle.id)}>
            {t('mgr.departures.fleet.remove')}
          </Button>}
        </li>)}</ul>}
    </div>
  </Modal>
}
