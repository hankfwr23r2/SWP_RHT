// Lượt xe của tôi (tài xế, hộ tống): xem phân công và xác nhận nhận việc (docs/MAN_HINH_VA_TAC_DONG.md mục 3.10)
import { useState } from 'react'
import { useAuth } from '@shared/auth/AuthContext'
import { MyHealthCheckTasks } from './MyHealthCheckTasks'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { dispatchApi, type MyCrewAssignment } from '@shared/api/dispatch'
import { useLoad } from '@shared/hooks/useLoad'
import { formatDateTime } from '@shared/lib/format'
import { ListPanel } from '@shared/ui/ListPanel'
import { Badge } from '@shared/ui/Badge'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { PageHeader } from '@shared/ui/PageHeader'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { useToast } from '@shared/ui/toast'
import { MyStayHealthPanel } from './MyStayHealthPanel'
import s from './MyTrips.module.css'

export default function MyTripsPage() {
  const { t } = useTranslation()
  const { session } = useAuth()
  const toast = useToast()
  const [busyId, setBusyId] = useState<number>()
  const { data, loaded, error, reload, updateData } = useLoad(dispatchApi.myAssignments, [], { toastOnError: false })

  const acknowledge = async (assignment: MyCrewAssignment) => {
    setBusyId(assignment.id)
    try {
      const updated = await dispatchApi.acknowledge(assignment.id ?? 0)
      updateData(current => current?.map(item => (item.id === updated.id ? updated : item)))
      toast(t('myTrips.done'), 'success')
    } catch (e) {
      toast(e instanceof Error ? e.message : String(e), 'error')
    } finally {
      setBusyId(undefined)
    }
  }

  const content = () => {
    if (!loaded) return <ListPanel loading />
    if (error || !data) return <ErrorState title={t('myTrips.loadError')} message={error?.message} onRetry={reload} />
    if (!data.length) return <EmptyState icon={<i className="fa-solid fa-truck" />} title={t('myTrips.emptyTitle')} description={t('myTrips.emptyDescription')} />
    return (
      <ul className={s.tripList}>
        {data.map(assignment => (
          <li key={assignment.id}>
            <Card className={s.tripCard}
              title={`${assignment.tripCode} · ${t(`tripWords.type.${assignment.tripType}`)}`}
              actions={<Badge>{t(`tripWords.role.${assignment.crewRole}`)}</Badge>}
            >
              <div className={s.body}>
                <p className={s.line}>{t('myTrips.journey', { from: assignment.startLocationName, to: assignment.endLocationName })}</p>
                <div className={s.tripMeta}>
                  <span>{t('myTrips.vehicle', { plate: assignment.licensePlate })}</span>
                  {assignment.plannedStartAt && <time dateTime={assignment.plannedStartAt}>
                    {t('myTrips.start', { time: formatDateTime(new Date(assignment.plannedStartAt)) })}
                  </time>}
                </div>
                <div className={s.tripActions}>
                  {assignment.status === 'ACKNOWLEDGED'
                    ? <StatusBadge tone="success" label={t('myTrips.acknowledged')} />
                    : <StatusBadge tone="warning" label={t('myTrips.waiting')} />}
                  {assignment.status !== 'ACKNOWLEDGED' && <Button variant="primary" size="lg" loading={busyId === assignment.id}
                    onClick={() => acknowledge(assignment)}>{t('myTrips.acknowledge')}</Button>}
                  <Link to={`/my-trips/${assignment.vehicleTripId}`}
                    className={`btn btn-lg ${assignment.status === 'ACKNOWLEDGED' ? 'btn-primary' : 'btn-outline'}`}>
                    {t('myTrips.run.open')}
                  </Link>
                </div>
              </div>
            </Card>
          </li>
        ))}
      </ul>
    )
  }

  return (
    <div className={s.page}>
      <PageHeader title={t('myTrips.title')} description={t('myTrips.description')} />
      {content()}
      {session?.role === 'escort' && <MyHealthCheckTasks />}
      <MyStayHealthPanel />
    </div>
  )
}
