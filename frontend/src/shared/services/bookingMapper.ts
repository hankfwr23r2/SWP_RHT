// Chuyển đơn từ backend (BookingResponse) sang kiểu Booking của frontend.
// Không điền giá trị mặc định giả: trường backend chưa trả thì để trống hoặc không có.
import type { BackendBookingResponse } from '../api/bookings'
import type { BookingStatus } from '../config/booking-rules'
import type { CountryCode } from '../config/network'
import type { Booking, BookingHorse, Clearance, HistoryEntry, MedicalReview, Payment, PlaceRef, Quote, Rating, RoutePlan, Settlement, VehicleTrip } from '../types/booking'
import { COUNTRY_LOCATIONS, PLACES } from '../config/network'

const allPlaces = (): { id: string; name: string; country: CountryCode }[] => [
  ...(Object.entries(COUNTRY_LOCATIONS) as [CountryCode, { id: string; name: string }[]][]).flatMap(([country, list]) => list.map(l => ({ id: l.id, name: l.name, country }))),
  ...PLACES,
]

// Backend chỉ lưu tên điểm và mã nước; tra lại mã điểm trong danh mục địa điểm của hệ thống
export function placeOf(name: string, country?: string): PlaceRef {
  const hit = allPlaces().find(p => p.name === name)
  if (hit) return { id: hit.id, name: hit.name, country: hit.country }
  return { id: name, name, country: (country as CountryCode) || 'VN' }
}

const num = (v: unknown): number | undefined => (typeof v === 'number' ? v : undefined)

function paymentOf(p: Record<string, unknown> | undefined): Payment | undefined {
  const paidAt = num(p?.paidAt), amount = num(p?.amount)
  return paidAt !== undefined && amount !== undefined ? { paidAt, amount, reference: String(p?.reference ?? '') } : undefined
}

// Tiền 70% ngày D: backend ghi cùng bản ghi thanh toán với tiền cọc, khác khóa
function balanceOf(p: Record<string, unknown> | undefined): Payment | undefined {
  if (!p?.balancePaid) return undefined
  const paidAt = num(p.balancePaidAt), amount = num(p.balanceAmount)
  return paidAt !== undefined && amount !== undefined ? { paidAt, amount, reference: String(p.balanceTransaction ?? '') } : undefined
}

function horseOf(h: BackendBookingResponse['horses'][number]): BookingHorse {
  return {
    horseId: h.horseId ?? '', name: h.name, microchip: h.microchip ?? '', breed: h.breed ?? '',
    sex: h.sex as BookingHorse['sex'], stall: h.stall as BookingHorse['stall'],
    feedPackage: h.feedPackage as BookingHorse['feedPackage'], waterPlan: h.waterPlan as BookingHorse['waterPlan'],
    insurance: { opted: !!h.insuranceOpted },
  }
}

function tripOf(t: NonNullable<BackendBookingResponse['trips']>[number]): VehicleTrip {
  const { tripId, vehicleId, driverId, escortId, horseIds, ...rest } = t
  return { tripId, vehicleId, driverId: driverId ?? '', escortId: escortId ?? '', horseIds, acks: {}, ...rest } as VehicleTrip
}

export function toBooking(r: BackendBookingResponse, customer?: string): Booking {
  const history: HistoryEntry[] = r.history ?? []
  const waybillAt = num(r.payment?.paidAt) ?? r.createdAt
  const b: Booking = {
    id: r.id,
    customer: customer ?? r.customerName ?? r.customer,
    createdAt: r.createdAt,
    type: r.type as Booking['type'],
    origin: placeOf(r.originName, r.originCountry),
    dest: placeOf(r.destName, r.destCountry),
    departAt: r.departAt,
    consignor: { name: r.consignor?.name ?? '', phone: r.consignor?.phone ?? '', idNumber: '', address: '' },
    consignee: { name: r.consignee?.name ?? '', phone: r.consignee?.phone ?? '', idNumber: '', address: '' },
    horses: (r.horses ?? []).map(horseOf),
    status: r.status as BookingStatus,
    history,
  }
  if (r.gate) b.gate = r.gate
  if (r.intake) b.intake = r.intake as Booking['intake']
  if (r.medical) b.medical = r.medical as MedicalReview
  if (r.trips?.length) b.trips = r.trips.map(tripOf)
  if (r.plan) b.plan = r.plan as Booking['plan']
  if (r.route) b.route = r.route as RoutePlan
  if (r.quote) b.quote = r.quote as Quote
  const payment = paymentOf(r.payment)
  if (payment) b.payment = payment
  const balance = balanceOf(r.payment)
  if (balance) b.balance = balance
  if (r.waybillNo) b.waybill = { no: r.waybillNo, issuedAt: waybillAt }
  if (r.clearance) b.clearance = r.clearance as Clearance
  if (r.settlement) b.settlement = r.settlement as Settlement
  if (r.rating) b.rating = r.rating as Rating
  return b
}

