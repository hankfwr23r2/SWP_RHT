// Bước 2: chọn ngựa, loại khoang và chỉ dẫn ăn uống của từng con.
import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { EmptyState } from '@shared/ui/EmptyState'
import { AddressSelect } from './components/AddressSelect'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { BookingShell, StepActions } from './BookingShell'
import { HorseSetup } from './components/HorseSetup'
import { emptyHorse, useBookingDraft } from './draft'
import { useBookingData, findAddress } from './loaders'
import { horsesReady, routeReady, bookingTypeOf, selectedDeliveryAddress } from './model'
import s from './Booking.module.css'

const SEARCH_FROM = 6

export default function Step2HorsesPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const data = useBookingData()
  const { draft, save, setHorse } = useBookingDraft()
  const [query, setQuery] = useState('')
  const [submitted, setSubmitted] = useState(false)

  if (!data.failed && !routeReady(draft)) return <Navigate to="/booking/route" replace />

  const horses = data.horses.filter(horse => horse.id != null && horse.status === 'ACTIVE')
  const term = query.trim().toLowerCase()
  const shown = horses.filter(horse => draft.horseIds.includes(horse.id!)
    || [horse.name, horse.microchip, horse.breed].some(value => value?.toLowerCase().includes(term)))
  const seatsOf = (code: string) => data.stallTypes.find(stall => stall.code === code)?.seats ?? 1
  const seats = draft.horseIds.reduce((sum, id) => sum + seatsOf(draft.horses[id]?.stallTypeCode ?? ''), 0)
  const available = draft.departure?.availableStalls
  const full = available != null && seats > available
  // Ngựa đã chọn trong bản nháp nhưng vừa vào đơn khác
  const busy = horses.filter(horse => horse.activeBookingCode && draft.horseIds.includes(horse.id!))

  const toggle = (id: number) => {
    if (draft.horseIds.includes(id)) {
      save({ horseIds: draft.horseIds.filter(item => item !== id) })
      return
    }
    // Khoang nhỏ nhất được chọn sẵn để khách thấy giá ngay, khách đổi được
    const smallest = [...data.stallTypes].sort((a, b) => (a.seats ?? 1) - (b.seats ?? 1))[0]?.code ?? ''
    save({
      horseIds: [...draft.horseIds, id],
      horses: { ...draft.horses, [id]: draft.horses[id] ?? { ...emptyHorse(), stallTypeCode: smallest } },
    })
  }

  const copyToAll = (id: number) => {
    const { stallTypeCode, dietNote, feedingsPerDay, feedSuppliedByOwner } = draft.horses[id]
    save({
      horses: Object.fromEntries(draft.horseIds.map(item => [item, {
        ...draft.horses[item], stallTypeCode, dietNote, feedingsPerDay, feedSuppliedByOwner,
      }])),
    })
  }

  const canChooseDelivery = draft.routing === 'LINEHAUL' && draft.deliveryMethod === 'COMPANY'
    && bookingTypeOf(findAddress(data, draft.pickupAddressId), findAddress(data, draft.deliveryAddressId)) === 'INTERPROVINCE'
  const contactsReady = draft.horseIds.every(id => {
    const addressId = selectedDeliveryAddress(draft, id)
    if (!addressId || addressId === draft.deliveryAddressId) return true
    const contact = draft.deliveryContacts[addressId]
    return Boolean(findAddress(data, addressId) && contact?.fullName.trim() && contact.phone.trim())
  })

  const next = () => {
    setSubmitted(true)
    if (horsesReady(draft) && !full && !busy.length && contactsReady) navigate('/booking/services')
  }

  return (
    <BookingShell step={2} title={t('bookingForm.horses.title')} subtitle={t('bookingForm.horses.subtitle')}>
      <div className={s.between}>
        <p className={s.hint}>
          {t('bookingForm.horses.selected', { count: draft.horseIds.length })}
          {available != null && ` · ${t('bookingForm.horses.seats', { seats, available })}`}
        </p>
        <Link to="/horses" className={s.link}>{t('bookingForm.horses.manage')}</Link>
      </div>
      {horses.length >= SEARCH_FROM && (
        <Input type="search" value={query} onChange={event => setQuery(event.target.value)}
          placeholder={t('bookingForm.horses.search')} aria-label={t('bookingForm.horses.search')} />
      )}
      {!horses.length && <EmptyState icon={<i className="fa-solid fa-horse" />} title={t('bookingForm.horses.empty')}
        description={t('bookingForm.horses.emptyHint')} action={<Link to="/horses" className={s.link}>{t('bookingForm.horses.manage')}</Link>} />}
      {shown.map(horse => {
        const id = horse.id!
        const on = draft.horseIds.includes(id)
        const addressId = selectedDeliveryAddress(draft, id) ?? draft.deliveryAddressId
        const otherDestination = addressId != null && addressId !== draft.deliveryAddressId
        const contact = addressId == null ? undefined : draft.deliveryContacts[addressId]
        return (
          <article key={id} className={`${s.horse} ${on ? s.horseOn : ''}`}>
            <label className={s.horsePick}>
              <input type="checkbox" checked={on} disabled={!on && !!horse.activeBookingCode} onChange={() => toggle(id)} />
              <div>
                <strong>{horse.name}</strong>
                <span>{[...new Set([horse.microchip, horse.breed, horse.horseTypeName])].filter(Boolean).join(' · ')}</span>
                {horse.activeBookingCode && <em className={s.busy}>{t('bookingForm.horses.busy', { code: horse.activeBookingCode })}</em>}
              </div>
            </label>
            {on && draft.horses[id] && (
              <HorseSetup horseId={id} choice={draft.horses[id]} onChange={patch => setHorse(id, patch)}
                onCopy={draft.horseIds.length > 1 ? () => copyToAll(id) : undefined} />
            )}
            {on && canChooseDelivery && (
              <>
                <AddressSelect label={t('bookingForm.horses.deliveryPlace')} value={addressId} exclude={draft.pickupAddressId}
                  onChange={deliveryAddressId => setHorse(id, { deliveryAddressId })} />
                {otherDestination && addressId != null && (['fullName', 'phone'] as const).map(field => (
                  <Field key={field} label={t(`bookingForm.horses.deliveryContact.${field}`)} required
                    error={submitted && !contact?.[field].trim() ? t('bookingForm.horses.deliveryContact.required') : undefined}>
                    {control => <Input {...control} type={field === 'phone' ? 'tel' : 'text'} value={contact?.[field] ?? ''}
                      onChange={event => save({ deliveryContacts: { ...draft.deliveryContacts, [addressId]: {
                        fullName: contact?.fullName ?? '', phone: contact?.phone ?? '', [field]: event.target.value,
                      } } })} />}
                  </Field>
                ))}
              </>
            )}
          </article>
        )
      })}
      {busy.length > 0 && <p className={`${s.alert} ${s.danger}`} role="alert">{t('bookingForm.horses.removeBusy', { names: busy.map(horse => horse.name).join(', ') })}</p>}
      {full && <p className={`${s.alert} ${s.danger}`} role="alert">{t('bookingForm.horses.full', { seats, available })}</p>}
      {submitted && !horsesReady(draft) && <p className={s.error} role="alert">{t('bookingForm.horses.required')}</p>}
      <StepActions back="/booking/route" backLabel={t('bookingForm.back')}>
        <Button variant="primary" size="lg" onClick={next}>{t('bookingForm.horses.next')}</Button>
      </StepActions>
    </BookingShell>
  )
}
