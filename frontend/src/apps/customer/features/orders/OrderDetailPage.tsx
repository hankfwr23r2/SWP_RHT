// Chi tiết đơn của khách: việc tiếp theo, báo giá và thanh toán, giấy tờ, hành trình, đổi chuyến, hủy, đánh giá.
import { useState } from 'react'
import { Button } from '@shared/ui/Button'
import { DeliveryChangeDialog } from './components/DeliveryChangeDialog'
import { Link, useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import { PickupMissPanel } from '@shared/components/PickupMissPanel'
import { BookingJourney } from '@shared/components/HorseJourney'
import { OrderStatusBadge } from '@shared/components/OrderStatusBadge'
import { CANCELLABLE, HAS_JOURNEY, RATEABLE } from '@shared/components/orderSteps'
import { Card } from '@shared/ui/Card'
import { ErrorState } from '@shared/ui/ErrorState'
import { formatDateTime } from '@shared/lib/format'
import { BookingDelayCard } from './components/BookingDelayCard'
import { CancelOrder } from './components/CancelOrder'
import { CollectionCodeCard } from './components/CollectionCodeCard'
import { DeliveryComplaintCard } from './components/DeliveryComplaintCard'
import { DocumentsCard } from './components/DocumentsCard'
import { IncidentDecisionsCard } from './components/IncidentDecisionsCard'
import { InsuranceCard } from './components/InsuranceCard'
import { NextStepBanner } from './components/NextStepBanner'
import { OrderInfo } from './components/OrderInfo'
import { PaymentsCard } from './components/PaymentsCard'
import { QuoteCard } from './components/QuoteCard'
import { RatingCard } from './components/RatingCard'
import { StablingClockCard } from './components/StablingClockCard'
import { RescheduleCard } from './components/RescheduleCard'
import { useOrderData } from './loaders'
import { useBookingDelay } from './useBookingDelay'
import s from './Orders.module.css'

// Các lý do khách cần biết khi đơn vốn đổi được nơi giao; loại đơn không áp dụng thì không nhắc tới
const SHOWN_REASONS = ['BALANCE_PAID', 'DELIVERY_PLANNED', 'LIMIT']

export default function OrderDetailPage() {
  const { t } = useTranslation()
  const data = useOrderData()
  const { revalidate, state } = useRevalidator()
  const [changingDelivery, setChangingDelivery] = useState(false)
  const booking = data.detail?.booking
  const delay = useBookingDelay(data.id)
  const status = booking?.status

  if (!booking || !status) {
    return <div className="page"><div className="wrap"><ErrorState onRetry={revalidate} retrying={state !== 'idle'} /></div></div>
  }

  return (
    <div className="page"><div className="wrap">
      <div className="breadcrumb"><Link to="/orders">{t('myOrders.title')}</Link> / <span>{booking.code}</span></div>
      <header className={s.heading}>
        <div>
          <h1>{booking.code}</h1>
          {booking.waybillNumber && <p>{t('myOrders.waybill', { code: booking.waybillNumber })}</p>}
        </div>
        <OrderStatusBadge status={status} />
      </header>
      <div className={s.layout}>
        <div className={s.stack}>
          <NextStepBanner data={data} />
          <BookingDelayCard delay={delay.data} loaded={delay.loaded} error={delay.error?.message} onRetry={delay.reload} />
          <PickupMissPanel bookingId={data.id} mode="customer" onChanged={revalidate} />
          {(status === 'IN_OPERATION' || data.detail?.balanceHoldAt) && <StablingClockCard bookingId={data.id} onChanged={revalidate} />}
          {data.detail?.balanceHoldAt && (
            <Card title={t('myOrders.balanceHold.title')}>
              <p>{t('myOrders.balanceHold.since', { at: formatDateTime(new Date(data.detail.balanceHoldAt)) })}</p>
              <p>{t(status === 'CANCELLED' ? 'myOrders.balanceHold.cancelled' : 'myOrders.balanceHold.options')}</p>
            </Card>
          )}
          {(status === 'SCHEDULE_CHANGE_REQUIRED' || delay.data?.canRescheduleFree) && (
            <section id="reschedule" className={s.section}>
              <RescheduleCard data={data} excludeDepartureId={delay.data?.departureId}
                reschedule={delay.data?.canRescheduleFree ? async body => {
                  await delay.reschedule(body)
                  await delay.reload()
                } : undefined} />
            </section>
          )}
          <section id="quote" className={s.section}><QuoteCard data={data} /></section>
          <section id="payments" className={s.section}><PaymentsCard data={data} /></section>
          {HAS_JOURNEY.includes(status) && <IncidentDecisionsCard bookingId={data.id} />}
          <InsuranceCard data={data} />
          <section id="documents" className={s.section}><DocumentsCard data={data} /></section>
          {status === 'IN_OPERATION' && <CollectionCodeCard bookingId={data.id} />}
          {(status === 'DELIVERED' || status === 'COMPLETED') && (
            <DeliveryComplaintCard key={data.id} bookingId={data.id} horses={data.detail?.horses ?? []} />
          )}
          {(HAS_JOURNEY.includes(status) || data.detail?.balanceHoldAt)
            && <Card title={t('myOrders.journey')}><BookingJourney bookingId={data.id} /></Card>}
          {RATEABLE.includes(status) && data.detail?.rated === false && <section id="rating" className={s.section}><RatingCard bookingId={data.id} /></section>}
        </div>
        <aside className={s.side}>
          <OrderInfo data={data} />
          {data.detail?.canChangeDelivery && (
            <Button onClick={() => setChangingDelivery(true)}>{t('myOrders.deliveryChange.title')}</Button>
          )}
          {data.detail?.deliveryChangeDisabledReason && SHOWN_REASONS.includes(data.detail.deliveryChangeDisabledReason) && (
            <p className={s.hint}>{t(`myOrders.deliveryChange.reasons.${data.detail.deliveryChangeDisabledReason}`)}</p>
          )}
          {changingDelivery && data.detail && <DeliveryChangeDialog id={data.id} detail={data.detail}
            onClose={() => setChangingDelivery(false)} onChanged={revalidate} />}
          {!delay.data?.requiresManagerContact && (CANCELLABLE.includes(status) || delay.data?.canRefundFull) && (
            <CancelOrder bookingId={data.id} refund={delay.data?.canRefundFull ? {
              preview: delay.refundPreview,
              submit: async reason => { await delay.refund(reason); await delay.reload() },
            } : undefined} />
          )}
        </aside>
      </div>
    </div></div>
  )
}
