// Hẹn giờ khách tự chở ngựa tới hub đầu tuyến trục (pickup_method SELF): lập lưu trú tập kết dự kiến để nhận ngựa
import { useTranslation } from 'react-i18next'
import type { DispatchBooking, DispatchHorse } from '@shared/api/dispatch'
import { formatDateTime } from '@shared/lib/format'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { isoToLocal } from '@backoffice/shared/localTime'
import { useHubAppointment } from './useHubAppointment'
import s from './Dispatch.module.css'

export function HubAppointmentModal({ booking, horse, onClose, onDone }: { booking: DispatchBooking; horse: DispatchHorse; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation()
  const { at, changeTime, busy, error, submit } = useHubAppointment(horse.bookingHorseId ?? 0, onDone)
  const deadline = booking.hubCheckinDeadlineAt
  const late = Boolean(deadline && at && new Date(at) > new Date(deadline))
  return (
    <Modal title={t('dispatch.hub.title', { horse: horse.horseName, code: booking.code })} onClose={onClose}
      footer={(
        <>
          <Button onClick={onClose}>{t('ui.cancel')}</Button>
          <Button variant="primary" disabled={!at || late} loading={busy} onClick={() => void submit()}>{t('dispatch.hub.appoint')}</Button>
        </>
      )}>
      <p className={s.muted}>{t('dispatch.hub.hint')}</p>
      <Field label={t('dispatch.hub.at')} required
        hint={deadline && t('dispatch.hub.deadline', { at: formatDateTime(new Date(deadline)) })}
        error={late ? t('dispatch.hub.late') : error}>
        {c => <Input {...c} type="datetime-local" value={at} max={deadline && isoToLocal(deadline)}
          onChange={e => changeTime(e.target.value)} data-autofocus />}
      </Field>
    </Modal>
  )
}
