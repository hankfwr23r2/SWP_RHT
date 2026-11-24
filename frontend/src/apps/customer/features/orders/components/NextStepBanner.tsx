import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { moment } from '../format'
import type { OrderData } from '../loaders'
import { currentQuotation, nextStepOf, payable } from '../orderSteps'
import s from '../Orders.module.css'

// Việc tiếp theo: khách phải làm gì hoặc đang chờ ai
export function NextStepBanner({ data }: { data: OrderData }) {
  const { t } = useTranslation()
  const next = nextStepOf({ ...data, status: data.detail?.booking?.status })
  const deadline = next.anchor === 'quote' ? currentQuotation(data.quotations)?.expiresAt
    : next.anchor === 'payments' ? payable(data.payments).find(payment => payment.dueAt)?.dueAt : undefined
  const go = () => document.getElementById(next.anchor!)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  return (
    <section className={`${s.banner} ${next.action ? s.bannerAction : ''}`} aria-label={t('myOrders.nextStep')}>
      <h2>{t(`myOrders.next.${next.key}.title`)}</h2>
      <p>{t(`myOrders.next.${next.key}.text`, next.values)}</p>
      {data.detail?.reviewReason && <p><strong>{t('myOrders.reason')}</strong> {data.detail.reviewReason}</p>}
      {deadline && <p><strong>{t('myOrders.deadline', { at: moment(deadline) })}</strong></p>}
      {next.action && next.anchor && <div><Button variant="primary" onClick={go}>{t(`myOrders.next.${next.key}.cta`)}</Button></div>}
    </section>
  )
}
