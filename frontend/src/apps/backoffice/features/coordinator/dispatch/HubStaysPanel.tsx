// Ngựa ở một hub (điều phối viên): lịch hẹn khách tự chở tới, ngựa đang lưu trú (tập kết, nghỉ đêm, gửi chuồng).
// Khách tự chở hoặc tự nhận (SELF): nhận và trả ngựa ngay tại hub (MAN_HINH 3.5c)
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dispatchApi, type HorseStay } from '@shared/api/dispatch'
import { useLoad } from '@shared/hooks/useLoad'
import { formatDateTime } from '@shared/lib/format'
import { Button } from '@shared/ui/Button'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import { Table, type TableColumn } from '@shared/ui/Table'
import { Textarea } from '@shared/ui/Textarea'
import { useToast } from '@shared/ui/toast'
import s from './Dispatch.module.css'

type Action = { stay: HorseStay; kind: 'receive' | 'deliver' }

export function HubStaysPanel() {
  const { t } = useTranslation()
  const toast = useToast()
  const [hubId, setHubId] = useState('')
  const [action, setAction] = useState<Action>()
  const [chip, setChip] = useState('')
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const { data: hubs } = useLoad(async () => (await dispatchApi.locations()).filter(location => location.locationType === 'HUB'), [])
  const { data, loaded, error, reload } = useLoad(async () => {
    if (!hubId) return { appointments: [], stays: [] }
    const [appointments, stays] = await Promise.all([dispatchApi.hubAppointments(Number(hubId)), dispatchApi.horseStays(Number(hubId))])
    return { appointments, stays }
  }, [hubId], { toastOnError: false })

  const open = (stay: HorseStay, kind: Action['kind']) => { setChip(''); setNote(''); setAction({ stay, kind }) }
  const submit = async () => {
    if (!action) return
    setBusy(true)
    try {
      const call = action.kind === 'receive' ? dispatchApi.receiveAtHub : dispatchApi.deliverAtHub
      await call(action.stay.id ?? 0, chip.trim(), note.trim() || undefined)
      toast(t(`dispatch.stays.${action.kind}Done`), 'success')
      setAction(undefined)
      void reload()
    } catch (e) {
      toast(e instanceof Error ? e.message : String(e), 'error')
    } finally {
      setBusy(false)
    }
  }

  const appointmentColumns: TableColumn<HorseStay>[] = [
    { key: 'horse', header: t('dispatch.stays.colHorse'), render: stay => stay.horseName },
    { key: 'booking', header: t('dispatch.stays.colBooking'), render: stay => stay.bookingCode },
    { key: 'at', header: t('dispatch.stays.colAppointment'), render: stay => (stay.plannedStartAt ? formatDateTime(new Date(stay.plannedStartAt)) : '') },
    { key: 'action', header: '', render: stay => <Button size="sm" variant="primary" onClick={() => open(stay, 'receive')}>{t('dispatch.stays.receive')}</Button> },
  ]
  const columns: TableColumn<HorseStay>[] = [
    { key: 'horse', header: t('dispatch.stays.colHorse'), render: stay => stay.horseName },
    { key: 'booking', header: t('dispatch.stays.colBooking'), render: stay => stay.bookingCode },
    { key: 'type', header: t('dispatch.stays.colType'), render: stay => t(`dispatch.stays.stayType.${stay.stayType}`) },
    { key: 'since', header: t('dispatch.stays.colSince'), render: stay => (stay.actualStartAt ? formatDateTime(new Date(stay.actualStartAt)) : '') },
    { key: 'until', header: t('dispatch.stays.colUntil'), render: stay => (stay.plannedEndAt ? formatDateTime(new Date(stay.plannedEndAt)) : '') },
    {
      key: 'action',
      header: '',
      // Người nhận tự tới hub; backend kiểm ngựa đã tới bằng chặng cuối
      render: stay => (stay.deliveryMethod === 'SELF' && stay.stayType === 'ASSEMBLY'
        ? <Button size="sm" onClick={() => open(stay, 'deliver')}>{t('dispatch.stays.deliver')}</Button> : null),
    },
  ]

  const content = () => {
    if (!hubId) return null
    if (!loaded) return <Skeleton variant="block" lines={3} />
    if (error || !data) return <ErrorState message={error?.message} onRetry={reload} />
    return (
      <>
        {data.appointments.length > 0 && (
          <Table caption={t('dispatch.stays.appointmentsCaption')} columns={appointmentColumns} rows={data.appointments} rowKey={stay => stay.id ?? 0} />
        )}
        {data.stays.length
          ? <Table caption={t('dispatch.stays.caption')} columns={columns} rows={data.stays} rowKey={stay => stay.id ?? 0} />
          : <EmptyState icon={<i className="fa-solid fa-warehouse" />} title={t('dispatch.stays.emptyTitle')} description={t('dispatch.stays.emptyDescription')} />}
      </>
    )
  }

  return (
    <div className={s.section}>
      <div className={s.form}>
        <Field label={t('dispatch.stays.hub')}>
          {c => <Select {...c} value={hubId} onChange={setHubId} placeholder={t('dispatch.detail.choose')}
            options={(hubs ?? []).map(hub => ({ value: `${hub.id}`, label: hub.name ?? '' }))} />}
        </Field>
      </div>
      {content()}
      {action && (
        <Modal title={t(`dispatch.stays.${action.kind}Title`, { horse: action.stay.horseName, code: action.stay.bookingCode })} onClose={() => setAction(undefined)}
          footer={(
            <>
              <Button onClick={() => setAction(undefined)}>{t('ui.cancel')}</Button>
              <Button variant="primary" disabled={!chip.trim()} loading={busy} onClick={() => void submit()}>{t(`dispatch.stays.${action.kind}`)}</Button>
            </>
          )}>
          <p className={s.muted}>{t(`dispatch.stays.${action.kind}Hint`)}</p>
          <Field label={t('dispatch.stays.chip')} required>
            {c => <Input {...c} inputMode="numeric" value={chip} onChange={e => setChip(e.target.value)} data-autofocus />}
          </Field>
          <Field label={t('dispatch.stays.condition')}>
            {c => <Textarea {...c} value={note} onChange={e => setNote(e.target.value)} />}
          </Field>
        </Modal>
      )}
    </div>
  )
}
