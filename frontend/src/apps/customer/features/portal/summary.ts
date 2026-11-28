import type { BookingSummary } from '@shared/api/bookingOrders'
import { groupOf, nextStepOf } from '@shared/components/orderSteps'

type OrderActions = { order: BookingSummary } & Omit<Parameters<typeof nextStepOf>[0], 'status'>

export function portalSummary(orders: BookingSummary[], actions: OrderActions[] = []) {
  const newest = [...orders].sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? '') || (b.id ?? 0) - (a.id ?? 0))
  const byId = new Map(actions.map(entry => [entry.order.id, entry]))
  const todo = newest.map(order => ({
    order, next: nextStepOf({ ...byId.get(order.id), status: order.status }),
  })).filter(({ next }) => next.action)
  return {
    action: todo.length,
    processing: newest.filter(order => groupOf(order) === 'processing').length,
    moving: newest.filter(order => order.status === 'IN_OPERATION').length,
    todo,
    recent: newest,
  }
}
