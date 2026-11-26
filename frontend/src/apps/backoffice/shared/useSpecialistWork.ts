import { useLocation } from 'react-router'
import { specialistWorkApi } from '@shared/api/specialistWork'
import { useAutoRefresh, useLoad } from '@shared/hooks/useLoad'

// Số việc của chuyên viên: làm mới khi chuyển trang và theo chu kỳ tự làm mới
export function useSpecialistWork(enabled: boolean) {
  const { pathname } = useLocation()
  const work = useLoad(() => enabled ? specialistWorkApi.summary() : Promise.resolve(undefined),
    [enabled, pathname], { toastOnError: false })
  useAutoRefresh(work.reload)
  return work.data
}
