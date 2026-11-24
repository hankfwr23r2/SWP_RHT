import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { BookingDocument, ChecklistItem, DocumentChecklist } from '@shared/api/bookingDocuments'
import type { BookingDetail, BookingStatus } from '@shared/api/bookingOrders'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { EmptyState } from '@shared/ui/EmptyState'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { documentsFor, itemKey, showDate, showsDocumentActions } from '../reviewDisplay'
import type { ReviewActions } from '../useReviewActions'
import { ActionButtons } from './ActionButtons'
import { ChecklistFacts } from './ChecklistFacts'
import { UploadPartnerDialog } from './UploadPartnerDialog'
import s from '../Review.module.css'

const documentTone = { UPLOADED: 'info', APPROVED: 'success', REJECTED: 'danger', EXPIRED: 'warning' } as const

function DocumentRow({ document, editable, actions }: { document: BookingDocument; editable: boolean; actions: ReviewActions }) {
  const { t, i18n } = useTranslation()
  const { id, status, fileUrl, fileName, expiryDate, reviewNote } = document
  return <li className={s.item}>
    <div className={s.stack}>
      <div className={s.row}>
        {fileUrl && <a href={fileUrl} target="_blank" rel="noopener noreferrer">{fileName || t('staffReview.viewFile')}</a>}
        {status && <StatusBadge tone={documentTone[status]} label={t(`staffReview.documentStatus.${status}`)} />}
      </div>
      {expiryDate && <p>{t('staffReview.expiry', { date: showDate(expiryDate, i18n.language) })}</p>}
      {reviewNote && <p>{reviewNote}</p>}
      {editable && status === 'UPLOADED' && id != null &&
        <ActionButtons options={[actions.approveDocument(id), actions.rejectDocument(id)]} actions={actions} />}
    </div>
  </li>
}

export function DocumentsCard({ checklist, horses, status, actions }: {
  checklist: DocumentChecklist; horses?: BookingDetail['horses']; status?: BookingStatus; actions: ReviewActions
}) {
  const { t } = useTranslation()
  const [uploading, setUploading] = useState<ChecklistItem>()
  const horseFor = (item: ChecklistItem) => horses?.find(horse => horse.id === item.bookingHorseId)
  const editable = showsDocumentActions(status)
  const items = checklist.items ?? []

  return <Card title={t('staffReview.documents')}>
    <div className={s.stack}>
      {checklist.requirementsDependOnGate && <p>{t('staffReview.gatePending')}</p>}
      {!items.length ? <EmptyState title={t('staffReview.noDocuments')} description={t('staffReview.noDocumentsHint')} /> : <ul className={s.list}>
        {items.map(item => {
          const horse = horseFor(item)
          const documents = documentsFor(item, checklist.documents ?? [], horse?.horseId)
          const submitter = item.submitterType ?? item.requirement?.submitterType
          const canUpload = editable && item.requirement?.id != null && submitter && submitter !== 'CUSTOMER'
            && item.satisfied === false && !documents.some(document => document.status === 'UPLOADED')
          return <li className={s.item} key={itemKey(item)}>
            <div className={s.stack}>
              <div className={s.row}>
                <ChecklistFacts item={item} horseName={horse?.horseName} />
                {item.satisfied != null && <StatusBadge tone={item.satisfied ? 'success' : 'neutral'}
                  label={t(item.satisfied ? 'staffReview.satisfied' : 'staffReview.unsatisfied')} />}
              </div>
              {!!documents.length && <ul className={s.list}>{documents.map(document =>
                <DocumentRow key={document.id} document={document} editable={editable} actions={actions} />)}</ul>}
              {canUpload && <div><Button size="sm" disabled={actions.busy} onClick={() => {
                actions.clear()
                setUploading(item)
              }}>{t('staffReview.upload')}</Button></div>}
            </div>
          </li>
        })}
      </ul>}
    </div>
    {uploading && <UploadPartnerDialog item={uploading} horseName={horseFor(uploading)?.horseName}
      actions={actions} onClose={() => setUploading(undefined)} />}
  </Card>
}
