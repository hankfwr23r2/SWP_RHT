import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router'
import { hubsApi, type HubFleetRequirement, type HubOverview } from '@shared/api/hubs'
import { useLoad } from '@shared/hooks/useLoad'
import { formatDateTime } from '@shared/lib/format'
import { Card } from '@shared/ui/Card'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { ListPanel } from '@shared/ui/ListPanel'
import { PageHeader } from '@shared/ui/PageHeader'
import { Skeleton } from '@shared/ui/Skeleton'
import { StatTile } from '@shared/ui/StatTile'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { Table, type TableColumn } from '@shared/ui/Table'
import { IncidentSeverityBadge } from '@backoffice/shared/IncidentSeverity'
import s from './ManagerHubs.module.css'

const fleetLabels = [
  ['idle', 'managerHubs.idle'],
  ['running', 'tripWords.status.IN_PROGRESS'],
  ['returning', 'tripWords.returning'],
  ['maintenance', 'managerHubs.maintenance'],
] as const satisfies readonly (readonly [keyof HubOverview['fleet'], string])[]

const requirementLabels = {
  LINEHAUL: 'managerHubs.requirement.linehaul',
  SPARE: 'managerHubs.requirement.spare',
  BRANCH: 'managerHubs.requirement.branch',
  DRIVER: 'roles.driver',
  ESCORT: 'roles.escort',
} as const satisfies Record<HubFleetRequirement['item'], string>

const hubLocation = (hub: HubOverview, headquartersLabel: string) =>
  [hub.code, hub.provinceName, hub.countryName, hub.headquarters ? headquartersLabel : null].filter(Boolean).join(' · ')

function FleetRequirementSummary({ rows }: { rows: HubFleetRequirement[] }) {
  const { t } = useTranslation()
  const missing = { vehicles: 0, drivers: 0, escorts: 0 }
  for (const row of rows) {
    const count = Math.max(0, row.required - row.actual)
    if (row.item === 'DRIVER') missing.drivers += count
    else if (row.item === 'ESCORT') missing.escorts += count
    else missing.vehicles += count
  }
  const labels = Object.entries(missing).flatMap(([key, count]) => count > 0 ? [t(`managerHubs.requirement.missing.${key}`, { count })] : [])
  return <p className={s.requirementSummary}>{labels.length
    ? <StatusBadge tone="warning" label={t('managerHubs.requirement.shortage', { items: labels.join(', ') })} />
    : t('managerHubs.requirement.sufficient')}</p>
}

export default function ManagerHubsPage() {
  const { t } = useTranslation()
  const [query] = useSearchParams()
  const hubId = query.get('hub')
  const { data, loaded, error, reload } = useLoad(hubsApi.overview, [], { toastOnError: false })
  const selected = data?.find(hub => String(hub.id) === hubId)
  const title = t('staffMenus./manager/hubs')
  const headquarters = t('managerHubs.headquarters')

  const content = () => {
    if (!loaded) return <Skeleton variant="block" />
    if (error || !data) return <ErrorState title={t('managerHubs.loadError')} onRetry={reload} />
    if (selected) return <HubDetails hub={selected} />
    if (hubId !== null) return <EmptyState title={t('managerHubs.unavailable')} description={t('managerHubs.unavailableHint')} />
    if (!data.length) return <EmptyState title={t('managerHubs.empty')} description={t('managerHubs.emptyHint')} />

    return <ListPanel count={data.length}>
      <ul className={s.hubs} aria-label={title}>
        {data.map(hub => <li key={hub.id}>
          <Link to={`?hub=${hub.id}`} className={s.cardLink} aria-labelledby={`hub-${hub.id}`}>
            <Card className={s.card} title={<span id={`hub-${hub.id}`} className={s.name}>
              <i className="fa-solid fa-warehouse" aria-hidden="true" />{hub.name}
            </span>}>
              <p className={s.location}>{hubLocation(hub, headquarters)}</p>
              <dl className={s.metrics}>
                <div><dt>{t('managerHubs.horses')}</dt><dd>{hub.horseCount}</dd></div>
                <div><dt>{t('managerHubs.stalls')}</dt><dd>{hub.stalls.available}/{hub.stalls.total}</dd></div>
                {fleetLabels.map(([key, label]) => <div key={key}><dt>{t(label)}</dt><dd>{hub.fleet[key].count}</dd></div>)}
                <div><dt>{t('managerOverview.incidentsCaption')}</dt><dd>{hub.openIncidentCount > 0
                  ? <StatusBadge tone="warning" label={String(hub.openIncidentCount)} />
                  : hub.openIncidentCount}</dd></div>
              </dl>
              {!!hub.fleetRequirement?.length && <FleetRequirementSummary rows={hub.fleetRequirement} />}
            </Card>
          </Link>
        </li>)}
      </ul>
    </ListPanel>
  }

  return <div className={s.page}>
    <PageHeader title={selected?.name ?? title} description={selected ? hubLocation(selected, headquarters) : t('managerHubs.description')}
      actions={hubId !== null && <Link to="/manager/hubs">{t('managerHubs.back')}</Link>} />
    {content()}
  </div>
}

const dateTime = (value: string) => <time dateTime={value}>{formatDateTime(new Date(value))}</time>

function FleetRequirementTable({ rows }: { rows: HubFleetRequirement[] }) {
  const { t } = useTranslation()
  const title = t('managerHubs.requirement.title')
  const columns: TableColumn<HubFleetRequirement>[] = [
    { key: 'item', header: t('managerHubs.requirement.item'), render: row => <div>
      {t(requirementLabels[row.item])}
      {row.breakdown && <small className={s.breakdown}>{t('managerHubs.requirement.breakdown', { ...row.breakdown })}</small>}
    </div> },
    { key: 'required', header: t('managerHubs.requirement.required'), render: row => row.required, align: 'end' },
    { key: 'actual', header: t('managerHubs.requirement.actual'), render: row => row.actual, align: 'end' },
    { key: 'difference', header: t('managerHubs.requirement.difference'), align: 'end', render: row => {
      const difference = row.actual - row.required
      if (difference < 0) return <StatusBadge tone="warning" label={String(difference)} />
      return difference > 0 ? `+${difference}` : difference
    } },
  ]

  return <Card title={title}>
    <Table caption={title} columns={columns} rows={rows} rowKey={row => row.item} />
    <p className={s.requirementNote}>{t('managerHubs.requirement.note')}</p>
  </Card>
}

function HubDetails({ hub }: { hub: HubOverview }) {
  const { t } = useTranslation()
  const fleet = fleetLabels.flatMap(([state, label]) => hub.fleet[state].vehicles.map(vehicle => ({ state, label, vehicle })))
  const horseColumns: TableColumn<HubOverview['horses'][number]>[] = [
    { key: 'horse', header: t('dispatch.stays.colHorse'), render: row => row.horseName },
    { key: 'booking', header: t('listPanel.orderCode'), render: row => <span className={s.code}>{row.bookingCode}</span> },
    { key: 'stay', header: t('dispatch.stays.colType'), render: row => t(`dispatch.stays.stayType.${row.stayType}`) },
    { key: 'since', header: t('dispatch.stays.colSince'), render: row => dateTime(row.actualStartAt) },
  ]
  const vehicleColumns: TableColumn<(typeof fleet)[number]>[] = [
    { key: 'plate', header: t('managerHubs.plate'), render: row => row.vehicle.licensePlate },
    { key: 'status', header: t('listPanel.status'), render: row => t(row.label) },
    { key: 'trip', header: t('incidents.colTrip'), render: row => row.vehicle.tripCode ?? '—' },
  ]
  const staffColumns: TableColumn<HubOverview['staff'][number]>[] = [
    { key: 'role', header: t('managerHubs.role'), render: row => t(`roles.${row.roleCode.toLowerCase()}`, row.roleCode) },
    { key: 'count', header: t('managerHubs.headcount'), render: row => row.count },
  ]
  const departureColumns: TableColumn<HubOverview['departures'][number]>[] = [
    { key: 'route', header: t('listPanel.route'), render: row => <>{row.originName} → {row.destinationName}</> },
    { key: 'at', header: t('mgr.departures.departsAt'), render: row => dateTime(row.departureAt) },
    { key: 'status', header: t('listPanel.status'), render: row => t(`mgr.departures.statuses.${row.status}`) },
  ]
  const incidentColumns: TableColumn<HubOverview['incidents'][number]>[] = [
    { key: 'title', header: t('incidents.colTitle'), render: row => row.title },
    { key: 'severity', header: t('incidents.severity.label'), render: row => <IncidentSeverityBadge severity={row.severity} /> },
    { key: 'status', header: t('listPanel.status'), render: row => t(`incidents.status.${row.status}`) },
    { key: 'at', header: t('managerOverview.colReported'), render: row => dateTime(row.reportedAt) },
  ]

  const horses = t('dispatch.stays.caption')
  const vehicles = t('managerHubs.vehicles')
  const staff = t('managerHubs.staff')
  const arrivals = t('managerHubs.arrivals')
  const departures = t('managerHubs.departures')
  const incidents = t('managerOverview.incidentsCaption')

  return <div className={s.details}>
    <StatTile icon={<i className="fa-solid fa-warehouse" />} label={t('managerHubs.stalls')}
      value={`${hub.stalls.available}/${hub.stalls.total}`} />
    {!!hub.fleetRequirement?.length && <FleetRequirementTable rows={hub.fleetRequirement} />}
    <Card title={horses}>
      {hub.horses.length ? <Table caption={horses} columns={horseColumns} rows={hub.horses} rowKey={row => row.id} />
        : <EmptyState compact title={t('dispatch.stays.emptyTitle')} description={t('dispatch.stays.emptyDescription')} />}
    </Card>
    <Card title={vehicles}>
      {fleet.length ? <Table caption={vehicles} columns={vehicleColumns} rows={fleet} rowKey={row => `${row.state}-${row.vehicle.id}`} />
        : <EmptyState compact title={t('managerHubs.noVehicles')} description={t('managerHubs.noVehiclesHint')} />}
    </Card>
    <Card title={staff}>
      {hub.staff.length ? <Table caption={staff} columns={staffColumns} rows={hub.staff} rowKey={row => row.roleCode} />
        : <EmptyState compact title={t('managerHubs.noStaff')} description={t('managerHubs.noStaffHint')} />}
    </Card>
    <Card title={arrivals}>
      {hub.arrivals.length ? <Table caption={arrivals} columns={departureColumns} rows={hub.arrivals} rowKey={row => row.id} />
        : <EmptyState compact title={t('managerHubs.noArrivals')} description={t('managerHubs.scheduleHint')} />}
    </Card>
    <Card title={departures}>
      {hub.departures.length ? <Table caption={departures} columns={departureColumns} rows={hub.departures} rowKey={row => row.id} />
        : <EmptyState compact title={t('managerHubs.noDepartures')} description={t('managerHubs.scheduleHint')} />}
    </Card>
    <Card title={incidents} actions={<Link to="/manager/incidents">{t('managerOverview.allIncidents')}</Link>}>
      {hub.incidents.length ? <Table caption={incidents} columns={incidentColumns} rows={hub.incidents} rowKey={row => row.id} />
        : <EmptyState compact title={t('managerOverview.noIncidents')} description={t('managerOverview.noIncidentsDescription')} />}
    </Card>
  </div>
}
