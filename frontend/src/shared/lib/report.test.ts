import { describe, expect, it } from 'vitest'
import { MARGIN_RATE } from '../config/booking-rules'
import { DAY } from '../config/business-rules'
import type { Booking } from '../types/booking'
import { bookingStatusLabel, changePct, orderFinance, series, summarize } from './report'

const NOW = Date.now()
const LINES = [
  { label: 'Cước vận chuyển nguyên chuyến', amount: 10_500_000 },
  { label: 'Nhân sự kỹ thuật (01 Driver + 01 Escort)', amount: 1_890_000 },
  { label: 'Nhiên liệu và BOT', amount: 1_050_000 },
  { label: 'Phí thủ tục kiểm dịch và hải quan', amount: 300_000 },
]
const TOTAL = LINES.reduce((n, l) => n + l.amount, 0)
// Đơn dựng tại chỗ cho phép thử công thức báo cáo: đã cọc, có báo giá, một xe đã giao ngựa
const order = (i: number, over: Partial<Booking> = {}): Booking => {
  const paidAt = NOW - (i + 1) * 3 * DAY
  const startedAt = paidAt + DAY
  return {
    id: `T-${i}`, customer: 'Khách thử', createdAt: paidAt - DAY, status: 'completed',
    payment: { paidAt, amount: Math.round(TOTAL * 0.3), reference: `P-${i}` }, balance: { paidAt: startedAt, amount: TOTAL - Math.round(TOTAL * 0.3), reference: `B-${i}` },
    quote: { lines: LINES, adjustments: [], subtotal: TOTAL, total: TOTAL, deposit: Math.round(TOTAL * 0.3), balance: TOTAL - Math.round(TOTAL * 0.3), sentAt: paidAt - DAY, expiresAt: paidAt + DAY, sentBy: 'Quản lý' },
    trips: [{ tripId: `TRP-${i}-1`, run: { startedAt, deliveredAt: startedAt + 8 * 3_600_000, checkpoints: [{ type: 'delivery', plannedAt: startedAt + 9 * 3_600_000, arrivedAt: startedAt + 8 * 3_600_000 }] } }],
    rating: { trip: 5, driver: 4, escort: 5, comment: '', at: startedAt + 9 * 3_600_000 },
    ...over,
  } as unknown as Booking
}
const all: Booking[] = [...Array.from({ length: 8 }, (_, i) => order(i)), order(9, { payment: undefined, balance: undefined, status: 'awaiting_payment' })]
const quoted = (over: Partial<Booking> = {}): Booking => order(0, { incidents: [], settlement: undefined, cancellation: undefined, ...over })

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
  it('hiển thị an toàn trạng thái đơn chưa có nhãn hoặc bị thiếu', () => {
    expect(bookingStatusLabel('completed')).toBe('Đã hoàn tất')
    expect(bookingStatusLabel('new_backend_status')).toBe('new_backend_status')
    expect(bookingStatusLabel(undefined)).toBe('Không xác định')
  })

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

  it('xử lý an toàn khi quote.lines hoặc incident expenses bị undefined, không sập với lỗi reduce', () => {
    const badQuote = quoted({
      quote: { lines: undefined as any, adjustments: [], subtotal: 5_000_000, total: 5_000_000, deposit: 1_500_000, balance: 3_500_000, sentAt: 1, expiresAt: 2, sentBy: 'x' },
      incidents: [{ id: 'I', tripId: 'T', kind: 'vehicle_breakdown', reportedBy: 'x', reportedAt: 1, location: { lat: 1, lng: 1 }, photo: 'a', note: '', status: 'resolved', expenses: undefined as any }],
    })
    expect(() => orderFinance(badQuote)).not.toThrow()
    const f = orderFinance(badQuote)!
    expect(f.revenue).toBe(5_000_000)
    expect(f.cost).toBe(0)
  })
})
