import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dispatchApi, type CrewAssignment, type CrewRole, type VehicleTrip } from '@shared/api/dispatch'
import { useLoad } from '@shared/hooks/useLoad'
import { Button } from '@shared/ui/Button'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import { StatusBadge } from '@shared/ui/StatusBadge'
import s from './Dispatch.module.css'

interface Props {
  tripId: number
  trip: VehicleTrip
  busy: boolean
  readOnly?: boolean
  onRelease: (member: CrewAssignment) => void
  onAssign: (staffId: number, role: CrewRole) => Promise<void>
}

export function TripCrewSection({ tripId, trip, busy, onRelease, onAssign, readOnly }: Props) {
  const { t } = useTranslation()
  const [crewRole, setCrewRole] = useState<CrewRole>('DRIVER')
  const [staffId, setStaffId] = useState('')
  const running = !readOnly && trip.status === 'IN_PROGRESS'
  const editable = !readOnly && (trip.status === 'PLANNED' || trip.status === 'DISPATCHED')
  const role = running ? 'ESCORT' : crewRole
  const crew = (trip.crew ?? []).filter(member => member.status !== 'RELEASED')
  const { data: candidates, loaded, error, reload } = useLoad(
    () => editable || running ? dispatchApi.crewCandidates(role, tripId) : Promise.resolve([]),
    [editable, running, role, tripId], { toastOnError: false },
  )

  return (
    <section className={s.section} aria-labelledby="trip-crew">
      <h3 id="trip-crew" className={s.sectionTitle}>{t('dispatch.detail.crew')}</h3>
      {crew.length ? (
        <ul className={s.list}>
          {crew.map(member => (
            <li key={member.id} className={s.item}>
              <span className={s.itemMain}>
                <strong>{member.fullName}</strong>
                <span className={s.muted}>{t(`tripWords.role.${member.crewRole}`)}</span>
              </span>
              <span className={s.legs}>
                <StatusBadge tone={member.status === 'ACKNOWLEDGED' ? 'success' : 'warning'} label={t(`dispatch.crewStatus.${member.status}`)} />
                {(editable || running) && <Button size="sm" onClick={() => onRelease(member)}>{t('dispatch.detail.release')}</Button>}
              </span>
            </li>
          ))}
        </ul>
      ) : <p className={s.muted}>{t('dispatch.detail.noCrew')}</p>}
      {running && <p className={s.muted}>{t('dispatch.detail.midwayHint')}</p>}
      {(editable || running) && (
        !loaded ? <Skeleton variant="block" lines={2} /> :
        error ? <ErrorState message={error.message} onRetry={reload} /> :
        <div className={s.form}>
          <Field label={t('dispatch.detail.role')}>
            {c => <Select {...c} value={role} onChange={value => { setCrewRole(value as CrewRole); setStaffId('') }}
              options={(running ? ['ESCORT'] as const : ['DRIVER', 'ESCORT'] as const).map(value => ({ value, label: t(`tripWords.role.${value}`) }))} />}
          </Field>
          <Field label={t('dispatch.detail.staff')}>
            {c => <Select {...c} value={staffId} onChange={setStaffId} placeholder={t('tripWords.choose')}
              options={(candidates ?? []).map(person => ({ value: `${person.staffId}`, label: `${person.fullName} (${person.staffCode}) · ${person.positionTripCode
                ? t('crewPositions.inVehicle', { code: person.positionTripCode })
                : person.positionLocationName ?? t('crewPositions.positionUnknown')}` }))} />}
          </Field>
          <Button variant="primary" disabled={!staffId} loading={busy}
            onClick={() => onAssign(Number(staffId), role).then(() => { setStaffId(''); void reload() }, () => {})}>
            {t('dispatch.detail.assignCrew')}
          </Button>
        </div>
      )}
    </section>
  )
}
