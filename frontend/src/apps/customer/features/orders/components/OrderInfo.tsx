import { useTranslation } from 'react-i18next'
import { Card } from '@shared/ui/Card'
import { day, money, showDate } from '../format'
import type { OrderData } from '../loaders'
import s from '../Orders.module.css'

const ROLES = ['SHIPPER', 'CONSIGNEE', 'PAYER', 'EMERGENCY_CONTACT'] as const

export function OrderInfo({ data }: { data: OrderData }) {
  const { t } = useTranslation()
  const { detail } = data
  const booking = detail?.booking
  if (!detail || !booking) return null
  const address = (type: 'PICKUP' | 'DELIVERY') => detail.addresses?.find(item => item.addressType === type)
  const limit = booking.emergencySpendingLimit

  return (
    <>
      <Card title={t('myOrders.info.trip')}>
        <dl className={s.facts}>
          {booking.bookingType && <div><dt>{t('bookingForm.review.type')}</dt><dd>{t(`bookingForm.types.${booking.bookingType}`)}</dd></div>}
          {booking.requestedPickupDate && <div><dt>{t('bookingForm.summary.pickupDate')}</dt><dd>{showDate(booking.requestedPickupDate)}</dd></div>}
          <div><dt>{t('bookingForm.route.pickup')}</dt><dd>{address('PICKUP')?.addressLine}<br />{address('PICKUP')?.countryCode === 'VN' && detail.pickupMethod && t(`bookingForm.methods.pickup.${detail.pickupMethod}`)}</dd></div>
          <div><dt>{t('bookingForm.route.delivery')}</dt><dd>{address('DELIVERY')?.addressLine}<br />{address('DELIVERY')?.countryCode === 'VN' && detail.deliveryMethod && t(`bookingForm.methods.delivery.${detail.deliveryMethod}`)}</dd></div>
          {limit != null && <div><dt>{t('bookingForm.review.emergencyLimit')}</dt><dd>{money(limit)}</dd></div>}
          {booking.createdAt && <div><dt>{t('myOrders.info.created')}</dt><dd>{day(booking.createdAt)}</dd></div>}
        </dl>
      </Card>
      <Card title={t('bookingForm.review.horses')}>
        <dl className={s.facts}>
          {detail.horses?.map(horse => (
            <div key={horse.id}>
              <dt><strong>{horse.horseName}</strong>{horse.status && horse.status !== 'ACTIVE' && ` · ${t(`myOrders.horseStatus.${horse.status}`)}`}</dt>
              <dd>{[
                data.stallTypes?.find(stall => stall.code === horse.stallTypeCode)?.name,
                horse.insurance?.coverageOption && t(`bookingForm.services.coverage.${horse.insurance.coverageOption}`),
                ...(horse.addOns ?? []).map(addOn => addOn.serviceName),
              ].filter(Boolean).join(' · ')}</dd>
            </div>
          ))}
        </dl>
      </Card>
      <Card title={t('myOrders.info.people')}>
        <dl className={s.facts}>
          {ROLES.map(role => {
            const party = detail.parties?.find(item => item.bookingRole === role)
            return party && <div key={role}><dt>{t(`bookingForm.parties.${role}`)}</dt><dd>{party.fullName} · {party.phone}</dd></div>
          })}
        </dl>
      </Card>
    </>
  )
}
