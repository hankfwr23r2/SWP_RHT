// Thực hiện lượt xe theo điểm dừng (tài xế, hộ tống, điện thoại): xuất phát, tới, giao nhận và quét chip, rời, kết thúc
// (docs/MAN_HINH_VA_TAC_DONG.md mục 4.1–4.5, 6.1, 6.5). Nhận ngựa: không nhận con nào thì ghi lý do (4.4)
import { useState } from 'react'
import { useParams, Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { tripExecutionApi, type CrewTrip, type Handover } from '@shared/api/tripExecution'
import { useLoad } from '@shared/hooks/useLoad'
import { formatDateTime } from '@shared/lib/format'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { PageHeader } from '@shared/ui/PageHeader'
import { Skeleton } from '@shared/ui/Skeleton'
import { Textarea } from '@shared/ui/Textarea'
import { StatusBadge, type StatusTone } from '@shared/ui/StatusBadge'
import { useToast } from '@shared/ui/toast'
import { StopHealthPanel } from './StopHealthPanel'
import { TripIncidentPanel } from './TripIncidentPanel'
import { canArrive, canComplete, canDepart, canReturnUnpaid, canStart, handoverTypesAt, isRestStop, pendingAt, stopState, type StopState } from './tripRunModel'
import s from './MyTrips.module.css'

const STATE_TONE: Record<StopState, StatusTone> = { waiting: 'neutral', here: 'info', left: 'success' }

export default function TripRunPage() {
  const { id } = useParams()
  const tripId = Number(id)
  const { t } = useTranslation()
  const toast = useToast()
  const [busy, setBusy] = useState(false)
  const [chips, setChips] = useState<Record<number, string>>({})
  const [earlyReasons, setEarlyReasons] = useState<Record<number, string>>({})
  const [failing, setFailing] = useState<number>()
  const [returning, setReturning] = useState<number>()
  const [refusing, setRefusing] = useState<{ handoverId: number; legId: number; name: string }>()
  const [refusalReason, setRefusalReason] = useState('')
  const { data, loaded, error, reload, updateData } = useLoad(() => tripExecutionApi.trip(tripId), [tripId], { toastOnError: false })

  // Mọi thao tác trả về lượt xe mới; lỗi hiện đúng lý do backend trả về
  const run = async (action: () => Promise<CrewTrip>, done: string) => {
    setBusy(true)
    try {
      const next = await action()
      updateData(() => next)
      toast(done, 'success')
      return true
    } catch (e) {
      toast(e instanceof Error ? e.message : String(e), 'error')
      return false
    } finally {
      setBusy(false)
    }
  }

  if (!loaded) return <Skeleton variant="block" lines={4} />
  if (error || !data?.trip) return <ErrorState title={t('myTrips.run.loadError')} message={error?.message} onRetry={reload} />
  const trip = data.trip

  const handoverPanel = (handover: Handover) => (
    <div className={s.body}>
      <StatusBadge tone="info" label={`${t(`myTrips.run.handover.${handover.handoverType}`)} · ${t(`myTrips.run.handoverStatus.${handover.status}`)}`} />
      {handover.partyName && <p className={s.line}>{t('myTrips.run.agentParty', { name: handover.partyName })}</p>}
      <ul className={s.list}>
        {(handover.horses ?? []).map(horse => {
          const legId = horse.horseLegId ?? 0
          return (
            <li key={legId} className={s.body}>
              <p className={s.line}>{horse.horseName} · {horse.bookingCode}</p>
              <StatusBadge
                tone={horse.microchipMatched === undefined || horse.microchipMatched === null ? 'neutral' : horse.microchipMatched ? 'success' : 'danger'}
                label={horse.microchipMatched === undefined || horse.microchipMatched === null ? t('myTrips.run.notScanned')
                  : horse.microchipMatched ? t('myTrips.run.matched') : t('myTrips.run.mismatched')}
              />
              <Field label={t('myTrips.run.chip', { name: horse.horseName })}>
                {c => <Input {...c} inputMode="numeric" value={chips[legId] ?? ''} onChange={e => setChips(current => ({ ...current, [legId]: e.target.value }))} />}
              </Field>
              <Button size="lg" fullWidth disabled={!chips[legId]?.trim()} loading={busy}
                onClick={() => run(() => tripExecutionApi.scan(handover.id ?? 0, legId, chips[legId].trim()), t('myTrips.run.done.scanned'))}>
                {t('myTrips.run.scan')}
              </Button>
              {handover.handoverType === 'PICKUP' && (
                <Button variant="danger" size="lg" fullWidth
                  onClick={() => { setRefusalReason(''); setRefusing({ handoverId: handover.id ?? 0, legId, name: horse.horseName ?? '' }) }}>
                  {t('myTrips.run.refusal.refuse')}
                </Button>
              )}
            </li>
          )
        })}
      </ul>
      <Button variant="primary" size="lg" fullWidth loading={busy}
        onClick={() => run(() => tripExecutionApi.completeHandover(handover.id ?? 0), t('myTrips.run.done.handed'))}>
        {t('myTrips.run.completeHandover')}
      </Button>
      {handover.handoverType === 'DELIVERY' && (handover.waitStartedAt
        ? (
          <>
            <p className={s.muted}>{t('myTrips.run.delivery.waitingSince', { time: formatDateTime(new Date(handover.waitStartedAt)) })}</p>
            <Button variant="danger" size="lg" fullWidth onClick={() => setFailing(handover.id)}>{t('myTrips.run.delivery.noRecipient')}</Button>
          </>
        )
        : (
          <Button size="lg" fullWidth loading={busy}
            onClick={() => run(() => tripExecutionApi.startWaiting(handover.id ?? 0), t('myTrips.run.delivery.waitStarted'))}>
            {t('myTrips.run.delivery.startWaiting')}
          </Button>
        ))}
    </div>
  )

  return (
    <div className={s.page}>
      <Link to="/my-trips">{t('myTrips.run.back')}</Link>
      <PageHeader title={`${trip.code} · ${t(`dispatch.tripType.${trip.tripType}`)}`}
        description={`${trip.licensePlate} · ${trip.startLocationName} → ${trip.endLocationName}`} />
      <StatusBadge tone={trip.status === 'COMPLETED' ? 'success' : trip.status === 'IN_PROGRESS' ? 'info' : 'neutral'} label={t(`dispatch.tripStatus.${trip.status}`)} />
      {canStart(data) && (
        <Button variant="primary" size="lg" fullWidth loading={busy} onClick={() => run(() => tripExecutionApi.start(tripId), t('myTrips.run.done.started'))}>
          {t('myTrips.run.start')}
        </Button>
      )}
      <ol className={s.list} aria-label={t('myTrips.run.stops')}>
        {(trip.stops ?? []).map(stop => {
          const state = stopState(stop)
          const pending = pendingAt(data, stop.id)
          return (
            <li key={stop.id}>
              <Card title={`${stop.sequenceNumber}. ${stop.locationName ?? `${stop.bookingCode}: ${stop.addressLine}`}`}
                actions={<StatusBadge tone={STATE_TONE[state]} label={t(`myTrips.run.stopState.${state}`)} />}>
                <div className={s.body}>
                  <p className={s.muted}>{stop.plannedArrivalAt ? formatDateTime(new Date(stop.plannedArrivalAt)) : ''}</p>
                  {canArrive(data, stop) && (
                    <Button variant="primary" size="lg" fullWidth loading={busy}
                      onClick={() => run(() => tripExecutionApi.arrive(stop.id ?? 0), t('myTrips.run.done.arrived'))}>{t('myTrips.run.arrive')}</Button>
                  )}
                  {handoverTypesAt(data, stop).map(type => (
                    <Button key={type} size="lg" fullWidth loading={busy}
                      onClick={() => run(() => tripExecutionApi.openHandover(stop.id ?? 0, type), t('myTrips.run.done.opened'))}>
                      {t(`myTrips.run.handover.${type}`)}
                    </Button>
                  ))}
                  {pending && handoverPanel(pending)}
                  {canReturnUnpaid(data, stop) && (
                    <Button variant="danger" size="lg" fullWidth onClick={() => setReturning(stop.id)}>{t('myTrips.run.unpaidReturn.action')}</Button>
                  )}
                  {state === 'here' && isRestStop(stop) && <StopHealthPanel run={data} stop={stop} />}
                  {canDepart(data, stop) && isRestStop(stop) && (
                    <Field label={t('myTrips.run.earlyReason')}>
                      {c => <Textarea {...c} value={earlyReasons[stop.id ?? 0] ?? ''}
                        onChange={e => setEarlyReasons(current => ({ ...current, [stop.id ?? 0]: e.target.value }))} />}
                    </Field>
                  )}
                  {canDepart(data, stop) && (
                    <Button size="lg" fullWidth loading={busy}
                      onClick={() => run(() => tripExecutionApi.depart(stop.id ?? 0, earlyReasons[stop.id ?? 0]?.trim() || undefined), t('myTrips.run.done.departed'))}>
                      {t('myTrips.run.depart')}
                    </Button>
                  )}
                </div>
              </Card>
            </li>
          )
        })}
      </ol>
      {trip.status === 'IN_PROGRESS' && <TripIncidentPanel run={data} />}
      {failing !== undefined && (
        <ConfirmDialog
          tone="danger"
          title={t('myTrips.run.delivery.noRecipientTitle')}
          message={t('myTrips.run.delivery.noRecipientMessage')}
          confirmLabel={t('myTrips.run.delivery.noRecipient')}
          onCancel={() => setFailing(undefined)}
          onConfirm={async () => {
            const next = await tripExecutionApi.failDelivery(failing).catch(e => { toast(e instanceof Error ? e.message : String(e), 'error'); throw e })
            updateData(() => next)
            toast(t('myTrips.run.delivery.failed'), 'success')
            setFailing(undefined)
          }}
        />
      )}
      {returning !== undefined && (
        <ConfirmDialog
          tone="danger"
          title={t('myTrips.run.unpaidReturn.title')}
          message={t('myTrips.run.unpaidReturn.message')}
          confirmLabel={t('myTrips.run.unpaidReturn.action')}
          onCancel={() => setReturning(undefined)}
          onConfirm={async () => {
            const next = await tripExecutionApi.openHandover(returning, 'RETURN').catch(e => { toast(e instanceof Error ? e.message : String(e), 'error'); throw e })
            updateData(() => next)
            toast(t('myTrips.run.unpaidReturn.done'), 'success')
            setReturning(undefined)
          }}
        />
      )}
      {refusing && (
        <Modal title={t('myTrips.run.refusal.title', { name: refusing.name })} onClose={() => setRefusing(undefined)}
          footer={(
            <>
              <Button onClick={() => setRefusing(undefined)}>{t('ui.cancel')}</Button>
              <Button variant="danger" disabled={!refusalReason.trim()} loading={busy}
                onClick={() => run(() => tripExecutionApi.refuse(refusing.handoverId, refusing.legId, refusalReason.trim()), t('myTrips.run.refusal.done'))
                  .then(ok => { if (ok) setRefusing(undefined) })}>
                {t('myTrips.run.refusal.refuse')}
              </Button>
            </>
          )}>
          <p className={s.muted}>{t('myTrips.run.refusal.message')}</p>
          <Field label={t('myTrips.run.refusal.reason')} required>
            {c => <Textarea {...c} value={refusalReason} onChange={e => setRefusalReason(e.target.value)} data-autofocus />}
          </Field>
        </Modal>
      )}
      {canComplete(data) && (
        <Button variant="primary" size="lg" fullWidth loading={busy} onClick={() => run(() => tripExecutionApi.complete(tripId), t('myTrips.run.done.completed'))}>
          {t('myTrips.run.complete')}
        </Button>
      )}
    </div>
  )
}
