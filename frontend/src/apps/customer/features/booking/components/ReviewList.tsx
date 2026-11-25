import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { Card } from '@shared/ui/Card'
import type { BookingDraft } from '../draft'
import { addressText, findAddress, useBookingData } from '../loaders'
import { bookingTypeOf, selectedDeliveryAddress, showDate, showDateTime } from '../model'
import s from '../Booking.module.css'

// Xem lại hành trình và từng con ngựa, có đường quay lại bước cần sửa
export function ReviewList({ draft }: { draft: BookingDraft }) {
  const { t } = useTranslation()
  const data = useBookingData()
  const pickup = findAddress(data, draft.pickupAddressId)
  const delivery = findAddress(data, draft.deliveryAddressId)
  const type = bookingTypeOf(pickup, delivery)
  const leaves = draft.departure?.plannedDepartureAt

  return (
    <>
      <Card title={t('bookingForm.review.route')} actions={<Link to="/booking/route" className={s.link}>{t('common.edit')}</Link>}>
        <dl className={s.review}>
          {type && <div><dt>{t('bookingForm.review.type')}</dt><dd>{t(`bookingForm.types.${type}`)}</dd></div>}
          <div><dt>{t('bookingForm.route.pickup')}</dt><dd>{pickup && addressText(data, pickup)}<br />{t(`bookingForm.methods.pickup.${draft.pickupMethod}`)}</dd></div>
          <div><dt>{t('bookingForm.route.delivery')}</dt><dd>{delivery && addressText(data, delivery)}<br />{t(`bookingForm.methods.delivery.${draft.deliveryMethod}`)}</dd></div>
          <div><dt>{t('bookingForm.summary.pickupDate')}</dt><dd>{showDate(draft.pickupDate)}</dd></div>
          {leaves && <div><dt>{t('bookingForm.summary.departure')}</dt><dd>{showDateTime(leaves)} · {draft.departure?.routeName}</dd></div>}
        </dl>
      </Card>
      <Card title={t('bookingForm.review.horses')} actions={<Link to="/booking/horses" className={s.link}>{t('common.edit')}</Link>}>
        <dl className={s.review}>
          {draft.horseIds.map(id => {
            const choice = draft.horses[id]
            const destination = findAddress(data, selectedDeliveryAddress(draft, id) ?? draft.deliveryAddressId)
            const contact = destination?.id == null || destination.id === draft.deliveryAddressId
              ? draft.parties.CONSIGNEE : draft.deliveryContacts[destination.id]
            const addOns = data.services.filter(service => choice?.addOnServiceIds.includes(service.id ?? 0)).map(service => service.name)
            return (
              <div key={id}>
                <dt>{data.horses.find(horse => horse.id === id)?.name}</dt>
                <dd>{[
                  data.stallTypes.find(stall => stall.code === choice?.stallTypeCode)?.name,
                  choice?.coverage && t(`bookingForm.services.coverage.${choice.coverage}`),
                  destination && addressText(data, destination),
                  contact?.fullName,
                  contact?.phone,
                  ...addOns,
                ].filter(Boolean).join(' · ')}</dd>
              </div>
            )
          })}
        </dl>
      </Card>
    </>
  )
}
