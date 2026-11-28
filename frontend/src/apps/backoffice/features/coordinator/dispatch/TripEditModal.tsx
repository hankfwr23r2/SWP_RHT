import { useTranslation } from 'react-i18next'
import { dispatchApi, type VehicleTrip } from '@shared/api/dispatch'
import { useLoad } from '@shared/hooks/useLoad'
import { Button } from '@shared/ui/Button'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import { useTripEdit, type TimeEditMode } from './useTripEdit'
import s from './Dispatch.module.css'

interface Props {
  tripId: number
  trip: VehicleTrip
  onClose: () => void
  onSaved: () => Promise<void>
}

export function TripEditModal({ tripId, trip, onClose, onSaved }: Props) {
  const { t } = useTranslation()
  const { data: vehicles, error, loaded, reload } = useLoad(dispatchApi.vehicles, [], { toastOnError: false })
  const edit = useTripEdit(tripId, trip, onSaved)
  const linehaul = trip.tripType === 'LINEHAUL'
  const options = [
    { value: 'keep', label: t('dispatch.edit.keep') },
    { value: 'shift', label: t('dispatch.edit.shift') },
    ...(trip.manualStopTimesEditable ? [{ value: 'stops', label: t('dispatch.edit.stops') }] : []),
  ]
  const close = () => { if (!edit.busy) onClose() }

  const content = () => {
    if (!loaded) return <Skeleton variant="block" lines={3} />
    if (error || !vehicles) return <ErrorState message={error?.message} onRetry={reload} />
    if (!vehicles.length) return <EmptyState title={t('dispatch.edit.noVehicles')} description={t('dispatch.edit.noVehiclesHint')} />
    return (
      <div className={s.section}>
        <p className={s.muted}>{t('dispatch.edit.notice')}</p>
        <Field label={t('dispatch.create.vehicle')} error={edit.errors.vehicleId} required>
          {c => <Select {...c} value={edit.vehicleId} onChange={edit.setVehicleId} disabled={edit.busy} options={vehicles.map(vehicle => ({
            value: String(vehicle.id),
            label: vehicle.status === 'ACTIVE' ? (vehicle.licensePlate ?? '') : t('dispatch.create.vehicleLocked', { plate: vehicle.licensePlate, status: vehicle.status }),
            disabled: vehicle.status !== 'ACTIVE',
          }))} />}
        </Field>
        {linehaul ? <p className={s.muted}>{t('dispatch.edit.linehaul')}</p> : (
          <Field label={t('dispatch.edit.schedule')} error={edit.errors.stops}>
            {c => <Select {...c} value={edit.mode} onChange={value => edit.setMode(value as TimeEditMode)} disabled={edit.busy} options={options} />}
          </Field>
        )}
        {edit.mode === 'shift' && (
          <Field label={t('dispatch.edit.start')} hint={t('dispatch.edit.shiftHint')} error={edit.errors.plannedStartAt ?? edit.errors.plannedEndAt} required>
            {c => <Input {...c} type="datetime-local" value={edit.start} disabled={edit.busy} onChange={e => edit.setStart(e.target.value)} />}
          </Field>
        )}
        {edit.mode === 'stops' && edit.stops.map(stop => {
          const field = `stops[${edit.submittedStopIds.indexOf(stop.id)}]`
          return (
            <fieldset className={`${s.section} ${s.fieldset}`} key={stop.id}>
              <legend className={s.sectionTitle}>{t('dispatch.edit.stop', { n: stop.sequence, name: stop.name })}</legend>
              <div className={s.form}>
                <Field label={t('dispatch.create.arrival')} error={edit.errors[`${field}.plannedArrivalAt`]} required>
                  {c => <Input {...c} type="datetime-local" value={stop.arrival} disabled={edit.busy} onChange={e => edit.changeStop(stop.id, 'arrival', e.target.value)} />}
                </Field>
                <Field label={t('dispatch.create.leave')} error={edit.errors[`${field}.plannedDepartureAt`]} required>
                  {c => <Input {...c} type="datetime-local" value={stop.departure} disabled={edit.busy} onChange={e => edit.changeStop(stop.id, 'departure', e.target.value)} />}
                </Field>
              </div>
            </fieldset>
          )
        })}
      </div>
    )
  }

  return (
    <Modal title={t('dispatch.edit.title')} subtitle={trip.code} onClose={close} footer={(
      <>
        <Button onClick={close} disabled={edit.busy}>{t('ui.cancel')}</Button>
        <Button variant="primary" disabled={!edit.ready || !vehicles?.length || Boolean(error)} loading={edit.busy}
          onClick={() => void edit.submit()}>{t('dispatch.edit.save')}</Button>
      </>
    )}>
      {content()}
    </Modal>
  )
}
