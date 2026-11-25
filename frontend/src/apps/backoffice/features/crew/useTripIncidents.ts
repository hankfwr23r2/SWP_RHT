import { incidentsApi, type IncidentReport } from '@shared/api/incidentOps'
import type { CrewTrip } from '@shared/api/tripExecution'
import { useAutoRefresh, useLoad } from '@shared/hooks/useLoad'

export function useTripIncidents(tripId: number, trip: CrewTrip['trip']) {
  const enabled = trip?.id === tripId && (trip.status === 'IN_PROGRESS' || trip.status === 'COMPLETED')
  const query = useLoad(() => enabled ? incidentsApi.ofTrip(tripId) : Promise.resolve(undefined), [tripId, enabled], { toastOnError: false })
  useAutoRefresh(query.reload)

  async function report(request: IncidentReport) {
    await incidentsApi.report(request)
    await query.reload()
  }

  return { ...query, data: enabled && query.loaded ? query.data : undefined, report }
}
