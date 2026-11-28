import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Modal } from '@shared/ui/Modal'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import { Textarea } from '@shared/ui/Textarea'
import { useCoordinatorAssignment } from './useCoordinatorAssignment'

export function CoordinatorAssignmentDialog({ resource, id, currentId, onClose, onSaved }: {
  resource: 'departures' | 'vehicle-trips'
  id: number
  currentId?: number
  onClose: () => void
  onSaved: () => void
}) {
  const { t } = useTranslation()
  const { people, person, setPerson, reason, setReason, changing, submit, busy, error } =
    useCoordinatorAssignment(resource, id, currentId, onSaved)
  const options = (people.data ?? []).flatMap(row => row.staffId == null ? [] : [{
    value: String(row.staffId), label: row.staffName ?? String(row.staffId),
  }])
  return <Modal title={t('coordAssignment.assign')} onClose={onClose} footer={
    <Button variant="primary" loading={busy} disabled={!options.some(option => option.value === person)
      || (changing && !reason.trim())} onClick={() => void submit()}>{t('coordAssignment.assign')}</Button>
  }>
    {!people.data ? people.error ? <ErrorState onRetry={people.reload} /> : <Skeleton lines={2} />
      : !options.length ? <EmptyState title={t('mgr.assignments.noStaff')} description={t('mgr.assignments.noStaffHint')} /> : <>
        <Field label={t('coordAssignment.owner')} required>
          {control => <Select {...control} options={options} value={person} onChange={setPerson} />}
        </Field>
        <Field label={t('coordAssignment.reason')} required={changing} error={error ?? undefined}>
          {control => <Textarea {...control} maxLength={500} value={reason} onChange={event => setReason(event.target.value)} />}
        </Field>
      </>}
  </Modal>
}
