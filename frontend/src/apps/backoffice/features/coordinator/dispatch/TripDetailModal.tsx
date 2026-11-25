// Chi tiết một lượt xe: điểm dừng, tổ lái (phân công, gỡ), ngựa trên xe (xếp, gỡ), hủy lượt
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import { dispatchApi, type CrewAssignment, type DispatchBooking, type TripHorse } from '@shared/api/dispatch'
import { useLoad } from '@shared/hooks/useLoad'
import { Button } from '@shared/ui/Button'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Modal } from '@shared/ui/Modal'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { Textarea } from '@shared/ui/Textarea'
import { useToast } from '@shared/ui/toast'
import { TRIP_STATUS_TONE, eligibleHorses, peakSeats } from './dispatchModel'
import { StallPlanSection } from './StallPlanSection'
import { TripMap } from './TripMap'
import { TripTimeline } from './TripTimeline'
import { TripTakeOverButton } from './TripTakeOverButton'
import { TripCrewSection } from './TripCrewSection'
import { TripEditButton } from './TripEditButton'
import b from './Board.module.css'
import s from './Dispatch.module.css'

interface Props {
  tripId: number
  bookings: DispatchBooking[]
  onClose: () => void
  onChanged: () => void
}

type Pending = { kind: 'release'; member: CrewAssignment } | { kind: 'removeHorse'; item: TripHorse } | { kind: 'cancel' }

export function TripDetailModal({ tripId, bookings, onClose, onChanged }: Props) {
  const { t } = useTranslation()
  const { session } = useAuth()
  const toast = useToast()
  const [horseKey, setHorseKey] = useState('')
  const [pending, setPending] = useState<Pending>()
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const { data, loaded, error, reload } = useLoad(async () => {
    const [trip, horses] = await Promise.all([dispatchApi.trip(tripId), dispatchApi.horsesOnTrip(tripId)])
    return { trip, horses }
  }, [tripId], { toastOnError: false })
  const stallName = (seats?: number) => t(seats === 2 ? 'dispatch.stall.wide' : 'dispatch.stall.standard')

  // Chạy một thao tác ghi: báo kết quả, tải lại lượt xe và bảng điều độ; lỗi thì hiện đúng lý do backend trả về
  const run = async (action: () => Promise<unknown>, done: string) => {
    setBusy(true)
    try {
      await action()
      toast(done, 'success')
      onChanged()
      await reload()
    } catch (e) {
      toast(e instanceof Error ? e.message : String(e), 'error')
      throw e
    } finally {
      setBusy(false)
    }
  }

  const mapStops = useMemo(() => (data?.trip.stops ?? []).map(stop => ({
    name: stop.locationName ?? `${stop.bookingCode}: ${stop.addressLine}`, latitude: stop.latitude, longitude: stop.longitude,
  })), [data])

  if (!loaded) return <Modal title={t('dispatch.trips.view')} onClose={onClose} wide><Skeleton variant="block" lines={4} /></Modal>
  if (error || !data) return <Modal title={t('dispatch.trips.view')} onClose={onClose} wide><ErrorState message={error?.message} onRetry={reload} /></Modal>

  const { trip, horses } = data
  const owner = trip.coordinatorStaffCode != null && trip.coordinatorStaffCode === session?.staffCode
  const readOnly = trip.coordinatorId != null && !owner
  const canTakeOver = !owner && trip.takeOverAllowed
  const editable = !readOnly && (trip.status === 'PLANNED' || trip.status === 'DISPATCHED')
  const transfer = trip.tripType === 'TRANSFER'
  const options = eligibleHorses(trip, bookings, horses)
  const chosen = options.find(option => `${option.horse.bookingHorseId}` === horseKey)

  return (
    <Modal
      wide
      title={`${trip.code} · ${t(`tripWords.type.${trip.tripType}`)}`}
      subtitle={`${trip.licensePlate} · ${trip.startLocationName} → ${trip.endLocationName}`}
      onClose={onClose}
      footer={editable && <Button variant="danger" onClick={() => { setReason(''); setPending({ kind: 'cancel' }) }}>{t('dispatch.detail.cancelTrip')}</Button>}
    >
      <div className={s.section}>
        <div className={s.sectionHead}>
          <StatusBadge tone={TRIP_STATUS_TONE[trip.status ?? 'PLANNED']} label={t(`tripWords.status.${trip.status}`)} />
          {!editable && <span className={s.muted}>{t('dispatch.detail.locked')}</span>}
          <span>{trip.coordinatorName ? t('coordAssignment.ownedBy', { name: trip.coordinatorName }) : t('coordAssignment.unassigned')}</span>
          {canTakeOver && <TripTakeOverButton tripId={tripId} onSaved={async () => { onChanged(); await reload() }} />}
          {owner && trip.status === 'PLANNED' && trip.actualStartAt == null && (
            <TripEditButton tripId={tripId} trip={trip} onSaved={async () => { onChanged(); await reload() }} />
          )}
        </div>
      </div>

      <section className={s.section} aria-labelledby="trip-stops">
        <h3 id="trip-stops" className={s.sectionTitle}>{t('dispatch.detail.stops')}</h3>
        <div className={b.plan}>
          <TripTimeline label={t('dispatch.detail.stops')} stops={(trip.stops ?? []).map((stop, index, all) => {
            const before = all[index - 1]?.plannedDepartureAt
            return {
              key: stop.id ?? index, name: mapStops[index]?.name ?? '', arrivalAt: stop.plannedArrivalAt, departureAt: stop.plannedDepartureAt,
              driveMinutes: before && stop.plannedArrivalAt ? Math.round((Date.parse(stop.plannedArrivalAt) - Date.parse(before)) / 60_000) : 0,
            }
          })} />
          <TripMap stops={mapStops} />
        </div>
      </section>

      <TripCrewSection key={`${tripId}-${trip.status}`} tripId={tripId} trip={trip} busy={busy} readOnly={readOnly}
        onAssign={(staffId, role) => run(() => dispatchApi.assignCrew(tripId, staffId, role), t('dispatch.done.assigned'))}
        onRelease={member => setPending({ kind: 'release', member })} />

      <section className={s.section} aria-labelledby="trip-horses">
        <div className={s.sectionHead}>
          <h3 id="trip-horses" className={s.sectionTitle}>{t('dispatch.detail.horses')}</h3>
          <span className={s.muted}>{t('dispatch.detail.seats', { used: peakSeats(trip, horses), total: trip.vehicleSeats })}</span>
        </div>
        {horses.length ? (
          <ul className={s.list}>
            {horses.map(item => (
              <li key={item.leg?.id} className={s.item}>
                <span className={s.itemMain}>
                  <strong>{item.horseName} · {item.bookingCode}</strong>
                  <span className={s.muted}>{`${item.leg?.fromName} → ${item.leg?.toName} · ${stallName(item.seats)}`}
                    {item.leg?.stallPosition ? ` · ${t('dispatch.stall.number', { n: item.leg.stallPosition })}` : ''}</span>
                </span>
                {editable && <Button size="sm" onClick={() => setPending({ kind: 'removeHorse', item })}>{t('dispatch.detail.release')}</Button>}
              </li>
            ))}
          </ul>
        ) : <p className={s.muted}>{t(transfer ? 'dispatch.detail.emptyTransfer' : 'dispatch.detail.noHorses')}</p>}
        {editable && !transfer && (options.length ? (
          <div className={s.form}>
            <Field label={t('dispatch.detail.horse')}>
              {c => <Select {...c} value={horseKey} onChange={setHorseKey} placeholder={t('tripWords.choose')}
                options={options.map(option => ({ value: `${option.horse.bookingHorseId}`, label: `${option.horse.horseName} · ${option.booking.code} · ${stallName(option.horse.seats)}` }))} />}
            </Field>
            <Button variant="primary" disabled={!chosen} loading={busy}
              onClick={() => chosen && run(() => dispatchApi.assignHorse(tripId, chosen.horse.bookingHorseId ?? 0, chosen.board, chosen.alight),
                t('dispatch.done.horseAssigned')).then(() => setHorseKey(''), () => {})}>
              {t('dispatch.detail.assignHorse')}
            </Button>
          </div>
        ) : <p className={s.muted}>{t('dispatch.detail.noEligible')}</p>)}
      </section>

      {(!transfer || horses.length > 0) && <StallPlanSection tripId={tripId} readOnly={readOnly} refreshKey={horses.map(item => `${item.leg?.id}:${item.leg?.stallPosition}`).join(',')} />}

      {pending?.kind === 'release' && (
        <ConfirmDialog
          tone="danger"
          title={t('dispatch.detail.releaseTitle', { name: pending.member.fullName })}
          message={t('dispatch.detail.releaseMessage')}
          confirmLabel={t('dispatch.detail.release')}
          onCancel={() => setPending(undefined)}
          onConfirm={() => run(() => dispatchApi.releaseCrew(pending.member.id ?? 0), t('dispatch.done.released')).then(() => setPending(undefined))}
        />
      )}
      {pending?.kind === 'removeHorse' && (
        <ConfirmDialog
          tone="danger"
          title={t('dispatch.detail.removeTitle', { name: pending.item.horseName })}
          message={t('dispatch.detail.removeMessage')}
          confirmLabel={t('dispatch.detail.release')}
          onCancel={() => setPending(undefined)}
          onConfirm={() => run(() => dispatchApi.cancelLeg(pending.item.leg?.id ?? 0), t('dispatch.done.horseRemoved')).then(() => setPending(undefined))}
        />
      )}
      {pending?.kind === 'cancel' && (
        <Modal
          title={t('dispatch.detail.cancelTitle', { code: trip.code })}
          onClose={() => setPending(undefined)}
          footer={(
            <>
              <Button onClick={() => setPending(undefined)}>{t('ui.cancel')}</Button>
              <Button variant="danger" disabled={!reason.trim()} loading={busy}
                onClick={() => run(() => dispatchApi.cancelTrip(tripId, reason.trim()), t('dispatch.done.cancelled')).then(() => setPending(undefined), () => {})}>
                {t('dispatch.detail.cancelConfirm')}
              </Button>
            </>
          )}
        >
          <p className={s.muted}>{t('dispatch.detail.cancelMessage')}</p>
          <Field label={t('dispatch.detail.cancelReason')} required>
            {c => <Textarea {...c} value={reason} onChange={e => setReason(e.target.value)} data-autofocus />}
          </Field>
        </Modal>
      )}
    </Modal>
  )
}
