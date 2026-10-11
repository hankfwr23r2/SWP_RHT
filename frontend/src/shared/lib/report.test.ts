import { describe, expect, it } from 'vitest'
import { MARGIN_RATE } from '../config/booking-rules'
import { DAY } from '../config/business-rules'
import { seedBookings } from '../services/mock/bookings'
import type { Booking } from '../types/booking'
import { changePct, orderFinance, series, summarize } from './report'

const all = seedBookings()
const NOW = Date.now()
const quoted = (over: Partial<Booking> = {}): Booking => ({
  ...all.find(b => b.payment && b.quote && b.status !== 'cancelled')!, incidents: [], settlement: undefined, cancellation: undefined, ...over,
})

describe('doanh thu và chi phí vận hành của một đơn', () => {
  it('đơn chưa cọc thì chưa có doanh thu', () => {
    expect(orderFinance(all.find(b => !b.payment)!)).toBeUndefined()
  })
  it('giá vốn bỏ biên 5% khỏi cước, nhân sự, nhiên liệu; các dòng còn lại tính nguyên', () => {
    const b = quoted()
    const f = orderFinance(b)!
    const raw = b.quote!.lines.reduce((t, l) => t + (/^(Cước vận chuyển|Nhân sự kỹ thuật|Nhiên liệu và BOT)/.test(l.label) ? l.amount / (1 + MARGIN_RATE) : l.amount), 0)
    expect(f.revenue).toBe(b.quote!.total)
    expect(f.cost).toBe(Math.round(raw))
    expect(f.profit).toBe(f.revenue - f.cost)
    expect(f.profit).toBeGreaterThan(0)
  })
  it('chi phí sự cố nhà xe chịu làm tăng chi phí; khoản khách chịu cộng vào doanh thu', () => {
    const base = orderFinance(quoted())!
    const withInc = orderFinance(quoted({
      incidents: [{ id: 'I', tripId: 'T', kind: 'vehicle_breakdown', reportedBy: 'x', reportedAt: 1, location: { lat: 1, lng: 1 }, photo: 'a', note: '', status: 'resolved', expenses: [
        { id: 'e1', category: 'rescue', label: 'Cứu hộ', photo: 'p', amount: 800_000, payer: 'carrier', at: 1, by: 'x' },
        { id: 'e2', category: 'vet_fee', label: 'Thú y', photo: 'p', amount: 500_000, payer: 'customer', at: 1, by: 'x' },
      ] }],
      settlement: { items: [], total: 500_000, issuedAt: 1, dueAt: 2, by: 'x' },
    }))!
    expect(withInc.cost - base.cost).toBe(800_000)
    expect(withInc.revenue - base.revenue).toBe(500_000)
  })
})

describe('tổng hợp báo cáo', () => {
  it('doanh thu - chi phí = lợi nhuận; tỷ lệ nằm trong 0–100', () => {
    const s = summarize(all, NOW - 90 * DAY, NOW + DAY)
    expect(s.orders).toBeGreaterThan(5)
    expect(s.profit).toBe(s.revenue - s.cost)
    for (const r of [s.completionRate, s.onTimeRate, s.incidentRate]) { expect(r).toBeGreaterThanOrEqual(0); expect(r).toBeLessThanOrEqual(100) }
    expect(s.delivered).toBeLessThanOrEqual(s.trips)
  })
  it('khoảng không có đơn nào thì mọi số bằng 0, không chia cho 0', () => {
    const s = summarize(all, 0, 1)
    expect(s).toMatchObject({ revenue: 0, cost: 0, orders: 0, trips: 0, completionRate: 0, onTimeRate: 0, avgHours: 0, avgRating: 0 })
  })
  it('chuỗi theo ngày cộng lại bằng tổng', () => {
    const from = NOW - 30 * DAY, to = NOW + DAY
    const pts = series(all, from, to)
    const s = summarize(all, from, to)
    expect(pts.reduce((t, p) => t + p.revenue, 0)).toBe(s.revenue)
    expect(pts.reduce((t, p) => t + p.cost, 0)).toBe(s.cost)
    expect(series(all, NOW - 90 * DAY, to).length).toBeLessThan(20) // khoảng dài gộp theo tuần
  })
  it('changePct không so được khi kỳ trước bằng 0', () => {
    expect(changePct(120, 100)).toBe(20)
    expect(changePct(5, 0)).toBeUndefined()
  })
  it('đơn hủy: doanh thu là phần tiền khách đã trả mà không được hoàn', () => {
    const b = quoted({ status: 'cancelled', cancellation: { at: 1, reason: '', by: 'customer', refund: 1_000_000 } })
    const f = orderFinance(b)!
    expect(f.revenue).toBe(b.payment!.amount + (b.balance?.amount ?? 0) - 1_000_000)
    expect(f.cost).toBe(0)
  })
})
