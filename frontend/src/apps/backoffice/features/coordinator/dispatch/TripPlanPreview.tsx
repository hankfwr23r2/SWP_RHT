// Xem trước giờ hệ thống tính cho một lượt xe; điều phối chỉ sửa giờ xuất phát, các mốc sau tự dời
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dispatchApi, type NetworkLocation, type PlannedTrip, type PlannedTripCreate } from '@shared/api/dispatch'
import { useLoad } from '@shared/hooks/useLoad'
import { Button } from '@shared/ui/Button'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import { isoToLocal, localToIso } from '@backoffice/shared/localTime'
import { TripMap } from './TripMap'
import { TripTimeline } from './TripTimeline'
import s from './Board.module.css'

const BLOCKING = new Set(['RIDE_TOO_LONG', 'LATE_FOR_HUB', 'DRIVER_DAY_LIMIT'])

// onPlan: kế hoạch mới nhất, hoặc undefined khi đang tính hay lỗi
export function TripPlanPreview({ request, onPlan, onStart, restStations, onPlaces }: {
  request: PlannedTripCreate
  onPlan: (plan: PlannedTrip | undefined) => void
  onStart: (startAt: string | undefined) => void
  restStations?: NetworkLocation[]
  onPlaces?: (places: PlannedTripCreate['places']) => void
}) {
  const { t } = useTranslation()
  const [draft, setDraft] = useState('')
  const key = JSON.stringify(request)
  const { data, loaded, error, reload } = useLoad(async () => {
    const plan = await dispatchApi.planTrip(JSON.parse(key) as PlannedTripCreate)
    return { key, plan }
  }, [key], { toastOnError: false })
  const plan = loaded && !error && data?.key === key ? data.plan : undefined
  useEffect(() => { onPlan(plan) }, [onPlan, plan])
  const stops = useMemo(() => (plan?.stops ?? []).map(stop => ({
    name: stop.bookingCode ? `${stop.bookingCode}: ${stop.name}` : stop.name ?? '', latitude: stop.point?.lat, longitude: stop.point?.lng, path: stop.path,
  })), [plan])

  if (error) {
    return <div className={s.waiting}>
      <ErrorState title={t('dispatch.plan.failed')} message={error.message} onRetry={reload} />
    </div>
  }
  if (!loaded || !plan) return <Skeleton variant="block" lines={4} />
  const transfer = request.tripType === 'TRANSFER'
  const start = transfer ? plan.startAt : plan.stops?.[0]?.departureAt
  return (
    <div className={s.waiting}>
      <p className={s.line}>{t('dispatch.plan.summary', { km: plan.km, minutes: plan.driveMinutes })}</p>
      {!!plan.warnings?.length && <ul className={s.alerts} aria-label={t('dispatch.plan.warnings')}>
        {plan.warnings.map(code => <li key={code} className={BLOCKING.has(code) ? s.blocking : undefined}>{t(`dispatch.plan.warning.${code}`)}</li>)}
      </ul>}
      <div className={s.plan}>
        <TripTimeline label={t('dispatch.plan.timeline')} stops={(plan.stops ?? []).map((stop, index) => ({
          key: index, name: stops[index].name, arrivalAt: stop.arrivalAt, departureAt: stop.departureAt,
          driveMinutes: stop.driveMinutes, restMinutes: stop.restMinutes, horses: stop.horses,
        }))} />
        <TripMap stops={stops} />
      </div>
      {onPlaces && plan.stops?.map((stop, index) => stop.stopType === 'REST' && (
        <Field key={index} label={t('dispatch.plan.restStation', { name: stop.name })}>
          {control => <Select {...control} value={String(stop.locationId)} onChange={value => onPlaces(plan.stops!.map((point, at) =>
            at === index ? { locationId: Number(value) }
              : point.locationId != null ? { locationId: point.locationId } : { bookingAddressId: point.bookingAddressId }))}
            options={(restStations ?? []).filter(station => station.locationType === 'REST_STOP' && station.id != null)
              .map(station => ({ value: String(station.id), label: station.name ?? '' }))} />}
        </Field>
      ))}
      <div className={s.chips}>
        <Field label={t(transfer ? 'dispatch.plan.returnStart' : 'dispatch.plan.start')}>
          {c => <Input {...c} type="datetime-local" value={draft || (start ? isoToLocal(start) : '')} onChange={e => setDraft(e.target.value)} />}
        </Field>
        <Button disabled={!draft} onClick={() => { onStart(localToIso(draft)); setDraft('') }}>{t('dispatch.plan.recalculate')}</Button>
        {request.startAt && <Button variant="ghost" onClick={() => { setDraft(''); onStart(undefined) }}>{t('dispatch.plan.auto')}</Button>}
      </div>
    </div>
  )
}
