// Dữ liệu trang chủ khách, router gọi song song với lúc tải file trang: trang hiện ra là đã có số liệu.
import { readSession } from '@shared/auth/session'

const message = (r: PromiseSettledResult<unknown>) =>
  r.status === 'rejected' ? [r.reason instanceof Error ? r.reason.message : 'Không thể tải dữ liệu.'] : []

export async function portalLoader() {
  // Loader chạy trước RequireRole: chưa đăng nhập thì không gọi API, để RequireRole chuyển về trang đăng nhập
  const session = readSession('customer')
  if (session?.role !== 'customer') return null
  // Service nạp động để trang chủ công khai không phải tải kèm
  const [{ customerBookingsApi }, { horsesApi }] = await Promise.all([import('@shared/services/bookings'), import('@shared/services/horses')])
  const [orders, horses] = await Promise.allSettled([customerBookingsApi.list(session.name), horsesApi.list(session.name)])
  return {
    orders: orders.status === 'fulfilled' ? orders.value : undefined,
    horses: horses.status === 'fulfilled' ? horses.value : undefined,
    errors: [...message(orders), ...message(horses)],
  }
}
