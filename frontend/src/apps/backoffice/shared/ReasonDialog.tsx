import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Modal } from '@shared/ui/Modal'
import { Textarea } from '@shared/ui/Textarea'
import s from './StaffPanels.module.css'

interface Props {
  title: string
  confirmLabel: string
  busy: boolean
  error: string
  disabled?: boolean
  children?: ReactNode
  onConfirm: (reason: string) => void
  onCancel: () => void
}

// Thao tác không hoàn tác được và bắt buộc có lý do
export function ReasonDialog({ title, confirmLabel, busy, error, disabled, children, onConfirm, onCancel }: Props) {
  const { t } = useTranslation()
  const [reason, setReason] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const confirm = () => {
    setSubmitted(true)
    if (reason.trim()) onConfirm(reason.trim())
  }
  return (
    <Modal title={title} onClose={() => { if (!busy) onCancel() }} footer={
      <>
        <Button onClick={onCancel} disabled={busy}>{t('mgr.close')}</Button>
        <Button variant="danger" loading={busy} disabled={disabled} onClick={confirm}>{confirmLabel}</Button>
      </>
    }>
      <div className={s.stack}>
        {children}
        <Field label={t('mgr.reason')} required error={submitted && !reason.trim() ? t('mgr.required') : undefined}>
          {control => <Textarea {...control} data-autofocus rows={3} maxLength={1000} value={reason} onChange={event => setReason(event.target.value)} />}
        </Field>
        {error && <p className={s.error} role="alert">{error}</p>}
      </div>
    </Modal>
  )
}
