import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { DepartureDecision, DepartureDelayRequest } from '@shared/api/departures'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { Textarea } from '@shared/ui/Textarea'
import { moment } from '@backoffice/shared/staffFormat'
import s from '@backoffice/shared/StaffPanels.module.css'

interface Props {
  row: DepartureDecision
  busy: boolean
  error: string
  fields: Record<string, string>
  onConfirm: (body: DepartureDelayRequest) => void
  onClose: () => void
}

export function DelayDialog({ row, busy, error, fields, onConfirm, onClose }: Props) {
  const { t } = useTranslation()
  const [time, setTime] = useState('')
  const [reason, setReason] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const submit = () => {
    setSubmitted(true)
    if (time && reason.trim()) {
      onConfirm({ revisedDepartureAt: new Date(`${time}+07:00`).toISOString(), reason: reason.trim() })
    }
  }

  return (
    <Modal title={t('mgr.departures.delay.title')} onClose={() => { if (!busy) onClose() }} footer={
      <>
        <Button disabled={busy} onClick={onClose}>{t('mgr.close')}</Button>
        <Button variant="primary" loading={busy} onClick={submit}>{t('mgr.departures.delay.save')}</Button>
      </>
    }>
      <div className={s.stack}>
        <p>{t('mgr.departures.delay.original', { at: row.plannedDepartureAt && moment(row.plannedDepartureAt) })}</p>
        <p>{t('mgr.departures.delay.notice')}</p>
        <Field label={t('mgr.departures.delay.newTime')} required
          error={submitted && !time ? t('mgr.required') : fields.revisedDepartureAt}>
          {control => <Input {...control} type="datetime-local" value={time} onChange={event => setTime(event.target.value)} />}
        </Field>
        <Field label={t('mgr.reason')} required error={submitted && !reason.trim() ? t('mgr.required') : fields.reason}>
          {control => <Textarea {...control} rows={3} maxLength={500} value={reason} onChange={event => setReason(event.target.value)} />}
        </Field>
        {error && <p className={s.error} role="alert">{error}</p>}
      </div>
    </Modal>
  )
}
