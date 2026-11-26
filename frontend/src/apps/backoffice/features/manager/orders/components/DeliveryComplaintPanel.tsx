import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { ComplaintDecision } from '@shared/api/deliveryComplaints'
import { DeliveryComplaintDetails } from '@shared/components/DeliveryComplaintDetails'
import s from '@shared/components/DeliveryComplaintDetails.module.css'
import { useDeliveryComplaint } from '@shared/hooks/useDeliveryComplaint'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { FormSelect } from '@shared/ui/FormSelect'
import { Input } from '@shared/ui/Input'
import { Skeleton } from '@shared/ui/Skeleton'
import { Textarea } from '@shared/ui/Textarea'

interface Props {
  bookingId: number
  horses: { id?: number; horseName?: string; status?: string }[]
  onChange: () => void
}

type Pending = { type: 'submit'; body: FormData } | { type: 'resolve'; reason: string; decision: ComplaintDecision }

export function DeliveryComplaintPanel({ bookingId, horses, onChange }: Props) {
  const { t } = useTranslation()
  const delivery = useDeliveryComplaint(bookingId, onChange)
  const [horseId, setHorseId] = useState('')
  const [decision, setDecision] = useState<ComplaintDecision>('ACCEPTED')
  const [pending, setPending] = useState<Pending>()
  const selectableHorses = horses.filter(horse => horse.id != null && horse.status !== 'EXCLUDED' && horse.status !== 'CANCELLED')

  function prepareSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const body = new FormData(event.currentTarget)
    if (horseId) body.set('bookingHorseId', horseId)
    const photos = body.getAll('photos').filter(photo => photo instanceof File && photo.size > 0)
    body.delete('photos')
    photos.forEach(photo => body.append('photos', photo))
    setPending({ type: 'submit', body })
  }

  function prepareResolve(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const reason = String(new FormData(event.currentTarget).get('reason'))
    setPending({ type: 'resolve', reason, decision })
  }

  async function confirm() {
    if (!pending) return
    const saved = pending.type === 'submit'
      ? await delivery.submitOnBehalf(pending.body)
      : await delivery.resolve(pending.decision, pending.reason)
    setPending(undefined)
    if (saved && pending.type === 'submit') {
      setHorseId('')
    }
  }

  const data = delivery.data
  const complaint = data?.complaint
  return (
    <Card title={t('deliveryComplaint.title')}>
      <div className={s.stack}>
        {delivery.error && <ErrorState onRetry={delivery.reload} />}
        {!delivery.loaded && <Skeleton lines={3} />}
        {delivery.loaded && data?.confirmedAt && <p>{t('deliveryComplaint.confirmed')}</p>}
        {delivery.loaded && complaint && <DeliveryComplaintDetails complaint={complaint}
          horseName={horses.find(horse => horse.id === complaint.bookingHorseId)?.horseName} />}
        {delivery.loaded && data?.managerCanSubmit && (
          <form className={s.stack} onSubmit={prepareSubmit}>
            <Field label={t('deliveryComplaint.horse')} error={delivery.fieldErrors.bookingHorseId}>{field => (
              <FormSelect id={field.id} value={horseId} onChange={event => setHorseId(event.target.value)}>
                <option value="">{t('deliveryComplaint.wholeBooking')}</option>
                {selectableHorses.map(horse => <option key={horse.id} value={horse.id}>{horse.horseName}</option>)}
              </FormSelect>
            )}</Field>
            <Field label={t('deliveryComplaint.description')} required error={delivery.fieldErrors.description}>{field => (
              <Textarea {...field} name="description" maxLength={4000} rows={3} />
            )}</Field>
            <Field label={t('deliveryComplaint.photos')} required error={delivery.fieldErrors.photos}>{field => (
              <Input {...field} name="photos" type="file" accept="image/*" multiple />
            )}</Field>
            <Button type="submit" variant="primary" loading={delivery.busy}>{t('managerDeliveryComplaint.submitOnBehalf')}</Button>
          </form>
        )}
        {delivery.loaded && complaint?.status === 'REPORTED' && (
          <>
            <form className={s.stack} onSubmit={event => {
              event.preventDefault()
              void delivery.reply(String(new FormData(event.currentTarget).get('reply')))
            }}>
              <Field label={t('deliveryComplaint.reply')} required error={delivery.fieldErrors.reply}>{field => (
                <Textarea {...field} name="reply" maxLength={4000} defaultValue={complaint.reply ?? ''} />
              )}</Field>
              <Button type="submit" loading={delivery.busy}>{t('managerDeliveryComplaint.saveReply')}</Button>
            </form>
            <form className={s.stack} onSubmit={prepareResolve}>
              <Field label={t('deliveryComplaint.decision')} error={delivery.fieldErrors.decision}>{field => (
                <FormSelect id={field.id} value={decision} onChange={event => setDecision(event.target.value === 'REJECTED' ? 'REJECTED' : 'ACCEPTED')}>
                  <option value="ACCEPTED">{t('deliveryComplaint.ACCEPTED')}</option>
                  <option value="REJECTED">{t('deliveryComplaint.REJECTED')}</option>
                </FormSelect>
              )}</Field>
              <Field label={t('deliveryComplaint.reason')} required error={delivery.fieldErrors.reason}>{field => (
                <Textarea {...field} name="reason" maxLength={4000} />
              )}</Field>
              <Button type="submit" variant="primary" loading={delivery.busy}>{t('managerDeliveryComplaint.resolve')}</Button>
            </form>
          </>
        )}
        {delivery.loaded && data && !complaint && !data.confirmedAt && !data.managerCanSubmit && (
          <EmptyState title={t('deliveryComplaint.none')} description={t('managerDeliveryComplaint.managerWindowClosed')} />
        )}
      </div>
      {pending && (
        <ConfirmDialog title={t('deliveryComplaint.title')}
          message={t(pending.type === 'submit' ? 'managerDeliveryComplaint.onBehalfEffect' : 'managerDeliveryComplaint.resolveEffect')}
          confirmLabel={t(pending.type === 'submit' ? 'managerDeliveryComplaint.submitOnBehalf' : 'managerDeliveryComplaint.resolve')}
          onConfirm={confirm} onCancel={() => setPending(undefined)} />
      )}
    </Card>
  )
}
