// Báo cáo của Quản lý: doanh thu, chi phí vận hành và hiệu suất hoàn thành chuyến, tính từ các đơn (PRD mục 15).
// Số tiền là số mẫu theo biểu giá mẫu (PRD mục 14.1). Khi có backend phải tính lại ở server.
import { DELAY_ALERT_MINUTES, MARGIN_RATE } from '../config/booking-rules'
import { DAY } from '../config/business-rules'
import type { Booking, VehicleTrip } from '../types/booking'

// Dòng báo giá đã gộp biên lợi nhuận 5% (cước, nhân sự, nhiên liệu và BOT): giá vốn = tiền / (1 + biên). Dòng còn lại tính nguyên.
const MARGINED = /^(Cước vận chuyển|Nhân sự kỹ thuật|Nhiên liệu và BOT)/

export interface OrderFinance {
  id: string; customer: string; at: number // at = ngày đặt cọc (đơn có hiệu lực)
  revenue: number // doanh thu ghi nhận: giá trị đơn + phát sinh khách chịu; đơn hủy thì phần cọc không hoàn
  cost: number // chi phí vận hành: giá vốn theo báo giá + chi phí sự cố nhà xe chịu
  profit: number
  collected: number // tiền đã thu thực tế
  status: Booking['status']
}

export function orderFinance(b: Booking): OrderFinance | undefined {
  if (!b.payment) return undefined // chưa cọc thì chưa có doanh thu
  const paid = b.payment.amount + (b.balance?.amount ?? 0)
  const carrierExpenses = (b.incidents ?? []).flatMap(i => i.expenses).filter(e => e.payer === 'carrier').reduce((t, e) => t + e.amount, 0)
  const extras = b.settlement?.total ?? 0
  const base = { id: b.id, customer: b.customer, at: b.payment.paidAt, status: b.status }
  if (b.status === 'cancelled') {
    const kept = paid - (b.cancellation?.refund ?? 0)
    return { ...base, revenue: kept, cost: carrierExpenses, profit: kept - carrierExpenses, collected: kept }
  }
  if (!b.quote) return undefined
  const cogs = b.quote.lines.reduce((t, l) => t + (MARGINED.test(l.label) ? l.amount / (1 + MARGIN_RATE) : l.amount), 0)
  const revenue = b.quote.total + extras
  const cost = Math.round(cogs) + carrierExpenses
  return { ...base, revenue, cost, profit: revenue - cost, collected: paid + (b.settlement?.paid?.amount ?? 0) }
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
  revenue: number; cost: number; profit: number; margin: number; collected: number; orders: number
  trips: number; delivered: number; completionRate: number; onTimeRate: number; incidentRate: number; avgHours: number; avgRating: number
}
const sum = (a: number[]) => a.reduce((t, x) => t + x, 0)
const pct = (n: number, d: number) => (d ? (n / d) * 100 : 0)

// Tổng hợp trong khoảng [from, to): tài chính theo ngày đặt cọc, chuyến theo ngày xuất phát
export function summarize(bookings: Booking[], from: number, to: number): ReportSummary {
  const fin = bookings.map(orderFinance).filter((f): f is OrderFinance => !!f && f.at >= from && f.at < to)
  const perf = bookings.flatMap(b => (b.trips ?? []).map(t => tripPerformance(b, t))).filter((p): p is TripPerf => !!p && p.startedAt >= from && p.startedAt < to)
  const done = perf.filter(p => p.deliveredAt)
  const ratings = bookings.filter(b => b.rating && b.rating.at >= from && b.rating.at < to).map(b => (b.rating!.trip + b.rating!.driver + b.rating!.escort) / 3)
  const revenue = sum(fin.map(f => f.revenue)), cost = sum(fin.map(f => f.cost))
  return {
    revenue, cost, profit: revenue - cost, margin: pct(revenue - cost, revenue), collected: sum(fin.map(f => f.collected)), orders: fin.length,
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
