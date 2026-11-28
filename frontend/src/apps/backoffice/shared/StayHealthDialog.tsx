import { useId, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { HealthLogCreate, HealthStatus } from '@shared/api/tripExecution'
import { Button } from '@shared/ui/Button'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { Select } from '@shared/ui/Select'
import { useStayHealth } from './useStayHealth'
import s from './StayHealth.module.css'

const STATUSES: HealthStatus[] = ['NORMAL', 'WATCH', 'SICK', 'CRITICAL']

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
  const [confirmation, setConfirmation] = useState<HealthLogCreate>()
  const { errors, save } = useStayHealth(onSaved)
  const update = (patch: Partial<HealthLogCreate>) => setDraft(current => ({ ...current, ...patch }))
  const prepare = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (incomplete) return
    setConfirmation(draft)
  }
  const incomplete = reported && !draft.reportedBy?.trim()

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
        {reported && <Field label={t('stayHealth.reportedBy')} error={errors.reportedBy} required>
          {control => <Input {...control} maxLength={120} value={draft.reportedBy ?? ''} onChange={event => update({ reportedBy: event.target.value })} />}
        </Field>}
      </form>
    </Modal>}
    {confirmation && <ConfirmDialog title={t('stayHealth.record')} message={t('stayHealth.confirm')}
      confirmLabel={t('myTrips.run.health.save')}
      onCancel={() => setConfirmation(undefined)}
      onConfirm={async () => { await save(confirmation); setConfirmation(undefined) }} />}
  </>
}
