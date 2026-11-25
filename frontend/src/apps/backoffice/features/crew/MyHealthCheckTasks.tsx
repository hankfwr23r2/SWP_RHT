import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { StayHealthAction } from '@backoffice/shared/StayHealthAction'
import { formatDateTime } from '@shared/lib/format'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Skeleton } from '@shared/ui/Skeleton'
import { useMyHealthCheckTasks } from './useMyHealthCheckTasks'
import s from './MyTrips.module.css'

export function MyHealthCheckTasks() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { data, loaded, error, reload, busyId, accept } = useMyHealthCheckTasks()

  return (
    <section aria-label={t('healthCheckTasks.title')}>
      <h2>{t('healthCheckTasks.title')}</h2>
      {!loaded ? <Skeleton lines={3} /> : error ? (
        <ErrorState title={t('healthCheckTasks.loadError')} onRetry={reload} />
      ) : !data?.length ? (
        <EmptyState title={t('healthCheckTasks.empty')} description={t('healthCheckTasks.mineHint')} />
      ) : (
        <ul className={s.list}>
          {data.map(task => (
            <li key={task.id}>
              <Card title={task.horseName}>
                <div className={s.body}>
                  <p>{task.place} ? {t(`healthCheckTasks.types.${task.checkType}`)}</p>
                  <p>{t(`healthCheckTasks.status.${task.status}`)}{task.overdue && ` ? ${t('healthCheckTasks.overdue')}`}
                    {task.dueAt && ` ? ${formatDateTime(new Date(task.dueAt))}`}</p>
                  {task.note && <p>{task.note}</p>}
                  {task.status === 'ASSIGNED' && task.id != null && (
                    <Button size="lg" variant="primary" disabled={busyId != null} loading={busyId === task.id}
                      onClick={() => void accept(task.id!)}>{t('healthCheckTasks.accept')}</Button>
                  )}
                  {task.horseStayId != null && task.bookingHorseId != null && (
                    <StayHealthAction horseStayId={task.horseStayId} bookingHorseId={task.bookingHorseId}
                      horseName={task.horseName} onSaved={reload} />
                  )}
                  {task.vehicleTripId != null && (
                    <Button size="lg" onClick={() => void navigate(`/my-trips/${task.vehicleTripId}`)}>{t('myTrips.run.open')}</Button>
                  )}
                </div>
              </Card>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
