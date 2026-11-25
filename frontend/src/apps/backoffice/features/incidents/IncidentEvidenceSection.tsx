import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Evidence } from '@shared/api/incidentOps'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Select } from '@shared/ui/Select'
import { EvidencePanel } from '@backoffice/shared/EvidencePanel'

type EvidenceType = 'INCIDENT_PHOTO' | 'VET_REPORT'

interface Props {
  evidence: Evidence[]
  busy: boolean
  canConfirm: boolean
  onConfirm: () => Promise<boolean>
  onUpload?: (file: File, type: EvidenceType) => Promise<Evidence[]>
}

export function IncidentEvidenceSection({ evidence, busy, canConfirm, onConfirm, onUpload }: Props) {
  const { t } = useTranslation()
  const [type, setType] = useState<EvidenceType>('INCIDENT_PHOTO')
  const hasVetReport = evidence.some(file => file.documentTypeCode === 'VET_REPORT')
  const fitPhotos = evidence.filter(file => file.documentTypeCode === 'FIT_CHECK_PHOTO')

  return (
    <>
      {onUpload && (
        <Field label={t('incidents.evidenceType')} hint={canConfirm && !hasVetReport ? t('incidents.vetReportNeeded') : undefined}>
          {c => <Select {...c} value={type} onChange={value => setType(value === 'VET_REPORT' ? value : 'INCIDENT_PHOTO')}
            options={[{ value: 'INCIDENT_PHOTO', label: t('incidents.photos') }, { value: 'VET_REPORT', label: t('incidents.vetReport') }]} />}
        </Field>
      )}
      <EvidencePanel title={t('incidents.evidence')} evidence={evidence.filter(file => file.documentTypeCode !== 'FIT_CHECK_PHOTO')}
        upload={onUpload ? file => onUpload(file, type) : undefined} />
      {fitPhotos.length > 0 && <EvidencePanel title={t('incidents.fitCheckPhoto')} evidence={fitPhotos} />}
      {canConfirm && (
        <Button disabled={!hasVetReport} loading={busy} onClick={() => void onConfirm()}>{t('incidents.confirmOnBehalf')}</Button>
      )}
    </>
  )
}
