import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { managerOrdersApi } from '@shared/api/managerOrders'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ReasonDialog } from '@backoffice/shared/ReasonDialog'
import { useStaffAction } from '@backoffice/shared/useStaffAction'
import type { OrderData } from '../loaders'
import { canReject } from '../orderGroups'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { staffWorkloadsApi } from '@shared/api/staffWorkloads'
import { useLoad } from '@shared/hooks/useLoad'
import { SpecialistSelect } from '@backoffice/shared/SpecialistSelect'
import s from '@backoffice/shared/StaffPanels.module.css'

// Tiếp nhận đơn mới hoặc từ chối đơn chưa báo giá
function PendingIntakePanel({ data }: { data: OrderData }) {
  const { t } = useTranslation()
  const { run, busy, error, clearError } = useStaffAction()
  const [rejecting, setRejecting] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [selected, setSelected] = useState('')
  const workloads = useLoad(staffWorkloadsApi.list, [], { toastOnError: false })
  const recommended = workloads.data?.reduce<typeof workloads.data[number] | undefined>((least, person) =>
    !least || (person.assignedCount ?? 0) < (least.assignedCount ?? 0) ? person : least, undefined)
  const specialistId = workloads.data?.find(person => String(person.staffId) === selected)?.staffId ?? recommended?.staffId
  const accept = async () => {
    if (specialistId != null && await run(() => managerOrdersApi.intake(data.id, specialistId), t('mgr.intake.done'))) {
      setConfirming(false)
    }
  }
  const reject = async (reason: string) => {
    if (await run(() => managerOrdersApi.reject(data.id, reason), t('mgr.intake.rejected'))) setRejecting(false)
  }
  return (
    <Card title={t('mgr.intake.title')}>
      <div className={s.stack}>
        <p className={s.hint}>{t('mgr.intake.pending')}</p>
        {error && !rejecting && <p className={s.error} role="alert">{error}</p>}
        <SpecialistSelect data={workloads.data} error={workloads.error} reload={workloads.reload}
          value={specialistId == null ? '' : String(specialistId)} onChange={setSelected} />
        <div className={s.row}>
          <Button variant="primary" disabled={specialistId == null || busy}
            onClick={() => { clearError(); setConfirming(true) }}>{t('mgr.intake.accept')}</Button>
          <Button variant="ghost" disabled={busy} onClick={() => { clearError(); setRejecting(true) }}>{t('mgr.intake.reject')}</Button>
        </div>
      </div>
      {confirming && <ConfirmDialog title={t('mgr.intake.accept')} confirmLabel={t('mgr.intake.accept')}
        message={<><p>{t('mgr.assignments.intakeHint')}</p>{error && <p role="alert">{error}</p>}</>}
        onConfirm={accept} onCancel={() => setConfirming(false)} />}
      {rejecting && <ReasonDialog title={t('mgr.intake.rejectTitle')} confirmLabel={t('mgr.intake.reject')} busy={busy} error={error}
        onConfirm={reject} onCancel={() => setRejecting(false)}><p className={s.hint}>{t('mgr.intake.rejectHint')}</p></ReasonDialog>}
    </Card>
  )
}

export function IntakePanel({ data }: { data: OrderData }) {
  return canReject(data.detail?.booking?.status) ? <PendingIntakePanel data={data} /> : null
}
