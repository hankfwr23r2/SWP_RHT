import { DateInput } from '@shared/ui/DateInput'
// Điều độ (điều phối viên): đơn chờ xếp xe và lượt xe trong tuần (docs/MAN_HINH_VA_TAC_DONG.md mục 3.9)
import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { invalidateStaffWork } from '@backoffice/shared/useStaffWork'
import { useTranslation } from 'react-i18next'
import { PickupMissPanel } from '@shared/components/PickupMissPanel'
import { dispatchApi, type DispatchBooking, type DispatchHorse, type DispatchVehicle, type VehicleTrip } from '@shared/api/dispatch'
import { BookingJourney } from '@shared/components/HorseJourney'
import { useAutoRefresh, useLoad } from '@shared/hooks/useLoad'
import { Button } from '@shared/ui/Button'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Modal } from '@shared/ui/Modal'
import { PageHeader } from '@shared/ui/PageHeader'
import { Skeleton } from '@shared/ui/Skeleton'
import { GroupingPanel } from './GroupingPanel'
import { LinehaulSuggestionModal } from './LinehaulSuggestionModal'
import { HubAppointmentModal } from './HubAppointmentModal'
import { CreateTripModal } from './CreateTripModal'
import { TripDetailModal } from './TripDetailModal'
import { WaitingList } from './WaitingList'
import { WeekBoard } from './WeekBoard'
import { addDays, missingLegs, mondayOf, nextWaitingWeek, weekRange } from './dispatchModel'
import b from './Board.module.css'
import s from './Dispatch.module.css'

const LOOKAHEAD_DAYS = 60
const today = () => new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' })

export default function DispatchPage() {
  const { t } = useTranslation()
  const [params, setParams] = useSearchParams()
  const requestedDate = params.get('date')
  const linkedDate = requestedDate && /^\d{4}-\d{2}-\d{2}$/.test(requestedDate) && Number.isFinite(Date.parse(requestedDate))
    ? requestedDate : undefined
  const linkedDeparture = params.get('departureId') ?? undefined
  const linkedBooking = Number(params.get('bookingId')) || undefined
  const linkedTrip = Number(params.get('tripId')) || undefined
  const clearLink = (key: string) => {
    if (params.has(key)) setParams(previous => { previous.delete(key); return previous }, { replace: true })
  }
  const [weekStart, setWeekStart] = useState(() => mondayOf(today()))
  const [grouping, setGrouping] = useState(false)
  const [linehaul, setLinehaul] = useState(false)
  // true: lập lượt mới; một xe: lập lượt đưa xe đó về
  const [creating, setCreating] = useState<boolean | DispatchVehicle>(false)
  const [openTripId, setOpenTripId] = useState<number>()
  const [journeyOf, setJourneyOf] = useState<DispatchBooking>()
  const [hubOf, setHubOf] = useState<{ booking: DispatchBooking; horse: DispatchHorse }>()
  const shownWeek = linkedDate ? mondayOf(linkedDate) : weekStart
  const chooseWeek = (week: string) => {
    setWeekStart(week)
    clearLink('date')
  }
  const range = weekRange(shownWeek)
  const journeyId = journeyOf?.id ?? linkedBooking
  const tripId = openTripId ?? linkedTrip
  const { data, loaded, error, reload } = useLoad(async () => {
    const [bookings, trips, vehicles] = await Promise.all([
      dispatchApi.bookings(range.fromDate, range.toDate), dispatchApi.trips(range.from, range.to), dispatchApi.vehicles(),
    ])
    // Tuần không còn đơn chờ thì tìm tuần gần nhất còn đơn, để điều phối không phải dò từng tuần
    const next = bookings.some(booking => missingLegs(booking).length) ? undefined
      : nextWaitingWeek(await dispatchApi.bookings(addDays(range.toDate, 1), addDays(range.toDate, LOOKAHEAD_DAYS)))
    return { bookings, trips, vehicles, next }
  }, [shownWeek], { toastOnError: false })
  useAutoRefresh(reload)
  const changed = () => { invalidateStaffWork(); void reload() }

  const content = () => {
    if (!loaded) return <Skeleton variant="block" lines={4} />
    if (error || !data) return <ErrorState title={t('dispatch.loadError')} message={error?.message} onRetry={reload} />
    return (
      <div className={b.layout}>
        <WaitingList bookings={data.bookings} next={data.next} onWeek={chooseWeek} onJourney={setJourneyOf}
          onHub={(booking, horse) => setHubOf({ booking, horse })} />
        <WeekBoard weekStart={shownWeek} vehicles={data.vehicles}
          trips={data.trips}
          onOpen={(trip: VehicleTrip) => setOpenTripId(trip.id)} onReturn={setCreating} />
      </div>
    )
  }

  return (
    <div className={s.page}>
      <PageHeader
        title={t('dispatch.title')}
        description={t('dispatch.description')}
        actions={(
          <div className={s.toolbar}>
            <Button aria-label={t('dispatch.board.previous')} icon={<i className="fa-solid fa-chevron-left" />} onClick={() => chooseWeek(addDays(shownWeek, -7))} />
            <Field label={t('dispatch.weekFrom')} className={s.week}>
              {control => <DateInput {...control} value={shownWeek} onValueChange={value => value && chooseWeek(mondayOf(value))} />}
            </Field>
            <Button aria-label={t('dispatch.board.next')} icon={<i className="fa-solid fa-chevron-right" />} onClick={() => chooseWeek(addDays(shownWeek, 7))} />
            <Button variant="primary" icon={<i className="fa-solid fa-route" />} onClick={() => setGrouping(true)}>{t('dispatch.board.group')}</Button>
            <Button onClick={() => setLinehaul(true)}>{t('linehaul.suggest')}</Button>
            <Button icon={<i className="fa-solid fa-plus" />} onClick={() => setCreating(true)}>{t('dispatch.newTrip')}</Button>
          </div>
        )}
      />
      {content()}
      {(linehaul || linkedDeparture) && <LinehaulSuggestionModal key={linkedDeparture} initialDeparture={linkedDeparture}
        from={range.fromDate} to={range.toDate} onClose={() => { setLinehaul(false); clearLink('departureId') }}
        onCreated={id => { setLinehaul(false); clearLink('departureId'); changed(); setOpenTripId(id) }} />}
      {creating && data && (
        <CreateTripModal
          bookings={data.bookings}
          weekFrom={range.fromDate}
          weekTo={range.toDate}
          returning={typeof creating === 'object' ? creating : undefined}
          onClose={() => setCreating(false)}
          onCreated={trip => { setCreating(false); changed(); setOpenTripId(trip.id) }}
        />
      )}
      {grouping && (
        <Modal wide title={t('dispatch.board.group')} onClose={() => setGrouping(false)}>
          <GroupingPanel defaultDate={shownWeek} onCreated={trip => { setGrouping(false); changed(); setOpenTripId(trip.id) }} />
        </Modal>
      )}
      {tripId !== undefined && (
        <TripDetailModal tripId={tripId} bookings={data?.bookings ?? []}
          onClose={() => { setOpenTripId(undefined); clearLink('tripId') }} onChanged={changed} />
      )}
      {hubOf && <HubAppointmentModal {...hubOf} onClose={() => setHubOf(undefined)} onDone={() => setHubOf(undefined)} />}
      {journeyId !== undefined && (
        <Modal title={t('dispatch.bookings.journeyTitle', { code: journeyOf?.code ?? journeyId })}
          onClose={() => { setJourneyOf(undefined); clearLink('bookingId') }}>
          <BookingJourney bookingId={journeyId} />
          <PickupMissPanel bookingId={journeyId} mode="coordinator" onChanged={changed} />
        </Modal>
      )}
    </div>
  )
}
