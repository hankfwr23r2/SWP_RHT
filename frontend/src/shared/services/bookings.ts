// Service đơn đặt chuyến (Flow 1–6): kết nối trực tiếp API Spring Boot, không giữ dữ liệu ở máy khách.
import { CLEARANCE_DOC, INCIDENT_KIND, type ClearanceDocType, type IncidentKind, type Payer } from '../config/booking-rules'
import { crewAssigned, defaultPayer, finalizeQuote, incidentLocation, quoteLines, sameVehicleId } from '../lib/booking'
import type { Adjustment, Booking, Incident, IncidentExpense, IncidentPlan, Rating, RoutePlan, StaffRef, VehicleTrip } from '../types/booking'
import { backendBookingApi, type BackendBookingResponse } from '../api/bookings'
import { backendIncidentApi, type BackendIncidentResponse } from '../api/incidents'
import { backendSettlementApi } from '../api/settlements'
import { specialistApi } from '../api/specialist'
import apiClient, { type ApiResponse } from '../api/client'
import { paymentApi, type VnpayPaymentType } from '../api/payments'
import { incidentKindOf, incidentStatusOf, toBooking } from './bookingMapper'
import { vehiclesApi, crewApi } from './fleet'

function numericId(id: string, prefix: string): number {
  const match = new RegExp(`^(?:${prefix}-)?(\\d+)$`, 'i').exec(id.trim())
  if (!match) throw new Error(`Mã ${prefix.toLowerCase()} không hợp lệ: ${id}.`)
  return Number(match[1])
}

function starsOf(rating: Omit<Rating, 'at'>): number {
  const stars = rating.stars ?? rating.trip ?? 5
  if (!Number.isInteger(stars) || stars < 1 || stars > 5) throw new Error('Chấm điểm từ 1 đến 5 sao.')
  return stars
}

function backofficeRole(): string | null {
  if (typeof window === 'undefined') return null
  try {
    const session = sessionStorage.getItem('SWP_SESSION_backoffice')
    return session ? (JSON.parse(session) as { role?: string }).role ?? null : null
  } catch {
    return null
  }
}

// Người gửi / nhận gửi lên API: CCCD và địa chỉ để trống thì bỏ qua
const partyDto = (p: Booking['consignor']) => ({
  name: p.name, phone: p.phone,
  ...(p.idNumber.trim() ? { idNumber: p.idNumber.trim() } : {}),
  ...(p.address.trim() ? { address: p.address.trim() } : {}),
})

// Bỏ trường nội bộ trước khi đưa sang app khách; xe, lộ trình chỉ hiện khi báo giá đã gửi
const QUOTED: Booking['status'][] = ['awaiting_payment', 'quote_expired', 'waybill_issued', 'clearance_in_progress', 'clearance_done', 'ready_for_pickup', 'en_route_to_pickup', 'in_transit', 'incident_reported', 'pending_emergency_approval', 'emergency_plan_active', 'delivered_pending_settlement', 'expenses_submitted', 'settlement_issued', 'payment_overdue', 'completed']
export type CustomerTrip = Omit<VehicleTrip, 'driverPack' | 'acks'>
// Khách chỉ thấy diễn biến sự cố (nhóm, bước xử lý, ETA mới); chi phí chỉ hiện trong bảng quyết toán
export type CustomerIncident = Pick<Incident, 'id' | 'kind' | 'status' | 'reportedAt' | 'resolvedAt'> & { newEta?: number }
export type CustomerBookingView = Omit<Booking, 'intake' | 'history' | 'plan' | 'trips' | 'incidents'> & { trips?: CustomerTrip[]; incidents?: CustomerIncident[] }
// Báo giá hết hạn / đơn hủy: xe và nhân sự đã nhả, khách không còn thấy thông tin xe, tài xế
const SHOW_TEAM: Booking['status'][] = QUOTED.filter(s => s !== 'quote_expired')
const toCustomerView = (b: Booking): CustomerBookingView => {
  const { intake: _intake, history: _history, plan: _plan, trips, incidents, ...view } = structuredClone(b)
  const out: CustomerBookingView = view
  if (incidents?.length) out.incidents = incidents.map(i => ({ id: i.id, kind: i.kind, status: i.status, reportedAt: i.reportedAt, resolvedAt: i.resolvedAt, newEta: i.plan && i.status === 'active' ? i.plan.newEta : undefined }))
  if (SHOW_TEAM.includes(b.status)) out.trips = trips?.map(({ driverPack: _p, acks: _a, ...t }) => t)
  else delete out.route
  if (!SHOW_TEAM.includes(b.status)) delete out.route
  return out
}

function customerIncidentOf(response: BackendIncidentResponse): CustomerIncident | undefined {
  const kind = incidentKindOf(response.incidentType)
  if (!kind || response.createdAt === undefined) return undefined
  return {
    id: response.formattedId,
    kind,
    status: incidentStatusOf(response.status),
    reportedAt: response.createdAt,
    ...(response.resolvedAt !== undefined ? { resolvedAt: response.resolvedAt } : {}),
    ...(incidentStatusOf(response.status) === 'active' && response.newEta !== undefined
      ? { newEta: response.newEta }
      : {}),
  }
}

function toCustomerBooking(
  response: BackendBookingResponse,
  incidents: BackendIncidentResponse[],
  customer: string,
): CustomerBookingView {
  const booking = toCustomerView(toBooking(response, customer))
  const publicIncidents = incidents.map(customerIncidentOf)
    .filter((incident): incident is CustomerIncident => !!incident)
  if (publicIncidents.length) booking.incidents = publicIncidents
  return booking
}

// Thông tin từng xe của đơn cho khách: biển số, tài xế, hộ tống, ngựa trên xe. Chỉ của đơn mình, sau khi đã có báo giá.
export interface TripTeam {
  tripId: string
  horseNames: string[]
  vehicle: { plate: string; kind: string; stalls: number }
  driver: { name: string; phone: string }
  escort: { name: string; phone: string }
}

// Tra cứu công khai: mã đơn + 4 số cuối SĐT người gửi. Chỉ trả tiến trình, tuyến, ngày; không có tên, giá hay giấy tờ.
export interface PublicTracking {
  route: string
  depart: number
  step: number
  done?: boolean
  status: string
  tone?: 'warn' | 'done' | 'bad'
  now?: { place: string; at: number; eta: number }
  note?: string
}

export interface NewBookingInput {
  type: Booking['type']
  origin: Booking['origin']
  dest: Booking['dest']
  departAt: number
  consignor: Booking['consignor']
  consignee: Booking['consignee']
  horses: Booking['horses']
}


// Công khai: không cần đăng nhập. Tra cứu đơn theo mã đơn và 4 số cuối SĐT.
export const publicBookingsApi = {
  track: async (code: string, phoneLast4: string): Promise<PublicTracking | null> => {
    try {
      const res = await apiClient.get<ApiResponse<PublicTracking>>('/bookings/public-track', {
        params: { code: code.trim(), phoneLast4: phoneLast4.trim() },
      })
      return res.data.data ?? null
    } catch {
      return null
    }
  },
}

// Dành cho app khách
export const customerBookingsApi = {
  list: async (customer: string): Promise<CustomerBookingView[]> => {
    const bookings = await backendBookingApi.getCustomerBookings()
    const withIncidents = await Promise.all(bookings.map(async booking => ({
      ...booking,
      incidents: await backendIncidentApi.getByBooking(booking.numericId),
    })))
    return withIncidents.map(r => toCustomerBooking(r, r.incidents, customer)).sort((a, b) => b.createdAt - a.createdAt)
  },
  get: async (customer: string, id: string): Promise<CustomerBookingView | undefined> => {
    const booking = await backendBookingApi.getCustomerBookingById(id)
    const incidents = await backendIncidentApi.getByBooking(booking.numericId)
    return toCustomerBooking(booking, incidents, customer)
  },
  create: async (customer: string, input: NewBookingInput): Promise<CustomerBookingView> => {
    // PRD mục 8.2: khách có đơn quá hạn thanh toán quyết toán thì chưa đặt được đơn mới (backend chưa kiểm)
    if ((await backendBookingApi.getCustomerBookings()).some(b => b.status === 'payment_overdue')) throw new Error('Bạn có đơn quá hạn thanh toán quyết toán nên chưa đặt được đơn mới. Vui lòng thanh toán trước.')
    const created = await backendBookingApi.create({
      type: input.type, originName: input.origin.name, originCountry: input.origin.country,
      destName: input.dest.name, destCountry: input.dest.country, departAt: input.departAt,
      consignor: partyDto(input.consignor),
      consignee: partyDto(input.consignee),
      horses: input.horses.map(h => ({ horseId: h.horseId, name: h.name, microchip: h.microchip, breed: h.breed, sex: h.sex, stall: h.stall, feedPackage: h.feedPackage, waterPlan: h.waterPlan, insuranceOpted: h.insurance?.opted })),
    })
    return toCustomerView(toBooking(created, customer))
  },
  // Xe, tài xế, hộ tống của từng chuyến (chỉ của đơn mình, sau khi đã có báo giá)
  team: async (customer: string, id: string): Promise<TripTeam[]> => {
    try {
      const b = toBooking(await backendBookingApi.getCustomerBookingById(id), customer)
      if (!b.trips || !SHOW_TEAM.includes(b.status)) return []
      const [vehicles, crew] = await Promise.all([vehiclesApi.list(), crewApi.list()])
      return b.trips.flatMap(t => {
        const v = vehicles.find(x => sameVehicleId(x.id, t.vehicleId))
        const driver = crew.find(x => x.id === t.driverId)
        const escort = crew.find(x => x.id === t.escortId)
        if (!v || !driver || !escort) return []
        return [{
          tripId: t.tripId, horseNames: b.horses.filter(h => t.horseIds.includes(h.horseId)).map(h => h.name),
          vehicle: { plate: v.plate, kind: v.name, stalls: v.capacity },
          driver: { name: driver.name, phone: driver.phone }, escort: { name: escort.name, phone: escort.phone },
        }]
      })
    } catch {
      return []
    }
  },
  // Khách từ chối báo giá: đơn đóng (Cancelled), chưa cọc nên miễn phí
  rejectQuote: async (customer: string, id: string, reason: string): Promise<CustomerBookingView> =>
    toCustomerView(toBooking(await backendBookingApi.rejectQuote(id, { reason: reason.trim() }), customer)),
  // Khách hủy đơn sau khi đặt cọc: mất 100% tiền cọc, số dư 70% đã trả (nếu có) được hoàn trả
  cancel: async (customer: string, id: string, reason: string): Promise<CustomerBookingView> => {
    await paymentApi.cancelWithRefund(id, reason)
    return toCustomerView(toBooking(await backendBookingApi.getCustomerBookingById(id), customer))
  },
  resubmit: async (customer: string, id: string): Promise<CustomerBookingView> =>
    toCustomerView(toBooking(await backendBookingApi.resubmitDocuments(id), customer)),
  // Link sang cổng VNPAY cho cọc 30% / số dư 70%; đơn chỉ đổi trạng thái khi VNPAY xác nhận đã nhận tiền
  vnpayUrl: async (id: string, type: Exclude<VnpayPaymentType, 'SETTLEMENT'>): Promise<string> =>
    (await paymentApi.createVnpay(id, type)).paymentUrl,
  // Quyết toán có khoản phải trả: đánh giá gửi kèm, đơn chỉ đóng khi VNPAY xác nhận đã nhận tiền
  settlementVnpayUrl: async (id: string, rating: Omit<Rating, 'at'>): Promise<string> =>
    (await paymentApi.createVnpay(id, 'SETTLEMENT', { stars: starsOf(rating), comment: rating.comment })).paymentUrl,
  // Quyết toán 0 đồng: chỉ gửi đánh giá và đóng đơn
  settle: async (customer: string, id: string, rating: Omit<Rating, 'at'>): Promise<CustomerBookingView> => {
    const stars = starsOf(rating)
    const out = await backendSettlementApi.payAndComplete(id, {
      rating: {
        stars,
        comment: rating.comment,
        trip: stars,
        driver: stars,
        escort: stars,
      },
    })
    return toCustomerView(toBooking(out, customer))
  },
}

// Danh sách đơn theo vai trò đang đăng nhập.
async function listForRole() {
  const role = backofficeRole()
  if (role === 'driver' || role === 'escort') {
    const bookings = await backendBookingApi.myFieldBookings()
    const incidents = await Promise.all(bookings.map(booking => backendIncidentApi.getByBooking(booking.numericId)))
    return bookings.map((booking, index) => ({ ...booking, incidents: incidents[index] }))
  }
  if (role === 'coordinator') {
    const [bookings, incidents] = await Promise.all([backendBookingApi.getCoordinatorBookings(), backendIncidentApi.list()])
    return bookings.map(booking => ({
      ...booking,
      incidents: incidents.filter(incident => incident.bookingId === booking.numericId),
    }))
  }
  if (role === 'specialist') {
    const [verify, clearance] = await Promise.all([specialistApi.getVerificationBookings(), specialistApi.getClearanceBookings()])
    return [...verify, ...clearance.filter(c => !verify.some(v => v.id === c.id))]
  }
  if (role === 'manager' || role === 'admin') {
    const [bookings, incidents] = await Promise.all([backendBookingApi.getAllBookings(), backendIncidentApi.list()])
    return bookings.map(booking => ({
      ...booking,
      incidents: incidents.filter(incident => incident.bookingId === booking.numericId),
    }))
  }
  return []
}

const byId = async (id: string): Promise<Booking> => {
  const b = (await listForRole()).find(x => x.id === id)
  if (!b) throw new Error(`Không tìm thấy đơn ${id}.`)
  return toBooking(b)
}

// Dành cho app nội bộ
export const bookingsApi = {
  list: async (): Promise<Booking[]> => (await listForRole()).map(r => toBooking(r)).sort((a, b) => b.createdAt - a.createdAt),
  get: async (id: string): Promise<Booking | undefined> => (await bookingsApi.list()).find(b => b.id === id),

  // ===== Flow 1 =====
  activate: async (id: string, by: string, specialist: StaffRef, coordinator: StaffRef): Promise<Booking> =>
    toBooking(await backendBookingApi.activate(id, { by, specialist, coordinator })),
  rejectOrder: async (id: string, by: string, role: 'manager' | 'coordinator', reason: string): Promise<Booking> =>
    toBooking(await backendBookingApi.reject(id, { by, role, reason: reason.trim() })),
  approveMedical: async (id: string, by: string): Promise<Booking> =>
    toBooking(await specialistApi.approveMedical(id, { specialistName: by })),
  requestResubmission: async (id: string, by: string, reason: string, items: { horseId: string; doc: 'passport' | 'vaccine' | 'lab' }[]): Promise<Booking> =>
    toBooking(await specialistApi.requestResubmission(id, { specialistName: by, reason: reason.trim(), items })),
  confirmPlan: async (id: string, by: string, input: { trips: Pick<VehicleTrip, 'vehicleId' | 'horseIds'>[]; route: Pick<RoutePlan, 'legs' | 'rests' | 'borderEta'>; gate?: string; note?: string }): Promise<Booking> =>
    toBooking(await backendBookingApi.confirmFleetPlan(id, { by, trips: input.trips, route: input.route, gate: input.gate, note: input.note })),
  sendBack: async (id: string, by: string, to: 'specialist' | 'coordinator', reason: string): Promise<Booking> =>
    toBooking(await backendBookingApi.sendBack(id, { by, to, reason: reason.trim() })),
  assignCrew: async (id: string, by: string, picks: { tripId: string; driverId: string; escortId: string }[]): Promise<Booking> =>
    toBooking(await backendBookingApi.assignCrew(id, { by, picks })),
  quoteDraft: async (id: string) => {
    const b = await byId(id)
    if (!b.trips?.length) throw new Error('Đơn chưa có phương án xe.')
    const all = await vehiclesApi.list()
    const vs = b.trips.map(t => all.find(v => sameVehicleId(v.id, t.vehicleId))).filter((v): v is NonNullable<typeof v> => !!v)
    if (vs.length !== b.trips.length) throw new Error('Không tìm thấy xe của đơn.')
    return quoteLines(b, vs)
  },
  // Manager: duyệt và gửi báo giá, khách có QUOTE_VALID_HOURS (24) giờ để đặt cọc. Tiền do frontend tính theo biểu giá rồi gửi lên.
  sendQuote: async (id: string, by: string, adjustments: Adjustment[]): Promise<Booking> => {
    const b = await byId(id)
    if (b.status !== 'pending_commercial') throw new Error('Đơn không ở bước duyệt báo giá.')
    if (!(b.trips ?? []).every(crewAssigned)) throw new Error('Chọn tài xế và hộ tống cho tất cả các xe trước khi gửi báo giá.')
    const { lines } = await bookingsApi.quoteDraft(id)
    const adj = adjustments.filter(a => a.label.trim() && a.amount !== 0)
    const quote = finalizeQuote(lines, adj, Date.now(), by)
    return toBooking(await backendBookingApi.sendQuote(id, { by, adjustments: adj, lines, subtotal: quote.subtotal, total: quote.total, deposit: quote.deposit, balance: quote.balance }))
  },

  // ===== Flow 2: Specialist làm giấy tờ =====
  acceptWaybill: async (id: string, _by: string): Promise<Booking> => toBooking(await specialistApi.acceptWaybill(id)),
  updateClearanceItem: async (id: string, _by: string, type: ClearanceDocType, patch: { note?: string; photos?: string[] }): Promise<Booking> =>
    toBooking(await specialistApi.updateClearanceItem(id, { type, note: patch.note, photos: patch.photos, status: patch.photos?.length ? 'done' : 'todo' })),
  addClearanceItem: async (id: string, _by: string, type: ClearanceDocType): Promise<Booking> =>
    toBooking(await specialistApi.addClearanceItem(id, { type, label: CLEARANCE_DOC[type]?.short })),
  // Giấy thông quan áp dụng chung cho cả lô (PRD sau PR #28): hoàn tất giấy tờ thì mọi ngựa của đơn quốc tế coi như đã có giấy.
  // Backend vẫn đòi cờ từng ngựa nên đánh dấu đủ rồi mới hoàn tất.
  completeClearance: async (id: string, _by: string): Promise<Booking> => {
    const b = await byId(id)
    if (b.type === 'international') {
      for (const h of b.horses) if (!b.clearance?.horsesCleared.includes(h.horseId)) await specialistApi.markHorseCleared(id, h.horseId, true)
    }
    return toBooking(await specialistApi.completeClearance(id))
  },
  transferQuarantine: async (id: string): Promise<Booking> => toBooking(await specialistApi.transferQuarantine(id)),
  approveQuarantine: async (id: string, _by: string, note?: string): Promise<Booking> => toBooking(await specialistApi.approveQuarantine(id, note)),

  setDriverPack: async (id: string, tripId: string, by: string, items: string[]): Promise<Booking> =>
    toBooking(await backendBookingApi.updateTripProgress(id, { tripId, action: 'SET_DRIVER_PACK', actor: by, payload: { items } })),
  acknowledgeTrip: async (id: string, tripId: string, who: 'driver' | 'escort', by: string): Promise<Booking> =>
    toBooking(await backendBookingApi.updateTripProgress(id, { tripId, action: 'ACKNOWLEDGE', actor: by, payload: { who } })),
  departToPickup: async (id: string, tripId: string, by: string): Promise<Booking> =>
    toBooking(await backendBookingApi.updateTripProgress(id, { tripId, action: 'DEPART_TO_PICKUP', actor: by })),
  arriveAtPickup: async (id: string, tripId: string, by: string, photo: string): Promise<Booking> =>
    toBooking(await backendBookingApi.updateTripProgress(id, { tripId, action: 'ARRIVE_PICKUP', actor: by, payload: { photo } })),
  scanChip: async (id: string, tripId: string, by: string, chip: string): Promise<Booking> =>
    toBooking(await backendBookingApi.updateTripProgress(id, { tripId, action: 'SCAN_CHIP', actor: by, payload: { chip } })),
  collectOriginals: async (id: string, tripId: string, by: string, items: string[]): Promise<Booking> =>
    toBooking(await backendBookingApi.updateTripProgress(id, { tripId, action: 'COLLECT_ORIGINALS', actor: by, payload: { items } })),
  uploadHandover: async (id: string, tripId: string, by: string, photo: string): Promise<Booking> =>
    toBooking(await backendBookingApi.updateTripProgress(id, { tripId, action: 'UPLOAD_HANDOVER', actor: by, payload: { photo } })),
  startJourney: async (id: string, tripId: string, by: string): Promise<Booking> =>
    toBooking(await backendBookingApi.updateTripProgress(id, { tripId, action: 'START_JOURNEY', actor: by })),
  arriveCheckpoint: async (id: string, tripId: string, by: string, photo: string): Promise<Booking> =>
    toBooking(await backendBookingApi.updateTripProgress(id, { tripId, action: 'ARRIVE_CHECKPOINT', actor: by, payload: { photo } })),
  submitWelfare: async (id: string, tripId: string, by: string, data: { condition: string; waterLiters: number; hay: boolean; photo: string; note: string }): Promise<Booking> =>
    toBooking(await backendBookingApi.updateTripProgress(id, { tripId, action: 'SUBMIT_WELFARE', actor: by, payload: data })),
  continueJourney: async (id: string, tripId: string, by: string): Promise<Booking> =>
    toBooking(await backendBookingApi.updateTripProgress(id, { tripId, action: 'CONTINUE_JOURNEY', actor: by })),
  customsCleared: async (id: string, tripId: string, by: string, stampPhotos: string[]): Promise<Booking> =>
    toBooking(await backendBookingApi.updateTripProgress(id, { tripId, action: 'CUSTOMS_CLEARED', actor: by, payload: { stampPhotos } })),
  completeDelivery: async (id: string, tripId: string, by: string): Promise<Booking> =>
    toBooking(await backendBookingApi.updateTripProgress(id, { tripId, action: 'COMPLETE_DELIVERY', actor: by })),
  confirmHome: async (id: string, tripId: string, by: string, photo: string): Promise<Booking> =>
    toBooking(await backendBookingApi.updateTripProgress(id, { tripId, action: 'CONFIRM_HOME', actor: by, payload: { photo } })),

  // ===== Flow 5: sự cố khẩn cấp =====
  reportIncident: async (id: string, tripId: string, _by: string, kind: IncidentKind, photo: string, note: string, booking: Booking, trip: VehicleTrip) => {
    const location = incidentLocation(booking, trip)
    return backendIncidentApi.report({
      bookingId: numericId(id, 'BK'),
      tripId,
      incidentName: INCIDENT_KIND[kind].label,
      incidentType: kind.toUpperCase(),
      description: note.trim(),
      photoUrl: photo,
      latitude: location.lat,
      longitude: location.lng,
    })
  },
  planIncident: async (_id: string, incidentId: string, by: string, input: Omit<IncidentPlan, 'at' | 'by'>) => {
    const rescue = input.rescue
    return backendIncidentApi.proposeExternal(numericId(incidentId, 'INC'), {
      resolutionType: input.action === 'reroute' ? 'REROUTE' : 'EXTERNAL_PARTNER',
      description: input.note,
      plan: { ...input, at: Date.now(), by },
      partnerName: rescue?.name,
      partnerService: rescue ? 'Cứu hộ phương tiện' : input.station ? 'Đưa ngựa tới trạm nghỉ' : undefined,
      partnerPhone: rescue?.phone,
    })
  },
  rejectIncident: async (_id: string, incidentId: string, _by: string, reason: string) =>
    backendIncidentApi.rejectPlan(numericId(incidentId, 'INC'), reason.trim()),
  approveIncident: async (_id: string, incidentId: string, _by: string) =>
    backendIncidentApi.approvePlan(numericId(incidentId, 'INC'), {}),
  addIncidentExpense: async (_id: string, incidentId: string, _by: string, input: Pick<IncidentExpense, 'category' | 'label' | 'photo' | 'amount'>, incident: Incident) =>
    backendIncidentApi.addExpense(numericId(incidentId, 'INC'), {
      ...input,
      label: input.label.trim() || input.category,
      payer: defaultPayer(incident.kind, input.category),
    }),
  confirmFit: async (_id: string, incidentId: string, _by: string) =>
    backendIncidentApi.confirmFit(numericId(incidentId, 'INC')),
  resumeJourney: async (_id: string, incidentId: string, _by: string) =>
    backendIncidentApi.resumeJourney(numericId(incidentId, 'INC')),

  // ===== Flow 6: quyết toán =====
  submitExpenses: async (id: string, _by: string): Promise<Booking> => {
    const b = await byId(id).catch(() => null)
    const incidentsFromApi = await backendIncidentApi.getByBooking(numericId(id, 'BK')).catch(() => [])
    const incidents = incidentsFromApi.length ? incidentsFromApi : (b?.incidents ?? [])
    const expenses = incidents.flatMap(incident => (incident.expenses ?? []).map(expense => ({
      id: expense.id,
      category: expense.category,
      description: expense.label,
      photo: expense.photo,
      amount: expense.amount,
      payer: expense.payer === 'customer' ? 'customer' as const : 'carrier' as const,
    })))
    return toBooking(await backendSettlementApi.submitExpenses(id, { expenses }))
  },
  issueSettlement: async (id: string, by: string, payers: Record<string, Payer> = {}): Promise<Booking> => {
    const booking = await byId(id)
    const items = (booking.incidents ?? []).flatMap(incident => incident.expenses.map(expense => ({
      id: expense.id,
      category: expense.category,
      description: expense.label,
      photo: expense.photo,
      amount: expense.amount,
      payer: payers[expense.id] ?? expense.payer,
    })))
    const payerMap: Record<string, 'customer' | 'carrier'> = {}
    Object.entries(payers).forEach(([k, v]) => {
      payerMap[k] = v === 'customer' ? 'customer' : 'carrier'
    })
    await backendSettlementApi.issue(id, {
      managerName: by,
      items,
      payers: payerMap,
      note: 'Quản lý đối soát và phát hành quyết toán',
    })
    return byId(id)
  },
}
