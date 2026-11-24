// Chi tiết đơn của quản lý: báo giá, thanh toán, giấy không kịp, hủy, quyết toán.
import { Link, useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import { OrderStatusBadge } from '@shared/components/OrderStatusBadge'
import { ErrorState } from '@shared/ui/ErrorState'
import { StaffBookingInfo } from '@backoffice/shared/StaffBookingInfo'
import { CancelPanel } from './components/CancelPanel'
import { DocumentDelayPanel } from './components/DocumentDelayPanel'
import { IntakePanel } from './components/IntakePanel'
import { PaymentsPanel } from './components/PaymentsPanel'
import { QuotePanel } from './components/QuotePanel'
import { SettlementPanel } from './components/SettlementPanel'
import { useOrderData } from './loaders'
import { canCancel, canSettle } from './orderGroups'
import s from '@backoffice/shared/StaffPanels.module.css'

export default function ManagerOrderPage() {
  const { t } = useTranslation()
  const data = useOrderData()
  const { revalidate, state } = useRevalidator()
  const booking = data.detail?.booking
  if (!data.detail || !booking) return <ErrorState onRetry={revalidate} retrying={state !== 'idle'} />
  const status = booking.status

  return (
    <div className={s.page}>
      <header className={s.heading}>
        <div>
          <p><Link to="/manager/orders" className={s.link}>{t('mgr.orders.title')}</Link></p>
          <h1>{booking.code}</h1>
          {booking.waybillNumber && <p>{t('mgr.orders.waybill', { code: booking.waybillNumber })}</p>}
        </div>
        <OrderStatusBadge status={status} />
      </header>
      {data.detail.reviewReason && <p className={s.alert}>{t('mgr.orders.reviewReason')} {data.detail.reviewReason}</p>}
      <div className={s.layout}>
        <div className={s.stack}>
          <IntakePanel data={data} />
          <QuotePanel data={data} />
          <PaymentsPanel data={data} />
          {status === 'CONFIRMED' && <DocumentDelayPanel bookingId={data.id} />}
          {canSettle(status) && <SettlementPanel data={data} />}
          {canCancel(status) && <CancelPanel bookingId={data.id} />}
        </div>
        <aside className={s.stack}><StaffBookingInfo detail={data.detail} /></aside>
      </div>
    </div>
  )
}
