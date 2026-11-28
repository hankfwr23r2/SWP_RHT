import { incidentsApi, type HandoverCauseChange } from '@shared/api/incidentOps'
import { useStaffFormAction } from '@shared/hooks/useStaffFormAction'

export function useHandoverCause() {
  const { run, fields, busy } = useStaffFormAction()
  const amend = (id: number, body: HandoverCauseChange) =>
    run(() => incidentsApi.amendHandoverCause(id, body), false)
  return { amend, fields, busy }
}
