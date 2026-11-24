import { useTranslation } from 'react-i18next'
import type { BookingDetail } from '@shared/api/bookingOrders'
import type { StallType } from '@shared/api/catalog'
import { Card } from '@shared/ui/Card'
import { EmptyState } from '@shared/ui/EmptyState'
import { showDate } from '../reviewDisplay'
import s from '../Review.module.css'

export function OrderFacts({ detail, stallTypes }: { detail: BookingDetail; stallTypes?: StallType[] }) {
  const { t, i18n } = useTranslation()
  const { booking, addresses, horses, parties } = detail
  if (!booking) return null

  return <>
    <Card title={t('staffReview.trip')}>
      <dl className={s.facts}>
        {booking.bookingType && <div><dt>{t('staffReview.type')}</dt><dd>{t(`bookingForm.types.${booking.bookingType}`)}</dd></div>}
        {booking.requestedPickupDate && <div><dt>{t('staffReview.requestedPickup')}</dt><dd>{showDate(booking.requestedPickupDate, i18n.language)}</dd></div>}
        {booking.waybillNumber && <div><dt>{t('staffReview.waybill')}</dt><dd>{booking.waybillNumber}</dd></div>}
        {addresses?.map(address => {
          const method = address.addressType === 'PICKUP' ? detail.pickupMethod : detail.deliveryMethod
          return <div key={address.id ?? address.addressType}>
            <dt>{t(address.addressType === 'PICKUP' ? 'bookingForm.route.pickup' : 'bookingForm.route.delivery')}</dt>
            <dd>{[address.addressLine, address.countryCode].filter(Boolean).join(' · ')}</dd>
            {method && <dd>{t(`staffReview.method.${method}`)}</dd>}
            {(address.contactName || address.contactPhone) && <dd>{[address.contactName, address.contactPhone].filter(Boolean).join(' · ')}</dd>}
            {address.accessNote && <dd>{address.accessNote}</dd>}
          </div>
        })}
      </dl>
    </Card>
    <Card title={t('staffReview.parties')}>
      {parties?.length ? <dl className={s.facts}>{parties.map((party, index) => <div key={index}>
        <dt>{party.bookingRole && t(`bookingForm.parties.${party.bookingRole}`)}</dt>
        <dd>{[party.fullName, party.phone, party.address].filter(Boolean).join(' · ')}</dd>
      </div>)}</dl> : <EmptyState title={t('staffReview.noParties')} description={t('staffReview.verifyOrder')} />}
    </Card>
    <Card title={t('staffReview.horses')}>
      {horses?.length ? <dl className={s.facts}>{horses.map(horse => <div key={horse.id}>
        <dt><strong>{horse.horseName}</strong>{horse.status && ` · ${t(`myOrders.horseStatus.${horse.status}`)}`}</dt>
        <dd>{stallTypes?.find(type => type.code === horse.stallTypeCode)?.name}</dd>
        {horse.dietNote && <dd>{horse.dietNote}</dd>}
        {!!horse.addOns?.length && <dd>{t('staffReview.addOns')}
          <ul className={s.list}>{horse.addOns.map(addOn => <li key={addOn.serviceId}>{addOn.serviceName}
            {addOn.quantity != null && ` × ${addOn.quantity}`}</li>)}</ul>
        </dd>}
      </div>)}</dl> : <EmptyState title={t('staffReview.noHorses')} description={t('staffReview.verifyOrder')} />}
    </Card>
  </>
}
