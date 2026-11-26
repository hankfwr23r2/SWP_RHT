// Đơn của tuần dạng thẻ: chặng còn thiếu, ngựa, nơi đón và giao. Mặc định chỉ hiện đơn còn chặng phải xếp
import { PrivateVehicleBadge } from '@shared/components/PrivateVehicleBadge'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { DispatchBooking, DispatchHorse } from '@shared/api/dispatch'
import { formatDate, formatDateTime } from '@shared/lib/format'
import { Badge } from '@shared/ui/Badge'
import { Button } from '@shared/ui/Button'
import { EmptyState } from '@shared/ui/EmptyState'
import { missingLegs } from './dispatchModel'
import s from './Board.module.css'

interface Props {
  bookings: DispatchBooking[]
  onJourney: (booking: DispatchBooking) => void
  onHub: (booking: DispatchBooking, horse: DispatchHorse) => void
  // Tuần này hết đơn chờ: tuần gần nhất còn đơn, để mở luôn
  next?: { weekStart: string; count: number }
  onWeek: (weekStart: string) => void
}

export function WaitingList({ bookings, onJourney, onHub, next, onWeek }: Props) {
  const { t } = useTranslation()
  const [all, setAll] = useState(false)
  const waiting = bookings.filter(booking => missingLegs(booking).length > 0)
  const shown = all ? bookings : waiting
  return (
    <section className={s.waiting} aria-labelledby="waiting-title">
      <h2 id="waiting-title" className={s.title}>{t('dispatch.tabBookings')}</h2>
      <div className={s.toggle} role="group" aria-label={t('dispatch.board.filter')}>
        <Button size="sm" variant={all ? 'ghost' : 'primary'} aria-pressed={!all} onClick={() => setAll(false)}>{t('dispatch.board.waiting', { count: waiting.length })}</Button>
        <Button size="sm" variant={all ? 'primary' : 'ghost'} aria-pressed={all} onClick={() => setAll(true)}>{t('dispatch.board.all', { count: bookings.length })}</Button>
      </div>
      {!shown.length && <EmptyState icon={<i className="fa-solid fa-inbox" />} title={t(all ? 'dispatch.bookings.emptyTitle' : 'dispatch.board.nothing')} description={next ? t('dispatch.board.nextWeekHint', { count: next.count }) : t('dispatch.bookings.emptyDescription')}
        action={next && <Button variant="primary" onClick={() => onWeek(next.weekStart)}>{t('dispatch.board.openWeek', { date: formatDate(new Date(next.weekStart)) })}</Button>} />}
      <ul className={s.cards} aria-label={t('dispatch.bookings.caption')}>
        {shown.map(booking => (
          <li key={booking.id} className={s.card}>
            <div className={s.cardHead}>
              <strong>{booking.code}</strong> <PrivateVehicleBadge mode={booking.transportMode} />
              <Badge>{booking.routeCode ?? t('dispatch.bookings.direct')}</Badge>
            </div>
            <p className={s.line}><i className="fa-solid fa-location-dot" aria-hidden="true" /> {booking.pickupAddress?.addressLine ?? '—'}</p>
            <p className={s.line}><i className="fa-solid fa-flag-checkered" aria-hidden="true" /> {booking.deliveryAddress?.addressLine ?? '—'}</p>
            <p className={s.line}><i className="fa-solid fa-clock" aria-hidden="true" /> {booking.requestedPickupDate && t('dispatch.board.pickupOn', { date: formatDate(new Date(booking.requestedPickupDate)) })}
              {booking.plannedDepartureAt && ` · ${t('dispatch.board.runsAt', { at: formatDateTime(new Date(booking.plannedDepartureAt)) })}`}</p>
            <div className={s.chips}>
              {missingLegs(booking).length
                ? missingLegs(booking).map(leg => <Badge key={leg} tone="warning">{t('dispatch.board.need', { leg: t(`tripWords.type.${leg}`) })}</Badge>)
                : <Badge tone="success">{t('dispatch.board.complete')}</Badge>}
            </div>
            <ul className={s.horses}>
              {(booking.horses ?? []).filter(horse => all || horse.missingLegs?.length).map(horse => (
                <li key={horse.bookingHorseId}>
                  <span><i className="fa-solid fa-horse-head" aria-hidden="true" /> {horse.horseName}{horse.deliveryAddress?.addressLine && ` · ${horse.deliveryAddress.addressLine}`}{horse.seats === 2 && ` · ${t('dispatch.stall.wide')}`}</span>
                  {booking.pickupMethod === 'SELF' && booking.departureId
                    && <Button size="sm" variant="ghost" onClick={() => onHub(booking, horse)}>{t('dispatch.hub.open')}</Button>}
                </li>
              ))}
            </ul>
            <div><Button size="sm" onClick={() => onJourney(booking)}>{t('dispatch.board.journey')}</Button></div>
          </li>
        ))}
      </ul>
    </section>
  )
}
