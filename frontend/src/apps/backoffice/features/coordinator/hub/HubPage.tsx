// Ngựa ở một hub (điều phối viên): lịch hẹn khách tự chở tới, ngựa đang lưu trú (tập kết, nghỉ đêm, gửi chuồng).
// Khách tự chở hoặc tự nhận (SELF): nhận và trả ngựa ngay tại hub (MAN_HINH 3.5c)
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dispatchApi, type HorseStay } from '@shared/api/dispatch'
import { useLoad } from '@shared/hooks/useLoad'
import { formatDateTime } from '@shared/lib/format'
import { Button } from '@shared/ui/Button'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { Table, type TableColumn } from '@shared/ui/Table'
import { Textarea } from '@shared/ui/Textarea'
import { useToast } from '@shared/ui/toast'
import { PageHeader } from '@shared/ui/PageHeader'
import { localToIso } from '@backoffice/shared/localTime'
import s from '@backoffice/shared/StaffPanels.module.css'
import h from './Hub.module.css'

// Nhận, trả ngựa (quét chip); không nhận (có lý do); hẹn giờ người nhận tới; gửi chuồng khi quá giờ hẹn (phí người nhận chịu)
type Action = { stay: HorseStay; kind: 'receive' | 'refuse' | 'deliver' | 'collect' | 'stable' | 'returnHeld' }

export default function HubPage() {
  const { t } = useTranslation()
  const toast = useToast()
  const [hubChoice, setHubId] = useState('')
  const [action, setAction] = useState<Action>()
  const [chip, setChip] = useState('')
  const [note, setNote] = useState('')
  const [collectAt, setCollectAt] = useState('')
  const [busy, setBusy] = useState(false)
  const [confirmReturn, setConfirmReturn] = useState(false)
  // Hub gốc của xe (trụ sở) đứng đầu và được chọn sẵn
  const { data: hubs } = useLoad(async () => {
    const [locations, vehicles] = await Promise.all([dispatchApi.locations(), dispatchApi.vehicles()])
    const home = new Set(vehicles.map(vehicle => vehicle.homeHubLocationId))
    return locations.filter(location => location.locationType === 'HUB').sort((a, b) => Number(home.has(b.id)) - Number(home.has(a.id)))
  }, [])
  const hubId = hubChoice || (hubs?.[0]?.id ? String(hubs[0].id) : '')
  const { data, loaded, error, reload } = useLoad(async () => {
    if (!hubId) return { appointments: [], stays: [] }
    const [appointments, stays] = await Promise.all([dispatchApi.hubAppointments(Number(hubId)), dispatchApi.horseStays(Number(hubId))])
    return { appointments, stays }
  }, [hubId], { toastOnError: false })

  const open = (stay: HorseStay, kind: Action['kind']) => { setChip(''); setNote(''); setCollectAt(''); setAction({ stay, kind }) }
  const submit = async () => {
    if (!action) return
    setBusy(true)
    try {
      const stayId = action.stay.id ?? 0
      if (action.kind === 'collect') await dispatchApi.setCollectionTime(stayId, localToIso(collectAt))
      else if (action.kind === 'refuse') await dispatchApi.refuseAtHub(stayId, note.trim())
      else if (action.kind === 'stable') await dispatchApi.stableAtHub(stayId, note.trim() || undefined)
      else await (action.kind === 'receive' ? dispatchApi.receiveAtHub : dispatchApi.deliverAtHub)(stayId, chip.trim(), note.trim() || undefined)
      toast(t(`dispatch.stays.${action.kind}Done`), 'success')
      setConfirmReturn(false)
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
    {
      key: 'action',
      header: '',
      render: stay => (
        <span className={s.row}>
          <Button size="sm" variant="primary" onClick={() => open(stay, 'receive')}>{t('dispatch.stays.receive')}</Button>
          <Button size="sm" variant="danger" onClick={() => open(stay, 'refuse')}>{t('dispatch.stays.refuse')}</Button>
        </span>
      ),
    },
  ]
  const columns: TableColumn<HorseStay>[] = [
    { key: 'horse', header: t('dispatch.stays.colHorse'), render: stay => stay.horseName },
    { key: 'booking', header: t('dispatch.stays.colBooking'), render: stay => stay.bookingCode },
    { key: 'type', header: t('dispatch.stays.colType'), render: stay =>
      stay.balanceHoldAt ? t('dispatch.stays.balanceHold') : t(`dispatch.stays.stayType.${stay.stayType}`) },
    { key: 'since', header: t('dispatch.stays.colSince'), render: stay => (stay.actualStartAt ? formatDateTime(new Date(stay.actualStartAt)) : '') },
    {
      key: 'until',
      header: t('dispatch.stays.colUntil'),
      // Gửi chuồng quá hạn giữ (B5): phí vẫn tính mỗi ngày, quản lý quyết định
      render: stay => stay.plannedEndAt && (
        <span className={s.legs}>
          {formatDateTime(new Date(stay.plannedEndAt))}
          {stay.stayType === 'STABLING' && new Date(stay.plannedEndAt).getTime() < Date.now() && <StatusBadge tone="danger" label={t('dispatch.stays.overdue')} />}
        </span>
      ),
    },
    {
      key: 'action',
      header: '',
      // Người nhận tự tới hub; backend kiểm ngựa đã tới bằng chặng cuối. Quá giờ hẹn thì gửi chuồng
      render: stay => {
        if (stay.returnForBalance) {
          return <Button size="sm" onClick={() => open(stay, 'returnHeld')}>{t('dispatch.stays.returnHeld')}</Button>
        }
        if (stay.stayType !== 'STABLING' && (stay.deliveryMethod !== 'SELF' || stay.stayType !== 'ASSEMBLY')) return null
        const overdue = stay.stayType === 'ASSEMBLY' && stay.plannedEndAt && new Date(stay.plannedEndAt).getTime() < Date.now()
        return (
          <span className={s.row}>
            <Button size="sm" onClick={() => open(stay, 'deliver')}>{t('dispatch.stays.deliver')}</Button>
            {stay.stayType === 'ASSEMBLY' && <Button size="sm" variant="ghost" onClick={() => open(stay, 'collect')}>{t('dispatch.stays.collect')}</Button>}
            {overdue && <Button size="sm" variant="danger" onClick={() => open(stay, 'stable')}>{t('dispatch.stays.stable')}</Button>}
          </span>
        )
      },
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
    <div className={h.section}>
      <PageHeader title={t('dispatch.stays.tab')} description={t('dispatch.stays.subtitle')} />
      <div className={h.form}>
        <Field label={t('dispatch.stays.hub')}>
          {c => <Select {...c} value={hubId} onChange={setHubId} placeholder={t('tripWords.choose')}
            options={(hubs ?? []).map(hub => ({ value: `${hub.id}`, label: hub.name ?? '' }))} />}
        </Field>
      </div>
      {content()}
      {action && !confirmReturn && (
        <Modal title={t(`dispatch.stays.${action.kind}Title`, { horse: action.stay.horseName, code: action.stay.bookingCode })} onClose={() => setAction(undefined)}
          footer={(
            <>
              <Button onClick={() => setAction(undefined)}>{t('ui.cancel')}</Button>
              <Button variant={action.kind === 'refuse' ? 'danger' : 'primary'} loading={busy}
                onClick={() => action.kind === 'returnHeld' ? setConfirmReturn(true) : void submit()}
                disabled={action.kind === 'collect' ? !collectAt : action.kind === 'stable' ? false : action.kind === 'refuse' ? !note.trim() : !chip.trim()}>
                {t(`dispatch.stays.${action.kind}`)}
              </Button>
            </>
          )}>
          <p className={s.hint}>{t(`dispatch.stays.${action.kind}Hint`)}</p>
          {(action.kind === 'receive' || action.kind === 'deliver' || action.kind === 'returnHeld') && (
            <Field label={t('dispatch.stays.chip')} required>
              {c => <Input {...c} inputMode="numeric" value={chip} onChange={e => setChip(e.target.value)} data-autofocus />}
            </Field>
          )}
          {action.kind === 'collect' && (
            <Field label={t('dispatch.stays.collectAt')} required>
              {c => <Input {...c} type="datetime-local" value={collectAt} onChange={e => setCollectAt(e.target.value)} data-autofocus />}
            </Field>
          )}
          {action.kind === 'refuse' && (
            <Field label={t('dispatch.stays.reason')} required>
              {c => <Textarea {...c} value={note} onChange={e => setNote(e.target.value)} maxLength={500} data-autofocus />}
            </Field>
          )}
          {action.kind !== 'collect' && action.kind !== 'refuse' && (
            <Field label={t(action.kind === 'stable' ? 'dispatch.stays.stableNote' : 'dispatch.stays.condition')}>
              {c => <Textarea {...c} value={note} onChange={e => setNote(e.target.value)} />}
            </Field>
          )}
        </Modal>
      )}
      {confirmReturn && (
        <ConfirmDialog title={t('dispatch.stays.returnHeld')} message={t('dispatch.stays.confirmReturnHeld')}
          confirmLabel={t('dispatch.stays.returnHeld')} onConfirm={submit} onCancel={() => setConfirmReturn(false)} />
      )}
    </div>
  )
}
