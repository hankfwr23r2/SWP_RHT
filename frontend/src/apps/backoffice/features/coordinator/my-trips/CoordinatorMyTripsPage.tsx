import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { PageHeader } from '@shared/ui/PageHeader'
import { Skeleton } from '@shared/ui/Skeleton'
import { Table } from '@shared/ui/Table'
import { CoordinatorTripHorses } from './CoordinatorTripHorses'
import { useCoordinatorTrips } from './useCoordinatorTrips'
import s from '@backoffice/shared/StaffPanels.module.css'

export default function CoordinatorMyTripsPage() {
  const { t } = useTranslation()
  const { data, error, reload } = useCoordinatorTrips()
  const [open, setOpen] = useState<{ id: number; code?: string }>()
  return <div className={s.page}>
    <PageHeader title={t('coordAssignment.mine')} />
    {!data ? error ? <ErrorState onRetry={reload} /> : <Skeleton lines={5} />
      : !data.length ? <EmptyState title={t('coordAssignment.empty')} description={t('coordAssignment.emptyHint')} />
        : <Table caption={t('coordAssignment.mine')} rows={data} rowKey={row => row.trip?.id ?? 0} columns={[
          { key: 'vehicle', header: t('coordAssignment.vehicle'), render: row => [row.trip?.code, row.trip?.licensePlate].filter(Boolean).join(' · ') },
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
        ]} />}
    {open && <CoordinatorTripHorses tripId={open.id} code={open.code} onClose={() => setOpen(undefined)} />}
  </div>
}
