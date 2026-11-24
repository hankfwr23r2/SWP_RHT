import { useTranslation } from 'react-i18next'
import type { BookingDetail } from '@shared/api/bookingOrders'
import { Card } from '@shared/ui/Card'
import { day, money, showDate } from './staffFormat'
import s from './StaffPanels.module.css'

const ROLES = ['SHIPPER', 'CONSIGNEE', 'PAYER', 'EMERGENCY_CONTACT'] as const
const PLACES = ['PICKUP', 'DELIVERY'] as const

// Thông tin đơn để nhân viên đọc: hành trình, ngựa, các bên
export function StaffBookingInfo({ detail }: { detail: BookingDetail }) {
  const { t } = useTranslation()
  const booking = detail.booking ?? {}
  return (
    <>
      <Card title={t('mgr.info.trip')}>
        <dl className={s.facts}>
          {booking.bookingType && <div><dt>{t('mgr.info.type')}</dt><dd>{t(`bookingForm.types.${booking.bookingType}`)}</dd></div>}
          {booking.requestedPickupDate && <div><dt>{t('mgr.info.pickup')}</dt><dd>{showDate(booking.requestedPickupDate)}</dd></div>}
          {booking.createdAt && <div><dt>{t('mgr.info.created')}</dt><dd>{day(booking.createdAt)}</dd></div>}
          {PLACES.map(type => {
            const address = detail.addresses?.find(item => item.addressType === type)
            const method = type === 'PICKUP' ? detail.pickupMethod : detail.deliveryMethod
            return address && (
              <div key={type}>
                <dt>{t(`mgr.info.${type}`)}</dt>
                <dd>{address.addressLine}<br />{address.contactName} · {address.contactPhone}
                  {address.countryCode === 'VN' && method && <><br />{t(`bookingForm.methods.${type === 'PICKUP' ? 'pickup' : 'delivery'}.${method}`)}</>}
                  {address.accessNote && <><br />{address.accessNote}</>}</dd>
              </div>
            )
          })}
          {booking.emergencySpendingLimit != null && <div><dt>{t('mgr.info.limit')}</dt><dd>{money(booking.emergencySpendingLimit)}</dd></div>}
        </dl>
      </Card>
      <Card title={t('mgr.info.horses')}>
        <dl className={s.facts}>
          {detail.horses?.map(horse => (
            <div key={horse.id}>
              <dt><strong>{horse.horseName}</strong></dt>
              <dd>{[
                horse.stallTypeCode && t(`mgr.info.stall.${horse.stallTypeCode}`, horse.stallTypeCode),
                horse.insurance?.coverageOption && t(`bookingForm.services.coverage.${horse.insurance.coverageOption}`),
                ...(horse.addOns ?? []).map(addOn => addOn.serviceName),
              ].filter(Boolean).join(' · ')}</dd>
            </div>
          ))}
        </dl>
      </Card>
      <Card title={t('mgr.info.people')}>
        <dl className={s.facts}>
          {ROLES.map(role => {
            const party = detail.parties?.find(item => item.bookingRole === role)
            return party && <div key={role}><dt>{t(`bookingForm.parties.${role}`)}</dt><dd>{party.fullName} · {party.phone}<br />{party.address}</dd></div>
          })}
        </dl>
      </Card>
    </>
  )
}
