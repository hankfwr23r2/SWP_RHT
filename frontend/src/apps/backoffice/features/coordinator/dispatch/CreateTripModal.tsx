// Lập lượt xe: trục theo chuyến (điểm dừng chép từ tuyến), hoặc đón, giao, chạy thẳng, chuyển xe với điểm dừng tự lập
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dispatchApi, type DispatchBooking, type TripType, type VehicleTrip } from '@shared/api/dispatch'
import { useLoad } from '@shared/hooks/useLoad'
import { formatDateTime } from '@shared/lib/format'
import { Button } from '@shared/ui/Button'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import { useToast } from '@shared/ui/toast'
import { localToIso } from './dispatchModel'
import s from './Dispatch.module.css'

interface Props {
  bookings: DispatchBooking[]
  weekFrom: string
  weekTo: string
  onClose: () => void
  onCreated: (trip: VehicleTrip) => void
}

// Nơi dừng: "L<id>" điểm mạng lưới, "P<id>" địa chỉ đón, "D<id>" địa chỉ giao
interface StopDraft { place: string; arrival: string; leave: string }
const TRIP_TYPES: TripType[] = ['PICKUP', 'LINEHAUL', 'DELIVERY', 'DIRECT', 'TRANSFER']
const emptyStop = (): StopDraft => ({ place: '', arrival: '', leave: '' })
// Loại điểm dừng theo loại địa điểm của mạng lưới
const STOP_TYPE_OF_LOCATION: Record<string, 'HUB' | 'REST' | 'BORDER'> = { REST_STOP: 'REST', BORDER_GATE: 'BORDER' }

export function CreateTripModal({ bookings, weekFrom, weekTo, onClose, onCreated }: Props) {
  const { t } = useTranslation()
  const toast = useToast()
  const [tripType, setTripType] = useState<TripType>('PICKUP')
  const [vehicleId, setVehicleId] = useState('')
  const [departureId, setDepartureId] = useState('')
  const [stops, setStops] = useState<StopDraft[]>([emptyStop(), emptyStop(), emptyStop()])
  const [busy, setBusy] = useState(false)
  const { data, loaded, error, reload } = useLoad(async () => {
    const [vehicles, departures, locations] = await Promise.all([dispatchApi.vehicles(), dispatchApi.departures(weekFrom, weekTo), dispatchApi.locations()])
    return { vehicles, departures, locations }
  }, [weekFrom, weekTo], { toastOnError: false })

  const body = () => {
    if (!loaded) return <Skeleton variant="block" lines={3} />
    if (error || !data) return <ErrorState message={error?.message} onRetry={reload} />
    const places = [
      ...data.locations.map(location => ({ value: `L${location.id}`, label: t('dispatch.create.placeHub', { name: location.name }) })),
      ...bookings.flatMap(booking => [
        booking.pickupAddress && { value: `P${booking.pickupAddress.id}`, label: t('dispatch.create.placePickup', { code: booking.code, line: booking.pickupAddress.addressLine }) },
        booking.deliveryAddress && { value: `D${booking.deliveryAddress.id}`, label: t('dispatch.create.placeDelivery', { code: booking.code, line: booking.deliveryAddress.addressLine }) },
      ].filter(Boolean) as { value: string; label: string }[]),
    ]
    const update = (index: number, patch: Partial<StopDraft>) => setStops(current => current.map((stop, i) => (i === index ? { ...stop, ...patch } : stop)))
    return (
      <div className={s.section}>
        <div className={s.form}>
          <Field label={t('dispatch.create.type')} required>
            {c => <Select {...c} value={tripType} onChange={value => setTripType(value as TripType)}
              options={TRIP_TYPES.map(type => ({ value: type, label: t(`dispatch.tripType.${type}`) }))} />}
          </Field>
          <Field label={t('dispatch.create.vehicle')} required>
            {c => <Select {...c} value={vehicleId} onChange={setVehicleId} placeholder={t('dispatch.detail.choose')}
              options={data.vehicles.map(vehicle => ({
                value: `${vehicle.id}`,
                label: vehicle.status === 'ACTIVE' ? `${vehicle.licensePlate}` : t('dispatch.create.vehicleLocked', { plate: vehicle.licensePlate, status: vehicle.status }),
                disabled: vehicle.status !== 'ACTIVE',
              }))} />}
          </Field>
          {tripType === 'LINEHAUL' && (
            <Field label={t('dispatch.create.departure')} hint={t('dispatch.create.departureHint')} required>
              {c => <Select {...c} value={departureId} onChange={setDepartureId} placeholder={t('dispatch.detail.choose')}
                options={data.departures.map(departure => ({
                  value: `${departure.id}`,
                  label: `${departure.routeCode} · ${departure.plannedDepartureAt ? formatDateTime(new Date(departure.plannedDepartureAt)) : ''}`,
                  disabled: departure.status === 'CANCELLED' || departure.status === 'COMPLETED',
                }))} />}
            </Field>
          )}
        </div>
        {tripType !== 'LINEHAUL' && (
          <fieldset className={s.section}>
            <legend className={s.sectionTitle}>{t('dispatch.create.stops')}</legend>
            <p className={s.muted}>{t('dispatch.create.stopsHint')}</p>
            {stops.map((stop, index) => (
              <div key={index} className={s.stopRow}>
                <Field label={t('dispatch.create.place', { n: index + 1 })} required>
                  {c => <Select {...c} value={stop.place} onChange={value => update(index, { place: value })} placeholder={t('dispatch.detail.choose')} options={places} />}
                </Field>
                <Field label={t('dispatch.create.arrival')} required>
                  {c => <Input {...c} type="datetime-local" value={stop.arrival} onChange={e => update(index, { arrival: e.target.value })} />}
                </Field>
                <Field label={t('dispatch.create.leave')} required>
                  {c => <Input {...c} type="datetime-local" value={stop.leave} onChange={e => update(index, { leave: e.target.value })} />}
                </Field>
                <Button icon={<i className="fa-solid fa-trash" />} aria-label={t('dispatch.create.removeStop', { n: index + 1 })}
                  disabled={stops.length <= 2} onClick={() => setStops(current => current.filter((_, i) => i !== index))} />
              </div>
            ))}
            <div>
              <Button icon={<i className="fa-solid fa-plus" />} onClick={() => setStops(current => [...current, emptyStop()])}>{t('dispatch.create.addStop')}</Button>
            </div>
          </fieldset>
        )}
      </div>
    )
  }

  const ready = vehicleId !== '' && (tripType === 'LINEHAUL' ? departureId !== '' : stops.every(stop => stop.place && stop.arrival && stop.leave))
  const submit = async () => {
    setBusy(true)
    try {
      const trip = await dispatchApi.createTrip(tripType === 'LINEHAUL'
        ? { tripType, vehicleId: Number(vehicleId), departureId: Number(departureId) }
        : {
            tripType,
            vehicleId: Number(vehicleId),
            stops: stops.map(stop => {
              const id = Number(stop.place.slice(1))
              const kind = stop.place[0]
              return {
                locationId: kind === 'L' ? id : undefined,
                bookingAddressId: kind === 'L' ? undefined : id,
                stopType: kind === 'P' ? 'PICKUP' : kind === 'D' ? 'DELIVERY'
                  : STOP_TYPE_OF_LOCATION[data?.locations.find(location => location.id === id)?.locationType ?? ''] ?? 'HUB',
                plannedArrivalAt: localToIso(stop.arrival),
                plannedDepartureAt: localToIso(stop.leave),
              }
            }),
          })
      toast(t('dispatch.done.created', { code: trip.code }), 'success')
      onCreated(trip)
    } catch (e) {
      toast(e instanceof Error ? e.message : String(e), 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal
      wide
      title={t('dispatch.create.title')}
      onClose={onClose}
      footer={(
        <>
          <Button onClick={onClose}>{t('ui.cancel')}</Button>
          <Button variant="primary" disabled={!ready} loading={busy} onClick={submit}>{t('dispatch.create.submit')}</Button>
        </>
      )}
    >
      {body()}
    </Modal>
  )
}
