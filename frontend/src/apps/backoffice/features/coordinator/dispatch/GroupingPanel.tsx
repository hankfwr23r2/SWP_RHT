// Gợi ý gom đơn vào lượt đón hoặc giao (màn 3.9): hệ thống đề xuất nhóm đủ chỗ, đi vòng không quá 1,5 lần, không quá 4 giờ;
// điều phối duyệt bằng cách chọn xe, hệ thống lập lượt xe với các điểm dừng gợi ý rồi xếp ngựa
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dispatchApi, type TripGroup, type TripStopCreate, type VehicleTrip } from '@shared/api/dispatch'
import { useLoad } from '@shared/hooks/useLoad'
import { formatDateTime } from '@shared/lib/format'
import { Badge } from '@shared/ui/Badge'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import { useToast } from '@shared/ui/toast'
import s from './Dispatch.module.css'

type Kind = 'PICKUP' | 'DELIVERY'

const MAX_HOURS = 4

export function GroupingPanel({ defaultDate, onCreated }: { defaultDate: string; onCreated: (trip: VehicleTrip) => void }) {
  const { t } = useTranslation()
  const toast = useToast()
  const [date, setDate] = useState(defaultDate)
  const [hubId, setHubId] = useState('')
  const [kind, setKind] = useState<Kind>('PICKUP')
  const [vehicleOf, setVehicleOf] = useState<Record<number, string>>({})
  const [busy, setBusy] = useState<number>()
  const { data: setup } = useLoad(async () => {
    const [locations, vehicles] = await Promise.all([dispatchApi.locations(), dispatchApi.vehicles()])
    return { hubs: locations.filter(l => l.locationType === 'HUB'), vehicles: vehicles.filter(v => v.status === 'ACTIVE') }
  }, [])
  const { data: groups, loaded, error, reload } = useLoad(
    async () => (hubId && date ? dispatchApi.groupingSuggestions(date, Number(hubId), kind) : []), [hubId, date, kind], { toastOnError: false })

  // Lập lượt xe theo gợi ý rồi xếp từng con: lượt đón lên ở địa chỉ, xuống ở hub; lượt giao lên ở hub, xuống ở địa chỉ
  const approve = async (group: TripGroup, index: number) => {
    setBusy(index)
    try {
      // Backend luôn trả đủ loại điểm dừng và giờ cho từng điểm gợi ý
      const trip = await dispatchApi.createTrip({ tripType: kind, vehicleId: Number(vehicleOf[index]), stops: (group.stops ?? []) as TripStopCreate[] })
      const stops = trip.stops ?? []
      for (const booking of group.bookings ?? []) {
        const at = stops.find(stop => stop.bookingAddressId === booking.bookingAddressId)
        const hub = kind === 'PICKUP' ? stops[stops.length - 1] : stops[0]
        for (const horse of booking.horses ?? []) {
          await dispatchApi.assignHorse(trip.id ?? 0, horse.bookingHorseId ?? 0,
            kind === 'PICKUP' ? at?.id ?? 0 : hub.id ?? 0, kind === 'PICKUP' ? hub.id ?? 0 : at?.id ?? 0)
        }
      }
      toast(t('dispatch.grouping.created', { code: trip.code }), 'success')
      void reload()
      onCreated(trip)
    } catch (e) {
      toast(e instanceof Error ? e.message : String(e), 'error')
    } finally {
      setBusy(undefined)
    }
  }

  const content = () => {
    if (!hubId) return <p className={s.muted}>{t('dispatch.grouping.chooseHub')}</p>
    if (!loaded) return <Skeleton variant="block" lines={3} />
    if (error || !groups) return <ErrorState message={error?.message} onRetry={reload} />
    if (!groups.length) {
      return <EmptyState icon={<i className="fa-solid fa-route" />} title={t('dispatch.grouping.emptyTitle')} description={t('dispatch.grouping.emptyDescription')} />
    }
    return (
      <ul className={s.list} aria-label={t('dispatch.grouping.list')}>
        {groups.map((group, index) => {
          const fits = (setup?.vehicles ?? []).filter(v => (v.standardStallCount ?? 0) + 2 * (v.wideStallCount ?? 0) >= (group.seats ?? 0))
          return (
            <li key={(group.bookings ?? []).map(b => b.bookingId).join('-')}>
              <Card title={t('dispatch.grouping.groupTitle', { n: index + 1, count: group.bookings?.length ?? 0 })}
                actions={(group.hours ?? 0) > MAX_HOURS ? <Badge tone="warning">{t('dispatch.grouping.overHours')}</Badge> : undefined}>
                <div className={s.list}>
                  <p className={s.muted}>{t('dispatch.grouping.summary', { seats: group.seats, km: group.routeKm, hours: group.hours })}</p>
                  <ol className={s.list}>
                    {(group.bookings ?? []).map(booking => (
                      <li key={booking.bookingId}>
                        <strong>{booking.bookingCode}</strong>{` · ${booking.addressLine} · `}
                        {(booking.horses ?? []).map(h => h.horseName).join(', ')}
                      </li>
                    ))}
                  </ol>
                  <p className={s.muted}>{t('dispatch.grouping.window', {
                    start: group.stops?.[0]?.plannedDepartureAt ? formatDateTime(new Date(group.stops[0].plannedDepartureAt)) : '',
                    end: group.stops?.at(-1)?.plannedArrivalAt ? formatDateTime(new Date(group.stops.at(-1)!.plannedArrivalAt!)) : '',
                  })}</p>
                  <div className={s.form}>
                    <Field label={t('dispatch.grouping.vehicle')}>
                      {c => <Select {...c} value={vehicleOf[index] ?? ''} onChange={value => setVehicleOf(current => ({ ...current, [index]: value }))}
                        placeholder={t('dispatch.detail.choose')}
                        options={fits.map(v => ({ value: String(v.id), label: `${v.licensePlate} · ${(v.standardStallCount ?? 0) + 2 * (v.wideStallCount ?? 0)}` }))} />}
                    </Field>
                    <Button variant="primary" disabled={!vehicleOf[index]} loading={busy === index} onClick={() => void approve(group, index)}>
                      {t('dispatch.grouping.approve')}
                    </Button>
                  </div>
                </div>
              </Card>
            </li>
          )
        })}
      </ul>
    )
  }

  return (
    <div className={s.section}>
      <div className={s.form}>
        <Field label={t('dispatch.grouping.date')}>
          {c => <Input {...c} type="date" value={date} onChange={e => e.target.value && setDate(e.target.value)} />}
        </Field>
        <Field label={t('dispatch.stays.hub')}>
          {c => <Select {...c} value={hubId} onChange={setHubId} placeholder={t('dispatch.detail.choose')}
            options={(setup?.hubs ?? []).map(hub => ({ value: String(hub.id), label: hub.name ?? '' }))} />}
        </Field>
        <Field label={t('dispatch.grouping.kind')}>
          {c => <Select {...c} value={kind} onChange={value => setKind(value as Kind)}
            options={[{ value: 'PICKUP', label: t('dispatch.tripType.PICKUP') }, { value: 'DELIVERY', label: t('dispatch.tripType.DELIVERY') }]} />}
        </Field>
      </div>
      {content()}
    </div>
  )
}
