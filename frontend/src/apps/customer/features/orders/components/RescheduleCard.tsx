import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { bookingOrdersApi } from '@shared/api/bookingOrders'
import { useLoad } from '@shared/hooks/useLoad'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Skeleton } from '@shared/ui/Skeleton'
import { moment, showDate } from '../format'
import type { OrderData } from '../loaders'
import { dayAfter, dayBefore, todayIso } from '../dates'
import { useOrderAction } from '../useOrderAction'
import s from '../Orders.module.css'

// Chọn lại chuyến khi lịch phải đổi
export function RescheduleCard({ data }: { data: OrderData }) {
  const { t } = useTranslation()
  const { run, busy, error } = useOrderAction()
  const [today] = useState(() => todayIso())
  const [departureId, setDepartureId] = useState<number>()
  const [pickupDate, setPickupDate] = useState('')
  const [confirming, setConfirming] = useState(false)
  const booking = data.detail?.booking
  const seats = (data.detail?.horses ?? []).filter(horse => horse.status === 'ACTIVE')
    .reduce((sum, horse) => sum + (data.stallTypes?.find(stall => stall.code === horse.stallTypeCode)?.seats ?? 1), 0)

  const { data: suggestion, loaded, error: loadError, reload } = useLoad(async () => {
    const pickup = data.detail?.addresses?.find(address => address.addressType === 'PICKUP')
    const delivery = data.detail?.addresses?.find(address => address.addressType === 'DELIVERY')
    return bookingOrdersApi.suggestions({
      bookingType: booking!.bookingType!, fromDate: dayAfter(today), stalls: Math.max(1, seats),
      pickupProvinceCode: pickup?.provinceCode ?? '', deliveryProvinceCode: delivery?.provinceCode ?? '',
      pickupLatitude: pickup?.latitude, pickupLongitude: pickup?.longitude,
      deliveryLatitude: delivery?.latitude, deliveryLongitude: delivery?.longitude,
    })
  }, [data.id], { toastOnError: false })

  const departures = (suggestion?.departures ?? []).filter(departure => departure.id !== booking?.departureId)
  const selected = departures.find(departure => departure.id === departureId)
  const last = selected?.departureDate && (booking?.bookingType === 'IMPORT' ? selected.departureDate : dayBefore(selected.departureDate))
  const badDate = !!selected && (!pickupDate || pickupDate < today || (!!last && pickupDate > last))

  const choose = (id: number, departureDate?: string) => {
    setDepartureId(id)
    if (departureDate) setPickupDate(booking?.bookingType === 'IMPORT' ? departureDate : dayBefore(departureDate))
  }
  const send = async () => {
    setConfirming(false)
    await run(() => bookingOrdersApi.reschedule(data.id, { departureId: departureId!, requestedPickupDate: pickupDate }), t('myOrders.reschedule.done'))
  }

  return (
    <Card title={t('myOrders.reschedule.title')}>
      <div className={s.stack}>
        {loadError ? <ErrorState message={loadError.message} onRetry={reload} /> : !loaded ? <Skeleton lines={3} /> : !departures.length
          ? <EmptyState icon={<i className="fa-solid fa-truck" />} title={t('bookingForm.route.noDeparture')} description={t('myOrders.reschedule.none')} />
          : (
            <fieldset className={s.options}>
              {departures.map(departure => (
                <label key={departure.id} className={s.option}>
                  <input type="radio" name="reschedule" checked={departure.id === departureId} onChange={() => choose(departure.id!, departure.departureDate)} />
                  <strong>{departure.plannedDepartureAt ? moment(departure.plannedDepartureAt) : showDate(departure.departureDate ?? '')}</strong>
                  <span>{departure.routeName}</span>
                  <span>{t('bookingForm.route.seats', { count: departure.availableStalls ?? 0 })}</span>
                </label>
              ))}
            </fieldset>
          )}
        {selected && (
          <Field label={t('bookingForm.route.pickupDate')} required error={badDate ? t('bookingForm.route.badDate') : undefined}>
            {control => <Input {...control} type="date" min={today} max={last} value={pickupDate} onChange={event => setPickupDate(event.target.value)} />}
          </Field>
        )}
        {error && <p className={s.error} role="alert">{error}</p>}
        <div><Button variant="primary" loading={busy} disabled={!selected || badDate} onClick={() => setConfirming(true)}>{t('myOrders.reschedule.send')}</Button></div>
      </div>
      {confirming && selected && <ConfirmDialog title={t('myOrders.reschedule.confirmTitle')}
        message={t('myOrders.reschedule.confirmMessage', { date: showDate(selected.departureDate ?? '') })}
        confirmLabel={t('myOrders.reschedule.send')} onConfirm={send} onCancel={() => setConfirming(false)} />}
    </Card>
  )
}
