// Điều độ (điều phối viên): đơn chờ xếp xe và lượt xe trong tuần (docs/MAN_HINH_VA_TAC_DONG.md mục 3.9)
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dispatchApi, type DispatchBooking, type DispatchHorse, type VehicleTrip } from '@shared/api/dispatch'
import { BookingJourney } from '@shared/components/HorseJourney'
import { useLoad } from '@shared/hooks/useLoad'
import { Button } from '@shared/ui/Button'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { PageHeader } from '@shared/ui/PageHeader'
import { Skeleton } from '@shared/ui/Skeleton'
import { AgentLegModal } from './AgentLegModal'
import { GroupingPanel } from './GroupingPanel'
import { HubAppointmentModal } from './HubAppointmentModal'
import { CreateTripModal } from './CreateTripModal'
import { TripDetailModal } from './TripDetailModal'
import { WaitingList } from './WaitingList'
import { WeekBoard } from './WeekBoard'
import { addDays, missingLegs, mondayOf, nextWaitingWeek, weekRange } from './dispatchModel'
import b from './Board.module.css'
import s from './Dispatch.module.css'

// Lấy lại đơn và con ngựa từ dữ liệu mới nhất sau mỗi lần tải lại
const agentTarget = (bookings: DispatchBooking[], key: { bookingId: number; bookingHorseId: number }): { booking: DispatchBooking; horse: DispatchHorse } | undefined => {
  const booking = bookings.find(b => b.id === key.bookingId)
  const horse = booking?.horses?.find(h => h.bookingHorseId === key.bookingHorseId)
  return booking && horse ? { booking, horse } : undefined
}

const LOOKAHEAD_DAYS = 60
const today = () => new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' })

export default function DispatchPage() {
  const { t } = useTranslation()
  const [weekStart, setWeekStart] = useState(() => mondayOf(today()))
  const [grouping, setGrouping] = useState(false)
  const [creating, setCreating] = useState(false)
  const [openTripId, setOpenTripId] = useState<number>()
  const [journeyOf, setJourneyOf] = useState<DispatchBooking>()
  const [agentOf, setAgentOf] = useState<{ bookingId: number; bookingHorseId: number }>()
  const [hubOf, setHubOf] = useState<{ booking: DispatchBooking; horse: DispatchHorse }>()
  const range = weekRange(weekStart)
  const { data, loaded, error, reload } = useLoad(async () => {
    const [bookings, trips, vehicles] = await Promise.all([
      dispatchApi.bookings(range.fromDate, range.toDate), dispatchApi.trips(range.from, range.to), dispatchApi.vehicles(),
    ])
    // Tuần không còn đơn chờ thì tìm tuần gần nhất còn đơn, để điều phối không phải dò từng tuần
    const next = bookings.some(booking => missingLegs(booking).length) ? undefined
      : nextWaitingWeek(await dispatchApi.bookings(addDays(range.toDate, 1), addDays(range.toDate, LOOKAHEAD_DAYS)))
    return { bookings, trips, vehicles, next }
  }, [weekStart], { toastOnError: false })

  const content = () => {
    if (!loaded) return <Skeleton variant="block" lines={4} />
    if (error || !data) return <ErrorState title={t('dispatch.loadError')} message={error?.message} onRetry={reload} />
    return (
      <div className={b.layout}>
        <WaitingList bookings={data.bookings} next={data.next} onWeek={setWeekStart} onJourney={setJourneyOf}
          onAgent={(booking, horse) => setAgentOf({ bookingId: booking.id ?? 0, bookingHorseId: horse.bookingHorseId ?? 0 })}
          onHub={(booking, horse) => setHubOf({ booking, horse })} />
        <WeekBoard weekStart={weekStart} vehicles={data.vehicles} trips={data.trips} onOpen={(trip: VehicleTrip) => setOpenTripId(trip.id)} />
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
            <Button aria-label={t('dispatch.board.previous')} icon={<i className="fa-solid fa-chevron-left" />} onClick={() => setWeekStart(addDays(weekStart, -7))} />
            <Field label={t('dispatch.weekFrom')} className={s.week}>
              {control => <Input {...control} type="date" value={weekStart} onChange={e => e.target.value && setWeekStart(mondayOf(e.target.value))} />}
            </Field>
            <Button aria-label={t('dispatch.board.next')} icon={<i className="fa-solid fa-chevron-right" />} onClick={() => setWeekStart(addDays(weekStart, 7))} />
            <Button variant="primary" icon={<i className="fa-solid fa-route" />} onClick={() => setGrouping(true)}>{t('dispatch.board.group')}</Button>
            <Button icon={<i className="fa-solid fa-plus" />} onClick={() => setCreating(true)}>{t('dispatch.newTrip')}</Button>
          </div>
        )}
      />
      {content()}
      {creating && data && (
        <CreateTripModal
          bookings={data.bookings}
          weekFrom={range.fromDate}
          weekTo={range.toDate}
          onClose={() => setCreating(false)}
          onCreated={trip => { setCreating(false); void reload(); setOpenTripId(trip.id) }}
        />
      )}
      {grouping && (
        <Modal wide title={t('dispatch.board.group')} onClose={() => setGrouping(false)}>
          <GroupingPanel defaultDate={weekStart} onCreated={trip => { setGrouping(false); void reload(); setOpenTripId(trip.id) }} />
        </Modal>
      )}
      {openTripId !== undefined && data && (
        <TripDetailModal tripId={openTripId} bookings={data.bookings} onClose={() => setOpenTripId(undefined)} onChanged={() => void reload()} />
      )}
      {agentOf && data && agentTarget(data.bookings, agentOf) && (
        <AgentLegModal {...agentTarget(data.bookings, agentOf)!} onClose={() => setAgentOf(undefined)} onChanged={() => void reload()} />
      )}
      {hubOf && <HubAppointmentModal {...hubOf} onClose={() => setHubOf(undefined)} onDone={() => setHubOf(undefined)} />}
      {journeyOf && (
        <Modal title={t('dispatch.bookings.journeyTitle', { code: journeyOf.code })} onClose={() => setJourneyOf(undefined)}>
          <BookingJourney bookingId={journeyOf.id!} />
        </Modal>
      )}
    </div>
  )
}
