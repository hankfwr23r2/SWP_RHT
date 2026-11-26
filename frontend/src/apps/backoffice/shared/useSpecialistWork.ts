import { useLocation, useRevalidator } from 'react-router'
import { specialistWorkApi } from '@shared/api/specialistWork'
import { useAutoRefresh, useLoad } from '@shared/hooks/useLoad'

export function useSpecialistWork(enabled: boolean) {
  const { pathname } = useLocation()
  const { state } = useRevalidator()
  const work = useLoad(() => enabled && state === 'idle' ? specialistWorkApi.summary() : Promise.resolve(undefined),
    [enabled, pathname, state], { toastOnError: false })
  useAutoRefresh(work.reload)
  return work.data
}
