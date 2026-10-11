// Service chuyến xe của Điều phối, Tài xế, Hộ tống. Gốc: Fleet And Route/ops_data.js (các hàm chuyển trạng thái).
// Trạng thái chuyến suy từ đơn (docs/PRD.md mục 3, 8):
//   đơn ở bước lập lộ trình: chưa khảo sát → pending_assessment · khảo sát đạt → awaiting_routing
//   khảo sát không đạt → rejected_assessment (đơn trả về Manager ở trang Phê duyệt)
//   đã chốt lộ trình (chờ duyệt / chờ thanh toán / đã thanh toán) → assigned
//   đơn đang vận chuyển → in_transit · đã giao → done
// Sau này: GET /api/trips, POST /api/trips/{id}/assess, /confirm-route, /depart, /check-in, /health-logs
import { HOUR } from '../config/business-rules'
import { legsFromStops, splitStop, tripHours } from '../lib/trip'
import type { Checkpoint, HealthLog, Order, Vitals } from '../types/order'
import { crewApi, vehiclesApi } from './fleet'
import { seedDriverOf } from './mock/fleet'
import { incidentsApi } from './incidents'
import { seedTrips, type Leg, type OpsTrip } from './mock/trips'
import { ordersApi } from './orders'
import { createStore } from './store'

export type TripStatus = 'pending_assessment' | 'awaiting_routing' | 'assigned' | 'in_transit' | 'done' | 'rejected_assessment'

export const TRIP_STATUS_LABEL: Record<TripStatus, string> = {
  pending_assessment: 'Chờ khảo sát',
  awaiting_routing: 'Đã khảo sát, chờ lập LT',
  assigned: 'Đã chốt lộ trình, chờ khởi hành',
  in_transit: 'Đang vận chuyển',
  done: 'Hoàn thành',
  rejected_assessment: 'Không khả thi, trả quản lý',
}

export interface TripView extends OpsTrip { status: TripStatus; order: Order }

const store = createStore<OpsTrip>('trips', seedTrips)

export function tripStatus(t: OpsTrip, o: Order): TripStatus | null {
  if (o.status === 'in_transit') return 'in_transit'
  if (['delivered', 'disputed', 'completed'].includes(o.status)) return 'done'
  if (o.status === 'rejected' || o.status === 'cancelled') return null
  if (t.feasible === false) return 'rejected_assessment'
  if (o.stage === 'routing') return t.feasible ? 'awaiting_routing' : 'pending_assessment'
  if (o.stage === 'approval' || o.status === 'awaiting_payment' || o.status === 'paid') return 'assigned'
  return null
}

// Đủ khi mọi chặng đã có xe, tài xế, hộ tống
export const assignmentComplete = (t: OpsTrip) => t.legs.length > 0 && t.legs.every(l => l.vehicleId && l.driverId && l.escortId)

// Đơn vừa chuyển sang bước lập lộ trình (kiểm dịch xác nhận hợp lệ) thì mở chuyến mới, 1 chặng đi thẳng
function ensureTrips(orders: Order[]) {
  orders.filter(o => o.stage === 'routing' && !store.all().some(t => t.orderId === o.id)).forEach(o => {
    const next = Math.max(...store.all().map(t => Number(t.id.slice(3)))) + 1
    store.add({ id: `TR-${next}`, orderId: o.id, assessNote: '', legs: [{ no: 1, from: o.from, to: o.to, vehicleId: '', driverId: '', escortId: '' }], activity: [] })
  })
}

async function view(): Promise<TripView[]> {
  const orders = await ordersApi.list()
  ensureTrips(orders)
  return store.all().flatMap(t => {
    const order = orders.find(o => o.id === t.orderId)
    const status = order && tripStatus(t, order)
    return order && status ? [{ ...structuredClone(t), status, order }] : []
  })
}

// Hộ tống tự động: người đang phụ trách ít chặng nhất (chuyến chưa xong)
async function autoEscort(excludeTripId: string) {
  const escorts = (await crewApi.list()).filter(c => c.role === 'escort')
  const trips = await view()
  const load = (id: string) => trips.filter(t => t.status !== 'done' && t.id !== excludeTripId).flatMap(t => t.legs).filter(l => l.escortId === id).length
  return escorts.map(e => ({ id: e.id, load: load(e.id) })).sort((a, b) => a.load - b.load || a.id.localeCompare(b.id))[0]?.id ?? ''
}

const setLegs = (id: string, legs: Leg[]) => store.update(id, { legs: legs.map((l, i) => ({ ...l, no: i + 1 })) })

// Điểm dừng hiện cho khách khi đơn chưa có: điểm nhận → các điểm nối chặng → điểm giao
function stopsFromLegs(legs: Leg[]) {
  const act = (place: string) => (place.startsWith('Cửa khẩu') ? 'thông quan' : place.startsWith('Trạm') ? 'nghỉ đêm' : 'dừng chân')
  return [`${legs[0].from} — nhận ngựa`, ...legs.slice(1).map(l => `${l.from} — ${act(l.from)}`), `${legs[legs.length - 1].to} — giao ngựa`]
}

// Ghi xe / tài xế / hộ tống / điểm dừng của chuyến vào đơn (Manager duyệt, khách xem theo đơn)
async function syncReview(id: string, extra: Partial<Order> = {}) {
  const t = store.get(id)!
  const [vehicles, crew, order] = await Promise.all([vehiclesApi.list(), crewApi.list(), ordersApi.get(t.orderId)])
  const o = order!
  const unique = (ids: string[]) => [...new Set(ids.filter(Boolean))]
  const name = (cid: string) => crew.find(c => c.id === cid)!.name
  const vehicle = unique(t.legs.map(l => l.vehicleId)).map(vid => vehicles.find(v => v.id === vid)!).map(v => `${v.plate} (${v.name.toLowerCase()})`).join(', ')
  // Giữ điểm dừng cũ nếu chặng không đổi (điểm dừng cũ có cả bước thông quan / kiểm tra thú y)
  const same = JSON.stringify(legsFromStops(o.stops, o.from, o.to)) === JSON.stringify(t.legs.map(l => [l.from, l.to]))
  await ordersApi.update(t.orderId, {
    ...extra,
    stops: same && o.stops ? o.stops : stopsFromLegs(t.legs),
    review: { inspectNote: o.review?.inspectNote ?? 'Giấy tờ hợp lệ.', vehicle, driver: unique(t.legs.map(l => l.driverId)).map(name).join(', '), grooms: unique(t.legs.map(l => l.escortId)).map(name).join(', ') },
  })
}
// Đổi phân công sau khi đã chốt lộ trình: cập nhật lại thông tin trình Manager
async function resyncIfRouted(id: string) {
  const o = await ordersApi.get(store.get(id)!.orderId)
  if (o && o.stage !== 'routing' && o.status !== 'in_transit') await syncReview(id)
}

const CHECKPOINT_LABEL: Record<string, string> = { 'nhận ngựa': 'Nhận ngựa lên xe, khởi hành', 'giao ngựa': 'Giao ngựa' }
const capitalize = (s: string) => s.charAt(0).toUpperCase() + s.slice(1)

function vitalsOf(log: HealthLog | undefined, fallbackTime: number): Vitals {
  const ok = !log?.status || log.status === 'Bình thường'
  return {
    time: log?.time ?? fallbackTime, temp: parseFloat(log?.temp ?? ''), heart: parseFloat(log?.heart ?? ''),
    eat: ok ? 'Bình thường' : log!.status!, body: ok ? 'Không chấn thương' : log!.note,
  }
}

export const tripsApi = {
  list: view,
  get: async (id: string) => (await view()).find(t => t.id === id),

  // OPS-03: kết luận khảo sát. Không khả thi → đơn trả về Manager ở trang Phê duyệt kèm lý do.
  async assess(id: string, feasible: boolean, note: string, by: string) {
    const t = store.update(id, { feasible, assessNote: note, assessedAt: Date.now() })
    if (!feasible) await ordersApi.update(t.orderId, { stage: 'approval', infeasible: { note, at: Date.now(), by } })
  },

  // Điều phối chọn XE: tài xế mặc định của dữ liệu cũ, hộ tống tự gán người rảnh nhất
  async setVehicle(id: string, legNo: number, vehicleId: string) {
    const v = (await vehiclesApi.list()).find(x => x.id === vehicleId)
    const escortId = v ? await autoEscort(id) : ''
    setLegs(id, store.get(id)!.legs.map(l => (l.no === legNo ? { ...l, vehicleId, driverId: v ? seedDriverOf(v.id) : '', escortId } : l)))
    await resyncIfRouted(id)
  },
  // Đổi hộ tống tay; bỏ trống = về chế độ tự động
  async setEscort(id: string, legNo: number, escortId: string) {
    const chosen = escortId || await autoEscort(id)
    setLegs(id, store.get(id)!.legs.map(l => (l.no === legNo ? { ...l, escortId: l.vehicleId ? chosen : '' } : l)))
    await resyncIfRouted(id)
  },
  // Chia chặng: thêm điểm dừng giữa chặng legNo (xe giữ nguyên cho cả 2 chặng mới)
  splitLeg(id: string, legNo: number, stop: string) {
    setLegs(id, store.get(id)!.legs.flatMap(l => (l.no === legNo ? [{ ...l, to: stop }, { ...l, from: stop }] : [l])))
  },
  // Bỏ điểm dừng cuối chặng legNo: gộp với chặng sau
  mergeLeg(id: string, legNo: number) {
    const legs = store.get(id)!.legs
    const i = legs.findIndex(l => l.no === legNo)
    setLegs(id, [...legs.slice(0, i), { ...legs[i], to: legs[i + 1].to }, ...legs.slice(i + 2)])
  },

  // OPS-05: chốt lộ trình → đơn chuyển Manager duyệt, kèm xe / tài xế / hộ tống
  async confirmRoute(id: string) {
    await syncReview(id, { stage: 'approval' })
  },

  // OPS-08: khởi hành → đơn chuyển Đang vận chuyển; khách thấy hành trình và nhật ký sức khỏe
  async depart(id: string) {
    const t = store.get(id)!
    const [vehicles, crew, order] = await Promise.all([vehiclesApi.list(), crewApi.list(), ordersApi.get(t.orderId)])
    const o = order!
    const first = t.legs[0]
    const now = Date.now()
    const eta = now + tripHours(o.duration) * HOUR
    const stops = (o.stops ?? stopsFromLegs(t.legs)).map(splitStop)
    const checkpoints: Checkpoint[] = stops.map((p, i) => ({
      label: CHECKPOINT_LABEL[p.act] ?? capitalize(p.act),
      place: p.place,
      time: Math.round(now + (eta - now) * i / (stops.length - 1)),
      state: i === 0 ? 'done' : i === 1 ? 'current' : 'next',
    }))
    const member = (cid: string) => crew.find(c => c.id === cid)!
    await ordersApi.update(t.orderId, {
      status: 'in_transit',
      trip: {
        plate: vehicles.find(v => v.id === first.vehicleId)!.plate, eta, updatedAt: now,
        contacts: [['Tài xế', member(first.driverId).name, member(first.driverId).phone], ['NV chăm sóc', member(first.escortId).name, member(first.escortId).phone]],
        checkpoints,
        health: [{ time: now, temp: '—', heart: '—', note: 'Đã nhận ngựa lên xe. Hộ tống cập nhật chỉ số sức khỏe dọc đường.', by: member(first.escortId).name }],
      },
    })
  },

  // OPS-06: ghi nhận ảnh chặng
  logActivity(id: string, text: string) {
    const t = store.get(id)!
    store.update(id, { activity: [{ time: Date.now(), text }, ...t.activity].slice(0, 20) })
  },

  // Tài xế check-in mốc hiện tại. Mốc cuối (giao ngựa) → đơn Đã giao, khách có 24 giờ nghiệm thu.
  async checkIn(orderId: string) {
    const o = (await ordersApi.get(orderId))!
    const trip = o.trip!
    const i = trip.checkpoints.findIndex(c => c.state === 'current')
    const now = Date.now()
    const checkpoints = trip.checkpoints.map((c, k): Checkpoint => (k === i ? { ...c, time: now, state: 'done' } : k === i + 1 ? { ...c, state: 'current' } : c))
    const last = i === trip.checkpoints.length - 1
    if (!last) return ordersApi.update(orderId, { trip: { ...trip, checkpoints, updatedAt: now } })
    const t = store.all().find(x => x.orderId === orderId)!
    const [vehicles, crew] = await Promise.all([vehiclesApi.list(), crewApi.list()])
    const v = vehicles.find(x => x.id === t.legs[t.legs.length - 1].vehicleId)!
    const measured = trip.health.filter(h => !Number.isNaN(parseFloat(h.temp)))
    return ordersApi.update(orderId, {
      status: 'delivered', deliveredAt: now,
      trip: { ...trip, checkpoints, updatedAt: now },
      handover: {
        vehicle: `${v.name} · ${v.plate}`, driver: crew.find(c => c.id === seedDriverOf(v.id))?.name ?? '',
        groom: trip.contacts.find(c => c[0] === 'NV chăm sóc')?.[1] ?? '', inspector: o.inspector ?? '',
        pickup: vitalsOf(measured[measured.length - 1], trip.checkpoints[0].time),
        delivery: vitalsOf(measured[0], now),
      },
    })
  },

  // Hộ tống xóa toàn bộ báo cáo của mình (nút "Delete All Reports" của bản cũ); giữ dòng hệ thống ghi lúc khởi hành
  async deleteHealthLogs(by: string) {
    const orders = await ordersApi.list()
    await Promise.all(orders.filter(o => o.trip?.health.some(h => h.by === by && h.temp !== '—')).map(o =>
      ordersApi.update(o.id, { trip: { ...o.trip!, health: o.trip!.health.filter(h => h.by !== by || h.temp === '—') } })))
  },

  // Hộ tống ghi báo cáo sức khỏe. Tình trạng nặng → tự tạo sự cố "Y tế ngựa" cho Điều phối xử lý (OPS-04).
  async addHealthLog(orderId: string, log: HealthLog, severe: boolean) {
    const o = (await ordersApi.get(orderId))!
    await ordersApi.update(orderId, { trip: { ...o.trip!, health: [log, ...o.trip!.health], updatedAt: Date.now() } })
    if (!severe) return
    const t = store.all().find(x => x.orderId === orderId)!
    const current = o.trip!.checkpoints.findIndex(c => c.state === 'current')
    await incidentsApi.create({
      tripId: t.id, orderId, leg: `${Math.max(1, Math.min(current, t.legs.length))}/${t.legs.length}`, time: log.time,
      severity: 'emergency', type: 'Y tế ngựa', desc: `${log.horse}: ${log.status}${log.note ? ` — ${log.note}` : ''} (thân nhiệt ${log.temp})`,
      status: 'open', proposal: '',
    })
  },
}

export type { Leg, OpsTrip }
