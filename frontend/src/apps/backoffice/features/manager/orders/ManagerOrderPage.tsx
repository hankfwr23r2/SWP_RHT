// Chi tiết đơn của quản lý: báo giá, thanh toán, giấy không kịp, hủy, quyết toán.
import { useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import { PickupMissPanel } from '@shared/components/PickupMissPanel'
import { PickupAddOnPanel } from './PickupAddOnPanel'
import { ActionBar } from '@backoffice/shared/ActionBar'
import { BackButton } from '@backoffice/shared/BackButton'
import { OrderStatusBadge } from '@shared/components/OrderStatusBadge'
import { Button } from '@shared/ui/Button'
import { PageTitle, PageTitleCode } from '@shared/ui/PageHeader'
import { Card } from '@shared/ui/Card'
import { ErrorState } from '@shared/ui/ErrorState'
import { StaffBookingInfo } from '@backoffice/shared/StaffBookingInfo'
import { CancelPanel } from './components/CancelPanel'
import { DeliveryComplaintPanel } from './components/DeliveryComplaintPanel'
import { DocumentDelayPanel } from './components/DocumentDelayPanel'
import { ReassignSpecialist } from '@backoffice/shared/ReassignSpecialist'
import { IntakePanel } from './components/IntakePanel'
import { UnclaimedHorsePanel } from './components/UnclaimedHorsePanel'
import { PaymentsPanel } from './components/PaymentsPanel'
import { QuotePanel } from './components/QuotePanel'
import { SettlementPanel } from './components/SettlementPanel'
import { useOrderData } from './loaders'
import { canCancel, canSettle, taskOf } from './orderGroups'
import s from '@backoffice/shared/StaffPanels.module.css'

export default function ManagerOrderPage() {
  const { t } = useTranslation()
  const data = useOrderData()
  const { revalidate, state } = useRevalidator()
  const booking = data.detail?.booking
  if (!data.detail || !booking) return <ErrorState onRetry={revalidate} retrying={state !== 'idle'} />
  const status = booking.status
  const task = taskOf(status)
  const jump = () => document.getElementById(`order-${task}`)?.scrollIntoView({ block: 'start' })

  return (
    <div className={s.page}>
      <header className={s.heading}>
        <div>
          <BackButton to="/manager/orders" label={t('mgr.orders.back')} />
          <PageTitle><PageTitleCode>{booking.code}</PageTitleCode></PageTitle>
          {booking.waybillNumber && <p>{t('mgr.orders.waybill', { code: booking.waybillNumber })}</p>}
        </div>
        <OrderStatusBadge status={status} />
      </header>
      {status && <ActionBar next={t(`mgr.nextStep.${status}`)}>
        {task && <Button variant="primary" onClick={jump}>{t(`mgr.orders.tasks.${task}`)}</Button>}
      </ActionBar>}
      {data.detail.reviewReason && <p className={s.alert}>{t('mgr.orders.reviewReason')} {data.detail.reviewReason}</p>}
      <div className={s.layout}>
        <div className={s.stack}>
          <div id="order-intake" className={s.anchor}>
            <IntakePanel data={data} />
            {status && status !== 'PENDING_INTAKE' && <Card title={t('mgr.assignments.specialist')}>
              <div className={s.row}>
                <span>{booking.specialistName ?? t('mgr.assignments.unassigned')}</span>
                {!['COMPLETED', 'CANCELLED', 'REJECTED'].includes(status) &&
                  <ReassignSpecialist id={data.id} currentId={booking.specialistId} />}
              </div>
            </Card>}
          </div>
          <div id="order-quote" className={s.anchor}><QuotePanel data={data} /></div>
          <PaymentsPanel data={data} />
          <PickupMissPanel bookingId={data.id} mode="manager" renderAddOns={id => <PickupAddOnPanel horseId={id} />} />
          {status === 'IN_OPERATION' && <UnclaimedHorsePanel bookingId={data.id} onChange={revalidate} />}
          {status === 'CONFIRMED' && <DocumentDelayPanel bookingId={data.id} />}
          {(status === 'DELIVERED' || status === 'COMPLETED') && (
            <DeliveryComplaintPanel key={data.id} bookingId={data.id} horses={data.detail.horses ?? []} onChange={revalidate} />
          )}
          {canSettle(status) && <div id="order-settle" className={s.anchor}><SettlementPanel data={data} /></div>}
          {canCancel(status) && <CancelPanel bookingId={data.id} />}
        </div>
        <aside className={s.stack}><StaffBookingInfo detail={data.detail} /></aside>
      </div>
    </div>
  )
}
