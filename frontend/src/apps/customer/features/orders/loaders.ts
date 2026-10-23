// Dữ liệu các trang đơn của khách, router tải song song với file trang (xem shared/routing/pageData)
import { loadAll, type PageLoaderContext } from '@shared/routing/pageData'

// Đơn của tôi, Lịch sử đơn
export async function ordersLoader({ session }: PageLoaderContext) {
  const { customerBookingsApi } = await import('@shared/services/bookings')
  return loadAll({ orders: customerBookingsApi.list(session.name) })
}

export async function orderDetailLoader({ params, session }: PageLoaderContext) {
  const { customerBookingsApi } = await import('@shared/services/bookings')
  return loadAll({ b: customerBookingsApi.get(session.name, params.id ?? '') })
}
