// Bước 4: khai các bên, xem lại, đồng ý điều khoản và gửi đơn.
import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useToast } from '@shared/ui/toast'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { PolicyLink } from '@shared/ui/PolicyLink'
import { Textarea } from '@shared/ui/Textarea'
import { BookingShell, StepActions } from './BookingShell'
import { NeedToKnow } from './components/NeedToKnow'
import { PartyFields } from './components/PartyFields'
import { ReviewList } from './components/ReviewList'
import { PARTY_ROLES, useBookingDraft, type PartyDraft, type PartyRole } from './draft'
import { addressText, findAddress, loadProfile, useBookingData } from './loaders'
import { horsesReady, isHome, routeReady, servicesReady } from './model'
import { useSubmitBooking } from './useSubmitBooking'
import s from './Booking.module.css'

const PARTY_FIELDS = ['fullName', 'phone', 'address'] as const

export default function Step4ReviewPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const toast = useToast()
  const data = useBookingData()
  const { draft, save, setParty } = useBookingDraft()
  const { submit, busy, order, error, fields } = useSubmitBooking()
  const [submitted, setSubmitted] = useState(false)
  const [confirming, setConfirming] = useState(false)

  const pickup = findAddress(data, draft.pickupAddressId)
  const delivery = findAddress(data, draft.deliveryAddressId)

  if (order) {
    return (
      <div className="page"><div className="wrap">
        <div className={s.done} role="status">
          <i className="fa-solid fa-circle-check" aria-hidden="true" />
          <h1>{t('bookingForm.done.title', { code: order.code })}</h1>
          <p className={s.hint}>{t('bookingForm.done.hint')}</p>
          <Button variant="primary" onClick={() => navigate('/orders')}>{t('bookingForm.done.orders')}</Button>
        </div>
      </div></div>
    )
  }
  if (!data.failed && (!routeReady(draft) || !pickup || !delivery)) return <Navigate to="/booking/route" replace />
  if (!data.failed && !horsesReady(draft)) return <Navigate to="/booking/horses" replace />
  if (!data.failed && !servicesReady(draft)) return <Navigate to="/booking/services" replace />

  const fillMine = async (role: PartyRole) => {
    try {
      const profile = await loadProfile()
      const patch: Partial<PartyDraft> = {}
      if (profile.fullName) patch.fullName = profile.fullName
      if (profile.phone) patch.phone = profile.phone
      // Người gửi và người nhận ở nơi đón, nơi giao; hai bên còn lại lấy địa chỉ trong hồ sơ
      const place = role === 'SHIPPER' ? pickup : role === 'CONSIGNEE' ? delivery : undefined
      if (place) patch.address = addressText(data, place)
      else if (profile.address) patch.address = profile.address
      setParty(role, patch)
    } catch {
      toast(t('bookingForm.parties.profileError'), 'error')
    }
  }

  const serverError = (path: string) => fields[path]
  const partyErrors = (role: PartyRole) => {
    const index = PARTY_ROLES.indexOf(role)
    const errors: Partial<Record<keyof PartyDraft, string>> = {}
    for (const field of PARTY_FIELDS) {
      const message = serverError(`parties[${index}].${field}`)
        ?? (submitted && !draft.parties[role][field].trim() ? t('bookingForm.required') : undefined)
      if (message) errors[field] = message
    }
    return errors
  }

  const complete = PARTY_ROLES.every(role => PARTY_FIELDS.every(field => draft.parties[role][field].trim()))
  const review = () => {
    setSubmitted(true)
    if (complete && draft.acceptedTerms) {
      setConfirming(true)
      return
    }
    requestAnimationFrame(() => document.querySelector('[role="alert"]')?.scrollIntoView({ block: 'center', behavior: 'smooth' }))
  }

  const send = async () => {
    setConfirming(false)
    if (pickup && delivery) await submit(draft, pickup, delivery)
  }

  return (
    <BookingShell step={4} title={t('bookingForm.review.title')} subtitle={t('bookingForm.review.subtitle')}>
      <ReviewList draft={draft} />

      {PARTY_ROLES.map(role => (
        <PartyFields key={role} role={role} party={draft.parties[role]} errors={partyErrors(role)}
          onChange={patch => setParty(role, patch)} onUseMine={() => void fillMine(role)}
          onCopyShipper={role === 'PAYER' || role === 'EMERGENCY_CONTACT'
            ? () => setParty(role, { ...draft.parties.SHIPPER, identityType: '', identityNumber: '' }) : undefined} />
      ))}

      <Card title={t('bookingForm.review.more')}>
        <div className={s.grid}>
          <Field label={t('bookingForm.review.pickupNote')} hint={t('bookingForm.review.noteHint')}>
            {control => <Textarea {...control} rows={2} maxLength={2000} value={draft.pickupNote} onChange={event => save({ pickupNote: event.target.value })} />}
          </Field>
          <Field label={t('bookingForm.review.deliveryNote')} hint={t('bookingForm.review.noteHint')}>
            {control => <Textarea {...control} rows={2} maxLength={2000} value={draft.deliveryNote} onChange={event => save({ deliveryNote: event.target.value })} />}
          </Field>
          <Field label={t('bookingForm.review.emergencyLimit')} hint={t('bookingForm.review.emergencyHint')}
            error={serverError('emergencySpendingLimit')} className={s.wide}>
            {control => <Input {...control} inputMode="numeric" maxLength={13} value={draft.emergencySpendingLimit}
              onChange={event => save({ emergencySpendingLimit: event.target.value.replace(/\D/g, '') })} />}
          </Field>
        </div>
      </Card>

      <NeedToKnow draft={draft} pickupHome={isHome(pickup)} deliveryHome={isHome(delivery)} />

      <div>
        <label className={s.check}>
          <input type="checkbox" checked={draft.acceptedTerms} onChange={event => save({ acceptedTerms: event.target.checked })} />
          <span>{t('bookingForm.review.terms')} <PolicyLink doc="transport" />, <PolicyLink doc="incident_cost" />.</span>
        </label>
        {submitted && !draft.acceptedTerms && <p className={s.error} role="alert">{t('bookingForm.review.termsRequired')}</p>}
      </div>

      {error && (
        <div className={`${s.alert} ${s.danger}`} role="alert">
          <strong>{t('bookingForm.review.failed')}</strong>
          <span>{error}</span>
          <span className={s.row}>
            <Link to="/booking/route" className={s.link}>{t('bookingForm.review.checkRoute')}</Link>
            <Link to="/booking/horses" className={s.link}>{t('bookingForm.review.checkHorses')}</Link>
          </span>
        </div>
      )}

      <StepActions back="/booking/services" backLabel={t('bookingForm.back')}>
        <Button variant="primary" size="lg" loading={busy} onClick={review}>{t('bookingForm.review.send')}</Button>
      </StepActions>

      {confirming && <ConfirmDialog title={t('bookingForm.review.confirmTitle')} message={t('bookingForm.review.confirmMessage')}
        confirmLabel={t('bookingForm.review.send')} onConfirm={send} onCancel={() => setConfirming(false)} />}
    </BookingShell>
  )
}
