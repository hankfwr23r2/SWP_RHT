import { useTranslation } from 'react-i18next'
import type { HorseStay } from '@shared/api/dispatch'
import { HealthCheckTaskAction } from '@backoffice/shared/HealthCheckTaskAction'
import { HubExitCheckDetails } from '@backoffice/shared/HubExitCheckDetails'
import { StayHealthAction } from '@backoffice/shared/StayHealthAction'
import s from '@backoffice/shared/StaffPanels.module.css'
import h from './Hub.module.css'

export function HubStayHealth({ stay, onSaved }: { stay: HorseStay; onSaved: () => void }) {
  const { t } = useTranslation()
  const { id, bookingHorseId, horseName, hubExitChecks } = stay
  if (id == null || bookingHorseId == null) return null

  return <div className={h.checks}>
    {hubExitChecks?.map(task => (
      <div key={task.id} className={h.checks}>
        <HubExitCheckDetails task={task} />
        {task.recordingHorseStayId != null && <StayHealthAction horseStayId={task.recordingHorseStayId}
          bookingHorseId={bookingHorseId} horseName={horseName} label={t('healthCheckTasks.record')} reported onSaved={onSaved} />}
      </div>
    ))}
    <span className={s.row}>
      <StayHealthAction horseStayId={id} bookingHorseId={bookingHorseId} horseName={horseName} reported onSaved={onSaved} />
      <HealthCheckTaskAction horseStayId={id} bookingHorseId={bookingHorseId} horseName={horseName} onChanged={onSaved} />
    </span>
  </div>
}
