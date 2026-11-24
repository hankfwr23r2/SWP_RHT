// Chi tiết một lượt xe: điểm dừng, tổ lái (phân công, gỡ), ngựa trên xe (xếp, gỡ), hủy lượt
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dispatchApi, type CrewAssignment, type CrewRole, type DispatchBooking, type TripHorse } from '@shared/api/dispatch'
import { useLoad } from '@shared/hooks/useLoad'
import { formatDateTime } from '@shared/lib/format'
import { Badge } from '@shared/ui/Badge'
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
  const toast = useToast()
  const [crewRole, setCrewRole] = useState<CrewRole>('DRIVER')
  const [staffId, setStaffId] = useState('')
  const [horseKey, setHorseKey] = useState('')
  const [pending, setPending] = useState<Pending>()
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const { data, loaded, error, reload } = useLoad(async () => {
    const [trip, horses] = await Promise.all([dispatchApi.trip(tripId), dispatchApi.horsesOnTrip(tripId)])
    return { trip, horses }
  }, [tripId], { toastOnError: false })
  // Lượt đang chạy chỉ thêm hộ tống thay ca
  const role: CrewRole = data?.trip.status === 'IN_PROGRESS' ? 'ESCORT' : crewRole
  const { data: candidates } = useLoad(() => dispatchApi.crewCandidates(role), [role])

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

  if (!loaded) return <Modal title={t('dispatch.trips.view')} onClose={onClose} wide><Skeleton variant="block" lines={4} /></Modal>
  if (error || !data) return <Modal title={t('dispatch.trips.view')} onClose={onClose} wide><ErrorState message={error?.message} onRetry={reload} /></Modal>

  const { trip, horses } = data
  const editable = trip.status === 'PLANNED' || trip.status === 'DISPATCHED'
  const running = trip.status === 'IN_PROGRESS'
  const crew = (trip.crew ?? []).filter(member => member.status !== 'RELEASED')
  const options = eligibleHorses(trip, bookings, horses)
  const chosen = options.find(option => `${option.horse.bookingHorseId}` === horseKey)

  return (
    <Modal
      wide
      title={`${trip.code} · ${t(`dispatch.tripType.${trip.tripType}`)}`}
      subtitle={`${trip.licensePlate} · ${trip.startLocationName} → ${trip.endLocationName}`}
      onClose={onClose}
      footer={editable && <Button variant="danger" onClick={() => { setReason(''); setPending({ kind: 'cancel' }) }}>{t('dispatch.detail.cancelTrip')}</Button>}
    >
      <div className={s.section}>
        <div className={s.sectionHead}>
          <StatusBadge tone={TRIP_STATUS_TONE[trip.status ?? 'PLANNED']} label={t(`dispatch.tripStatus.${trip.status}`)} />
          {!editable && <span className={s.muted}>{t('dispatch.detail.locked')}</span>}
        </div>
      </div>

      <section className={s.section} aria-labelledby="trip-stops">
        <h3 id="trip-stops" className={s.sectionTitle}>{t('dispatch.detail.stops')}</h3>
        <ol className={s.list}>
          {(trip.stops ?? []).map(stop => (
            <li key={stop.id} className={s.item}>
              <span className={s.itemMain}>
                <strong>{stop.sequenceNumber}. {stop.locationName ?? `${stop.bookingCode}: ${stop.addressLine}`}</strong>
                <span className={s.muted}>{stop.plannedArrivalAt ? formatDateTime(new Date(stop.plannedArrivalAt)) : ''}</span>
              </span>
              <Badge>{stop.stopType}</Badge>
            </li>
          ))}
        </ol>
      </section>

      <section className={s.section} aria-labelledby="trip-crew">
        <h3 id="trip-crew" className={s.sectionTitle}>{t('dispatch.detail.crew')}</h3>
        {crew.length ? (
          <ul className={s.list}>
            {crew.map(member => (
              <li key={member.id} className={s.item}>
                <span className={s.itemMain}>
                  <strong>{member.fullName}</strong>
                  <span className={s.muted}>{t(`dispatch.crewRole.${member.crewRole}`)}</span>
                </span>
                <span className={s.legs}>
                  <StatusBadge tone={member.status === 'ACKNOWLEDGED' ? 'success' : 'warning'} label={t(`dispatch.crewStatus.${member.status}`)} />
                  {(editable || running) && <Button size="sm" onClick={() => setPending({ kind: 'release', member })}>{t('dispatch.detail.release')}</Button>}
                </span>
              </li>
            ))}
          </ul>
        ) : <p className={s.muted}>{t('dispatch.detail.noCrew')}</p>}
        {running && <p className={s.muted}>{t('dispatch.detail.midwayHint')}</p>}
        {(editable || running) && (
          <div className={s.form}>
            <Field label={t('dispatch.detail.role')}>
              {c => <Select {...c} value={role} onChange={value => { setCrewRole(value as CrewRole); setStaffId('') }}
                options={(running ? ['ESCORT'] as const : ['DRIVER', 'ESCORT'] as const).map(role => ({ value: role, label: t(`dispatch.crewRole.${role}`) }))} />}
            </Field>
            <Field label={t('dispatch.detail.staff')}>
              {c => <Select {...c} value={staffId} onChange={setStaffId} placeholder={t('dispatch.detail.choose')}
                options={(candidates ?? []).map(person => ({ value: `${person.staffId}`, label: `${person.fullName} (${person.staffCode})` }))} />}
            </Field>
            <Button variant="primary" disabled={!staffId} loading={busy}
              onClick={() => run(() => dispatchApi.assignCrew(tripId, Number(staffId), role), t('dispatch.done.assigned')).then(() => setStaffId(''), () => {})}>
              {t('dispatch.detail.assignCrew')}
            </Button>
          </div>
        )}
      </section>

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
                  <span className={s.muted}>{`${item.leg?.fromName} → ${item.leg?.toName} · ${item.stallTypeCode}`}
                    {item.leg?.stallPosition ? ` · ${t('dispatch.stall.number', { n: item.leg.stallPosition })}` : ''}</span>
                </span>
                {editable && <Button size="sm" onClick={() => setPending({ kind: 'removeHorse', item })}>{t('dispatch.detail.release')}</Button>}
              </li>
            ))}
          </ul>
        ) : <p className={s.muted}>{t('dispatch.detail.noHorses')}</p>}
        {editable && (options.length ? (
          <div className={s.form}>
            <Field label={t('dispatch.detail.horse')}>
              {c => <Select {...c} value={horseKey} onChange={setHorseKey} placeholder={t('dispatch.detail.choose')}
                options={options.map(option => ({ value: `${option.horse.bookingHorseId}`, label: `${option.horse.horseName} · ${option.booking.code} · ${option.horse.stallTypeCode}` }))} />}
            </Field>
            <Button variant="primary" disabled={!chosen} loading={busy}
              onClick={() => chosen && run(() => dispatchApi.assignHorse(tripId, chosen.horse.bookingHorseId ?? 0, chosen.board, chosen.alight),
                t('dispatch.done.horseAssigned')).then(() => setHorseKey(''), () => {})}>
              {t('dispatch.detail.assignHorse')}
            </Button>
          </div>
        ) : <p className={s.muted}>{t('dispatch.detail.noEligible')}</p>)}
      </section>

      <StallPlanSection tripId={tripId} refreshKey={horses.map(item => `${item.leg?.id}:${item.leg?.stallPosition}`).join(',')} />

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
