import { useTranslation } from 'react-i18next'
import type { HorseDocument } from '@shared/api/horses'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { horseDocumentName } from './horseProfileDisplay'
import type { SelectedDocuments } from './useHorseForm'
import s from './Horses.module.css'

const documentTone = { UPLOADED: 'info', APPROVED: 'success', REJECTED: 'danger', EXPIRED: 'warning', MISSING: 'neutral' } as const

export function HorseDocumentBadge({ document, showType = false }: { document: HorseDocument; showType?: boolean }) {
  const { t } = useTranslation()
  const status = document.status ?? 'MISSING'
  const label = t(`horses.documentStatus.${status}`)
  return <StatusBadge tone={documentTone[status]} label={showType
    ? t('horses.documentChip', { name: horseDocumentName(t, document), status: label }) : label} />
}

export function HorseDocumentFields({ documents, selected, onSelect, busy }: {
  documents: HorseDocument[]; selected: SelectedDocuments; onSelect: (code: string, file?: File) => void; busy: boolean
}) {
  const { t, i18n } = useTranslation()
  const expiryFormat = new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium', timeZone: 'UTC' })
  const uploadedFormat = new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium', timeZone: 'Asia/Ho_Chi_Minh' })
  return <div className={s.documentList}>
    {documents.map(document => {
      const code = document.documentTypeCode
      if (!code) return null
      const name = horseDocumentName(t, document)
      const choice = selected[code]
      return <div key={code} className={s.document}>
        <div className={s.documentHeading}><strong>{name}</strong><HorseDocumentBadge document={document} /></div>
        {document.fileUrl && <a href={document.fileUrl} target="_blank" rel="noreferrer">
          {document.fileName || t('horses.viewDocument')}
        </a>}
        {document.uploadedAt && <p className={s.subtle}>
          {t('horses.uploadedOn', { date: uploadedFormat.format(new Date(document.uploadedAt)) })}
        </p>}
        {document.expiryDate && <p className={s.subtle}>
          {t('horses.documentExpiry', { date: expiryFormat.format(new Date(document.expiryDate)) })}
        </p>}
        {document.status === 'REJECTED' && document.reviewNote && <p className={s.notice}>
          {t('horses.rejectionReason', { reason: document.reviewNote })}
        </p>}
        {document.canUpload && <Field label={t('horses.chooseDocument', { name })} hint={t('horses.documentHint')} error={choice?.error}>
          {control => <Input {...control} type="file" accept=".pdf,.jpg,.jpeg,.png" disabled={busy} onChange={event => {
            const file = event.currentTarget.files?.[0]
            event.currentTarget.value = ''
            if (file) onSelect(code, file)
          }} />}
        </Field>}
        {choice && <div className={s.documentHeading}>
          <span>{choice.file.name}</span>
          <Button size="sm" variant="ghost" disabled={busy} aria-label={t('horses.removeFile', { name: choice.file.name })}
            icon={<i className="fa-solid fa-xmark" />} onClick={() => onSelect(code)} />
        </div>}
      </div>
    })}
  </div>
}
