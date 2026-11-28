import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { refundsApi, type Refund } from '@shared/api/refunds'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { FileField } from '@shared/ui/FileField'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { useToast } from '@shared/ui/toast'
import { money } from '@backoffice/shared/staffFormat'
import s from '@backoffice/shared/StaffPanels.module.css'

interface Props {
  refund: Refund
  onClose: () => void
  onDone: () => void
}

export function MarkRefundedDialog({ refund, onClose, onDone }: Props) {
  const { t } = useTranslation()
  const toast = useToast()
  const [reference, setReference] = useState('')
  const [receipt, setReceipt] = useState<File>()
  const [submitted, setSubmitted] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const save = async () => {
    setSubmitted(true)
    if (!reference.trim() || refund.id == null) return
    setBusy(true)
    setError('')
    try {
      await refundsApi.markRefunded(refund.id, reference.trim(), receipt)
      toast(t('mgr.refunds.done'))
      onDone()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '')
    } finally {
      setBusy(false)
    }
  }
  return (
    <Modal title={t('mgr.refunds.markTitle', { code: refund.bookingCode })} onClose={() => { if (!busy) onClose() }} footer={
      <>
        <Button onClick={onClose} disabled={busy}>{t('mgr.close')}</Button>
        <Button variant="primary" loading={busy} onClick={save}>{t('mgr.refunds.mark')}</Button>
      </>
    }>
      <div className={s.stack}>
        <p>{t('mgr.refunds.markHint', { amount: money(refund.amount, refund.currencyCode), name: refund.customerName ?? '' })}</p>
        <Field label={t('mgr.refunds.reference')} required error={submitted && !reference.trim() ? t('mgr.required') : undefined}>
          {control => <Input {...control} data-autofocus maxLength={100} value={reference} onChange={event => setReference(event.target.value)} />}
        </Field>
        <FileField label={t('mgr.refunds.receipt')} fileName={receipt?.name} onChange={(_, file) => setReceipt(file)} />
        {error && <p className={s.error} role="alert">{error}</p>}
      </div>
    </Modal>
  )
}
