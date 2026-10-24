import { describe, expect, it } from 'vitest'
import type { CustomerBookingView, TripTeam } from '@shared/services/bookings'
import { orderFacts, quoteFacts, type FactGroup } from './orderFacts'

const base: CustomerBookingView = {
  id: 'BK-007', customer: 'Nguyễn Văn A', createdAt: 1_000, type: 'international',
  origin: { id: 'p1', name: 'Kho Đồng Nai — Trang trại Long Thành', country: 'VN' },
  dest: { id: 'p2', name: 'Kho Phnom Penh', country: 'KH' },
  departAt: Date.parse('2027-01-15T00:00:00Z'),
  consignor: { name: 'Gửi', phone: '0901111111', idNumber: '079123456789', address: '12 Lê Lợi, Q.1' },
  consignee: { name: 'Nhận', phone: '0902222222', idNumber: '', address: 'Phnom Penh' },
  horses: [{ horseId: 'H-001', name: 'Storm', microchip: 'MC1', breed: 'Arabian', sex: 'mare', stall: 'single', feedPackage: 'sport', waterPlan: 'every_2h', insurance: { opted: true } }],
  status: 'under_review',
}
const team: TripTeam[] = [{ tripId: 'TRP-007-1', horseNames: ['Storm'], vehicle: { plate: '51C-123.45', kind: 'Xe 2 khoang', stalls: 2 }, driver: { name: 'Tài xế B', phone: '0903333333' }, escort: { name: 'Hộ tống C', phone: '0904444444' } }]

const group = (groups: FactGroup[], key: FactGroup['key']) => groups.find(g => g.key === key)
const text = (g?: FactGroup) => (g?.rows ?? []).map(r => [r.label, r.value, r.sub ?? ''].join(' ')).join('\n')

describe('thông tin đơn theo 5W', () => {
  it('đơn mới chưa chốt xe: Ai, Cái gì, Ở đâu vẫn đủ và báo khi nào có phần còn thiếu', () => {
    const g = orderFacts(base, [])
    expect(g.map(x => x.key).slice(0, 3)).toEqual(['who', 'what', 'where'])
    expect(text(group(g, 'who'))).toContain('0901111111')
    expect(text(group(g, 'who'))).toContain('079123456789')
    expect(text(group(g, 'who'))).toContain('12 Lê Lợi, Q.1')
    expect(text(group(g, 'who'))).toContain('Có sau khi nhà xe chốt phương án xe')
    expect(text(group(g, 'what'))).toContain('Cấp sau khi đặt cọc')
    expect(text(group(g, 'what'))).toContain('Gói thể thao')
    expect(text(group(g, 'what'))).toContain('Mỗi 2 giờ')
    expect(text(group(g, 'where'))).toContain('Kho Đồng Nai — Trang trại Long Thành')
    expect(text(group(g, 'where'))).toContain('Nhà xe chọn khi chốt lộ trình')
  })
  it('Khi nào, Tại sao chỉ hiện dòng có dữ liệu', () => {
    const g = orderFacts(base, [])
    expect(group(g, 'when')?.rows.map(r => r.label)).toEqual(['Ngày khởi hành'])
    expect(group(g, 'why')).toBeUndefined()
  })
  it('đơn đã chốt xe: hiện kíp xe, vận đơn, giờ đón và giờ đến dự kiến', () => {
    const g = orderFacts({
      ...base, status: 'waybill_issued', gate: 'Mộc Bài', waybill: { no: 'VD-2026-0007', issuedAt: 2_000 },
      route: { legs: [{ no: 1, from: 'A', to: 'B', departAt: Date.parse('2027-01-15T06:00:00Z'), arriveAt: Date.parse('2027-01-15T18:00:00Z') }], rests: [] },
    }, team)
    expect(text(group(g, 'who'))).toContain('51C-123.45')
    expect(text(group(g, 'who'))).toContain('Tài xế B')
    expect(text(group(g, 'who'))).toContain('0904444444')
    expect(text(group(g, 'what'))).toContain('VD-2026-0007')
    expect(text(group(g, 'where'))).toContain('Mộc Bài')
    expect(group(g, 'when')?.rows.map(r => r.label)).toEqual(['Giờ đón dự kiến', 'Dự kiến đến nơi'])
  })
  it('đơn bị từ chối: Tại sao nêu lý do của nhà xe', () => {
    const g = orderFacts({ ...base, status: 'rejected', rejection: { at: 3_000, by: 'manager01', role: 'manager', reason: 'Hết xe trong ngày này' } }, [])
    expect(text(group(g, 'why'))).toContain('Hết xe trong ngày này')
    expect(text(group(g, 'who'))).toContain('Đơn đã đóng')
  })
  it('Tại sao gồm hồ sơ cần bổ sung, ngựa không đủ sức khỏe và điều chỉnh giá', () => {
    const g = orderFacts({
      ...base,
      medical: { status: 'resubmit', resubmit: { reason: 'Giấy tiêm phòng hết hạn', items: [], at: 2_000 } },
      horses: [{ ...base.horses[0], status: 'unfit', unfitInfo: { reason: 'Sốt', discountAmount: 5_000_000, at: 2_500, by: 'escort01' } }],
      quote: { lines: [], adjustments: [{ label: 'Khách quen', amount: -1_000_000 }], subtotal: 10_000_000, total: 9_000_000, deposit: 2_700_000, balance: 6_300_000, sentAt: 2_000, expiresAt: 9_000, sentBy: 'manager01' },
    }, [])
    const why = text(group(g, 'why'))
    expect(why).toContain('Giấy tiêm phòng hết hạn')
    expect(why).toContain('Storm')
    expect(why).toContain('Sốt')
    expect(why).toContain('Khách quen')
  })
})

describe('tóm tắt đơn trên báo giá', () => {
  it('nêu bên gửi, bên nhận, ngựa, xe, tuyến và người gửi báo giá', () => {
    const rows = quoteFacts({
      ...base, status: 'awaiting_payment',
      quote: { lines: [], adjustments: [], subtotal: 10_000_000, total: 10_000_000, deposit: 3_000_000, balance: 7_000_000, sentAt: Date.parse('2027-01-01T03:00:00Z'), expiresAt: 9_000, sentBy: 'manager01' },
    }, team)
    const all = rows.map(r => `${r.label} ${r.value}`).join('\n')
    expect(rows.map(r => r.label)).toEqual(['Bên gửi', 'Bên nhận', 'Ngựa', 'Xe', 'Tuyến', 'Khởi hành', 'Báo giá'])
    expect(all).toContain('Gửi · 0901111111')
    expect(all).toContain('1 ngựa: Storm')
    expect(all).toContain('51C-123.45')
    expect(all).toContain('Kho Đồng Nai')
    expect(all).toContain('manager01')
  })
})
