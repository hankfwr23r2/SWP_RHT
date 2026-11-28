import { useState } from 'react'
import { useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import type { ChecklistItem } from '@shared/api/bookingDocuments'
import { bookingOrdersApi } from '@shared/api/bookingOrders'
import { canUpload, latestDocument, myItems } from '@shared/components/orderSteps'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { ErrorState } from '@shared/ui/ErrorState'
import { FilePreviewButton } from '@shared/ui/FilePreviewButton'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { showDate } from '../format'
import type { OrderData } from '../loaders'
import { useOrderAction } from '../useOrderAction'
import { UploadDialog } from './UploadDialog'
import s from '../Orders.module.css'

const TONE = { UPLOADED: 'info', APPROVED: 'success', REJECTED: 'danger', EXPIRED: 'danger', MISSING: 'warning' } as const

export function DocumentsCard({ data }: { data: OrderData }) {
  const { t } = useTranslation()
  const { revalidate, state } = useRevalidator()
  const { run, busy, error } = useOrderAction()
  const [uploading, setUploading] = useState<ChecklistItem | null>(null)
  const [confirming, setConfirming] = useState(false)
  const status = data.detail?.booking?.status
  const items = myItems(data.checklist)

  if (!data.checklist) return <Card title={t('myOrders.documents.title')}><ErrorState onRetry={revalidate} retrying={state !== 'idle'} /></Card>
  if (!items.length && status !== 'NEEDS_RESUBMISSION') return null

  const horseName = (id?: number) => data.detail?.horses?.find(horse => horse.id === id)?.horseName
  const resubmit = async () => { setConfirming(false); await run(() => bookingOrdersApi.resubmit(data.id), t('myOrders.documents.resubmitted')) }

  return (
    <Card title={t('myOrders.documents.title')}>
      <div className={s.stack}>
        {data.checklist.requirementsDependOnGate && <p className={s.hint}>{t('myOrders.documents.dependsOnGate')}</p>}
        <ul className={s.items}>
          {items.map(item => {
            const document = latestDocument(item, data.checklist?.documents)
            const key = item.satisfied ? 'APPROVED' : document?.status ?? 'MISSING'
            return (
              <li key={`${item.requirement?.id ?? item.profileDocumentType?.code}-${item.bookingHorseId ?? ''}`} className={s.item}>
                <div>
                  <strong>{item.requirement?.documentTypeName ?? item.profileDocumentType?.name}{horseName(item.bookingHorseId) && ` · ${horseName(item.bookingHorseId)}`}</strong>
                  {!item.requirement?.mandatory && <p className={s.hint}>{t('bookingForm.route.optional')}</p>}
                  {document?.expiryDate && <p className={s.hint}>{t('myOrders.documents.expiry', { date: showDate(document.expiryDate) })}</p>}
                  {document?.status === 'REJECTED' && document.reviewNote && <p className={s.error}>{document.reviewNote}</p>}
                  {document?.fileUrl && <div>
                    <span>{document.fileName ?? t('myOrders.documents.view')}</span>
                    <FilePreviewButton fileName={document.fileName ?? t('myOrders.documents.view')}
                      fileUrl={document.fileUrl} label={t('ui.previewFile')} />
                  </div>}
                </div>
                <StatusBadge tone={TONE[key]} label={t(`myOrders.documents.status.${key}`)} />
                {canUpload(status, item, document) && (
                  <Button size="sm" onClick={() => setUploading(item)}>{t(document ? 'myOrders.documents.replace' : 'myOrders.documents.upload')}</Button>
                )}
              </li>
            )
          })}
        </ul>
        {error && <p className={s.error} role="alert">{error}</p>}
        {status === 'NEEDS_RESUBMISSION' && (
          <div><Button variant="primary" loading={busy} onClick={() => setConfirming(true)}>{t('myOrders.documents.resubmit')}</Button></div>
        )}
      </div>
      {uploading && <UploadDialog bookingId={data.id} item={uploading} horseName={horseName(uploading.bookingHorseId)} onClose={() => setUploading(null)} />}
      {confirming && <ConfirmDialog title={t('myOrders.documents.resubmitTitle')} message={t('myOrders.documents.resubmitMessage')}
        confirmLabel={t('myOrders.documents.resubmit')} onConfirm={resubmit} onCancel={() => setConfirming(false)} />}
    </Card>
  )
}
