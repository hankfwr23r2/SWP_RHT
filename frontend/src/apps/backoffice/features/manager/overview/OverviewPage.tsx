// Quản lý: tổng quan vận hành tuần này (B6). Ghép từ báo cáo doanh thu, số liệu vận hành, sự cố đang mở và lượt xe trong tuần
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { incidentsApi, type Incident } from '@shared/api/incidentOps'
import { reportsApi, type TripReportRow } from '@shared/api/reports'
import { useLoad } from '@shared/hooks/useLoad'
import { formatDateTime, formatVND } from '@shared/lib/format'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { PageHeader } from '@shared/ui/PageHeader'
import { Skeleton } from '@shared/ui/Skeleton'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { Table, type TableColumn } from '@shared/ui/Table'
import { IncidentSeverityBadge } from '@backoffice/shared/IncidentSeverity'
import s from './Overview.module.css'

const DAY_MS = 86_400_000
// Thứ Hai đầu tuần theo giờ Việt Nam
function weekStart(now: number) {
  const today = new Date(`${new Date(now).toLocaleDateString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' })}T00:00:00+07:00`)
  const weekday = (new Date(today.getTime() + 7 * 3_600_000).getUTCDay() + 6) % 7
  return today.getTime() - weekday * DAY_MS
}
const iso = (ms: number) => new Date(ms).toISOString()

export default function OverviewPage() {
  const { t } = useTranslation()
  const [now] = useState(() => Date.now())
  const from = weekStart(now)
  const to = from + 7 * DAY_MS
  const { data, loaded, error, reload } = useLoad(async () => {
    const [summary, trips, incidents, revenue] = await Promise.all([
      reportsApi.summary(iso(from), iso(to)), reportsApi.trips(iso(from), iso(to)), incidentsApi.open(),
      reportsApi.revenue(iso(now - 30 * DAY_MS), iso(now)),
    ])
    return { summary, trips, incidents, revenue }
  }, [from, to], { toastOnError: false })

  const incidentColumns: TableColumn<Incident>[] = [
    { key: 'title', header: t('managerOverview.colIncident'), render: i => i.title },
    { key: 'severity', header: t('incidents.severity.label'), render: i => <IncidentSeverityBadge severity={i.severity} /> },
    { key: 'trip', header: t('managerOverview.colTrip'), render: i => [i.vehicleTripCode, i.horseName, i.bookingCode].filter(Boolean).join(' · ') },
    { key: 'status', header: t('managerOverview.colStatus'), render: i => <StatusBadge tone={i.status === 'PLAN_PENDING' ? 'warning' : 'info'} label={t(`incidents.status.${i.status}`)} /> },
    { key: 'at', header: t('managerOverview.colReported'), render: i => (i.reportedAt ? formatDateTime(new Date(i.reportedAt)) : '') },
  ]
  const tripColumns: TableColumn<TripReportRow>[] = [
    { key: 'code', header: t('opsReport.colTrip'), render: r => r.code },
    { key: 'type', header: t('opsReport.colType'), render: r => t(`dispatch.tripType.${r.tripType}`) },
    { key: 'vehicle', header: t('opsReport.colVehicle'), render: r => r.licensePlate },
    { key: 'start', header: t('opsReport.colStart'), render: r => (r.plannedStartAt ? formatDateTime(new Date(r.plannedStartAt)) : '') },
    { key: 'status', header: t('opsReport.colStatus'), render: r => t(`dispatch.tripStatus.${r.status}`) },
    { key: 'incidents', header: t('opsReport.colIncidents'), render: r => r.incidentCount },
  ]

  const content = () => {
    if (!loaded) return <Skeleton variant="block" lines={5} />
    if (error || !data) return <ErrorState message={error?.message} onRetry={reload} />
    const { summary, trips, incidents, revenue } = data
    const waiting = incidents.filter(i => i.status === 'PLAN_PENDING')
    const cards = [
      { key: 'revenue', value: formatVND(revenue.revenue ?? 0), to: '/manager/reports' },
      { key: 'trips', value: `${summary.completedTripCount ?? 0}/${summary.tripCount ?? 0}`, to: '/manager/trip-reports' },
      { key: 'onTime', value: summary.onTimeRate == null ? '—' : `${Math.round(summary.onTimeRate * 100)}%`, to: '/manager/trip-reports' },
      { key: 'incidents', value: `${incidents.length}`, to: '/manager/incidents' },
    ]
    return (
      <>
        <ul className={s.cards} aria-label={t('managerOverview.cardsLabel')}>
          {cards.map(card => (
            <li key={card.key} className={s.card}>
              <span className={s.label}>{t(`managerOverview.card.${card.key}`)}</span>
              <span className={s.value}>{card.value}</span>
              <Link to={card.to}>{t('managerOverview.details')}</Link>
            </li>
          ))}
        </ul>
        <section className={s.section} aria-labelledby="overview-incidents">
          <div className={s.sectionHead}>
            <h2 id="overview-incidents" className={s.title}>{t('managerOverview.incidentsTitle', { count: waiting.length })}</h2>
            <Link to="/manager/incidents">{t('managerOverview.allIncidents')}</Link>
          </div>
          {incidents.length
            ? <Table caption={t('managerOverview.incidentsCaption')} columns={incidentColumns} rows={incidents} rowKey={i => i.id ?? 0} />
            : <EmptyState icon={<i className="fa-solid fa-circle-check" />} title={t('managerOverview.noIncidents')} description={t('managerOverview.noIncidentsDescription')} />}
        </section>
        <section className={s.section} aria-labelledby="overview-trips">
          <div className={s.sectionHead}>
            <h2 id="overview-trips" className={s.title}>{t('managerOverview.tripsTitle')}</h2>
            <Link to="/manager/trip-reports">{t('managerOverview.allTrips')}</Link>
          </div>
          {trips.length
            ? <Table caption={t('managerOverview.tripsCaption')} columns={tripColumns} rows={trips} rowKey={r => r.id ?? 0} />
            : <EmptyState icon={<i className="fa-solid fa-truck" />} title={t('managerOverview.noTrips')} description={t('managerOverview.noTripsDescription')} />}
        </section>
      </>
    )
  }

  return (
    <div className={s.page}>
      <PageHeader title={t('managerOverview.title')} description={t('managerOverview.description')} />
      {content()}
    </div>
  )
}
