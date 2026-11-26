import { useTranslation } from 'react-i18next'
import type { BookingDetail } from '@shared/api/bookingOrders'
import { formatDate } from '@shared/lib/format'
import { Card } from '@shared/ui/Card'
import s from '../Review.module.css'

export function OrderFacts({ detail }: { detail: BookingDetail }) {
  const { t } = useTranslation()
  const { booking, addresses } = detail
  if (!booking) return null

  return <Card title={t('staffReview.trip')}>
    <dl className={s.facts}>
      {detail.routeName && <div><dt>{t('listPanel.route')}</dt><dd>{detail.routeName}</dd></div>}
      {booking.departureId != null && <div>
        <dt>{t('staffReview.workspace.departure', { id: booking.departureId })}</dt><dd>{detail.departureDate ? formatDate(detail.departureDate) : '—'}</dd>
      </div>}
      {booking.bookingType && <div><dt>{t('staffReview.type')}</dt><dd>{t(`bookingForm.types.${booking.bookingType}`)}</dd></div>}
      {booking.requestedPickupDate && <div><dt>{t('staffReview.requestedPickup')}</dt><dd>{formatDate(booking.requestedPickupDate)}</dd></div>}
      {booking.waybillNumber && <div><dt>{t('staffReview.waybill')}</dt><dd>{booking.waybillNumber}</dd></div>}
      {addresses?.map(address => {
        const pickup = address.addressType === 'PICKUP'
        const method = pickup ? detail.pickupMethod : detail.deliveryMethod
        return <div key={address.id ?? address.addressType}>
          <dt>{t(pickup ? 'bookingForm.route.pickup' : 'bookingForm.route.delivery')}</dt>
          <dd>{[address.addressLine, address.countryCode].filter(Boolean).join(' · ')}</dd>
          {method && <dd>{t(`staffReview.method.${method}`)}</dd>}
          {(address.contactName || address.contactPhone) && <dd>{[address.contactName, address.contactPhone].filter(Boolean).join(' · ')}</dd>}
        </div>
      })}
    </dl>
  </Card>
}
