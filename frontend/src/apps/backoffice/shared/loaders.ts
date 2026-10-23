// Dữ liệu các trang nội bộ, router tải song song với file trang (xem shared/routing/pageData).
// Service nạp động để mỗi vai trò chỉ tải phần mình dùng.
import { loadAll, type PageLoaderContext } from '@shared/routing/pageData'
import type { HorseProfile } from '@shared/types/booking'

const bookings = () => import('@shared/services/bookings')

// Các trang danh sách đơn theo vai trò (bảng điều khiển, tiếp nhận, duyệt báo giá, sự cố, giấy tờ…)
export async function bookingListLoader() {
  const { bookingsApi } = await bookings()
  return loadAll({ all: bookingsApi.list() })
}

export async function tripReportsLoader() {
  const { reportsApi } = await import('@shared/services/reports')
  return loadAll({ reports: reportsApi.list() })
}

export async function accountsLoader() {
  const { accountsApi } = await import('@shared/services/accounts')
  return loadAll({ all: accountsApi.list() })
}

export async function adminRoutesLoader() {
  const { routesApi } = await import('@shared/api/routes')
  return loadAll({ routes: routesApi.listRoutes(), locations: routesApi.listLocations() })
}

// Giám sát vận chuyển: đơn và kíp xe
export async function monitoringLoader() {
  const [{ bookingsApi }, { crewApi }] = await Promise.all([bookings(), import('@shared/services/fleet')])
  return loadAll({ all: bookingsApi.list(), crew: crewApi.list() })
}

// Chốt xe và lộ trình một đơn: đơn, mọi đơn (để biết lịch xe) và đội xe
export async function fleetPlanLoader({ params }: PageLoaderContext) {
  const [{ bookingsApi }, { vehiclesApi }] = await Promise.all([bookings(), import('@shared/services/fleet')])
  return loadAll({ b: bookingsApi.get(params.id ?? ''), all: bookingsApi.list(), vehicles: vehiclesApi.list() })
}

export async function bookingLoader({ params }: PageLoaderContext) {
  const { bookingsApi } = await bookings()
  return loadAll({ b: bookingsApi.get(params.id ?? '') })
}

// Duyệt hồ sơ ngựa một đơn: đơn, rồi hồ sơ từng ngựa trong đơn
export async function verifyLoader({ params }: PageLoaderContext) {
  const [{ bookingsApi }, { horsesApi }] = await Promise.all([bookings(), import('@shared/services/horses')])
  const { b, errors } = await loadAll({ b: bookingsApi.get(params.id ?? '') })
  const horses: Promise<HorseProfile[]> = Promise.all((b?.horses ?? []).map(h => horsesApi.byId(h.horseId)))
    .then(list => list.filter((h): h is HorseProfile => !!h))
  const more = await loadAll({ horses })
  return { b, horses: more.horses, errors: [...errors, ...more.errors] }
}
