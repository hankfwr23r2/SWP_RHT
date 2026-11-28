import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { tripExecutionApi, type HealthLogCreate } from '@shared/api/tripExecution'
import { useToast } from '@shared/ui/toast'

export function useStayHealth(onSaved: () => void) {
  const { t } = useTranslation()
  const toast = useToast()
  const [errors, setErrors] = useState<Record<string, string>>({})

  const save = async (request: HealthLogCreate) => {
    setErrors({})
    try {
      await tripExecutionApi.recordHealth(request)
      toast(t('myTrips.run.health.saved'), 'success')
      onSaved()
    } catch (error) {
      if (error instanceof Error && 'fields' in error) setErrors(error.fields as Record<string, string>)
      toast(t('stayHealth.saveError'), 'error')
    }
  }

  return { save, errors }
}
