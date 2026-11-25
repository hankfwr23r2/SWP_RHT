import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { CostItem } from '@shared/api/incidentOps'
import { Button } from '@shared/ui/Button'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { Field } from '@shared/ui/Field'
import { Textarea } from '@shared/ui/Textarea'

interface Props {
  cost: CostItem
  role: 'coordinator' | 'manager'
  busy: boolean
  onApprove: (reason?: string) => Promise<boolean>
  onPhoneConsent: (note: string) => Promise<boolean>
}

export function IncidentCostControls({ cost, role, busy, onApprove, onPhoneConsent }: Props) {
  const { t } = useTranslation()
  const [note, setNote] = useState('')
  const [confirming, setConfirming] = useState(false)
  if (cost.approvedAt) return null
  const phone = role === 'coordinator' && !!cost.customerResponseDueAt && cost.customerAgreed == null
  if (role === 'coordinator' && !phone) return null
  const override = cost.customerAgreed === false
  if (override && cost.category !== 'VET') return <p>{t('incidentDecisions.declined')}</p>
  const label = t(phone ? 'incidentDecisions.phoneSave' : 'incidents.approveCost')

  return (
    <>
      {(phone || override) && <Field required label={t(phone ? 'incidentDecisions.phoneNote' : 'incidentDecisions.override')}>
        {props => <Textarea {...props} maxLength={500} value={note} onChange={event => setNote(event.target.value)} />}
      </Field>}
      <Button size="sm" disabled={busy || ((phone || override) && !note.trim())} onClick={() => setConfirming(true)}>{label}</Button>
      {confirming && <ConfirmDialog title={label}
        message={t(phone ? 'incidentDecisions.phoneConfirm' : 'incidentDecisions.costConfirm')}
        confirmLabel={label} onCancel={() => setConfirming(false)}
        onConfirm={async () => {
          const saved = phone ? await onPhoneConsent(note.trim()) : await onApprove(override ? note.trim() : undefined)
          if (saved) setConfirming(false)
        }} />}
    </>
  )
}
