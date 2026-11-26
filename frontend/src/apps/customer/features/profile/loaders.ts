// Dữ liệu trang hồ sơ khách, router tải song song với file trang (xem shared/routing/pageData)
import { bookingOrdersApi } from '@shared/api/bookingOrders'
import { customerHorseApi } from '@shared/api/horses'
import { catalogApi } from '@shared/api/catalog'
import { loadAll } from '@shared/routing/pageData'

export async function profileLoader() {
  return { errors: [] as string[], result: loadAll({
    profile: bookingOrdersApi.myProfile(), countries: catalogApi.countries(),
    horses: customerHorseApi.list(), orders: bookingOrdersApi.list(),
  }) }
}
