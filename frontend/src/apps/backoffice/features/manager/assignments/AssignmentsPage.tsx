import { useState } from 'react'
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { staffWorkloadsApi } from '@shared/api/staffWorkloads'
import { useLoad } from '@shared/hooks/useLoad'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Skeleton } from '@shared/ui/Skeleton'
import { Table } from '@shared/ui/Table'
import { ReassignSpecialist } from '@backoffice/shared/ReassignSpecialist'
import s from '@backoffice/shared/StaffPanels.module.css'

function AssignedBookings({ staffId, onDone }: { staffId: number; onDone: () => void }) {
  const { t } = useTranslation()
  const { data, error, reload } = useLoad(() => staffWorkloadsApi.bookings(staffId), [staffId])
  if (error && !data) return <ErrorState onRetry={reload} />
  if (!data) return <Skeleton lines={3} />
  if (!data.length) return <EmptyState title={t('mgr.assignments.noBookings')} description={t('mgr.assignments.noBookingsHint')} />
  return <div className={s.stack}>
    {data.map(row => row.id != null && <div className={s.row} key={row.id}>
      <Link to={'/manager/orders/' + row.id}>{row.code}</Link>
      <ReassignSpecialist id={row.id} currentId={staffId} onDone={() => { reload(); onDone() }} />
    </div>)}
  </div>
}

export default function AssignmentsPage() {
  const { t } = useTranslation()
  const { data, error, reload } = useLoad(staffWorkloadsApi.list)
  const [selected, setSelected] = useState<number>()
  const person = data?.find(row => row.staffId === selected)
  return <div className={s.stack}>
    <h1>{t('mgr.assignments.title')}</h1>
    {error && !data ? <ErrorState onRetry={reload} /> : !data ? <Skeleton lines={3} /> :
      !data.length ? <EmptyState title={t('mgr.assignments.noStaff')} description={t('mgr.assignments.noStaffHint')} /> :
        <Table caption={t('mgr.assignments.title')} rows={data} rowKey={row => String(row.staffId)} columns={[
          { key: 'name', header: t('mgr.assignments.specialist'), render: row => row.staffName },
          { key: 'count', header: t('mgr.assignments.bookings'), render: row => row.assignedCount },
          { key: 'actions', header: t('mgr.assignments.showBookings'), render: row =>
            <Button size="sm" aria-expanded={selected === row.staffId}
              onClick={() => setSelected(selected === row.staffId ? undefined : row.staffId)}>{t('mgr.assignments.showBookings')}</Button> },
        ]} />}
    {selected != null && person && <Card title={person.staffName}>
      <AssignedBookings key={selected} staffId={selected} onDone={reload} />
    </Card>}
  </div>
}
