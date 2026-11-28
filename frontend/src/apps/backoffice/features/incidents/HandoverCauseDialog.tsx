import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { HandoverCauseChange, Incident } from '@shared/api/incidentOps'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { Field } from '@shared/ui/Field'
import { Select } from '@shared/ui/Select'
import { Textarea } from '@shared/ui/Textarea'
import { useHandoverCause } from './useHandoverCause'

type Cause = NonNullable<HandoverCauseChange['cause']>
const managerCauses: Cause[] = ['RECIPIENT_ABSENT', 'COMPANY_FAULT', 'FORCE_MAJEURE']

interface Props {
  incident: Incident
  role: 'manager' | 'coordinator'
  onClose: () => void
  onChanged: () => Promise<unknown>
}

export function HandoverCauseDialog({ incident, role, onClose, onChanged }: Props) {
  const { t } = useTranslation()
  const { amend, fields, busy } = useHandoverCause()
  const choices: Cause[] = role === 'manager' ? managerCauses : ['FORCE_MAJEURE']
  const current = incident.confirmedCauseType ?? incident.causeType
  const [cause, setCause] = useState<Cause | ''>(role === 'coordinator' ? 'FORCE_MAJEURE' : '')
  const [reason, setReason] = useState('')
  const [submitted, setSubmitted] = useState(false)

  async function confirm() {
    setSubmitted(true)
    const id = incident.id
    if (id === undefined || !cause || !reason.trim()) return
    if (await amend(id, { cause, reason: reason.trim() })) {
      onClose()
      await onChanged()
    }
  }

  return (
    <ConfirmDialog title={t('incidents.handoverCause.edit')} confirmLabel={t('incidents.handoverCause.save')}
      onConfirm={confirm} onCancel={onClose} message={
        <>
          <p>{t('incidents.handoverCause.consequence')}</p>
          <Field label={t('incidents.handoverCause.result')} required
            error={fields.cause ?? (submitted && !cause ? t('incidents.handoverCause.choose') : undefined)}>
            {props => <Select {...props} value={cause} disabled={busy}
              onChange={value => setCause(choices.find(choice => choice === value) ?? '')}
              options={choices.filter(choice => choice !== current)
                .map(choice => ({ value: choice, label: t(`incidents.cause.${choice}`) }))} />}
          </Field>
          <Field label={t('incidents.handoverCause.reason')} required
            error={fields.reason ?? (submitted && !reason.trim() ? t('incidents.handoverCause.reasonRequired') : undefined)}>
            {props => <Textarea {...props} maxLength={500} value={reason} disabled={busy}
              onChange={event => setReason(event.target.value)} />}
          </Field>
        </>
      } />
  )
}
