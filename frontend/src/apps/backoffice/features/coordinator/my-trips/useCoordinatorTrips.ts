import { coordinatorAssignmentsApi } from '@shared/api/coordinatorAssignments'
import { useLoad } from '@shared/hooks/useLoad'

export function useCoordinatorTrips() {
  return useLoad(coordinatorAssignmentsApi.mine)
}

export function useCoordinatorTripHorses(tripId: number) {
  return useLoad(() => coordinatorAssignmentsApi.horses(tripId), [tripId])
}
