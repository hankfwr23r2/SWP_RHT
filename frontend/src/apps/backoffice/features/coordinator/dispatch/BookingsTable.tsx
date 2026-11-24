import { useTranslation } from 'react-i18next'
import type { DispatchBooking, DispatchHorse } from '@shared/api/dispatch'
import { formatDate } from '@shared/lib/format'
import { Badge } from '@shared/ui/Badge'
import { Button } from '@shared/ui/Button'
import { Table, type TableColumn } from '@shared/ui/Table'
import { activeLegs } from './dispatchModel'
import s from './Dispatch.module.css'

interface Row { booking: DispatchBooking; horse: DispatchHorse }

// Mỗi dòng là một con ngựa của đơn, kèm các chặng đã lập
export function BookingsTable({ bookings, onJourney, onAgent, onHub }: {
  bookings: DispatchBooking[]
  onJourney: (booking: DispatchBooking) => void
  onAgent: (booking: DispatchBooking, horse: DispatchHorse) => void
  onHub: (booking: DispatchBooking, horse: DispatchHorse) => void
}) {
  const { t } = useTranslation()
  const rows: Row[] = bookings.flatMap(booking => (booking.horses ?? []).map(horse => ({ booking, horse })))
  const columns: TableColumn<Row>[] = [
    { key: 'booking', header: t('dispatch.bookings.colBooking'), render: r => r.booking.code },
    { key: 'route', header: t('dispatch.bookings.colRoute'), render: r => r.booking.routeCode ?? t('dispatch.bookings.direct') },
    { key: 'date', header: t('dispatch.bookings.colPickupDate'), render: r => (r.booking.requestedPickupDate ? formatDate(new Date(r.booking.requestedPickupDate)) : '') },
    { key: 'horse', header: t('dispatch.bookings.colHorse'), render: r => r.horse.horseName },
    { key: 'stall', header: t('dispatch.bookings.colStall'), render: r => r.horse.stallTypeCode },
    {
      key: 'legs',
      header: t('dispatch.bookings.colLegs'),
      render: r => {
        const legs = activeLegs(r.horse)
        if (!legs.length) return <Badge tone="warning">{t('dispatch.bookings.noLegs')}</Badge>
        return (
          <span className={s.legs}>
            {legs.map(leg => <Badge key={leg.id} tone="info">{`${t(`dispatch.tripType.${leg.legType}`)} · ${leg.vehicleTripCode ?? leg.agentName ?? ''}`}</Badge>)}
          </span>
        )
      },
    },
    {
      // Đơn xuất, nhập: đoạn ở nước ngoài do đại lý chở
      key: 'agent',
      header: t('dispatch.agent.col'),
      render: r => (r.booking.bookingType === 'EXPORT' || r.booking.bookingType === 'IMPORT'
        ? <Button size="sm" variant="ghost" onClick={() => onAgent(r.booking, r.horse)}>{t('dispatch.agent.open')}</Button>
        : null),
    },
    {
      // Khách tự chở ngựa tới hub đầu tuyến trục: hẹn giờ nhận tại hub
      key: 'hub',
      header: t('dispatch.hub.col'),
      render: r => (r.booking.pickupMethod === 'SELF' && r.booking.departureId
        ? <Button size="sm" variant="ghost" onClick={() => onHub(r.booking, r.horse)}>{t('dispatch.hub.open')}</Button>
        : null),
    },
    {
      key: 'journey',
      header: t('dispatch.bookings.colJourney'),
      render: r => <Button size="sm" variant="ghost" onClick={() => onJourney(r.booking)}>{t('dispatch.bookings.viewJourney')}</Button>,
    },
  ]
  return <Table caption={t('dispatch.bookings.caption')} columns={columns} rows={rows} rowKey={r => r.horse.bookingHorseId ?? 0} />
}
