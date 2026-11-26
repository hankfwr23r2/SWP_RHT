import { DateInput } from '@shared/ui/DateInput'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { ChecklistItem } from '@shared/api/bookingDocuments'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { FileField } from '@shared/ui/FileField'
import { Modal } from '@shared/ui/Modal'
import type { ReviewActions } from '../useReviewActions'
import s from '../Review.module.css'

export function UploadPartnerDialog({ item, horseName, actions, onClose }: {
  item: ChecklistItem; horseName?: string; actions: ReviewActions; onClose: () => void
}) {
  const { t } = useTranslation()
  const [file, setFile] = useState<File>()
  const [expiry, setExpiry] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [fileError, setFileError] = useState<string>()

  const close = () => { if (!actions.busy) { actions.clear(); onClose() } }
  const send = async () => {
    setSubmitted(true)
    if (file && !fileError && await actions.upload(item, file, expiry)) onClose()
  }

  return <Modal title={t('staffReview.upload')} subtitle={horseName} onClose={close} footer={<>
    <Button onClick={close} disabled={actions.busy}>{t('ui.cancel')}</Button>
    <Button variant="primary" loading={actions.busy} onClick={send}>{t('staffReview.upload')}</Button>
  </>}>
    <div className={s.stack}>
      <p>{item.requirement?.documentTypeName}</p>
      <fieldset className={s.controls} disabled={actions.busy}>
        <FileField label={t('staffReview.file')} required fileName={file?.name}
          onChange={(_, next) => { setFile(next); actions.clear() }} onValidationError={setFileError}
          error={actions.fields.file || (submitted && !file ? t('staffReview.required') : undefined)} />
        <Field label={t('staffReview.expiryDate')} error={actions.fields.expiryDate}>
          {control => <DateInput {...control} value={expiry}
            onValueChange={value => { setExpiry(value); actions.clear() }} />}
        </Field>
      </fieldset>
      {actions.error && (!Object.keys(actions.fields).length || actions.fields.requirementId || actions.fields.bookingHorseId)
        && <p className={s.error} role="alert">{actions.fields.requirementId || actions.fields.bookingHorseId || actions.error}</p>}
    </div>
  </Modal>
}
