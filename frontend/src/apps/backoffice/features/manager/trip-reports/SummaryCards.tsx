// Thẻ số liệu vận hành trong khoảng ngày đã chọn
import { useTranslation } from 'react-i18next'
import type { OperationsSummary } from '@shared/api/reports'
import { formatVND } from '@shared/lib/format'
import s from './TripReports.module.css'

export function SummaryCards({ summary }: { summary: OperationsSummary }) {
  const { t } = useTranslation()
  const percent = summary.onTimeRate == null ? '—' : `${Math.round(summary.onTimeRate * 100)}%`
  const cards = [
    { key: 'trips', value: summary.tripCount, hint: t('opsReport.completedOf', { count: summary.completedTripCount }) },
    { key: 'delivered', value: summary.horsesDelivered },
    { key: 'onTime', value: percent, hint: t('opsReport.lateOf', { late: summary.lateStopCount, arrived: summary.arrivedStopCount }) },
    { key: 'incidents', value: summary.incidentCount, hint: t('opsReport.openOf', { count: summary.openIncidentCount }) },
    { key: 'cost', value: formatVND(summary.costTotal ?? 0),
      hint: (summary.costsByPayer ?? []).map(c => `${t(`opsReport.payer.${c.key}`)} ${formatVND(c.amount ?? 0)}`).join(' · ') },
    { key: 'rating', value: summary.averageRating == null ? '—' : summary.averageRating.toFixed(1), hint: t('opsReport.ratingCount', { count: summary.ratingCount }) },
  ]
  return (
    <ul className={s.cards} aria-label={t('opsReport.summaryLabel')}>
      {cards.map(card => (
        <li key={card.key} className={s.card}>
          <span className={s.label}>{t(`opsReport.card.${card.key}`)}</span>
          <span className={s.value}>{card.value}</span>
          {card.hint && <span className={s.hint}>{card.hint}</span>}
        </li>
      ))}
    </ul>
  )
}
