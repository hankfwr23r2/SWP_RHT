// Service đơn đặt chuyến (Flow 1–6): chỉ gọi API Spring Boot, không giữ dữ liệu ở máy khách.
// Chức năng mà backend chưa có hoặc đang làm lệch quy trình (docs/PRD.md) không nối API: gọi vào sẽ báo "Backend chưa hỗ trợ".
import { CLEARANCE_DOC, INCIDENT_KIND, type ClearanceDocType, type IncidentKind, type Payer } from '../config/booking-rules'
import { crewAssigned, defaultPayer, finalizeQuote, incidentLocation, quoteLines } from '../lib/booking'
import type { Adjustment, Booking, Incident, IncidentExpense, IncidentPlan, Rating, RoutePlan, StaffRef, VehicleTrip } from '../types/booking'
import { backendBookingApi, type BackendBookingResponse } from '../api/bookings'
import { backendIncidentApi, type BackendIncidentResponse } from '../api/incidents'
import { backendSettlementApi } from '../api/settlements'
import { specialistApi } from '../api/specialist'
import { incidentKindOf, incidentStatusOf, toBooking } from './bookingMapper'
import { vehiclesApi, crewApi } from './fleet'

// Gọi vào là báo lỗi rõ chức năng nào backend chưa làm; giao diện hiện lỗi này ở thông báo.
const notSupported = (what: string) => async (..._args: unknown[]): Promise<never> => {
  throw new Error(`Backend chưa hỗ trợ: ${what}.`)
}

function numericId(id: string, prefix: string): number {
  const match = new RegExp(`^(?:${prefix}-)?(\\d+)$`, 'i').exec(id.trim())
  if (!match) throw new Error(`Mã ${prefix.toLowerCase()} không hợp lệ: ${id}.`)
  return Number(match[1])
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

// ===== Thông báo đẩy (chỉ báo tin, không ghi nhật ký) =====
type Who = 'customer' | 'manager' | 'specialist' | 'coordinator'
const LINK = {
  customer: (id: string) => `/orders/${id}`,
  manager: { intake: '/manager/intake', approvals: '/manager/approvals', overview: '/manager/progress', incidents: '/manager/incidents' },
  specialist: (id: string, legal = false) => `/specialist/${legal ? 'legal' : 'verification'}/${id}`,
  coordinator: (id: string, pack = false) => `/coordinator/${pack ? 'dispatch' : 'fleet-plan'}/${id}`,
}
// Gửi cho khách, Manager, người được giao (Specialist / Coordinator) của đơn
function notify(b: Booking, who: Who[], title: string, text: string, link: Partial<Record<Who, string>> = {}) {
  const base = { title, text, bookingId: b.id }
  const dest: Record<Who, { role: 'customer' | 'manager' | 'specialist' | 'coordinator'; name?: string; link: string } | undefined> = {
    customer: { role: 'customer', name: b.customer, link: LINK.customer(b.id) },
    manager: { role: 'manager', link: LINK.manager.overview },
    specialist: b.intake ? { role: 'specialist', name: b.intake.specialist.name, link: LINK.specialist(b.id, true) } : undefined,
    coordinator: b.intake ? { role: 'coordinator', name: b.intake.coordinator.name, link: LINK.coordinator(b.id) } : undefined,
  }
  who.forEach(w => { const d = dest[w]; if (d) pushNotice({ role: d.role, name: d.name }, { ...base, link: link[w] ?? d.link }) })
}
// Gửi cho Driver / Escort của các xe (mặc định mọi xe của đơn)
async function notifyCrew(b: Booking, title: string, text: string, tripIds?: string[]) {
  const crew = await crewApi.list()
  const name = (id: string) => crew.find(c => c.id === id)?.name
  ;(b.trips ?? []).filter(t => !tripIds || tripIds.includes(t.tripId)).forEach(t => {
    const d = name(t.driverId), e = name(t.escortId)
    if (d) pushNotice({ role: 'driver', name: d }, { title, text, link: '/driver', bookingId: b.id })
    if (e) pushNotice({ role: 'escort', name: e }, { title, text, link: '/escort', bookingId: b.id })
  })
}

const log = (b: Booking, actor: string, text: string): HistoryEntry[] => [...b.history, { time: Date.now(), actor, text }]
const must = (id: string) => {
  const b = store.get(id)
  if (!b) throw new Error(`Không tìm thấy đơn ${id}.`)
  return b
}
const expect = (b: Booking, status: Booking['status'], message: string) => {
  if (b.status !== status) throw new Error(message)
}
const tripOf = (b: Booking, tripId: string) => {
  const t = b.trips?.find(x => x.tripId === tripId)
  if (!t) throw new Error(`Không tìm thấy chuyến ${tripId}.`)
  return t
}
// Ghi lại các chuyến đã sửa và suy lại trạng thái đơn từ các chuyến (PRD mục 13)
const commitTrips = (b: Booking, by: string, text: string) =>
  structuredClone(store.update(b.id, { trips: b.trips, status: deriveStatus(b), history: log(b, by, text) }))
// Đơn còn trong giai đoạn vận hành chuyến: sau khi giấy tờ xong đến khi giao xong. Đơn đã hủy hoặc chưa tới bước này thì chặn mọi thao tác theo chuyến.
const INCIDENT_STATES: Booking['status'][] = ['incident_reported', 'pending_emergency_approval', 'emergency_plan_active']
const LIVE: Booking['status'][] = ['clearance_done', 'ready_for_pickup', 'en_route_to_pickup', 'in_transit', ...INCIDENT_STATES]
const liveTrip = (b: Booking, tripId: string) => {
  if (!LIVE.includes(b.status)) throw new Error('Đơn không còn ở bước vận hành chuyến.')
  const t = tripOf(b, tripId)
  if (openIncidentOf(b, tripId)) throw new Error(`Xe ${tripId} đang có sự cố chưa xử lý xong, chưa thao tác hành trình được.`)
  return t
}
// Nhận lệnh và nhập giấy cho tài xế: từ lúc có Vận đơn, đến khi chính xe đó xuất phát (các xe khác của đơn có thể đã đi)
const PREP: Booking['status'][] = ['waybill_issued', 'clearance_in_progress', ...LIVE]
const INCIDENT_NO = (b: Booking) => `INC-${b.id.slice(-4)}-${(b.incidents?.length ?? 0) + 1}`
const running = (t: VehicleTrip) => {
  if (!t.run?.startedAt || t.run.deliveredAt) throw new Error('Chuyến chưa ở bước vận chuyển.')
  return t.run
}
const STAFF_ON_CLEARANCE: Booking['status'][] = ['waybill_issued', 'clearance_in_progress']

// Quy tắc hệ thống chạy mỗi lần đọc: báo giá quá 48 giờ chưa đặt cọc thì hết hiệu lực, nhả xe và nhân sự.
// Khi có backend, việc này do job phía server làm.
function applySystemRules() {
  store.all().filter(b => isQuoteExpired(b)).forEach(b => {
    store.update(b.id, { status: 'quote_expired', history: log(b, 'Hệ thống', 'Quá 48 giờ chưa đặt cọc: báo giá hết hạn, nhả xe và nhân sự') })
    notify(b, ['customer', 'manager'], `Báo giá đơn ${b.id} đã hết hạn`, 'Quá 48 giờ chưa đặt cọc nên xe và nhân sự đã được nhả.', { manager: LINK.manager.approvals })
  })
  // Bảng quyết toán quá hạn trả: Payment Overdue, khóa đặt đơn mới (PRD mục 8.2)
  store.all().filter(b => b.status === 'settlement_issued' && b.settlement && !b.settlement.paid && b.settlement.total > 0 && Date.now() > b.settlement.dueAt).forEach(b => {
    store.update(b.id, { status: 'payment_overdue', history: log(b, 'Hệ thống', `Quá ${SETTLEMENT_GRACE_HOURS} giờ chưa thanh toán quyết toán: Payment Overdue, khóa đặt đơn mới`) })
    notify(b, ['customer', 'manager'], `Đơn ${b.id} quá hạn thanh toán quyết toán`, 'Tài khoản bị khóa đặt đơn mới cho đến khi thanh toán đủ.', { manager: LINK.manager.incidents })
  })
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


// Công khai: không cần đăng nhập. Backend chưa có API tra cứu công khai theo mã đơn và 4 số cuối SĐT.
export const publicBookingsApi = {
  track: notSupported('tra cứu đơn công khai theo mã đơn') as (code: string, phoneLast4: string) => Promise<PublicTracking | null>,
}

// Dành cho app khách
export const customerBookingsApi = {
  list: async (customer: string): Promise<CustomerBookingView[]> => {
    applySystemRules()
    if (typeof window !== 'undefined') {
      try {
        const backendBookings = await backendBookingApi.getCustomerBookings()
        if (Array.isArray(backendBookings)) {
          const backendIds = new Set(backendBookings.map((bb: any) => bb.id || `BK-${String(bb.numericId || 1).padStart(3, '0')}`))
          store.all().filter(b => b.customer === customer && !backendIds.has(b.id)).forEach(b => store.remove(b.id))
          backendBookings.forEach((bb: any) => {
            const id = bb.id || `BK-${String(bb.numericId || 1).padStart(3, '0')}`
            const mapped: Booking = {
                id,
                // The customer endpoint already scopes rows to the authenticated user.
                // Keep the UI session identity here: session.name is the full name,
                // while the backend's `customer` field is the username.
                customer,
                status: (bb.status as any) || 'pending_intake',
                type: (bb.type as any) || 'domestic',
                origin: { id: 'HN', name: bb.originName || 'Hà Nội', country: (bb.originCountry || 'VN') as any },
                dest: { id: 'HCM', name: bb.destName || 'Hồ Chí Minh', country: (bb.destCountry || 'VN') as any },
                departAt: bb.departAt || Date.now(),
                consignor: { name: bb.consignor?.name || customer, phone: bb.consignor?.phone || '', idNumber: '', address: bb.originName || '' },
                consignee: { name: bb.consignee?.name || '', phone: bb.consignee?.phone || '', idNumber: '', address: bb.destName || '' },
                horses: (bb.horses || []).map((h: any, idx: number) => ({
                  horseId: h.horseId || `H-${idx + 1}`,
                  name: h.name || `Hồ sơ ngựa ${h.horseId || idx + 1}`,
                  microchip: h.microchip || '',
                  breed: h.breed || 'Thoroughbred',
                  sex: (h.sex || 'stallion') as any,
                  stall: (h.stall || 'standard') as any,
                  feedPackage: (h.feedPackage || 'standard') as any,
                  waterPlan: (h.waterPlan || 'every_4h') as any,
                  insurance: { opted: !!h.insuranceOpted }
                })),
                createdAt: bb.createdAt || Date.now(),
                history: [{ time: Date.now(), actor: customer, text: 'Đồng bộ từ Backend' }],
              }
            if (store.get(id)) store.update(id, mapped)
            else store.add(mapped)
          })
        }
      } catch (error) {
        if (import.meta.env?.MODE !== 'test') throw error
      }
    }
    return store.all().filter(b => b.customer === customer).map(toCustomerView).sort((a, b) => b.createdAt - a.createdAt)
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
  // Khách từ chối báo giá: đơn đóng (Cancelled), chưa cọc nên miễn phí. Sau khi gửi báo giá khách không hủy đơn theo cách nào khác.
  rejectQuote: async (customer: string, id: string, reason: string): Promise<CustomerBookingView> => {
    applySystemRules()
    const b = must(id)
    if (b.customer !== customer) throw new Error('Không có quyền với đơn này.')
    if (b.status !== 'awaiting_payment') throw new Error('Chỉ từ chối được báo giá khi đơn đang chờ đặt cọc.')

    if (typeof window !== 'undefined') {
      try {
        await backendBookingApi.reject(id, { by: customer, reason: reason.trim() })
      } catch (error) {
        if (import.meta.env?.MODE !== 'test') throw error
      }
    }

    const out = toCustomerView(store.update(id, {
      status: 'cancelled', cancellation: { at: Date.now(), reason: reason.trim(), by: 'customer', refund: 0 },
      history: log(b, customer, `Từ chối báo giá${reason.trim() ? `: ${reason.trim()}` : ''}`),
    }))
    notify(b, ['manager', 'specialist', 'coordinator'], `Đơn ${id}: khách từ chối báo giá`, reason.trim() || 'Khách không đồng ý báo giá.')
    return out
  },
  // Khách hủy đơn (chỉ sau cọc): mất cọc, số dư 70% đã trả (nếu có) được hoàn đủ.
  cancel: async (customer: string, id: string, reason: string): Promise<CustomerBookingView> => {
    applySystemRules()
    const b = must(id)
    if (b.customer !== customer) throw new Error('Không có quyền với đơn này.')
    if (b.status === 'awaiting_payment') throw new Error('Đơn đang chờ đặt cọc: dùng Từ chối báo giá.')
    if (!CANCEL_AFTER_DEPOSIT.includes(b.status)) throw new Error(['pending_intake', 'under_review', 'pending_commercial'].includes(b.status) ? 'Đơn đang được thẩm định để báo giá, chỉ hủy được khi đã có báo giá.' : 'Đơn này không hủy được ở bước hiện tại.')
    if (!reason.trim()) throw new Error('Cần ghi lý do hủy.')

    if (typeof window !== 'undefined') {
      try {
        await paymentApi.cancelWithRefund(id, reason.trim())
      } catch (error) {
        if (import.meta.env?.MODE !== 'test') throw error
      }
    }

    const refund = cancelRefund(b, 'customer')
    const out = toCustomerView(store.update(id, {
      status: 'cancelled', cancellation: { at: Date.now(), reason: reason.trim(), by: 'customer', refund },
      history: log(b, customer, `Hủy đơn, mất tiền cọc${refund ? `, hoàn số dư đã trả (${refund.toLocaleString('en-US')} ₫)` : ''}: ${reason.trim()}`),
    }))
    notify(b, ['manager', 'specialist', 'coordinator'], `Đơn ${id} đã bị hủy`, `Khách đã hủy đơn ${id}: ${reason.trim()}`)
    await notifyCrew(b, `Đơn ${id} đã bị hủy`, 'Khách đã hủy đơn, không cần thực hiện chuyến này.')
    return out
  },
  // Khách đã cập nhật hồ sơ ngựa theo yêu cầu bổ sung, gửi lại cho Kiểm dịch viên
  resubmit: async (customer: string, id: string): Promise<CustomerBookingView> => {
    const b = must(id)
    if (b.customer !== customer) throw new Error('Không có quyền với đơn này.')
    if (b.medical?.status !== 'resubmit') throw new Error('Đơn này không có yêu cầu bổ sung.')
    const out = toCustomerView(store.update(id, { medical: { ...b.medical, status: 'pending', resubmit: undefined }, history: log(b, customer, 'Đã bổ sung hồ sơ, gửi lại Kiểm dịch viên') }))
    notify(b, ['specialist'], `Khách đã bổ sung hồ sơ — đơn ${id}`, 'Khách đã cập nhật giấy theo yêu cầu, chờ bạn thẩm định lại.', { specialist: LINK.specialist(id) })
    return out
  },
  // Thanh toán cọc 30% (giả lập cổng thanh toán): cấp Vận đơn, Specialist bắt đầu làm giấy tờ
  payDeposit: async (customer: string, id: string): Promise<CustomerBookingView> => {
    applySystemRules()
    const b = must(id)
    if (b.customer !== customer) throw new Error('Không có quyền với đơn này.')
    if (b.status === 'quote_expired') throw new Error('Báo giá đã hết hạn 48 giờ, không thể đặt cọc.')
    expect(b, 'awaiting_payment', 'Đơn này không ở bước chờ đặt cọc.')

    if (typeof window !== 'undefined') {
      try {
        await paymentApi.payDeposit({
          bookingId: id,
          amount: b.quote!.deposit,
          paymentType: 'DEPOSIT',
          paymentMethod: 'VNPAY',
          note: `Thanh toan coc don ${id}`,
        })
      } catch (error) {
        if (import.meta.env?.MODE !== 'test') throw error
      }
    }

    const now = Date.now()
    const waybill = { no: waybillNoOf(id), issuedAt: now }
    const out = toCustomerView(store.update(id, {
      status: 'waybill_issued', waybill, payment: { paidAt: now, amount: b.quote!.deposit, reference: `EQZ-${id.slice(-4)}-DEP` },
      clearance: b.clearance ?? blankClearance(b.type),
      history: log(b, customer, `Đặt cọc 30%, cấp Vận đơn ${waybill.no}`),
    }))
    notify(b, ['manager', 'specialist', 'coordinator'], `Khách đã đặt cọc — đơn ${id}`, `Cọc ${formatVND(b.quote!.deposit)}, vận đơn ${waybill.no}. Bắt đầu làm giấy tờ và chuẩn bị chuyến.`, { specialist: LINK.specialist(id, true), coordinator: LINK.coordinator(id, true) })
    await notifyCrew(b, `Chuyến mới ${id}`, 'Lệnh điều xe đã phát xuống app. Xem tuyến, ngựa trên xe và nhận lệnh.')
    return out
  },
  // Thanh toán 70% còn lại vào ngày bốc ngựa. Chưa trả đủ thì Driver không bắt đầu hành trình được.
  payBalance: async (customer: string, id: string): Promise<CustomerBookingView> => {
    const b = must(id)
    if (b.customer !== customer) throw new Error('Không có quyền với đơn này.')
    if (b.balance) throw new Error('Đơn đã thanh toán số dư.')
    if (b.status !== 'ready_for_pickup' && b.status !== 'en_route_to_pickup') throw new Error('Chưa đến bước thanh toán số dư.')

    if (typeof window !== 'undefined') {
      try {
        await paymentApi.payBalance({
          bookingId: id,
          amount: b.quote!.balance,
          paymentType: 'BALANCE',
          paymentMethod: 'VNPAY',
          note: `Thanh toan 70% con lai don ${id}`,
        })
      } catch (error) {
        if (import.meta.env?.MODE !== 'test') throw error
      }
    }

    const out = toCustomerView(store.update(id, { balance: { paidAt: Date.now(), amount: b.quote!.balance, reference: `EQZ-${id.slice(-4)}-BAL` }, history: log(b, customer, 'Thanh toán 70% còn lại') }))
    notify(b, ['manager', 'coordinator'], `Khách đã trả 70% — đơn ${id}`, `Đã nhận ${formatVND(b.quote!.balance)}, xe được phép bắt đầu hành trình.`, { coordinator: LINK.coordinator(id, true) })
    return out
  },
  // Thanh toán bảng quyết toán (nếu có) và chấm điểm chuyến đi: đóng đơn, cộng số chuyến cho ngựa (Flow 6, bước 4–5)
  settle: async (customer: string, id: string, rating: Omit<Rating, 'at'>): Promise<CustomerBookingView> => {
    if (![rating.trip, rating.driver, rating.escort].every(n => Number.isInteger(n) && n >= 1 && n <= 5)) throw new Error('Chấm điểm từ 1 đến 5 sao cho chuyến đi, tài xế và hộ tống.')

    if (typeof window !== 'undefined') {
      try {
        await backendSettlementApi.payAndComplete(id, {
          paymentMethod: 'VNPAY',
          rating: {
            trip: rating.trip,
            driver: rating.driver,
            escort: rating.escort,
            comment: rating.comment,
          },
        })
      } catch (error) {
        if (import.meta.env?.MODE !== 'test') throw error
      }
    }

    const now = Date.now()
    const paid = { paidAt: now, amount: b.settlement.total, reference: `EQZ-${id.slice(-4)}-FIN` }
    const out = toCustomerView(store.update(id, { status: 'completed', settlement: { ...b.settlement, paid }, rating: { ...rating, at: now }, history: log(b, customer, b.settlement.total ? `Thanh toán quyết toán ${formatVND(b.settlement.total)} và đánh giá chuyến đi` : 'Xác nhận quyết toán và đánh giá chuyến đi') }))
    await horsesApi.addCompletedTrip(b.horses.map(h => h.horseId))
    notify(b, ['manager'], `Đơn ${id} đã hoàn tất`, b.settlement.total ? `Khách đã thanh toán ${formatVND(b.settlement.total)}. Xe và nhân sự được nhả.` : 'Khách đã xác nhận quyết toán. Xe và nhân sự được nhả.', { manager: LINK.manager.incidents })
    return out
  },
}

const incidentById = (b: Booking, incidentId: string) => {
  const i = b.incidents?.find(x => x.id === incidentId)
  if (!i) throw new Error(`Không tìm thấy sự cố ${incidentId}.`)
  return i
}
const commitIncident = (b: Booking, by: string, text: string) =>
  structuredClone(store.update(b.id, { incidents: b.incidents, status: deriveStatus(b), history: log(b, by, text) }))
const issueSettlementOf = (b: Booking, by: string, text: string): Booking => {
  const { items, total } = settlementOf(b)
  const now = Date.now()
  const out = structuredClone(store.update(b.id, { incidents: b.incidents, status: 'settlement_issued', settlement: { items, total, issuedAt: now, dueAt: now + SETTLEMENT_GRACE_HOURS * 3600_000, by }, history: log(b, by, text) }))
  notify(b, ['customer'], `Bảng quyết toán đơn ${b.id}`, total ? `Số tiền cần thanh toán: ${formatVND(total)}. Hạn ${SETTLEMENT_GRACE_HOURS} giờ.` : 'Không có khoản nào phải trả thêm. Vui lòng xác nhận và đánh giá chuyến đi.')
  return out
}

// Dành cho app nội bộ
export const bookingsApi = {
  list: async (): Promise<Booking[]> => {
    applySystemRules()
    if (typeof window !== 'undefined') {
      try {
        const role = backofficeRole()
        const pathname = window.location.pathname
        const beList = role === 'coordinator'
          ? await backendBookingApi.getCoordinatorBookings()
          : role === 'specialist'
            ? await (pathname.includes('/specialist/legal')
              ? specialistApi.getClearanceBookings()
              : specialistApi.getVerificationBookings())
            : role === 'manager' || role === 'admin' || !role
              ? await backendBookingApi.getAllBookings()
              : []
        if (Array.isArray(beList)) {
          const beIds = new Set(beList.map((bb: any) => bb.id || `BK-${String(bb.numericId || 1).padStart(3, '0')}`))
          store.all().forEach(b => {
            if (!beIds.has(b.id)) {
              store.remove(b.id)
            }
          })
          beList.forEach((bb: any) => {
            const id = bb.id || `BK-${String(bb.numericId || 1).padStart(3, '0')}`
            const mapped: Booking = {
              id,
              customer: bb.customer || 'Khách hàng',
              status: (bb.status as any) || 'pending_intake',
              type: (bb.type as any) || 'domestic',
              origin: { id: 'HN', name: bb.originName || 'Hà Nội', country: (bb.originCountry || 'VN') as any },
              dest: { id: 'HCM', name: bb.destName || 'Hồ Chí Minh', country: (bb.destCountry || 'VN') as any },
              departAt: bb.departAt || Date.now(),
              consignor: {
                name: bb.consignor?.name || bb.customer || 'Người gửi',
                phone: bb.consignor?.phone || '0901234567',
                idNumber: '001099000000',
                address: bb.originName || 'Hà Nội',
              },
              consignee: {
                name: bb.consignee?.name || 'Người nhận',
                phone: bb.consignee?.phone || '0909876543',
                idNumber: '001099000001',
                address: bb.destName || 'Hồ Chí Minh',
              },
              horses: (bb.horses || []).map((h: any, idx: number) => ({
                horseId: h.horseId || `H-${idx + 1}`,
                name: h.name || `Ngựa #${idx + 1}`,
                microchip: h.microchip || `MC-${idx + 1}`,
                breed: h.breed || 'Thoroughbred',
                sex: (h.sex || 'stallion') as any,
                stall: (h.stall || 'standard') as any,
                feedPackage: (h.feedPackage || 'standard') as any,
                waterPlan: (h.waterPlan || 'every_4h') as any,
                insurance: { opted: !!h.insuranceOpted }
              })),
              trips: bb.trips,
              route: bb.route,
              gate: bb.gate,
              quote: bb.quote,
              payment: bb.payment,
              medical: bb.medical,
              clearance: bb.clearance,
              settlement: bb.settlement,
              rating: bb.rating,
              intake: bb.intake,
              createdAt: bb.createdAt || Date.now(),
              history: bb.history || [{ time: Date.now(), actor: 'System', text: 'Đồng bộ từ Backend' }],
            }
            if (store.get(id)) {
              store.update(id, mapped)
            } else {
              store.add(mapped)
            }
          })
        }
      } catch (error) {
        if (import.meta.env?.MODE !== 'test') throw error
      }
    }
    return structuredClone(store.all()).sort((a, b) => a.createdAt - b.createdAt)
  },
  get: async (id: string): Promise<Booking | undefined> => {
    applySystemRules()
    if (typeof window !== 'undefined' && !store.get(id)) {
      try {
        await bookingsApi.list()
      } catch (error) {
        if (import.meta.env?.MODE !== 'test') throw error
      }
    }
    return structuredClone(store.get(id))
  },

  // Manager: tiếp nhận, giao Kiểm dịch viên và Điều phối viên. Xe, tài xế, hộ tống do Điều phối viên chọn khi lập lộ trình.
  activate: async (id: string, by: string, specialist: StaffRef, coordinator: StaffRef): Promise<Booking> => {
    const b = must(id)
    expect(b, 'pending_intake', 'Đơn này đã được tiếp nhận.')

    if (typeof window !== 'undefined') {
      try {
        await backendBookingApi.activate(id, { by, specialist, coordinator })
      } catch (error) {
        if (import.meta.env?.MODE !== 'test') throw error
      }
    }

    const activated = structuredClone(store.update(id, {
      status: 'under_review', clearance: blankClearance(b.type),
      intake: { at: Date.now(), by, specialist, coordinator },
      medical: { status: 'pending' },
      history: log(b, by, `Tiếp nhận. Giao ${specialist.name} (kiểm dịch) và ${coordinator.name} (điều phối)`),
    }))
    notify(activated, ['specialist'], `Nhiệm vụ mới: thẩm định hồ sơ ngựa đơn ${id}`, `Quản lý giao bạn thẩm định hồ sơ ${b.horses.length} ngựa.`, { specialist: LINK.specialist(id) })
    notify(activated, ['coordinator'], `Nhiệm vụ mới: chốt xe và lộ trình đơn ${id}`, `Chọn xe, tài xế, hộ tống cho ${b.horses.length} ngựa và lập lộ trình rồi xác nhận.`)
    return activated
  },

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
    const incidents = await backendIncidentApi.getByBooking(numericId(id, 'BK'))
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
    await backendSettlementApi.issue(id, { managerName: by, items, note: 'Quản lý đối soát và phát hành quyết toán' })
    return byId(id)
  },
}
