import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { bookingOrdersApi, type BookingCancellation } from '@shared/api/bookingOrders'
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

interface Props {
  bookingId: number
  refund?: {
    preview: () => Promise<BookingCancellation>
    submit: (reason: string) => Promise<unknown>
  }
}

export function CancelOrder({ bookingId, refund }: Props) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  return (
    <>
      <Button variant="ghost" onClick={() => setOpen(true)}>{t(refund ? 'myOrders.delay.refundOpen' : 'myOrders.cancel.open')}</Button>
      {open && <CancelDialog bookingId={bookingId} refund={refund} onClose={() => setOpen(false)} />}
    </>
  )
}

// Xem trước tiền mất và tiền hoàn rồi mới cho hủy
function CancelDialog({ bookingId, refund, onClose }: Props & { onClose: () => void }) {
  const { t } = useTranslation()
  const { run, busy, error } = useOrderAction()
  const { hotline } = useContact()
  const title = t(refund ? 'myOrders.delay.refundTitle' : 'myOrders.cancel.title')
  const { data: preview, loaded, error: loadError, reload } = useLoad(
    () => refund ? refund.preview() : bookingOrdersApi.cancellationPreview(bookingId), [bookingId, !!refund], { toastOnError: false })

  if (!loaded || loadError || !preview) {
    return <Modal title={title} onClose={onClose}>
      {loadError ? <ErrorState message={loadError.message} onRetry={reload} /> : <Skeleton lines={3} />}
    </Modal>
  }
  const cancel = async (reason: string) => {
    const success = await run(() => refund ? refund.submit(reason) : bookingOrdersApi.cancel(bookingId, reason),
      t(refund ? 'myOrders.delay.refundDone' : 'myOrders.cancel.done'))
    if (success) onClose()
  }
  const amounts = (['refundable', 'reviewable', 'forfeited'] as const).filter(key => preview[key])
  return (
    <ReasonDialog title={title} confirmLabel={t(refund ? 'myOrders.delay.refundOpen' : 'myOrders.cancel.confirm')} busy={busy} error={error}
      disabled={!preview.allowed} onConfirm={cancel} onCancel={onClose} message={
        !preview.allowed ? <p>{t('myOrders.cancel.notAllowed', { hotline })}</p>
          : (
            <>
              <p>{t(refund ? 'myOrders.delay.refundMessage' : 'myOrders.cancel.message')}</p>
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
