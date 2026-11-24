// Dữ liệu các trang nội bộ, router tải song song với file trang (xem shared/routing/pageData).
// Service nạp động để mỗi vai trò chỉ tải phần mình dùng.
import type { Vehicle } from '@shared/types/fleet'
import { loadAll, type PageLoaderContext } from '@shared/routing/pageData'

const bookings = () => import('@shared/services/bookings')

// Các trang danh sách đơn theo vai trò (bảng điều khiển, tiếp nhận, duyệt báo giá, sự cố, giấy tờ…)
export async function bookingListLoader() {
  const { bookingsApi } = await bookings()
  return loadAll({ all: bookingsApi.list() })
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
