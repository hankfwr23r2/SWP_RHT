// Lập lượt xe: lượt trục theo chuyến; lượt đón, giao, chạy thẳng chọn đơn rồi hệ thống tính giờ; chuyển xe nhập giờ tay
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dispatchApi, type DispatchBooking, type DispatchVehicle, type PlannedTrip, type TripType, type VehicleTrip } from '@shared/api/dispatch'
import { useLoad } from '@shared/hooks/useLoad'
import { formatDate, formatDateTime } from '@shared/lib/format'
import { Button } from '@shared/ui/Button'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Modal } from '@shared/ui/Modal'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import { useToast } from '@shared/ui/toast'
import { horsesNeeding, hubsOf, placesFor } from './dispatchModel'
import { emptyStop, ManualStops, stopsReady, toStops, type StopDraft } from './ManualStops'
import { TripPlanPreview } from './TripPlanPreview'
import b from './Board.module.css'
import s from './Dispatch.module.css'

interface Props {
  bookings: DispatchBooking[]
  weekFrom: string
  weekTo: string
  // Lập lượt đưa xe này về: xe về bằng chuyến trục chiều ngược (docs/NGHIEP_VU_THEO_59_BANG.md mục 4)
  returning?: DispatchVehicle
  onClose: () => void
  onCreated: (trip: VehicleTrip) => void
}

const TRIP_TYPES: TripType[] = ['PICKUP', 'LINEHAUL', 'DELIVERY', 'DIRECT', 'TRANSFER']

export function CreateTripModal({ bookings, weekFrom, weekTo, returning, onClose, onCreated }: Props) {
  const { t } = useTranslation()
  const toast = useToast()
  const [tripType, setTripType] = useState<TripType>(returning ? 'LINEHAUL' : 'PICKUP')
  const [vehicleId, setVehicleId] = useState(returning ? String(returning.id) : '')
  const [departureId, setDepartureId] = useState('')
  const [hubChoice, setHubChoice] = useState('')
  const [chosen, setChosen] = useState<number[]>([])
  const [startAt, setStartAt] = useState<string>()
  const [plan, setPlan] = useState<PlannedTrip>()
  const [manual, setManual] = useState(false)
  const [stops, setStops] = useState<StopDraft[]>([emptyStop(), emptyStop(), emptyStop()])
  const [busy, setBusy] = useState(false)
  const { data, loaded, error, reload } = useLoad(async () => {
    const [vehicles, departures, locations] = await Promise.all([dispatchApi.vehicles(), dispatchApi.departures(weekFrom, weekTo), dispatchApi.locations()])
    return { vehicles, departures, locations }
  }, [weekFrom, weekTo], { toastOnError: false })

  const vehicle = data?.vehicles.find(item => String(item.id) === vehicleId)
  // Chuyến không xuất phát từ nơi xe đang ở thì xe không chạy được (quy tắc #20)
  const fromHere = (data?.departures ?? []).filter(departure => !vehicle?.currentLocationCode || departure.originCode === vehicle.currentLocationCode)
  // Đưa xe về: ưu tiên chuyến về thẳng hub gốc; không có thì hiện mọi chuyến đi từ đây (xe ở cửa khẩu Lào về qua Đà Nẵng)
  const homeward = returning ? fromHere.filter(departure => departure.destinationCode === returning.homeHubLocationCode) : []
  const departures = homeward.length ? homeward : fromHere
  const hubs = hubsOf(data?.locations ?? [], data?.vehicles ?? [])
  const hubId = Number(hubChoice || hubs[0]?.id || 0)
  const byHand = manual || tripType === 'TRANSFER'
  const eligible = bookings.filter(booking => horsesNeeding(tripType, [booking]).length > 0)
  const picked = eligible.filter(booking => chosen.includes(booking.id ?? 0))
  const request = picked.length && hubId ? { tripType, places: placesFor(tripType, hubId, picked), bookingHorseIds: horsesNeeding(tripType, picked), startAt } : undefined
  const reset = (type: TripType) => { setTripType(type); setChosen([]); setStartAt(undefined); setPlan(undefined); setManual(false); setStops(type === 'TRANSFER' ? [emptyStop(), emptyStop()] : [emptyStop(), emptyStop(), emptyStop()]) }
  const toggle = (id: number) => { setPlan(undefined); setStartAt(undefined); setChosen(current => current.includes(id) ? current.filter(item => item !== id) : [...current, id]) }

  const finalPlace = stops.at(-1)?.place
  const needsFinalHub = tripType === 'PICKUP' || tripType === 'DELIVERY' || tripType === 'DIRECT'
  const invalidFinalPlace = byHand && needsFinalHub && !!finalPlace && !hubs.some(hub => `L${hub.id}` === finalPlace)
  const manualReady = stopsReady(stops) && !invalidFinalPlace
  const ready = vehicleId !== '' && (tripType === 'LINEHAUL' ? departureId !== '' : byHand ? manualReady : !!request && !!plan && !plan.blocked)
  const submit = async () => {
    setBusy(true)
    try {
      const trip = tripType === 'LINEHAUL' ? await dispatchApi.createTrip({ tripType, vehicleId: Number(vehicleId), departureId: Number(departureId) })
        : byHand ? await dispatchApi.createTrip({ tripType, vehicleId: Number(vehicleId), stops: toStops(stops, data?.locations ?? []) })
          : await dispatchApi.createPlannedTrip({ ...request!, vehicleId: Number(vehicleId) })
      toast(t('dispatch.done.created', { code: trip.code }), 'success')
      onCreated(trip)
    } catch (e) {
      toast(e instanceof Error ? e.message : String(e), 'error')
    } finally {
      setBusy(false)
    }
  }

  const body = () => {
    if (!loaded) return <Skeleton variant="block" lines={3} />
    if (error || !data) return <ErrorState message={error?.message} onRetry={reload} />
    return (
      <div className={s.section}>
        <div className={s.form}>
          <Field label={t('dispatch.create.type')} required>
            {c => <Select {...c} value={tripType} onChange={value => reset(value as TripType)}
              options={TRIP_TYPES.map(type => ({ value: type, label: t(`tripWords.type.${type}`) }))} />}
          </Field>
          <Field label={t('dispatch.create.vehicle')} required>
            {c => <Select {...c} value={vehicleId} onChange={setVehicleId} placeholder={t('tripWords.choose')}
              options={data.vehicles.map(vehicle => ({
                value: `${vehicle.id}`,
                label: vehicle.status === 'ACTIVE' ? t('dispatch.create.vehicleAt', { plate: vehicle.licensePlate, name: vehicle.currentLocationName ?? '—' }) : t('dispatch.create.vehicleLocked', { plate: vehicle.licensePlate, status: vehicle.status }),
                disabled: vehicle.status !== 'ACTIVE',
              }))} />}
          </Field>
          {tripType === 'LINEHAUL' && (
            <Field label={t('dispatch.create.departure')} hint={t(departures.length ? 'dispatch.create.departureHint' : 'dispatch.create.noDepartureHere')} required>
              {c => <Select {...c} value={departureId} onChange={setDepartureId} placeholder={t('tripWords.choose')}
                options={departures.map(departure => ({
                  value: `${departure.id}`,
                  label: `${departure.routeCode} · ${departure.plannedDepartureAt ? formatDateTime(new Date(departure.plannedDepartureAt)) : ''}${departure.runConfirmed ? '' : ` · ${t('dispatch.create.notConfirmed')}`}`,
                  disabled: !departure.runConfirmed || departure.status === 'CANCELLED' || departure.status === 'COMPLETED',
                }))} />}
            </Field>
          )}
          {tripType !== 'LINEHAUL' && !byHand && (
            <Field label={t('dispatch.create.hub')} required>
              {c => <Select {...c} value={hubId ? String(hubId) : ''} onChange={value => { setPlan(undefined); setHubChoice(value) }} placeholder={t('tripWords.choose')}
                options={hubs.map(hub => ({ value: String(hub.id), label: hub.name ?? '' }))} />}
            </Field>
          )}
        </div>
        {tripType !== 'LINEHAUL' && !byHand && (
          <fieldset className={`${s.section} ${s.fieldset}`}>
            <legend className={s.sectionTitle}>{t('dispatch.create.bookings')}</legend>
            {!eligible.length && <p className={s.muted}>{t('dispatch.create.noBookings')}</p>}
            <ul className={b.pickList}>
              {eligible.map(booking => (
                <li key={booking.id}>
                  <label className={b.pick}>
                    <input type="checkbox" checked={chosen.includes(booking.id ?? 0)} onChange={() => toggle(booking.id ?? 0)} />
                    <span><strong>{booking.code}</strong> · {(booking.horses ?? []).map(horse => horse.horseName).join(', ')}<br />
                      {tripType === 'DELIVERY' ? (booking.horses ?? []).map(horse => horse.deliveryAddress?.addressLine).filter(Boolean).filter((line, index, all) => all.indexOf(line) === index).join(' · ') || booking.deliveryAddress?.addressLine : booking.pickupAddress?.addressLine}
                      {booking.requestedPickupDate && ` · ${formatDate(new Date(booking.requestedPickupDate))}`}</span>
                  </label>
                </li>
              ))}
            </ul>
            <div><Button variant="ghost" size="sm" onClick={() => setManual(true)}>{t('dispatch.plan.manual')}</Button></div>
          </fieldset>
        )}
        {request && !byHand && <TripPlanPreview request={request} onPlan={setPlan} onStart={setStartAt} onManual={() => setManual(true)} />}
        {tripType !== 'LINEHAUL' && byHand && (
          <ManualStops stops={stops} onChange={setStops} bookings={bookings} locations={data.locations}
            finalPlaceError={invalidFinalPlace ? t('dispatch.create.finalPlaceHub') : undefined} />
        )}
        {manual && tripType !== 'TRANSFER' && <div><Button variant="ghost" size="sm" onClick={() => setManual(false)}>{t('dispatch.plan.backToAuto')}</Button></div>}
      </div>
    )
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
