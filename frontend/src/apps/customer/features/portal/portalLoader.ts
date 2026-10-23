// Dữ liệu trang chủ khách, router gọi song song với lúc tải file trang: trang hiện ra là đã có số liệu.
import { loadAll, type PageLoaderContext } from '@shared/routing/pageData'

export async function portalLoader({ session }: PageLoaderContext) {
  // Service nạp động để trang chủ công khai không phải tải kèm
  const [{ customerBookingsApi }, { horsesApi }] = await Promise.all([import('@shared/services/bookings'), import('@shared/services/horses')])
  return loadAll({ orders: customerBookingsApi.list(session.name), horses: horsesApi.list(session.name) })
}
