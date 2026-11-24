// Dữ liệu các trang nội bộ, router tải song song với file trang (xem shared/routing/pageData).
// Service nạp động để mỗi vai trò chỉ tải phần mình dùng.
import type { Vehicle } from '@shared/types/fleet'
import { loadAll, type PageLoaderContext } from '@shared/routing/pageData'
import type { Booking, HorseProfile } from '@shared/types/booking'

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

// Duyệt hồ sơ ngựa một đơn: đơn, rồi hồ sơ từng ngựa trong đơn
export async function verifyLoader({ params }: PageLoaderContext) {
  const [{ bookingsApi }, { horsesApi }] = await Promise.all([bookings(), import('@shared/services/horses')])
  const { b, errors } = await loadAll({ b: bookingsApi.get(params.id ?? '') })
  const horses: Promise<HorseProfile[]> = Promise.all((b?.horses ?? []).map(h => horsesApi.byId(h.horseId)))
    .then(list => list.filter((h): h is HorseProfile => !!h))
  const more = await loadAll({ horses })
  return { b, horses: more.horses, errors: [...errors, ...more.errors] }
}

export async function insuranceListLoader() {
  const [{ specialistApi }, { toBooking }] = await Promise.all([import('@shared/api/specialist'), import('@shared/services/bookingMapper')])
  const all: Promise<Booking[]> = specialistApi.getInsuranceBookings().then(rows => rows.map(row => toBooking(row)))
  return loadAll<{ all: Promise<Booking[]> }>({ all })
}
