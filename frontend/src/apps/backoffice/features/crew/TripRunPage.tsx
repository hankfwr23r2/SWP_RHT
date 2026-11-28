// Thực hiện lượt xe theo điểm dừng (tài xế, hộ tống, điện thoại): xuất phát, tới, giao nhận và quét chip, rời, kết thúc
import { useState } from 'react'
import { useParams, Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import { tripExecutionApi, type CrewTrip, type Handover } from '@shared/api/tripExecution'
import type { IncidentPlan } from '@shared/api/incidentOps'
import { useAutoRefresh, useLoad } from '@shared/hooks/useLoad'
import { formatDateTime } from '@shared/lib/format'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { PageHeader } from '@shared/ui/PageHeader'
import { Skeleton } from '@shared/ui/Skeleton'
import { Textarea } from '@shared/ui/Textarea'
import { StatusBadge, type StatusTone } from '@shared/ui/StatusBadge'
import { useToast } from '@shared/ui/toast'
import { TripReturnDetails } from '@backoffice/shared/TripReturnDetails'
import { PickupMissDialog } from './PickupMissDialog'
import { TripHandoverPanel } from './TripHandoverPanel'
import { StopHealthPanel } from './StopHealthPanel'
import { StableHandoverDialog } from './StableHandoverDialog'
import { TripStartForm } from './TripStartForm'
import { TripCompletionDialog } from './TripCompletionDialog'
import { TripIncidentPanel } from './TripIncidentPanel'
import { useTripIncidents } from './useTripIncidents'
import { canArrive, canComplete, canDepart, canReturnUnpaid, canStart, handoverTypesAt, isRestStop, pendingAt, stablePlansAt, stopState, type StopState } from './tripRunModel'
import s from './MyTrips.module.css'

const STATE_TONE: Record<StopState, StatusTone> = { waiting: 'neutral', here: 'info', left: 'success' }

export default function TripRunPage() {
  const { id } = useParams()
  const tripId = Number(id)
  const { t } = useTranslation()
  const toast = useToast()
  const { session } = useAuth()
  const driver = session?.role === 'driver'
  const escort = session?.role === 'escort'
  const [busy, setBusy] = useState(false)
  const [completing, setCompleting] = useState(false)
  const [earlyReasons, setEarlyReasons] = useState<Record<number, string>>({})
  const [failing, setFailing] = useState<number>()
  const [returning, setReturning] = useState<number>()
  const [refusing, setRefusing] = useState<{ handoverId: number; legId: number; name: string }>()
  const [stay, setStay] = useState<{ stopId: number; plan?: IncidentPlan }>()
  const [confirmStay, setConfirmStay] = useState<Handover>()
  const { data, loaded, error, reload, updateData } = useLoad(() => tripExecutionApi.trip(tripId), [tripId], { toastOnError: false })
  useAutoRefresh(reload)
  const parkedStopId = data?.trip?.stops?.find(stop => canDepart(data, stop))?.id
  const readiness = useLoad(() => driver && parkedStopId != null
    ? tripExecutionApi.departureReadiness(tripId) : Promise.resolve(undefined), [driver, tripId, parkedStopId], { toastOnError: false })
  useAutoRefresh(readiness.reload)
  const incidents = useTripIncidents(tripId, data?.trip)

  // Mọi thao tác trả về lượt xe mới; lỗi hiện đúng lý do backend trả về
  const run = async (action: () => Promise<CrewTrip>, done: string, onFields?: (fields: Record<string, string>) => void) => {
    setBusy(true)
    try {
      const next = await action()
      updateData(() => next)
      toast(done, 'success')
      return true
    } catch (e) {
      if (e instanceof Error && 'fields' in e) onFields?.(e.fields as Record<string, string>)
      toast(e instanceof Error ? e.message : String(e), 'error')
      return false
    } finally {
      setBusy(false)
    }
  }

  if (!loaded) return <Skeleton variant="block" lines={4} />
  if (error || !data?.trip) return <ErrorState title={t('myTrips.run.loadError')} message={error?.message} onRetry={reload} />
  const trip = data.trip
  const readyToComplete = canComplete(data)
  const tripBlocked = trip.status === 'IN_PROGRESS' && incidents.data?.some(incident => incident.blocksTrip)
  const canMove = incidents.data !== undefined && !incidents.error && !tripBlocked
  const stops = trip.stops ?? []
  const currentStop = stops.find(stop => stopState(stop) === 'here') ?? stops.find(stop => canArrive(data, stop))


  return (
    <div className={s.page}>
      <Link to="/my-trips">{t('myTrips.run.back')}</Link>
      <PageHeader title={`${trip.code} · ${t(`tripWords.type.${trip.tripType}`)}`}
        description={`${trip.licensePlate} · ${trip.startLocationName} → ${trip.endLocationName}`} />
      {!trip.returning && <StatusBadge tone={trip.status === 'COMPLETED' ? 'success' : trip.status === 'IN_PROGRESS' ? 'info' : 'neutral'} label={t(`tripWords.status.${trip.status}`)} />}
      <TripReturnDetails trip={trip} />
      {tripBlocked && <p className={s.warning} role="status">{t('myTrips.run.incident.tripBlocked')}</p>}
      {driver && canStart(data) && <TripStartForm busy={busy}
        onStart={reason => run(() => tripExecutionApi.start(tripId, reason), t('myTrips.run.done.started'))} />}
      {!stops.length && <EmptyState compact title={t('myTrips.run.emptyStops')} description={t('myTrips.run.emptyStopsHint')} />}
      {!!stops.length && <ol className={s.list} aria-label={t('myTrips.run.stops')}>
        {stops.map(stop => {
          const state = stopState(stop)
          const pending = pendingAt(data, stop.id)
          const pickupCompleted = pending?.handoverType !== 'PICKUP' && data.handovers?.some(handover =>
            handover.tripStopId === stop.id && handover.handoverType === 'PICKUP' && handover.status === 'DONE')
          const stablePlans = escort ? stablePlansAt(data, stop, incidents.data ?? []) : []
          const types = escort ? handoverTypesAt(data, stop) : []
          const showDeparture = driver && canMove && canDepart(data, stop)
          const readinessChecked = readiness.loaded && !readiness.error && readiness.data?.stopId === stop.id
          const waitingForEscort = readinessChecked && readiness.data?.waitingForEscort === true
          const readyToDepart = readinessChecked && readiness.data?.waitingForEscort === false
          return (
            <li key={stop.id} aria-current={stop === currentStop ? 'step' : undefined}>
              <Card className={stop === currentStop ? s.currentStop : s.stopCard} title={`${stop.sequenceNumber}. ${stop.locationName ?? `${stop.bookingCode}: ${stop.addressLine}`}`}
                actions={<StatusBadge tone={STATE_TONE[state]} label={t(`myTrips.run.stopState.${state}`)} />}>
                <div className={s.body}>
                  {stop.plannedArrivalAt && <time className={s.muted} dateTime={stop.plannedArrivalAt}>{formatDateTime(new Date(stop.plannedArrivalAt))}</time>}
                  {pickupCompleted && <StatusBadge tone="success" label={`${t('myTrips.run.handover.PICKUP')} · ${t('myTrips.run.handoverStatus.DONE')}`} />}
                  {escort && stop === currentStop && state === 'waiting' && <p className={s.muted}>{t('myTrips.run.waitingForDriver')}</p>}
                  {driver && canMove && canArrive(data, stop) && (
                    <Button variant="primary" size="lg" fullWidth loading={busy}
                      onClick={() => run(() => tripExecutionApi.arrive(stop.id ?? 0), t('myTrips.run.done.arrived'))}>{t('myTrips.run.arrive')}</Button>
                  )}
                  {types.filter(type => type !== 'STAY_IN' || !stablePlans.length).map(type => (
                    <Button key={type} variant={types.length === 1 ? 'primary' : 'secondary'} size="lg" fullWidth loading={busy}
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
                  {escort && pending && <TripHandoverPanel key={pending.id} handover={pending} horses={data.horses} busy={busy}
                    onScan={(request, onFields) => run(() => tripExecutionApi.scan(pending.id ?? 0, request), t('myTrips.run.done.scanned'), onFields)}
                    onPhoto={(legId, photo) => run(() => tripExecutionApi.uploadHorsePhoto(pending.id ?? 0, legId, photo), t('handoverEvidence.photoSaved'))}
                    onComplete={recipients => run(() => tripExecutionApi.completeHandover(pending.id ?? 0, recipients), t('myTrips.run.done.handed'))}
                    onStartWaiting={() => run(() => tripExecutionApi.startWaiting(pending.id ?? 0), t('myTrips.run.delivery.waitStarted'))}
                    onTrip={next => updateData(() => next)} onRefuse={setRefusing} onConfirmStay={setConfirmStay} onFail={setFailing} />}
                  {escort && canReturnUnpaid(data, stop) && (
                    <Button variant="danger" size="lg" fullWidth onClick={() => setReturning(stop.id)}>{t('myTrips.run.unpaidReturn.action')}</Button>
                  )}
                  {escort && state === 'here' && isRestStop(stop) && <StopHealthPanel run={data} stop={stop} />}
                  {showDeparture && readiness.error && <ErrorState title={t('myTrips.run.departureReadinessError')} onRetry={readiness.reload} />}
                  {showDeparture && waitingForEscort && <p className={s.warning} role="status">{t('myTrips.run.waitingForEscort')}</p>}
                  {showDeparture && readyToDepart && isRestStop(stop) && (
                    <Field label={t('myTrips.run.earlyReason')}>
                      {c => <Textarea {...c} value={earlyReasons[stop.id ?? 0] ?? ''}
                        onChange={e => setEarlyReasons(current => ({ ...current, [stop.id ?? 0]: e.target.value }))} />}
                    </Field>
                  )}
                  {showDeparture && (
                    <Button variant="primary" size="lg" fullWidth loading={busy} disabled={!readyToDepart}
                      onClick={() => run(() => tripExecutionApi.depart(stop.id ?? 0, earlyReasons[stop.id ?? 0]?.trim() || undefined), t('myTrips.run.done.departed'))}>
                      {t('myTrips.run.depart')}
                    </Button>
                  )}
                </div>
              </Card>
            </li>
          )
        })}
      </ol>}
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
      {driver && (trip.returning || readyToComplete) && (
        <Button variant="primary" size="lg" fullWidth disabled={busy || !readyToComplete} onClick={() => setCompleting(true)}>
          {t('myTrips.run.backAtHub')}
        </Button>
      )}
      {readyToComplete && completing && <TripCompletionDialog trip={trip} onCancel={() => setCompleting(false)}
        onUpload={async photo => {
          const next = await tripExecutionApi.uploadReturnPhoto(tripId, photo)
          updateData(() => next)
        }}
        onConfirm={async (condition, note) => {
          const next = await tripExecutionApi.complete(tripId, condition, note)
          updateData(() => next)
          toast(t('myTrips.run.done.completed'), 'success')
          setCompleting(false)
        }} />}
    </div>
  )
}
