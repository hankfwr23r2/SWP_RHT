import { Link, useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import type { BookingDetail } from '@shared/api/bookingOrders'
import { OrderStatusBadge } from '@shared/components/OrderStatusBadge'
import { usePageData } from '@shared/routing/pageData'
import { Card } from '@shared/ui/Card'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Skeleton } from '@shared/ui/Skeleton'
import { ActionButtons } from './components/ActionButtons'
import { DocumentsCard } from './components/DocumentsCard'
import { InsurancesCard } from './components/InsurancesCard'
import { OrderFacts } from './components/OrderFacts'
import type { reviewDetailLoader } from './loaders'
import { useReviewActions, type ReviewAction } from './useReviewActions'
import s from './Review.module.css'

function ReviewDetail({ id, detail, data }: {
  id: number; detail: BookingDetail; data: ReturnType<typeof usePageData<typeof reviewDetailLoader>>
}) {
  const { t } = useTranslation()
  const { state } = useRevalidator()
  const actions = useReviewActions(id)
  const { booking, reviewReason } = detail
  const status = booking?.status
  const options: ReviewAction[] = []
  if (status === 'PENDING_INTAKE') options.push(actions.intake(), actions.reject())
  if (status === 'UNDER_REVIEW') options.push(actions.returnForResubmission(), actions.reject(), actions.startQuoting())
  return <>
    <div className={s.row}>
      <h1>{t('staffReview.order', { code: booking?.code })}</h1>
      <OrderStatusBadge status={status} />
    </div>
    {reviewReason && <p>{t('staffReview.reviewReason', { reason: reviewReason })}</p>}
    <ActionButtons options={options} actions={actions} />
    {(!data.insurers || !data.stallTypes) && <ErrorState onRetry={data.reload} retrying={state !== 'idle'} />}
    <OrderFacts detail={detail} stallTypes={data.stallTypes} />
    {data.checklist
      ? <DocumentsCard checklist={data.checklist} horses={detail.horses} status={status} actions={actions} />
      : <Card title={t('staffReview.documents')}><ErrorState onRetry={data.reload} retrying={state !== 'idle'} /></Card>}
    <InsurancesCard horses={detail.horses} status={status} insurers={data.insurers} actions={actions} />
  </>
}

export default function ReviewDetailPage() {
  const { t } = useTranslation()
  const data = usePageData<typeof reviewDetailLoader>()
  const { state } = useRevalidator()
  const id = data.detail?.booking?.id
  return <div className={s.stack}>
    <Link to="/specialist/review">{t('staffReview.back')}</Link>
    {data.detail && id != null ? <ReviewDetail key={id} id={id} detail={data.detail} data={data} /> : <>
      <h1>{t('staffReview.detailTitle')}</h1>
      {state !== 'idle' ? <Skeleton lines={4} /> : data.errors.length ? <ErrorState onRetry={data.reload} retrying={state !== 'idle'} />
        : <EmptyState title={t('staffReview.notFound')} description={t('staffReview.checkOrderLink')} />}
    </>}
  </div>
}
