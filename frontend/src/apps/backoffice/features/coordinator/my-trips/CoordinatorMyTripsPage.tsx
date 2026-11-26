import { Input } from '@shared/ui/Input'
import { ListPanel } from '@shared/ui/ListPanel'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { PageHeader } from '@shared/ui/PageHeader'
import { Skeleton } from '@shared/ui/Skeleton'
import { Table } from '@shared/ui/Table'
import { TripReturnDetails } from '@backoffice/shared/TripReturnDetails'
import { CoordinatorTripHorses } from './CoordinatorTripHorses'
import { useCoordinatorTrips } from './useCoordinatorTrips'
import s from '@backoffice/shared/StaffPanels.module.css'

export default function CoordinatorMyTripsPage() {
  const { t } = useTranslation()
  const { data, error, reload } = useCoordinatorTrips()
  const [open, setOpen] = useState<{ id: number; code?: string }>()
  const [query, setQuery] = useState('')
  const term = query.trim().toLowerCase()
  const shown = data?.filter(row => !term || [row.trip?.code, row.trip?.licensePlate, row.currentPoint].some(value => value?.toLowerCase().includes(term)))
  return <div className={s.page}>
    <PageHeader title={t('coordAssignment.mine')} />
    <ListPanel count={shown?.length} toolbar={<Input type="search" value={query} onChange={event => setQuery(event.target.value)}
          placeholder={t('listPanel.search')} aria-label={t('listPanel.search')} />}>{!shown ? error ? <ErrorState onRetry={reload} /> : <Skeleton lines={5} />
      : !shown.length ? <EmptyState compact title={t(term ? 'listPanel.noMatch' : 'coordAssignment.empty')} description={t(term ? 'listPanel.trySearch' : 'coordAssignment.emptyHint')} />
        : <Table caption={t('coordAssignment.mine')} rows={shown} rowKey={row => row.trip?.id ?? 0} columns={[
          { key: 'vehicle', header: t('coordAssignment.vehicle'), render: row => row.trip && <>
            {[row.trip.code, row.trip.licensePlate].filter(Boolean).join(' · ')}
            <TripReturnDetails trip={row.trip} />
          </> },
          { key: 'crew', header: t('coordAssignment.crew'), render: row =>
            row.trip?.crew?.filter(member => member.status !== 'RELEASED').map(member => member.fullName).join(', ') || '—' },
          { key: 'point', header: t('coordAssignment.point'), render: row => row.currentPoint },
          { key: 'late', header: t('coordAssignment.late'), render: row => row.late ? t('coordAssignment.yes') : t('coordAssignment.no') },
          { key: 'horses', header: t('coordAssignment.horses'), render: row => row.horseCount },
          { key: 'incidents', header: t('coordAssignment.incidents'), render: row => row.trip?.openIncidentCount },
          { key: 'detail', header: t('coordAssignment.view'), render: row => {
            const trip = row.trip
            return trip?.id == null ? null : <Button size="sm" onClick={() => setOpen({ id: trip.id!, code: trip.code })}>
              {t('coordAssignment.view')}
            </Button>
          } },
        ]} />}</ListPanel>
    {open && <CoordinatorTripHorses tripId={open.id} code={open.code} onClose={() => setOpen(undefined)} />}
  </div>
}
