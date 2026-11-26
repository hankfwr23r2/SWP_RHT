import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { BookingReschedule } from '@shared/api/bookingOrders'
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
import { dayBefore } from '../dates'
import { useReschedule } from '../useReschedule'
import s from '../Orders.module.css'

// Chọn lại chuyến khi lịch phải đổi
interface Props {
  data: OrderData
  excludeDepartureId?: number
  reschedule?: (body: BookingReschedule) => Promise<unknown>
}

export function RescheduleCard({ data, excludeDepartureId, reschedule }: Props) {
  const { t } = useTranslation()
  const { today, merge, suggestions, submit, accept, busy, error } = useReschedule(data, reschedule)
  const [departureId, setDepartureId] = useState<number>()
  const [pickupDate, setPickupDate] = useState('')
  const [confirming, setConfirming] = useState(false)
  const booking = data.detail?.booking
  const { data: suggestion, loaded, error: loadError, reload } = suggestions
  const offered = booking?.status === 'SCHEDULE_CHANGE_REQUIRED' && merge.data?.departureId != null ? merge.data : null

  const departures = (suggestion?.departures ?? []).filter(departure => departure.id !== (excludeDepartureId ?? booking?.departureId))
  const selected = departures.find(departure => departure.id === departureId)
  const last = selected?.departureDate && (booking?.bookingType === 'IMPORT' ? selected.departureDate : dayBefore(selected.departureDate))
  const badDate = !!selected && (!pickupDate || pickupDate < today || (!!last && pickupDate > last))

  const choose = (id: number, departureDate?: string) => {
    setDepartureId(id)
    if (departureDate) setPickupDate(booking?.bookingType === 'IMPORT' ? departureDate : dayBefore(departureDate))
  }
  const send = async () => {
    setConfirming(false)
    await submit({ departureId: departureId!, requestedPickupDate: pickupDate })
  }

  // Đơn chạy thẳng đổi ngày đón ở thẻ đón hụt, không chọn chuyến
  if (suggestion?.routing === 'DIRECT') return null

  return (
    <Card title={t('myOrders.reschedule.title')}>
      <div className={s.stack}>
        {merge.error && <ErrorState message={merge.error.message} onRetry={merge.reload} />}
        {offered && !merge.error && <div className={s.stack}>
          <strong>{t('myOrders.reschedule.mergeSuggestion', { date: showDate(offered.departureDate) })}</strong>
          <p>{t('myOrders.reschedule.mergePickup', { date: showDate(offered.requestedPickupDate) })}</p>
          <div><Button variant="primary" loading={busy} onClick={() => void accept()}>{t('myOrders.reschedule.acceptMerge')}</Button></div>
        </div>}
        {loadError ? <ErrorState message={loadError.message} onRetry={reload} /> : !loaded ? <Skeleton lines={3} /> : !departures.length
          ? !offered && <EmptyState icon={<i className="fa-solid fa-truck" />} title={t('bookingForm.route.noDeparture')} description={t('myOrders.reschedule.none')} />
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
        <div><Button variant={offered ? 'secondary' : 'primary'} loading={busy} disabled={!selected || badDate}
          onClick={() => setConfirming(true)}>{t('myOrders.reschedule.send')}</Button></div>
      </div>
      {confirming && selected && <ConfirmDialog title={t('myOrders.reschedule.confirmTitle')}
        message={t('myOrders.reschedule.confirmMessage', { date: showDate(selected.departureDate ?? '') })}
        confirmLabel={t('myOrders.reschedule.send')} onConfirm={send} onCancel={() => setConfirming(false)} />}
    </Card>
  )
}
