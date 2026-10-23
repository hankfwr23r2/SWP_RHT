// Dữ liệu các trang dùng hồ sơ ngựa của khách, router tải song song với file trang (xem shared/routing/pageData)
import { loadAll, type PageLoaderContext } from '@shared/routing/pageData'

// Hồ sơ ngựa, Đặt chuyến bước 2: ngựa kèm các đơn để biết ngựa nào đang bận
export async function horsesLoader({ session }: PageLoaderContext) {
  const [{ horsesApi }, { backendBookingApi }] = await Promise.all([import('@shared/services/horses'), import('@shared/api/bookings')])
  return loadAll({ horses: horsesApi.list(session.name), customerBookings: backendBookingApi.getCustomerBookings() })
}

// Đặt chuyến bước 3, 4: chỉ cần ngựa
export async function horseListLoader({ session }: PageLoaderContext) {
  const { horsesApi } = await import('@shared/services/horses')
  return loadAll({ horses: horsesApi.list(session.name) })
}
