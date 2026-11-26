import { useState } from 'react'
import { Link, useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import type { BookingDetail } from '@shared/api/bookingOrders'
import { OrderStatusBadge } from '@shared/components/OrderStatusBadge'
import { formatDate } from '@shared/lib/format'
import { usePageData } from '@shared/routing/pageData'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { DetailColumns } from '@shared/ui/DetailColumns'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Skeleton } from '@shared/ui/Skeleton'
import { BackButton } from '@backoffice/shared/BackButton'
import { ActionButtons } from './components/ActionButtons'
import { DocumentsCard } from './components/DocumentsCard'
import { InsurancesCard } from './components/InsurancesCard'
import { OrderFacts } from './components/OrderFacts'
import type { reviewDetailLoader } from './loaders'
import { reviewTodos } from './nextStep'
import { documentsFor } from './reviewDisplay'
import { ResubmissionDialog } from './ResubmissionDialog'
import type { SpecialistSection } from './SpecialistQueuePage'
import { useReviewActions } from './useReviewActions'
import { VerifyHorses } from './VerifyHorses'
import s from './Review.module.css'

type LoadedDetail = ReturnType<typeof usePageData<typeof reviewDetailLoader>>

function SpecialistDetail({ section, id, detail, data }: {
  section: SpecialistSection; id: number; detail: BookingDetail; data: LoadedDetail
}) {
  const { t } = useTranslation()
  const actions = useReviewActions(id)
  const [checked, setChecked] = useState<number[]>([])
  const [requesting, setRequesting] = useState(false)
  const status = detail.booking?.status
  const canEdit = detail.assignedToMe === true
  const canReview = canEdit && status === 'UNDER_REVIEW'
  const horses = (detail.horses ?? []).filter(horse => horse.status === 'ACTIVE')
  const customer = (data.checklist?.items ?? []).filter(item => item.submitterType === 'CUSTOMER')
  const legal = (data.checklist?.items ?? []).filter(item => item.submitterType !== 'CUSTOMER' || item.requirement?.gateLocationId != null)
  const documents = data.checklist?.documents ?? []
  const submitted = legal.filter(item => documentsFor(item, documents, horses.find(h => h.id === item.bookingHorseId)?.horseId)
    .some(document => document.status === 'UPLOADED' || document.status === 'APPROVED')).length
  const customerDocuments = documents.filter(document => customer.some(item =>
    documentsFor(item, [document], horses.find(horse => horse.id === item.bookingHorseId)?.horseId).length > 0))
  const todos = reviewTodos(detail, data.checklist && { ...data.checklist, documents: customerDocuments })
  const allChecked = horses.length > 0 && horses.every(horse => horse.id != null && checked.includes(horse.id))
  const canStartQuoting = allChecked && !data.checklist?.requirementsDependOnGate && todos.insurance === 0 && todos.customer === 0
  const dueDate = detail.departureDate ?? detail.booking?.requestedPickupDate
  const pending = [
    todos.docs && t('staffReview.todoDocs', { count: todos.docs }),
    todos.insurance && t('staffReview.todoInsurance', { count: todos.insurance }),
    todos.customer && t('staffReview.todoCustomer', { count: todos.customer }),
  ].filter(Boolean).join(' ')
  return <>
    <div className={s.detailHeading}>
      <h1>{t(`staffMenus./specialist/${section}`)} · <span className={s.orderCode}>{detail.booking?.code}</span></h1>
      <OrderStatusBadge status={status} />
    </div>
    <DetailColumns info={<>
      <Card title={t('staffReview.workspace.nextStep')}>
        <p>{status === 'UNDER_REVIEW' ? pending || t('staffReview.ready') : status && t(`staffReview.nextStep.${status}`)}</p>
        <p>{detail.booking?.specialistName
          ? t('staffReview.assigned', { name: detail.booking.specialistName }) : t('staffReview.unassigned')}</p>
        {detail.reviewReason && <p className={s.reviewReason}>{t('staffReview.reviewReason', { reason: detail.reviewReason })}</p>}
      </Card>
      {section === 'verification' && todos.insurance > 0 && <Link to={`/specialist/insurance/${id}`}>
        {t('staffMenus./specialist/insurance')}
      </Link>}
      <OrderFacts detail={detail} />
    </>}>
      {section === 'verification' && data.checklist && <>
        <VerifyHorses horses={horses} checklist={{ ...data.checklist, items: customer }}
          status={canEdit ? status : undefined} actions={actions} checked={checked} setChecked={setChecked} />
        {canReview && <Card>
          <div className={s.actions}>
            <Button disabled={actions.busy} onClick={() => { actions.clear(); setRequesting(true) }}>{t('staffReview.action.return')}</Button>
            <ActionButtons options={[actions.reject(), ...(canStartQuoting ? [actions.startQuoting()] : [])]} actions={actions} />
          </div>
        </Card>}
        {requesting && <ResubmissionDialog items={customer} horses={horses} actions={actions} onClose={() => setRequesting(false)} />}
      </>}
      {section === 'legal' && data.checklist && <>
        <Card title={t('staffReview.workspace.submitted', { submitted, total: legal.length })}>
          {dueDate && <p>{t('staffReview.workspace.due')} · {formatDate(dueDate)}</p>}
          <h2>{t('staffReview.workspace.horseBatch')}</h2>
          <ul className={s.list}>{horses.map(horse => <li key={horse.id}>{horse.horseName}</li>)}</ul>
        </Card>
        <DocumentsCard checklist={{ ...data.checklist, items: legal }} horses={horses}
          status={canEdit ? status : undefined} actions={actions} />
      </>}
      {section === 'insurance' && <InsurancesCard horses={detail.horses} documents={data.documents}
        status={status} canEdit={canEdit} actions={actions} />}
    </DetailColumns>
  </>
}

export function SpecialistDetailPage({ section }: { section: SpecialistSection }) {
  const { t } = useTranslation()
  const data = usePageData<typeof reviewDetailLoader>()
  const { state } = useRevalidator()
  const id = data.detail?.booking?.id
  return <div className={s.stack}>
    <BackButton to={`/specialist/${section}`} label={t('staffReview.workspace.back')} />
    {data.errors.length > 0 && <ErrorState onRetry={data.reload} retrying={state !== 'idle'} />}
    {data.detail && id != null ? <SpecialistDetail key={id} id={id} detail={data.detail} data={data} section={section} />
      : state !== 'idle' ? <Skeleton lines={4} /> : !data.errors.length &&
        <EmptyState title={t('staffReview.notFound')} description={t('staffReview.checkOrderLink')} />}
  </div>
}
