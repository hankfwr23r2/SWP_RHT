// Dữ liệu các trang nội bộ, router tải song song với file trang (xem shared/routing/pageData).
// Service nạp động để mỗi vai trò chỉ tải phần mình dùng.
import type { Vehicle } from '@shared/types/fleet'
import { loadAll, type PageLoaderContext } from '@shared/routing/pageData'
import type { BackendLocationResponse } from '@shared/api/routes'

const bookings = () => import('@shared/services/bookings')

// Các trang danh sách đơn theo vai trò (bảng điều khiển, tiếp nhận, duyệt báo giá, sự cố, giấy tờ…)
export async function bookingListLoader() {
  const { bookingsApi } = await bookings()
  return loadAll({ all: bookingsApi.list() })
}

export async function accountsLoader() {
  const { accountsApi } = await import('@shared/services/accounts')
  return loadAll({ all: accountsApi.list() })
}

export async function adminRoutesLoader() {
  const { routesApi } = await import('@shared/api/routes')
  return loadAll({ routes: routesApi.listRoutes(), locations: routesApi.listAdminLocations() })
}

export async function adminServicesLoader() {
  const { adminServicesApi } = await import('@shared/api/adminServices')
  return loadAll({ services: adminServicesApi.listServices(), categories: adminServicesApi.listCategories() })
}

export async function systemConfigLoader() {
  const { systemConfigApi } = await import('@shared/api/systemConfig')
  return loadAll({ configs: systemConfigApi.listAdminConfigs() })
}

export async function adminPricingLoader() {
  const { systemConfigApi } = await import('@shared/api/systemConfig')
  return loadAll({ configs: systemConfigApi.listAdminConfigs() })
}

export async function adminHorseTypesLoader() {
  const { backendHorseApi } = await import('@shared/api/horses')
  return loadAll({ horseTypes: backendHorseApi.listTypes() })
}

export async function documentRequirementsLoader() {
  const { documentRequirementsApi } = await import('@shared/api/documentRequirements')
  const { routesApi } = await import('@shared/api/routes')
  return loadAll({
    requirements: documentRequirementsApi.listAdmin(),
    gates: routesApi.listAdminLocations()
      .then((locations): BackendLocationResponse[] => locations.filter(location => location.locationType === 'BorderGate')),
  })
}

// Giám sát vận chuyển: đơn và kíp xe
export async function monitoringLoader() {
  const [{ bookingsApi }, { crewApi }] = await Promise.all([bookings(), import('@shared/services/fleet')])
  return loadAll({ all: bookingsApi.list(), crew: crewApi.list() })
}

// Chốt xe và lộ trình một đơn: đơn và đội xe.
// Xe tải theo ngày đi và loại chuyến của chính đơn: backend quyết định xe nào chọn được (kèm lịch từng xe), cùng quy tắc với bước lưu.
export async function fleetPlanLoader({ params }: PageLoaderContext) {
  const [{ bookingsApi }, { vehiclesApi }] = await Promise.all([bookings(), import('@shared/services/fleet')])
  const b = bookingsApi.get(params.id ?? '')
  const vehicles = b.then(async (found): Promise<Vehicle[]> => (found ? vehiclesApi.forBooking(found) : []))
  return loadAll({ b, vehicles })
}

export async function bookingLoader({ params }: PageLoaderContext) {
  const { bookingsApi } = await bookings()
  return loadAll({ b: bookingsApi.get(params.id ?? '') })
}
