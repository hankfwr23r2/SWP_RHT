import { bookingDocumentsApi } from '@shared/api/bookingDocuments'
import { bookingOrdersApi, type BookingSummary } from '@shared/api/bookingOrders'
import { bookingPaymentsApi } from '@shared/api/payments'
import { quotationsApi } from '@shared/api/quotations'
import { groupOf, missingDocuments } from '@shared/components/orderSteps'
import { loadAll } from '@shared/routing/pageData'

async function loadOrderActions(order: BookingSummary) {
  if (order.id == null || ['done', 'closed'].includes(groupOf(order))) return { order, missingHorseIds: [] }

  const id = order.id
  const { checklist, payments, quotations, errors } = await loadAll({
    checklist: bookingDocumentsApi.myChecklist(id),
    payments: ['CONFIRMED', 'IN_OPERATION', 'DELIVERED'].includes(order.status ?? '')
      ? bookingPaymentsApi.ofBooking(id) : Promise.resolve(undefined),
    quotations: order.status === 'AWAITING_DEPOSIT' ? quotationsApi.ofBooking(id) : Promise.resolve(undefined),
  })
  const missing = missingDocuments(checklist).filter(item => item.bookingHorseId != null)
  const detail = missing.length ? await bookingOrdersApi.detail(id).catch(() => undefined) : undefined
  const horseIds = new Map(detail?.horses?.map(horse => [horse.id, horse.horseId]))
  const missingHorseIds = missing.flatMap(item => {
    const horseId = horseIds.get(item.bookingHorseId)
    return horseId == null ? [] : [horseId]
  })
  return {
    order, checklist, payments, quotations, missingHorseIds,
    incomplete: errors.length > 0 || !checklist || missingHorseIds.length !== missing.length,
  }
}

export async function portalLoader() {
  const result = loadAll({ orders: bookingOrdersApi.list() }).then(async ({ orders }) => ({
    orders,
    actions: orders && await Promise.all(orders.map(loadOrderActions)),
  }))
  return { errors: [] as string[], result }
}

export type PortalData = Awaited<Awaited<ReturnType<typeof portalLoader>>['result']>
