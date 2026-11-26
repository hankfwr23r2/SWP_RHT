import { tripExecutionApi } from '@shared/api/tripExecution'
import { useLoad } from '@shared/hooks/useLoad'

export function useMyHorseStays() {
  return useLoad(tripExecutionApi.myHorseStays, [], { toastOnError: false })
}
