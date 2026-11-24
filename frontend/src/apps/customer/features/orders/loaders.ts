// Dữ liệu các trang đơn của khách, router tải song song với file trang (xem shared/routing/pageData)
import { useLoaderData } from 'react-router'
import { bookingDocumentsApi } from '@shared/api/bookingDocuments'
import { bookingOrdersApi } from '@shared/api/bookingOrders'
import { catalogApi } from '@shared/api/catalog'
import { bookingPaymentsApi } from '@shared/api/payments'
import { quotationsApi } from '@shared/api/quotations'
import { loadAll, type PageLoaderContext } from '@shared/routing/pageData'

export async function ordersLoader() {
  const { orders, errors } = await loadAll({ orders: bookingOrdersApi.list() })
  return { errors: [] as string[], orders, failed: errors.length > 0 }
}

export async function orderDetailLoader({ params }: PageLoaderContext) {
  const id = Number(params.id)
  const { errors: _errors, ...loaded } = await loadAll({
    detail: bookingOrdersApi.detail(id),
    quotations: quotationsApi.ofBooking(id),
    payments: bookingPaymentsApi.ofBooking(id),
    checklist: bookingDocumentsApi.myChecklist(id),
    stallTypes: catalogApi.stallTypes(),
  })
  return { errors: [] as string[], id, ...loaded }
}

export type OrdersData = Awaited<ReturnType<typeof ordersLoader>>
export type OrderData = Awaited<ReturnType<typeof orderDetailLoader>>

export const useOrdersData = () => useLoaderData() as OrdersData
export const useOrderData = () => useLoaderData() as OrderData
