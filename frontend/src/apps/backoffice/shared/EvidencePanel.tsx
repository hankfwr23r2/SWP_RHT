// Chứng từ của sự cố (B4): biên lai, hóa đơn của một chi phí hoặc ảnh hiện trường của một sự cố; có upload thì cho tải thêm
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Evidence } from '@shared/api/incidentOps'
import { useLoad } from '@shared/hooks/useLoad'
import { formatDateTime } from '@shared/lib/format'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { useToast } from '@shared/ui/toast'
import s from './EvidencePanel.module.css'

interface Props {
  title: string
  load?: () => Promise<Evidence[]>
  evidence?: Evidence[]
  upload?: (file: File) => Promise<Evidence[]>
  accept?: string
  fileLabel?: string
  uploadLabel?: string
}

export function EvidencePanel({ title, load, evidence, upload, accept = 'image/*,application/pdf', fileLabel, uploadLabel }: Props) {
  const { t } = useTranslation()
  const toast = useToast()
  const { data, updateData } = useLoad(() => load?.() ?? Promise.resolve([]), [], { toastOnError: false })
  const items = evidence ?? data ?? []
  const [file, setFile] = useState<File>()
  const [busy, setBusy] = useState(false)

  const send = async () => {
    if (!file || !upload) return
    setBusy(true)
    try {
      const next = await upload(file)
      updateData(() => next)
      setFile(undefined)
      toast(t('incidents.evidenceUploaded'), 'success')
    } catch (e) {
      toast(e instanceof Error ? e.message : String(e), 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className={s.panel}>
      <strong>{title}</strong>
      {items.length ? (
        <ul className={s.list} aria-label={title}>
          {items.map(item => (
            <li key={item.id} className={s.muted}>
              <a href={item.fileUrl} target="_blank" rel="noreferrer">{item.fileName}</a>
              {item.documentTypeCode === 'VET_REPORT' && <> · {t('incidents.vetReport')}</>}
              {item.documentTypeCode === 'VEHICLE_SWAP_RECORD' && <> · {t('incidents.vehicleSwapRecord')}</>}
              {' · '}{item.uploadedByName}
              {item.capturedAt || item.uploadedAt ? ` · ${formatDateTime(new Date(item.capturedAt ?? item.uploadedAt ?? ''))}` : ''}
              {item.latitude != null && item.longitude != null ? ` · ${item.latitude}, ${item.longitude}` : ''}
            </li>
          ))}
        </ul>
      ) : <p className={s.muted}>{t('incidents.noEvidence')}</p>}
      {upload && (
        <div className={s.row}>
          <Field label={fileLabel ?? t('incidents.evidenceFile')}>
            {c => <input {...c} type="file" accept={accept} capture="environment" onChange={e => setFile(e.target.files?.[0])} />}
          </Field>
          <Button size="sm" disabled={!file} loading={busy} onClick={() => void send()}>{uploadLabel ?? t('incidents.uploadEvidence')}</Button>
        </div>
      )}
    </div>
  )
}
