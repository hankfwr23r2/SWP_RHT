// Một nhóm đơn hệ thống gợi ý: chọn xe, xem giờ theo đường thật, lập lượt xe kèm ngựa trong một lần
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dispatchApi, type DispatchVehicle, type PlannedTrip, type TripGroup, type VehicleTrip } from '@shared/api/dispatch'
import { Badge } from '@shared/ui/Badge'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { Field } from '@shared/ui/Field'
import { Select } from '@shared/ui/Select'
import { useToast } from '@shared/ui/toast'
import { TripPlanPreview } from './TripPlanPreview'
import s from './Dispatch.module.css'

export function GroupCard({ group, index, kind, vehicles, onCreated }: {
  group: TripGroup
  index: number
  kind: 'PICKUP' | 'DELIVERY'
  vehicles: DispatchVehicle[]
  onCreated: (trip: VehicleTrip) => void
}) {
  const { t } = useTranslation()
  const toast = useToast()
  const [vehicleId, setVehicleId] = useState('')
  const [preview, setPreview] = useState(false)
  const [plan, setPlan] = useState<PlannedTrip>()
  const [startAt, setStartAt] = useState<string>()
  const [busy, setBusy] = useState(false)
  const seats = (vehicle: DispatchVehicle) => (vehicle.standardStallCount ?? 0) + 2 * (vehicle.wideStallCount ?? 0)
  const fits = vehicles.filter(vehicle => seats(vehicle) >= (group.seats ?? 0))
  const request = {
    tripType: kind,
    places: (group.stops ?? []).map(stop => stop.locationId != null ? { locationId: stop.locationId } : { bookingAddressId: stop.bookingAddressId }),
    bookingHorseIds: (group.bookings ?? []).flatMap(booking => (booking.horses ?? []).map(horse => horse.bookingHorseId ?? 0)),
    startAt,
  }
  const approve = async () => {
    setBusy(true)
    try {
      const trip = await dispatchApi.createPlannedTrip({ ...request, vehicleId: Number(vehicleId) })
      toast(t('dispatch.grouping.created', { code: trip.code }), 'success')
      onCreated(trip)
    } catch (e) {
      toast(e instanceof Error ? e.message : String(e), 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card title={t('dispatch.grouping.groupTitle', { n: index + 1, count: new Set(group.bookings?.map(booking => booking.bookingId)).size })}
      actions={group.overLimit ? <Badge tone="warning">{t('dispatch.grouping.overHours')}</Badge> : undefined}>
      <div className={s.list}>
        <p className={s.muted}>{t('dispatch.grouping.summary', { seats: group.seats, km: group.routeKm, hours: group.hours })}</p>
        <ol className={s.list}>
          {(group.bookings ?? []).map(booking => (
            <li key={`${booking.bookingId}-${booking.bookingAddressId}`}>
              <strong>{booking.bookingCode}</strong>{` · ${booking.addressLine} · `}{(booking.horses ?? []).map(horse => horse.horseName).join(', ')}
            </li>
          ))}
        </ol>
        <div className={s.form}>
          <Field label={t('dispatch.grouping.vehicle')}>
            {c => <Select {...c} value={vehicleId} onChange={setVehicleId} placeholder={t('tripWords.choose')}
              options={fits.map(vehicle => ({ value: String(vehicle.id), label: `${vehicle.licensePlate} · ${seats(vehicle)}` }))} />}
          </Field>
          <Button icon={<i className="fa-solid fa-map-location-dot" />} aria-expanded={preview} onClick={() => setPreview(open => !open)}>{t('dispatch.plan.preview')}</Button>
          <Button variant="primary" disabled={!vehicleId || (preview && !!plan?.blocked)} loading={busy} onClick={() => void approve()}>{t('dispatch.grouping.approve')}</Button>
        </div>
        {preview && <TripPlanPreview request={request} onPlan={setPlan} onStart={setStartAt} />}
      </div>
    </Card>
  )
}
