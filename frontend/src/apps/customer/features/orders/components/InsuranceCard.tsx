import { DateInput } from '@shared/ui/DateInput'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { BookingDetail } from '@shared/api/bookingOrders'
import { documentUploadError } from '@shared/lib/documentUpload'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { Field } from '@shared/ui/Field'
import { FilePreviewButton } from '@shared/ui/FilePreviewButton'
import { Input } from '@shared/ui/Input'
import type { OrderData } from '../loaders'
import { useInsuranceRevision } from '../useInsuranceRevision'
import s from '../Orders.module.css'

type HorseDetail = NonNullable<BookingDetail['horses']>[number]

// Bảo hiểm khách tự mua bị yêu cầu bổ sung: sửa từng con ngay trong trang
export function InsuranceCard({ data }: { data: OrderData }) {
  const { t } = useTranslation()
  const horses = (data.detail?.horses ?? []).filter(horse => horse.insurance?.status === 'REVISION_REQUESTED')
  if (!horses.length) return null
  return (
    <Card title={t('myOrders.insurance.title')}>
      <div className={s.stack}>{horses.map(horse => <RevisionForm key={horse.id} bookingId={data.id} horse={horse} />)}</div>
    </Card>
  )
}

function RevisionForm({ bookingId, horse }: { bookingId: number; horse: HorseDetail }) {
  const { t } = useTranslation()
  const insurance = horse.insurance!
  const { save, busy, error, fields, clearErrors } = useInsuranceRevision(bookingId, insurance.id!)
  const [form, setForm] = useState({
    insurerName: insurance.insurerName ?? '',
    policyNumber: insurance.policyNumber ?? '', validFrom: insurance.validFrom ?? '', validTo: insurance.validTo ?? '',
  })
  const [submitted, setSubmitted] = useState(false)
  const [file, setFile] = useState<File>()
  const [confirming, setConfirming] = useState(false)
  const set = (patch: Partial<typeof form>) => {
    setForm(current => ({ ...current, ...patch }))
    clearErrors()
  }
  const missing = (value: string) => submitted && !value.trim() ? t('bookingForm.required') : undefined
  const badRange = !!form.validFrom && !!form.validTo && form.validFrom > form.validTo
  const invalidFile = file && documentUploadError(file)

  const review = () => {
    setSubmitted(true)
    if (Object.values(form).some(value => !value.trim()) || badRange || !file || invalidFile) return
    setConfirming(true)
  }

  const send = async () => {
    if (!file) return
    setConfirming(false)
    await save({
      insurerName: form.insurerName.trim(), policyNumber: form.policyNumber.trim(), validFrom: form.validFrom, validTo: form.validTo,
    }, file)
  }

  return (
    <div className={s.stack}>
      <p className={s.error}><strong>{horse.horseName}:</strong> {insurance.revisionReason}</p>
      <div className={s.options}>
        <Field label={t('bookingForm.services.insurer')} required
          error={fields.insurerName ?? missing(form.insurerName)}>
          {control => <Input {...control} maxLength={200} value={form.insurerName}
            placeholder={t('bookingForm.services.insurerPlaceholder')}
            onChange={event => set({ insurerName: event.target.value })} />}
        </Field>
        <Field label={t('bookingForm.services.policyNumber')} required error={fields.policyNumber ?? missing(form.policyNumber)}>
          {control => <Input {...control} maxLength={50} value={form.policyNumber} onChange={event => set({ policyNumber: event.target.value })} />}
        </Field>
        <Field label={t('bookingForm.services.validFrom')} required error={fields.validFrom ?? missing(form.validFrom)}>
          {control => <DateInput {...control} value={form.validFrom} onValueChange={value => set({ validFrom: value })} />}
        </Field>
        <Field label={t('bookingForm.services.validTo')} required
          error={fields.validTo ?? (badRange ? t('bookingForm.services.badRange') : missing(form.validTo))}>
          {control => <DateInput {...control} value={form.validTo} onValueChange={value => set({ validTo: value })} />}
        </Field>
        <Field label={t('bookingForm.services.policyFile')} required
          hint={file?.name ?? t('bookingForm.services.policyFileHint')}
          error={fields.file ?? (invalidFile ? t('bookingForm.services.policyFileInvalid') : missing(file?.name ?? ''))}>
          {control => <>
            <Input {...control} type="file" accept=".pdf,.jpg,.jpeg,.png" onChange={event => {
              setFile(event.target.files?.[0])
              clearErrors()
              event.target.value = ''
            }} />
            {file && <FilePreviewButton fileName={file.name} file={file} label={t('ui.previewFile')} />}
          </>}
        </Field>
      </div>
      {error && !Object.keys(fields).length && <p className={s.error} role="alert">{error}</p>}
      <div><Button variant="primary" loading={busy} onClick={review}>{t('myOrders.insurance.save')}</Button></div>
      {confirming && <ConfirmDialog title={t('myOrders.insurance.confirmTitle')} message={t('myOrders.insurance.confirmMessage')}
        confirmLabel={t('myOrders.insurance.save')} onConfirm={send} onCancel={() => setConfirming(false)} />}
    </div>
  )
}
