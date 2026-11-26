import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { healthCheckTasksApi, type HealthCheckTarget, type HealthCheckTaskDraft } from '@shared/api/healthCheckTasks'
import { useLoad } from '@shared/hooks/useLoad'
import { useToast } from '@shared/ui/toast'

export function useHealthCheckAssignment(target: HealthCheckTarget, onChanged?: () => void) {
  const { bookingHorseId, vehicleTripId, horseStayId } = target
  const { t } = useTranslation()
  const toast = useToast()
  const assignees = useLoad(() => healthCheckTasksApi.assignees(target), [bookingHorseId, vehicleTripId, horseStayId], { toastOnError: false })
  const tasks = useLoad(() => healthCheckTasksApi.ofHorse(bookingHorseId), [bookingHorseId], { toastOnError: false })
  const [errors, setErrors] = useState<Record<string, string>>({})

  const assign = async (draft: HealthCheckTaskDraft) => {
    setErrors({})
    try {
      await healthCheckTasksApi.assign(draft)
      toast(t('healthCheckTasks.assigned'), 'success')
      onChanged?.()
      await tasks.reload()
    } catch (error) {
      if (error instanceof Error && 'fields' in error) setErrors(error.fields as Record<string, string>)
      toast(t('healthCheckTasks.saveError'), 'error')
      throw error
    }
  }

  const cancel = async (id: number, reason: string) => {
    setErrors({})
    try {
      await healthCheckTasksApi.cancel(id, reason)
      toast(t('healthCheckTasks.cancelled'), 'success')
      onChanged?.()
      await tasks.reload()
    } catch (error) {
      if (error instanceof Error && 'fields' in error) setErrors(error.fields as Record<string, string>)
      toast(t('healthCheckTasks.saveError'), 'error')
      throw error
    }
  }

  return { assignees, tasks, errors, assign, cancel }
}
