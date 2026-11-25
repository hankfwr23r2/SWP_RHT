import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { incidentsApi, type IncidentSeverity } from '@shared/api/incidentOps'
import { useStaffFormAction } from '@shared/hooks/useStaffFormAction'
import { Button } from '@shared/ui/Button'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { Field } from '@shared/ui/Field'
import { Textarea } from '@shared/ui/Textarea'
import { IncidentSeveritySelect } from '@backoffice/shared/IncidentSeverity'
import s from './Incidents.module.css'

interface Props {
  incidentId: number
  severity?: IncidentSeverity
  canUnlock?: boolean
  busy: boolean
  onChanged: () => Promise<void>
}

function useIncidentSeverityActions(incidentId: number, onChanged: () => Promise<void>) {
  const action = useStaffFormAction()
  const submit = (save: () => Promise<void>) => action.run(async () => {
    await save()
    await onChanged()
  }, false)
  return {
    ...action,
    changeSeverity: (severity: IncidentSeverity) => submit(() => incidentsApi.changeSeverity(incidentId, severity)),
    unlockTrip: (reason: string) => submit(() => incidentsApi.unlockTrip(incidentId, reason)),
  }
}

export function IncidentSeverityControls({ incidentId, severity, canUnlock, busy, onChanged }: Props) {
  const { t } = useTranslation()
  const [draft, setDraft] = useState<IncidentSeverity>()
  const [unlocking, setUnlocking] = useState(false)
  const [reason, setReason] = useState('')
  const action = useIncidentSeverityActions(incidentId, onChanged)
  const blocked = busy || action.busy

  return (
    <div className={s.form}>
      <IncidentSeveritySelect value={draft ?? severity} onChange={setDraft} error={action.fields.severity} disabled={blocked} />
      <Button disabled={busy || !draft || draft === severity} loading={action.busy && !unlocking} onClick={async () => {
        if (draft && await action.changeSeverity(draft)) setDraft(undefined)
      }}>{t('incidents.severity.save')}</Button>
      {canUnlock && <Button variant="danger" disabled={blocked} onClick={() => {
        action.reset()
        setReason('')
        setUnlocking(true)
      }}>{t('incidents.unlockTrip.action')}</Button>}
      {unlocking && <ConfirmDialog tone="danger" title={t('incidents.unlockTrip.action')} confirmLabel={t('incidents.unlockTrip.action')}
        confirmDisabled={!reason.trim() || blocked} onCancel={() => setUnlocking(false)} onConfirm={async () => {
          if (await action.unlockTrip(reason.trim())) setUnlocking(false)
        }} message={
          <>
            <p>{t('incidents.unlockTrip.consequence')}</p>
            <Field label={t('incidents.unlockTrip.reason')} required error={action.fields.reason}>
              {props => <Textarea {...props} value={reason} maxLength={1000} disabled={action.busy}
                onChange={event => setReason(event.target.value)} />}
            </Field>
          </>
        } />}
    </div>
  )
}
