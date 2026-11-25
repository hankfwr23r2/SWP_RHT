import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { PickupMissReport } from '@shared/api/tripExecution'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { Select } from '@shared/ui/Select'
import { Textarea } from '@shared/ui/Textarea'

const reasons: PickupMissReport['reasonCode'][] = [
  'SENDER_ABSENT', 'CHIP_MISMATCH', 'ORIGINAL_DOCUMENTS_MISSING', 'HORSE_UNWELL', 'HORSE_WILL_NOT_BOARD',
  'VEHICLE_FAILURE', 'UNSUITABLE_STALL', 'LATE_ARRIVAL', 'CREW_SHORTAGE', 'FLOOD', 'ROAD_CLOSURE',
]
const contacts = ['senderContact', 'customerContact', 'emergencyContact'] as const

export function PickupMissDialog({ horseLegId, name, busy, onClose, onSubmit }: {
  horseLegId: number
  name: string
  busy: boolean
  onClose: () => void
  onSubmit: (request: PickupMissReport, photo: File) => void
}) {
  const { t } = useTranslation()
  const [reasonCode, setReasonCode] = useState<PickupMissReport['reasonCode']>()
  const [reason, setReason] = useState('')
  const [notes, setNotes] = useState({ senderContact: '', customerContact: '', emergencyContact: '' })
  const [photo, setPhoto] = useState<File>()
  const absent = reasonCode === 'SENDER_ABSENT'
  const valid = Boolean(reasonCode && reason.trim() && photo && (!absent || contacts.every(key => notes[key].trim())))

  return (
    <Modal title={t('myTrips.run.refusal.title', { name })} onClose={() => { if (!busy) onClose() }}
      footer={<>
        <Button disabled={busy} onClick={onClose}>{t('ui.cancel')}</Button>
        <Button variant="danger" loading={busy} disabled={!valid} onClick={() => {
          if (reasonCode && photo) onSubmit({ horseLegId, reasonCode, reason: reason.trim(), ...(absent ? notes : {}) }, photo)
        }}>{t('myTrips.run.refusal.refuse')}</Button>
      </>}>
      <p>{t('myTrips.run.refusal.message')}</p>
      <Field label={t('myTrips.run.refusal.reasonCode')} required>
        {props => <Select {...props} value={reasonCode ?? ''} onChange={value => setReasonCode(reasons.find(item => item === value))}
          options={reasons.map(value => ({ value, label: t('myTrips.run.refusal.reasons.' + value) }))} />}
      </Field>
      <Field label={t('myTrips.run.refusal.reason')} required>
        {props => <Textarea {...props} maxLength={500} value={reason} onChange={e => setReason(e.target.value)} />}
      </Field>
      {absent && contacts.map(key => <Field key={key} label={t('myTrips.run.refusal.' + key)} required>
        {props => <Input {...props} maxLength={500} value={notes[key]} onChange={e => setNotes({ ...notes, [key]: e.target.value })} />}
      </Field>)}
      <Field label={t('myTrips.run.refusal.photo')} required>
        {props => <Input {...props} type="file" accept="image/*" capture="environment" onChange={e => setPhoto(e.target.files?.[0])} />}
      </Field>
    </Modal>
  )
}
