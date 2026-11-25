// Thực hiện lượt xe theo điểm dừng (tài xế, hộ tống, điện thoại): xuất phát, tới, giao nhận và quét chip, rời, kết thúc
import { useState } from 'react'
import { useParams, Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import { tripExecutionApi, type CrewTrip, type Handover, type DeliveryRecipient } from '@shared/api/tripExecution'
import type { IncidentPlan } from '@shared/api/incidentOps'
import { useAutoRefresh, useLoad } from '@shared/hooks/useLoad'
import { formatDateTime } from '@shared/lib/format'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { PageHeader } from '@shared/ui/PageHeader'
import { Skeleton } from '@shared/ui/Skeleton'
import { Textarea } from '@shared/ui/Textarea'
import { StatusBadge, type StatusTone } from '@shared/ui/StatusBadge'
import { useToast } from '@shared/ui/toast'
import { PickupMissDialog } from './PickupMissDialog'
import { HandoverPapersPanel } from './HandoverPapersPanel'
import { HorseFeedingDetails } from './HorseFeedingDetails'
import { StopHealthPanel } from './StopHealthPanel'
import { StableHandoverDialog } from './StableHandoverDialog'
import { TripCompletionDialog } from './TripCompletionDialog'
import { TripIncidentPanel } from './TripIncidentPanel'
import { useTripIncidents } from './useTripIncidents'
import { canArrive, canComplete, canDepart, canRefuse, canReturnUnpaid, canStart, needsRecord, handoverTypesAt, isRestStop, isStableHandover, pendingAt, receivesOriginals, stablePlansAt, stopState, type StopState } from './tripRunModel'
import s from './MyTrips.module.css'

const STATE_TONE: Record<StopState, StatusTone> = { waiting: 'neutral', here: 'info', left: 'success' }

export default function TripRunPage() {
  const { id } = useParams()
  const tripId = Number(id)
  const { t } = useTranslation()
  const toast = useToast()
  const { session } = useAuth()
  const [busy, setBusy] = useState(false)
  const [completing, setCompleting] = useState(false)
  const [deliveryRecipients, setDeliveryRecipients] = useState<Record<number, Record<string, DeliveryRecipient>>>({})
  const [conditions, setConditions] = useState<Record<string, string>>({})
  const [chips, setChips] = useState<Record<number, string>>({})
  const [earlyReasons, setEarlyReasons] = useState<Record<number, string>>({})
  const [failing, setFailing] = useState<number>()
  const [returning, setReturning] = useState<number>()
  const [refusing, setRefusing] = useState<{ handoverId: number; legId: number; name: string }>()
  const [stay, setStay] = useState<{ stopId: number; plan?: IncidentPlan }>()
  const [confirmStay, setConfirmStay] = useState<Handover>()
  const { data, loaded, error, reload, updateData } = useLoad(() => tripExecutionApi.trip(tripId), [tripId], { toastOnError: false })
  useAutoRefresh(reload)
  const incidents = useTripIncidents(tripId, data?.trip)

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
  const tripBlocked = trip.status === 'IN_PROGRESS' && incidents.data?.some(incident => incident.blocksTrip)
  const canMove = incidents.data !== undefined && !incidents.error && !tripBlocked

  const stallOf = (legId: number) => (data.horses ?? []).find(h => h.leg?.id === legId)?.leg?.stallPosition
  const handoverPanel = (handover: Handover) => {
    const handoverId = handover.id ?? 0
    const recipients = deliveryRecipients[handoverId] ?? {}
    const deliveryCodes = handover.handoverType === 'DELIVERY' ? [...new Set(
      (handover.horses ?? []).filter(horse => horse.microchipMatched).map(horse => horse.bookingCode ?? ''),
    )] : []
    const setRecipient = (code: string, change: Partial<DeliveryRecipient>) =>
      setDeliveryRecipients(current => ({
        ...current, [handoverId]: { ...current[handoverId], [code]: { ...current[handoverId]?.[code], ...change } },
      }))
    return (
    <div className={s.body}>
      <StatusBadge tone="info" label={`${t(`myTrips.run.handover.${handover.handoverType}`)} · ${t(`myTrips.run.handoverStatus.${handover.status}`)}`} />
      {handover.partyName && <p className={s.line}>{t(isStableHandover(handover.handoverType) ? 'myTrips.run.stayIn.party' : 'myTrips.run.agentParty', { name: handover.partyName })}</p>}
      <ul className={s.list}>
        {(handover.horses ?? []).map(horse => {
          const legId = horse.horseLegId ?? 0
          const conditionKey = `${handoverId}:${legId}`
          const needsPhoto = handover.handoverType === 'PICKUP' || handover.handoverType === 'DELIVERY'
            || handover.handoverType === 'AGENT' && receivesOriginals(data, handover)
          return (
            <li key={legId} className={s.body}>
              <p className={s.line}>{horse.horseName} · {horse.bookingCode}</p>
              <HorseFeedingDetails horse={horse} />
              {/* Chip đúng thì chỉ chỗ dẫn ngựa vào (MAN_HINH 4.2, 6.1) */}
              {horse.microchipMatched && stallOf(legId) && <p className={s.line}><strong>{t('myTrips.run.stall', { n: stallOf(legId) })}</strong></p>}
              <StatusBadge
                tone={horse.microchipMatched === undefined || horse.microchipMatched === null ? 'neutral' : horse.microchipMatched ? 'success' : 'danger'}
                label={horse.microchipMatched === undefined || horse.microchipMatched === null ? t('myTrips.run.notScanned')
                  : horse.microchipMatched ? t('myTrips.run.matched') : t('myTrips.run.mismatched')}
              />
              <Field label={t('myTrips.run.chip', { name: horse.horseName })}>
                {c => <Input {...c} inputMode="numeric" value={chips[legId] ?? ''} onChange={e => setChips(current => ({ ...current, [legId]: e.target.value }))} />}
              </Field>
              {needsPhoto && <Field label={t('handoverEvidence.condition')} required>
                {c => <Textarea {...c} maxLength={1000} value={conditions[conditionKey] ?? horse.conditionNote ?? ''}
                  onChange={e => setConditions(current => ({ ...current, [conditionKey]: e.target.value }))} />}
              </Field>}
              <Button size="lg" fullWidth disabled={!chips[legId]?.trim() || needsPhoto && !(conditions[conditionKey] ?? horse.conditionNote)?.trim()} loading={busy}
                onClick={() => run(() => tripExecutionApi.scan(handover.id ?? 0, legId, chips[legId].trim(), conditions[conditionKey] ?? horse.conditionNote), t('myTrips.run.done.scanned'))}>
                {t('myTrips.run.scan')}
              </Button>
              {horse.photoAttached && <StatusBadge tone="success" label={t('handoverEvidence.photoAttached')} />}
              {needsPhoto && horse.microchipMatched && <Field label={t('handoverEvidence.photo')} required>
                {c => <Input {...c} type="file" accept="image/*" disabled={busy} onChange={e => {
                  const photo = e.target.files?.[0]
                  if (photo) void run(() => tripExecutionApi.uploadHorsePhoto(handover.id ?? 0, legId, photo), t('handoverEvidence.photoSaved'))
                }} />}
              </Field>}
              {canRefuse(data, handover, legId) && (
                <Button variant="danger" size="lg" fullWidth
                  onClick={() => setRefusing({ handoverId: handover.id ?? 0, legId, name: horse.horseName ?? '' })}>
                  {t('myTrips.run.refusal.refuse')}
                </Button>
              )}
            </li>
          )
        })}
      </ul>
      {deliveryCodes.map(code => <div key={code} className={s.body}>
        <Field label={`${t('handoverEvidence.recipient')} · ${code}`} required>
          {c => <Input {...c} maxLength={120} value={recipients[code]?.recipientName ?? ''}
            onChange={e => setRecipient(code, { recipientName: e.target.value })} />}
        </Field>
        <Field label={t('handoverEvidence.code')}>
          {c => <Input {...c} inputMode="numeric" maxLength={6} value={recipients[code]?.collectionCode ?? ''}
            onChange={e => setRecipient(code, { collectionCode: e.target.value || undefined })} />}
        </Field>
      </div>)}
      {needsRecord(handover) && <HandoverPapersPanel handover={handover} receiving={receivesOriginals(data, handover)} onTrip={next => updateData(() => next)} />}
      {needsRecord(handover) && !handover.recordAttached && <p className={s.muted}>{t('myTrips.run.papers.needRecord')}</p>}
      <Button variant="primary" size="lg" fullWidth loading={busy} disabled={needsRecord(handover) && !handover.recordAttached}
        onClick={() => isStableHandover(handover.handoverType) ? setConfirmStay(handover)
          : run(() => tripExecutionApi.completeHandover(handoverId, handover.handoverType === 'DELIVERY' ? recipients : undefined), t('myTrips.run.done.handed'))}>
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
  }

  return (
    <div className={s.page}>
      <Link to="/my-trips">{t('myTrips.run.back')}</Link>
      <PageHeader title={`${trip.code} · ${t(`tripWords.type.${trip.tripType}`)}`}
        description={`${trip.licensePlate} · ${trip.startLocationName} → ${trip.endLocationName}`} />
      <StatusBadge tone={trip.status === 'COMPLETED' ? 'success' : trip.status === 'IN_PROGRESS' ? 'info' : 'neutral'} label={t(`tripWords.status.${trip.status}`)} />
      {tripBlocked && <p className={s.warning} role="status">{t('myTrips.run.incident.tripBlocked')}</p>}
      {canStart(data) && (
        <Button variant="primary" size="lg" fullWidth loading={busy} onClick={() => run(() => tripExecutionApi.start(tripId), t('myTrips.run.done.started'))}>
          {t('myTrips.run.start')}
        </Button>
      )}
      <ol className={s.list} aria-label={t('myTrips.run.stops')}>
        {(trip.stops ?? []).map(stop => {
          const state = stopState(stop)
          const pending = pendingAt(data, stop.id)
          const stablePlans = stablePlansAt(data, stop, incidents.data ?? [])
          const types = handoverTypesAt(data, stop)
          return (
            <li key={stop.id}>
              <Card title={`${stop.sequenceNumber}. ${stop.locationName ?? `${stop.bookingCode}: ${stop.addressLine}`}`}
                actions={<StatusBadge tone={STATE_TONE[state]} label={t(`myTrips.run.stopState.${state}`)} />}>
                <div className={s.body}>
                  <p className={s.muted}>{stop.plannedArrivalAt ? formatDateTime(new Date(stop.plannedArrivalAt)) : ''}</p>
                  {canMove && canArrive(data, stop) && (
                    <Button variant="primary" size="lg" fullWidth loading={busy}
                      onClick={() => run(() => tripExecutionApi.arrive(stop.id ?? 0), t('myTrips.run.done.arrived'))}>{t('myTrips.run.arrive')}</Button>
                  )}
                  {types.filter(type => (type !== 'SHIFT' || session?.role === 'escort') && (type !== 'STAY_IN' || !stablePlans.length)).map(type => (
                    <Button key={type} size="lg" fullWidth loading={busy}
                      onClick={() => (type === 'STAY_IN' ? setStay({ stopId: stop.id ?? 0 })
                        : run(() => tripExecutionApi.openHandover(stop.id ?? 0, type), t('myTrips.run.done.opened')))}>
                      {t(`myTrips.run.handover.${type}`)}
                    </Button>
                  ))}
                  {!types.includes('STAY_OUT') && (stop.locationId != null ? stablePlans.map(plan => (
                    <Button key={plan.id} size="lg" fullWidth loading={busy} onClick={() => setStay({ stopId: stop.id ?? 0, plan })}>
                      {t('myTrips.run.stayIn.incidentAction', { name: plan.handlerPartyName })}
                    </Button>
                  )) : stablePlans.length > 0 && <p className={s.warning}>{t('myTrips.run.stayIn.customerAddress')}</p>)}
                  {pending && handoverPanel(pending)}
                  {canReturnUnpaid(data, stop) && (
                    <Button variant="danger" size="lg" fullWidth onClick={() => setReturning(stop.id)}>{t('myTrips.run.unpaidReturn.action')}</Button>
                  )}
                  {state === 'here' && isRestStop(stop) && <StopHealthPanel run={data} stop={stop} />}
                  {canMove && canDepart(data, stop) && isRestStop(stop) && (
                    <Field label={t('myTrips.run.earlyReason')}>
                      {c => <Textarea {...c} value={earlyReasons[stop.id ?? 0] ?? ''}
                        onChange={e => setEarlyReasons(current => ({ ...current, [stop.id ?? 0]: e.target.value }))} />}
                    </Field>
                  )}
                  {canMove && canDepart(data, stop) && (
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
      {(trip.status === 'IN_PROGRESS' || trip.status === 'COMPLETED') && <TripIncidentPanel run={data} query={incidents} />}
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
      {stay && <StableHandoverDialog busy={busy} plan={stay.plan} onClose={() => setStay(undefined)}
        canOpen={!stay.plan || !!incidents.data?.some(incident =>
          incident.plans?.at(-1)?.status === 'APPROVED' && incident.plans.at(-1)?.id === stay.plan?.id)}
        onOpen={async (partyId, planId) => {
          if (await run(() => tripExecutionApi.openHandover(stay.stopId, 'STAY_IN', partyId, planId), t('myTrips.run.done.opened'))) setStay(undefined)
        }} />}
      {confirmStay && <ConfirmDialog title={t(`myTrips.run.handover.${confirmStay.handoverType}`)}
        message={t(confirmStay.handoverType === 'STAY_IN' ? 'myTrips.run.stayIn.confirmIn' : 'myTrips.run.stayIn.confirmOut', { name: confirmStay.partyName })}
        confirmLabel={t('myTrips.run.completeHandover')} onCancel={() => setConfirmStay(undefined)}
        onConfirm={async () => {
          if (await run(() => tripExecutionApi.completeHandover(confirmStay.id ?? 0), t('myTrips.run.done.handed'))) {
            await incidents.reload()
            setConfirmStay(undefined)
          }
        }} />}
      {refusing && <PickupMissDialog horseLegId={refusing.legId} name={refusing.name} busy={busy}
        onClose={() => setRefusing(undefined)}
        onSubmit={(request, photo) => {
          void run(() => tripExecutionApi.refuse(refusing.handoverId, request, photo), t('myTrips.run.refusal.done'))
            .then(ok => { if (ok) setRefusing(undefined) })
        }} />}
      {canComplete(data) && (
        <Button variant="primary" size="lg" fullWidth disabled={busy} onClick={() => setCompleting(true)}>
          {t('myTrips.run.complete')}
        </Button>
      )}
      {canComplete(data) && completing && <TripCompletionDialog onCancel={() => setCompleting(false)}
        onConfirm={async (condition, note) => {
          const next = await tripExecutionApi.complete(tripId, condition, note)
          updateData(() => next)
          toast(t('myTrips.run.done.completed'), 'success')
          setCompleting(false)
        }} />}
    </div>
  )
}
