// Chuyển đơn từ backend (BookingResponse) sang kiểu Booking của frontend.
// Không điền giá trị mặc định giả: trường backend chưa trả thì để trống hoặc không có.
import type { BackendBookingResponse } from '../api/bookings'
import type { BackendIncidentResponse } from '../api/incidents'
import type { BookingStatus, IncidentKind } from '../config/booking-rules'
import type { CountryCode } from '../config/network'
import type { Booking, BookingHorse, Cancellation, Clearance, HistoryEntry, Incident, IncidentExpense, MedicalReview, Payment, PlaceRef, Quote, Rating, Rejection, RoutePlan, Settlement, VehicleTrip } from '../types/booking'
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

// Khách hủy sau cọc: backend ghi vào cùng bản ghi thanh toán (cancelled, cancelledAt, cancelReason, refundAmount).
// Đơn cũ hủy trước khi có cancelledAt thì chưa biết thời điểm hủy.
function cancellationOf(p: Record<string, unknown> | undefined): Cancellation | undefined {
  if (!p?.cancelled) return undefined
  return { at: num(p.cancelledAt), reason: String(p.cancelReason ?? ''), by: 'customer', refund: num(p.refundAmount) ?? 0 }
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
    selectedServices: h.selectedServices,
    selectedServiceCodes: h.selectedServiceCodes,
    insurance: { opted: !!h.insuranceOpted },
    status: (h.status?.toLowerCase() === 'unfit' ? 'unfit' : 'eligible'),
    unfitInfo: h.unfitInfo as BookingHorse['unfitInfo'],
  }
}

function tripOf(t: NonNullable<BackendBookingResponse['trips']>[number]): VehicleTrip {
  const { tripId, vehicleId, driverId, escortId, horseIds, acks, run, ...rest } = t as any
  const normalizedRun = run ? {
    ...run,
    checkpoints: Array.isArray(run.checkpoints)
      ? run.checkpoints.map((c: any) => ({
          ...c,
          id: c.id ?? 'checkpoint',
          type: c.type ?? 'rest',
          label: c.label || c.name || (c.type === 'pickup' ? 'Điểm đón' : c.type === 'delivery' ? 'Điểm giao' : c.type === 'customs' ? 'Cửa khẩu' : c.id || 'Trạm nghỉ'),
          place: c.place || c.location || '',
          plannedAt: Number(c.plannedAt || 0),
        }))
      : [],
    welfare: Array.isArray(run.welfare) ? run.welfare : [],
  } : undefined
  return { tripId, vehicleId, driverId: driverId ?? '', escortId: escortId ?? '', horseIds, ...rest, run: normalizedRun, acks: acks ?? {} } as VehicleTrip
}

export function incidentKindOf(value: string): IncidentKind | undefined {
  switch (value.toUpperCase()) {
    case 'HORSE_HEALTH': return 'horse_health'
    case 'VEHICLE_BREAKDOWN': return 'vehicle_breakdown'
    case 'TRAFFIC_JAM':
    case 'TRAFFIC_DELAY': return 'traffic_jam'
    default: return undefined
  }
}

export function incidentStatusOf(value: string): Incident['status'] {
  switch (value.toLowerCase()) {
    case 'proposed':
    case 'pending_approval': return 'pending_approval'
    case 'active': return 'active'
    case 'resolved': return 'resolved'
    default: return 'reported'
  }
}

export function incidentOf(response: BackendIncidentResponse): Incident | undefined {
  const kind = incidentKindOf(response.incidentType)
  if (!kind || !response.tripId || !response.location || response.createdAt === undefined) return undefined
  const expenses: IncidentExpense[] = (response.expenses ?? []).map(expense => ({
    ...expense,
    category: expense.category as IncidentExpense['category'],
    payer: expense.payer === 'customer' ? 'customer' : 'carrier',
  }))
  return {
    id: response.formattedId,
    tripId: response.tripId,
    kind,
    reportedBy: response.reporter ?? '',
    reportedAt: response.createdAt,
    location: response.location,
    photo: response.photoUrl ?? '',
    note: response.description ?? '',
    status: incidentStatusOf(response.status),
    expenses,
    ...(response.plan ? { plan: response.plan } : {}),
    ...(response.directive ? { directive: response.directive } : {}),
    ...(response.rejection ? { rejection: response.rejection } : {}),
    ...(response.approval ? { approval: response.approval } : {}),
    ...(response.fitConfirmedAt !== undefined ? { fitConfirmedAt: response.fitConfirmedAt } : {}),
    ...(response.resolvedAt !== undefined ? { resolvedAt: response.resolvedAt } : {}),
  }
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
    consignor: { name: r.consignor?.name ?? '', phone: r.consignor?.phone ?? '', idNumber: r.consignor?.idNumber ?? '', address: r.consignor?.address ?? '', idCountry: r.consignor?.idCountry as Booking['consignor']['idCountry'], idType: r.consignor?.idType as Booking['consignor']['idType'] },
    consignee: { name: r.consignee?.name ?? '', phone: r.consignee?.phone ?? '', idNumber: r.consignee?.idNumber ?? '', address: r.consignee?.address ?? '', idCountry: r.consignee?.idCountry as Booking['consignee']['idCountry'], idType: r.consignee?.idType as Booking['consignee']['idType'] },
    horses: (r.horses ?? []).map(horseOf),
    status: (r.status === 'resubmission_requested' ? 'under_review' : r.status) as BookingStatus,
    statusLabel: r.statusLabel,
    customerStatusLabel: r.customerStatusLabel,
    history,
  }
  if (r.gate) b.gate = r.gate
  if (r.vehicleCount !== undefined) b.vehicleCount = r.vehicleCount
  else if (r.trips) b.vehicleCount = r.trips.length
  if (r.routeId != null) b.routeId = r.routeId
  if (r.intake) b.intake = r.intake as Booking['intake']
  if (r.medical) {
    const med = r.medical as Record<string, unknown>
    const medicalAt = num(med.at) ?? num(med.reviewedAt)
    b.medical = {
      ...med,
      ...(medicalAt !== undefined ? { at: medicalAt } : {}),
      status: med.status === 'resubmission_requested' ? 'resubmit' : med.status,
    } as MedicalReview
  }
  if (r.trips?.length) b.trips = r.trips.map(tripOf)
  if (r.plan) b.plan = r.plan as Booking['plan']
  // Lộ trình thiếu danh sách chặng thì coi như chưa có, để các trang đọc legs[0] không sập
  if (Array.isArray((r.route as Partial<RoutePlan> | undefined)?.legs)) b.route = r.route as RoutePlan
  if (r.quote) b.quote = r.quote as Quote
  const payment = paymentOf(r.payment)
  if (payment) b.payment = payment
  const balance = balanceOf(r.payment)
  const cancellation = cancellationOf(r.payment)
  if (cancellation) b.cancellation = cancellation
  if (balance) b.balance = balance
  b.canCancel = r.canCancel === true
  b.canPayBalance = r.canPayBalance === true
  if (r.cancelPenalty != null) b.cancelPenalty = r.cancelPenalty
  if (r.waybillNo) b.waybill = { no: r.waybillNo, issuedAt: waybillAt }
  if (r.clearance) {
    b.clearance = r.clearance as Clearance
  } else if (['waybill_issued', 'clearance_in_progress', 'clearance_done', 'ready_for_pickup'].includes(r.status) || r.waybillNo) {
    b.clearance = {
      items: [
        { type: 'health_cert' as const, status: 'todo' as const, note: '', photos: [] },
        ...(r.type === 'international' ? [
          { type: 'customs_declaration' as const, status: 'todo' as const, note: '', photos: [] },
          { type: 'import_permit' as const, status: 'todo' as const, note: '', photos: [] },
          { type: 'poa' as const, status: 'todo' as const, note: '', photos: [] },
        ] : []),
      ],
      horsesCleared: [],
    }
  }
  if (r.settlement) b.settlement = r.settlement as Settlement
  if (r.rating) b.rating = r.rating as Rating
  if (r.rejection) b.rejection = r.rejection as Rejection
  const incidents = (r.incidents ?? []).map(incidentOf).filter((incident): incident is Incident => !!incident)
  if (incidents.length) b.incidents = incidents
  return b
}
