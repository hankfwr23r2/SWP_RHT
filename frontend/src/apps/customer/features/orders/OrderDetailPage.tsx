// Chi tiết đơn của khách: việc tiếp theo, báo giá và thanh toán, giấy tờ, hành trình, đổi chuyến, hủy, đánh giá.
import { Link, useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import { BookingJourney } from '@shared/components/HorseJourney'
import { OrderStatusBadge } from '@shared/components/OrderStatusBadge'
import { Card } from '@shared/ui/Card'
import { ErrorState } from '@shared/ui/ErrorState'
import { CancelOrder } from './components/CancelOrder'
import { DocumentsCard } from './components/DocumentsCard'
import { InsuranceCard } from './components/InsuranceCard'
import { NextStepBanner } from './components/NextStepBanner'
import { OrderInfo } from './components/OrderInfo'
import { PaymentsCard } from './components/PaymentsCard'
import { QuoteCard } from './components/QuoteCard'
import { RatingCard } from './components/RatingCard'
import { RescheduleCard } from './components/RescheduleCard'
import { useOrderData } from './loaders'
import { CANCELLABLE, HAS_JOURNEY, RATEABLE } from './orderSteps'
import s from './Orders.module.css'

export default function OrderDetailPage() {
  const { t } = useTranslation()
  const data = useOrderData()
  const { revalidate, state } = useRevalidator()
  const booking = data.detail?.booking
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
          {status === 'SCHEDULE_CHANGE_REQUIRED' && <section id="reschedule" className={s.section}><RescheduleCard data={data} /></section>}
          <section id="quote" className={s.section}><QuoteCard data={data} /></section>
          <section id="payments" className={s.section}><PaymentsCard data={data} /></section>
          <InsuranceCard data={data} />
          <section id="documents" className={s.section}><DocumentsCard data={data} /></section>
          {HAS_JOURNEY.includes(status) && <Card title={t('myOrders.journey')}><BookingJourney bookingId={data.id} /></Card>}
          {RATEABLE.includes(status) && <section id="rating" className={s.section}><RatingCard bookingId={data.id} /></section>}
        </div>
        <aside className={s.side}>
          <OrderInfo data={data} />
          {CANCELLABLE.includes(status) && <CancelOrder bookingId={data.id} />}
        </aside>
      </div>
    </div></div>
  )
}
