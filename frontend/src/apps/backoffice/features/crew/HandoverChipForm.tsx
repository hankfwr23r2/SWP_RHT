import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { HandoverHorse, HandoverScanRequest } from '@shared/api/tripExecution'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Textarea } from '@shared/ui/Textarea'
import p from '@backoffice/shared/StaffPanels.module.css'
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
  const [manual, setManual] = useState(false)
  const [reason, setReason] = useState('')
  const [condition, setCondition] = useState<string>()
  const [errors, setErrors] = useState<Record<string, string>>({})
  const conditionNote = condition ?? horse.conditionNote
  const canSubmit = !!(chip.trim() && (!manual || reason.trim()) && (!needsCondition || conditionNote?.trim()))

  return (
    <form className={s.body} onSubmit={event => {
      event.preventDefault()
      if (!canSubmit || busy) return
      setErrors({})
      onSave({
        horseLegId: horse.horseLegId ?? 0,
        microchip: chip.trim(),
        entryMethod: manual ? 'MANUAL' : 'SCAN',
        manualReason: manual ? reason.trim() : undefined,
        conditionNote,
      }, setErrors)
    }}>
      <Field label={t('myTrips.run.chip', { name: horse.horseName })} required error={errors.microchip}>
        {c => <Input {...c} inputMode="numeric" maxLength={30} value={chip} disabled={busy} onChange={e => setChip(e.target.value)} />}
      </Field>
      <label className={p.check}>
        <input type="checkbox" checked={manual} disabled={busy} onChange={e => setManual(e.target.checked)} /> {t('handoverEvidence.manualToggle')}
      </label>
      {manual && <Field label={t('handoverEvidence.manualReason')} required error={errors.manualReason}>
        {c => <Textarea {...c} maxLength={500} value={reason} disabled={busy} onChange={e => setReason(e.target.value)} />}
      </Field>}
      {needsCondition && <Field label={t('handoverEvidence.condition')} required error={errors.conditionNote}>
        {c => <Textarea {...c} maxLength={1000} value={conditionNote ?? ''} disabled={busy} onChange={e => setCondition(e.target.value)} />}
      </Field>}
      <Button type="submit" size="lg" fullWidth disabled={!canSubmit} loading={busy}>{t('myTrips.run.scan')}</Button>
    </form>
  )
}
