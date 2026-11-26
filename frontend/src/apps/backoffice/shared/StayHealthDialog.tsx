import { useId, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { HealthLogCreate, HealthStatus, IntakeLevel } from '@shared/api/tripExecution'
import { Button } from '@shared/ui/Button'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { Select } from '@shared/ui/Select'
import { Textarea } from '@shared/ui/Textarea'
import { useStayHealth } from './useStayHealth'
import s from './StayHealth.module.css'

const STATUSES: HealthStatus[] = ['NORMAL', 'WATCH', 'SICK', 'CRITICAL']
const INTAKES: IntakeLevel[] = ['NORMAL', 'REDUCED', 'REFUSED']
const VITALS = [
  { name: 'temperatureCelsius', label: 'myTrips.run.health.temperature', min: 30, max: 45, step: '0.1' },
  { name: 'heartRateBpm', label: 'stayHealth.heartRateBpm', min: 10, max: 200, step: '1' },
  { name: 'respirationRatePerMinute', label: 'stayHealth.respirationRatePerMinute', min: 4, max: 100, step: '1' },
] as const

interface Props {
  bookingHorseId: number
  horseStayId: number
  horseName?: string
  reported?: boolean
  onClose: () => void
  onSaved: () => void
}

export function StayHealthDialog({ bookingHorseId, horseStayId, horseName, reported, onClose, onSaved }: Props) {
  const { t } = useTranslation()
  const formId = useId()
  const [draft, setDraft] = useState<HealthLogCreate>({ bookingHorseId, horseStayId, healthStatus: 'NORMAL' })
  const [vitals, setVitals] = useState<Record<string, string>>({})
  const [confirmation, setConfirmation] = useState<HealthLogCreate>()
  const { errors, save } = useStayHealth(onSaved)
  const update = (patch: Partial<HealthLogCreate>) => setDraft(current => ({ ...current, ...patch }))
  const prepare = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (incomplete) return
    const measured = Object.fromEntries(VITALS.flatMap(({ name }) => vitals[name] ? [[name, Number(vitals[name])]] : []))
    setConfirmation({ ...draft, ...measured })
  }
  const incomplete = reported && (!draft.reportedBy?.trim() || !draft.reportChannel?.trim())

  return <>
    {!confirmation && <Modal title={t('stayHealth.record')} subtitle={horseName} onClose={onClose}
      footer={<Button variant="primary" type="submit" form={formId} disabled={incomplete}>
        {t('myTrips.run.health.save')}
      </Button>}>
      <form id={formId} className={s.form} onSubmit={prepare}>
        <Field label={t('myTrips.run.health.status')} error={errors.healthStatus} required>
          {control => <Select {...control} value={draft.healthStatus ?? 'NORMAL'} onChange={value => update({ healthStatus: value as HealthStatus })}
            options={STATUSES.map(value => ({ value, label: t(`myTrips.run.health.statusValue.${value}`) }))} />}
        </Field>
        {(['waterIntake', 'feedIntake'] as const).map((name, index) =>
          <Field key={name} label={t(index === 0 ? 'myTrips.run.health.water' : 'myTrips.run.health.feed')} error={errors[name]}>
            {control => <Select {...control} value={draft[name] ?? ''} placeholder={t('tripWords.choose')}
              onChange={value => update({ [name]: (value as IntakeLevel) || undefined })}
              options={INTAKES.map(value => ({ value, label: t(`myTrips.run.health.intakeValue.${value}`) }))} />}
          </Field>,
        )}
        {VITALS.map(({ name, label, min, max, step }) =>
          <Field key={name} label={t(label)} error={errors[name]}>
            {control => <Input {...control} type="number" min={min} max={max} step={step} value={vitals[name] ?? ''}
              onChange={event => setVitals(current => ({ ...current, [name]: event.target.value }))} />}
          </Field>,
        )}
        {reported && (['reportedBy', 'reportChannel'] as const).map(name =>
          <Field key={name} label={t(`stayHealth.${name}`)} error={errors[name]} required>
            {control => <Input {...control} maxLength={120} value={draft[name] ?? ''} onChange={event => update({ [name]: event.target.value })} />}
          </Field>,
        )}
        {(['symptoms', 'note'] as const).map(name =>
          <Field key={name} label={t(`stayHealth.${name}`)} error={errors[name]}>
            {control => <Textarea {...control} maxLength={2000} value={draft[name] ?? ''} onChange={event => update({ [name]: event.target.value })} />}
          </Field>,
        )}
      </form>
    </Modal>}
    {confirmation && <ConfirmDialog title={t('stayHealth.record')} message={t('stayHealth.confirm')}
      confirmLabel={t('myTrips.run.health.save')}
      onCancel={() => setConfirmation(undefined)}
      onConfirm={async () => { await save(confirmation); setConfirmation(undefined) }} />}
  </>
}
