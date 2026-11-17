import { incidentExpenseItems } from './incidentCosts'
// Báo cáo của Quản lý: tính toán tổng hợp doanh thu, chi phí vận hành và hiệu suất từ dữ liệu đơn hàng backend (PRD mục 15).
import { BOOKING_STATUS, DELAY_ALERT_MINUTES, MARGIN_RATE, type BookingStatus } from '../config/booking-rules'
import { DAY } from '../config/business-rules'
import type { Booking, VehicleTrip } from '../types/booking'

// marginApplied distinguishes new quote lines; missing values retain the legacy calculation.
const MARGINED = /^(Cước vận chuyển|Nhân sự kỹ thuật|Nhiên liệu và BOT)/

export function bookingStatusLabel(status: string | undefined): string {
  if (!status) return 'Không xác định'
  return Object.hasOwn(BOOKING_STATUS, status)
    ? BOOKING_STATUS[status as BookingStatus].label
    : status
}

export interface OrderFinance {
  id: string; customer: string; at: number // at = ngày đặt cọc (đơn có hiệu lực)
  insuranceCollection: number // phí thu hộ trong báo giá, đơn hủy dùng số đã thu
  grossRevenue: number // doanh thu vận chuyển trước chi phí sự cố
  incidentCost: number // thực chi sự cố, có chứng từ
  revenue: number // doanh thu công ty sau chi phí sự cố
  cost: number // giá vốn vận hành gốc; chi phí sự cố đã trừ khỏi revenue
  profit: number
  collected: number // tiền đã thu thực tế
  status: Booking['status']
}

export function orderFinance(b: Booking): OrderFinance | undefined {
  if (!b.payment) return undefined // chưa cọc thì chưa có doanh thu
  const paid = b.payment.amount + (b.balance?.amount ?? 0)
  const incidentCost = incidentExpenseItems(b).reduce((total, item) => total + item.amount, 0)
  const base = { id: b.id, customer: b.customer, at: b.payment.paidAt, status: b.status }
  if (b.status === 'cancelled') {
    const kept = paid - (b.cancellation?.refund ?? 0)
    const grossRevenue = b.cancellation?.penalty ?? (kept - (b.cancellation?.insuranceCollected ?? 0))
    const revenue = grossRevenue - incidentCost
    return { ...base, grossRevenue, incidentCost, revenue, insuranceCollection: b.cancellation?.insuranceCollected ?? 0, cost: 0, profit: revenue, collected: kept }
  }
  if (!b.quote) return undefined
  const lines = Array.isArray(b.quote.lines) ? b.quote.lines : []
  const insuranceCollection = lines.filter(l => l.label === 'Thu hộ bảo hiểm bên thứ ba').reduce((total, line) => total + line.amount, 0)
  const cogs = lines.filter(l => l.label !== 'Thu hộ bảo hiểm bên thứ ba').reduce((t, l) => {
    const marginApplied = l.marginApplied ?? MARGINED.test(l.label)
    return t + (marginApplied ? l.amount / (1 + MARGIN_RATE) : l.amount)
  }, 0)
  const grossRevenue = (b.quote.total ?? 0) - insuranceCollection
  const revenue = grossRevenue - incidentCost
  const cost = Math.round(cogs)
  return { ...base, grossRevenue, incidentCost, revenue, insuranceCollection, cost, profit: revenue - cost, collected: paid + (b.settlement?.paid?.amount ?? 0) }
}

export interface TripPerf { orderId: string; tripId: string; startedAt: number; deliveredAt?: number; onTime?: boolean; hours?: number; hadIncident: boolean }
export function tripPerformance(b: Booking, t: VehicleTrip): TripPerf | undefined {
  const run = t.run
  if (!run?.startedAt) return undefined
  const delivery = run.checkpoints.find(c => c.type === 'delivery')
  const done = run.deliveredAt
  return {
    orderId: b.id, tripId: t.tripId, startedAt: run.startedAt, deliveredAt: done,
    onTime: done && delivery ? (delivery.arrivedAt ?? done) <= delivery.plannedAt + DELAY_ALERT_MINUTES * 60_000 : undefined,
    hours: done ? (done - run.startedAt) / 3_600_000 : undefined,
    hadIncident: (b.incidents ?? []).some(i => i.tripId === t.tripId),
  }
}

export interface ReportSummary {
  grossRevenue: number; incidentCost: number; revenue: number; cost: number; profit: number; margin: number; collected: number; orders: number
  trips: number; delivered: number; completionRate: number; onTimeRate: number; incidentRate: number; avgHours: number; avgRating: number
}
const sum = (a: number[]) => a.reduce((t, x) => t + x, 0)
const pct = (n: number, d: number) => (d ? (n / d) * 100 : 0)

// Tổng hợp trong khoảng [from, to): tài chính theo ngày đặt cọc, chuyến theo ngày xuất phát
export function summarize(bookings: Booking[], from: number, to: number): ReportSummary {
  const fin = bookings.map(orderFinance).filter((f): f is OrderFinance => !!f && f.at >= from && f.at < to)
  const perf = bookings.flatMap(b => (b.trips ?? []).map(t => tripPerformance(b, t))).filter((p): p is TripPerf => !!p && p.startedAt >= from && p.startedAt < to)
  const done = perf.filter(p => p.deliveredAt)
  const ratings = bookings
    .filter(b => b.rating && b.rating.at >= from && b.rating.at < to)
    .map(b => b.rating!.stars ?? ((b.rating!.trip ?? 0) + (b.rating!.driver ?? 0) + (b.rating!.escort ?? 0)) / 3)
  const revenue = sum(fin.map(f => f.revenue)), cost = sum(fin.map(f => f.cost))
  return {
    grossRevenue: sum(fin.map(f => f.grossRevenue)), incidentCost: sum(fin.map(f => f.incidentCost)),
    revenue, cost, profit: revenue - cost, margin: revenue > 0 ? pct(revenue - cost, revenue) : 0, collected: sum(fin.map(f => f.collected)), orders: fin.length,
    trips: perf.length, delivered: done.length, completionRate: pct(done.length, perf.length),
    onTimeRate: pct(done.filter(p => p.onTime).length, done.length), incidentRate: pct(perf.filter(p => p.hadIncident).length, perf.length),
    avgHours: done.length ? sum(done.map(p => p.hours!)) / done.length : 0, avgRating: ratings.length ? sum(ratings) / ratings.length : 0,
  }
}

// Mức thay đổi so với kỳ trước, tính bằng %; kỳ trước bằng 0 thì không so được
export const changePct = (now: number, before: number) => (before ? ((now - before) / before) * 100 : undefined)

export interface DayPoint { label: string; at: number; revenue: number; cost: number; trips: number }
// Chuỗi theo ngày (hoặc theo tuần khi khoảng dài hơn 31 ngày) cho biểu đồ
export function series(bookings: Booking[], from: number, to: number): DayPoint[] {
  const days = Math.round((to - from) / DAY)
  const bucket = days > 31 ? 7 * DAY : DAY
  const n = Math.ceil((to - from) / bucket)
  const pts: DayPoint[] = Array.from({ length: n }, (_, i) => {
    const d = new Date(from + i * bucket)
    return { label: `${d.getDate()}/${d.getMonth() + 1}`, at: from + i * bucket, revenue: 0, cost: 0, trips: 0 }
  })
  const at = (t: number) => Math.floor((t - from) / bucket)
  for (const b of bookings) {
    const f = orderFinance(b)
    if (f && f.at >= from && f.at < to) { const p = pts[at(f.at)]; p.revenue += f.revenue; p.cost += f.cost }
    for (const t of b.trips ?? []) { const d = t.run?.deliveredAt; if (d && d >= from && d < to) pts[at(d)].trips += 1 }
  }
  return pts
}
