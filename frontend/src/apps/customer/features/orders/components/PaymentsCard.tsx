import { useState } from 'react'
import { useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import { bookingPaymentsApi, type BookingPayment } from '@shared/api/payments'
import { useToast } from '@shared/ui/toast'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ErrorState } from '@shared/ui/ErrorState'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { money, moment } from '../format'
import type { OrderData } from '../loaders'
import { ReasonDialog } from './ReasonDialog'
import { useOrderAction } from '../useOrderAction'
import s from '../Orders.module.css'

const TONE = { PENDING: 'warning', PAID: 'success', FAILED: 'danger', CANCELLED: 'neutral' } as const

export function PaymentsCard({ data }: { data: OrderData }) {
  const { t } = useTranslation()
  const toast = useToast()
  const { revalidate, state } = useRevalidator()
  const [opening, setOpening] = useState<number>()

  if (!data.payments) return <Card title={t('myOrders.payments.title')}><ErrorState onRetry={revalidate} retrying={state !== 'idle'} /></Card>
  if (!data.payments.length) return null

  // Chuyển sang cổng VNPAY; lỗi thì tải lại vì khoản thu có thể đã đổi
  const pay = async (id: number) => {
    setOpening(id)
    try {
      const { paymentUrl } = await bookingPaymentsApi.checkout(id)
      if (paymentUrl) window.location.assign(paymentUrl)
    } catch (error) {
      toast(error instanceof Error ? error.message : t('myOrders.payments.openError'), 'error')
      setOpening(undefined)
      await revalidate()
    }
  }

  return (
    <Card title={t('myOrders.payments.title')}>
      <ul className={s.items}>
        {data.payments.map(payment => (
          <PaymentRow key={payment.id} payment={payment} opening={opening} onPay={pay} />
        ))}
      </ul>
    </Card>
  )
}

function PaymentRow({ payment, opening, onPay }: { payment: BookingPayment; opening?: number; onPay: (id: number) => void }) {
  const { t } = useTranslation()
  const { run, busy, error } = useOrderAction()
  const [disputing, setDisputing] = useState(false)
  const pending = payment.status === 'PENDING' && payment.type !== 'REFUND'
  const canDispute = pending && payment.type === 'EXTRA_CHARGE' && !payment.disputedAt
  return (
    <li className={s.item}>
      <div>
        <strong>{payment.type && t(`myOrders.payments.type.${payment.type}`)}</strong>
        {payment.paidAt && <p className={s.hint}>{t('myOrders.payments.paidAt', { at: moment(payment.paidAt) })}</p>}
        {pending && payment.dueAt && <p className={s.hint}>{t('myOrders.payments.dueAt', { at: moment(payment.dueAt) })}</p>}
        {pending && payment.confirmationUntil && <p className={s.hint}>{t('myOrders.payments.confirming', { at: moment(payment.confirmationUntil) })}</p>}
        {payment.disputedAt && !payment.disputeDecision && <p className={s.hint}>{t('myOrders.payments.disputePending')}</p>}
        {payment.disputeDecision && <p className={s.hint}>{t(`myOrders.payments.dispute.${payment.disputeDecision}`, { response: payment.disputeResponse })}</p>}
        {payment.type === 'REFUND' && payment.status === 'PENDING' && <p className={s.hint}>{t('myOrders.payments.refundPending')}</p>}
      </div>
      <span className={s.amount}>{money(payment.amount, payment.currencyCode)}</span>
      {payment.status && <StatusBadge tone={TONE[payment.status]} label={t(`myOrders.payments.status.${payment.status}`)} />}
      {pending && payment.id != null && (
        <Button variant="primary" loading={opening === payment.id} disabled={opening != null} onClick={() => onPay(payment.id!)}>{t('myOrders.payments.pay')}</Button>
      )}
      {canDispute && <Button onClick={() => setDisputing(true)}>{t('myOrders.payments.disputeButton')}</Button>}
      {disputing && payment.id != null && (
        <ReasonDialog title={t('myOrders.payments.disputeButton')} message={<p className={s.hint}>{t('myOrders.payments.disputeHint')}</p>}
          confirmLabel={t('myOrders.payments.disputeButton')} busy={busy} error={error} onCancel={() => setDisputing(false)}
          onConfirm={async reason => {
            if (await run(() => bookingPaymentsApi.dispute(payment.id!, reason), t('myOrders.payments.disputeSent'))) setDisputing(false)
          }} />
      )}
    </li>
  )
}
