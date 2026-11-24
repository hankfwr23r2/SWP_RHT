// Hẹn giờ khách tự chở ngựa tới hub đầu tuyến trục (pickup_method SELF): lập lưu trú tập kết dự kiến để nhận ngựa
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dispatchApi, type DispatchBooking, type DispatchHorse } from '@shared/api/dispatch'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { useToast } from '@shared/ui/toast'
import { localToIso } from './dispatchModel'
import s from './Dispatch.module.css'

export function HubAppointmentModal({ booking, horse, onClose, onDone }: { booking: DispatchBooking; horse: DispatchHorse; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation()
  const toast = useToast()
  const [at, setAt] = useState('')
  const [busy, setBusy] = useState(false)
  const submit = async () => {
    setBusy(true)
    try {
      await dispatchApi.appointAtHub(horse.bookingHorseId ?? 0, localToIso(at))
      toast(t('dispatch.hub.done'), 'success')
      onDone()
    } catch (e) {
      toast(e instanceof Error ? e.message : String(e), 'error')
    } finally {
      setBusy(false)
    }
  }
  return (
    <Modal title={t('dispatch.hub.title', { horse: horse.horseName, code: booking.code })} onClose={onClose}
      footer={(
        <>
          <Button onClick={onClose}>{t('ui.cancel')}</Button>
          <Button variant="primary" disabled={!at} loading={busy} onClick={() => void submit()}>{t('dispatch.hub.appoint')}</Button>
        </>
      )}>
      <p className={s.muted}>{t('dispatch.hub.hint')}</p>
      <Field label={t('dispatch.hub.at')} required>
        {c => <Input {...c} type="datetime-local" value={at} onChange={e => setAt(e.target.value)} data-autofocus />}
      </Field>
    </Modal>
  )
}
