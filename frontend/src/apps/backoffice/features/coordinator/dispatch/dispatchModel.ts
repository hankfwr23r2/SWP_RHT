// Logic hiển thị của bảng điều độ. Không quyết định nghiệp vụ: backend vẫn kiểm mọi quy tắc khi gán.
import type { DispatchBooking, DispatchHorse, HorseLeg, TripHorse, VehicleTrip } from '@shared/api/dispatch'
import type { StatusTone } from '@shared/ui/StatusBadge'

// Sắc thái của trạng thái lượt xe, một nơi cho cả bảng điều độ
export const TRIP_STATUS_TONE: Record<string, StatusTone> = {
  PLANNED: 'neutral', DISPATCHED: 'info', IN_PROGRESS: 'info', COMPLETED: 'success', CANCELLED: 'danger',
}

const VN_OFFSET = '+07:00'
const INACTIVE_LEGS = new Set(['CANCELLED', 'FAILED'])

// Một tuần bắt đầu từ ngày chọn: ngày (cho danh sách đơn) và mốc thời gian (cho danh sách lượt xe), theo giờ Việt Nam
export function weekRange(fromDate: string) {
  const start = new Date(`${fromDate}T00:00:00${VN_OFFSET}`)
  const end = new Date(start.getTime() + 7 * 24 * 3600 * 1000)
  const toDate = new Date(end.getTime() - 24 * 3600 * 1000).toLocaleDateString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' })
  return { fromDate, toDate, from: start.toISOString(), to: end.toISOString() }
}

// Giá trị ô datetime-local (giờ của máy) sang ISO
export const localToIso = (value: string) => new Date(value).toISOString()

export const activeLegs = (horse: DispatchHorse): HorseLeg[] => (horse.legs ?? []).filter(leg => !INACTIVE_LEGS.has(leg.status ?? ''))

const stopWithAddress = (trip: VehicleTrip, addressId?: number) =>
  addressId === undefined ? undefined : trip.stops?.find(stop => stop.bookingAddressId === addressId)

// Điểm lên và điểm xuống gợi ý cho một con của đơn trên lượt xe; undefined nếu lượt không hợp với đơn
export function suggestedStops(trip: VehicleTrip, booking: DispatchBooking): { board: number; alight: number } | undefined {
  const stops = trip.stops ?? []
  const first = stops[0]
  const last = stops[stops.length - 1]
  const pickup = stopWithAddress(trip, booking.pickupAddress?.id)
  const delivery = stopWithAddress(trip, booking.deliveryAddress?.id)
  const pair = (board?: { id?: number }, alight?: { id?: number }) =>
    board?.id !== undefined && alight?.id !== undefined ? { board: board.id, alight: alight.id } : undefined
  switch (trip.tripType) {
    case 'LINEHAUL': return booking.departureId !== undefined && booking.departureId === trip.departureId ? pair(first, last) : undefined
    case 'PICKUP': return pair(pickup, last)
    case 'DELIVERY': return pair(first, delivery)
    case 'DIRECT': return booking.departureId === undefined || booking.departureId === null ? pair(pickup, delivery) : undefined
    case 'TRANSFER': return pair(first, last)
    default: return undefined
  }
}

export interface HorseOption { booking: DispatchBooking; horse: DispatchHorse; board: number; alight: number }

// Ngựa có thể xếp vào lượt: con ACTIVE, đơn hợp với lượt, chưa có trên lượt này
export function eligibleHorses(trip: VehicleTrip, bookings: DispatchBooking[], onTrip: TripHorse[]): HorseOption[] {
  const taken = new Set(onTrip.map(item => item.leg?.bookingHorseId))
  return bookings.flatMap(booking => {
    const stops = suggestedStops(trip, booking)
    if (!stops) return []
    return (booking.horses ?? [])
      .filter(horse => horse.status === 'ACTIVE' && !taken.has(horse.bookingHorseId))
      .map(horse => ({ booking, horse, ...stops }))
  })
}

// Số chỗ đang dùng lớn nhất trên các đoạn của lượt xe (khoang rộng 2 chỗ)
export function peakSeats(trip: VehicleTrip, onTrip: TripHorse[]): number {
  const sequenceOf = new Map((trip.stops ?? []).map(stop => [stop.id, stop.sequenceNumber ?? 0]))
  const last = Math.max(0, ...(trip.stops ?? []).map(stop => stop.sequenceNumber ?? 0))
  let peak = 0
  for (let segment = 1; segment < last; segment++) {
    const used = onTrip
      .filter(item => (sequenceOf.get(item.leg?.boardTripStopId) ?? 0) <= segment && segment < (sequenceOf.get(item.leg?.alightTripStopId) ?? 0))
      .reduce((sum, item) => sum + (item.seats ?? 0), 0)
    peak = Math.max(peak, used)
  }
  return peak
}

// Mọi con ACTIVE của đơn đã có ít nhất một chặng
export const isBookingPlanned = (booking: DispatchBooking) =>
  (booking.horses ?? []).filter(horse => horse.status === 'ACTIVE').every(horse => activeLegs(horse).length > 0)
