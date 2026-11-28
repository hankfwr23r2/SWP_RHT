import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { coordinatorAssignmentsApi } from '@shared/api/coordinatorAssignments'
import { staffWorkloadsApi } from '@shared/api/staffWorkloads'
import { useLoad } from '@shared/hooks/useLoad'
import { useStaffAction } from './useStaffAction'

export function useCoordinatorAssignment(resource: 'departures' | 'vehicle-trips', id: number, currentId: number | undefined,
  onSaved: () => void) {
  const { t } = useTranslation()
  const people = useLoad(() => staffWorkloadsApi.list('COORDINATOR'))
  const [person, setPerson] = useState(currentId == null ? '' : String(currentId))
  const [reason, setReason] = useState('')
  const { run, busy, error } = useStaffAction()
  const changing = currentId != null && String(currentId) !== person
  const submit = async () => {
    if (await run(() => coordinatorAssignmentsApi.assign(resource, id, {
      coordinatorId: Number(person), reason: reason.trim() || undefined,
    }), t('coordAssignment.saved'))) onSaved()
  }
  return { people, person, setPerson, reason, setReason, changing, submit, busy, error }
}
