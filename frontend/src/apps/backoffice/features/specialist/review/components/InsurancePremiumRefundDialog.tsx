import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import type { ReviewActions } from '../useReviewActions'

export function InsurancePremiumRefundDialog({ id, actions, onClose }: {
  id: number; actions: ReviewActions; onClose: () => void
}) {
  const { t } = useTranslation()
  const [amount, setAmount] = useState('')
  const [reference, setReference] = useState('')
  const [confirming, setConfirming] = useState(false)
  const submit = async () => {
    const result = await actions.recordPremiumRefund(id, {
      refundedPremiumAmount: Number(amount),
      receiptReference: reference.trim(),
    })
    if (result) onClose()
    else setConfirming(false)
  }

  if (confirming) return <ConfirmDialog title={t('staffReview.recordPremiumRefund')}
    message={t('staffReview.premiumRefundHint')} confirmLabel={t('staffReview.recordPremiumRefund')}
    onCancel={() => setConfirming(false)} onConfirm={submit} />

  return (
    <Modal title={t('staffReview.recordPremiumRefund')} onClose={onClose}
      footer={<Button loading={actions.busy} disabled={!reference.trim() || Number(amount) <= 0}
        onClick={() => setConfirming(true)}>{t('staffReview.recordPremiumRefund')}</Button>}>
      <p>{t('staffReview.premiumRefundHint')}</p>
      <Field label={t('staffReview.refundedPremiumAmount')} error={actions.fields.refundedPremiumAmount} required>
        {props => <Input {...props} type="number" min="0.01" step="0.01" value={amount}
          onChange={event => setAmount(event.target.value)} />}
      </Field>
      <Field label={t('staffReview.premiumRefundReceipt')} error={actions.fields.receiptReference} required>
        {props => <Input {...props} maxLength={120} value={reference} onChange={event => setReference(event.target.value)} />}
      </Field>
    </Modal>
  )
}
