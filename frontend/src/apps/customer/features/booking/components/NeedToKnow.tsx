// Những điều khách cần biết trước khi gửi đơn, con số lấy từ quy định của công ty. Chưa tải được con số thì ẩn cả khối.
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useTransportRules } from '@shared/hooks/useTransportRules'
import { Card } from '@shared/ui/Card'
import type { BookingDraft } from '../draft'
import s from '../Booking.module.css'

export function NeedToKnow({ draft }: { draft: BookingDraft }) {
  const { t } = useTranslation()
  const rules = useTransportRules()
  if (!rules) return null
  const linehaul = draft.routing === 'LINEHAUL'
  const lines = linehaul
    ? [
      `methodRules.pickup.${draft.pickupMethod}`,
      `methodRules.delivery.${draft.deliveryMethod}`,
      'needToKnow.balance',
      'needToKnow.decision',
    ]
    : ['needToKnow.freeWait', 'needToKnow.balance']
  return (
    <Card title={t('bookingForm.needToKnow.title')}>
      <ul className={s.docs}>
        {lines.map(line => <li key={line}>{t(`bookingForm.${line}`, { ...rules })}</li>)}
      </ul>
      <p className={s.hint}>{t('bookingForm.needToKnow.more')} <Link to="/terms" className={s.link}>{t('bookingForm.needToKnow.terms')}</Link></p>
    </Card>
  )
}
