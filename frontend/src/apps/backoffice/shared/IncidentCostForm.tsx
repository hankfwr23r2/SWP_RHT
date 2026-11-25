import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { CostCategory, CostItemCreate } from '@shared/api/incidentOps'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Select } from '@shared/ui/Select'

const CATEGORIES: CostCategory[] = ['VET', 'RESCUE', 'REPAIR', 'STABLING', 'TOLL', 'FUEL', 'WAITING', 'OTHER']

interface Props {
  incidentId: number
  busy: boolean
  className: string
  onSave: (draft: CostItemCreate) => Promise<boolean>
}

export function IncidentCostForm({ incidentId, busy, className, onSave }: Props) {
  const { t } = useTranslation()
  const [category, setCategory] = useState<CostCategory>('VET')
  const [description, setDescription] = useState('')
  const [amount, setAmount] = useState('')
  const [consulted, setConsulted] = useState('no')

  const save = async () => {
    if (await onSave({ incidentId, category, description: description.trim(), amount: Number(amount),
      customerConsulted: consulted === 'yes', incurredAt: new Date().toISOString() })) {
      setDescription('')
      setAmount('')
      setConsulted('no')
    }
  }

  return (
    <div className={className}>
      <Field label={t('incidents.costs')}>
        {c => <Select {...c} value={category} onChange={value => setCategory(value as CostCategory)}
          options={CATEGORIES.map(v => ({ value: v, label: t(`incidents.category.${v}`) }))} />}
      </Field>
      <Field label={t('myTrips.run.incident.costDescription')} required>
        {c => <Input {...c} value={description} onChange={e => setDescription(e.target.value)} />}
      </Field>
      <Field label={t('myTrips.run.incident.amount')} required>
        {c => <Input {...c} inputMode="decimal" value={amount} onChange={e => setAmount(e.target.value)} />}
      </Field>
      <Field label={t('myTrips.run.incident.consulted')}>
        {c => <Select {...c} value={consulted} onChange={setConsulted}
          options={[{ value: 'no', label: t('incidents.notConsulted') }, { value: 'yes', label: t('incidents.consulted') }]} />}
      </Field>
      <Button size="lg" fullWidth disabled={!description.trim() || !(Number(amount) > 0)} loading={busy} onClick={() => void save()}>
        {t('myTrips.run.incident.addCost')}
      </Button>
    </div>
  )
}
