// Điều phối: giám sát các lượt xe đang chạy và sắp chạy hôm nay (B6). Xe đang ở đâu, trễ bao lâu, tổ lái, sự cố đang mở. Tự làm mới mỗi phút
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dispatchApi, type VehicleTrip } from '@shared/api/dispatch'
import { incidentsApi } from '@shared/api/incidentOps'
import { useLoad } from '@shared/hooks/useLoad'
import { useNow } from '@shared/hooks/useNow'
import { formatDateTime } from '@shared/lib/format'
import { Button } from '@shared/ui/Button'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { PageHeader } from '@shared/ui/PageHeader'
import { Skeleton } from '@shared/ui/Skeleton'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { Table, type TableColumn } from '@shared/ui/Table'
import { isLate, positionOf, stopName } from './monitorModel'
import s from './Monitoring.module.css'

const DAY_MS = 86_400_000

export default function MonitoringPage() {
  const { t } = useTranslation()
  const now = useNow(60_000)
  const [openId, setOpenId] = useState<number>()
  const { data, loaded, error, reload } = useLoad(async () => {
    // Lượt chạy kéo dài nhiều ngày vẫn hiện: lấy các lượt có kế hoạch chạm khoảng 3 ngày trước tới hết ngày mai
    const [trips, incidents] = await Promise.all([
      dispatchApi.trips(new Date(now - 3 * DAY_MS).toISOString(), new Date(now + DAY_MS).toISOString()), incidentsApi.open(),
    ])
    return { trips, incidents }
  }, [now], { toastOnError: false })

  const content = () => {
    if (!loaded && !data) return <Skeleton variant="block" lines={4} />
    if (error && !data) return <ErrorState message={error.message} onRetry={reload} />
    const trips = data?.trips ?? []
    const openIncidents = (tripId?: number) => (data?.incidents ?? []).filter(i => i.vehicleTripId === tripId).length
    const running = trips.filter(trip => trip.status === 'IN_PROGRESS')
      .map(trip => ({ trip, position: positionOf(trip, now) }))
      .sort((a, b) => Number(isLate(b.position)) - Number(isLate(a.position)) || b.position.lateMinutes - a.position.lateMinutes)
    const upcoming = trips.filter(trip => (trip.status === 'PLANNED' || trip.status === 'DISPATCHED')
      && trip.plannedStartAt && new Date(trip.plannedStartAt).getTime() < now + DAY_MS)
    const crewOf = (trip: VehicleTrip) => (trip.crew ?? []).filter(c => c.status !== 'RELEASED').map(c => c.fullName).join(', ')
    const late = running.filter(r => isLate(r.position)).length

    const runningColumns: TableColumn<(typeof running)[number]>[] = [
      { key: 'code', header: t('dispatch.monitor.colTrip'), render: r => `${r.trip.code} · ${t(`dispatch.tripType.${r.trip.tripType}`)}` },
      { key: 'vehicle', header: t('dispatch.monitor.colVehicle'), render: r => r.trip.licensePlate },
      { key: 'crew', header: t('dispatch.monitor.colCrew'), render: r => crewOf(r.trip) },
      { key: 'where', header: t('dispatch.monitor.colWhere'), render: r => t(`dispatch.monitor.position.${r.position.kind}`, { stop: stopName(r.position.stop) }) },
      {
        key: 'late', header: t('dispatch.monitor.colLate'),
        render: r => (isLate(r.position)
          ? <StatusBadge tone="warning" label={t('dispatch.monitor.late', { minutes: r.position.lateMinutes })} />
          : <StatusBadge tone="success" label={t('dispatch.monitor.onTime')} />),
      },
      { key: 'incidents', header: t('dispatch.monitor.colIncidents'), render: r => (openIncidents(r.trip.id) ? <StatusBadge tone="danger" label={`${openIncidents(r.trip.id)}`} /> : '—') },
      { key: 'view', header: '', render: r => <Button size="sm" variant="ghost" onClick={() => setOpenId(openId === r.trip.id ? undefined : r.trip.id)}>{t('dispatch.monitor.stops')}</Button> },
    ]
    const upcomingColumns: TableColumn<VehicleTrip>[] = [
      { key: 'code', header: t('dispatch.monitor.colTrip'), render: trip => `${trip.code} · ${t(`dispatch.tripType.${trip.tripType}`)}` },
      { key: 'vehicle', header: t('dispatch.monitor.colVehicle'), render: trip => trip.licensePlate },
      { key: 'crew', header: t('dispatch.monitor.colCrew'), render: trip => crewOf(trip) || <StatusBadge tone="warning" label={t('dispatch.monitor.noCrew')} /> },
      { key: 'start', header: t('dispatch.monitor.colStart'), render: trip => (trip.plannedStartAt ? formatDateTime(new Date(trip.plannedStartAt)) : '') },
      { key: 'from', header: t('dispatch.monitor.colFrom'), render: trip => trip.startLocationName },
    ]
    const opened = running.find(r => r.trip.id === openId)?.trip

    return (
      <>
        <ul className={s.cards} aria-label={t('dispatch.monitor.summary')}>
          <li className={s.card}><span className={s.label}>{t('dispatch.monitor.runningCount')}</span><span className={s.value}>{running.length}</span></li>
          <li className={s.card}><span className={s.label}>{t('dispatch.monitor.lateCount')}</span><span className={s.value}>{late}</span></li>
          <li className={s.card}><span className={s.label}>{t('dispatch.monitor.upcomingCount')}</span><span className={s.value}>{upcoming.length}</span></li>
        </ul>
        {running.length
          ? <Table caption={t('dispatch.monitor.runningCaption')} columns={runningColumns} rows={running} rowKey={r => r.trip.id ?? 0} />
          : <EmptyState icon={<i className="fa-solid fa-truck" />} title={t('dispatch.monitor.noRunning')} description={t('dispatch.monitor.noRunningDescription')} />}
        {opened && (
          <section className={s.section} aria-label={t('dispatch.monitor.stopsOf', { code: opened.code })}>
            <h2 className={s.title}>{t('dispatch.monitor.stopsOf', { code: opened.code })}</h2>
            <ol className={s.stops}>
              {(opened.stops ?? []).map(stop => (
                <li key={stop.id}>
                  <strong>{stop.sequenceNumber}. {stopName(stop)}</strong>
                  <span className={s.label}>
                    {t('dispatch.monitor.plannedAt', { at: stop.plannedArrivalAt ? formatDateTime(new Date(stop.plannedArrivalAt)) : '—' })}
                    {stop.actualArrivalAt ? ` · ${t('dispatch.monitor.arrivedAt', { at: formatDateTime(new Date(stop.actualArrivalAt)) })}` : ''}
                    {stop.actualDepartureAt ? ` · ${t('dispatch.monitor.leftAt', { at: formatDateTime(new Date(stop.actualDepartureAt)) })}` : ''}
                  </span>
                </li>
              ))}
            </ol>
          </section>
        )}
        <section className={s.section}>
          <h2 className={s.title}>{t('dispatch.monitor.upcomingTitle')}</h2>
          {upcoming.length
            ? <Table caption={t('dispatch.monitor.upcomingCaption')} columns={upcomingColumns} rows={upcoming} rowKey={trip => trip.id ?? 0} />
            : <p className={s.label}>{t('dispatch.monitor.noUpcoming')}</p>}
        </section>
      </>
    )
  }

  return (
    <div className={s.page}>
      <PageHeader title={t('dispatch.monitor.title')} description={t('dispatch.monitor.description', { at: formatDateTime(new Date(now)) })} />
      {content()}
    </div>
  )
}
