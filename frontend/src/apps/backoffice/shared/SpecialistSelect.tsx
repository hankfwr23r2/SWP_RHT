import { useTranslation } from 'react-i18next'
import type { StaffWorkload } from '@shared/api/staffWorkloads'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'

export function SpecialistSelect({ data, error, reload, value, onChange }: {
  data?: StaffWorkload[]; error: unknown; reload: () => void
  value: string; onChange: (value: string) => void
}) {
  const { t } = useTranslation()
  if (error && !data) return <ErrorState onRetry={reload} />
  if (!data) return <Skeleton lines={1} />
  if (!data.length) return <EmptyState title={t('mgr.assignments.noStaff')} description={t('mgr.assignments.noStaffHint')} />
  return <Field label={t('mgr.assignments.specialist')} required>
    {control => <Select {...control} value={value} onChange={onChange} options={data.map(person => ({
      value: String(person.staffId),
      label: t('mgr.assignments.option', { name: person.staffName, count: person.assignedCount }),
    }))} />}
  </Field>
}
