import { ListPanel } from '@shared/ui/ListPanel'
import { StatTile } from '@shared/ui/StatTile'
// Ngựa ở một hub (điều phối viên): lịch hẹn khách tự chở tới, ngựa đang lưu trú (tập kết, nghỉ đêm, gửi chuồng).
// Khách tự chở hoặc tự nhận (SELF): nhận và trả ngựa ngay tại hub (MAN_HINH 3.5c)
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dispatchApi, type HorseStay } from '@shared/api/dispatch'
import { hubStallsApi } from '@shared/api/hubStalls'
import { HubStallMoveDialog } from './HubStallMoveDialog'
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
import { HealthCheckTaskAction } from '@backoffice/shared/HealthCheckTaskAction'
import { StayHealthAction } from '@backoffice/shared/StayHealthAction'
import { HubStablingActions } from './HubStablingActions'

// Nhận, trả ngựa (quét chip); không nhận (có lý do); hẹn giờ người nhận tới; gửi chuồng khi quá giờ hẹn (phí người nhận chịu)
type Action = { stay: HorseStay; kind: 'receive' | 'refuse' | 'deliver' | 'collect' | 'stable' | 'returnHeld' }

export default function HubPage() {
  const { t } = useTranslation()
  const toast = useToast()
  const [hubChoice, setHubId] = useState('')
  const [action, setAction] = useState<Action>()
  const [moving, setMoving] = useState<HorseStay>()
  const [photo, setPhoto] = useState<File>()
  const [record, setRecord] = useState<File>()
  const [recipientName, setRecipientName] = useState('')
  const [collectionCode, setCollectionCode] = useState('')
  const [marked, setMarked] = useState<Record<number, boolean>>({})
  const [chip, setChip] = useState('')
  const [note, setNote] = useState('')
  const [collectAt, setCollectAt] = useState('')
  const [busy, setBusy] = useState(false)
  const [confirmReturn, setConfirmReturn] = useState(false)
  // Hub gốc của xe (trụ sở) đứng đầu và được chọn sẵn
  const { data: hubs, loaded: hubsLoaded, error: hubsError, reload: reloadHubs } = useLoad(async () => {
    const [locations, vehicles] = await Promise.all([dispatchApi.locations(), dispatchApi.vehicles()])
    const home = new Set(vehicles.map(vehicle => vehicle.homeHubLocationId))
    return locations.filter(location => location.locationType === 'HUB').sort((a, b) => Number(home.has(b.id)) - Number(home.has(a.id)))
  }, [], { toastOnError: false })
  const hubId = hubChoice || (hubs?.[0]?.id ? String(hubs[0].id) : '')
  const { data, loaded, error, reload } = useLoad(async () => {
    if (!hubId) return { appointments: [], stays: [], inventory: undefined }
    const id = Number(hubId)
    const [appointments, stays, inventory] = await Promise.all([
      dispatchApi.hubAppointments(id), dispatchApi.horseStays(id), hubStallsApi.inventory(id),
    ])
    return { appointments, stays, inventory }
  }, [hubId], { toastOnError: false })

  const isHubHandover = action?.kind === 'receive' || action?.kind === 'deliver' || action?.kind === 'returnHeld'
  const { data: papers, loaded: papersLoaded, error: papersError, reload: reloadPapers } = useLoad(
    () => isHubHandover && action?.stay.id
      ? dispatchApi.hubHandoverPapers(action.stay.id, action.kind === 'receive')
      : Promise.resolve([]),
    [action?.stay.id, action?.kind], { toastOnError: false },
  )
  const papersMarked = papersLoaded && !papersError && (papers ?? []).every(paper => paper.documentId !== undefined && paper.documentId in marked)

  const open = (stay: HorseStay, kind: Action['kind']) => { setMarked({}); setRecipientName(''); setCollectionCode(''); setPhoto(undefined); setRecord(undefined); setChip(''); setNote(''); setCollectAt(''); setAction({ stay, kind }) }
  const submit = async () => {
    if (!action) return
    setBusy(true)
    try {
      const stayId = action.stay.id ?? 0
      if (action.kind === 'collect') await dispatchApi.setCollectionTime(stayId, localToIso(collectAt))
      else if (action.kind === 'refuse') await dispatchApi.refuseAtHub(stayId, note.trim())
      else if (action.kind === 'stable') await dispatchApi.stableAtHub(stayId, note.trim() || undefined)
      else {
        if (!photo || !record) return
        await (action.kind === 'receive' ? dispatchApi.receiveAtHub : dispatchApi.deliverAtHub)(stayId, {
          microchip: chip.trim(), conditionNote: note.trim(),
          recipient: action.kind === 'deliver' ? { recipientName: recipientName.trim(), collectionCode: collectionCode || undefined } : undefined,
          papers: Object.entries(marked).map(([id, originalMissing]) => ({ documentId: Number(id), originalMissing })),
        }, photo, record)
      }
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
    { key: 'coordinator', header: t('coordAssignment.owner'), render: stay => stay.responsibleCoordinatorName ?? t('coordAssignment.unassigned') },
    { key: 'booking', header: t('dispatch.stays.colBooking'), render: stay => stay.bookingCode },
    { key: 'stall', header: t('hubStalls.title'), render: stay => stay.canChangeHubStall && (
      <div className={s.legs}>
        <span>{stay.hubStallCode ?? t('hubStalls.unassigned')}</span>
        {stay.suggestIsolation && <StatusBadge tone="warning" label={t('hubStalls.suggestIsolation')} />}
        <Button size="sm" onClick={() => setMoving(stay)}>{t('hubStalls.move')}</Button>
      </div>
    ) },
    { key: 'feeding', header: t('horseFeeding.title'), render: stay => (
      <div className={h.feeding}>
        <div>{stay.dietNote?.trim() || t('horseFeeding.none')}</div>
        <div>{stay.feedingsPerDay == null ? t('horseFeeding.mealsUnknown') : t('horseFeeding.meals', { count: stay.feedingsPerDay })}</div>
        <div>{t(stay.feedSuppliedByOwner ? 'horseFeeding.owner' : 'horseFeeding.company')}</div>
      </div>
    ) },
    { key: 'type', header: t('dispatch.stays.colType'), render: stay =>
      stay.balanceHoldAt ? t('dispatch.stays.balanceHold') : t(`dispatch.stays.stayType.${stay.stayType}`) },
    { key: 'health', header: t('myTrips.run.health.status'), render: stay =>
      stay.id != null && stay.bookingHorseId != null && (
        <span className={s.row}>
          <StayHealthAction horseStayId={stay.id} bookingHorseId={stay.bookingHorseId}
            horseName={stay.horseName} reported onSaved={reload} />
          <HealthCheckTaskAction horseStayId={stay.id} bookingHorseId={stay.bookingHorseId} horseName={stay.horseName} />
        </span>
      ) },
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
            {stay.stayType === 'STABLING' && stay.id != null && <HubStablingActions
              stayId={stay.id} readyAt={stay.readyAt} onChanged={reload} />}
            {stay.stayType === 'ASSEMBLY' && <Button size="sm" variant="ghost" onClick={() => open(stay, 'collect')}>{t('dispatch.stays.collect')}</Button>}
            {overdue && <Button size="sm" variant="danger" onClick={() => open(stay, 'stable')}>{t('dispatch.stays.stable')}</Button>}
          </span>
        )
      },
    },
  ]

  const content = () => {
    if (!hubsLoaded) return <Skeleton variant="block" lines={3} />
    if (hubsError) return <ErrorState message={hubsError.message} onRetry={reloadHubs} />
    if (!hubId) return <EmptyState compact title={t('hubStalls.noHubs')} description={t('hubStalls.noHubsHint')} />
    if (!loaded) return <Skeleton variant="block" lines={3} />
    if (error || !data) return <ErrorState message={error?.message} onRetry={reload} />
    return (
      <div className={h.section}>
        {data.inventory?.available != null && data.inventory.total != null && <StatTile
          icon={<i className="fa-solid fa-warehouse" />} label={t('listPanel.freeStalls')}
          value={`${data.inventory.available}/${data.inventory.total}`} />}
        {data.appointments.length > 0 && (
          <Table caption={t('dispatch.stays.appointmentsCaption')} columns={appointmentColumns} rows={data.appointments} rowKey={stay => stay.id ?? 0} />
        )}
        {data.stays.length
          ? <Table caption={t('dispatch.stays.caption')} columns={columns} rows={data.stays} rowKey={stay => stay.id ?? 0} />
          : <EmptyState compact icon={<i className="fa-solid fa-warehouse" />} title={t('dispatch.stays.emptyTitle')} description={t('dispatch.stays.emptyDescription')} />}
      </div>
    )
  }

  return (
    <div className={h.section}>
      <PageHeader title={t('dispatch.stays.tab')} description={t('dispatch.stays.subtitle')} />
      <ListPanel count={data ? data.stays.length + data.appointments.length : undefined} toolbar={<div className={h.form}>
        <Field label={t('dispatch.stays.hub')}>
          {c => <Select {...c} value={hubId} onChange={value => { setMoving(undefined); setHubId(value) }} placeholder={t('tripWords.choose')} disabled={!hubs?.length}
            options={(hubs ?? []).map(hub => ({ value: `${hub.id}`, label: hub.name ?? '' }))} />}
        </Field>
      </div>}>
        {content()}
      </ListPanel>
      {moving?.id != null && data?.inventory && <HubStallMoveDialog stayId={moving.id} horseName={moving.horseName}
        stalls={data.inventory.stalls ?? []} onMoved={reload} onClose={() => setMoving(undefined)} />}
      {action && !confirmReturn && (
        <Modal title={t(`dispatch.stays.${action.kind}Title`, { horse: action.stay.horseName, code: action.stay.bookingCode })} onClose={() => setAction(undefined)}
          footer={(
            <>
              <Button onClick={() => setAction(undefined)}>{t('ui.cancel')}</Button>
              <Button variant={action.kind === 'refuse' ? 'danger' : 'primary'} loading={busy}
                onClick={() => action.kind === 'returnHeld' ? setConfirmReturn(true) : void submit()}
                disabled={action.kind === 'collect' ? !collectAt : action.kind === 'stable' ? false : action.kind === 'refuse' ? !note.trim() : !chip.trim() || !note.trim() || !photo || !record || !papersMarked || action.kind === 'deliver' && !recipientName.trim()}>
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
          {isHubHandover && (
            <>
              <Field label={t('handoverEvidence.photo')} required>
                {c => <Input {...c} type="file" accept="image/*" onChange={e => setPhoto(e.target.files?.[0])} />}
              </Field>
              <Field label={t('handoverEvidence.record')} required>
                {c => <Input {...c} type="file" accept="image/*,application/pdf" onChange={e => setRecord(e.target.files?.[0])} />}
              </Field>
            </>
          )}
          {isHubHandover && !papersLoaded && <Skeleton variant="block" lines={2} />}
          {isHubHandover && papersError && <ErrorState message={papersError.message} onRetry={reloadPapers} />}
          {isHubHandover && (papers ?? []).map(paper => {
            const id = paper.documentId ?? 0
            return <Field key={id} label={`${paper.documentTypeName} · ${paper.ownerLabel}`} required>
              {c => <Select {...c} value={id in marked ? String(marked[id]) : ''} placeholder={t('tripWords.choose')}
                onChange={value => setMarked(current => ({ ...current, [id]: value === 'true' }))}
                options={action.kind === 'receive' ? [
                  { value: 'false', label: t('myTrips.run.papers.receive') },
                  { value: 'true', label: t('handoverEvidence.originalMissing') },
                ] : [{ value: 'false', label: t('myTrips.run.papers.return') }]} />}
            </Field>
          })}
          {action.kind === 'deliver' && (
            <>
              <Field label={t('handoverEvidence.recipient')} required>
                {c => <Input {...c} maxLength={120} value={recipientName} onChange={e => setRecipientName(e.target.value)} />}
              </Field>
              <Field label={t('handoverEvidence.code')}>
                {c => <Input {...c} inputMode="numeric" maxLength={6} value={collectionCode} onChange={e => setCollectionCode(e.target.value)} />}
              </Field>
            </>
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
