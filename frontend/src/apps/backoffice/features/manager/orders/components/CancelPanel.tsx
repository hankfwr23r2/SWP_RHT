import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { managerOrdersApi } from '@shared/api/managerOrders'
import { useLoad } from '@shared/hooks/useLoad'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ErrorState } from '@shared/ui/ErrorState'
import { Modal } from '@shared/ui/Modal'
import { Skeleton } from '@shared/ui/Skeleton'
import { ReasonDialog } from '@backoffice/shared/ReasonDialog'
import { useStaffAction } from '@backoffice/shared/useStaffAction'
import { CancellationAmounts } from './CancellationAmounts'
import s from '@backoffice/shared/StaffPanels.module.css'

// Hủy theo yêu cầu khách hoặc do công ty, luôn xem trước tiền mất và tiền hoàn
export function CancelPanel({ bookingId }: { bookingId: number }) {
  const { t } = useTranslation()
  const [mode, setMode] = useState<'customer' | 'company' | null>(null)
  return (
    <Card title={t('mgr.cancel.title')}>
      <div className={s.row}>
        <Button onClick={() => setMode('customer')}>{t('mgr.cancel.customer')}</Button>
        <Button variant="ghost" onClick={() => setMode('company')}>{t('mgr.cancel.company')}</Button>
      </div>
      {mode && <CancelDialog key={mode} bookingId={bookingId} byCompany={mode === 'company'} onClose={() => setMode(null)} />}
    </Card>
  )
}

function CancelDialog({ bookingId, byCompany, onClose }: { bookingId: number; byCompany: boolean; onClose: () => void }) {
  const { t } = useTranslation()
  const { run, busy, error } = useStaffAction()
  const { data: preview, loaded, error: loadError, reload } = useLoad(
    () => managerOrdersApi.cancellationPreview(bookingId, byCompany), [bookingId, byCompany], { toastOnError: false })
  const title = t(byCompany ? 'mgr.cancel.company' : 'mgr.cancel.customer')

  if (!loaded || loadError || !preview) {
    return <Modal title={title} onClose={onClose}>{loadError ? <ErrorState message={loadError.message} onRetry={reload} /> : <Skeleton lines={3} />}</Modal>
  }
  const cancel = async (reason: string) => {
    const done = await run(() => byCompany ? managerOrdersApi.cancelForCompany(bookingId, reason)
      : managerOrdersApi.cancelForCustomer(bookingId, reason), t('mgr.cancel.done'))
    if (done) onClose()
  }
  return (
    <ReasonDialog title={title} confirmLabel={t('mgr.cancel.confirm')} busy={busy} error={error} disabled={!preview.allowed} onConfirm={cancel} onCancel={onClose}>
      {!preview.allowed ? <p className={s.alert}>{t('mgr.cancel.notAllowed')}</p> : (
        <>
          <p className={s.hint}>{t(byCompany ? 'mgr.cancel.companyHint' : 'mgr.cancel.customerHint')}</p>
          <CancellationAmounts amounts={preview} />
        </>
      )}
    </ReasonDialog>
  )
}
