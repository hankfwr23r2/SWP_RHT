import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { coordinatorAssignmentsApi } from '@shared/api/coordinatorAssignments'
import { Button } from '@shared/ui/Button'
import { ReasonDialog } from '@backoffice/shared/ReasonDialog'
import { useStaffAction } from '@backoffice/shared/useStaffAction'

export function TripTakeOverButton({ tripId, onSaved }: { tripId: number; onSaved: () => Promise<void> }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const { run, busy, error, clearError } = useStaffAction()
  const label = t('coordAssignment.takeOver')
  return <>
    <Button size="sm" onClick={() => { clearError(); setOpen(true) }}>{label}</Button>
    {open && <ReasonDialog title={label} confirmLabel={label} busy={busy} error={error} onCancel={() => setOpen(false)}
      onConfirm={async reason => {
        if (await run(() => coordinatorAssignmentsApi.takeOver(tripId, { reason }), t('coordAssignment.saved'))) {
          setOpen(false)
          await onSaved()
        }
      }} />}
  </>
}
