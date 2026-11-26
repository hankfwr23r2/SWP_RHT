import { incidentsApi } from '@shared/api/incidentOps'
import { useLoad } from '@shared/hooks/useLoad'
import { useOrderAction } from './useOrderAction'

export type Decision = { id: number; kind: 'plan' | 'cost'; agree: boolean }

export function useIncidentDecisions(bookingId: number) {
  const load = useLoad(async () => {
    const [costs, plans] = await Promise.all([incidentsApi.customerCosts(bookingId), incidentsApi.customerPlans(bookingId)])
    return { costs, plans }
  }, [bookingId], { toastOnError: false })
  const { run, busy, error } = useOrderAction()

  const respond = async ({ id, kind, agree }: Decision, message: string) => {
    const saved = await run(() => kind === 'plan' ? incidentsApi.acceptPlan(id) : incidentsApi.respondCost(id, agree), message)
    if (saved) await load.reload()
    return saved
  }
  return { ...load, respond, busy, actionError: error }
}
