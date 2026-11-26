import { DateInput } from '@shared/ui/DateInput'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { bookingDocumentsApi, type ChecklistItem } from '@shared/api/bookingDocuments'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { FileField } from '@shared/ui/FileField'
import { Modal } from '@shared/ui/Modal'
import { useOrderAction } from '../useOrderAction'
import s from '../Orders.module.css'

interface Props {
  bookingId: number
  item: ChecklistItem
  horseName?: string
  onClose: () => void
}

export function UploadDialog({ bookingId, item, horseName, onClose }: Props) {
  const { t } = useTranslation()
  const { run, busy, error } = useOrderAction()
  const [file, setFile] = useState<File>()
  const [expiry, setExpiry] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const name = item.requirement?.documentTypeName ?? ''

  const send = async () => {
    setSubmitted(true)
    if (file && await run(() => bookingDocumentsApi.upload(bookingId, item, file, expiry), t('myOrders.documents.uploaded'))) onClose()
  }

  return (
    <Modal title={name} subtitle={horseName} onClose={() => { if (!busy) onClose() }} footer={
      <>
        <Button onClick={onClose} disabled={busy}>{t('myOrders.close')}</Button>
        <Button variant="primary" loading={busy} onClick={send}>{t('myOrders.documents.upload')}</Button>
      </>
    }>
      <div className={s.stack}>
        <FileField label={t('myOrders.documents.file')} required fileName={file?.name} onChange={(_, next) => setFile(next)}
          error={submitted && !file ? t('bookingForm.required') : undefined} />
        <Field label={t('myOrders.documents.expiryLabel')} hint={t('myOrders.documents.expiryHint')}>
          {control => <DateInput {...control} value={expiry} onValueChange={value => setExpiry(value)} />}
        </Field>
        {error && <p className={s.error} role="alert">{error}</p>}
      </div>
    </Modal>
  )
}
