import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { HandoverHorse, HandoverScanRequest } from '@shared/api/tripExecution'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Textarea } from '@shared/ui/Textarea'
import s from './MyTrips.module.css'

interface Props {
  horse: HandoverHorse
  needsCondition: boolean
  busy: boolean
  onSave: (request: HandoverScanRequest, onFields: (fields: Record<string, string>) => void) => void
}

export function HandoverChipForm({ horse, needsCondition, busy, onSave }: Props) {
  const { t } = useTranslation()
  const [chip, setChip] = useState('')
  const [condition, setCondition] = useState<string>()
  const [errors, setErrors] = useState<Record<string, string>>({})
  const conditionNote = condition ?? horse.conditionNote
  const canSubmit = !!(chip.trim() && (!needsCondition || conditionNote?.trim()))

  return (
    <form className={s.body} onSubmit={event => {
      event.preventDefault()
      if (!canSubmit || busy) return
      setErrors({})
      onSave({
        horseLegId: horse.horseLegId ?? 0,
        microchip: chip.trim(),
        entryMethod: 'SCAN',
        conditionNote,
      }, setErrors)
    }}>
      <Field label={t('myTrips.run.chip', { name: horse.horseName })} required error={errors.microchip}>
        {c => <Input {...c} inputMode="numeric" maxLength={30} value={chip} disabled={busy} onChange={e => setChip(e.target.value)} />}
      </Field>
      {needsCondition && <Field label={t('handoverEvidence.condition')} required error={errors.conditionNote}>
        {c => <Textarea {...c} maxLength={1000} value={conditionNote ?? ''} disabled={busy} onChange={e => setCondition(e.target.value)} />}
      </Field>}
      <Button type="submit" size="lg" fullWidth disabled={!canSubmit} loading={busy}>{t('myTrips.run.scan')}</Button>
    </form>
  )
}
