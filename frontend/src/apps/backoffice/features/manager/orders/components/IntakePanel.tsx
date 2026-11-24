import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { managerOrdersApi } from '@shared/api/managerOrders'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ReasonDialog } from '@backoffice/shared/ReasonDialog'
import { useStaffAction } from '@backoffice/shared/useStaffAction'
import type { OrderData } from '../loaders'
import { canReject } from '../orderGroups'
import s from '@backoffice/shared/StaffPanels.module.css'

// Tiếp nhận đơn mới hoặc từ chối đơn chưa báo giá
export function IntakePanel({ data }: { data: OrderData }) {
  const { t } = useTranslation()
  const { run, busy, error, clearError } = useStaffAction()
  const [rejecting, setRejecting] = useState(false)
  const status = data.detail?.booking?.status
  if (!canReject(status)) return null
  const reject = async (reason: string) => {
    if (await run(() => managerOrdersApi.reject(data.id, reason), t('mgr.intake.rejected'))) setRejecting(false)
  }
  return (
    <Card title={t('mgr.intake.title')}>
      <div className={s.stack}>
        <p className={s.hint}>{t(status === 'PENDING_INTAKE' ? 'mgr.intake.pending' : 'mgr.intake.reviewing')}</p>
        {error && !rejecting && <p className={s.error} role="alert">{error}</p>}
        <div className={s.row}>
          {status === 'PENDING_INTAKE' && (
            <Button variant="primary" loading={busy} onClick={() => run(() => managerOrdersApi.intake(data.id), t('mgr.intake.done'))}>{t('mgr.intake.accept')}</Button>
          )}
          <Button variant="ghost" disabled={busy} onClick={() => { clearError(); setRejecting(true) }}>{t('mgr.intake.reject')}</Button>
        </div>
      </div>
      {rejecting && <ReasonDialog title={t('mgr.intake.rejectTitle')} confirmLabel={t('mgr.intake.reject')} busy={busy} error={error}
        onConfirm={reject} onCancel={() => setRejecting(false)}><p className={s.hint}>{t('mgr.intake.rejectHint')}</p></ReasonDialog>}
    </Card>
  )
}
