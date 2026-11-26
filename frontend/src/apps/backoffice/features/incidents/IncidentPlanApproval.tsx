import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { CauseType, Incident } from '@shared/api/incidentOps'
import { Button } from '@shared/ui/Button'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { Field } from '@shared/ui/Field'
import { Select } from '@shared/ui/Select'

const CAUSES: CauseType[] = ['COMPANY_FAULT', 'FORCE_MAJEURE', 'HORSE_CONDITION', 'CUSTOMER_DOCS', 'RECIPIENT_ABSENT']

interface Props {
  incident: Incident
  busy: boolean
  onApprove: (cause?: CauseType) => Promise<boolean>
}

export function IncidentPlanApproval({ incident, busy, onApprove }: Props) {
  const { t } = useTranslation()
  const [cause, setCause] = useState(incident.causeType)
  const [confirming, setConfirming] = useState(false)

  return (
    <>
      {incident.incidentType !== 'HANDOVER_TIMEOUT' && <Field label={t('incidentDecisions.cause')}>
        {props => <Select {...props} value={cause ?? ''} onChange={value => setCause(value as CauseType)}
          options={CAUSES.map(value => ({ value, label: t(`incidents.cause.${value}`) }))} />}
      </Field>}
      <Button variant="primary" disabled={busy} onClick={() => setConfirming(true)}>{t('incidents.approve')}</Button>
      {confirming && <ConfirmDialog title={t('incidents.approve')} message={t('incidentDecisions.planConfirm')}
        confirmLabel={t('incidents.approve')} onCancel={() => setConfirming(false)}
        onConfirm={async () => { if (await onApprove(cause)) setConfirming(false) }} />}
    </>
  )
}
