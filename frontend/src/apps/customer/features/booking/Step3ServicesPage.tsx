// Bước 3: dịch vụ thêm và bảo hiểm của từng con.
import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { Input } from '@shared/ui/Input'
import { BookingShell, StepActions } from './BookingShell'
import { InsuranceChoice } from './components/InsuranceChoice'
import { useBookingDraft } from './draft'
import { useBookingData } from './loaders'
import { horsesReady, insuranceReady, routeReady, servicesReady } from './model'
import s from './Booking.module.css'

export default function Step3ServicesPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { state } = useLocation()
  const data = useBookingData()
  const { draft, save, setHorse } = useBookingDraft()
  const [submitted, setSubmitted] = useState(false)
  const [fileErrors, setFileErrors] = useState<Record<string, string>>(() => state?.insuranceFileErrors ?? {})

  if (!data.failed && !routeReady(draft)) return <Navigate to="/booking/route" replace />
  if (!data.failed && !horsesReady(draft)) return <Navigate to="/booking/horses" replace />

  const addOns = data.services.filter(service => service.layer === 'ADD_ON' && service.status === 'ACTIVE' && !service.autoApplied)

  const bookingAddOns = addOns.filter(service => service.billingUnit === 'PER_BOOKING')
  const horseAddOns = addOns.filter(service => service.billingUnit !== 'PER_BOOKING')
  const bookingIds = bookingAddOns.map(service => service.id!)
  // Bản nháp cũ có thể đã chọn dịch vụ theo đơn ở thẻ ngựa.
  const selectedBookingAddOns = [...new Set([
    ...draft.bookingAddOnServiceIds,
    ...draft.horseIds.flatMap(id => draft.horses[id].addOnServiceIds.filter(serviceId => bookingIds.includes(serviceId))),
  ])]
  const toggleBooking = (serviceId: number) => save({
    bookingAddOnServiceIds: selectedBookingAddOns.includes(serviceId)
      ? selectedBookingAddOns.filter(id => id !== serviceId) : [...selectedBookingAddOns, serviceId],
    horses: Object.fromEntries(draft.horseIds.map(id => [id, {
      ...draft.horses[id], addOnServiceIds: draft.horses[id].addOnServiceIds.filter(value => !bookingIds.includes(value)),
    }])),
  })

  const toggle = (horseId: number, serviceId: number) => {
    const chosen = draft.horses[horseId].addOnServiceIds
    setHorse(horseId, {
      addOnServiceIds: chosen.includes(serviceId) ? chosen.filter(id => id !== serviceId) : [...chosen, serviceId],
    })
  }

  // Số, tệp hợp đồng và miễn trách là của riêng từng con.
  const copyToAll = (horseId: number) => {
    const { addOnServiceIds, coverage, insurerName } = draft.horses[horseId]
    save({
      horses: Object.fromEntries(draft.horseIds.map(id => [id, {
        ...draft.horses[id],
        addOnServiceIds: [...addOnServiceIds.filter(value => !bookingIds.includes(value)),
          ...draft.horses[id].addOnServiceIds.filter(value => bookingIds.includes(value))],
        coverage, insurerName,
        policyFile: coverage === 'PERSONAL' ? draft.horses[id].policyFile : undefined,
      }])),
    })
  }

  const next = () => {
    setSubmitted(true)
    if (servicesReady(draft)) {
      save({
        bookingAddOnServiceIds: selectedBookingAddOns,
        horses: Object.fromEntries(draft.horseIds.map(id => [id, {
          ...draft.horses[id], addOnServiceIds: draft.horses[id].addOnServiceIds.filter(value => !bookingIds.includes(value)),
        }])),
      })
      navigate('/booking/review')
      return
    }
    requestAnimationFrame(() => document.querySelector('[role="alert"]')?.scrollIntoView({ block: 'center', behavior: 'smooth' }))
  }

  return (
    <BookingShell step={3} title={t('bookingForm.services.title')} subtitle={t('bookingForm.services.subtitle')}>
      <Card title={t('bookingForm.services.bookingAddOns')}>
        {!bookingAddOns.length && <p className={s.hint}>{t('bookingForm.services.noAddOns')}</p>}
        <div className={s.options}>
          {bookingAddOns.map(service => (
            <label key={service.id} className={`${s.option} ${s.optionCheck}`}>
              <Input type="checkbox" checked={selectedBookingAddOns.includes(service.id!)}
                onChange={() => toggleBooking(service.id!)} />
              <strong>{service.name}</strong>
            </label>
          ))}
        </div>
      </Card>
      <section className={s.stack} aria-label={t('bookingForm.services.horseAddOns')}>
      <h2>{t('bookingForm.services.horseAddOns')}</h2>
      {draft.horseIds.map((id, index) => {
        const horse = data.horses.find(item => item.id === id)
        const choice = draft.horses[id]
        const fileField = `horses[${index}].insurance.file`
        return (
          <Card key={id} title={horse?.name ?? `#${id}`} actions={draft.horseIds.length > 1 && (
            <Button size="sm" variant="ghost" onClick={() => copyToAll(id)}>{t('bookingForm.copyToAll')}</Button>
          )}>
            <div className={s.stack}>
              <fieldset className={s.group}>
                <legend>{t('bookingForm.services.addOns')}</legend>
                {!horseAddOns.length && <p className={s.hint}>{t('bookingForm.services.noAddOns')}</p>}
                <div className={s.options}>
                  {horseAddOns.map(service => (
                    <label key={service.id} className={`${s.option} ${s.optionCheck}`}>
                      <Input type="checkbox" checked={choice.addOnServiceIds.includes(service.id!)} onChange={() => toggle(id, service.id!)} />
                      <strong>{service.name}</strong>
                    </label>
                  ))}
                </div>
              </fieldset>
              <InsuranceChoice horseId={id} choice={choice}
                missing={submitted && !insuranceReady(choice)} fileError={fileErrors[fileField]} onChange={patch => {
                  if (Object.hasOwn(patch, 'policyFile') && fileErrors[fileField]) {
                    setFileErrors(current => {
                      const remaining = { ...current }
                      delete remaining[fileField]
                      return remaining
                    })
                  }
                  setHorse(id, patch)
                }} />
            </div>
          </Card>
        )
      })}
      </section>
      <StepActions back="/booking/horses" backLabel={t('bookingForm.back')}>
        <Button variant="primary" size="lg" onClick={next}>{t('bookingForm.services.next')}</Button>
      </StepActions>
    </BookingShell>
  )
}
