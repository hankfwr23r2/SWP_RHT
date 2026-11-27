import { useTranslation } from 'react-i18next'
import type { BookingDetail } from '@shared/api/bookingOrders'
import { Card } from '@shared/ui/Card'
import type { BookingDocument } from '@shared/api/bookingDocuments'
import { formatDate, formatDateTime, formatVND } from '@shared/lib/format'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { FilePreviewButton } from '@shared/ui/FilePreviewButton'
import { Badge } from '@shared/ui/Badge'
import s from './StaffPanels.module.css'

const ROLES = ['SHIPPER', 'CONSIGNEE', 'PAYER', 'EMERGENCY_CONTACT'] as const

// Thông tin đơn để nhân viên đọc: hành trình, ngựa, các bên
export function StaffBookingInfo({ detail, documents, retry }: {
  detail: BookingDetail
  documents?: BookingDocument[]
  retry: () => void
}) {
  const { t } = useTranslation()
  const booking = detail.booking ?? {}
  return (
    <>
      <Card title={t('mgr.orders.customer')}>
        <dl className={s.facts}>
          <div><dt>{t('mgr.orders.customer')}</dt><dd>{detail.customerName ?? '—'}</dd></div>
          <div><dt>{t('mgr.info.phone')}</dt><dd>{detail.customerPhone ?? '—'}</dd></div>
        </dl>
      </Card>
      <Card title={t('mgr.info.trip')}>
        <dl className={s.facts}>
          <div><dt>{t('mgr.orders.route')}</dt><dd>{detail.routeName ?? t(booking.status === 'SCHEDULE_CHANGE_REQUIRED' ? 'mgr.info.noRoute' : 'mgr.info.direct')}</dd></div>
          {detail.departureDate && <div><dt>{t('mgr.orders.departure')}</dt><dd>{formatDate(detail.departureDate)}</dd></div>}
          {booking.bookingType && <div><dt>{t('mgr.info.type')}</dt><dd>{t(`bookingForm.types.${booking.bookingType}`)}</dd></div>}
          {booking.requestedPickupDate && <div><dt>{t('mgr.info.pickup')}</dt><dd>{formatDate(booking.requestedPickupDate)}</dd></div>}
          {booking.createdAt && <div><dt>{t('mgr.info.created')}</dt><dd>{formatDateTime(booking.createdAt)}</dd></div>}
          {detail.addresses?.map(address => {
            const type = address.addressType
            const method = type === 'PICKUP' ? detail.pickupMethod : detail.deliveryMethod
            return address && (
              <div key={address.id}>
                <dt>{t(`mgr.info.${type}`)}</dt>
                <dd>{address.addressLine}<br />{address.contactName} · {address.contactPhone}
                  {method && <><br />{t(`bookingForm.methods.${type === 'PICKUP' ? 'pickup' : 'delivery'}.${method}`)}</>}
                  {address.accessNote && <><br />{address.accessNote}</>}</dd>
              </div>
            )
          })}
          {booking.emergencySpendingLimit != null && <div><dt>{t('mgr.info.limit')}</dt><dd>{formatVND(booking.emergencySpendingLimit)}</dd></div>}
        </dl>
      </Card>
      <Card title={t('mgr.info.horses')}>
        <dl className={s.facts}>
          {detail.horses?.map(horse => (
            <div key={horse.id}>
              <dt><strong>{horse.horseName}</strong></dt>
              <dd>{[
                horse.microchip && t('mgr.info.microchip', { code: horse.microchip }),
                horse.horseTypeName,
                horse.stallTypeCode && t(`mgr.info.stall.${horse.stallTypeCode}`, horse.stallTypeCode),
                horse.insurance?.coverageOption && t(`bookingForm.services.coverage.${horse.insurance.coverageOption}`),
                detail.addresses?.find(item => item.id === horse.deliveryAddressId)?.addressLine,
                ...(horse.addOns ?? []).map(addOn => addOn.quantity && addOn.quantity > 1 ? `${addOn.serviceName} × ${addOn.quantity}` : addOn.serviceName),
              ].filter(Boolean).join(' · ')}</dd>
              {horse.dietNote && <dd>{horse.dietNote}</dd>}
              {horse.insurance?.insurerName && <dd>{[horse.insurance.insurerName, horse.insurance.policyNumber].filter(Boolean).join(' · ')}</dd>}
              {horse.insurance?.validFrom && horse.insurance.validTo &&
                <dd>{formatDate(horse.insurance.validFrom)} – {formatDate(horse.insurance.validTo)}</dd>}
            </div>
          ))}
        </dl>
      </Card>
      <Card title={t('mgr.info.documents')}>
        {documents === undefined ? <ErrorState onRetry={retry} /> : documents.length === 0
          ? <EmptyState compact title={t('mgr.info.noDocuments')} description={t('mgr.info.noDocumentsHint')} />
          : <ul className={s.stack}>{documents.map(document => (
            <li key={document.id} className={s.row}>
              {document.fileUrl ? <>
                <span>{document.fileName ?? document.typeCode}</span>
                <FilePreviewButton fileName={document.fileName ?? document.typeCode ?? ''} fileUrl={document.fileUrl}
                  label={t('ui.previewFile')} />
              </>
                : <span>{document.fileName ?? document.typeCode}</span>}
              {document.status && <Badge>{t(`mgr.info.documentStatus.${document.status}`)}</Badge>}
            </li>
          ))}</ul>}
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
