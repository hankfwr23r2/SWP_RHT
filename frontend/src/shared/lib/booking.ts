// Hàm thuần của luồng đặt đơn → duyệt báo giá → đặt cọc (Flow 1). Khớp docs/PRD.md mục 2, 10, 11.
import {
  BORDER_WINDOW, CLEARANCE_DOC, CLEARANCE_FEE, CLASS_FACTOR, CREW_FEE_PER_DAY, DEPOSIT_RATE, DOCS_CUTOFF_HOUR, FUEL_BOT_PER_KM, FUEL_BUFFER_RATE, HORSE_DOC_TYPES, MARGIN_RATE,
  BREED_INSURED_VALUE, INSURANCE_RATE_BOOKING, DELAY_ALERT_MINUTES, HOME_BASE, RETURN_EARLIEST_RATIO, MAX_CONTINUOUS_HOURS, MIN_REST_MINUTES, QUOTE_VALID_HOURS, TARGET_LEG_HOURS, SINGLE_STALL_FEE, FEED_PACKAGE, WATER_PLAN, VEHICLE_CLASS, EXPENSE_CATEGORY, type BookingStatus, type ClearanceDocType, type ExpenseCategory, type IncidentAction, type IncidentKind, type Payer, type HorseDocType, type VehicleClass,
} from '../config/booking-rules'
import { DAY, HOUR, MIN_LEAD_DAYS } from '../config/business-rules'
import { COUNTRIES, COUNTRY_LOCATIONS, GATES, PLACES, TRANSIT_STATIONS, type GeoPoint, type Gate, type CountryCode } from '../config/network'
import { FAMOUS_HORSE_VENUES, PROVINCES_DATA } from '../config/administrative'
import { AVG_SPEED_KMH, BORDER_HOURS, DRIVE_HOURS_PER_DAY } from '../config/public-pricing'
import type { Vehicle } from '../types/fleet'
import type { StaffMember } from '../types/staff'
import type { Adjustment, Booking, BookingHorse, Checkpoint, Clearance, ClearanceItem, HorseProfile, PlaceRef, Quote, QuoteLine, SettlementItem, RestStop, HomeReturn, RouteLeg, RoutePlan, TripRun, VehicleTrip, WelfareLog } from '../types/booking'
import { atHour, dayKey, startOfDay } from './dates'
import { haversineKm, roadKm, truckCost } from './pricing'

// ===== Ngày khởi hành =====
// Ngày sớm nhất được chọn: hôm nay + 30 ngày (00:00)
export function earliestDeparture(now = Date.now()) {
  const d = startOfDay(now)
  d.setDate(d.getDate() + MIN_LEAD_DAYS)
  return d.getTime()
}
export const toIsoDay = (t: number) => dayKey(new Date(t))
export const fromIsoDay = (s: string) => { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d).getTime() }
export const isDepartureAllowed = (departAt: number, now = Date.now()) => departAt >= earliestDeparture(now)
// 18:00 ngày D-1: mốc cảnh báo giấy tờ chưa xong
export const docsDueAt = (departAt: number) => atHour(new Date(departAt - DAY), DOCS_CUTOFF_HOUR)

// ===== Hồ sơ ngựa =====
// Sẵn sàng khi đã có đủ 3 giấy tờ ngựa.
export function horseReadiness(h: HorseProfile) {
  const missing: HorseDocType[] = HORSE_DOC_TYPES.filter(t => !h.docs[t])
  return { ok: !missing.length, missing }
}

export const ageOf = (h: Pick<HorseProfile, 'birthYear'>, now = Date.now()) => new Date(now).getFullYear() - h.birthYear

// ===== Hạng xe =====
export const vehicleClassOf = (capacity: number): VehicleClass => (capacity <= VEHICLE_CLASS.light.maxStalls ? 'light' : capacity <= VEHICLE_CLASS.medium.maxStalls ? 'medium' : 'heavy')
// Hạng xe nhỏ nhất đủ chỗ cho số ngựa
export const classForHorses = (n: number): VehicleClass => vehicleClassOf(n)

// ===== Tuyến, quãng đường =====
// Điểm nhận / giao khi đặt đơn; bảng giá công khai còn nhận cả các thành phố trong PLACES
export const findLocation = (id: string, country?: string) => {
  const all = [...Object.values(COUNTRY_LOCATIONS).flat(), ...PLACES]
  const exact = all.find(l => l.id === id)
  if (exact) return exact
  const clean = id.toLowerCase().trim()
  const matched = all.find(l => l.name.toLowerCase().includes(clean) || clean.includes(l.name.toLowerCase().split(' — ')[0].trim()))
  if (matched) return matched

  // Kiểm tra danh mục trang trại ngựa nổi tiếng
  const venue = FAMOUS_HORSE_VENUES.find(v => v.id === id || v.name.toLowerCase().includes(clean) || clean.includes(v.name.toLowerCase()))
  if (venue) return { id, name: venue.name, lat: venue.lat, lng: venue.lng }

  // Kiểm tra địa chỉ 2 cấp (Tỉnh / Thành và Xã / Phường)
  for (const prov of PROVINCES_DATA) {
    const provMatched = clean.includes(prov.name.toLowerCase()) || clean.includes(prov.code.toLowerCase())
    if (provMatched) {
      for (const w of prov.wards) {
        const wClean = w.name.toLowerCase().replace(/^(xã|phường|sangkat|ban|krong)\s+/i, '').split('(')[0].trim()
        if (clean.includes(wClean)) {
          return { id, name: id, lat: w.lat, lng: w.lng }
        }
      }
      return { id, name: id, lat: prov.lat, lng: prov.lng }
    }
  }

  if (country && COUNTRY_LOCATIONS[country as CountryCode]?.length) {
    return COUNTRY_LOCATIONS[country as CountryCode][0]
  }
  return undefined
}

export function routeKm(origin: PlaceRef, dest: PlaceRef, gate?: string) {
  const a = findLocation(origin.id, origin.country)
  const b = findLocation(dest.id, dest.country)
  if (!a || !b) return 0
  const g = gate ? GATES.find(x => x.name === gate) : undefined
  return roadKm(g ? haversineKm(a, g) + haversineKm(g, b) : haversineKm(a, b))
}
// Cửa khẩu Coordinator được chọn: của nước đến (hoặc nước đi nếu chiều về Việt Nam). Tuyến nội địa không có.
export function gatesFor(origin: PlaceRef, dest: PlaceRef): Gate[] {
  const country = dest.country !== 'VN' ? dest.country : origin.country
  return country === 'VN' ? [] : GATES.filter(g => g.country === country)
}
// Gợi ý cửa khẩu có tổng quãng đường ngắn nhất
export function suggestGate(origin: PlaceRef, dest: PlaceRef): string | undefined {
  const gates = gatesFor(origin, dest)
  return [...gates].sort((a, b) => routeKm(origin, dest, a.name) - routeKm(origin, dest, b.name))[0]?.name
}

// Gợi ý cửa khẩu kèm thông tin so sánh số km tiết kiệm được so với phương án khác
export function suggestGateWithSavings(origin: PlaceRef, dest: PlaceRef): { gate: string; km: number; savingsKm?: number } | undefined {
  const gates = gatesFor(origin, dest)
  if (!gates.length) return undefined
  const sorted = [...gates]
    .map(g => ({ gate: g.name, km: routeKm(origin, dest, g.name) }))
    .sort((a, b) => a.km - b.km)
  const best = sorted[0]
  const second = sorted[1]
  const savingsKm = second && second.km > best.km ? Math.round(second.km - best.km) : undefined
  return { gate: best.gate, km: Math.round(best.km), savingsKm }
}
// Gợi ý trạm nghỉ: chia đều đường đi (điểm đón → cửa khẩu → điểm trả), mỗi phân đoạn chỉ chọn trạm thuộc quốc gia tương ứng
export function suggestTransitStations(origin: PlaceRef, dest: PlaceRef, gate: string | undefined, count: number): string[] {
  const a = findLocation(origin.id, origin.country), b = findLocation(dest.id, dest.country)
  if (!a || !b || count <= 0) return []
  const g = gate ? GATES.find(x => x.name === gate) : undefined
  const pts = g ? [a, g, b] : [a, b]
  const seg = pts.slice(1).map((p, i) => haversineKm(pts[i], p))
  const total = seg.reduce((t, x) => t + x, 0)
  if (total <= 0) return []

  // Phân định quốc gia cho từng phân đoạn:
  // - Tuyến nội địa: tất cả đều là VN
  // - Tuyến quốc tế: đoạn 0 (đến cửa khẩu) là origin.country (thường VN), đoạn 1 (sau cửa khẩu) là dest.country
  const segCountries: CountryCode[] = g
    ? [(origin.country as CountryCode) || 'VN', (dest.country as CountryCode) || (g.country as CountryCode)]
    : [(origin.country as CountryCode) || 'VN']

  const used = new Set<string>()
  const pickedStations: (typeof TRANSIT_STATIONS)[number][] = []

  for (let i = 0; i < count; i++) {
    let d = (total * (i + 1)) / (count + 1)
    let k = 0
    while (k < seg.length - 1 && d > seg[k]) { d -= seg[k]; k++ }
    const f = seg[k] ? d / seg[k] : 0
    const at = {
      lat: pts[k].lat + (pts[k + 1].lat - pts[k].lat) * f,
      lng: pts[k].lng + (pts[k + 1].lng - pts[k].lng) * f,
    }
    const targetCountry = segCountries[k] || 'VN'

    // Lọc các trạm thuộc đúng quốc gia của phân đoạn và chưa sử dụng
    let pool = TRANSIT_STATIONS.filter(s => s.country === targetCountry && !used.has(s.name))
    if (!pool.length) {
      // Dự phòng nếu hết trạm nước đích: lấy trạm chưa dùng bất kỳ
      pool = TRANSIT_STATIONS.filter(s => !used.has(s.name))
    }
    if (!pool.length) break

    const best = [...pool].sort((x, y) => haversineKm(at, x) - haversineKm(at, y))[0]
    used.add(best.name)
    pickedStations.push(best)
  }

  // Tự động sắp xếp trạm theo đúng lộ trình di chuyển từ điểm đón đến điểm trả
  const ordered = routeOutline(a, g, b, pickedStations)
  return ordered.stations.map(s => s.name)
}
// Xếp các trạm theo thứ tự xe đi qua: chiếu từng trạm lên đường đi điểm đón → (cửa khẩu) → điểm trả,
// rồi sắp theo quãng đường dọc đường đi. Trả về trạm đã xếp và các điểm để vẽ đường (có cửa khẩu chen đúng chỗ).
export function routeOutline<T extends GeoPoint>(origin: GeoPoint, gate: GeoPoint | undefined, dest: GeoPoint, stations: T[]): { stations: T[]; path: GeoPoint[] } {
  const pts = gate ? [origin, gate, dest] : [origin, dest]
  const kx = Math.cos((origin.lat * Math.PI) / 180) * 111, ky = 111 // km trên mỗi độ, đủ chính xác cho vài trăm km
  const flat = (p: GeoPoint) => ({ x: (p.lng - origin.lng) * kx, y: (p.lat - origin.lat) * ky })
  const seg = pts.slice(1).map((p, i) => { const a = flat(pts[i]), b = flat(p); return { a, b, len: Math.hypot(b.x - a.x, b.y - a.y) } })
  const progress = (s: GeoPoint) => {
    const q = flat(s)
    let best = { d: Infinity, at: 0 }, before = 0
    seg.forEach(({ a, b, len }) => {
      const t = len ? Math.max(0, Math.min(1, ((q.x - a.x) * (b.x - a.x) + (q.y - a.y) * (b.y - a.y)) / (len * len))) : 0
      const d = Math.hypot(q.x - (a.x + (b.x - a.x) * t), q.y - (a.y + (b.y - a.y) * t))
      if (d < best.d) best = { d, at: before + t * len }
      before += len
    })
    return best.at
  }
  const ordered = stations.map(s => ({ s, at: progress(s) })).sort((x, y) => x.at - y.at)
  const gateAt = gate ? seg[0].len : Infinity
  const path: GeoPoint[] = [origin, ...ordered.filter(x => x.at < gateAt).map(x => x.s), ...(gate ? [gate] : []), ...ordered.filter(x => x.at >= gateAt).map(x => x.s), dest]
  return { stations: ordered.map(x => x.s), path }
}

export const travelHours = (km: number, international: boolean) => km / AVG_SPEED_KMH + (international ? BORDER_HOURS : 0)
export const tripDays = (km: number, international: boolean) => Math.max(1, Math.ceil(travelHours(km, international) / DRIVE_HOURS_PER_DAY))

// ===== Báo giá (PRD mục 2.5) =====
const roundK = (n: number) => Math.round(n / 1000) * 1000
const insuredValue = (breed: string) => BREED_INSURED_VALUE[breed] ?? BREED_INSURED_VALUE['Khác']
export const insuranceFee = (breed: string) => roundK(insuredValue(breed) * INSURANCE_RATE_BOOKING)

type QuoteInput = Pick<Booking, 'type' | 'origin' | 'dest' | 'gate' | 'horses'>

export function quoteLines(b: QuoteInput, vehicles: Pick<Vehicle, 'capacity'>[]) {
  const international = b.type === 'international'
  const km = routeKm(b.origin, b.dest, b.gate)
  const days = tripDays(km, international)
  const m = 1 + MARGIN_RATE
  const many = vehicles.length > 1
  const lines: QuoteLine[] = []
  vehicles.forEach((v, i) => {
    const cls = vehicleClassOf(v.capacity)
    const tag = many ? ` (xe ${i + 1}/${vehicles.length})` : ''
    lines.push(
      { label: `Cước vận chuyển nguyên chuyến${tag}`, detail: `Xe ${VEHICLE_CLASS[cls].label} (${VEHICLE_CLASS[cls].stalls}) · ${km} km`, amount: roundK(truckCost(km, false) * CLASS_FACTOR[cls] * m) },
      { label: `Nhân sự kỹ thuật, 01 tài xế + 01 hộ tống${tag}`, detail: `${days} ngày`, amount: roundK(days * CREW_FEE_PER_DAY * m) },
      { label: `Nhiên liệu và BOT${tag}`, detail: `Ước tính theo lộ trình ${km} km, đã gồm dự phòng`, amount: roundK(km * FUEL_BOT_PER_KM * (1 + FUEL_BUFFER_RATE) * m) },
    )
  })
  const singles = b.horses.filter(h => h.stall === 'single').length
  if (singles) lines.push({ label: 'Khoang đơn mở rộng', detail: `${singles} ngựa`, amount: singles * SINGLE_STALL_FEE })
  const feed = b.horses.filter(h => FEED_PACKAGE[h.feedPackage].fee > 0)
  if (feed.length) lines.push({ label: 'Gói thức ăn', detail: `${feed.length} ngựa chọn gói trả phí`, amount: feed.reduce((t, h) => t + FEED_PACKAGE[h.feedPackage].fee, 0) })
  const water = b.horses.filter(h => WATER_PLAN[h.waterPlan].fee > 0)
  if (water.length) lines.push({ label: 'Cữ nước tăng cường', detail: `${water.length} ngựa chọn cữ dày hơn mặc định`, amount: water.reduce((t, h) => t + WATER_PLAN[h.waterPlan].fee, 0) })
  lines.push({ label: 'Thủ tục kiểm dịch và hải quan', detail: international ? 'Nhà xe làm trọn gói' : 'Nhà xe làm giấy kiểm dịch trong nước', amount: international ? CLEARANCE_FEE.international : CLEARANCE_FEE.domestic })
  const insured = b.horses.filter(h => h.insurance.opted)
  if (insured.length) lines.push({ label: 'Bảo hiểm Động vật Sống', detail: `${insured.length} ngựa mua bảo hiểm`, amount: insured.reduce((t, h) => t + insuranceFee(h.breed), 0) })
  return { lines, km, days }
}

// ===== Ước tính chi phí cho khách (Tra cứu cước / Bảng giá) =====
// Số xe ước tính theo số ngựa: ít xe nhất theo hạng xe (9, 6, 2 ngăn). Khi đặt thật, Coordinator tự chọn xe theo đội xe rảnh.
export function capacitiesFor(n: number): number[] {
  const out: number[] = []
  let left = n
  const { light, medium, heavy } = VEHICLE_CLASS
  while (left > heavy.maxStalls) { out.push(heavy.maxStalls); left -= heavy.maxStalls }
  if (left > 0) out.push(left <= light.maxStalls ? light.maxStalls : left <= medium.maxStalls ? medium.maxStalls : heavy.maxStalls)
  return out
}

export interface EstimateHorse { breed: string; single: boolean; insured: boolean }
// Dùng đúng công thức báo giá thật (quoteLines, finalizeQuote); cửa khẩu lấy theo gợi ý tối ưu của nhà xe
export function estimateQuote(input: { origin: PlaceRef; dest: PlaceRef; horses: EstimateHorse[] }) {
  const gate = suggestGate(input.origin, input.dest)
  const vehicles = capacitiesFor(input.horses.length)
  const horses = input.horses.map((h, i): BookingHorse => ({
    horseId: `E${i}`, name: '', microchip: '', breed: h.breed, sex: 'gelding', stall: h.single ? 'single' : 'standard', feedPackage: 'basic', waterPlan: 'every_3h', insurance: { opted: h.insured },
  }))
  const { lines, km, days } = quoteLines({ type: gate ? 'international' : 'domestic', origin: input.origin, dest: input.dest, gate, horses }, vehicles.map(capacity => ({ capacity })))
  const q = finalizeQuote(lines, [], 0, '')
  return { lines, km, days, gate, vehicles, hours: travelHours(km, !!gate), total: q.total, deposit: q.deposit, balance: q.balance }
}

export function finalizeQuote(lines: QuoteLine[], adjustments: Adjustment[], sentAt: number, sentBy: string): Quote {
  const subtotal = lines.reduce((t, l) => t + l.amount, 0)
  const total = Math.max(0, subtotal + adjustments.reduce((t, a) => t + a.amount, 0))
  const deposit = roundK(total * DEPOSIT_RATE)
  return { lines, adjustments, subtotal, total, deposit, balance: total - deposit, sentAt, expiresAt: sentAt + QUOTE_VALID_HOURS * HOUR, sentBy }
}

export const isQuoteExpired = (b: Pick<Booking, 'status' | 'quote'>, now = Date.now()) => b.status === 'awaiting_payment' && !!b.quote && now > b.quote.expiresAt

// ===== Cổng chuyển bước =====
// Cả duyệt hồ sơ ngựa và phương án xe + lộ trình được duyệt thì đơn mới sang Manager duyệt báo giá
export const reviewDone = (b: Pick<Booking, 'medical' | 'plan'>) => b.medical?.status === 'approved' && !!b.plan

// ===== Xe, nhân sự =====
// Đơn còn giữ xe và nhân sự: từ lúc thẩm định tới khi giao xong
export const HOLDING: Booking['status'][] = ['under_review', 'pending_commercial', 'awaiting_payment', 'waybill_issued', 'clearance_in_progress', 'clearance_done', 'ready_for_pickup', 'en_route_to_pickup', 'in_transit', 'incident_reported', 'pending_emergency_approval', 'emergency_plan_active']
// Xe đang giữ cho đơn khác có ngày đi cách ngày này dưới 3 ngày. Tài xế và Escort theo kiểu giao việc (assign task):
// đã được giao vào một chuyến của đơn còn đang giữ nhân sự thì không chọn được nữa, bất kể ngày đi, cho tới khi đơn giao xong.
export function busyResources(all: Booking[], departAt: number, exceptId?: string) {
  const vehicles = new Set<string>()
  const crew = new Set<string>()
  all.filter(o => o.id !== exceptId && HOLDING.includes(o.status)).forEach(o => (o.trips ?? []).forEach(t => {
    if (Math.abs(o.departAt - departAt) < 3 * DAY) vehicles.add(t.vehicleId)
    if (t.driverId) crew.add(t.driverId)
    if (t.escortId) crew.add(t.escortId)
  }))
  // Đang chạy về trụ sở sau khi giao ngựa ở nước ngoài: chưa chọn được cho đơn mới
  returningTrips(all.filter(o => o.id !== exceptId)).forEach(({ t }) => { crew.add(t.driverId); crew.add(t.escortId) })
  return { vehicles, crew }
}

// ===== Chọn xe, tài xế, hộ tống (PRD mục 10.2): Coordinator chọn xe, sau đó Manager chọn tài xế và hộ tống; hệ thống chỉ khóa người và xe trùng lịch =====
// Xe đã có đủ tài xế và hộ tống chưa (Manager chọn lúc duyệt báo giá)
export const crewAssigned = (t: Pick<VehicleTrip, 'driverId' | 'escortId'>) => !!t.driverId && !!t.escortId
// So sánh mã xe linh hoạt (chuẩn hóa VH-XXX hoặc số ID)
export const sameVehicleId = (a?: string, b?: string): boolean => {
  if (!a || !b) return false
  if (a.toLowerCase() === b.toLowerCase()) return true
  const numA = a.replace(/\D/g, '')
  const numB = b.replace(/\D/g, '')
  return numA !== '' && numA === numB
}
// Lịch của một xe / tài xế / hộ tống: các đơn khác đang giữ họ (kèm ngày đi và tuyến), để Coordinator biết họ bận gì
export interface Booked {
  order: string; departAt: number; route: string; trip: string
  countries: string // "Việt Nam → Lào": chiều của đơn, để biết người đang làm việc ở nước nào
  active?: boolean // đơn đang thực tế lăn bánh ngoài đường
  returning?: { from: string; eta: number; late: boolean } // đã giao ngựa ở nước ngoài, đang về trụ sở
}
export interface Schedules { vehicles: Map<string, Booked[]>; drivers: Map<string, Booked[]>; escorts: Map<string, Booked[]> }
export function schedulesOf(all: Booking[], exceptId: string, now = Date.now()): Schedules {
  const out: Schedules = { vehicles: new Map(), drivers: new Map(), escorts: new Map() }
  const put = (m: Map<string, Booked[]>, id: string, x: Booked) => { if (id) m.set(id, [...(m.get(id) ?? []), x]) }
  const activeStatuses: Booking['status'][] = ['en_route_to_pickup', 'ready_for_pickup', 'in_transit', 'incident_reported', 'pending_emergency_approval', 'emergency_plan_active']
  const bookedOf = (o: Booking, t: VehicleTrip): Booked => ({
    order: o.id, departAt: o.departAt, route: `${o.origin.name.split(' — ')[0]} → ${o.dest.name.split(' — ')[0]}`, trip: t.tripId,
    countries: `${COUNTRIES[o.origin.country].name} → ${COUNTRIES[o.dest.country].name}`,
    active: activeStatuses.includes(o.status),
    returning: t.homeReturn && !t.homeReturn.backAt ? { from: t.homeReturn.fromPlace, eta: t.homeReturn.eta, late: returnLate(t, now) } : undefined,
  })
  all.filter(o => o.id !== exceptId && HOLDING.includes(o.status)).forEach(o => (o.trips ?? []).forEach(t => {
    const x = bookedOf(o, t)
    put(out.vehicles, t.vehicleId, x); put(out.drivers, t.driverId, x); put(out.escorts, t.escortId, x)
  }))
  // Đơn đã giao xong (không còn giữ xe) nhưng Driver và Escort chưa về trụ sở: vẫn bị giữ, xe thì không
  returningTrips(all.filter(o => o.id !== exceptId && !HOLDING.includes(o.status))).forEach(({ b, t }) => {
    const x = bookedOf(b, t)
    put(out.drivers, t.driverId, x); put(out.escorts, t.escortId, x)
  })
  return out
}
// ===== Về trụ sở sau khi giao ngựa ở nước ngoài (PRD mục 1.4, 10.2) =====
// Giờ về dự kiến: giờ giao xong cộng thời gian lái từ điểm giao về trụ sở (qua cửa khẩu). Chỉ tính khi điểm giao ở nước ngoài.
export function planHomeReturn(b: Pick<Booking, 'dest'>, deliveredAt: number): HomeReturn | undefined {
  if (b.dest.country === 'VN') return undefined
  const to = findLocation(b.dest.id)
  const driveMs = to ? travelHours(roadKm(haversineKm(to, HOME_BASE)), true) * HOUR : BORDER_HOURS * HOUR
  return { fromPlace: `${b.dest.name.split(' — ')[0]} (${COUNTRIES[b.dest.country].name})`, eta: Math.round(deliveredAt + driveMs), earliestAt: Math.round(deliveredAt + driveMs * RETURN_EARLIEST_RATIO) }
}
// Chuyến mà Driver và Escort đang chạy về trụ sở
export const returningTrips = (all: Booking[]) => all.flatMap(b => (b.trips ?? []).filter(t => t.homeReturn && !t.homeReturn.backAt).map(t => ({ b, t })))
// Quá giờ về dự kiến từ 30 phút mà chưa xác nhận: cờ "Về trễ" (cùng ngưỡng Delayed Check-in)
export const returnLate = (t: Pick<VehicleTrip, 'homeReturn'>, now = Date.now()) => !!t.homeReturn && !t.homeReturn.backAt && now - t.homeReturn.eta >= DELAY_ALERT_MINUTES * 60_000

// Xe trùng lịch: đơn khác của xe có ngày đi cách ngày đi này dưới 3 ngày (cùng quy tắc với busyResources)
export const clashOf = (booked: Booked[] | undefined, departAt: number) => (booked ?? []).find(x => Math.abs(x.departAt - departAt) < 3 * DAY)

// So sánh linh hoạt mã nhân viên (hỗ trợ DR-01, TX-01, NV-01, ES-01 hoặc ID số)
export const sameStaffId = (a?: string, b?: string): boolean => {
  if (!a || !b) return false
  if (a.toLowerCase() === b.toLowerCase()) return true
  const cleanA = a.replace(/^(TX|DR|NV|ES|HT)-/i, '').replace(/\D/g, '')
  const cleanB = b.replace(/^(TX|DR|NV|ES|HT)-/i, '').replace(/\D/g, '')
  return cleanA !== '' && cleanA === cleanB
}

// Tìm lịch của nhân sự trong Map bất kể định dạng mã hoặc tên
export function findCrewBooked(schedules: Map<string, Booked[]>, id?: string, name?: string): Booked[] {
  if (!id && !name) return []
  if (id && schedules.has(id)) return schedules.get(id)!
  for (const [k, v] of schedules.entries()) {
    if (id && sameStaffId(k, id)) return v
    if (name && k.toLowerCase() === name.toLowerCase()) return v
  }
  return []
}

// Trùng lịch của nhân sự:
// 1. Nếu đang trên đường về trụ sở mà giờ về sau giờ đơn mới khởi hành -> Xung đột
// 2. Nếu đơn đang chạy thực tế trên đường và đơn mới khởi hành trong 2 ngày tới -> Xung đột
// 3. Nếu trùng lịch theo khoảng thời gian chuyến đi (quốc tế 5 ngày, nội địa 3 ngày) -> Xung đột
// 4. Nếu đơn mới cách xa hơn nhiều ngày -> RẢNH!
export function crewClashOf(booked: Booked[] | undefined, departAt: number, now = Date.now()): Booked | undefined {
  if (!booked?.length) return undefined
  return booked.find(x => {
    if (x.returning && x.returning.eta && departAt <= x.returning.eta + 12 * HOUR) return true
    if (x.active && departAt <= now + 2 * DAY) return true
    const isInter = x.countries.includes('Campuchia') || x.countries.includes('Lào')
    const days = isInter ? 5 * DAY : 3 * DAY
    return Math.abs(x.departAt - departAt) < days
  })
}

export const tripIdFor = (bookingId: string, index: number) => `TRP-${bookingId.slice(-4)}-${index}`

// Nhân viên đang hoạt động (không nghỉ), có thể giao việc. Manager chỉ cần biết điều này, không xem số đơn họ đang làm.
export function activeStaff(staff: StaffMember[], task: 'specialist' | 'coordinator') {
  const role = task === 'specialist' ? 'inspector' : 'coordinator'
  return staff.filter(s => s.role === role && s.status === 'working').sort((a, z) => a.id.localeCompare(z.id))
}

// ===== Mã đơn =====
export function nextBookingId(all: Pick<Booking, 'id'>[], year = new Date().getFullYear()) {
  const prefix = `ORD-${year}-`
  const max = Math.max(0, ...all.filter(o => o.id.startsWith(prefix)).map(o => Number(o.id.slice(prefix.length)) || 0))
  return `${prefix}${String(max + 1).padStart(4, '0')}`
}

// ===== Giấy tờ do Specialist làm (Flow 2, PRD mục 3) =====
export const defaultClearanceItems = (type: Booking['type']): ClearanceItem[] =>
  (Object.keys(CLEARANCE_DOC) as ClearanceDocType[])
    .filter(t => CLEARANCE_DOC[t].base && (type === 'international' || !CLEARANCE_DOC[t].international))
    .map(t => ({ type: t, status: 'todo', note: '', photos: [] }))
export const clearanceProgress = (c: Clearance) => ({ done: c.items.filter(i => i.status === 'done').length, total: c.items.length })

// Lý do chưa hoàn tất được (null = được)
export function canCompleteClearance(b: Pick<Booking, 'type' | 'horses' | 'clearance'>): string | null {
  const c = b.clearance
  if (!c) return 'Đơn chưa có danh sách giấy tờ.'
  const { done, total } = clearanceProgress(c)
  if (done < total) return `Còn ${total - done} hạng mục giấy tờ chưa xong.`
  return null
}

// Quá 18:00 ngày D-1 mà giấy tờ chưa xong: cảnh báo nội bộ cho Manager, không tính phí khách (PRD mục 3.4)
export const isClearanceOverdue = (b: Pick<Booking, 'status' | 'departAt'>, now = Date.now()) =>
  (b.status === 'waybill_issued' || b.status === 'clearance_in_progress') && now > docsDueAt(b.departAt)

// ===== Nhóm đơn cho khách (Đơn của tôi) =====
export type OrderGroup = 'new' | 'approved' | 'supplement' | 'moving' | 'settle' | 'closed' | 'done'
// Thứ tự hiển thị trên thanh dọc bên trái
export const ORDER_GROUPS: Record<OrderGroup, { label: string; icon: string; hint: string }> = {
  new: { label: 'Vừa đặt', icon: 'fa-paper-plane', hint: 'Đã gửi, đang chờ tiếp nhận, thẩm định và lập báo giá' },
  approved: { label: 'Đã duyệt', icon: 'fa-circle-check', hint: 'Đã có báo giá, đặt cọc, làm giấy tờ, chờ xe đón ngựa' },
  supplement: { label: 'Yêu cầu bổ sung', icon: 'fa-file-circle-exclamation', hint: 'Kiểm dịch viên cần bạn bổ sung hồ sơ ngựa' },
  moving: { label: 'Đang di chuyển', icon: 'fa-truck-fast', hint: 'Xe đang đến điểm đón hoặc đang chở ngựa' },
  settle: { label: 'Chờ quyết toán', icon: 'fa-receipt', hint: 'Ngựa đã giao, đang đối soát chi phí hoặc chờ bạn thanh toán và đánh giá' },
  closed: { label: 'Hết hạn / hủy / từ chối', icon: 'fa-ban', hint: 'Báo giá hết hạn, đơn đã hủy hoặc nhà xe từ chối đơn' },
  done: { label: 'Đã hoàn thành', icon: 'fa-flag-checkered', hint: 'Ngựa đã được giao' },
}
export function orderGroupOf(b: { status: BookingStatus; medical?: { status: string } }): OrderGroup {
  if (b.medical?.status === 'resubmit' && b.status === 'under_review') return 'supplement'
  switch (b.status) {
    case 'pending_intake': case 'under_review': case 'pending_commercial': return 'new'
    case 'awaiting_payment': case 'waybill_issued': case 'clearance_in_progress': case 'clearance_done': case 'ready_for_pickup': return 'approved'
    case 'en_route_to_pickup': case 'in_transit': case 'incident_reported': case 'pending_emergency_approval': case 'emergency_plan_active':
    case 'arrived': case 'handover_timeout': case 'returning_to_hq': return 'moving'
    case 'delivered_pending_settlement': case 'expenses_submitted': case 'settlement_issued': case 'payment_overdue': return 'settle'
    case 'completed': return 'done'
    case 'quote_expired': case 'cancelled': case 'rejected': return 'closed'
  }
}

// Tab của "Đơn hàng của tôi" (khách): mỗi đơn đúng một tab, xếp theo thứ tự các giai đoạn
export type OrderTab = 'confirm' | 'supplement' | 'pay' | 'prepare' | 'moving' | 'settle' | 'done' | 'closed'
export function orderTabOf(b: Pick<Booking, 'status' | 'medical' | 'balance' | 'settlement'>): OrderTab {
  switch (b.status) {
    case 'pending_intake': case 'under_review': return b.medical?.status === 'resubmit' && b.status === 'under_review' ? 'supplement' : 'confirm'
    case 'pending_commercial': return 'confirm'
    case 'awaiting_payment': return 'pay'
    case 'waybill_issued': case 'clearance_in_progress': case 'clearance_done': return 'prepare'
    case 'ready_for_pickup': return b.balance ? 'prepare' : 'pay'
    case 'en_route_to_pickup': return b.balance ? 'moving' : 'pay'
    case 'in_transit': case 'incident_reported': case 'pending_emergency_approval': case 'emergency_plan_active':
    case 'arrived': case 'handover_timeout': case 'returning_to_hq': return 'moving'
    case 'delivered_pending_settlement': case 'expenses_submitted': return 'settle'
    case 'settlement_issued': case 'payment_overdue': return b.settlement?.total ? 'pay' : 'settle'
    case 'completed': return 'done'
    case 'quote_expired': case 'cancelled': case 'rejected': return 'closed'
  }
}

// Cột của bảng Kanban trên trang Tổng quan của Manager: mỗi đơn đang chạy đúng một cột (đơn đã đóng không hiện)
export type BoardCol = 'intake' | 'review' | 'quote' | 'deposit' | 'prepare' | 'moving' | 'settle' | 'done'
export function managerBoardOf(s: BookingStatus): BoardCol | undefined {
  switch (s) {
    case 'pending_intake': return 'intake'
    case 'under_review': return 'review'
    case 'pending_commercial': return 'quote'
    case 'awaiting_payment': return 'deposit'
    case 'waybill_issued': case 'clearance_in_progress': case 'clearance_done': case 'ready_for_pickup': case 'en_route_to_pickup': return 'prepare'
    case 'in_transit': case 'incident_reported': case 'pending_emergency_approval': case 'emergency_plan_active':
    case 'arrived': case 'handover_timeout': case 'returning_to_hq': return 'moving'
    case 'delivered_pending_settlement': case 'expenses_submitted': case 'settlement_issued': case 'payment_overdue': return 'settle'
    case 'completed': return 'done'
    case 'quote_expired': case 'cancelled': case 'rejected': return undefined
  }
}

// Ngày giờ đến nơi dự kiến = giờ đến của chặng cuối; chưa có lộ trình thì chưa biết
export const arrivalOf = (route?: Pick<RoutePlan, 'legs'>) => route?.legs.length ? route.legs[route.legs.length - 1].arriveAt : undefined

// Số đơn đang chờ Manager (menu và trang Tổng quan). `moving` chỉ để theo dõi, không phải việc cần xử lý.
export const managerCounts = (list: Pick<Booking, 'status' | 'incidents' | 'medical'>[]) => ({
  intake: list.filter(b => b.status === 'pending_intake').length,
  quote: list.filter(b => b.status === 'pending_commercial').length,
  incident: list.reduce((n, b) => n + (b.incidents ?? []).filter(i => i.status === 'pending_approval').length, 0),
  moving: list.filter(b => orderGroupOf(b) === 'moving').length,
})

// Nhóm Đã duyệt chia nhỏ theo việc khách cần làm về thanh toán
export type ApprovedSub = 'await_deposit' | 'deposited' | 'pay_at_pickup' | 'ready'
export const APPROVED_SUBS: Record<ApprovedSub, { label: string; icon: string; hint: string }> = {
  await_deposit: { label: 'Chờ đặt cọc', icon: 'fa-credit-card', hint: `Bạn cần đặt cọc ${(DEPOSIT_RATE * 100).toLocaleString('vi-VN')}% trong ${QUOTE_VALID_HOURS} giờ để nhận vận đơn` },
  deposited: { label: 'Đã cọc, nhà xe chuẩn bị', icon: 'fa-file-signature', hint: 'Nhà xe làm giấy kiểm dịch, hải quan và chuẩn bị xe. Bạn không cần làm gì thêm' },
  pay_at_pickup: { label: 'Thanh toán lúc bốc ngựa', icon: 'fa-wallet', hint: 'Xe đã sẵn sàng. Trả 70% còn lại vào ngày bốc ngựa để xe được xuất bến' },
  ready: { label: 'Sẵn sàng đón ngựa', icon: 'fa-circle-check', hint: 'Đã thanh toán đủ. Chuẩn bị bản gốc hồ sơ ngựa để giao cho tài xế' },
}
export function approvedSubOf(b: { status: BookingStatus; balance?: unknown }): ApprovedSub | undefined {
  if (b.status === 'awaiting_payment') return 'await_deposit'
  if (b.status === 'waybill_issued' || b.status === 'clearance_in_progress' || b.status === 'clearance_done') return 'deposited'
  if (b.status === 'ready_for_pickup') return b.balance ? 'ready' : 'pay_at_pickup'
  return undefined
}

// ===== Trạng thái đơn suy từ các chuyến (PRD mục 13) =====
const DERIVED: BookingStatus[] = ['clearance_done', 'ready_for_pickup', 'en_route_to_pickup', 'in_transit', 'incident_reported', 'pending_emergency_approval', 'emergency_plan_active', 'delivered_pending_settlement']
export function deriveStatus(b: Pick<Booking, 'status' | 'trips' | 'clearance' | 'incidents'>): BookingStatus {
  if (!DERIVED.includes(b.status)) return b.status
  const trips = b.trips ?? []
  if (trips.length && trips.every(t => t.run?.deliveredAt)) return 'delivered_pending_settlement'
  const open = (b.incidents ?? []).filter(i => i.status !== 'resolved').map(i => i.status)
  if (open.includes('reported')) return 'incident_reported'
  if (open.includes('pending_approval')) return 'pending_emergency_approval'
  if (open.includes('active')) return 'emergency_plan_active'
  if (trips.some(t => t.run?.startedAt)) return 'in_transit'
  if (trips.some(t => t.departedAt)) return 'en_route_to_pickup'
  return trips.length && trips.every(t => t.acks.driver && t.acks.escort) ? 'ready_for_pickup' : 'clearance_done'
}

// ===== Lộ trình chi tiết (Flow 3, PRD mục 4.2) =====
const placeName = (n: string) => n.split(' — ')[0]
const MIN = 60_000

// Tính khoảng cách từng chặng dựa trên vị trí thực tế của các điểm
export function computeLegDistances(fromName: string, toName: string, rests: Pick<RestStop, 'name'>[], gate?: string): number[] {
  const findPt = (name: string): GeoPoint | undefined => {
    const clean = placeName(name).trim()
    const loc = findLocation(clean) || findLocation(name)
    if (loc) return loc
    const st = TRANSIT_STATIONS.find(s => s.name.toLowerCase() === clean.toLowerCase() || s.name.toLowerCase().includes(clean.toLowerCase()) || clean.toLowerCase().includes(s.name.toLowerCase().replace(/^trạm nghỉ /, '')))
    if (st) return st
    const g = GATES.find(x => x.name.toLowerCase().includes(clean.toLowerCase()) || clean.toLowerCase().includes(x.name.toLowerCase()))
    if (g) return g
    return undefined
  }

  const pFrom = findPt(fromName)
  const pTo = findPt(toName)
  const pRests = rests.map(r => findPt(r.name))

  const n = rests.length + 1
  if (!pFrom || !pTo || pRests.some(p => !p)) return []

  const gPt = gate ? GATES.find(g => g.name === gate) : undefined
  const pts: GeoPoint[] = [pFrom, ...(pRests as GeoPoint[]), pTo]
  const dists: number[] = []

  let gateIdx = -1
  if (gPt) {
    let minDetour = Infinity
    for (let i = 0; i < n; i++) {
      const detour = haversineKm(pts[i], gPt) + haversineKm(gPt, pts[i + 1]) - haversineKm(pts[i], pts[i + 1])
      if (detour < minDetour) {
        minDetour = detour
        gateIdx = i
      }
    }
  }

  for (let i = 0; i < n; i++) {
    const a = pts[i]
    const b = pts[i + 1]
    let d = haversineKm(a, b)
    if (gPt && i === gateIdx) {
      d = haversineKm(a, gPt) + haversineKm(gPt, b)
    }
    dists.push(Math.max(15, roadKm(d)))
  }
  return dists
}

export interface LegMetric {
  km: number
  hours: number
}

// Chia chặng theo dữ liệu đường bộ thực tế (OSRM road legs) hoặc tỷ lệ cự ly thực tế giữa các trạm dừng
export function layoutLegs(
  from: string,
  to: string,
  etd: number,
  rests: Pick<RestStop, 'name' | 'minutes'>[],
  driveHours: number,
  gate?: string,
  useRealDistances = false,
  legMetrics?: LegMetric[]
): RouteLeg[] {
  const n = rests.length + 1
  const names = [placeName(from), ...rests.map(r => r.name || `Trạm nghỉ ${rests.indexOf(r) + 1}`), placeName(to)]
  const legs: RouteLeg[] = []
  let t = etd

  // Ưu tiên 1: Dùng trực tiếp metrics từng chặng từ OSRM (đường bộ thực tế)
  if (legMetrics && legMetrics.length === n) {
    for (let i = 0; i < n; i++) {
      const metric = legMetrics[i]
      const legMs = Math.round(metric.hours * 60 * MIN)
      legs.push({
        no: i + 1,
        from: names[i],
        to: names[i + 1],
        departAt: Math.round(t),
        arriveAt: Math.round(t + legMs),
        km: metric.km,
        hours: metric.hours,
      })
      t += legMs + (rests[i]?.minutes ?? 0) * MIN
    }
    return legs
  }

  // Ưu tiên 2: Tính tỷ lệ theo cự ly thực tế (khi bật useRealDistances)
  const dists = useRealDistances ? computeLegDistances(from, to, rests, gate) : []
  const totalDist = dists.reduce((sum, d) => sum + d, 0)

  for (let i = 0; i < n; i++) {
    const ratio = useRealDistances && totalDist > 0 && dists.length === n
      ? (dists[i] / totalDist)
      : (1 / n)
    const legMs = ratio * driveHours * 60 * MIN
    const legKm = dists.length === n ? dists[i] : undefined
    const legHours = parseFloat((legMs / (60 * MIN)).toFixed(2))

    legs.push({
      no: i + 1,
      from: names[i],
      to: names[i + 1],
      departAt: Math.round(t),
      arriveAt: Math.round(t + legMs),
      km: legKm,
      hours: legHours,
    })
    t += legMs + (rests[i]?.minutes ?? 0) * MIN
  }
  return legs
}

// Giờ xe tới cửa khẩu: tính theo vị trí chặng tới cửa khẩu thay vì nhân cứng 60%
export function estimateBorderEta(
  legs: RouteLeg[],
  _rests?: Pick<RestStop, 'name' | 'minutes'>[],
  gate?: string
): number | undefined {
  if (!legs.length) return undefined
  if (gate && legs.length > 1) {
    const borderLegIdx = Math.max(1, Math.min(legs.length - 1, Math.round(legs.length * 0.55)))
    return legs[borderLegIdx - 1]?.arriveAt
  }
  const start = legs[0].departAt, end = legs[legs.length - 1].arriveAt
  return Math.round(start + (end - start) * 0.6)
}

// Đề xuất giờ xuất phát tối ưu sao cho xe tới cửa khẩu rơi vào khung giờ vàng thông quan (08:30 - 11:30)
export function suggestOptimalDepartureForBorder(
  targetDate: number,
  driveHoursToGate: number,
  restMinutesBefore = 45
): number {
  const d = new Date(targetDate)
  const targetBorderMinutes = 10 * 60 + 15 // 10:15 AM
  const travelMinutes = Math.round(driveHoursToGate * 60) + restMinutesBefore
  let departMinutes = targetBorderMinutes - travelMinutes
  if (departMinutes < 4 * 60 + 30) {
    // Nếu quá sớm trước 4h30 sáng, chuyển sang khung giờ đầu chiều (13:30)
    departMinutes = Math.max(5 * 60, 13 * 60 + 30 - travelMinutes)
  }
  const h = Math.floor(departMinutes / 60)
  const m = Math.round(departMinutes % 60)
  d.setHours(h, m, 0, 0)
  return d.getTime()
}

// Phương án mặc định cho Coordinator chỉnh: chia chặng vừa đủ, trạm nghỉ gợi ý theo cửa khẩu, mỗi trạm dừng 45 phút
export function buildRoutePlan(b: Pick<Booking, 'type' | 'origin' | 'dest' | 'gate'>, etd: number): RoutePlan {
  const international = b.type === 'international'
  const driveHours = routeKm(b.origin, b.dest, b.gate) / AVG_SPEED_KMH
  const minLegs = Math.max(1, Math.ceil(driveHours / MAX_CONTINUOUS_HOURS))
  const targetLegs = Math.max(1, Math.ceil(driveHours / TARGET_LEG_HOURS))
  const n = Math.max(minLegs, targetLegs)
  const names = suggestTransitStations(b.origin, b.dest, b.gate, n - 1)
  const rests: RestStop[] = names.map((name, i) => ({ afterLeg: i + 1, name, minutes: 45 }))
  const legs = layoutLegs(b.origin.name, b.dest.name, etd, rests, driveHours, b.gate)
  return { legs, rests, borderEta: international ? estimateBorderEta(legs, rests, b.gate) : undefined }
}

const minutesOfDay = (t: number) => new Date(t).getHours() * 60 + new Date(t).getMinutes()
export const borderOutsideWindow = (t: number) => minutesOfDay(t) < BORDER_WINDOW.open || minutesOfDay(t) > BORDER_WINDOW.close

// Kiểm tra quy tắc chia chặng: lỗi chặn hoàn tất; cảnh báo chuyển cho Manager xem như đề nghị ngoại lệ
export function validateRoutePlan(plan: Pick<RoutePlan, 'legs' | 'rests' | 'borderEta'>, international: boolean) {
  const errors: string[] = []
  const warnings: string[] = []
  plan.legs.forEach(l => {
    const h = (l.arriveAt - l.departAt) / (60 * MIN)
    if (h > MAX_CONTINUOUS_HOURS) errors.push(`Chặng ${l.no} đi liên tục ${h.toFixed(1)} giờ, vượt ${MAX_CONTINUOUS_HOURS} giờ. Thêm trạm nghỉ.`)
  })
  plan.rests.forEach((r, i) => {
    if (r.minutes < MIN_REST_MINUTES) errors.push(`Trạm nghỉ ${i + 1} chỉ ${r.minutes} phút, tối thiểu ${MIN_REST_MINUTES} phút.`)
    if (!r.name.trim()) errors.push(`Trạm nghỉ ${i + 1} chưa có tên.`)
  })
  if (international) {
    if (!plan.borderEta) errors.push('Chưa có giờ dự kiến tới cửa khẩu.')
    else if (borderOutsideWindow(plan.borderEta)) warnings.push('Giờ tới cửa khẩu ngoài khung 07:30–16:30, cần quản lý phê duyệt ngoại lệ.')
  }
  return { errors, warnings }
}

// ===== Lệnh điều xe (Trip Manifest, PRD mục 4.3) =====
// Chứng từ nhà xe cấp cho tài xế mang theo, và bản gốc tài xế phải thu của khách tại điểm đón
export function manifestDocuments(b: Pick<Booking, 'type'>) {
  const intl = b.type === 'international'
  return {
    system: [
      'Bản in Lệnh điều xe',
      'Vận đơn',
      'Giấy kiểm dịch, tờ khai, giấy ủy quyền áp tải nhà xe đã làm (bản in)',
      ...(intl ? ['Giấy phép vận tải liên vận quốc tế CLV / song phương (bản gốc kèm xe)'] : []),
      'Sổ đăng kiểm xe chuyên dụng và Bảo hiểm trách nhiệm dân sự còn hiệu lực',
      '02 bản "Biên bản Giao nhận Động vật sống & Chứng từ gốc" (ký tay với người gửi)',
      '02 bản "Biên bản Bàn giao & Hoàn tất chuyến đi" (ký tay với người nhận)',
    ],
    originals: [
      'Hộ chiếu ngựa bản gốc (FEI / National Passport)',
      'Sổ tiêm phòng',
      'Phiếu xét nghiệm EIA/EVA, bản gốc kèm 02 bản sao công chứng',
    ],
  }
}

// Mốc đang chờ làm: mốc đầu tiên chưa hoàn tất
export const currentCheckpoint = (t: { run?: TripRun }) => t.run?.checkpoints.find((c, index) =>
  !c.doneAt && !(index === 0 && c.type === 'pickup' && !!t.run?.startedAt && !!c.arrivedAt),
)
export const checkpointState = (cp: Checkpoint, current?: Checkpoint): 'done' | 'current' | 'locked' => (cp.doneAt ? 'done' : cp === current ? 'current' : 'locked')
// Mốc nhỏ của bước Vận chuyển: các mốc (đón, trạm nghỉ, cửa khẩu, giao) đã xong / tổng và mốc hiện tại
export function transitProgress(t: { run?: TripRun }): { done: number; total: number; current?: string } | undefined {
  const cps = t.run?.checkpoints
  if (!cps?.length) return undefined
  return { done: cps.filter(c => c.doneAt).length, total: cps.length, current: currentCheckpoint(t)?.label }
}
// "In Transit - Leg N": số trạm nghỉ đã qua + 1
export const legNumber = (t: { run?: TripRun }) => (t.run?.checkpoints.filter(c => c.type === 'rest' && c.doneAt).length ?? 0) + 1

// Thông quan xong mà Driver chưa bấm tiếp tục hành trình (xe còn ở cửa khẩu). Mốc sau đã check-in rồi (dữ liệu cũ) thì coi như đã rời.
export function pendingDeparture(t: { run?: TripRun }): Checkpoint | undefined {
  const cps = t.run?.checkpoints
  if (!cps || t.run?.deliveredAt) return undefined
  const i = cps.findIndex(c => c.type === 'customs' && c.doneAt && !c.leftAt)
  return i >= 0 && !cps.slice(i + 1).some(c => c.arrivedAt) ? cps[i] : undefined
}
// Xe đang ở đâu theo các xác nhận thủ công của Driver (không dùng GPS): đang dừng tại một mốc, hoặc đang chạy giữa hai mốc
export type VehicleSpot = { kind: 'at'; index: number } | { kind: 'between'; from: number; to: number }
export function vehicleSpot(t: { run?: TripRun }): VehicleSpot {
  const cps = t.run?.checkpoints
  if (!cps?.length) return { kind: 'at', index: 0 }
  let i = -1
  cps.forEach((c, k) => { if (c.arrivedAt) i = k })
  if (i < 0) return { kind: 'at', index: 0 }
  const c = cps[i], next = cps[i + 1]
  const sameBorderCheckpoint = c.type === 'border' && next?.type === 'customs' && next.place === c.place
  const left = c.type === 'delivery' ? false : sameBorderCheckpoint ? !!next?.leftAt : c.type === 'border' ? !!c.doneAt || !!next?.arrivedAt : c.type === 'customs' ? !!c.leftAt || !!next?.arrivedAt : c.type === 'pickup' ? !!c.doneAt || (!!t.run?.startedAt && !!c.arrivedAt) : !!c.doneAt
  return left && next ? { kind: 'between', from: i, to: i + 1 } : { kind: 'at', index: i }
}
export function spotLabel(t: { run?: TripRun; departedAt?: number }): string {
  const cps = t.run?.checkpoints, spot = vehicleSpot(t)
  if (!cps?.length) return t.departedAt ? 'Đang trên đường tới điểm đón' : 'Chưa xuất phát'
  if (!cps[0].arrivedAt) return 'Đang trên đường tới điểm đón'
  const name = (c: Checkpoint) => (c.type === 'rest' ? c.place.replace(/^Trạm nghỉ /, 'trạm ') : c.type === 'pickup' ? 'điểm đón' : c.type === 'delivery' ? 'điểm giao' : `cửa khẩu ${c.place}`)
  if (spot.kind === 'at') return t.run?.deliveredAt ? 'Đã giao ngựa tại điểm giao' : `Đang ở ${name(cps[spot.index])}`
  return `Đang trên đường từ ${name(cps[spot.from])} tới ${name(cps[spot.to])}`
}
// Toạ độ của một mốc trên bản đồ: điểm đón, trạm nghỉ, cửa khẩu, điểm giao
export function checkpointPoint(b: Pick<Booking, 'origin' | 'dest'>, cp: Checkpoint): GeoPoint | undefined {
  if (cp.type === 'pickup') return findLocation(b.origin.id)
  if (cp.type === 'delivery') return findLocation(b.dest.id)
  if (cp.type === 'rest') return TRANSIT_STATIONS.find(s => s.name === cp.place)
  return GATES.find(g => g.name === cp.place)
}
// Vị trí xe để vẽ: dừng tại mốc thì đúng chỗ đó, đang đi thì ở giữa hai mốc
export function vehiclePoint(b: Pick<Booking, 'origin' | 'dest'>, t: { run?: TripRun }): GeoPoint | undefined {
  const spot = vehicleSpot(t), cps = t.run?.checkpoints
  if (!cps?.length) return findLocation(b.origin.id)
  if (spot.kind === 'at') return checkpointPoint(b, cps[spot.index])
  const a = checkpointPoint(b, cps[spot.from]), z = checkpointPoint(b, cps[spot.to])
  return a && z ? { lat: (a.lat + z.lat) / 2, lng: (a.lng + z.lng) / 2 } : a ?? z
}

// Mốc chưa check-in mà đã quá giờ dự kiến từ 30 phút: Delayed Check-in cho Coordinator
export function delayedCheckpoint(t: { run?: TripRun }, now = Date.now()) {
  if (!t.run?.startedAt || t.run.deliveredAt) return undefined
  const cp = currentCheckpoint(t)
  const waiting = cp && !cp.arrivedAt && cp.type !== 'customs'
  return waiting && now - cp.plannedAt >= DELAY_ALERT_MINUTES * 60_000 ? cp : undefined
}
export const lastWelfare = (t: { run?: TripRun }): WelfareLog | undefined => t.run?.welfare?.length ? t.run.welfare[t.run.welfare.length - 1] : undefined
export const needsAttention = (w?: WelfareLog) => !!w && w.condition !== 'normal'


// ===== Sự cố và quyết toán (Flow 5, 6; PRD mục 6, 7, 11.5) =====
export const openIncidentOf = (b: Pick<Booking, 'incidents'>, tripId: string) => b.incidents?.find(i => i.tripId === tripId && i.status !== 'resolved')
// Ngựa thì khách chịu, vận chuyển thì nhà xe chịu; chuồng đệm do tắc cửa khẩu nhà xe chịu. Manager sửa được lúc đối soát.
export function defaultPayer(kind: IncidentKind, category: ExpenseCategory): Payer {
  if (kind === 'traffic_jam') return 'carrier' // tắc đường là việc vận chuyển: nhà xe chịu toàn bộ (PRD 11.5)
  return category === 'vet_fee' || category === 'medicine' || category === 'holding_stable' ? 'customer' : 'carrier'
}
// Mỗi nhóm sự cố có đúng một cách xử lý, lập trên bản đồ
export function incidentActionsFor(kind: IncidentKind): IncidentAction[] {
  return kind === 'horse_health' ? ['to_station'] : kind === 'vehicle_breakdown' ? ['rescue_and_station'] : ['reroute']
}
// Sức khỏe ngựa và xe gặp sự cố đều đưa ngựa tới trạm nghỉ, nên Escort phải xác nhận ngựa đủ sức mới đi tiếp; tắc đường thì không
export const needsFitCheck = (kind: IncidentKind) => kind !== 'traffic_jam'

// ----- Bản đồ sự cố: điểm gần nhất, đường đi còn lại, vị trí xe -----
export function nearestTo<T extends GeoPoint>(from: GeoPoint, list: T[], n = list.length): (T & { km: number })[] {
  return list.map(x => ({ ...x, km: haversineKm(from, x) })).sort((a, z) => a.km - z.km).slice(0, n)
}
// Chiếu một điểm lên đường gấp khúc: trả về đoạn gần nhất (chỉ số điểm đầu đoạn) và quãng đường tính từ điểm đầu đường
export function projectOnPath(path: GeoPoint[], p: GeoPoint): { segment: number; at: number } {
  const kx = Math.cos((path[0].lat * Math.PI) / 180) * 111, ky = 111
  const flat = (q: GeoPoint) => ({ x: (q.lng - path[0].lng) * kx, y: (q.lat - path[0].lat) * ky })
  const q = flat(p)
  let best = { d: Infinity, segment: 0, at: 0 }, before = 0
  for (let i = 0; i < path.length - 1; i++) {
    const a = flat(path[i]), b = flat(path[i + 1]), len = Math.hypot(b.x - a.x, b.y - a.y)
    const t = len ? Math.max(0, Math.min(1, ((q.x - a.x) * (b.x - a.x) + (q.y - a.y) * (b.y - a.y)) / (len * len))) : 0
    const d = Math.hypot(q.x - (a.x + (b.x - a.x) * t), q.y - (a.y + (b.y - a.y) * t))
    if (d < best.d) best = { d, segment: i, at: before + t * len }
    before += len
  }
  return { segment: best.segment, at: best.at }
}
// Các điểm xe còn phải đi qua kể từ vị trí hiện tại (điểm kế tiếp, các trạm, cửa khẩu, điểm trả)
export const pointsAhead = <T extends GeoPoint>(path: T[], p: GeoPoint): T[] => path.slice(projectOnPath(path, p).segment + 1)
// Điểm nằm ở tỷ lệ f (0 – 1) quãng đường dọc đường gấp khúc
export function pointAlong(path: GeoPoint[], f: number): GeoPoint {
  const lens = path.slice(1).map((p, i) => haversineKm(path[i], p))
  let want = lens.reduce((t, x) => t + x, 0) * Math.max(0, Math.min(1, f))
  for (let i = 0; i < lens.length; i++) {
    if (want <= lens[i] || i === lens.length - 1) { const k = lens[i] ? Math.min(1, want / lens[i]) : 0; return { lat: path[i].lat + (path[i + 1].lat - path[i].lat) * k, lng: path[i].lng + (path[i + 1].lng - path[i].lng) * k } }
    want -= lens[i]
  }
  return path[0]
}
// Đường đi đã lập của đơn: điểm đón → trạm đã chọn → cửa khẩu → điểm trả
export function bookingPath(b: Pick<Booking, 'origin' | 'dest' | 'gate' | 'route'>): GeoPoint[] {
  const a = findLocation(b.origin.id), z = findLocation(b.dest.id)
  if (!a || !z) return []
  const gate = b.gate ? GATES.find(g => g.name === b.gate) : undefined
  const stations = (b.route?.rests ?? []).flatMap(r => TRANSIT_STATIONS.filter(s => s.name === r.name))
  return routeOutline(a, gate, z, stations).path
}
// Vị trí xe lúc báo sự cố (bản thử, mô phỏng): giữa mốc vừa qua và mốc kế tiếp của hành trình. Có app thật thì lấy từ GPS.
export function incidentLocation(b: Pick<Booking, 'origin' | 'dest' | 'gate' | 'route'>, trip: VehicleTrip): GeoPoint {
  const path = bookingPath(b)
  if (path.length < 2) return findLocation(b.origin.id) ?? { lat: 10.78, lng: 106.7 }
  const p = transitProgress(trip)
  const f = p && p.total ? (p.done + 0.5) / p.total : 0.5
  return pointAlong(path, Math.max(0.08, Math.min(0.92, f)))
}
// Chỉ các khoản khách chịu vào bảng quyết toán
export function settlementOf(b: Pick<Booking, 'incidents'>): { items: SettlementItem[]; total: number } {
  const items = (b.incidents ?? []).flatMap(i => i.expenses).filter(e => e.payer === 'customer').map(e => ({ label: `${EXPENSE_CATEGORY[e.category]}: ${e.label}`, amount: e.amount, photo: e.photo }))
  return { items, total: items.reduce((n, i) => n + i.amount, 0) }
}

// Tự động phân bổ xe thông minh (Smart Fleet Allocation):
// 1. Lọc xe sẵn sàng và không trùng lịch.
// 2. Nếu vừa 1 xe: chọn xe có sức chứa nhỏ nhất mà vẫn đủ chỗ (Best-fit single vehicle) để tránh lãng phí xe lớn.
// 3. Nếu vượt sức chứa 1 xe: tự động chia thành nhiều xe (Multi-vehicle) ưu tiên xe to giảm dần để tối thiểu số chuyến.
export interface AutoPackDraft {
  vehicleId: string
  horseIds: string[]
}

export const isVehicleReadyForDispatch = (v: Vehicle): boolean => {
  const s = (v.status || 'AVAILABLE').toUpperCase().replace(/[\s_-]/g, '')
  return s === 'AVAILABLE' || s === 'ACTIVE' || s === ''
}

export function autoPackFleet(
  horseIds: string[],
  vehicles: Vehicle[],
  schedVehicles: Map<string, Booked[]>,
  departAt: number
): AutoPackDraft[] {
  if (!horseIds.length) return [{ vehicleId: '', horseIds: [] }]

  // 1. Lọc xe khả dụng: trạng thái sẵn sàng (không bận/bảo dưỡng) và không trùng lịch.
  const available = vehicles.filter(v =>
    isVehicleReadyForDispatch(v) && !clashOf(schedVehicles.get(v.id), departAt)
  )

  // Nếu không có xe nào đạt chuẩn, trả về 1 chuyến chưa gán xe để điều phối viên nhận biết
  if (!available.length) {
    return [{ vehicleId: '', horseIds: [...horseIds] }]
  }

  // Trường hợp 1: Tất cả ngựa có thể xếp vừa vào 1 xe duy nhất
  // Tìm xe có capacity >= horseIds.length nhỏ nhất (Best-Fit Single Vehicle)
  const singleFits = available
    .filter(v => v.capacity >= horseIds.length)
    .sort((a, b) => a.capacity - b.capacity)

  if (singleFits.length > 0) {
    return [{ vehicleId: singleFits[0].id, horseIds: [...horseIds] }]
  }

  // Trường hợp 2: Số ngựa vượt quá sức chứa của bất kỳ xe đơn lẻ nào -> Cần chia nhiều xe (Multi-vehicle Bin Packing)
  // Sắp xếp xe khả dụng theo sức chứa giảm dần (Best-Fit Decreasing) để dùng ít xe nhất
  const sortedVehicles = [...available].sort((a, b) => b.capacity - a.capacity)
  const results: AutoPackDraft[] = []
  let remainingHorses = [...horseIds]
  const usedVehicleIds = new Set<string>()

  for (const v of sortedVehicles) {
    if (!remainingHorses.length) break
    if (usedVehicleIds.has(v.id)) continue

    const takeCount = Math.min(v.capacity, remainingHorses.length)
    const assigned = remainingHorses.slice(0, takeCount)
    remainingHorses = remainingHorses.slice(takeCount)
    usedVehicleIds.add(v.id)

    results.push({
      vehicleId: v.id,
      horseIds: assigned,
    })
  }

  // Nếu vẫn còn ngựa mà đã hết xe khả dụng -> để lại ở chuyến cuối chưa gán xe để điều phối viên thấy cảnh báo
  if (remainingHorses.length > 0) {
    results.push({
      vehicleId: '',
      horseIds: remainingHorses,
    })
  }

  return results
}
