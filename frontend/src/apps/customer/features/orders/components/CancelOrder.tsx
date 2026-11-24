import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { bookingOrdersApi } from '@shared/api/bookingOrders'
import { useLoad } from '@shared/hooks/useLoad'
import { useContact } from '@shared/hooks/useContact'
import { Button } from '@shared/ui/Button'
import { ErrorState } from '@shared/ui/ErrorState'
import { Modal } from '@shared/ui/Modal'
import { Skeleton } from '@shared/ui/Skeleton'
import { money } from '../format'
import { useOrderAction } from '../useOrderAction'
import { ReasonDialog } from './ReasonDialog'
import s from '../Orders.module.css'

export function CancelOrder({ bookingId }: { bookingId: number }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  return (
    <>
      <Button variant="ghost" onClick={() => setOpen(true)}>{t('myOrders.cancel.open')}</Button>
      {open && <CancelDialog bookingId={bookingId} onClose={() => setOpen(false)} />}
    </>
  )
}

// Xem trước tiền mất và tiền hoàn rồi mới cho hủy
function CancelDialog({ bookingId, onClose }: { bookingId: number; onClose: () => void }) {
  const { t } = useTranslation()
  const { run, busy, error } = useOrderAction()
  const { hotline } = useContact()
  const { data: preview, loaded, error: loadError, reload } = useLoad(
    () => bookingOrdersApi.cancellationPreview(bookingId), [bookingId], { toastOnError: false })

  if (!loaded || loadError || !preview) {
    return <Modal title={t('myOrders.cancel.title')} onClose={onClose}>
      {loadError ? <ErrorState message={loadError.message} onRetry={reload} /> : <Skeleton lines={3} />}
    </Modal>
  }
  const cancel = async (reason: string) => {
    if (await run(() => bookingOrdersApi.cancel(bookingId, reason), t('myOrders.cancel.done'))) onClose()
  }
  const amounts = (['refundable', 'reviewable', 'forfeited'] as const).filter(key => preview[key])
  return (
    <ReasonDialog title={t('myOrders.cancel.title')} confirmLabel={t('myOrders.cancel.confirm')} busy={busy} error={error}
      disabled={!preview.allowed} onConfirm={cancel} onCancel={onClose} message={
        !preview.allowed ? <p>{t('myOrders.cancel.notAllowed', { hotline })}</p>
          : (
            <>
              <p>{t('myOrders.cancel.message')}</p>
              {!amounts.length && <p><strong>{t('myOrders.cancel.free')}</strong></p>}
              {amounts.length > 0 && (
                <dl className={s.facts}>
                  {amounts.map(key => <div key={key}><dt>{t(`myOrders.cancel.${key}`)}</dt><dd><strong>{money(preview[key], preview.currencyCode)}</strong></dd></div>)}
                </dl>
              )}
            </>
          )
      } />
  )
}
