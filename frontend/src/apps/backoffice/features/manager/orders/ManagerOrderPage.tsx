// Chi tiết đơn của quản lý: báo giá, thanh toán, giấy không kịp, hủy, quyết toán.
import { PrivateVehicleBadge } from '@shared/components/PrivateVehicleBadge'
import { useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import { PickupMissPanel } from '@shared/components/PickupMissPanel'
import { PickupAddOnPanel } from './PickupAddOnPanel'
import { ActionBar } from '@backoffice/shared/ActionBar'
import { BackButton } from '@backoffice/shared/BackButton'
import { BookingJourney } from '@shared/components/HorseJourney'
import { HAS_JOURNEY } from '@shared/components/orderSteps'
import { OrderStatusBadge } from '@shared/components/OrderStatusBadge'
import { PageTitle, PageTitleCode } from '@shared/ui/PageHeader'
import { Card } from '@shared/ui/Card'
import { DetailColumns } from '@shared/ui/DetailColumns'
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
import { CompleteOrderPanel, SettlementPanel } from './components/SettlementPanel'
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
          <BackButton to="/manager/orders" label={t('mgr.orders.back')} />
          <div className={s.row}><PageTitle><PageTitleCode>{booking.code}</PageTitleCode></PageTitle><OrderStatusBadge status={status} /><PrivateVehicleBadge mode={booking.transportMode} /></div>
          {booking.waybillNumber && <p>{t('mgr.orders.waybill', { code: booking.waybillNumber })}</p>}
        </div>
      </header>
      <DetailColumns wideInfo info={<StaffBookingInfo detail={data.detail} documents={data.documents} retry={revalidate} />} actions={<>
        {status && <ActionBar next={t(`mgr.nextStep.${status}`)} />}
        {data.detail.reviewReason && <p className={s.alert}>{t('mgr.orders.reviewReason')} {data.detail.reviewReason}</p>}
        <IntakePanel data={data} />
        {(status === 'QUOTING' || status === 'QUOTE_EXPIRED') && <QuotePanel data={data} actionsOnly />}
        {status === 'CONFIRMED' && <DocumentDelayPanel bookingId={data.id} />}
        {status === 'DELIVERED' && <CompleteOrderPanel data={data} />}
        {status && status !== 'PENDING_INTAKE' && <Card title={t('mgr.assignments.specialist')}>
          <div className={s.row}>
            <span>{booking.specialistName ?? t('mgr.assignments.unassigned')}</span>
            {!['COMPLETED', 'CANCELLED', 'REJECTED', 'QUOTE_EXPIRED'].includes(status) &&
              <ReassignSpecialist id={data.id} currentId={booking.specialistId} />}
          </div>
        </Card>}
        {canCancel(status) && <CancelPanel bookingId={data.id} />}
      </>}>
        <QuotePanel data={data} />
        <PaymentsPanel data={data} />
        {status && HAS_JOURNEY.includes(status) && <Card title={t('myOrders.journey')}><BookingJourney bookingId={data.id} /></Card>}
        <PickupMissPanel bookingId={data.id} mode="manager" renderAddOns={id => <PickupAddOnPanel horseId={id} />} />
        {status === 'IN_OPERATION' && <UnclaimedHorsePanel bookingId={data.id} onChange={revalidate} />}
        {(status === 'DELIVERED' || status === 'COMPLETED') && (
          <DeliveryComplaintPanel key={data.id} bookingId={data.id} horses={data.detail.horses ?? []} onChange={revalidate} />
        )}
        {canSettle(status) && <SettlementPanel data={data} />}
      </DetailColumns>
    </div>
  )
}
