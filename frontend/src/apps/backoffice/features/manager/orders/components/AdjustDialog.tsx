import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { Textarea } from '@shared/ui/Textarea'
import s from '@backoffice/shared/StaffPanels.module.css'

interface Props {
  busy: boolean
  error: string
  onSave: (amount: number, reason: string) => void
  onCancel: () => void
}

// Điều chỉnh báo giá: số dương là tăng, số âm là giảm
export function AdjustDialog({ busy, error, onSave, onCancel }: Props) {
  const { t } = useTranslation()
  const [amount, setAmount] = useState('')
  const [reason, setReason] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const value = Number(amount)
  const badAmount = !amount.trim() || !Number.isFinite(value) || value === 0

  const save = () => {
    setSubmitted(true)
    if (!badAmount && reason.trim()) onSave(value, reason.trim())
  }
  return (
    <Modal title={t('mgr.quote.adjust')} onClose={() => { if (!busy) onCancel() }} footer={
      <>
        <Button onClick={onCancel} disabled={busy}>{t('mgr.close')}</Button>
        <Button variant="primary" loading={busy} onClick={save}>{t('mgr.quote.saveAdjust')}</Button>
      </>
    }>
      <div className={s.stack}>
        <Field label={t('mgr.quote.adjustAmount')} hint={t('mgr.quote.adjustHint')} required error={submitted && badAmount ? t('mgr.quote.badAmount') : undefined}>
          {control => <Input {...control} data-autofocus inputMode="numeric" value={amount} maxLength={15}
            onChange={event => setAmount(event.target.value.replace(/(?!^-)[^\d]/g, ''))} />}
        </Field>
        <Field label={t('mgr.reason')} required error={submitted && !reason.trim() ? t('mgr.required') : undefined}>
          {control => <Textarea {...control} rows={3} maxLength={500} value={reason} onChange={event => setReason(event.target.value)} />}
        </Field>
        {error && <p className={s.error} role="alert">{error}</p>}
      </div>
    </Modal>
  )
}
