import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { incidentsApi, type CauseType, type IncidentReport, type IncidentSeverity, type IncidentType } from '@shared/api/incidentOps'
import type { CrewTrip } from '@shared/api/tripExecution'
import { useLoad } from '@shared/hooks/useLoad'
import { useStaffFormAction } from '@shared/hooks/useStaffFormAction'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Select } from '@shared/ui/Select'
import { Textarea } from '@shared/ui/Textarea'
import { IncidentSeveritySelect } from '@backoffice/shared/IncidentSeverity'
import s from './MyTrips.module.css'

const types: IncidentType[] = ['HEALTH', 'VEHICLE_BREAKDOWN', 'ACCIDENT', 'DELAY', 'DOCUMENT', 'OTHER']
const causes: CauseType[] = ['COMPANY_FAULT', 'HORSE_CONDITION', 'CUSTOMER_DOCS', 'AGENT_FAULT', 'FORCE_MAJEURE', 'RECIPIENT_ABSENT']

interface Props {
  run: CrewTrip
  onClose: () => void
  onReport: (request: IncidentReport) => Promise<void>
}

export function TripIncidentReportDialog({ run, onClose, onReport }: Props) {
  const { t } = useTranslation()
  const [form, setForm] = useState({
    scope: '', type: 'OTHER' as IncidentType, cause: 'COMPANY_FAULT' as CauseType, title: '', description: '',
  })
  const [severity, setSeverity] = useState<IncidentSeverity>()
  const { data: defaults, error, reload } = useLoad(incidentsApi.severityDefaults, [], { toastOnError: false })
  const action = useStaffFormAction()
  const tripId = run.trip?.id
  const disabled = tripId === undefined || !form.title.trim() || !form.description.trim() || (form.type === 'HEALTH' && !form.scope)

  async function submit() {
    if (tripId === undefined) return
    const reported = await action.run(() => onReport({
      vehicleTripId: tripId, horseLegId: form.scope ? Number(form.scope) : undefined,
      incidentType: form.type, severity, causeType: form.cause, title: form.title.trim(), description: form.description.trim(),
    }), false)
    if (reported) onClose()
  }

  return (
    <ConfirmDialog tone="danger" title={t('myTrips.run.incident.reportTitle')} confirmLabel={t('myTrips.run.incident.send')}
      confirmDisabled={disabled} onCancel={onClose} onConfirm={submit} message={
        <div className={s.body}>
          <p className={s.muted}>{t('myTrips.run.incident.reportConsequence')}</p>
          <Field label={t('myTrips.run.incident.scope')} required={form.type === 'HEALTH'} error={action.fields.horseLegId}>
            {props => <Select {...props} value={form.scope} onChange={scope => setForm(current => ({ ...current, scope }))}
              options={[{ value: '', label: t('myTrips.run.incident.wholeTrip') },
                ...(run.horses ?? []).map(h => ({ value: `${h.leg?.id}`, label: `${h.horseName} · ${h.bookingCode}` }))]}
              disabled={action.busy} />}
          </Field>
          <Field label={t('incidents.colType')} error={action.fields.incidentType}>
            {props => <Select {...props} value={form.type} onChange={value => {
              setForm(current => ({ ...current, type: value as IncidentType }))
              setSeverity(undefined)
            }} options={types.map(type => ({ value: type, label: t(`incidents.type.${type}`) }))} disabled={action.busy} />}
          </Field>
          <IncidentSeveritySelect value={severity ?? defaults?.[form.type]} onChange={setSeverity}
            error={action.fields.severity} disabled={action.busy} />
          {error && <ErrorState message={t('incidents.severity.defaultsError')} onRetry={reload} />}
          <Field label={t('myTrips.run.incident.cause')} error={action.fields.causeType}>
            {props => <Select {...props} value={form.cause} onChange={value => setForm(current => ({ ...current, cause: value as CauseType }))}
              options={causes.map(cause => ({ value: cause, label: t(`incidents.cause.${cause}`) }))} disabled={action.busy} />}
          </Field>
          <Field label={t('myTrips.run.incident.incidentTitle')} required error={action.fields.title}>
            {props => <Input {...props} maxLength={200} value={form.title}
              onChange={event => setForm(current => ({ ...current, title: event.target.value }))} disabled={action.busy} />}
          </Field>
          <Field label={t('myTrips.run.incident.incidentDescription')} required error={action.fields.description}>
            {props => <Textarea {...props} maxLength={4000} value={form.description}
              onChange={event => setForm(current => ({ ...current, description: event.target.value }))} disabled={action.busy} />}
          </Field>
        </div>
      } />
  )
}
