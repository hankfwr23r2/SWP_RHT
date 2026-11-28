import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { isoToLocal, localToIso } from '@backoffice/shared/localTime'
import { dispatchApi, type VehicleTrip, type VehicleTripUpdate } from '@shared/api/dispatch'
import { useToast } from '@shared/ui/toast'

export type TimeEditMode = 'keep' | 'shift' | 'stops'

const stopDrafts = (trip: VehicleTrip) => (trip.stops ?? []).flatMap(stop => {
  if (stop.id == null || !stop.plannedArrivalAt || !stop.plannedDepartureAt) return []
  return [{
    id: stop.id, sequence: stop.sequenceNumber, name: stop.locationName ?? stop.addressLine,
    arrival: isoToLocal(stop.plannedArrivalAt), departure: isoToLocal(stop.plannedDepartureAt),
    originalArrival: stop.plannedArrivalAt, originalDeparture: stop.plannedDepartureAt,
  }]
})

export function useTripEdit(tripId: number, trip: VehicleTrip, onSaved: () => Promise<void>) {
  const { t } = useTranslation()
  const toast = useToast()
  const originalVehicleId = trip.vehicleId?.toString() ?? ''
  const originalStart = trip.plannedStartAt ? isoToLocal(trip.plannedStartAt) : ''
  const [vehicleId, setVehicleId] = useState(originalVehicleId)
  const [mode, setMode] = useState<TimeEditMode>('keep')
  const [start, setStart] = useState(originalStart)
  const [stops, setStops] = useState(() => stopDrafts(trip))
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submittedStopIds, setSubmittedStopIds] = useState<number[]>([])
  const [busy, setBusy] = useState(false)
  const timesChanged = mode === 'shift' ? start !== originalStart : mode === 'stops' && stops.some(stop =>
    stop.arrival !== isoToLocal(stop.originalArrival) || stop.departure !== isoToLocal(stop.originalDeparture))
  const timesReady = mode === 'shift' ? Boolean(start) : mode !== 'stops' || stops.every(stop => stop.arrival && stop.departure)
  const ready = Boolean(vehicleId) && timesReady && (timesChanged || vehicleId !== originalVehicleId)

  const changeStop = (id: number, field: 'arrival' | 'departure', value: string) => {
    setErrors({})
    setStops(current => current.map(stop => stop.id === id ? { ...stop, [field]: value } : stop))
  }

  const submit = async () => {
    if (!ready || busy) return
    setBusy(true)
    setErrors({})
    try {
      const request: VehicleTripUpdate = {}
      if (vehicleId !== originalVehicleId) request.vehicleId = Number(vehicleId)
      if (mode === 'shift' && timesChanged) request.plannedStartAt = localToIso(start)
      if (mode === 'stops' && timesChanged) {
        request.stops = stops.flatMap(stop => {
          const arrivalChanged = stop.arrival !== isoToLocal(stop.originalArrival)
          const departureChanged = stop.departure !== isoToLocal(stop.originalDeparture)
          return arrivalChanged || departureChanged ? [{
            stopId: stop.id,
            plannedArrivalAt: arrivalChanged ? localToIso(stop.arrival) : stop.originalArrival,
            plannedDepartureAt: departureChanged ? localToIso(stop.departure) : stop.originalDeparture,
          }] : []
        })
      }
      setSubmittedStopIds(request.stops?.map(stop => stop.stopId) ?? [])
      await dispatchApi.updateTrip(tripId, request)
      toast(t('dispatch.edit.saved'), 'success')
      await onSaved()
    } catch (error) {
      if (error instanceof Error && 'fields' in error) setErrors(error.fields as Record<string, string>)
      toast(error instanceof Error ? error.message : t('dispatch.edit.failed'), 'error')
    } finally {
      setBusy(false)
    }
  }

  return { vehicleId, setVehicleId, mode, setMode, start, setStart, stops, changeStop, errors, submittedStopIds, busy, ready, submit }
}
