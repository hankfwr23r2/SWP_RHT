// Dữ liệu trang chủ khách, router gọi song song với lúc tải file trang (xem shared/routing/pageData)
import { bookingOrdersApi } from '@shared/api/bookingOrders'
import { customerHorseApi } from '@shared/api/horses'
import { loadAll } from '@shared/routing/pageData'

export async function portalLoader() {
  return { errors: [] as string[], result: loadAll({ orders: bookingOrdersApi.list(), horses: customerHorseApi.list() }) }
}

export type PortalData = Awaited<Awaited<ReturnType<typeof portalLoader>>['result']>
