// Bước 2: chọn ngựa, loại khoang và chỉ dẫn ăn uống của từng con.
import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { EmptyState } from '@shared/ui/EmptyState'
import { Input } from '@shared/ui/Input'
import { BookingShell, StepActions } from './BookingShell'
import { HorseSetup } from './components/HorseSetup'
import { emptyHorse, useBookingDraft } from './draft'
import { useBookingData } from './loaders'
import { horsesReady, routeReady } from './model'
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

  const next = () => {
    setSubmitted(true)
    if (horsesReady(draft) && !full) navigate('/booking/services')
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
        return (
          <article key={id} className={`${s.horse} ${on ? s.horseOn : ''}`}>
            <label className={s.horsePick}>
              <input type="checkbox" checked={on} onChange={() => toggle(id)} />
              <div>
                <strong>{horse.name}</strong>
                <span>{[...new Set([horse.microchip, horse.breed, horse.horseTypeName])].filter(Boolean).join(' · ')}</span>
              </div>
            </label>
            {on && draft.horses[id] && (
              <HorseSetup horseId={id} choice={draft.horses[id]} onChange={patch => setHorse(id, patch)}
                onCopy={draft.horseIds.length > 1 ? () => copyToAll(id) : undefined} />
            )}
          </article>
        )
      })}
      {full && <p className={`${s.alert} ${s.danger}`} role="alert">{t('bookingForm.horses.full', { seats, available })}</p>}
      {submitted && !horsesReady(draft) && <p className={s.error} role="alert">{t('bookingForm.horses.required')}</p>}
      <StepActions back="/booking/route" backLabel={t('bookingForm.back')}>
        <Button variant="primary" size="lg" onClick={next}>{t('bookingForm.horses.next')}</Button>
      </StepActions>
    </BookingShell>
  )
}
