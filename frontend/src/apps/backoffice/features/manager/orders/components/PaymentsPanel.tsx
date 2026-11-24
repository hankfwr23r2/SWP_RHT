import { useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import { Card } from '@shared/ui/Card'
import { ErrorState } from '@shared/ui/ErrorState'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { money, moment } from '@backoffice/shared/staffFormat'
import type { OrderData } from '../loaders'
import s from '@backoffice/shared/StaffPanels.module.css'

const TONE = { PENDING: 'warning', PAID: 'success', FAILED: 'danger', CANCELLED: 'neutral' } as const

export function PaymentsPanel({ data }: { data: OrderData }) {
  const { t } = useTranslation()
  const { revalidate, state } = useRevalidator()
  if (!data.payments) return <Card title={t('mgr.payments.title')}><ErrorState onRetry={revalidate} retrying={state !== 'idle'} /></Card>
  if (!data.payments.length) return null
  return (
    <Card title={t('mgr.payments.title')}>
      <ul className={s.items}>
        {data.payments.map(payment => (
          <li key={payment.id} className={s.item}>
            <div>
              <strong>{payment.type && t(`myOrders.payments.type.${payment.type}`)}</strong>
              {payment.paidAt && <p className={s.hint}>{t('myOrders.payments.paidAt', { at: moment(payment.paidAt) })}</p>}
              {payment.status === 'PENDING' && payment.dueAt && <p className={s.hint}>{t('myOrders.payments.dueAt', { at: moment(payment.dueAt) })}</p>}
              {payment.status === 'PENDING' && payment.confirmationUntil && <p className={s.hint}>{t('myOrders.payments.confirming', { at: moment(payment.confirmationUntil) })}</p>}
            </div>
            <span className={s.amount}>{money(payment.amount, payment.currencyCode)}</span>
            {payment.status && <StatusBadge tone={TONE[payment.status]} label={t(`myOrders.payments.status.${payment.status}`)} />}
          </li>
        ))}
      </ul>
    </Card>
  )
}
