import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { healthCheckTasksApi } from '@shared/api/healthCheckTasks'
import { useAutoRefresh, useLoad } from '@shared/hooks/useLoad'
import { useToast } from '@shared/ui/toast'

export function useMyHealthCheckTasks() {
  const { t } = useTranslation()
  const toast = useToast()
  const tasks = useLoad(healthCheckTasksApi.mine, [], { toastOnError: false })
  const [busyId, setBusyId] = useState<number>()
  useAutoRefresh(tasks.reload)

  const accept = async (id: number) => {
    setBusyId(id)
    try {
      await healthCheckTasksApi.accept(id)
      toast(t('healthCheckTasks.accepted'), 'success')
      await tasks.reload()
    } catch {
      toast(t('healthCheckTasks.saveError'), 'error')
    } finally {
      setBusyId(undefined)
    }
  }
  return { ...tasks, busyId, accept }
}
