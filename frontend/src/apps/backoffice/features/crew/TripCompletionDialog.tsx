import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { Field } from '@shared/ui/Field'
import { Select } from '@shared/ui/Select'
import { Textarea } from '@shared/ui/Textarea'
import { useToast } from '@shared/ui/toast'
import s from './MyTrips.module.css'

interface Props {
  onConfirm: (condition: string, note?: string) => Promise<void>
  onCancel: () => void
}

export function TripCompletionDialog({ onConfirm, onCancel }: Props) {
  const { t } = useTranslation()
  const toast = useToast()
  const [condition, setCondition] = useState('NORMAL')
  const [note, setNote] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const needsMaintenance = condition === 'NEEDS_MAINTENANCE'

  const submit = async () => {
    setErrors({})
    try {
      await onConfirm(condition, note.trim() || undefined)
    } catch (error) {
      if (error instanceof Error && 'fields' in error) {
        setErrors(error.fields as Record<string, string>)
      }
      toast(error instanceof Error ? error.message : String(error), 'error')
    }
  }

  return (
    <ConfirmDialog
      title={t('myTrips.run.complete')}
      confirmLabel={t('myTrips.run.complete')}
      confirmDisabled={needsMaintenance && !note.trim()}
      onConfirm={submit}
      onCancel={onCancel}
      message={(
        <div className={s.body}>
          <p className={s.line}>{t(needsMaintenance ? 'myTrips.run.completion.maintenanceHint' : 'myTrips.run.completion.hint')}</p>
          <Field label={t('myTrips.run.completion.condition')} error={errors.endVehicleCondition} required>
            {c => <Select {...c} value={condition} onChange={value => { setCondition(value); setErrors({}) }} options={[
              { value: 'NORMAL', label: t('myTrips.run.completion.normal') },
              { value: 'NEEDS_MAINTENANCE', label: t('myTrips.run.completion.needsMaintenance') },
            ]} />}
          </Field>
          <Field label={t('myTrips.run.completion.note')} error={errors.endVehicleNote} required={needsMaintenance}>
            {c => <Textarea {...c} maxLength={1000} value={note} onChange={e => { setNote(e.target.value); setErrors({}) }} />}
          </Field>
        </div>
      )}
    />
  )
}
