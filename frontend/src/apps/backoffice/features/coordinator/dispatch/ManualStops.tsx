// Nhập giờ tay: dùng cho lượt chuyển xe hoặc khi dịch vụ bản đồ không tính được
import { useTranslation } from 'react-i18next'
import type { DispatchBooking, NetworkLocation, TripStopCreate } from '@shared/api/dispatch'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Select } from '@shared/ui/Select'
import { localToIso } from '@backoffice/shared/localTime'
import { deliveryAddresses } from './dispatchModel'
import s from './Dispatch.module.css'

// Nơi dừng: "L<id>" điểm mạng lưới, "P<id>" địa chỉ đón, "D<id>" địa chỉ giao
export interface StopDraft { place: string; arrival: string; leave: string }
export const emptyStop = (): StopDraft => ({ place: '', arrival: '', leave: '' })
export const stopsReady = (stops: StopDraft[]) => stops.every(stop => stop.place && stop.arrival && stop.leave)
// Loại điểm dừng theo loại địa điểm của mạng lưới
const STOP_TYPE_OF_LOCATION: Record<string, 'HUB' | 'REST' | 'BORDER'> = { REST_STOP: 'REST', BORDER_GATE: 'BORDER' }

export const toStops = (stops: StopDraft[], locations: NetworkLocation[]): TripStopCreate[] => stops.map(stop => {
  const id = Number(stop.place.slice(1))
  const kind = stop.place[0]
  return {
    locationId: kind === 'L' ? id : undefined,
    bookingAddressId: kind === 'L' ? undefined : id,
    stopType: kind === 'P' ? 'PICKUP' : kind === 'D' ? 'DELIVERY'
      : STOP_TYPE_OF_LOCATION[locations.find(location => location.id === id)?.locationType ?? ''] ?? 'HUB',
    plannedArrivalAt: localToIso(stop.arrival),
    plannedDepartureAt: localToIso(stop.leave),
  }
})

export function ManualStops({ stops, onChange, bookings, locations }: {
  stops: StopDraft[]
  onChange: (stops: StopDraft[]) => void
  bookings: DispatchBooking[]
  locations: NetworkLocation[]
}) {
  const { t } = useTranslation()
  const places = [
    ...locations.map(location => ({ value: `L${location.id}`, label: t(`dispatch.create.place${location.locationType === 'REST_STOP' ? 'Rest' : location.locationType === 'BORDER_GATE' ? 'Gate' : 'Hub'}`, { name: location.name }) })),
    ...bookings.flatMap(booking => [
      booking.pickupAddress && { value: `P${booking.pickupAddress.id}`, label: t('dispatch.create.placePickup', { code: booking.code, line: booking.pickupAddress.addressLine }) },
      ...deliveryAddresses(booking).map(address => ({
        value: `D${address.id}`, label: t('dispatch.create.placeDelivery', { code: booking.code, line: address.addressLine }),
      })),
    ].filter(Boolean) as { value: string; label: string }[]),
  ]
  const update = (index: number, patch: Partial<StopDraft>) => onChange(stops.map((stop, i) => (i === index ? { ...stop, ...patch } : stop)))
  return (
    <fieldset className={`${s.section} ${s.fieldset}`}>
      <legend className={s.sectionTitle}>{t('dispatch.create.stops')}</legend>
      <p className={s.muted}>{t('dispatch.create.stopsHint')}</p>
      {stops.map((stop, index) => (
        <div key={index} className={s.stopRow}>
          <Field label={t('dispatch.create.place', { n: index + 1 })} required>
            {c => <Select {...c} value={stop.place} onChange={value => update(index, { place: value })} placeholder={t('tripWords.choose')} options={places} />}
          </Field>
          <Field label={t('dispatch.create.arrival')} required>
            {c => <Input {...c} type="datetime-local" value={stop.arrival} onChange={e => update(index, { arrival: e.target.value })} />}
          </Field>
          <Field label={t('dispatch.create.leave')} required>
            {c => <Input {...c} type="datetime-local" value={stop.leave} onChange={e => update(index, { leave: e.target.value })} />}
          </Field>
          <Button icon={<i className="fa-solid fa-trash" />} aria-label={t('dispatch.create.removeStop', { n: index + 1 })}
            disabled={stops.length <= 2} onClick={() => onChange(stops.filter((_, i) => i !== index))} />
        </div>
      ))}
      <div><Button icon={<i className="fa-solid fa-plus" />} onClick={() => onChange([...stops, emptyStop()])}>{t('dispatch.create.addStop')}</Button></div>
    </fieldset>
  )
}
