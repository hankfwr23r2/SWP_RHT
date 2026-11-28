import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { IncidentPlanCreate, Party, ResolutionType } from '@shared/api/incidentOps'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Select } from '@shared/ui/Select'
import { Textarea } from '@shared/ui/Textarea'
import s from './Incidents.module.css'

const RESOLUTIONS: ResolutionType[] = ['CONTINUE', 'VET_TREATMENT', 'STABLE_NEARBY', 'TRANSFER_VEHICLE', 'RETURN_TO_HUB', 'CANCEL']

interface Props {
  parties?: Party[]
  canReturn: boolean
  busy: boolean
  onPropose: (draft: IncidentPlanCreate) => Promise<boolean>
}

export function IncidentProposalForm({ parties, canReturn, busy, onPropose }: Props) {
  const { t } = useTranslation()
  const [resolution, setResolution] = useState<ResolutionType>('CONTINUE')
  const [instructions, setInstructions] = useState('')
  const [estimatedCost, setEstimatedCost] = useState('')
  const [handler, setHandler] = useState('')

  return (
    <div className={s.form}>
      <Field label={t('incidents.resolutionType')}>
        {c => <Select {...c} value={resolution} onChange={value => setResolution(value as ResolutionType)}
          options={RESOLUTIONS.filter(r => canReturn || r !== 'RETURN_TO_HUB').map(r => ({ value: r, label: t(`incidents.resolution.${r}`) }))} />}
      </Field>
      <Field label={t('incidents.handler')}>
        {c => <Select {...c} value={handler} onChange={setHandler} placeholder={t('incidents.noHandler')}
          options={[{ value: '', label: t('incidents.noHandler') }, ...(parties ?? []).map(p => ({ value: `${p.id}`, label: p.displayName ?? '' }))]} />}
      </Field>
      <Field label={t('incidents.estimatedCost')}>
        {c => <Input {...c} inputMode="numeric" value={estimatedCost} onChange={e => setEstimatedCost(e.target.value)} />}
      </Field>
      <Field label={t('incidents.instructions')} required>
        {c => <Textarea {...c} value={instructions} onChange={e => setInstructions(e.target.value)} />}
      </Field>
      <Button variant="primary" disabled={!instructions.trim()} loading={busy}
        onClick={async () => {
          if (await onPropose({ resolutionType: resolution, instructions: instructions.trim(),
            estimatedCost: estimatedCost ? Number(estimatedCost) : undefined, handlerPartyId: handler ? Number(handler) : undefined })) {
            setInstructions('')
          }
        }}>
        {t('incidents.propose')}
      </Button>
    </div>
  )
}
