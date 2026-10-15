// Service đơn đặt chuyến (Flow 1–6): chỉ gọi API Spring Boot, không giữ dữ liệu ở máy khách.
// Chức năng mà backend chưa có hoặc đang làm lệch quy trình (docs/PRD.md) không nối API: gọi vào sẽ báo "Backend chưa hỗ trợ".
import { CLEARANCE_DOC, type ClearanceDocType, type Payer } from '../config/booking-rules'
import { crewAssigned, finalizeQuote, quoteLines } from '../lib/booking'
import type { Adjustment, Booking, Incident, Rating, RoutePlan, StaffRef, VehicleTrip } from '../types/booking'
import { backendBookingApi } from '../api/bookings'
import { backendSettlementApi } from '../api/settlements'
import { specialistApi } from '../api/specialist'
import { toBooking } from './bookingMapper'
import { vehiclesApi, crewApi } from './fleet'

// Gọi vào là báo lỗi rõ chức năng nào backend chưa làm; giao diện hiện lỗi này ở thông báo.
const notSupported = (what: string) => async (..._args: unknown[]): Promise<never> => {
  throw new Error(`Backend chưa hỗ trợ: ${what}.`)
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


// Công khai: không cần đăng nhập. Backend chưa có API tra cứu công khai theo mã đơn và 4 số cuối SĐT.
export const publicBookingsApi = {
  track: notSupported('tra cứu đơn công khai theo mã đơn') as (code: string, phoneLast4: string) => Promise<PublicTracking | null>,
}

// Dành cho app khách
export const customerBookingsApi = {
  list: async (customer: string): Promise<CustomerBookingView[]> =>
    (await backendBookingApi.getCustomerBookings()).map(r => toCustomerView(toBooking(r, customer))).sort((a, b) => b.createdAt - a.createdAt),
  get: async (customer: string, id: string): Promise<CustomerBookingView | undefined> =>
    toCustomerView(toBooking(await backendBookingApi.getCustomerBookingById(id), customer)),
  create: async (customer: string, input: NewBookingInput): Promise<CustomerBookingView> => {
    // PRD mục 8.2: khách có đơn quá hạn thanh toán quyết toán thì chưa đặt được đơn mới (backend chưa kiểm)
    if ((await backendBookingApi.getCustomerBookings()).some(b => b.status === 'payment_overdue')) throw new Error('Bạn có đơn quá hạn thanh toán quyết toán nên chưa đặt được đơn mới. Vui lòng thanh toán trước.')
    const created = await backendBookingApi.create({
      type: input.type, originName: input.origin.name, originCountry: input.origin.country,
      destName: input.dest.name, destCountry: input.dest.country, departAt: input.departAt,
      consignor: { name: input.consignor.name, phone: input.consignor.phone },
      consignee: { name: input.consignee.name, phone: input.consignee.phone },
      horses: input.horses.map(h => ({ horseId: h.horseId, name: h.name, microchip: h.microchip, breed: h.breed, sex: h.sex, stall: h.stall, feedPackage: h.feedPackage, waterPlan: h.waterPlan, insuranceOpted: h.insurance?.opted })),
    })
    return toCustomerView(toBooking(created, customer))
  },
  // Xe, tài xế, hộ tống của từng chuyến (chỉ của đơn mình, sau khi đã có báo giá)
  team: async (customer: string, id: string): Promise<TripTeam[]> => {
    const b = toBooking(await backendBookingApi.getCustomerBookingById(id), customer)
    if (!b.trips || !SHOW_TEAM.includes(b.status)) return []
    const [vehicles, crew] = await Promise.all([vehiclesApi.list(), crewApi.list()])
    return b.trips.flatMap(t => {
      const v = vehicles.find(x => x.id === t.vehicleId)
      const driver = crew.find(x => x.id === t.driverId)
      const escort = crew.find(x => x.id === t.escortId)
      if (!v || !driver || !escort) return []
      return [{
        tripId: t.tripId, horseNames: b.horses.filter(h => t.horseIds.includes(h.horseId)).map(h => h.name),
        vehicle: { plate: v.plate, kind: v.name, stalls: v.capacity },
        driver: { name: driver.name, phone: driver.phone }, escort: { name: escort.name, phone: escort.phone },
      }]
    })
  },
  // Khách từ chối báo giá: đơn đóng (Cancelled), chưa cọc nên miễn phí
  rejectQuote: async (customer: string, id: string, reason: string): Promise<CustomerBookingView> =>
    toCustomerView(toBooking(await backendBookingApi.rejectQuote(id, { reason: reason.trim() }), customer)),
  // Backend hủy đơn theo chính sách hoàn 50% trước D-15, lệch PRD mục 8.3 (mất 100% cọc, hoàn số dư 70%)
  cancel: notSupported('khách hủy đơn đã cọc (chính sách hoàn tiền của backend lệch PRD mục 8.3)') as (customer: string, id: string, reason: string) => Promise<CustomerBookingView>,
  resubmit: async (customer: string, id: string): Promise<CustomerBookingView> =>
    toCustomerView(toBooking(await backendBookingApi.resubmitDocuments(id), customer)),
  payDeposit: async (customer: string, id: string): Promise<CustomerBookingView> =>
    toCustomerView(toBooking(await backendBookingApi.payDeposit(id, { paymentMethod: 'VNPAY' }), customer)),
  payBalance: async (customer: string, id: string): Promise<CustomerBookingView> =>
    toCustomerView(toBooking(await backendBookingApi.payBalance(id, { paymentMethod: 'VNPAY' }), customer)),
  settle: async (customer: string, id: string, rating: Omit<Rating, 'at'>): Promise<CustomerBookingView> => {
    if (![rating.trip, rating.driver, rating.escort].every(n => Number.isInteger(n) && n >= 1 && n <= 5)) throw new Error('Chấm điểm từ 1 đến 5 sao cho chuyến đi, tài xế và hộ tống.')
    const out = await backendSettlementApi.payAndComplete(id, { paymentMethod: 'VNPAY', rating: { trip: rating.trip, driver: rating.driver, escort: rating.escort, comment: rating.comment } })
    return toCustomerView(toBooking(out, customer))
  },
}

// Danh sách đơn theo vai trò đang đăng nhập.
async function listForRole() {
  const role = backofficeRole()
  if (role === 'coordinator') return backendBookingApi.getCoordinatorBookings()
  if (role === 'specialist') {
    const [verify, clearance] = await Promise.all([specialistApi.getVerificationBookings(), specialistApi.getClearanceBookings()])
    return [...verify, ...clearance.filter(c => !verify.some(v => v.id === c.id))]
  }
  if (role === 'manager' || role === 'admin') return backendBookingApi.getAllBookings()
  if (role === 'driver' || role === 'escort') return backendBookingApi.getFieldBookings()
  return []
}

const byId = async (id: string): Promise<Booking> => {
  const b = (await listForRole()).find(x => x.id === id)
  if (!b) throw new Error(`Không tìm thấy đơn ${id}.`)
  return toBooking(b)
}

// Dành cho app nội bộ
export const bookingsApi = {
  list: async (): Promise<Booking[]> => (await listForRole()).map(r => toBooking(r)).sort((a, b) => a.createdAt - b.createdAt),
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
  confirmPlan: notSupported('Điều phối viên chốt xe và lộ trình (backend khóa xe theo 1 đến 4 ngày, không kiểm đăng kiểm, lệch PRD mục 10.2)') as (id: string, by: string, input: { trips: Pick<VehicleTrip, 'vehicleId' | 'horseIds'>[]; route: Pick<RoutePlan, 'legs' | 'rests' | 'borderEta'>; gate?: string; note?: string }) => Promise<Booking>,
  sendBack: async (id: string, by: string, to: 'specialist' | 'coordinator', reason: string): Promise<Booking> =>
    toBooking(await backendBookingApi.sendBack(id, { by, to, reason: reason.trim() })),
  assignCrew: notSupported('Manager chọn tài xế và hộ tống (backend khóa người theo khung ngày, lệch PRD mục 10.2)') as (id: string, by: string, picks: { tripId: string; driverId: string; escortId: string }[]) => Promise<Booking>,
  quoteDraft: async (id: string) => {
    const b = await byId(id)
    if (!b.trips?.length) throw new Error('Đơn chưa có phương án xe.')
    const all = await vehiclesApi.list()
    const vs = b.trips.map(t => all.find(v => v.id === t.vehicleId)).filter((v): v is NonNullable<typeof v> => !!v)
    if (vs.length !== b.trips.length) throw new Error('Không tìm thấy xe của đơn.')
    return quoteLines(b, vs)
  },
  // Manager: duyệt và gửi báo giá, khách có 48 giờ để đặt cọc. Tiền do frontend tính theo biểu giá rồi gửi lên.
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

  // ===== Flow 3, 4: lệnh điều xe, check-in, nhật ký lộ trình =====
  setDriverPack: notSupported('Điều phối viên nhập bộ giấy cho Driver') as (id: string, tripId: string, by: string, items: string[]) => Promise<Booking>,
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

  // ===== Flow 5: sự cố khẩn cấp. Mô hình của backend (đội ngoài) khác PRD mục 6 =====
  reportIncident: notSupported('báo sự cố SOS (backend không trả sự cố theo xe)') as (id: string, tripId: string, by: string, kind: Incident['kind'], photo: string, note: string) => Promise<Booking>,
  planIncident: notSupported('Điều phối viên lập phương án sự cố trên bản đồ') as (id: string, incidentId: string, by: string, input: Record<string, unknown>) => Promise<Booking>,
  rejectIncident: notSupported('Manager trả lại phương án sự cố') as (id: string, incidentId: string, by: string, reason: string) => Promise<Booking>,
  approveIncident: notSupported('Manager duyệt phương án sự cố') as (id: string, incidentId: string, by: string) => Promise<Booking>,
  addIncidentExpense: notSupported('kê chi phí sự cố') as (id: string, incidentId: string, by: string, input: Record<string, unknown>) => Promise<Booking>,
  confirmFit: notSupported('Escort xác nhận ngựa đủ sức đi tiếp') as (id: string, incidentId: string, by: string) => Promise<Booking>,
  resumeJourney: notSupported('Driver tiếp tục hành trình sau sự cố') as (id: string, incidentId: string, by: string) => Promise<Booking>,

  // ===== Flow 6: quyết toán =====
  submitExpenses: async (id: string, _by: string): Promise<Booking> =>
    toBooking(await backendSettlementApi.submitExpenses(id, { note: 'Tài xế gửi bảng kê chi phí phát sinh', expenses: [] })),
  issueSettlement: async (id: string, _by: string, payers: Record<string, Payer> = {}): Promise<Booking> => {
    await backendSettlementApi.issue(id, { payers, note: 'Quản lý đối soát và phát hành quyết toán' })
    return byId(id)
  },
}
