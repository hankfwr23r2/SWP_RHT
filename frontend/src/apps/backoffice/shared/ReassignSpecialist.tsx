import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { staffWorkloadsApi } from '@shared/api/staffWorkloads'
import { useLoad } from '@shared/hooks/useLoad'
import { Button } from '@shared/ui/Button'
import { ReasonDialog } from './ReasonDialog'
import { SpecialistSelect } from './SpecialistSelect'
import { useStaffAction } from './useStaffAction'

function ReassignDialog({ id, currentId, onClose, onDone }: {
  id: number; currentId?: number | null; onClose: () => void; onDone?: () => void
}) {
  const { t } = useTranslation()
  const { data, error, reload } = useLoad(staffWorkloadsApi.list)
  const { run, busy, error: saveError } = useStaffAction()
  const [value, setValue] = useState('')
  const options = data?.filter(person => person.staffId !== currentId)
  const specialistId = options?.find(person => String(person.staffId) === value)?.staffId
  const confirm = async (reason: string) => {
    if (specialistId == null) return
    if (await run(() => staffWorkloadsApi.reassign(id, { specialistId, reason }), t('mgr.assignments.saved'))) {
      onDone?.()
      onClose()
    }
  }
  return <ReasonDialog title={t('mgr.assignments.change')} confirmLabel={t('mgr.assignments.change')}
    busy={busy} error={saveError} disabled={specialistId == null} onConfirm={confirm} onCancel={onClose}>
    <p>{t('mgr.assignments.changeHint')}</p>
    <SpecialistSelect data={options} error={error} reload={reload} value={value} onChange={setValue} />
  </ReasonDialog>
}

export function ReassignSpecialist({ id, currentId, onDone }: {
  id: number; currentId?: number | null; onDone?: () => void
}) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  return <>
    <Button size="sm" onClick={() => setOpen(true)}>{t('mgr.assignments.change')}</Button>
    {open && <ReassignDialog id={id} currentId={currentId} onDone={onDone} onClose={() => setOpen(false)} />}
  </>
}
