import { useTranslation } from 'react-i18next'
import type { VehicleTrip } from '@shared/api/dispatch'
import { formatDateTime } from '@shared/lib/format'
import { Badge } from '@shared/ui/Badge'
import { Button } from '@shared/ui/Button'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { Table, type TableColumn } from '@shared/ui/Table'
import { TRIP_STATUS_TONE } from './dispatchModel'

export function TripsTable({ trips, onOpen }: { trips: VehicleTrip[]; onOpen: (trip: VehicleTrip) => void }) {
  const { t } = useTranslation()
  const columns: TableColumn<VehicleTrip>[] = [
    { key: 'code', header: t('dispatch.trips.colCode'), render: trip => trip.code },
    { key: 'type', header: t('dispatch.trips.colType'), render: trip => <Badge tone="primary">{t(`dispatch.tripType.${trip.tripType}`)}</Badge> },
    { key: 'vehicle', header: t('dispatch.trips.colVehicle'), render: trip => trip.licensePlate },
    { key: 'route', header: t('dispatch.trips.colRoute'), render: trip => `${trip.startLocationName} → ${trip.endLocationName}` },
    { key: 'time', header: t('dispatch.trips.colTime'), render: trip => (trip.plannedStartAt ? formatDateTime(new Date(trip.plannedStartAt)) : '') },
    {
      key: 'crew',
      header: t('dispatch.trips.colCrew'),
      render: trip => {
        const crew = (trip.crew ?? []).filter(member => member.status !== 'RELEASED')
        return crew.length ? crew.map(member => member.fullName).join(', ') : t('dispatch.trips.noCrew')
      },
    },
    { key: 'status', header: t('dispatch.trips.colStatus'), render: trip => <StatusBadge tone={TRIP_STATUS_TONE[trip.status ?? 'PLANNED']} label={t(`dispatch.tripStatus.${trip.status}`)} /> },
    { key: 'open', header: '', align: 'end', render: trip => <Button size="sm" onClick={() => onOpen(trip)}>{t('dispatch.trips.view')}</Button> },
  ]
  return <Table caption={t('dispatch.trips.caption')} columns={columns} rows={trips} rowKey={trip => trip.id ?? 0} />
}
