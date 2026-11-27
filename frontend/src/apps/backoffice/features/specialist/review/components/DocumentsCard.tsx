import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { BookingDocument, ChecklistItem, DocumentChecklist } from '@shared/api/bookingDocuments'
import type { BookingDetail, BookingStatus } from '@shared/api/bookingOrders'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { Modal } from '@shared/ui/Modal'
import { EmptyState } from '@shared/ui/EmptyState'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { formatDate } from '@shared/lib/format'
import { documentsFor, itemKey, showsDocumentActions } from '../reviewDisplay'
import type { ReviewAction, ReviewActions } from '../useReviewActions'
import { ActionButtons } from './ActionButtons'
import { ChecklistFacts } from './ChecklistFacts'
import { UploadPartnerDialog } from './UploadPartnerDialog'
import s from '../Review.module.css'

const documentTone = { UPLOADED: 'info', APPROVED: 'success', REJECTED: 'danger', EXPIRED: 'warning' } as const

function DocumentRow({ document, editable, actions }: { document: BookingDocument; editable: boolean; actions: ReviewActions }) {
  const { t } = useTranslation()
  const [preview, setPreview] = useState(false)
  const { id, status, fileUrl, fileName, expiryDate, reviewNote } = document
  const isImage = /\.(?:png|jpe?g)$/i.test(fileName ?? '')
  const pending = editable && status === 'UPLOADED'
  const options: ReviewAction[] = []
  if (editable && id != null) {
    if (status === 'UPLOADED') options.push(actions.approveDocument(id), actions.rejectDocument(id))
    if (status === 'APPROVED') options.push(actions.revokeDocument(id))
  }
  return <li className={s.item} data-pending={pending || undefined}>
    <div className={s.stack}>
      <div className={s.row}>
        {fileName && <span className={s.fileName}>{fileName}</span>}
        {status && <StatusBadge tone={documentTone[status]} label={t(`staffReview.documentStatus.${status}`)} />}
        {fileUrl && <Button size="sm" className={s.previewButton}
          onClick={() => setPreview(true)}>{t('staffReview.workspace.preview')}</Button>}
      </div>
      {preview && fileUrl && <Modal wide title={fileName ?? t('staffReview.workspace.preview')} onClose={() => setPreview(false)}
        footer={<a className={s.file} href={fileUrl} target="_blank" rel="noopener noreferrer">
          {t('staffReview.workspace.openTab')}
        </a>}>
        {isImage ? <img className={s.previewImage} src={fileUrl} alt={fileName} />
          : <iframe className={s.preview} src={fileUrl} title={fileName ?? t('staffReview.workspace.preview')} referrerPolicy="no-referrer" />}
      </Modal>}
      {expiryDate && <p>{t('staffReview.expiry', { date: formatDate(expiryDate) })}</p>}
      {reviewNote && <p>{reviewNote}</p>}
      {options.length > 0 && <ActionButtons options={options} actions={actions} />}
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
  // Duyệt hoặc từ chối xong một giấy thì cuộn tới giấy chờ duyệt kế tiếp
  const list = useRef<HTMLUListElement>(null)
  const pending = checklist.documents?.filter(document => document.status === 'UPLOADED').length ?? 0
  const before = useRef(pending)
  useEffect(() => {
    if (pending < before.current) list.current?.querySelector('[data-pending]')?.scrollIntoView?.({ block: 'center' })
    before.current = pending
  }, [pending])

  return <Card title={t('staffReview.documents')}>
    <div className={s.stack}>
      {checklist.requirementsDependOnGate && <p>{t('staffReview.gatePending')}</p>}
      {!items.length ? <EmptyState title={t('staffReview.noDocuments')} description={t('staffReview.noDocumentsHint')} /> : <ul ref={list} className={s.list}>
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
                {item.requirement && item.satisfied != null && <StatusBadge tone={item.satisfied ? 'success' : 'neutral'}
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
