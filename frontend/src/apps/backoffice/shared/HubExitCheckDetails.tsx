import { useTranslation } from 'react-i18next'
import type { HealthCheckTask } from '@shared/api/healthCheckTasks'
import { formatDateTime } from '@shared/lib/format'
import { StatusBadge } from '@shared/ui/StatusBadge'

export function HubExitCheckDetails({ task }: { task: HealthCheckTask }) {
  const { t } = useTranslation()
  return <>
    <strong>{t(`healthCheckTasks.hubExitTypes.${task.checkType}`)}</strong>
    <StatusBadge tone={task.status === 'DONE' ? 'success' : 'neutral'} label={t(`healthCheckTasks.status.${task.status}`)} />
    {task.overdue && <StatusBadge tone="danger" label={t('healthCheckTasks.overdue')} />}
    {task.dueAt && <div>{t('healthCheckTasks.due')} <time dateTime={task.dueAt}>{formatDateTime(new Date(task.dueAt))}</time></div>}
  </>
}
