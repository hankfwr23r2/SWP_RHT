import { PrivateVehicleBadge } from '@shared/components/PrivateVehicleBadge'
import { useRevalidator } from 'react-router'
import { Trans, useTranslation } from 'react-i18next'
import type { BookingDetail } from '@shared/api/bookingOrders'
import { ActionBar } from '@backoffice/shared/ActionBar'
import { BackButton } from '@backoffice/shared/BackButton'
import { BookingJourney } from '@shared/components/HorseJourney'
import { HAS_JOURNEY } from '@shared/components/orderSteps'
import { OrderStatusBadge } from '@shared/components/OrderStatusBadge'
import { usePageData } from '@shared/routing/pageData'
import { PageTitle, PageTitleCode } from '@shared/ui/PageHeader'
import { Card } from '@shared/ui/Card'
import { DetailColumns } from '@shared/ui/DetailColumns'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Skeleton } from '@shared/ui/Skeleton'
import { ActionButtons } from './components/ActionButtons'
import { DocumentsCard } from './components/DocumentsCard'
import { InsurancesCard } from './components/InsurancesCard'
import { OrderFacts } from './components/OrderFacts'
import { reviewTodos } from './nextStep'
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
  if (detail.assignedToMe && status === 'UNDER_REVIEW') options.push(actions.returnForResubmission(), actions.reject(), actions.startQuoting())
  const todos = reviewTodos(detail, data.checklist)
  const work = [
    todos.docs && t('staffReview.todoDocs', { count: todos.docs }),
    todos.insurance && t('staffReview.todoInsurance', { count: todos.insurance }),
    todos.customer && t('staffReview.todoCustomer', { count: todos.customer }),
  ].filter(Boolean).join(' ')
  const next = status === 'UNDER_REVIEW' ? work || t('staffReview.ready') : status && t(`staffReview.nextStep.${status}`)
  return <>
    <div className={s.detailHeading}>
      <PageTitle><Trans i18nKey="staffReview.order" values={{ code: booking?.code }} components={{ orderCode: <PageTitleCode /> }} /></PageTitle>
      <OrderStatusBadge status={status} />
      <PrivateVehicleBadge mode={booking?.transportMode} />
    </div>
    <DetailColumns info={<OrderFacts detail={detail} stallTypes={data.stallTypes} />} actions={<>
      {detail.assignedToMe && <ActionBar next={next}>{options.length > 0 && <ActionButtons options={options} actions={actions} />}</ActionBar>}
      <p>{booking?.specialistName ? t('staffReview.assigned', { name: booking.specialistName }) : t('staffReview.unassigned')}</p>
      {reviewReason && <p>{t('staffReview.reviewReason', { reason: reviewReason })}</p>}
    </>}>
      {status && HAS_JOURNEY.includes(status) && <Card title={t('myOrders.journey')}><BookingJourney bookingId={id} /></Card>}
      {(!data.insurers || !data.stallTypes || !data.documents) && <ErrorState onRetry={data.reload} retrying={state !== 'idle'} />}
      {data.checklist
        ? <DocumentsCard checklist={data.checklist} horses={detail.horses} status={detail.assignedToMe ? status : undefined} actions={actions} />
        : <Card title={t('staffReview.documents')}><ErrorState onRetry={data.reload} retrying={state !== 'idle'} /></Card>}
      <InsurancesCard horses={detail.horses} status={status} canEdit={detail.assignedToMe === true} insurers={data.insurers}
        documents={data.documents} actions={actions} />
    </DetailColumns>
  </>
}

export default function ReviewDetailPage() {
  const { t } = useTranslation()
  const data = usePageData<typeof reviewDetailLoader>()
  const { state } = useRevalidator()
  const id = data.detail?.booking?.id
  return <div className={s.stack}>
    <BackButton to="/specialist/review" label={t('staffReview.back')} />
    {data.detail && id != null ? <ReviewDetail key={id} id={id} detail={data.detail} data={data} /> : <>
      <h1>{t('staffReview.detailTitle')}</h1>
      {state !== 'idle' ? <Skeleton lines={4} /> : data.errors.length ? <ErrorState onRetry={data.reload} retrying={state !== 'idle'} />
        : <EmptyState title={t('staffReview.notFound')} description={t('staffReview.checkOrderLink')} />}
    </>}
  </div>
}
