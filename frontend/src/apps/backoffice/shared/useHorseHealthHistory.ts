import { tripExecutionApi } from '@shared/api/tripExecution'
import { useLoad } from '@shared/hooks/useLoad'

export function useHorseHealthHistory(bookingHorseId: number) {
  return useLoad(() => tripExecutionApi.healthLogs(bookingHorseId), [bookingHorseId], { toastOnError: false })
}
