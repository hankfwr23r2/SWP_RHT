// Dữ liệu mẫu của luồng đặt đơn (Flow 1): hồ sơ ngựa của khách mẫu và các đơn ở từng trạng thái.
// Đơn mẫu ở mọi trạng thái của luồng mới, gồm đơn nhiều xe. Sửa file này thì tăng MOCK_VERSION trong store.ts.
import type { HorseDocType } from '../../config/booking-rules'
import { DAY, HOUR } from '../../config/business-rules'
import { AVG_SPEED_KMH } from '../../config/public-pricing'
import { COUNTRY_LOCATIONS, RESCUE_POINTS, TRANSIT_STATIONS, type GeoPoint } from '../../config/network'
import { findLocation, nearestTo, blankClearance, settlementOf, buildCheckpoints, buildRoutePlan, finalizeQuote, layoutLegs, manifestDocuments, quoteLines, tripIdFor, waybillNoOf } from '../../lib/booking'
import { daysFromToday } from '../../lib/dates'
import type { IncidentPlan, PlanLine } from '../../types/booking'
import type { Booking, BookingHorse, Clearance, ClearanceStatus, HorseDoc, Incident, HorseProfile, Party, PlaceRef, Sex, TripRun, VehicleTrip, WelfareLog } from '../../types/booking'
import { CUSTOMER } from './orders'
import { seedCrew, seedDriverOf, seedVehicles } from './fleet'

const NOW = Date.now()
const doc = (daysAgo: number, expiresInDays?: number): HorseDoc => ({
  fileName: 'scan.pdf', uploadedAt: NOW - daysAgo * DAY, expiresAt: expiresInDays === undefined ? undefined : NOW + expiresInDays * DAY,
})
const horse = (n: number, name: string, breed: string, sex: Sex, color: string, birthYear: number, marks: string, docs: Partial<Record<HorseDocType, HorseDoc>>, completedTrips: number): HorseProfile => ({
  id: `H-${String(n).padStart(3, '0')}`, owner: CUSTOMER.name, name, microchip: `VN-98521${n}`, breed, sex, color, birthYear, marks, docs, completedTrips, createdAt: NOW - 200 * DAY,
})

export const seedHorses = (): HorseProfile[] => [
  horse(1, 'Storm Runner', 'Thoroughbred', 'gelding', 'Nâu đỏ (Bay)', 2021, 'Sao trắng trán, tất trắng chân sau', { passport: doc(190), vaccine: doc(60, 170), lab: doc(40, 140) }, 5),
  horse(2, 'Bạch Phong', 'Arabian', 'mare', 'Bạch mã (White)', 2019, 'Đốm trắng nhỏ trên mũi', { passport: doc(190), vaccine: doc(60, 160), lab: doc(40, 150) }, 2),
  horse(3, 'Kim Lân', 'Thoroughbred', 'stallion', 'Hạt dẻ (Chestnut)', 2020, 'Vệt trắng dài giữa trán', { passport: doc(190), vaccine: doc(50, 200), lab: doc(30, 160) }, 6),
  horse(4, 'Thanh Vân', 'Warmblood', 'mare', 'Nâu đỏ (Bay)', 2018, 'Xoáy lông ở cổ bên trái', { passport: doc(120), vaccine: doc(30, 150) }, 1),
  horse(5, 'Hắc Mã', 'Quarter Horse', 'gelding', 'Đen (Black)', 2022, 'Không có dấu hiệu riêng', { passport: doc(100), vaccine: doc(300, -10), lab: doc(200, 90) }, 0),
  horse(6, 'Ngọc Long', 'Appaloosa', 'stallion', 'Đốm (Leopard)', 2021, 'Đốm đen toàn thân', { passport: doc(80), vaccine: doc(25, 180), lab: doc(20, 170) }, 0),
]

const place = (id: string): PlaceRef => {
  for (const [country, list] of Object.entries(COUNTRY_LOCATIONS)) {
    const l = list.find(x => x.id === id)
    if (l) return { id, name: l.name, country: country as PlaceRef['country'] }
  }
  throw new Error(`Không có địa điểm ${id}`)
}
const consignor: Party = { name: CUSTOMER.name, phone: '0901 456 789', idNumber: '3602 123 456', address: 'Ấp 5, Long Thành, Đồng Nai' }
const consignee = (name: string, address: string): Party => ({ name, phone: '0912 345 678', idNumber: '0310 987 654', address })

const bookingHorse = (h: HorseProfile, over: Partial<BookingHorse> = {}): BookingHorse => ({
  horseId: h.id, name: h.name, microchip: h.microchip, breed: h.breed, sex: h.sex,
  stall: 'standard', feedPackage: 'basic', waterPlan: 'every_3h', insurance: { opted: false }, ...over,
})

type Route = { type: Booking['type']; origin: PlaceRef; dest: PlaceRef; gate?: string; id: string }
const intl = (id: string, from: string, to: string, gate: string): Route => ({ type: 'international', origin: place(from), dest: place(to), gate, id })
const dom = (id: string, from: string, to: string): Route => ({ type: 'domestic', origin: place(from), dest: place(to), id })

export const seedBookings = (): Booking[] => {
  const horses = seedHorses()
  const h = (n: number) => horses[n - 1]
  const vehicles = seedVehicles()
  const vehicle = (id: string) => vehicles.find(v => v.id === id)!
  const crewName = (id: string) => seedCrew().find(c => c.id === id)!.name
  const common = { customer: CUSTOMER.name, consignor }
  const log = (time: number, actor: string, text: string) => ({ time, actor, text })
  const staffKD1 = { id: 'KD-01', name: 'Phạm Văn Hưng' }
  const staffKD2 = { id: 'KD-02', name: 'Nguyễn Thị Thu' }
  const staffDP1 = { id: 'DP-01', name: 'Trần Minh' }
  const staffDP3 = { id: 'DP-03', name: 'Phạm Tâm' }
  const intake = (at: number, specialist = staffKD1, coordinator = staffDP1) => ({ at, by: 'Quản lý', specialist, coordinator })
  const medicalOk = (at: number, by: string) => ({ status: 'approved' as const, at, by })
  const planDone = (by: string, hoursAgo = 20) => ({ at: NOW - hoursAgo * HOUR, by, note: '' })

  // Các xe của đơn: [mã xe, mã Escort, số thứ tự ngựa trong đơn]. Tài xế do Điều phối chọn.
  const mkTrips = (id: string, specs: [string, string, number[]][], hs: BookingHorse[], over: (i: number) => Partial<VehicleTrip> = () => ({})): VehicleTrip[] =>
    specs.map(([vid, eid, idx], i) => ({ tripId: tripIdFor(id, i + 1), vehicleId: vid, driverId: seedDriverOf(vid), escortId: eid, horseIds: idx.map(k => hs[k].horseId), acks: {}, ...over(i) }))
  const tripEtd = (departIn: number) => daysFromToday(departIn, 5)
  // Lộ trình dùng chung: chia chặng đều, trạm nghỉ do hệ thống gợi ý theo cửa khẩu
  const routeFor = (b: Pick<Booking, 'type' | 'origin' | 'dest' | 'gate'>, etd: number, by: string, hoursAgo = 20): NonNullable<Booking['route']> => {
    const r = buildRoutePlan(b, etd)
    return { ...r, completedAt: NOW - hoursAgo * HOUR, by }
  }
  const draftQuote = (b: Pick<Booking, 'type' | 'origin' | 'dest' | 'gate' | 'horses'>, vehicleIds: string[], sentAt: number) =>
    finalizeQuote(quoteLines(b, vehicleIds.map(vehicle)).lines, [], sentAt, 'Quản lý')
  const paidOf = (q: ReturnType<typeof draftQuote>, id: string, hoursAgo: number) => ({
    payment: { paidAt: NOW - hoursAgo * HOUR, amount: q.deposit, reference: `EQZ-${id.slice(-4)}-DEP` },
    waybill: { no: waybillNoOf(id), issuedAt: NOW - hoursAgo * HOUR },
  })
  const balanceOf = (q: ReturnType<typeof draftQuote>, id: string, hoursAgo: number) => ({ balance: { paidAt: NOW - hoursAgo * HOUR, amount: q.balance, reference: `EQZ-${id.slice(-4)}-BAL` } })
  // Giấy tờ: trạng thái từng hạng mục theo thứ tự mặc định của tuyến (xem defaultClearanceItems)
  const clr = (type: Booking['type'], statuses: ClearanceStatus[], extra: Partial<Clearance> = {}, notes: string[] = []): Clearance => {
    const c = blankClearance(type)
    c.items = c.items.map((it, i) => {
      const status = statuses[i] ?? 'todo'
      return { ...it, status, note: notes[i] ?? '', photos: status === 'done' ? [`${it.type}_scan.jpg`] : [], updatedAt: status === 'todo' ? undefined : NOW - 10 * HOUR, by: status === 'todo' ? undefined : staffKD1.name }
    })
    return { ...c, ...extra }
  }
  const allDone = (type: Booking['type']): ClearanceStatus[] => blankClearance(type).items.map(() => 'done')
  const accepted = { acceptedAt: NOW - 30 * HOUR, acceptedBy: staffKD1.name }
  const hist = (n: number, ...rest: [number, string, string][]) => [log(NOW - n * DAY, CUSTOMER.name, 'Gửi yêu cầu đặt đơn'), ...rest.map(([t, a, x]) => log(NOW - t, a, x))]

  // Mọi đơn đã qua thẩm định: có trips, plan, route, quote
  const reviewed = (route: Route, hs: BookingHorse[], specs: [string, string, number[]][], departIn: number, ageDays: number, status: Booking['status'], extra: Partial<Booking> = {}): Booking => {
    const intlRoute = route.type === 'international'
    const base: Booking = {
      ...common, ...route, createdAt: NOW - ageDays * DAY, departAt: daysFromToday(departIn),
      consignee: consignee(intlRoute ? 'Trung tâm Kiểm dịch Phnom Penh' : 'CLB Cưỡi ngựa Sài Gòn', intlRoute ? 'Phnom Penh, Campuchia' : 'Quận 2, TP.HCM'),
      horses: hs, status, intake: intake(NOW - (ageDays - 1) * DAY), medical: medicalOk(NOW - (ageDays - 2) * DAY, staffKD1.name),
      // Tài xế và hộ tống do Manager chọn lúc duyệt báo giá: đơn chưa tới bước đó thì chưa có
      trips: mkTrips(route.id, specs, hs).map(tr => (status === 'under_review' || status === 'pending_commercial' ? { ...tr, driverId: '', escortId: '' } : tr)), plan: planDone(staffDP1.name, 30), history: hist(ageDays),
    }
    base.route = routeFor(base, tripEtd(departIn), staffDP1.name, 30)
    base.quote = draftQuote(base, specs.map(s => s[0]), NOW - (ageDays - 3) * DAY)
    return { ...base, ...extra }
  }
  // Đã đặt cọc: có Vận đơn, danh sách giấy tờ
  const paid = (route: Route, hs: BookingHorse[], specs: [string, string, number[]][], departIn: number, ageDays: number, status: Booking['status'], clearance: Clearance, extra: Partial<Booking> = {}): Booking => {
    const b = reviewed(route, hs, specs, departIn, ageDays, status)
    return { ...b, ...paidOf(b.quote!, route.id, (ageDays - 3) * 24), clearance, history: hist(ageDays, [(ageDays - 3) * 24 * HOUR, CUSTOMER.name, `Đặt cọc 30%, cấp Vận đơn ${waybillNoOf(route.id)}`]), ...extra }
  }
  const crewHist = (b: Booking, ...more: [number, string, string][]) => [...b.history, ...more.map(([t, a, x]) => log(NOW - t, a, x))]

  // ===== Hành trình thực tế (Flow 4) cho một xe =====
  const fillRun = (b: Booking, t: VehicleTrip, done: number, welfareOverride: Partial<WelfareLog>[] = []): TripRun => {
    const cps = buildCheckpoints(b)
    const driver = crewName(t.driverId), escort = crewName(t.escortId)
    const mine = b.horses.filter(x => t.horseIds.includes(x.horseId))
    cps.forEach((cp, i) => {
      if (i >= done) return
      cp.arrivedAt = cp.plannedAt + 3 * 60_000
      cp.photo = `capture_${cp.id}.jpg`
      cp.by = driver
      cp.doneAt = cp.arrivedAt + (cp.type === 'rest' ? 45 * 60_000 : 5 * 60_000)
      if (cp.type === 'pickup') { cp.chips = mine.map(x => x.microchip); cp.originals = manifestDocuments(b).originals; cp.handoverPhoto = 'bien_ban_giao_nhan.jpg' }
      if (cp.type === 'customs') cp.stampPhotos = ['health_cert_stamp.jpg']
      if (cp.type === 'delivery') { cp.handoverPhoto = 'bien_ban_ban_giao.jpg'; cp.returnedOriginals = true }
    })
    const welfare: WelfareLog[] = cps.filter(c => c.doneAt && (c.type === 'rest' || c.type === 'delivery')).map((c, i) => ({
      id: `WL-${i + 1}`, checkpointId: c.id, at: c.arrivedAt! + 8 * 60_000, by: escort, condition: 'normal', waterLiters: 8, hay: true, photo: `ngua_${c.id}.jpg`, note: '', ...welfareOverride[i],
    }))
    return { checkpoints: cps, welfare, startedAt: cps[0].doneAt, deliveredAt: done >= cps.length ? cps[cps.length - 1].doneAt : undefined }
  }
  // Lộ trình một trạm nghỉ cố định giờ xuất phát, để các đơn đang chạy có mốc quanh "bây giờ"
  const runRoute = (b: Booking, etd: number, hours: number, restName: string, borderAt?: number) => {
    const rests = [{ name: restName, minutes: 45 }]
    b.route = {
      legs: layoutLegs(b.origin.name, b.dest.name, etd, rests, hours), rests: [{ afterLeg: 1, name: restName, minutes: 45 }],
      borderEta: borderAt, completedAt: etd - 40 * HOUR, by: staffDP1.name,
    }
  }
  const ackAll = (t: VehicleTrip, etd: number, departed?: number): VehicleTrip => ({ ...t, acks: { driver: etd - 5 * HOUR, escort: etd - 4 * HOUR }, departedAt: departed })
  const driverPack = (b: Booking) => ({ items: manifestDocuments(b).system, at: NOW - 40 * HOUR, by: staffDP1.name })

  // ===== Đơn trước thẩm định =====
  const b1Horses = [bookingHorse(h(1), { insurance: { opted: true } }), bookingHorse(h(2))]
  const b2Horses = [bookingHorse(h(3), { stall: 'single' }), bookingHorse(h(6)), bookingHorse(h(2))]
  const b3Horses = [bookingHorse(h(3))]
  const b4Horses = [bookingHorse(h(1), { stall: 'single', insurance: { opted: true } }), bookingHorse(h(6))]
  const b5Horses = [bookingHorse(h(1)), bookingHorse(h(2)), bookingHorse(h(3), { stall: 'single' })]
  const b6Horses = [bookingHorse(h(2)), bookingHorse(h(6)), bookingHorse(h(1))]
  const b7Horses = [bookingHorse(h(6))]

  const b3 = reviewed(dom('ORD-2026-0103', 'KHO-BD', 'KHO-DN'), b3Horses, [['VH-002', 'NV-01', [0]]], 50, 2, 'under_review')
  const b4 = reviewed(intl('ORD-2026-0104', 'KHO-DN', 'KHO-VTE', 'Lao Bảo – Densavanh'), b4Horses, [['VH-006', 'NV-02', [0, 1]]], 60, 3, 'pending_commercial')
  const b5 = reviewed(dom('ORD-2026-0105', 'KHO-BD', 'CLB-SG'), b5Horses, [['VH-009', 'NV-03', [0, 1, 2]]], 40, 4, 'awaiting_payment')
  const b7 = reviewed(dom('ORD-2026-0107', 'KHO-DN', 'KHO-LA'), b7Horses, [['VH-006', 'NV-05', [0]]], 33, 7, 'quote_expired')
  const b6 = paid(intl('ORD-2026-0106', 'KHO-DN', 'KHO-PNH', 'Mộc Bài – Bavet'), b6Horses, [['VH-004', 'NV-04', [0, 1, 2]]], 55, 6, 'waybill_issued', clr('international', []))
  const b10 = paid(dom('ORD-2026-0110', 'KHO-LA', 'KHO-BD'), b3Horses, [['VH-011', 'NV-04', [0]]], 47, 9, 'clearance_in_progress', clr('domestic', ['done', 'todo'], accepted, ['Đã nộp hồ sơ ở Chi cục Thú y, chờ cấp giấy']))
  const b11 = paid(dom('ORD-2026-0111', 'KHO-DN', 'CLB-SG'), [bookingHorse(h(2)), bookingHorse(h(6))], [['VH-012', 'NV-05', [0, 1]]], 52, 10, 'clearance_in_progress', clr('domestic', allDone('domestic'), accepted))
  const b12 = paid(dom('ORD-2026-0112', 'KHO-BD', 'CLB-SG'), [bookingHorse(h(1))], [['VH-013', 'NV-01', [0]]], 44, 12, 'clearance_done', clr('domestic', allDone('domestic'), { ...accepted, doneAt: NOW - 18 * HOUR, doneBy: staffKD1.name }))
  const b13 = paid(intl('ORD-2026-0113', 'KHO-DN', 'KHO-PNH', 'Mộc Bài – Bavet'), [bookingHorse(h(1)), bookingHorse(h(2))], [['VH-009', 'NV-03', [0, 1]]], 36, 14, 'clearance_in_progress', clr('international', allDone('international'), { ...accepted, horsesCleared: [h(1).id] }))
  b13.route = { ...b13.route!, borderEta: daysFromToday(36, 6) + 10 * 60_000 } // ETA cửa khẩu 06:10, ngoài khung giờ: chỉ cảnh báo
  const b15 = paid(dom('ORD-2026-0115', 'KHO-LA', 'KHO-BD'), [bookingHorse(h(2))], [['VH-007', 'NV-02', [0]]], 12, 20, 'ready_for_pickup', clr('domestic', allDone('domestic'), { ...accepted, doneAt: NOW - 40 * HOUR, doneBy: staffKD1.name }))
  b15.trips = [{ ...ackAll(b15.trips![0], tripEtd(12)), driverPack: driverPack(b15) }]
  b15.history = crewHist(b15, [18 * HOUR, 'Võ Thị Lan', 'Hộ tống xác nhận nhận lệnh. Xe và nhân sự sẵn sàng đón ngựa'])

  const b16 = paid(dom('ORD-2026-0116', 'KHO-BD', 'CLB-SG'), [bookingHorse(h(1)), bookingHorse(h(3))], [['VH-007', 'NV-02', [0, 1]]], 1, 35, 'en_route_to_pickup', clr('domestic', allDone('domestic'), { ...accepted, doneAt: NOW - 60 * HOUR, doneBy: staffKD1.name }))
  Object.assign(b16, balanceOf(b16.quote!, b16.id, 2))
  b16.trips = [{ ...ackAll(b16.trips![0], tripEtd(1), NOW - 1 * HOUR), driverPack: driverPack(b16) }]
  b16.history = crewHist(b16, [2 * HOUR, CUSTOMER.name, 'Thanh toán 70% còn lại'], [1 * HOUR, 'Nguyễn Văn Hùng', 'Xe bắt đầu di chuyển đến điểm đón ngựa'])

  const running = (route: Route, hs: BookingHorse[], specs: [string, string, number[]][], ageDays: number, status: Booking['status'], etd: number, hours: number, restName: string, borderAt: number | undefined, doneOf: (i: number) => number | undefined, over: Partial<Booking> = {}): Booking => {
    const b = paid(route, hs, specs, 0, ageDays, status, clr(route.type, allDone(route.type), { ...accepted, doneAt: NOW - 60 * HOUR, doneBy: staffKD1.name, horsesCleared: route.type === 'international' ? hs.map(x => x.horseId) : [] }))
    runRoute(b, etd, hours, restName, borderAt)
    Object.assign(b, balanceOf(b.quote!, b.id, 12), over)
    b.trips = b.trips!.map((t, i) => {
      const d = doneOf(i)
      const base = { ...ackAll(t, etd, etd - 90 * 60_000), driverPack: driverPack(b) }
      return d === undefined ? base : { ...base, run: fillRun(b, t, d) }
    })
    return b
  }

  const b17 = running(dom('ORD-2026-0117', 'KHO-DN', 'KHO-LA'), [bookingHorse(h(3)), bookingHorse(h(6))], [['VH-007', 'NV-02', [0, 1]]], 33, 'in_transit', NOW - 6.5 * HOUR, 5, 'Trạm nghỉ Long Khánh', undefined, () => 2)
  b17.history = crewHist(b17, [6.5 * HOUR, 'Nguyễn Văn Hùng', 'Bắt đầu hành trình'], [3.2 * HOUR, 'Nguyễn Văn Hùng', 'Tiếp tục hành trình (Leg 2)'])
  const b18 = running(intl('ORD-2026-0118', 'KHO-DN', 'KHO-PNH', 'Mộc Bài – Bavet'), [bookingHorse(h(1)), bookingHorse(h(2))], [['VH-009', 'NV-03', [0, 1]]], 34, 'in_transit', NOW - 4 * HOUR, 6, 'Trạm nghỉ Trảng Bàng', NOW + 1.5 * HOUR, () => 2)
  b18.trips![0].run = fillRun(b18, b18.trips![0], 2, [{ condition: 'stress', note: 'Storm Runner đổ mồ hôi nhẹ, đã xịt nước làm mát', waterLiters: 10 }])
  b18.history = crewHist(b18, [4 * HOUR, 'Trần Quốc Bảo', 'Bắt đầu hành trình'])
  const b19 = running(dom('ORD-2026-0119', 'KHO-BD', 'CLB-SG'), [bookingHorse(h(2))], [['VH-004', 'NV-04', [0]]], 34, 'delivered_pending_settlement', NOW - 9 * HOUR, 5, 'Trạm nghỉ Thủ Dầu Một', undefined, () => 3)
  b19.history = crewHist(b19, [9 * HOUR, 'Phạm Văn D', 'Bắt đầu hành trình'], [3 * HOUR, 'Phạm Văn D', 'Hoàn tất giao ngựa. Chờ tài xế gửi chi phí để quyết toán'])

  // Bốn đơn của khách demo để thử bản đồ theo dõi: xe đang dừng ở trạm, đang chạy giữa đường, đang ở cửa khẩu, vừa thông quan chờ rời cửa khẩu
  const track = (b: Booking, who: string, ...more: [number, string, string][]) => { b.history = crewHist(b, [5 * HOUR, who, 'Bắt đầu hành trình'], ...more); return b }
  const b34 = running(dom('ORD-2026-0134', 'KHO-DN', 'CLB-SG'), [bookingHorse(h(1))], [['VH-003', 'NV-05', [0]]], 36, 'in_transit', NOW - 3 * HOUR, 4, 'Trạm nghỉ Biên Hòa', undefined, () => 2)
  const rest34 = b34.trips![0].run!.checkpoints[1]
  rest34.doneAt = undefined // đã tới trạm, đang nghỉ, chưa bấm tiếp tục
  b34.trips![0].run!.welfare = []
  track(b34, crewName(b34.trips![0].driverId), [20 * 60_000, crewName(b34.trips![0].driverId), 'Xác nhận đã tới trạm nghỉ'])
  const b35 = running(dom('ORD-2026-0135', 'KHO-BD', 'KHO-LA'), [bookingHorse(h(2))], [['VH-005', 'NV-04', [0]]], 36, 'in_transit', NOW - 3.2 * HOUR, 4, 'Trạm nghỉ Củ Chi', undefined, () => 2)
  track(b35, crewName(b35.trips![0].driverId), [HOUR, crewName(b35.trips![0].driverId), 'Tiếp tục hành trình'])
  const b36 = running(intl('ORD-2026-0136', 'KHO-DN', 'KHO-PNH', 'Mộc Bài – Bavet'), [bookingHorse(h(3))], [['VH-006', 'NV-05', [0]]], 36, 'in_transit', NOW - 5 * HOUR, 6, 'Trạm nghỉ Củ Chi', NOW - 0.5 * HOUR, () => 3)
  track(b36, crewName(b36.trips![0].driverId), [30 * 60_000, crewName(b36.trips![0].driverId), 'Xác nhận đã tới cửa khẩu'])
  const b37 = running(intl('ORD-2026-0137', 'KHO-DN', 'KHO-PNH', 'Mộc Bài – Bavet'), [bookingHorse(h(4))], [['VH-008', 'NV-03', [0]]], 36, 'in_transit', NOW - 6 * HOUR, 6, 'Trạm nghỉ Củ Chi', NOW - 1.5 * HOUR, () => 4)
  track(b37, crewName(b37.trips![0].driverId), [10 * 60_000, crewName(b37.trips![0].driverId), 'Đã thông quan thành công'])

  // Phương án sự cố mẫu theo từng nhóm: trạm / cứu hộ gần nhất với vị trí xe (đường nối thẳng, số mẫu)
  const mid = (from: string, to: string): GeoPoint => { const a = findLocation(from)!, z = findLocation(to)!; return { lat: (a.lat + z.lat) / 2, lng: (a.lng + z.lng) / 2 } }
  const line = (a: GeoPoint, z: GeoPoint): PlanLine => { const km = Math.round(Math.hypot((a.lat - z.lat) * 111, (a.lng - z.lng) * 108) * 1.3); return { path: [[a.lat, a.lng], [z.lat, z.lng]], km, hours: km / AVG_SPEED_KMH } }
  const samplePlan = (kind: Incident['kind'], at: GeoPoint, to: GeoPoint, over: Partial<IncidentPlan> & Pick<IncidentPlan, 'newEta' | 'at'>): IncidentPlan => {
    const station = nearestTo(at, TRANSIT_STATIONS, 1)[0]
    const base = { note: '', by: 'Trần Minh', ...over }
    if (kind === 'traffic_jam') return { ...base, action: 'reroute', note: base.note || 'Đi vòng qua đường tránh', detour: line(at, to) }
    const toStation = line(at, station)
    if (kind === 'horse_health') return { ...base, action: 'to_station', station: station.name, restMinutes: 60, toStation }
    const rescue = nearestTo(at, RESCUE_POINTS, 1)[0]
    return { ...base, action: 'rescue_and_station', station: station.name, restMinutes: 60, toStation, rescue: { name: rescue.name, phone: rescue.phone, lat: rescue.lat, lng: rescue.lng }, rescueLine: line(rescue, at) }
  }

  // Đã giao, có sự cố đã xử lý xong và tài xế đã gửi bảng kê chi phí: chờ Manager đối soát (Flow 6)
  const costed = (id: string, horse: number, kind: Incident['kind'], expenses: [Incident['expenses'][number]['category'], string, number, Incident['expenses'][number]['payer']][]): Booking => {
    const b = running(dom(id, 'KHO-BD', 'CLB-SG'), [bookingHorse(h(horse))], [['VH-004', 'NV-04', [0]]], 34, 'expenses_submitted', NOW - 9 * HOUR, 5, 'Trạm nghỉ Thủ Dầu Một', undefined, () => 3)
    const driver = crewName('NV-04')
    b.incidents = [{
      id: `INC-${id.slice(-4)}-1`, tripId: b.trips![0].tripId, kind, reportedBy: driver, reportedAt: NOW - 7 * HOUR, location: mid('KHO-BD', 'CLB-SG'), photo: 'sos_scene.jpg', note: kind === 'horse_health' ? 'Ngựa đau bụng nhẹ, đổ mồ hôi' : 'Nổ lốp sau',
      status: 'resolved', resolvedAt: NOW - 5 * HOUR, fitConfirmedAt: NOW - 5.2 * HOUR,
      plan: samplePlan(kind, mid('KHO-BD', 'CLB-SG'), findLocation('CLB-SG')!, { newEta: NOW - 4 * HOUR, at: NOW - 6.8 * HOUR }),
      approval: { at: NOW - 6.5 * HOUR, by: 'Quản lý' },
      expenses: expenses.map(([category, label, amount, payer], i) => ({ id: `EXP-${id.slice(-4)}-${i + 1}`, category, label, photo: `${category}_receipt.jpg`, amount, payer, at: NOW - 6 * HOUR, by: driver })),
    }]
    b.expensesSubmittedAt = NOW - 2 * HOUR
    b.history = crewHist(b, [9 * HOUR, driver, 'Bắt đầu hành trình'], [3 * HOUR, driver, 'Hoàn tất giao ngựa'], [2 * HOUR, driver, 'Gửi bảng kê chi phí kèm chứng từ'])
    return b
  }
  const b23 = costed('ORD-2026-0123', 4, 'horse_health', [['vet_fee', 'Khám và truyền dịch', 1_200_000, 'customer'], ['medicine', 'Thuốc giảm đau', 350_000, 'customer'], ['rescue', 'Xe cứu hộ', 800_000, 'carrier']])

  // Xe đang chạy gặp sự cố ở từng bước xử lý (Flow 5)
  const stuck = (id: string, kind: Incident['kind'], state: Exclude<Incident['status'], 'resolved'>, from: string, to: string): Booking => {
    const b = running(dom(id, from, to), [bookingHorse(h(3))], [['VH-007', 'NV-02', [0]]], 33, 'in_transit', NOW - 3 * HOUR, 5, 'Trạm nghỉ Long Khánh', undefined, () => 2)
    const driver = crewName('NV-02')
    const where = mid(from, to)
    const plan = samplePlan(kind, where, findLocation(to)!, { note: 'Theo phương án trên bản đồ', newEta: NOW + 3 * HOUR, at: NOW - 1 * HOUR })
    const inc: Incident = { id: `INC-${id.slice(-4)}-1`, tripId: b.trips![0].tripId, kind, reportedBy: driver, reportedAt: NOW - 2 * HOUR, location: where, photo: 'sos_scene.jpg', note: kind === 'horse_health' ? 'Ngựa đau bụng, đổ mồ hôi' : kind === 'vehicle_breakdown' ? 'Hỏng điều hòa thùng xe' : 'Kẹt xe kéo dài do tai nạn phía trước', status: state, expenses: [] }
    if (state !== 'reported') inc.plan = plan
    if (state === 'active') { inc.approval = { at: NOW - 0.5 * HOUR, by: 'Quản lý' }; inc.expenses = [{ id: `EXP-${id.slice(-4)}-1`, category: 'other', label: 'Phí đường tránh', photo: 'other_receipt.jpg', amount: 150_000, payer: 'carrier', at: NOW - 0.3 * HOUR, by: driver }] }
    b.incidents = [inc]
    b.status = state === 'reported' ? 'incident_reported' : state === 'pending_approval' ? 'pending_emergency_approval' : 'emergency_plan_active'
    b.history = crewHist(b, [3 * HOUR, driver, 'Bắt đầu hành trình'], [2 * HOUR, driver, `SOS xe ${b.trips![0].tripId}`])
    return b
  }
  const b25 = stuck('ORD-2026-0125', 'horse_health', 'reported', 'KHO-DN', 'KHO-LA')
  const b26 = stuck('ORD-2026-0126', 'vehicle_breakdown', 'pending_approval', 'KHO-LA', 'KHO-BD')
  const b27 = stuck('ORD-2026-0127', 'traffic_jam', 'active', 'KHO-BD', 'KHO-DN')

  // Đã phát hành quyết toán (còn hạn, quá hạn) và đã hoàn tất (Flow 6)
  const settled = (b: Booking, status: Booking['status'], issuedAgo: number, paid?: number): Booking => {
    const { items, total } = settlementOf(b)
    const issuedAt = NOW - issuedAgo * HOUR
    b.status = status
    b.settlement = { items, total, issuedAt, dueAt: issuedAt + 24 * HOUR, by: 'Quản lý', ...(paid ? { paid: { paidAt: NOW - paid * HOUR, amount: total, reference: `EQZ-${b.id.slice(-4)}-FIN` } } : {}) }
    if (status === 'completed') b.rating = { trip: 5, driver: 5, escort: 4, comment: 'Ngựa đến nơi khỏe, đội ngũ chu đáo.', at: NOW - (paid ?? 1) * HOUR }
    b.history = [...b.history, { time: issuedAt, actor: 'Quản lý', text: 'Đối soát chi phí và phát hành bảng quyết toán' }]
    return b
  }
  const b28 = settled(costed('ORD-2026-0128', 6, 'horse_health', [['vet_fee', 'Khám và truyền dịch', 900_000, 'customer'], ['rescue', 'Xe cứu hộ', 600_000, 'carrier']]), 'settlement_issued', 3)
  const b29 = settled(costed('ORD-2026-0129', 1, 'horse_health', [['vet_fee', 'Khám tại phòng khám', 1_500_000, 'customer']]), 'payment_overdue', 30)
  const b30 = settled(costed('ORD-2026-0130', 2, 'vehicle_breakdown', [['repair', 'Sửa điều hòa thùng xe', 1_100_000, 'carrier']]), 'completed', 20, 18)

  // Xe đã đến điểm đón nhưng khách chưa trả 70%: chặn Bắt đầu hành trình
  const b20 = paid(dom('ORD-2026-0120', 'KHO-BD', 'CLB-SG'), [bookingHorse(h(2))], [['VH-010', 'NV-01', [0]]], 1, 35, 'en_route_to_pickup', clr('domestic', allDone('domestic'), { ...accepted, doneAt: NOW - 60 * HOUR, doneBy: staffKD1.name }))
  b20.trips = [{ ...ackAll(b20.trips![0], tripEtd(1), NOW - 1 * HOUR), driverPack: driverPack(b20) }]
  b20.history = crewHist(b20, [1 * HOUR, crewName('TX-10'), 'Xe bắt đầu di chuyển đến điểm đón ngựa'])

  // ===== Đơn nhiều xe: 6 ngựa chia 3 + 3 cho hai xe 4 chỗ =====
  const six = [1, 2, 3, 4, 5, 6].map(n => bookingHorse(h(n)))
  const b21 = paid(intl('ORD-2026-0121', 'KHO-DN', 'KHO-PNH', 'Mộc Bài – Bavet'), six, [['VH-009', 'NV-01', [0, 1, 2]], ['VH-010', 'NV-05', [3, 4, 5]]], 70, 5, 'waybill_issued', clr('international', []))
  // Hai xe, 4 ngựa: xe 1 đã chạy qua trạm nghỉ, xe 2 đang đến điểm đón
  const b22 = running(dom('ORD-2026-0122', 'KHO-DN', 'KHO-LA'), [bookingHorse(h(1)), bookingHorse(h(2)), bookingHorse(h(3)), bookingHorse(h(6))], [['VH-002', 'NV-01', [0, 1]], ['VH-003', 'NV-05', [2, 3]]], 33, 'in_transit', NOW - 5 * HOUR, 5, 'Trạm nghỉ Long Khánh', undefined, i => (i === 0 ? 2 : undefined))
  b22.trips![1] = { ...b22.trips![1], departedAt: NOW - 1 * HOUR }
  b22.history = crewHist(b22, [5 * HOUR, 'Lê Văn C', 'Xe 1 bắt đầu hành trình'])

  // Khách demo chỉ giữ một số đơn đại diện; các đơn còn lại thuộc khách khác để nhân sự nội bộ vẫn thấy đủ đơn ở mọi trạng thái
  const OTHER_CUSTOMER = 'CLB Ngựa Phương Nam'
  const OTHER = ['ORD-2026-0111', 'ORD-2026-0113', 'ORD-2026-0117', 'ORD-2026-0118', 'ORD-2026-0121', 'ORD-2026-0122', 'ORD-2026-0125', 'ORD-2026-0126', 'ORD-2026-0127', 'ORD-2026-0128', 'ORD-2026-0129', 'ORD-2026-0130', 'ORD-2026-0131', 'ORD-2026-0132']
  const all: Booking[] = [
    { ...common, ...intl('ORD-2026-0101', 'KHO-DN', 'KHO-PNH', 'Mộc Bài – Bavet'), gate: undefined, createdAt: NOW - 3 * HOUR, departAt: daysFromToday(45), consignee: consignee('Trung tâm Kiểm dịch Phnom Penh', 'Phnom Penh, Campuchia'),
      horses: b1Horses, status: 'pending_intake', history: [log(NOW - 3 * HOUR, CUSTOMER.name, 'Gửi yêu cầu đặt đơn')] },

    { ...common, ...dom('ORD-2026-0102', 'KHO-LA', 'CLB-SG'), createdAt: NOW - 1 * DAY, departAt: daysFromToday(38), consignee: consignee('CLB Cưỡi ngựa Sài Gòn', 'Quận 2, TP.HCM'),
      horses: b2Horses, status: 'under_review', intake: intake(NOW - 20 * HOUR), medical: { status: 'pending' },
      trips: mkTrips('ORD-2026-0102', [['VH-002', 'NV-01', [0, 1, 2]]], b2Horses), clearance: blankClearance('domestic'),
      history: [log(NOW - 1 * DAY, CUSTOMER.name, 'Gửi yêu cầu đặt đơn'), log(NOW - 20 * HOUR, 'Quản lý', 'Tiếp nhận. Giao Phạm Văn Hưng (kiểm dịch) và Trần Minh (điều phối)')] },

    // Hai đơn quốc tế đang thẩm định, chưa có xe và lộ trình: để Coordinator thử chọn cửa khẩu và trạm nghỉ trên bản đồ
    { ...common, ...intl('ORD-2026-0131', 'KHO-DN', 'KHO-PNH', 'Mộc Bài – Bavet'), gate: undefined, createdAt: NOW - 8 * HOUR, departAt: daysFromToday(42), consignee: consignee('Trung tâm Kiểm dịch Phnom Penh', 'Phnom Penh, Campuchia'),
      horses: [bookingHorse(h(4)), bookingHorse(h(5))], status: 'under_review', intake: intake(NOW - 6 * HOUR, staffKD1, staffDP1), medical: { status: 'pending' }, clearance: blankClearance('international'),
      history: [log(NOW - 8 * HOUR, CUSTOMER.name, 'Gửi yêu cầu đặt đơn'), log(NOW - 6 * HOUR, 'Quản lý', 'Tiếp nhận. Giao Phạm Văn Hưng (kiểm dịch) và Trần Minh (điều phối)')] },

    { ...common, ...intl('ORD-2026-0132', 'KHO-DN', 'KHO-VTE', 'Lao Bảo – Densavanh'), gate: undefined, createdAt: NOW - 7 * HOUR, departAt: daysFromToday(47), consignee: consignee('Trang trại Chăn nuôi & Kiểm dịch Vientiane', 'Viêng Chăn, Lào'),
      horses: [bookingHorse(h(6))], status: 'under_review', intake: intake(NOW - 5 * HOUR, staffKD2, staffDP1), medical: { status: 'pending' }, clearance: blankClearance('international'),
      history: [log(NOW - 7 * HOUR, CUSTOMER.name, 'Gửi yêu cầu đặt đơn'), log(NOW - 5 * HOUR, 'Quản lý', 'Tiếp nhận. Giao Nguyễn Thị Thu (kiểm dịch) và Trần Minh (điều phối)')] },

    { ...b3, intake: intake(NOW - 40 * HOUR, staffKD1, staffDP3), quote: undefined, clearance: blankClearance('domestic'),
      plan: planDone(staffDP3.name, 20),
      medical: { status: 'resubmit', at: NOW - 5 * HOUR, by: staffKD1.name, resubmit: { reason: 'Phiếu xét nghiệm EIA bị mờ, không đọc được ngày lấy mẫu. Vui lòng tải lại bản rõ nét.', items: [{ horseId: h(3).id, doc: 'lab' }], at: NOW - 5 * HOUR } },
      history: [log(NOW - 2 * DAY, CUSTOMER.name, 'Gửi yêu cầu đặt đơn'), log(NOW - 40 * HOUR, 'Quản lý', 'Tiếp nhận, giao Phạm Văn Hưng và Phạm Tâm'), log(NOW - 20 * HOUR, staffDP3.name, 'Xác nhận phương án xe và lộ trình'), log(NOW - 5 * HOUR, staffKD1.name, 'Yêu cầu khách bổ sung xét nghiệm EIA của Kim Lân')] },

    { ...b4, quote: undefined, clearance: blankClearance('international'), intake: intake(NOW - 2 * DAY, staffKD2, staffDP1), medical: medicalOk(NOW - 30 * HOUR, staffKD2.name),
      history: [log(NOW - 3 * DAY, CUSTOMER.name, 'Gửi yêu cầu đặt đơn'), log(NOW - 2 * DAY, 'Quản lý', 'Tiếp nhận, giao Nguyễn Thị Thu và Trần Minh'), log(NOW - 36 * HOUR, staffDP1.name, 'Xác nhận phương án xe và lộ trình'), log(NOW - 30 * HOUR, staffKD2.name, 'Duyệt hồ sơ ngựa')] },

    { ...b5, clearance: blankClearance('domestic'), intake: intake(NOW - 3 * DAY, staffKD2, staffDP1), medical: medicalOk(NOW - 2 * DAY, staffKD2.name), quote: draftQuote(b5, ['VH-009'], NOW - 10 * HOUR),
      history: [log(NOW - 4 * DAY, CUSTOMER.name, 'Gửi yêu cầu đặt đơn'), log(NOW - 10 * HOUR, 'Quản lý', 'Duyệt và gửi báo giá')] },

    { ...b6, history: [log(NOW - 6 * DAY, CUSTOMER.name, 'Gửi yêu cầu đặt đơn'), log(NOW - 2 * DAY, 'Quản lý', 'Duyệt và gửi báo giá'), log(NOW - 1 * DAY, CUSTOMER.name, `Đặt cọc 30%, cấp Vận đơn ${waybillNoOf('ORD-2026-0106')}`)] },

    { ...b7, clearance: blankClearance('domestic'), intake: intake(NOW - 6 * DAY, staffKD2, staffDP3), medical: medicalOk(NOW - 5 * DAY, staffKD2.name), quote: draftQuote(b7, ['VH-006'], NOW - 3 * DAY),
      history: [log(NOW - 7 * DAY, CUSTOMER.name, 'Gửi yêu cầu đặt đơn'), log(NOW - 3 * DAY, 'Quản lý', 'Duyệt và gửi báo giá'), log(NOW - 1 * DAY, 'Hệ thống', 'Quá 48 giờ chưa đặt cọc: báo giá hết hạn, nhả xe và nhân sự')] },

    b10, b11, b12, b13, b15, b16, b17, b18, b19, b20, b21, b22, b23, b25, b26, b27, b28, b29, b30, b34, b35, b36, b37,
  ]
  // Xe, tài xế, hộ tống do Điều phối viên chọn khi lập lộ trình: đơn chưa chốt phương án thì chưa có chuyến nào
  const unplanned = (o: Booking) => (o.status === 'pending_intake' || o.status === 'under_review') && !o.plan ? { ...o, trips: undefined } : o
  return all.map(unplanned).map(o => (OTHER.includes(o.id) ? { ...o, customer: OTHER_CUSTOMER, consignor: { ...o.consignor, name: OTHER_CUSTOMER } } : o))
}
