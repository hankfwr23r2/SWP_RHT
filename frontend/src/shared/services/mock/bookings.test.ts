// Dữ liệu mẫu của luồng đặt đơn phải khớp quy tắc nghiệp vụ
import { describe, expect, it } from 'vitest'
import { canCompleteClearance, currentCheckpoint, delayedCheckpoint, horseReadiness, reviewDone, validateRoutePlan, waybillNoOf } from '../../lib/booking'
import { seedVehicles } from './fleet'
import { CUSTOMER } from './orders'
import { seedBookings, seedHorses } from './bookings'

const horses = seedHorses()
const bookings = seedBookings()
const vehicles = seedVehicles()
const FLOW34: string[] = ['ready_for_pickup', 'en_route_to_pickup', 'in_transit', 'delivered_pending_settlement']
const PAID: string[] = ['waybill_issued', 'clearance_in_progress', 'clearance_done', ...FLOW34]

describe('hồ sơ ngựa mẫu', () => {
  it('mã và microchip không trùng', () => {
    expect(new Set(horses.map(h => h.id)).size).toBe(horses.length)
    expect(new Set(horses.map(h => h.microchip)).size).toBe(horses.length)
  })
  it('mọi ngựa trong đơn mẫu đều có hồ sơ để Specialist xem giấy khách nộp', () => {
    const ids = new Set(horses.map(h => h.id))
    for (const b of bookings) for (const h of b.horses) expect(ids.has(h.horseId), `${b.id}: ${h.horseId}`).toBe(true)
  })
  it('có cả ngựa sẵn sàng đặt và ngựa thiếu giấy / hết hạn', () => {
    const ok = horses.filter(h => horseReadiness(h).ok).length
    expect(ok).toBeGreaterThan(0)
    expect(ok).toBeLessThan(horses.length)
  })
})

describe('đơn mẫu', () => {
  it('mã đơn không trùng, đặt trước ngày đi ít nhất 30 ngày', () => {
    expect(new Set(bookings.map(b => b.id)).size).toBe(bookings.length)
    bookings.forEach(b => expect(b.departAt - b.createdAt, b.id).toBeGreaterThanOrEqual(30 * 86_400_000 - 86_400_000))
  })
  it('mọi trạng thái của luồng mới đều có đơn mẫu', () => {
    expect(new Set(bookings.map(b => b.status))).toEqual(new Set(['pending_intake', 'under_review', 'pending_commercial', 'awaiting_payment', 'quote_expired', 'waybill_issued', 'clearance_in_progress', 'clearance_done', 'ready_for_pickup', 'en_route_to_pickup', 'in_transit', 'incident_reported', 'pending_emergency_approval', 'emergency_plan_active', 'delivered_pending_settlement', 'expenses_submitted', 'settlement_issued', 'payment_overdue', 'completed']))
  })
  it('ngựa trong đơn có trong hồ sơ; khách không phải tải Import Permit', () => {
    bookings.forEach(b => {
      b.horses.forEach(bh => expect(horses.find(h => h.id === bh.horseId)?.microchip, `${b.id} ${bh.name}`).toBe(bh.microchip))
      expect('importPermit' in b, b.id).toBe(false)
    })
  })
  it('cửa khẩu do Coordinator chốt: chỉ đơn quốc tế đã chốt phương án mới có, đúng nước đến', () => {
    bookings.forEach(b => expect(!!b.gate, b.id).toBe(b.type === 'international' && !!b.plan))
  })
})

describe('xe và ngựa trên xe', () => {
  const assigned = bookings.filter(b => b.trips)
  it('mỗi ngựa của đơn nằm trên đúng một xe, xe đủ ngăn, tài xế đi theo xe, mã chuyến theo thứ tự', () => {
    assigned.forEach(b => {
      const placed = b.trips!.flatMap(t => t.horseIds)
      expect(placed.slice().sort(), b.id).toEqual(b.horses.map(h => h.horseId).sort())
      expect(new Set(b.trips!.map(t => t.vehicleId)).size, b.id).toBe(b.trips!.length)
      expect(new Set(b.trips!.map(t => t.escortId)).size, b.id).toBe(b.trips!.length)
      expect(new Set(b.trips!.map(t => t.driverId)).size, b.id).toBe(b.trips!.length)
      b.trips!.forEach((t, i) => {
        const v = vehicles.find(x => x.id === t.vehicleId)!
        expect(t.horseIds.length, `${b.id} ${t.tripId}`).toBeLessThanOrEqual(v.capacity)
        expect(t.tripId, b.id).toBe(`TRP-${b.id.slice(-4)}-${i + 1}`)
      })
    })
  })
  it('có ít nhất một đơn nhiều xe, chia 3 + 3 cho 6 ngựa', () => {
    const multi = bookings.filter(b => (b.trips?.length ?? 0) > 1)
    expect(multi.length).toBeGreaterThan(0)
    const six = multi.find(b => b.horses.length === 6)!
    expect(six.trips!.map(t => t.horseIds.length)).toEqual([3, 3])
  })
  it('đơn đã chốt phương án mới có xe; đơn chưa chốt thì chưa có chuyến nào (Điều phối viên chọn khi lập lộ trình)', () => {
    bookings.forEach(b => expect(!!b.trips, b.id).toBe(!((b.status === 'pending_intake' || b.status === 'under_review') && !b.plan)))
  })
})

describe('đơn mẫu theo cổng chuyển bước', () => {
  it('dữ liệu từng trạng thái khớp cổng chuyển bước', () => {
    bookings.forEach(b => {
      const afterReview = !['pending_intake', 'under_review'].includes(b.status)
      expect(reviewDone(b), `${b.id} cổng thẩm định`).toBe(afterReview)
      expect(!!b.intake, `${b.id} intake`).toBe(b.status !== 'pending_intake')
      const quoted = !['pending_intake', 'under_review', 'pending_commercial'].includes(b.status)
      expect(!!b.quote, `${b.id} quote`).toBe(quoted)
      expect(!!b.payment, `${b.id} payment`).toBe(quoted && !['awaiting_payment', 'quote_expired'].includes(b.status))
    })
  })
  it('cọc đúng 30% tổng báo giá, số dư 70%; không có phí lưu xe hay hợp đồng ký số', () => {
    bookings.filter(b => b.quote).forEach(b => {
      expect(Math.abs(b.quote!.deposit - b.quote!.total * 0.3), b.id).toBeLessThanOrEqual(500)
      expect(b.quote!.deposit + b.quote!.balance, b.id).toBe(b.quote!.total)
      expect('demurragePerHour' in b.quote!, b.id).toBe(false)
    })
    bookings.filter(b => b.payment).forEach(b => {
      expect(b.payment!.amount, b.id).toBe(b.quote!.deposit)
      expect('contractSignedAt' in b.payment!, b.id).toBe(false)
    })
  })
  it('đã cọc thì có Vận đơn và danh sách giấy tờ', () => {
    bookings.filter(b => PAID.includes(b.status)).forEach(b => {
      expect(b.waybill!.no, b.id).toBe(waybillNoOf(b.id))
      expect(b.clearance!.items.length, b.id).toBeGreaterThan(0)
    })
  })
})

describe('đơn mẫu giấy tờ do Specialist làm', () => {
  it('giấy tờ xong thì mọi hạng mục xong và có người hoàn tất; quốc tế đủ cờ thông quan', () => {
    bookings.filter(b => b.clearance?.doneAt).forEach(b => {
      expect(canCompleteClearance(b), b.id).toBeNull()
      expect(b.clearance!.doneBy, b.id).toBeTruthy()
    })
    bookings.filter(b => ['clearance_done', ...FLOW34].includes(b.status)).forEach(b => expect(b.clearance!.doneAt, b.id).toBeTruthy())
  })
  it('đơn đang làm giấy: có đơn đã làm xong hạng mục chưa đủ cờ thông quan', () => {
    const doing = bookings.filter(b => b.status === 'clearance_in_progress')
    expect(doing.length).toBeGreaterThan(0)
    expect(doing.some(b => canCompleteClearance(b) !== null)).toBe(true)
    expect(doing.some(b => canCompleteClearance(b) === null)).toBe(true)
  })
  it('hạng mục đã xong có ảnh chụp, hạng mục chưa làm thì chưa có', () => {
    bookings.filter(b => b.clearance).forEach(b => b.clearance!.items.forEach(i => {
      if (i.status === 'done') expect(i.photos.length, `${b.id} ${i.type}`).toBeGreaterThan(0)
      if (i.status === 'todo') expect(i.photos.length, `${b.id} ${i.type}`).toBe(0)
    }))
  })
})

describe('đơn mẫu Flow 3', () => {
  it('lộ trình mẫu qua kiểm tra chia chặng, riêng đơn ETA cửa khẩu sớm chỉ có cảnh báo', () => {
    const planned = bookings.filter(b => b.route)
    expect(planned.length).toBeGreaterThan(0)
    planned.forEach(b => expect(validateRoutePlan(b.route!, b.type === 'international').errors, b.id).toEqual([]))
    const early = bookings.find(b => b.id === 'ORD-2026-0113')!
    expect(validateRoutePlan(early.route!, true).warnings.length).toBe(1)
  })
  it('đơn đã xác nhận phương án thì có lộ trình; sẵn sàng đón thì mọi xe đã nhận lệnh; đã đến điểm đón thì xe có giờ xuất phát', () => {
    bookings.filter(b => b.plan).forEach(b => expect(b.route, b.id).toBeTruthy())
    bookings.filter(b => b.status === 'ready_for_pickup').forEach(b => b.trips!.forEach(t => { expect(t.acks.driver, b.id).toBeTruthy(); expect(t.acks.escort, b.id).toBeTruthy() }))
    bookings.filter(b => ['en_route_to_pickup', 'delivered_pending_settlement'].includes(b.status)).forEach(b => b.trips!.forEach(t => expect(t.departedAt, `${b.id} ${t.tripId}`).toBeTruthy()))
  })
  it('đã sang giai đoạn đón ngựa: 70% đã trả, riêng đơn mẫu thử cổng thanh toán thì chưa', () => {
    bookings.filter(b => FLOW34.includes(b.status) && !['ORD-2026-0115', 'ORD-2026-0120'].includes(b.id)).forEach(b => expect(b.balance!.amount, b.id).toBe(b.quote!.balance))
    expect(bookings.find(b => b.id === 'ORD-2026-0115')!.balance).toBeUndefined()
    expect(bookings.find(b => b.id === 'ORD-2026-0120')!.balance).toBeUndefined()
  })
})

describe('đơn mẫu Flow 4', () => {
  const runs = bookings.flatMap(b => (b.trips ?? []).filter(t => t.run).map(t => ({ b, t })))
  const going = runs.filter(({ t }) => !t.run!.deliveredAt)
  it('xe đang chạy có hành trình, mốc hiện tại là mốc đầu tiên chưa hoàn tất, mốc đã xong có ảnh', () => {
    expect(going.length).toBeGreaterThan(0)
    going.forEach(({ b, t }) => {
      const cps = t.run!.checkpoints
      const firstOpen = cps.findIndex(c => !c.doneAt)
      expect(firstOpen, `${b.id} ${t.tripId}`).toBeGreaterThan(0)
      cps.slice(0, firstOpen).forEach(c => { expect(c.photo, `${b.id} ${c.id}`).toBeTruthy(); expect(c.arrivedAt, `${b.id} ${c.id}`).toBeTruthy() })
      cps.slice(firstOpen).forEach(c => expect(c.doneAt, `${b.id} ${c.id}`).toBeUndefined())
      expect(currentCheckpoint(t)!.id).toBe(cps[firstOpen].id)
    })
  })
  it('mốc nhận ngựa đã quét đủ chip của ngựa trên xe; mỗi trạm nghỉ đã qua có nhật ký an sinh', () => {
    runs.forEach(({ b, t }) => {
      const [pickup, ...rest] = t.run!.checkpoints
      expect(pickup.chips?.length, `${b.id} ${t.tripId}`).toBe(t.horseIds.length)
      rest.filter(c => c.type === 'rest' && c.doneAt).forEach(c => expect(t.run!.welfare.some(w => w.checkpointId === c.id), `${b.id} ${c.id}`).toBe(true))
    })
  })
  it('có xe trễ mốc (cờ vàng) và đơn đã giao xong hết mốc', () => {
    expect(going.filter(({ t }) => delayedCheckpoint(t)).length).toBeGreaterThan(0)
    const done = bookings.filter(b => b.status === 'delivered_pending_settlement')
    expect(done.length).toBeGreaterThan(0)
    done.forEach(b => b.trips!.forEach(t => { expect(t.run!.checkpoints.every(c => c.doneAt), b.id).toBe(true); expect(t.run!.deliveredAt, b.id).toBeTruthy() }))
  })
  it('đơn nhiều xe in_transit: một xe đã chạy, xe kia chưa', () => {
    const b = bookings.find(x => x.id === 'ORD-2026-0122')!
    expect(b.status).toBe('in_transit')
    expect(b.trips![0].run?.startedAt).toBeTruthy()
    expect(b.trips![1].run).toBeUndefined()
    expect(b.trips![1].departedAt).toBeTruthy()
  })
})

describe('đơn mẫu của khách demo', () => {
  const mine = bookings.filter(b => b.customer === CUSTOMER.name)
  it('khách demo chỉ có một số đơn đại diện, các đơn còn lại thuộc khách khác (nhân viên vẫn thấy đủ)', () => {
    expect(mine.length).toBeLessThanOrEqual(20)
    expect(bookings.length - mine.length).toBeGreaterThan(5)
    expect(new Set(mine.map(b => b.status)).size).toBeGreaterThanOrEqual(7)
  })
})
