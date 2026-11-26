import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { DispatchVehicle, PlannedTrip, PlannedTripCreate, VehicleTrip } from '@shared/api/dispatch'
import { Button } from '@shared/ui/Button'
import { EmptyState } from '@shared/ui/EmptyState'
import { Modal } from '@shared/ui/Modal'
import { TripPlanPreview } from './TripPlanPreview'
import { useCreateTrip } from './useCreateTrip'

export function ReturnTripModal({ vehicle, onClose, onCreated }: {
  vehicle: DispatchVehicle
  onClose: () => void
  onCreated: (trip: VehicleTrip) => void
}) {
  const { t } = useTranslation()
  const { busy, create } = useCreateTrip(onCreated)
  const [startAt, setStartAt] = useState<string>()
  const [plan, setPlan] = useState<PlannedTrip>()
  const { id, status, currentLocationId, homeHubLocationId } = vehicle
  const request: PlannedTripCreate | undefined = id && status === 'ACTIVE' && currentLocationId && homeHubLocationId && currentLocationId !== homeHubLocationId
    ? { tripType: 'TRANSFER', vehicleId: id, places: [{ locationId: currentLocationId }, { locationId: homeHubLocationId }], bookingHorseIds: [], startAt }
    : undefined
  const ready = request && plan?.startAt && !plan.blocked
  const title = t('dispatch.board.planReturn')
  const changeStart = (next: string | undefined) => {
    if (next === startAt) return
    setPlan(undefined)
    setStartAt(next)
  }

  return (
    <Modal wide title={title} subtitle={request && t('dispatch.create.returnRoute', {
      plate: vehicle.licensePlate, from: vehicle.currentLocationName, to: vehicle.homeHubLocationCode,
    })} onClose={onClose} footer={(
      <>
        <Button onClick={onClose}>{t('ui.cancel')}</Button>
        <Button variant="primary" disabled={!ready} loading={busy}
          onClick={() => request && plan?.startAt && create({ ...request, startAt: plan.startAt })}>{title}</Button>
      </>
    )}>
      {request ? (
        <TripPlanPreview request={request} onPlan={setPlan} onStart={changeStart} />
      ) : <EmptyState title={t('dispatch.create.returnUnavailable')} description={t('dispatch.create.returnUnavailableHint')} />}
    </Modal>
  )
}
