// Điều độ (điều phối viên): đơn chờ xếp xe và lượt xe trong tuần (docs/MAN_HINH_VA_TAC_DONG.md mục 3.9)
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dispatchApi, type DispatchBooking, type VehicleTrip } from '@shared/api/dispatch'
import { BookingJourney } from '@shared/components/HorseJourney'
import { useLoad } from '@shared/hooks/useLoad'
import { Button } from '@shared/ui/Button'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { PageHeader } from '@shared/ui/PageHeader'
import { Skeleton } from '@shared/ui/Skeleton'
import { Tabs } from '@shared/ui/Tabs'
import { BookingsTable } from './BookingsTable'
import { CreateTripModal } from './CreateTripModal'
import { HubStaysPanel } from './HubStaysPanel'
import { TripDetailModal } from './TripDetailModal'
import { TripsTable } from './TripsTable'
import { weekRange } from './dispatchModel'
import s from './Dispatch.module.css'

const today = () => new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' })

export default function DispatchPage() {
  const { t } = useTranslation()
  const [weekStart, setWeekStart] = useState(today)
  const [tab, setTab] = useState('bookings')
  const [creating, setCreating] = useState(false)
  const [openTripId, setOpenTripId] = useState<number>()
  const [journeyOf, setJourneyOf] = useState<DispatchBooking>()
  const range = weekRange(weekStart)
  const { data, loaded, error, reload } = useLoad(async () => {
    const [bookings, trips] = await Promise.all([dispatchApi.bookings(range.fromDate, range.toDate), dispatchApi.trips(range.from, range.to)])
    return { bookings, trips }
  }, [weekStart], { toastOnError: false })

  const content = () => {
    if (tab === 'stays') return <HubStaysPanel />
    if (!loaded) return <Skeleton variant="block" lines={4} />
    if (error || !data) return <ErrorState title={t('dispatch.loadError')} message={error?.message} onRetry={reload} />
    if (tab === 'bookings') {
      return data.bookings.length
        ? <BookingsTable bookings={data.bookings} onJourney={setJourneyOf} />
        : <EmptyState icon={<i className="fa-solid fa-inbox" />} title={t('dispatch.bookings.emptyTitle')} description={t('dispatch.bookings.emptyDescription')} />
    }
    return data.trips.length
      ? <TripsTable trips={data.trips} onOpen={(trip: VehicleTrip) => setOpenTripId(trip.id)} />
      : <EmptyState icon={<i className="fa-solid fa-truck" />} title={t('dispatch.trips.emptyTitle')} description={t('dispatch.trips.emptyDescription')}
          action={<Button variant="primary" onClick={() => setCreating(true)}>{t('dispatch.newTrip')}</Button>} />
  }

  return (
    <div className={s.page}>
      <PageHeader
        title={t('dispatch.title')}
        description={t('dispatch.description')}
        actions={(
          <div className={s.toolbar}>
            <Field label={t('dispatch.weekFrom')} className={s.week}>
              {control => <Input {...control} type="date" value={weekStart} onChange={e => e.target.value && setWeekStart(e.target.value)} />}
            </Field>
            <Button variant="primary" icon={<i className="fa-solid fa-plus" />} onClick={() => setCreating(true)}>{t('dispatch.newTrip')}</Button>
          </div>
        )}
      />
      <Tabs
        label={t('dispatch.tabsLabel')}
        value={tab}
        onChange={setTab}
        items={[
          { value: 'bookings', label: t('dispatch.tabBookings'), count: data?.bookings.length },
          { value: 'trips', label: t('dispatch.tabTrips'), count: data?.trips.length },
          { value: 'stays', label: t('dispatch.stays.tab') },
        ]}
      >
        {content()}
      </Tabs>
      {creating && data && (
        <CreateTripModal
          bookings={data.bookings}
          weekFrom={range.fromDate}
          weekTo={range.toDate}
          onClose={() => setCreating(false)}
          onCreated={trip => { setCreating(false); setTab('trips'); void reload(); setOpenTripId(trip.id) }}
        />
      )}
      {openTripId !== undefined && data && (
        <TripDetailModal tripId={openTripId} bookings={data.bookings} onClose={() => setOpenTripId(undefined)} onChanged={() => void reload()} />
      )}
      {journeyOf && (
        <Modal title={t('dispatch.bookings.journeyTitle', { code: journeyOf.code })} onClose={() => setJourneyOf(undefined)}>
          <BookingJourney bookingId={journeyOf.id!} />
        </Modal>
      )}
    </div>
  )
}
