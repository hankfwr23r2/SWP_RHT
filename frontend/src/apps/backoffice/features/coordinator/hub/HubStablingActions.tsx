import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { stablingApi } from '@shared/api/stabling'
import { Button } from '@shared/ui/Button'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Textarea } from '@shared/ui/Textarea'
import { useToast } from '@shared/ui/toast'
import { localToIso } from '@backoffice/shared/localTime'

export function HubStablingActions({ stayId, readyAt, onChanged }: { stayId: number; readyAt?: string; onChanged: () => void }) {
  const { t } = useTranslation()
  const toast = useToast()
  const [open, setOpen] = useState(false)
  const [note, setNote] = useState('')
  const [earliest, setEarliest] = useState('')
  const [chosen, setChosen] = useState('')
  const [invalid, setInvalid] = useState(false)
  const title = t(readyAt ? 'stablingClock.later' : 'stablingClock.recordReady')

  const confirm = async () => {
    if (readyAt ? !earliest || !chosen : !note.trim()) {
      setInvalid(true)
      return
    }
    try {
      if (readyAt) await stablingApi.laterSlot(stayId, { earliestAt: localToIso(earliest), chosenAt: localToIso(chosen) })
      else await stablingApi.recordReady(stayId, { callNote: note.trim() })
      toast(t('stablingClock.saved'), 'success')
      setOpen(false)
      onChanged()
    } catch (cause) {
      toast(cause instanceof Error ? cause.message : t('stablingClock.failed'), 'error')
      throw cause
    }
  }

  return <>
    <Button size="sm" onClick={() => { setInvalid(false); setOpen(true) }}>{title}</Button>
    {open && <ConfirmDialog title={title} confirmLabel={title} onCancel={() => setOpen(false)} onConfirm={confirm}
      message={readyAt ? <>
        <p>{t('stablingClock.laterMessage')}</p>
        <Field label={t('stablingClock.earliest')} error={invalid && !earliest ? t('stablingClock.invalidTime') : undefined}>
          {props => <Input {...props} type="datetime-local" value={earliest} onChange={event => setEarliest(event.target.value)} />}
        </Field>
        <Field label={t('stablingClock.chosen')} error={invalid && !chosen ? t('stablingClock.invalidTime') : undefined}>
          {props => <Input {...props} type="datetime-local" value={chosen} onChange={event => setChosen(event.target.value)} />}
        </Field>
      </> : <>
        <p>{t('stablingClock.readyMessage')}</p>
        <Field label={t('stablingClock.callNote')} error={invalid && !note.trim() ? t('stablingClock.requiredNote') : undefined}>
          {props => <Textarea {...props} maxLength={500} value={note} onChange={event => setNote(event.target.value)} />}
        </Field>
      </>} />}
  </>
}
