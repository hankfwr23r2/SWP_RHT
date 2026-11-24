// Logic hiển thị của bảng điều độ. Không quyết định nghiệp vụ: backend vẫn kiểm mọi quy tắc khi gán.
import type { DispatchBooking, DispatchHorse, DispatchVehicle, HorseLeg, LegType, NetworkLocation, PlannedTripCreate, TripHorse, TripType, VehicleTrip } from '@shared/api/dispatch'
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

// ISO sang giá trị ô datetime-local (giờ của máy)
export function isoToLocal(value: string) {
  const date = new Date(value)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
}

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

// Chặng còn thiếu của đơn, gộp các con; backend tính
export const missingLegs = (booking: DispatchBooking): LegType[] =>
  [...new Set((booking.horses ?? []).flatMap(horse => horse.missingLegs ?? []))]

const DAY_MS = 24 * 3600 * 1000

export const addDays = (date: string, days: number) =>
  new Date(Date.parse(`${date}T12:00:00${VN_OFFSET}`) + days * DAY_MS).toLocaleDateString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' })

// Thứ Hai của tuần chứa ngày này
export function mondayOf(date: string) {
  const weekday = new Date(`${date}T12:00:00Z`).getUTCDay()
  return addDays(date, weekday === 0 ? -6 : 1 - weekday)
}

// Cột bắt đầu (0–6) và số ngày lượt xe chiếm trong tuần; lượt tràn khỏi tuần thì cắt ở mép
export function daySpan(trip: VehicleTrip, weekStart: string): { from: number; days: number } | undefined {
  if (!trip.plannedStartAt) return undefined
  const start = Date.parse(`${weekStart}T00:00:00${VN_OFFSET}`)
  const from = Math.floor((Date.parse(trip.plannedStartAt) - start) / DAY_MS)
  const to = Math.floor((Date.parse(trip.plannedEndAt ?? trip.plannedStartAt) - start) / DAY_MS)
  if (to < 0 || from > 6) return undefined
  const first = Math.max(0, from)
  return { from: first, days: Math.min(6, to) - first + 1 }
}

export interface BoardRow { vehicle: DispatchVehicle; trips: VehicleTrip[] }

// Mỗi xe một hàng: xe đang dùng được, và xe khác nếu có lượt trong tuần
export function boardRows(vehicles: DispatchVehicle[], trips: VehicleTrip[]): BoardRow[] {
  const shown = trips.filter(trip => trip.status !== 'CANCELLED')
  return vehicles
    .map(vehicle => ({ vehicle, trips: shown.filter(trip => trip.vehicleId === vehicle.id).sort((a, b) => (a.plannedStartAt ?? '').localeCompare(b.plannedStartAt ?? '')) }))
    .filter(row => row.vehicle.status === 'ACTIVE' || row.trips.length > 0)
}

// Lượt chưa chạy mà chưa có tài xế trong tổ lái
export const lacksDriver = (trip: VehicleTrip) =>
  (trip.status === 'PLANNED' || trip.status === 'DISPATCHED') && !(trip.crew ?? []).some(member => member.crewRole === 'DRIVER' && member.status !== 'RELEASED')

// Danh sách điểm của một lượt đón, giao hoặc chạy thẳng: hub, địa chỉ các đơn đã chọn, hub
export function placesFor(type: TripType, hubId: number, bookings: DispatchBooking[]): PlannedTripCreate['places'] {
  const pickups = bookings.map(booking => ({ bookingAddressId: booking.pickupAddress?.id }))
  const deliveries = bookings.map(booking => ({ bookingAddressId: booking.deliveryAddress?.id }))
  const middle = type === 'PICKUP' ? pickups : type === 'DELIVERY' ? deliveries : [...pickups, ...deliveries]
  return [{ locationId: hubId }, ...middle, { locationId: hubId }]
}

export const horsesNeeding = (type: TripType, bookings: DispatchBooking[]) =>
  bookings.flatMap(booking => (booking.horses ?? []).filter(horse => horse.missingLegs?.includes(type as LegType)).map(horse => horse.bookingHorseId ?? 0))

// Các hub, hub gốc của xe (trụ sở) đứng đầu để chọn sẵn
export function hubsOf(locations: NetworkLocation[], vehicles: DispatchVehicle[]) {
  const home = new Set(vehicles.map(vehicle => vehicle.homeHubLocationId))
  return locations.filter(location => location.locationType === 'HUB').sort((a, b) => Number(home.has(b.id)) - Number(home.has(a.id)))
}
