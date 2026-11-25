// Bước 1: nơi đón, nơi giao, cách đưa và nhận ngựa, chuyến trong tuần, giấy phải nộp.
import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import { bookingOrdersApi } from '@shared/api/bookingOrders'
import { useLoad } from '@shared/hooks/useLoad'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { BookingShell, StepActions } from './BookingShell'
import { AddressSelect } from './components/AddressSelect'
import { DeparturePicker } from './components/DeparturePicker'
import { MethodChoice } from './components/MethodChoice'
import { RegulationNotice } from './components/RegulationNotice'
import { useBookingDraft, type Departure } from './draft'
import { findAddress, useBookingData } from './loaders'
import { addDays, bookingTypeOf, isHome, latestPickup, routeReady, todayIso } from './model'
import { useTransferOptions } from './useTransferOptions'
import s from './Booking.module.css'

export default function Step1RoutePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const data = useBookingData()
  const { draft, save } = useBookingDraft()
  const [today] = useState(() => todayIso())
  const [submitted, setSubmitted] = useState(false)

  const pickup = findAddress(data, draft.pickupAddressId)
  const delivery = findAddress(data, draft.deliveryAddressId)
  const type = bookingTypeOf(pickup, delivery)
  const fromDate = draft.fromDate || addDays(today, 1)
  const selected = draft.departure

  const suggestions = useLoad(async () => {
    if (!type || !pickup || !delivery) return undefined
    const result = await bookingOrdersApi.suggestions({
      bookingType: type,
      pickupProvinceCode: pickup.provinceCode!,
      deliveryProvinceCode: delivery.provinceCode!,
      pickupLatitude: pickup.latitude, pickupLongitude: pickup.longitude,
      deliveryLatitude: delivery.latitude, deliveryLongitude: delivery.longitude,
      fromDate, stalls: 1,
    })
    const direct = result.routing === 'DIRECT'
    // Chuyến đã chọn nhưng hết chỗ hoặc qua hạn chốt thì bỏ chọn; còn thì cập nhật chỗ trống
    const current = selected && result.departures?.find(departure => departure.id === selected.id)
    save({
      routing: result.routing ?? '',
      departure: current ?? null,
      ...(selected && !current && { pickupDate: '' }),
      ...(direct && { pickupMethod: 'COMPANY', deliveryMethod: 'COMPANY' }),
    })
    return result
  }, [type, pickup?.id, delivery?.id, fromDate], { toastOnError: false })

  const needRules = type != null && type !== 'LOCAL' && routeReady(draft)
  const regulations = useLoad(async () => needRules
    ? bookingOrdersApi.previewRegulations({
      bookingType: type!, pickupCountryCode: pickup!.countryCode!, deliveryCountryCode: delivery!.countryCode!,
      requestedPickupDate: draft.pickupDate, departureId: selected?.id,
    })
    : undefined, [needRules, type, selected?.id, draft.pickupDate], { toastOnError: false })

  const blocked = useTransferOptions(draft, save)

  const changeAddress = (side: 'pickupAddressId' | 'deliveryAddressId', id: number) =>
    save({ [side]: id, routing: '', departure: null, pickupDate: '', pickupMethod: 'COMPANY', deliveryMethod: 'COMPANY' })

  const swap = () => save({
    pickupAddressId: draft.deliveryAddressId, deliveryAddressId: draft.pickupAddressId,
    routing: '', departure: null, pickupDate: '', pickupMethod: 'COMPANY', deliveryMethod: 'COMPANY',
  })

  const chooseDeparture = (departure: Departure) => {
    const latest = latestPickup(type, departure.departureDate)
    save({ departure, pickupDate: latest && latest >= today ? latest : '' })
  }

  const lastDay = latestPickup(type, selected?.departureDate)
  const direct = draft.routing === 'DIRECT'
  const linehaul = draft.routing === 'LINEHAUL'
  const selfPickup = draft.pickupMethod === 'SELF'
  const bothAbroad = pickup && delivery && !type
  const infeasible = needRules && regulations.data?.feasible === false
  const badDate = !!draft.pickupDate && (draft.pickupDate < today || (!!lastDay && draft.pickupDate > lastDay))
  const dateError = direct && infeasible
    ? t('bookingForm.route.directNotFeasible', { days: regulations.data?.leadDays ?? 0 })
    : badDate ? t('bookingForm.route.badDate') : submitted && !draft.pickupDate ? t('bookingForm.required') : undefined

  const next = (event: FormEvent) => {
    event.preventDefault()
    setSubmitted(true)
    if (!type || !routeReady(draft) || badDate || infeasible) {
      requestAnimationFrame(() => document.querySelector('[role="alert"]')?.scrollIntoView({ block: 'center', behavior: 'smooth' }))
      return
    }
    navigate('/booking/horses')
  }

  return (
    <BookingShell step={1} title={t('bookingForm.route.title')} subtitle={t('bookingForm.route.subtitle')}>
      <form className={s.stack} onSubmit={next} noValidate>
        <Card title={t('bookingForm.route.places')} actions={
          <Button size="sm" variant="ghost" disabled={!pickup && !delivery} onClick={swap}
            icon={<i className="fa-solid fa-right-left" />}>{t('bookingForm.route.swap')}</Button>
        }>
          <div className={s.stack}>
            <div className={s.grid}>
              <AddressSelect label={t('bookingForm.route.pickup')} value={draft.pickupAddressId} exclude={draft.deliveryAddressId}
                error={submitted && !pickup ? t('bookingForm.required') : undefined} onChange={id => changeAddress('pickupAddressId', id)} />
              <AddressSelect label={t('bookingForm.route.delivery')} value={draft.deliveryAddressId} exclude={draft.pickupAddressId}
                error={submitted && !delivery ? t('bookingForm.required') : undefined} onChange={id => changeAddress('deliveryAddressId', id)} />
            </div>
            {bothAbroad && <p className={`${s.alert} ${s.danger}`} role="alert">{t('bookingForm.route.bothAbroad')}</p>}
            {type && <p className={s.hint}><strong>{t(`bookingForm.types.${type}`)}</strong> — {t(`bookingForm.typeHints.${type}`)}</p>}
          </div>
        </Card>

        {type && !direct && (
          <DeparturePicker suggestion={suggestions.data} loaded={suggestions.loaded} error={suggestions.error} onRetry={suggestions.reload}
            fromDate={fromDate} minDate={today} onFromDate={date => save({ fromDate: date, departure: null, pickupDate: '' })}
            selectedId={selected?.id} onSelect={chooseDeparture}
            invalid={infeasible ? t('bookingForm.route.notFeasible', { days: regulations.data?.leadDays ?? 0 })
              : submitted && !selected ? t('bookingForm.route.departureRequired') : undefined} />
        )}

        {(direct || (linehaul && selected)) && (
          <Card title={t('bookingForm.route.transfer')}>
            <div className={s.stack}>
              {linehaul && isHome(pickup) && (
                <MethodChoice name="pickupMethod" legend={t('bookingForm.route.pickupMethod')} side="pickup"
                  value={draft.pickupMethod} hub={suggestions.data?.pickupHubName}
                  blocked={blocked.pickup} onChange={pickupMethod => save({ pickupMethod })} />
              )}
              <Field label={t(selfPickup ? 'bookingForm.route.hubDate' : 'bookingForm.route.pickupDate')} required error={dateError}
                hint={linehaul ? t(type === 'IMPORT' ? 'bookingForm.route.importDateHint' : 'bookingForm.route.linehaulDateHint') : t('bookingForm.route.directDateHint')}>
                {control => <Input {...control} type="date" value={draft.pickupDate} min={today} max={lastDay}
                  onChange={event => save({ pickupDate: event.target.value })} />}
              </Field>
              {linehaul && isHome(delivery) && (
                <MethodChoice name="deliveryMethod" legend={t('bookingForm.route.deliveryMethod')} side="delivery"
                  value={draft.deliveryMethod} hub={suggestions.data?.deliveryHubName}
                  blocked={blocked.delivery} onChange={deliveryMethod => save({ deliveryMethod })} />
              )}
            </div>
          </Card>
        )}

        {needRules && <RegulationNotice preview={regulations.data} loaded={regulations.loaded} error={regulations.error} onRetry={regulations.reload} />}

        <StepActions back="/portal" backLabel={t('bookingForm.cancel')}>
          <Button type="submit" variant="primary" size="lg">{t('bookingForm.route.next')}</Button>
        </StepActions>
      </form>
    </BookingShell>
  )
}
