import { useLoaderData } from 'react-router'
import { bookingDocumentsApi } from '@shared/api/bookingDocuments'
import { managerOrdersApi } from '@shared/api/managerOrders'
import { bookingPaymentsApi } from '@shared/api/payments'
import { quotationsApi } from '@shared/api/quotations'
import { settlementApi, type Settlement } from '@shared/api/settlement'
import { loadAll, type PageLoaderContext } from '@shared/routing/pageData'
import { canSettle } from './orderGroups'

export async function orderLoader({ params }: PageLoaderContext) {
  const id = Number(params.id)
  const detail = managerOrdersApi.detail(id)
  const loaded = await loadAll({
    detail,
    documents: bookingDocumentsApi.list(id),
    quotations: quotationsApi.ofBooking(id),
    payments: bookingPaymentsApi.ofBooking(id),
    settlement: detail.then(row => canSettle(row.booking?.status) ? settlementApi.get(id) : undefined),
  })
  return {
    errors: [] as string[], id, detail: loaded.detail, documents: loaded.documents, quotations: loaded.quotations, payments: loaded.payments,
    settlement: loaded.settlement as Settlement | undefined,
  }
}

export type OrderData = Awaited<ReturnType<typeof orderLoader>>
export const useOrderData = () => useLoaderData() as OrderData
