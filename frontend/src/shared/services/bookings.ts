// Service đơn đặt chuyến (Flow 1–4). Tên hàm theo REST để sau này thay bằng API Spring Boot.
// Mỗi hàm là một thao tác nghiệp vụ; trạng thái chỉ đổi qua các hàm này để giữ đúng cổng chuyển bước của PRD.
import { BOOKING_STATUS, CLEARANCE_DOC, EXPENSE_CATEGORY, INCIDENT_ACTION, INCIDENT_KIND, MIN_REST_MINUTES, SETTLEMENT_GRACE_HOURS, publicStepOf, type ClearanceDocType, type ExpenseCategory, type IncidentAction, type IncidentKind, type Payer, type WelfareCondition } from '../config/booking-rules'
import { CANCEL_AFTER_DEPOSIT, cancelRefund, crewAssigned, openIncidentOf, defaultPayer, incidentActionsFor, incidentLocation, pendingDeparture, needsFitCheck, settlementOf, blankClearance, buildCheckpoints, busyResources, canCompleteClearance, vehicleDocsOk, currentCheckpoint, gatesFor, deriveStatus, finalizeQuote, isQuoteExpired, manifestDocuments, nextBookingId, quoteLines, reviewDone, tripIdFor, validateRoutePlan, waybillNoOf } from '../lib/booking'
import { TRANSIT_STATIONS } from '../config/network'
import type { Adjustment, Booking, Incident, IncidentPlan, Rating, ClearanceItem, HistoryEntry, MedicalReview, RoutePlan, SentBack, StaffRef, TripRun, VehicleTrip, WelfareLog } from '../types/booking'
import { formatVND } from '../lib/format'
import { crewApi, vehiclesApi } from './fleet'
import { horsesApi } from './horses'
import { pushNotice } from './notices'
import { seedBookings } from './mock/bookings'
import { createStore } from './store'
import { backendBookingApi, type BackendBookingResponse } from '../api/bookings'

const store = createStore<Booking>('bookings', seedBookings)

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

export function backendToBooking(b: BackendBookingResponse): Booking {
  return {
    id: b.id,
    type: (b.type === 'international' ? 'international' : 'domestic') as any,
    origin: {
      id: b.originName || 'VN-ORIGIN',
      name: b.originName || 'Điểm đón',
      country: ((b.originCountry as any) || 'VN'),
    },
    dest: {
      id: b.destName || 'VN-DEST',
      name: b.destName || 'Điểm đến',
      country: ((b.destCountry as any) || 'VN'),
    },
    departAt: b.departAt ?? Date.now(),
    consignor: {
      name: b.consignor?.name ?? '',
      phone: b.consignor?.phone ?? '',
      idNumber: '',
      address: '',
    },
    consignee: {
      name: b.consignee?.name ?? '',
      phone: b.consignee?.phone ?? '',
      idNumber: '',
      address: '',
    },
    horses: (b.horses ?? []).map((h, i) => ({
      horseId: h.horseId || `H-${b.numericId}-${i + 1}`,
      name: h.name,
      microchip: h.microchip ?? '',
      breed: h.breed ?? 'Thoroughbred',
      sex: (h.sex ?? 'stallion') as any,
      stall: (h.stall ?? 'standard') as any,
      feedPackage: (h.feedPackage ?? 'standard') as any,
      waterPlan: (h.waterPlan ?? 'auto') as any,
      insurance: { opted: !!h.insuranceOpted },
    })),
    customer: b.customer || 'Khách hàng',
    createdAt: b.createdAt ?? Date.now(),
    status: (b.status ?? 'pending_intake') as any,
    history: [{ time: b.createdAt ?? Date.now(), actor: b.customer || 'Khách hàng', text: 'Gửi yêu cầu đặt đơn' }],
  }
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

// Công khai: không cần đăng nhập. null = không tìm thấy hoặc SĐT không khớp (không tiết lộ đơn có tồn tại hay không).
export const publicBookingsApi = {
  track: async (code: string, phoneLast4: string): Promise<PublicTracking | null> => {
    applySystemRules()
    const b = store.get(code.trim().toUpperCase())
    if (!b || b.consignor.phone.replace(/\D/g, '').slice(-4) !== phoneLast4) return null
    const short = (n: string) => n.split(' — ')[0]
    const active = b.trips?.find(t => t.run?.startedAt && !t.run.deliveredAt)
    const cp = active && currentCheckpoint(active)
    const delivery = active?.run?.checkpoints.at(-1)
    const needsCustomer = b.status === 'awaiting_payment' || b.medical?.status === 'resubmit'
    return {
      route: `${short(b.origin.name)} → ${short(b.dest.name)}`, depart: b.departAt, step: publicStepOf(b.status),
      done: b.status === 'delivered_pending_settlement', status: BOOKING_STATUS[b.status].customerLabel,
      tone: b.status === 'quote_expired' ? 'bad' : b.status === 'delivered_pending_settlement' ? 'done' : needsCustomer ? 'warn' : undefined,
      now: b.status === 'in_transit' && active && cp && delivery ? { place: `${cp.place}, ${cp.label.toLowerCase()}`, at: active.run!.checkpoints.filter(c => c.arrivedAt).at(-1)?.arrivedAt ?? active.run!.startedAt ?? Date.now(), eta: delivery.plannedAt } : undefined,
      note: needsCustomer ? 'Đơn cần người đặt xử lý. Vui lòng đăng nhập để xem chi tiết.' : undefined,
    }
  },
}

// Dành cho app khách
export const customerBookingsApi = {
  list: async (customer: string): Promise<CustomerBookingView[]> => {
    applySystemRules()
    try {
      const beList = await backendBookingApi.getCustomerBookings()
      if (beList && beList.length > 0) {
        for (const item of beList) {
          if (!store.get(item.id)) {
            store.add(backendToBooking(item))
          }
        }
      }
    } catch {
      // Offline fallback
    }
    return store.all().filter(b => b.customer === customer).map(toCustomerView).sort((a, b) => b.createdAt - a.createdAt)
  },
  get: async (customer: string, id: string): Promise<CustomerBookingView | undefined> => {
    applySystemRules()
    let b = store.get(id)
    if (!b && id.startsWith('BK-')) {
      try {
        const numId = parseInt(id.replace(/\D/g, ''), 10)
        if (!isNaN(numId)) {
          const beRes = await backendBookingApi.getCustomerBookingById(numId)
          if (beRes) {
            b = backendToBooking(beRes)
            store.add(b)
          }
        }
      } catch {
        // Offline fallback
      }
    }
    return b && b.customer === customer ? toCustomerView(b) : undefined
  },
  create: async (customer: string, input: NewBookingInput): Promise<CustomerBookingView> => {
    const now = Date.now()
    if (store.all().some(o => o.customer === customer && o.status === 'payment_overdue')) throw new Error('Bạn có đơn quá hạn thanh toán quyết toán nên chưa đặt được đơn mới. Vui lòng thanh toán trước.')

    let b: Booking
    try {
      const beRes = await backendBookingApi.create({
        type: input.type,
        originName: input.origin.name,
        originCountry: input.origin.country,
        destName: input.dest.name,
        destCountry: input.dest.country,
        departAt: input.departAt,
        consignor: { name: input.consignor.name, phone: input.consignor.phone },
        consignee: { name: input.consignee.name, phone: input.consignee.phone },
        horses: input.horses.map(h => ({
          horseId: h.horseId,
          name: h.name,
          microchip: h.microchip,
          breed: h.breed,
          sex: h.sex,
          stall: h.stall,
          feedPackage: h.feedPackage,
          waterPlan: h.waterPlan,
          insuranceOpted: h.insurance?.opted,
        })),
      })
      b = backendToBooking(beRes)
    } catch {
      b = { ...input, id: nextBookingId(store.all()), customer, createdAt: now, status: 'pending_intake', history: [{ time: now, actor: customer, text: 'Gửi yêu cầu đặt đơn' }] }
    }
    store.add(b)
    notify(b, ['manager'], `Đơn mới ${b.id}`, `${customer} vừa gửi đơn ${input.horses.length} ngựa, chờ tiếp nhận.`, { manager: LINK.manager.intake })
    return toCustomerView(b)
  },
  team: async (customer: string, id: string): Promise<TripTeam[]> => {
    const b = store.get(id)
    if (!b || b.customer !== customer || !b.trips || !SHOW_TEAM.includes(b.status)) return []
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
    const out = toCustomerView(store.update(id, {
      status: 'cancelled', cancellation: { at: Date.now(), reason: reason.trim(), by: 'customer', refund: 0 },
      history: log(b, customer, `Từ chối báo giá${reason.trim() ? `: ${reason.trim()}` : ''}`),
    }))
    if (typeof window !== 'undefined') {
      try {
        await backendBookingApi.rejectQuote(id, { reason: reason.trim() })
      } catch (e) {
        console.warn('Backend rejectQuote sync error:', e)
      }
    }
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
    const now = Date.now()
    const waybill = { no: waybillNoOf(id), issuedAt: now }
    const out = toCustomerView(store.update(id, {
      status: 'waybill_issued', waybill, payment: { paidAt: now, amount: b.quote!.deposit, reference: `EQZ-${id.slice(-4)}-DEP` },
      clearance: b.clearance ?? blankClearance(b.type),
      history: log(b, customer, `Đặt cọc 30%, cấp Vận đơn ${waybill.no}`),
    }))
    if (typeof window !== 'undefined') {
      try {
        await backendBookingApi.payDeposit(id, { amount: b.quote!.deposit, transactionCode: `EQZ-${id.slice(-4)}-DEP` })
      } catch (e) {
        console.warn('Backend payDeposit sync error:', e)
      }
    }
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
    const out = toCustomerView(store.update(id, { balance: { paidAt: Date.now(), amount: b.quote!.balance, reference: `EQZ-${id.slice(-4)}-BAL` }, history: log(b, customer, 'Thanh toán 70% còn lại') }))
    notify(b, ['manager', 'coordinator'], `Khách đã trả 70% — đơn ${id}`, `Đã nhận ${formatVND(b.quote!.balance)}, xe được phép bắt đầu hành trình.`, { coordinator: LINK.coordinator(id, true) })
    return out
  },
  // Thanh toán bảng quyết toán (nếu có) và chấm điểm chuyến đi: đóng đơn, cộng số chuyến cho ngựa (Flow 6, bước 4–5)
  settle: async (customer: string, id: string, rating: Omit<Rating, 'at'>): Promise<CustomerBookingView> => {
    const b = must(id)
    if (b.customer !== customer) throw new Error('Không có quyền với đơn này.')
    if (!['settlement_issued', 'payment_overdue'].includes(b.status) || !b.settlement) throw new Error(b.status === 'completed' ? 'Đơn đã đóng.' : 'Bảng quyết toán chưa phát hành.')
    if (![rating.trip, rating.driver, rating.escort].every(n => Number.isInteger(n) && n >= 1 && n <= 5)) throw new Error('Chấm điểm từ 1 đến 5 sao cho chuyến đi, tài xế và hộ tống.')
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
    try {
      const beList = await backendBookingApi.getAllBookings()
      if (beList && beList.length > 0) {
        for (const item of beList) {
          if (!store.get(item.id)) {
            store.add(backendToBooking(item))
          }
        }
      }
    } catch {
      // Offline fallback
    }
    return structuredClone(store.all()).sort((a, b) => a.createdAt - b.createdAt)
  },
  get: async (id: string): Promise<Booking | undefined> => {
    applySystemRules()
    let b = store.get(id)
    if (!b && id.startsWith('BK-')) {
      try {
        const numId = parseInt(id.replace(/\D/g, ''), 10)
        if (!isNaN(numId)) {
          const beRes = await backendBookingApi.getCustomerBookingById(numId)
          if (beRes) {
            b = backendToBooking(beRes)
            store.add(b)
          }
        }
      } catch {
        // Offline fallback
      }
    }
    return b ? structuredClone(b) : undefined
  },

  // Manager: tiếp nhận, giao Kiểm dịch viên và Điều phối viên. Xe, tài xế, hộ tống do Điều phối viên chọn khi lập lộ trình.
  activate: async (id: string, by: string, specialist: StaffRef, coordinator: StaffRef): Promise<Booking> => {
    const b = must(id)
    expect(b, 'pending_intake', 'Đơn này đã được tiếp nhận.')
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

  // Manager hủy đơn đã cọc khi xe chưa nhận ngựa: hệ thống hủy nên hoàn đủ cọc và số dư đã trả cho khách.
  managerCancel: async (id: string, by: string, reason: string): Promise<Booking> => {
    const b = must(id)
    if (!CANCEL_AFTER_DEPOSIT.includes(b.status)) throw new Error('Chỉ hủy được đơn đã cọc mà xe chưa nhận ngựa.')
    if (!reason.trim()) throw new Error('Cần ghi lý do hủy.')
    const refund = cancelRefund(b, 'manager')
    const out = structuredClone(store.update(id, {
      status: 'cancelled', cancellation: { at: Date.now(), reason: reason.trim(), by: 'manager', refund },
      history: log(b, by, `Manager hủy đơn, hoàn ${refund.toLocaleString('en-US')} ₫ cho khách: ${reason.trim()}`),
    }))
    notify(out, ['customer'], `Đơn ${id} đã bị hủy`, `Nhà xe hủy đơn: ${reason.trim()}. Hoàn ${refund.toLocaleString('en-US')} ₫ về tài khoản bạn dùng để thanh toán.`)
    notify(out, ['specialist', 'coordinator'], `Đơn ${id} đã bị hủy`, `Manager hủy đơn: ${reason.trim()}`)
    await notifyCrew(out, `Đơn ${id} đã bị hủy`, 'Nhà xe đã hủy đơn, không cần thực hiện chuyến này.')
    return out
  },

  // Manager không nhận đơn lúc tiếp nhận, hoặc Coordinator không duyệt phương án xe và lộ trình: đơn đóng, khách nhận lý do và đặt lại.
  rejectOrder: async (id: string, by: string, role: 'manager' | 'coordinator', reason: string): Promise<Booking> => {
    const b = must(id)
    if (role === 'manager') expect(b, 'pending_intake', 'Chỉ từ chối được đơn đang chờ tiếp nhận.')
    else expect(b, 'under_review', 'Chỉ từ chối được đơn đang thẩm định.')
    if (!reason.trim()) throw new Error('Cần ghi lý do từ chối.')
    const out = structuredClone(store.update(id, {
      status: 'rejected', rejection: { at: Date.now(), by, role, reason: reason.trim() },
      history: log(b, by, `Từ chối đơn${role === 'coordinator' ? ' (không duyệt xe và lộ trình)' : ''}: ${reason.trim()}`),
    }))
    notify(out, ['customer'], `Đơn ${id} không được nhận`, `Nhà xe không nhận đơn: ${reason.trim()}`)
    if (role === 'coordinator') notify(out, ['manager', 'specialist'], `Đơn ${id} bị từ chối`, `Điều phối viên không duyệt xe và lộ trình: ${reason.trim()}`)
    return out
  },

  // Specialist: duyệt hồ sơ ngựa
  approveMedical: async (id: string, by: string): Promise<Booking> => {
    const b = must(id)
    expect(b, 'under_review', 'Đơn không ở bước thẩm định.')
    const medical: MedicalReview = { status: 'approved', at: Date.now(), by }
    const next = { ...b, medical }
    const out = structuredClone(store.update(id, {
      medical, sentBack: b.sentBack?.to === 'specialist' ? undefined : b.sentBack, status: reviewDone(next) ? 'pending_commercial' : 'under_review',
      history: log(b, by, reviewDone(next) ? 'Duyệt hồ sơ ngựa. Đủ điều kiện, chuyển quản lý duyệt báo giá' : 'Duyệt hồ sơ ngựa'),
    }))
    notify(b, ['manager'], `Hồ sơ ngựa đã duyệt — đơn ${id}`, reviewDone(next) ? 'Đơn đã đủ điều kiện, chờ bạn duyệt báo giá.' : 'Kiểm dịch viên đã duyệt hồ sơ ngựa, còn chờ Điều phối viên chốt xe.', { manager: reviewDone(next) ? LINK.manager.approvals : LINK.manager.intake })
    return out
  },

  // Specialist: yêu cầu khách bổ sung hồ sơ ngựa (bắt buộc ghi lý do)
  requestResubmission: async (id: string, by: string, reason: string, items: { horseId: string; doc: 'passport' | 'vaccine' | 'lab' }[]): Promise<Booking> => {
    const b = must(id)
    expect(b, 'under_review', 'Đơn không ở bước thẩm định.')
    if (!reason.trim()) throw new Error('Cần ghi lý do yêu cầu bổ sung.')
    if (!items.length) throw new Error('Chọn ít nhất một giấy cần bổ sung.')
    const out = structuredClone(store.update(id, {
      medical: { ...b.medical, status: 'resubmit', at: Date.now(), by, resubmit: { reason: reason.trim(), items, at: Date.now() } },
      history: log(b, by, `Yêu cầu khách bổ sung hồ sơ: ${reason.trim()}`),
    }))
    notify(b, ['customer'], `Cần bổ sung hồ sơ ngựa — đơn ${id}`, reason.trim())
    return out
  },

  // Coordinator: xác nhận xe, ngựa trên từng xe và lộ trình chi tiết (một lộ trình cho cả đơn). Tài xế và hộ tống do Manager chọn sau (assignCrew).
  confirmPlan: async (id: string, by: string, input: { trips: Pick<VehicleTrip, 'vehicleId' | 'horseIds'>[]; route: Pick<RoutePlan, 'legs' | 'rests' | 'borderEta'>; gate?: string; note: string }): Promise<Booking> => {
    const b = must(id)
    expect(b, 'under_review', 'Đơn không ở bước thẩm định.')
    const international = b.type === 'international'
    if (!input.trips.length) throw new Error('Chưa có xe nào.')
    if (input.trips.some(t => !t.horseIds.length)) throw new Error('Mỗi xe phải chở ít nhất một ngựa.')
    const vehicles = await vehiclesApi.list()
    const busy = busyResources(store.all(), b.departAt, id)
    const placed = input.trips.flatMap(t => t.horseIds)
    if (placed.length !== b.horses.length || b.horses.some(h => placed.filter(x => x === h.horseId).length !== 1)) throw new Error('Mỗi ngựa phải nằm trên đúng một xe.')
    if (new Set(input.trips.map(t => t.vehicleId)).size !== input.trips.length) throw new Error('Một xe không được chọn hai lần.')
    input.trips.forEach(t => {
      const v = vehicles.find(x => x.id === t.vehicleId)
      if (!v) throw new Error('Chưa chọn xe cho tất cả các chuyến.')
      if (!vehicleDocsOk(v, international)) throw new Error(`Xe ${v.plate} thiếu giấy đăng kiểm${international ? ' hoặc giấy phép liên vận' : ''}, chọn xe khác.`)
      if (t.horseIds.length > v.capacity) throw new Error(`Xe ${v.plate} chỉ có ${v.capacity} ngăn, đang xếp ${t.horseIds.length} ngựa.`)
      if (busy.vehicles.has(v.id)) throw new Error(`Xe ${v.plate} đã được giữ cho đơn khác có ngày đi gần ngày này.`)
    })
    if (international && !gatesFor(b.origin, b.dest).some(g => g.name === input.gate)) throw new Error('Chưa chọn cửa khẩu hợp lệ cho tuyến này.')
    const { errors } = validateRoutePlan(input.route, international)
    if (errors.length) throw new Error(errors[0])
    const trips: VehicleTrip[] = input.trips.map((t, i) => ({ ...t, driverId: '', escortId: '', tripId: tripIdFor(id, i + 1), acks: {} }))
    const next = { ...b, plan: { at: Date.now(), by, note: input.note } }
    const out = structuredClone(store.update(id, {
      trips, route: { ...input.route, completedAt: Date.now(), by }, plan: next.plan, sentBack: b.sentBack?.to === 'coordinator' ? undefined : b.sentBack, gate: international ? input.gate : undefined,
      status: reviewDone(next) ? 'pending_commercial' : 'under_review',
      history: log(b, by, reviewDone(next) ? `Xác nhận ${trips.length} xe và lộ trình. Đủ điều kiện, chuyển quản lý duyệt báo giá` : `Xác nhận ${trips.length} xe và lộ trình`),
    }))
    // Đồng bộ phương án xe và lộ trình xuống Backend SQL Server (khi chạy trên trình duyệt)
    if (typeof window !== 'undefined') {
      try {
        await backendBookingApi.confirmFleetPlan(id, {
          by,
          trips: input.trips.map(t => ({ vehicleId: t.vehicleId, horseIds: t.horseIds })),
          route: input.route,
          gate: input.gate,
          note: input.note,
        })
      } catch (apiErr) {
        console.warn('Backend confirmFleetPlan sync notification:', apiErr)
      }
    }

    notify(b, ['manager'], `Điều phối viên đã chốt xe và lộ trình — đơn ${id}`, reviewDone(next) ? `${trips.length} xe. Đơn đã đủ điều kiện, chờ bạn duyệt báo giá.` : `${trips.length} xe. Còn chờ Kiểm dịch viên thẩm định hồ sơ ngựa.`, { manager: reviewDone(next) ? LINK.manager.approvals : LINK.manager.intake })
    return out
  },

  // Manager: trả đơn đang chờ duyệt báo giá về Kiểm dịch viên (duyệt lại hồ sơ ngựa) hoặc Điều phối viên (làm lại xe và lộ trình), bắt buộc ghi lý do
  sendBack: async (id: string, by: string, to: 'specialist' | 'coordinator', reason: string): Promise<Booking> => {
    const b = must(id)
    expect(b, 'pending_commercial', 'Chỉ trả lại được đơn đang chờ duyệt báo giá.')
    if (!reason.trim()) throw new Error('Cần ghi lý do trả lại.')
    const sentBack: SentBack = { to, reason: reason.trim(), at: Date.now(), by }
    const label = to === 'specialist' ? 'Kiểm dịch viên duyệt lại hồ sơ ngựa' : 'Điều phối viên làm lại xe và lộ trình'
    const patch: Partial<Booking> = to === 'specialist'
      ? { medical: { status: 'pending' } }
      : { plan: undefined, route: undefined, trips: undefined, gate: undefined } // chọn lại xe nên Driver, Escort đã chọn cũng bỏ
    const out = structuredClone(store.update(id, { ...patch, sentBack, status: 'under_review', history: log(b, by, `Trả lại ${label}: ${reason.trim()}`) }))
    if (typeof window !== 'undefined') {
      try {
        await backendBookingApi.sendBack(id, { by, to, reason: reason.trim() })
      } catch (e) {
        console.warn('Backend sendBack sync error:', e)
      }
    }
    notify(out, [to], `Đơn ${id} được trả lại`, `Quản lý yêu cầu ${label.toLowerCase()}: ${reason.trim()}`, to === 'specialist' ? {} : { coordinator: LINK.coordinator(id) })
    return out
  },

  // Manager: chọn tài xế và hộ tống cho từng xe Coordinator đã chọn (sau khi Coordinator chốt xe và lộ trình, trước khi gửi báo giá)
  assignCrew: async (id: string, by: string, picks: { tripId: string; driverId: string; escortId: string }[]): Promise<Booking> => {
    const b = must(id)
    expect(b, 'pending_commercial', 'Chỉ chọn tài xế và hộ tống khi đơn đang chờ duyệt báo giá.')
    const trips = b.trips ?? []
    if (picks.length !== trips.length || trips.some(t => picks.filter(p => p.tripId === t.tripId).length !== 1)) throw new Error('Cần chọn tài xế và hộ tống cho tất cả các xe.')
    if (picks.some(p => !p.driverId || !p.escortId)) throw new Error('Mỗi xe cần một tài xế và một nhân viên hộ tống.')
    if (new Set(picks.map(p => p.driverId)).size !== picks.length) throw new Error('Mỗi xe cần một tài xế riêng.')
    if (new Set(picks.map(p => p.escortId)).size !== picks.length) throw new Error('Mỗi xe cần một nhân viên hộ tống riêng.')
    const crew = await crewApi.list()
    const busy = busyResources(store.all(), b.departAt, id)
    picks.forEach(p => {
      if (!crew.some(c => c.id === p.driverId && c.role === 'driver')) throw new Error('Tài xế không có trong danh bạ.')
      if (!crew.some(c => c.id === p.escortId && c.role === 'escort')) throw new Error('Nhân viên hộ tống không có trong danh bạ.')
      if (busy.crew.has(p.driverId)) throw new Error('Tài xế đã được giữ cho đơn khác có ngày đi gần ngày này.')
      if (busy.crew.has(p.escortId)) throw new Error('Nhân viên hộ tống đã được giữ cho đơn khác có ngày đi gần ngày này.')
    })
    const next = trips.map(t => { const p = picks.find(x => x.tripId === t.tripId)!; return { ...t, driverId: p.driverId, escortId: p.escortId } })
    const out = structuredClone(store.update(id, { trips: next, history: log(b, by, `Chọn tài xế và hộ tống cho ${next.length} xe`) }))
    if (typeof window !== 'undefined') {
      try {
        await backendBookingApi.assignCrew(id, { by, picks })
      } catch (e) {
        console.warn('Backend assignCrew sync error:', e)
      }
    }
    return out
  },

  // Manager: dòng báo giá do hệ thống tính (chưa gồm phụ phí và chiết khấu)
  quoteDraft: async (id: string) => {
    const b = must(id)
    if (!b.trips?.length) throw new Error('Đơn chưa có phương án xe.')
    const all = await vehiclesApi.list()
    const vs = b.trips.map(t => all.find(v => v.id === t.vehicleId)).filter((v): v is NonNullable<typeof v> => !!v)
    if (vs.length !== b.trips.length) throw new Error('Không tìm thấy xe của đơn.')
    return quoteLines(b, vs)
  },

  // Manager: duyệt và gửi báo giá, khách có 48 giờ để đặt cọc
  sendQuote: async (id: string, by: string, adjustments: Adjustment[]): Promise<Booking> => {
    const b = must(id)
    expect(b, 'pending_commercial', 'Đơn không ở bước duyệt báo giá.')
    if (!(b.trips ?? []).every(crewAssigned)) throw new Error('Chọn tài xế và hộ tống cho tất cả các xe trước khi gửi báo giá.')
    const { lines } = await bookingsApi.quoteDraft(id)
    const quote = finalizeQuote(lines, adjustments.filter(a => a.label.trim() && a.amount !== 0), Date.now(), by)
    const out = structuredClone(store.update(id, { quote, status: 'awaiting_payment', history: log(b, by, 'Duyệt và gửi báo giá') }))
    if (typeof window !== 'undefined') {
      try {
        await backendBookingApi.sendQuote(id, {
          by,
          adjustments: adjustments.filter(a => a.label.trim() && a.amount !== 0),
          lines,
          subtotal: quote.subtotal,
          total: quote.total,
          deposit: quote.deposit,
          balance: quote.balance,
        })
      } catch (e) {
        console.warn('Backend sendQuote sync error:', e)
      }
    }
    notify(b, ['customer'], `Báo giá đơn ${id}`, `Tổng ${formatVND(quote.total)}, đặt cọc ${formatVND(quote.deposit)} trong 48 giờ.`)
    return out
  },

  // ===== Flow 2: Specialist làm giấy tờ =====
  // Specialist: tiếp nhận Vận đơn, bắt đầu làm giấy tờ
  acceptWaybill: async (id: string, by: string): Promise<Booking> => {
    const b = must(id)
    expect(b, 'waybill_issued', 'Đơn này chưa có Vận đơn hoặc đã được tiếp nhận.')
    const out = structuredClone(store.update(id, {
      status: 'clearance_in_progress', clearance: { ...(b.clearance ?? blankClearance(b.type)), acceptedAt: Date.now(), acceptedBy: by },
      history: log(b, by, `Tiếp nhận Vận đơn ${b.waybill?.no ?? ''}, bắt đầu làm giấy tờ`),
    }))
    notify(b, ['customer'], `Nhà xe bắt đầu làm giấy tờ — đơn ${id}`, 'Giấy kiểm dịch và hải quan đang được làm. Bạn theo dõi tiến độ trong đơn.')
    return out
  },

  // Specialist: cập nhật một hạng mục giấy tờ (ghi chú, ảnh chụp). Trạng thái không chỉnh tay: có ảnh = Đã nộp, chưa có ảnh = Chưa nộp.
  updateClearanceItem: async (id: string, by: string, type: ClearanceDocType, patch: { note?: string; photos?: string[] }): Promise<Booking> => {
    const b = must(id)
    if (!STAFF_ON_CLEARANCE.includes(b.status) || !b.clearance) throw new Error('Đơn không ở bước làm giấy tờ.')
    const item = b.clearance.items.find(i => i.type === type)
    if (!item) throw new Error('Hạng mục này không có trong đơn.')
    const merged = { ...item, ...patch }
    const next: ClearanceItem = { ...merged, status: merged.photos.length ? 'done' : 'todo', updatedAt: Date.now(), by }
    const items = b.clearance.items.map(i => (i.type === type ? next : i))
    const firstTouch = b.status === 'waybill_issued'
    return structuredClone(store.update(id, {
      status: 'clearance_in_progress',
      clearance: { ...b.clearance, items, acceptedAt: b.clearance.acceptedAt ?? Date.now(), acceptedBy: b.clearance.acceptedBy ?? by },
      history: log(b, by, `${firstTouch ? 'Tiếp nhận Vận đơn. ' : ''}Giấy tờ "${CLEARANCE_DOC[type].short}": ${patch.photos ? (next.status === 'done' ? 'đã nộp (có ảnh chụp)' : 'chưa nộp (chưa có ảnh)') : 'cập nhật ghi chú'}`),
    }))
  },

  // Specialist: thêm hạng mục giấy không có sẵn (giấy cách ly, ATA Carnet, hóa đơn thương mại)
  addClearanceItem: async (id: string, by: string, type: ClearanceDocType): Promise<Booking> => {
    const b = must(id)
    if (!STAFF_ON_CLEARANCE.includes(b.status) || !b.clearance) throw new Error('Đơn không ở bước làm giấy tờ.')
    if (CLEARANCE_DOC[type].international && b.type !== 'international') throw new Error('Giấy này chỉ áp dụng cho tuyến quốc tế.')
    if (b.clearance.items.some(i => i.type === type)) throw new Error('Hạng mục này đã có trong đơn.')
    const items = [...b.clearance.items, { type, status: 'todo' as const, note: '', photos: [] }]
    return structuredClone(store.update(id, { clearance: { ...b.clearance, items }, history: log(b, by, `Thêm hạng mục giấy tờ "${CLEARANCE_DOC[type].short}"`) }))
  },

  // Specialist: ghi nhận (hoặc bỏ ghi nhận) một ngựa đã có giấy thông quan (quốc tế)
  markHorseCleared: async (id: string, by: string, horseId: string, cleared: boolean): Promise<Booking> => {
    const b = must(id)
    if (!STAFF_ON_CLEARANCE.includes(b.status) || !b.clearance) throw new Error('Đơn không ở bước làm giấy tờ.')
    if (b.type !== 'international') throw new Error('Chỉ tuyến quốc tế mới ghi nhận thông quan.')
    const horse = b.horses.find(h => h.horseId === horseId)
    if (!horse) throw new Error('Ngựa này không có trong đơn.')
    const rest = b.clearance.horsesCleared.filter(x => x !== horseId)
    return structuredClone(store.update(id, { clearance: { ...b.clearance, horsesCleared: cleared ? [...rest, horseId] : rest }, history: log(b, by, `${cleared ? 'Ghi nhận' : 'Bỏ ghi nhận'} giấy thông quan của ${horse.name}`) }))
  },

  // Specialist: hoàn tất mọi giấy tờ. Có thể thành Ready for Pickup ngay nếu mọi xe đã nhận lệnh.
  completeClearance: async (id: string, by: string): Promise<Booking> => {
    const b = must(id)
    if (!STAFF_ON_CLEARANCE.includes(b.status)) throw new Error('Đơn không ở bước làm giấy tờ.')
    const why = canCompleteClearance(b)
    if (why) throw new Error(why)
    const done = { ...b, status: 'clearance_done' as const }
    const out = structuredClone(store.update(id, {
      clearance: { ...b.clearance!, doneAt: Date.now(), doneBy: by }, status: deriveStatus(done),
      history: log(b, by, 'Hoàn tất toàn bộ giấy tờ kiểm dịch và hải quan'),
    }))
    notify(b, ['customer', 'manager'], `Giấy tờ đã xong — đơn ${id}`, 'Giấy kiểm dịch và hải quan đã hoàn tất. Xe sẵn sàng đi đón ngựa khi tài xế và hộ tống nhận lệnh.')
    return out
  },

  // Coordinator: nhập bộ giấy cho Driver của một xe mang theo
  setDriverPack: async (id: string, tripId: string, by: string, items: string[]): Promise<Booking> => {
    const b = must(id)
    if (!PREP.includes(b.status)) throw new Error('Đơn chưa đến hoặc đã qua bước chuẩn bị giấy cho tài xế.')
    const t = tripOf(b, tripId)
    if (t.departedAt) throw new Error('Xe này đã xuất phát, không nhập thêm bộ giấy.')
    t.driverPack = { items, at: Date.now(), by }
    return commitTrips(b, by, `Nhập bộ giấy cho tài xế ${tripId} (${items.length} mục)`)
  },

  // ===== Flow 3: Lệnh điều xe =====
  // Driver / Escort: xác nhận đã nhận Lệnh điều xe (phát ngay sau cọc). Xe sẵn sàng khi giấy xong và cả hai đã nhận.
  acknowledgeTrip: async (id: string, tripId: string, who: 'driver' | 'escort', by: string): Promise<Booking> => {
    const b = must(id)
    if (!PREP.includes(b.status)) throw new Error('Đơn không ở bước nhận lệnh.')
    const t = tripOf(b, tripId)
    if (t.departedAt) throw new Error('Xe này đã xuất phát.')
    t.acks = { ...t.acks, [who]: Date.now() }
    return commitTrips(b, by, `${who === 'driver' ? 'Tài xế' : 'Hộ tống'} xác nhận đã nhận Lệnh điều xe ${tripId}`)
  },

  // Driver: bắt đầu di chuyển đến điểm đón (cần giấy tờ xong và cả hai đã nhận lệnh)
  departToPickup: async (id: string, tripId: string, by: string): Promise<Booking> => {
    const b = must(id)
    if (STAFF_ON_CLEARANCE.includes(b.status) || !b.clearance?.doneAt) throw new Error('Xe chưa sẵn sàng: giấy tờ chưa hoàn tất.')
    const t = liveTrip(b, tripId)
    if (!t.acks.driver || !t.acks.escort) throw new Error('Xe chưa sẵn sàng: Tài xế và hộ tống cần xác nhận nhận lệnh trước.')
    if (t.departedAt) throw new Error('Xe đã xuất phát đến điểm đón.')
    t.departedAt = Date.now()
    return commitTrips(b, by, `Xe ${tripId} bắt đầu di chuyển đến điểm đón ngựa`)
  },

  // ===== Flow 4: hành trình thực tế của từng xe =====
  // Driver: tới điểm đón, check-in kèm ảnh chụp trực tiếp (tạo các mốc hành trình từ lộ trình đã lập)
  arriveAtPickup: async (id: string, tripId: string, by: string, photo: string): Promise<Booking> => {
    const b = must(id)
    const t = liveTrip(b, tripId)
    if (!t.departedAt || t.run?.startedAt) throw new Error('Xe chưa ở bước đến điểm đón.')
    if (!photo) throw new Error('Cần chụp ảnh tại điểm đón.')
    const run: TripRun = t.run ?? { checkpoints: buildCheckpoints(b), welfare: [] }
    t.run = run
    const cp = run.checkpoints[0]
    cp.arrivedAt = Date.now(); cp.photo = photo; cp.by = by
    return commitTrips(b, by, `Tài xế ${tripId} đã tới điểm đón, xác nhận có mặt kèm ảnh`)
  },

  // Escort: quét microchip từng con, phải khớp ngựa trên xe này
  scanChip: async (id: string, tripId: string, by: string, chip: string): Promise<Booking> => {
    const b = must(id)
    const t = liveTrip(b, tripId)
    const pickup = t.run?.checkpoints[0]
    if (!pickup?.arrivedAt || pickup.doneAt) throw new Error('Chưa tới bước quét microchip.')
    const code = chip.trim().toUpperCase()
    if (!b.horses.some(h => t.horseIds.includes(h.horseId) && h.microchip.toUpperCase() === code)) throw new Error(`Microchip ${code} không khớp ngựa nào trên xe này. Dừng lại và báo Điều phối.`)
    pickup.chips = [...new Set([...(pickup.chips ?? []), code])]
    return commitTrips(b, by, `Quét microchip ${code}, khớp hồ sơ`)
  },

  // Driver: tick các bản gốc đã nhận từ người gửi
  collectOriginals: async (id: string, tripId: string, by: string, items: string[]): Promise<Booking> => {
    const b = must(id)
    const t = liveTrip(b, tripId)
    const pickup = t.run?.checkpoints[0]
    if (!pickup?.arrivedAt || pickup.doneAt) throw new Error('Chưa tới bước thu chứng từ gốc.')
    pickup.originals = items
    return commitTrips(b, by, `Thu chứng từ gốc ${items.length}/${manifestDocuments(b).originals.length}`)
  },

  // Driver: ảnh biên bản giao nhận có chữ ký hai bên (tại điểm đón hoặc điểm giao)
  uploadHandover: async (id: string, tripId: string, by: string, photo: string): Promise<Booking> => {
    const b = must(id)
    const t = liveTrip(b, tripId)
    const cp = currentCheckpoint(t)
    if (!cp || !cp.arrivedAt || (cp.type !== 'pickup' && cp.type !== 'delivery')) throw new Error('Chưa tới bước ký biên bản.')
    cp.handoverPhoto = photo
    return commitTrips(b, by, cp.type === 'pickup' ? 'Tải ảnh biên bản giao nhận ngựa và chứng từ gốc' : 'Tải ảnh biên bản bàn giao hoàn tất')
  },

  // Driver: bắt đầu hành trình (đủ: khách trả 70%, check-in, quét hết chip, thu đủ bản gốc, có biên bản)
  startJourney: async (id: string, tripId: string, by: string): Promise<Booking> => {
    const b = must(id)
    const t = liveTrip(b, tripId)
    if (!b.balance) throw new Error('Khách chưa thanh toán 70% còn lại. Chưa được bắt đầu hành trình.')
    const p = t.run?.checkpoints[0]
    if (!p?.arrivedAt) throw new Error('Chưa xác nhận có mặt tại điểm đón.')
    if (p.doneAt) throw new Error('Xe đã bắt đầu hành trình.')
    if ((p.chips?.length ?? 0) < t.horseIds.length) throw new Error('Chưa quét đủ microchip của tất cả ngựa trên xe.')
    if ((p.originals?.length ?? 0) < manifestDocuments(b).originals.length) throw new Error('Chưa thu đủ chứng từ gốc.')
    if (!p.handoverPhoto) throw new Error('Chưa có ảnh biên bản giao nhận có chữ ký.')
    p.doneAt = Date.now()
    t.run!.startedAt = p.doneAt
    const out = commitTrips(b, by, `Xe ${tripId} bắt đầu hành trình`)
    notify(b, ['customer', 'manager', 'coordinator'], `Xe ${tripId} đã bắt đầu hành trình — đơn ${b.id}`, 'Tài xế đã nhận ngựa tại điểm đón và xuất phát.', { coordinator: '/coordinator/monitoring' })
    return out
  },

  // Driver: check-in tại trạm nghỉ, cửa khẩu hoặc điểm giao
  arriveCheckpoint: async (id: string, tripId: string, by: string, photo: string): Promise<Booking> => {
    const b = must(id)
    const t = liveTrip(b, tripId)
    running(t)
    if (pendingDeparture(t)) throw new Error('Chưa bấm tiếp tục hành trình rời cửa khẩu.')
    const cp = currentCheckpoint(t)
    if (!cp || !['rest', 'border', 'delivery'].includes(cp.type)) throw new Error('Mốc hiện tại không phải xác nhận có mặt tới nơi.')
    if (cp.arrivedAt) throw new Error('Đã xác nhận có mặt mốc này.')
    if (!photo) throw new Error('Cần chụp ảnh trực tiếp tại mốc.')
    const now = Date.now()
    cp.arrivedAt = now; cp.photo = photo; cp.by = by
    if (cp.type === 'border') cp.doneAt = now // tới cửa khẩu xong, mốc kế tiếp là thông quan
    return commitTrips(b, by, `Xác nhận có mặt ${tripId}: ${cp.label.toLowerCase()} tại ${cp.place}`)
  },

  // Escort: nhật ký an sinh tại trạm nghỉ hoặc điểm giao
  submitWelfare: async (id: string, tripId: string, by: string, data: { condition: WelfareCondition; waterLiters: number; hay: boolean; photo: string; note: string }): Promise<Booking> => {
    const b = must(id)
    const t = liveTrip(b, tripId)
    const run = running(t)
    const cp = currentCheckpoint(t)
    if (!cp?.arrivedAt || !['rest', 'delivery'].includes(cp.type)) throw new Error('Chỉ ghi nhật ký an sinh khi xe đã tới trạm nghỉ hoặc điểm giao.')
    if (!data.photo) throw new Error('Cần chụp ảnh ngựa trong khoang.')
    const entry: WelfareLog = { id: `WL-${run.welfare.length + 1}`, checkpointId: cp.id, at: Date.now(), by, ...data }
    run.welfare = [...run.welfare, entry]
    return commitTrips(b, by, `Nhật ký an sinh ${tripId}: ${data.condition === 'normal' ? 'bình thường' : data.condition === 'stress' ? 'căng thẳng' : 'đổ mồ hôi nhiều'}`)
  },

  // Driver: tiếp tục hành trình sau khi dừng ở trạm nghỉ (cần đã có nhật ký an sinh của trạm)
  continueJourney: async (id: string, tripId: string, by: string): Promise<Booking> => {
    const b = must(id)
    const t = liveTrip(b, tripId)
    const run = running(t)
    const left = pendingDeparture(t) // thông quan xong: rời cửa khẩu
    if (left) { left.leftAt = Date.now(); return commitTrips(b, by, `Xe ${tripId} rời cửa khẩu, tiếp tục hành trình`) }
    const cp = currentCheckpoint(t)
    if (cp?.type !== 'rest' || !cp.arrivedAt) throw new Error('Chưa ở trạm nghỉ.')
    if (!run.welfare.some(w => w.checkpointId === cp.id)) throw new Error('Hộ tống chưa gửi nhật ký an sinh của trạm này.')
    cp.doneAt = Date.now()
    return commitTrips(b, by, `Tiếp tục hành trình ${tripId} (chặng ${run.checkpoints.filter(c => c.type === 'rest' && c.doneAt).length + 1})`)
  },

  // Driver: thông quan xong, tải ảnh mộc đỏ
  customsCleared: async (id: string, tripId: string, by: string, stampPhotos: string[]): Promise<Booking> => {
    const b = must(id)
    const t = liveTrip(b, tripId)
    running(t)
    const cp = currentCheckpoint(t)
    if (cp?.type !== 'customs') throw new Error('Chưa tới bước thông quan.')
    if (!stampPhotos.length) throw new Error('Cần ảnh trang mộc đỏ kiểm dịch và cuống ATA Carnet (nếu có).')
    const now = Date.now()
    cp.arrivedAt = now; cp.doneAt = now; cp.stampPhotos = stampPhotos; cp.by = by
    return commitTrips(b, by, `Xe ${tripId} đã thông quan thành công tại cửa khẩu`)
  },

  // Driver: hoàn tất giao ngựa (có ảnh biên bản ký, đã trả bản gốc, Escort đã kiểm tra lần cuối). Đơn Delivered khi mọi xe giao xong.
  completeDelivery: async (id: string, tripId: string, by: string): Promise<Booking> => {
    const b = must(id)
    const t = liveTrip(b, tripId)
    const run = running(t)
    const cp = currentCheckpoint(t)
    if (cp?.type !== 'delivery' || !cp.arrivedAt) throw new Error('Chưa tới điểm giao.')
    if (!run.welfare.some(w => w.checkpointId === cp.id)) throw new Error('Hộ tống chưa kiểm tra thể trạng lần cuối.')
    if (!cp.handoverPhoto) throw new Error('Chưa có ảnh Biên bản Bàn giao & Hoàn tất có chữ ký.')
    const now = Date.now()
    cp.doneAt = now; cp.returnedOriginals = true
    run.deliveredAt = now
    const out = commitTrips(b, by, `Xe ${tripId} hoàn tất giao ngựa`)
    notify(b, ['customer', 'manager'], `Xe ${tripId} đã giao ngựa — đơn ${b.id}`, out.status === 'delivered_pending_settlement' ? 'Mọi xe của đơn đã giao xong.' : 'Còn xe khác của đơn đang trên đường.')
    return out
  },

  // ===== Flow 5: sự cố khẩn cấp =====
  // Driver / Escort: bấm SOS cho một xe đang chạy
  reportIncident: async (id: string, tripId: string, by: string, kind: IncidentKind, photo: string, note: string): Promise<Booking> => {
    const b = must(id)
    if (!LIVE.includes(b.status)) throw new Error('Đơn không còn ở bước vận hành chuyến.')
    const t = tripOf(b, tripId)
    if (!t.run?.startedAt || t.run.deliveredAt) throw new Error('Xe chưa chở ngựa hoặc đã giao xong, không báo sự cố được.')
    if (openIncidentOf(b, tripId)) throw new Error(`Xe ${tripId} đang có sự cố chưa xử lý xong.`)
    if (!photo) throw new Error('Cần chụp ảnh hiện trường sự cố.')
    const inc: Incident = { id: INCIDENT_NO(b), tripId, kind, reportedBy: by, reportedAt: Date.now(), location: incidentLocation(b, t), photo, note: note.trim(), status: 'reported', expenses: [] }
    b.incidents = [...(b.incidents ?? []), inc]
    const out = commitIncident(b, by, `SOS xe ${tripId}: ${INCIDENT_KIND[kind].label.toLowerCase()}`)
    notify(b, ['manager', 'coordinator'], `SOS xe ${tripId} — đơn ${id}`, note.trim() || 'Tài xế / hộ tống báo sự cố khẩn cấp.', { manager: LINK.manager.incidents, coordinator: '/coordinator/incidents' })
    return out
  },
  // Coordinator: lập phương án xử lý trên bản đồ trình Manager. Mỗi nhóm sự cố có một cách xử lý:
  // sức khỏe ngựa → đưa ngựa tới trạm nghỉ gần nhất; xe gặp sự cố → gọi cứu hộ gần chỗ xe + đưa ngựa tới trạm nghỉ; tắc đường → đổi lộ trình.
  planIncident: async (id: string, incidentId: string, by: string, input: { action: IncidentAction; note: string; newEta: number } & Pick<IncidentPlan, 'station' | 'restMinutes' | 'rescue' | 'rescueLine' | 'toStation' | 'detour'>): Promise<Booking> => {
    const b = must(id)
    const inc = incidentById(b, incidentId)
    if (inc.status !== 'reported') throw new Error('Sự cố này không ở bước lập phương án.')
    if (!incidentActionsFor(inc.kind).includes(input.action)) throw new Error('Phương án không phù hợp với nhóm sự cố này.')
    if (input.newEta <= Date.now()) throw new Error('ETA mới phải sau thời điểm hiện tại.')
    if (input.action !== 'reroute') {
      if (!input.station || !TRANSIT_STATIONS.some(s => s.name === input.station)) throw new Error('Chọn trạm nghỉ trong danh mục để đưa ngựa tới.')
      if (!((input.restMinutes ?? 0) >= MIN_REST_MINUTES)) throw new Error(`Ngựa nghỉ tại trạm tối thiểu ${MIN_REST_MINUTES} phút.`)
    }
    if (input.action === 'rescue_and_station' && !input.rescue) throw new Error('Chọn điểm cứu hộ gần chỗ xe gặp sự cố.')
    if (input.action === 'reroute' && !input.detour) throw new Error('Chọn lộ trình mới để tránh tắc nghẽn.')
    const { action, newEta, station, restMinutes, rescue, rescueLine, toStation, detour } = input
    inc.plan = { action, newEta, note: input.note.trim(), at: Date.now(), by, ...(action !== 'reroute' ? { station, restMinutes, toStation } : {}), ...(action === 'rescue_and_station' ? { rescue, rescueLine } : {}), ...(action === 'reroute' ? { detour } : {}) }
    inc.status = 'pending_approval'
    delete inc.rejection
    const out = commitIncident(b, by, `Lập phương án sự cố ${incidentId} trên bản đồ: ${INCIDENT_ACTION[action]}, trình Quản lý duyệt`)
    notify(b, ['manager'], `Phương án sự cố đơn ${id} chờ duyệt`, `${INCIDENT_ACTION[action]}.`, { manager: LINK.manager.incidents })
    return out
  },
  // Manager: trả phương án về
  rejectIncident: async (id: string, incidentId: string, by: string, reason: string): Promise<Booking> => {
    const b = must(id)
    const inc = incidentById(b, incidentId)
    if (inc.status !== 'pending_approval') throw new Error('Sự cố này không ở bước chờ duyệt.')
    if (!reason.trim()) throw new Error('Cần ghi lý do trả về.')
    inc.status = 'reported'
    inc.rejection = { reason: reason.trim(), at: Date.now(), by }
    const out = commitIncident(b, by, `Trả phương án sự cố ${incidentId} về: ${reason.trim()}`)
    notify(b, ['coordinator'], `Phương án sự cố đơn ${id} bị trả về`, reason.trim(), { coordinator: '/coordinator/incidents' })
    return out
  },
  // Manager: duyệt phương án khẩn cấp
  approveIncident: async (id: string, incidentId: string, by: string): Promise<Booking> => {
    const b = must(id)
    const inc = incidentById(b, incidentId)
    if (inc.status !== 'pending_approval') throw new Error('Sự cố này không ở bước chờ duyệt.')
    inc.approval = { at: Date.now(), by }
    inc.status = 'active'
    const out = commitIncident(b, by, `Duyệt phương án khẩn cấp ${incidentId}`)
    notify(b, ['customer', 'coordinator'], `Xe ${inc.tripId} đang xử lý sự cố — đơn ${id}`, 'Quản lý đã duyệt phương án. Ngựa đang được đội ngũ chăm sóc, ETA mới đã cập nhật.', { coordinator: '/coordinator/incidents' })
    await notifyCrew(b, `Phương án sự cố đã duyệt — ${id}`, inc.plan ? `Thực hiện: ${INCIDENT_ACTION[inc.plan.action]}${inc.plan.station ? ` (${inc.plan.station})` : ''}.` : 'Phương án khẩn cấp đã duyệt.', [inc.tripId])
    return out
  },
  // Driver / Escort: tải chi phí tại chỗ, ảnh chứng từ trước, số tiền sau (Evidence-First)
  addIncidentExpense: async (id: string, incidentId: string, by: string, input: { category: ExpenseCategory; label: string; photo: string; amount: number }): Promise<Booking> => {
    const b = must(id)
    const inc = incidentById(b, incidentId)
    if (inc.status === 'resolved') throw new Error('Sự cố đã xử lý xong, không thêm chi phí được.')
    if (inc.status !== 'active') throw new Error('Chỉ kê chi phí khi phương án khẩn cấp đã được duyệt.')
    if (!input.photo) throw new Error('Cần ảnh chứng từ trước khi nhập số tiền.')
    if (!(input.amount > 0)) throw new Error('Cần nhập số tiền trên hóa đơn.')
    inc.expenses = [...inc.expenses, { id: `EXP-${b.id.slice(-4)}-${(b.incidents ?? []).reduce((n, i) => n + i.expenses.length, 0) + 1}`, category: input.category, label: input.label.trim() || EXPENSE_CATEGORY[input.category], photo: input.photo, amount: Math.round(input.amount), payer: defaultPayer(inc.kind, input.category), at: Date.now(), by }]
    return commitIncident(b, by, `Chi phí sự cố ${incidentId}: ${EXPENSE_CATEGORY[input.category]} ${formatVND(input.amount)}`)
  },
  // Escort: xác nhận ngựa đủ sức đi tiếp (sự cố sức khỏe)
  confirmFit: async (id: string, incidentId: string, by: string): Promise<Booking> => {
    const b = must(id)
    const inc = incidentById(b, incidentId)
    if (inc.status !== 'active') throw new Error('Phương án khẩn cấp chưa được duyệt hoặc đã xong.')
    if (!needsFitCheck(inc.kind)) throw new Error('Sự cố tắc nghẽn không cần xác nhận ngựa đủ sức.')
    inc.fitConfirmedAt = Date.now()
    return commitIncident(b, by, `Hộ tống xác nhận ngựa đủ sức đi tiếp (${incidentId})`)
  },
  // Driver: tiếp tục hành trình chính, sự cố xử lý xong
  resumeJourney: async (id: string, incidentId: string, by: string): Promise<Booking> => {
    const b = must(id)
    const inc = incidentById(b, incidentId)
    if (inc.status !== 'active') throw new Error('Phương án khẩn cấp chưa được duyệt hoặc đã xong.')
    if (needsFitCheck(inc.kind) && !inc.fitConfirmedAt) throw new Error('Hộ tống chưa xác nhận ngựa đủ sức đi tiếp.')
    inc.status = 'resolved'
    inc.resolvedAt = Date.now()
    const out = commitIncident(b, by, `Tiếp tục hành trình chính xe ${inc.tripId}, sự cố ${incidentId} đã xử lý xong`)
    notify(b, ['customer', 'manager', 'coordinator'], `Xe ${inc.tripId} tiếp tục hành trình — đơn ${id}`, 'Sự cố đã xử lý xong, xe tiếp tục đi.', { coordinator: '/coordinator/monitoring', manager: LINK.manager.incidents })
    return out
  },

  // ===== Flow 6: quyết toán =====
  // Driver: gửi bảng kê chi phí sau giao. Không có chi phí thì phát hành luôn bảng quyết toán 0 đồng.
  submitExpenses: async (id: string, by: string): Promise<Booking> => {
    const b = must(id)
    if (b.status !== 'delivered_pending_settlement') throw new Error('Đơn chưa giao xong hoặc đã gửi bảng kê.')
    const noExpenses = !(b.incidents ?? []).some(i => i.expenses.length)
    if (noExpenses) return issueSettlementOf(b, 'Hệ thống', 'Không có chi phí phát sinh: hệ thống phát hành bảng quyết toán 0 đồng')
    const out = structuredClone(store.update(id, { status: 'expenses_submitted', expensesSubmittedAt: Date.now(), history: log(b, by, 'Gửi bảng kê chi phí kèm chứng từ') }))
    notify(b, ['manager'], `Bảng kê chi phí đơn ${id} chờ đối soát`, 'Tài xế đã gửi chi phí kèm chứng từ.', { manager: LINK.manager.overview })
    return out
  },
  // Manager: đối soát, chọn bên chịu từng khoản (theo chính sách 11.5), phát hành bảng quyết toán
  issueSettlement: async (id: string, by: string, payers: Record<string, Payer> = {}): Promise<Booking> => {
    const b = must(id)
    if (b.status !== 'expenses_submitted') throw new Error('Đơn chưa ở bước đối soát chi phí.')
    b.incidents?.forEach(i => i.expenses.forEach(e => { if (payers[e.id]) e.payer = payers[e.id] }))
    return issueSettlementOf(b, by, 'Đối soát chi phí và phát hành bảng quyết toán')
  },
}
