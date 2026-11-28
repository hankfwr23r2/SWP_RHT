import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { incidentsApi } from '@shared/api/incidentOps'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Modal } from '@shared/ui/Modal'
import { Textarea } from '@shared/ui/Textarea'
import { useToast } from '@shared/ui/toast'

function useVetResult(incidentId: number, planId: number | undefined, onSaved: () => Promise<void>) {
  const { t } = useTranslation()
  const toast = useToast()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  async function save(note: string) {
    setBusy(true)
    setError(undefined)
    try {
      await incidentsApi.recordVetResult(incidentId, note.trim(), planId)
      toast(t('incidents.vetResult.saved'), 'success')
      await onSaved()
      return true
    } catch (cause) {
      const fields = cause instanceof Error && 'fields' in cause ? cause.fields : undefined
      if (fields && typeof fields === 'object' && 'note' in fields && typeof fields.note === 'string') {
        setError(fields.note)
      }
      toast(cause instanceof Error ? cause.message : t('incidents.vetResult.error'), 'error')
      return false
    } finally {
      setBusy(false)
    }
  }

  return { busy, error, save }
}

interface Props {
  incidentId: number
  planId?: number
  partnerName?: string
  disabled?: boolean
  onSaved: () => Promise<void>
}

export function VetResultControl({ incidentId, planId, partnerName, disabled, onSaved }: Props) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [note, setNote] = useState('')
  const { busy, error, save } = useVetResult(incidentId, planId, onSaved)
  const close = () => { if (!busy) setOpen(false) }

  return (
    <>
      <Button size="lg" disabled={disabled} onClick={() => setOpen(true)}>{t('incidents.vetResult.action')}</Button>
      {open && <Modal title={t('incidents.vetResult.action')} subtitle={partnerName} onClose={close} footer={(
        <>
          <Button disabled={busy} onClick={close}>{t('ui.cancel')}</Button>
          <Button variant="primary" loading={busy} disabled={!note.trim()}
            onClick={async () => { if (await save(note)) setOpen(false) }}>
            {t('incidents.vetResult.save')}
          </Button>
        </>
      )}>
        <Field label={t('incidents.vetResult.note')} hint={t('incidents.vetResult.hint')} error={error} required>
          {c => <Textarea {...c} value={note} maxLength={2000} disabled={busy} onChange={e => setNote(e.target.value)} />}
        </Field>
      </Modal>}
    </>
  )
}
