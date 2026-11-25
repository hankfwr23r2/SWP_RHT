import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dispatchApi, type PlannedTripCreate, type VehicleTrip, type VehicleTripCreate } from '@shared/api/dispatch'
import { useToast } from '@shared/ui/toast'

export function useCreateTrip(onCreated: (trip: VehicleTrip) => void) {
  const { t } = useTranslation()
  const toast = useToast()
  const [busy, setBusy] = useState(false)

  const create = async (request: PlannedTripCreate | VehicleTripCreate) => {
    setBusy(true)
    try {
      const trip = 'places' in request ? await dispatchApi.createPlannedTrip(request) : await dispatchApi.createTrip(request)
      toast(t('dispatch.done.created', { code: trip.code }), 'success')
      onCreated(trip)
    } catch (error) {
      toast(error instanceof Error ? error.message : String(error), 'error')
    } finally {
      setBusy(false)
    }
  }

  return { busy, create }
}
