import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
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
  horses: { id?: number; horseName?: string }[]
}

export function DeliveryComplaintCard({ bookingId, horses }: Props) {
  const { t } = useTranslation()
  const delivery = useDeliveryComplaint(bookingId)
  const [showForm, setShowForm] = useState(false)
  const [horseId, setHorseId] = useState('')
  const [confirmation, setConfirmation] = useState<'confirm' | FormData>()

  function prepareComplaint(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const body = new FormData(event.currentTarget)
    if (horseId) body.set('bookingHorseId', horseId)
    setConfirmation(body)
  }

  async function submit() {
    const saved = confirmation === 'confirm'
      ? await delivery.confirm()
      : confirmation && await delivery.submit(confirmation)
    if (saved) {
      setConfirmation(undefined)
      setShowForm(false)
    }
  }

  const data = delivery.data
  const complaint = data?.complaint
  return (
    <Card title={t('deliveryComplaint.title')}>
      <div className={s.stack}>
        {delivery.error && <ErrorState onRetry={delivery.reload} />}
        {!delivery.loaded && <Skeleton lines={3} />}
        {delivery.loaded && complaint && (
          <DeliveryComplaintDetails complaint={complaint} horseName={horses.find(horse => horse.id === complaint.bookingHorseId)?.horseName} />
        )}
        {delivery.loaded && data?.confirmedAt && <p>{t('deliveryComplaint.confirmed')}</p>}
        {delivery.loaded && data?.customerCanRespond && (
          <div className={s.actions}>
            <Button variant="primary" disabled={delivery.busy} onClick={() => setConfirmation('confirm')}>{t('deliveryComplaint.confirm')}</Button>
            <Button disabled={delivery.busy} onClick={() => setShowForm(current => !current)}>{t('deliveryComplaint.submit')}</Button>
          </div>
        )}
        {delivery.loaded && data?.customerCanRespond && showForm && (
          <form className={s.stack} onSubmit={prepareComplaint}>
            <Field label={t('deliveryComplaint.horse')} error={delivery.fieldErrors.bookingHorseId}>{field => (
              <FormSelect id={field.id} value={horseId} onChange={event => setHorseId(event.target.value)}>
                <option value="">{t('deliveryComplaint.wholeBooking')}</option>
                {horses.filter(horse => horse.id != null).map(horse => <option key={horse.id} value={horse.id}>{horse.horseName}</option>)}
              </FormSelect>
            )}</Field>
            <Field label={t('deliveryComplaint.description')} required error={delivery.fieldErrors.description}>{field => (
              <Textarea {...field} name="description" maxLength={4000} rows={3} />
            )}</Field>
            <Field label={t('deliveryComplaint.photos')} required error={delivery.fieldErrors.photos}>{field => (
              <Input {...field} name="photos" type="file" accept="image/*" multiple />
            )}</Field>
            <Button type="submit" variant="primary" loading={delivery.busy}>{t('deliveryComplaint.send')}</Button>
          </form>
        )}
        {delivery.loaded && data && !complaint && !data.confirmedAt && !data.customerCanRespond && (
          <EmptyState title={t('deliveryComplaint.none')} description={t('deliveryComplaint.windowClosed')} />
        )}
      </div>
      {confirmation && (
        <ConfirmDialog title={t('deliveryComplaint.title')}
          message={t(confirmation === 'confirm' ? 'deliveryComplaint.confirmEffect' : 'deliveryComplaint.sendEffect')}
          confirmLabel={t(confirmation === 'confirm' ? 'deliveryComplaint.confirm' : 'deliveryComplaint.send')}
          onConfirm={submit} onCancel={() => setConfirmation(undefined)} />
      )}
    </Card>
  )
}
