import { useState } from 'react'
import { useNavigate, useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import type { ChecklistItem } from '@shared/api/bookingDocuments'
import type { BookingOrder } from '@shared/api/bookingOrders'
import { OrderStatusBadge } from '@shared/components/OrderStatusBadge'
import { usePageData } from '@shared/routing/pageData'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Skeleton } from '@shared/ui/Skeleton'
import { ChecklistFacts } from './components/ChecklistFacts'
import { UploadPartnerDialog } from './components/UploadPartnerDialog'
import type { missingDocumentsLoader } from './loaders'
import { documentsFor, itemKey, showDate, showsDocumentActions } from './reviewDisplay'
import { useMissingChecklist } from './useMissingChecklist'
import { useReviewActions } from './useReviewActions'
import s from './Review.module.css'

const PAGE_SIZE = 10

function MissingRow({ booking, id }: { booking: BookingOrder; id: number }) {
  const { t, i18n } = useTranslation()
  const { data, loaded, error, reload } = useMissingChecklist(id)
  const navigate = useNavigate()
  const actions = useReviewActions(id)
  const [uploading, setUploading] = useState<ChecklistItem>()
  const horseOf = (item: ChecklistItem) => data?.horses?.find(horse => horse.id === item.bookingHorseId)
  const canUpload = (item: ChecklistItem) => {
    const submitter = item.submitterType ?? item.requirement?.submitterType
    return data?.assignedToMe === true && showsDocumentActions(booking.status) && item.requirement?.id != null && !!submitter && submitter !== 'CUSTOMER'
      && !documentsFor(item, data?.checklist.documents ?? [], horseOf(item)?.horseId).some(document => document.status === 'UPLOADED')
  }
  const missing = data?.checklist.items?.filter(item => item.requirement?.mandatory && item.satisfied === false) ?? []
  return <Card>
    <div className={s.stack}>
      <div className={s.row}>
        <h2>{booking.code}</h2>
        <OrderStatusBadge status={booking.status} />
      </div>
      <p>{booking.specialistName ? t('staffReview.assigned', { name: booking.specialistName }) : t('staffReview.unassigned')}</p>
      {booking.requestedPickupDate && <p>{t('staffReview.pickupDate', { date: showDate(booking.requestedPickupDate, i18n.language) })}</p>}
      {!loaded && <Skeleton lines={2} />}
      {error && <ErrorState onRetry={reload} />}
      {data && <>
        {data.checklist.requirementsDependOnGate && <p>{t('staffReview.gatePending')}</p>}
        {missing.length ? <ul className={s.list}>
          {missing.map(item => <li key={itemKey(item)}>
            <div className={s.row}>
              <ChecklistFacts item={item} horseName={horseOf(item)?.horseName} />
              {canUpload(item) && <Button size="sm" variant="primary" onClick={() => { actions.clear(); setUploading(item) }}>{t('staffReview.upload')}</Button>}
            </div>
          </li>)}
        </ul> : <p>{t('staffReview.noMissingItems')}</p>}
      </>}
      <div><Button size="sm" onClick={() => navigate(`/specialist/review/${id}`)}>{t('staffReview.openOrder', { code: booking.code })}</Button></div>
    </div>
    {uploading && <UploadPartnerDialog item={uploading} horseName={horseOf(uploading)?.horseName} actions={actions}
      onClose={() => { setUploading(undefined); void reload() }} />}
  </Card>
}

export default function MissingDocumentsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { rows, reload } = usePageData<typeof missingDocumentsLoader>()
  const { state } = useRevalidator()
  const [limit, setLimit] = useState(PAGE_SIZE)

  return <div className={s.stack}>
    <h1>{t('staffReview.missingTitle')}</h1>
    {!rows ? state === 'idle' ? <ErrorState onRetry={reload} /> : <Skeleton lines={4} />
      : !rows.length ? <EmptyState title={t('staffReview.noMissingOrders')} description={t('staffReview.checkReviewQueue')} action={<Button onClick={() => navigate('/specialist/review')}>{t('staffReview.title')}</Button>} />
      : <>
        <p>{t('staffReview.deadlineUnavailable')}</p>
        <ul className={s.list}>{rows.slice(0, limit).map(booking => booking.id != null &&
          <li key={booking.id}><MissingRow booking={booking} id={booking.id} /></li>)}</ul>
        {rows.length > limit && <Button onClick={() => setLimit(limit + PAGE_SIZE)}>{t('staffReview.loadMore')}</Button>}
      </>}
  </div>
}
