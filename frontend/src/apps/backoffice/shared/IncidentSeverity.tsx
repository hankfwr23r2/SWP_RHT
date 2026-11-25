import { useTranslation } from 'react-i18next'
import type { IncidentSeverity } from '@shared/api/incidentOps'
import { Field } from '@shared/ui/Field'
import { Select } from '@shared/ui/Select'
import { StatusBadge, type StatusTone } from '@shared/ui/StatusBadge'

const severities: IncidentSeverity[] = ['LOW', 'MEDIUM', 'HIGH']
const tones: Record<IncidentSeverity, StatusTone> = { LOW: 'info', MEDIUM: 'warning', HIGH: 'danger' }

export function IncidentSeverityBadge({ severity }: { severity?: IncidentSeverity }) {
  const { t } = useTranslation()
  return severity && <StatusBadge tone={tones[severity]} label={t(`incidents.severity.${severity}`)} />
}

interface SelectProps {
  value?: IncidentSeverity
  onChange: (severity: IncidentSeverity) => void
  error?: string
  disabled?: boolean
}

export function IncidentSeveritySelect({ value, onChange, error, disabled }: SelectProps) {
  const { t } = useTranslation()
  return (
    <Field label={t('incidents.severity.label')} error={error}>
      {props => <Select {...props} value={value ?? ''} disabled={disabled}
        placeholder={t('incidents.severity.default')}
        options={severities.map(severity => ({ value: severity, label: t(`incidents.severity.${severity}`) }))}
        onChange={next => {
          const selected = severities.find(severity => severity === next)
          if (selected) onChange(selected)
        }} />}
    </Field>
  )
}
