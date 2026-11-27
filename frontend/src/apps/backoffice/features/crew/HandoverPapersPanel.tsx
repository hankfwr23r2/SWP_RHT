// Biên bản đã ký và giấy tờ gốc của lần nhận hoặc giao ngựa (MAN_HINH 4.2, 4.3, 6.5). Chưa có biên bản thì chưa hoàn tất được
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { tripExecutionApi, type CrewTrip, type Handover } from '@shared/api/tripExecution'
import { useLoad } from '@shared/hooks/useLoad'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { FilePreviewButton } from '@shared/ui/FilePreviewButton'
import { Input } from '@shared/ui/Input'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { useToast } from '@shared/ui/toast'
import s from './MyTrips.module.css'

export function HandoverPapersPanel({ handover, receiving, onTrip }: { handover: Handover; receiving: boolean; onTrip: (next: CrewTrip) => void }) {
  const { t } = useTranslation()
  const toast = useToast()
  const [file, setFile] = useState<File>()
  const [busy, setBusy] = useState(false)
  const handoverId = handover.id ?? 0
  const showOriginals = receiving || handover.handoverType === 'DELIVERY'
  // Giấy cần trả thu hẹp theo con đã quét
  const scanned = (handover.horses ?? []).filter(horse => horse.microchipMatched).length
  const { data: papers, updateData } = useLoad(
    () => showOriginals ? tripExecutionApi.papers(handoverId) : Promise.resolve([]),
    [handoverId, showOriginals, scanned],
    { toastOnError: false },
  )

  const upload = async () => {
    if (!file) return
    setBusy(true)
    try {
      onTrip(await tripExecutionApi.uploadRecord(handoverId, file))
      setFile(undefined)
      toast(t('myTrips.run.papers.recordSaved'), 'success')
    } catch (e) {
      toast(e instanceof Error ? e.message : String(e), 'error')
    } finally {
      setBusy(false)
    }
  }
  const mark = async (documentId: number, originalMissing = false) => {
    try {
      const next = await tripExecutionApi.markPaper(handoverId, documentId, originalMissing)
      updateData(() => next)
    } catch (e) {
      toast(e instanceof Error ? e.message : String(e), 'error')
    }
  }

  return (
    <div className={s.body}>
      {handover.recordAttached
        ? <StatusBadge tone="success" label={t('myTrips.run.papers.recordAttached')} />
        : (
          <>
            <Field label={t('myTrips.run.papers.record')}>
              {c => <>
                <Input {...c} type="file" accept="image/*,application/pdf" capture="environment" onChange={e => setFile(e.target.files?.[0])} />
                {file && <FilePreviewButton fileName={file.name} file={file} label={t('ui.previewFile')} />}
              </>}
            </Field>
            <Button size="lg" fullWidth disabled={!file} loading={busy} onClick={() => void upload()}>{t('myTrips.run.papers.upload')}</Button>
          </>
        )}
      {(papers ?? []).length > 0 && (
        <ul className={s.list} aria-label={t('myTrips.run.papers.originals')}>
          {(papers ?? []).map(paper => (
            <li key={paper.documentId} className={s.body}>
              <p className={s.line}>{paper.documentTypeName} · {paper.ownerLabel}</p>
              {paper.direction
                ? <StatusBadge tone="success" label={t(paper.direction === 'NOT_RECEIVED' ? 'handoverEvidence.originalMissing' : `myTrips.run.papers.${paper.direction}`)} />
                : null}
              {(!paper.direction || paper.direction === 'NOT_RECEIVED') && (
                <Button size="lg" fullWidth onClick={() => void mark(paper.documentId ?? 0)}>
                  {t(receiving ? 'myTrips.run.papers.receive' : 'myTrips.run.papers.return')}
                </Button>
              )}
              {receiving && paper.direction !== 'NOT_RECEIVED' && <Button size="lg" fullWidth onClick={() => void mark(paper.documentId ?? 0, true)}>
                {t('handoverEvidence.originalMissing')}
              </Button>}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
