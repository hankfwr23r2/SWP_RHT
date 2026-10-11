// Thông báo đẩy xuống từng vai trò: chỉ báo tin, không ghi gì vào đơn.
import { describe, expect, it } from 'vitest'
import { bookingsApi, customerBookingsApi } from './bookings'
import { busyResources } from '../lib/booking'
import { crewApi } from './fleet'
import { noticesApi } from './notices'
import { CUSTOMER } from './mock/orders'

const SP = { id: 'KD-01', name: 'Phạm Văn Hưng' }
const CO = { id: 'DP-01', name: 'Trần Minh' }
const forOrder = async (role: 'customer' | 'manager' | 'specialist' | 'coordinator' | 'driver' | 'escort', name: string | undefined, id: string) =>
  (await noticesApi.list(role, name)).filter(n => n.bookingId === id)
const draft = () => ({
  type: 'domestic' as const, origin: { id: 'KHO-DN', name: 'Kho Đồng Nai', country: 'VN' as const }, dest: { id: 'CLB-SG', name: 'CLB', country: 'VN' as const },
  departAt: Date.now() + 40 * 86_400_000, consignor: { name: 'a', phone: '0901000009', idNumber: '1', address: 'x' }, consignee: { name: 'b', phone: '0901000008', idNumber: '2', address: 'y' },
  horses: [{ horseId: 'H-001', name: 'Storm Runner', microchip: 'VN-985211', breed: 'Thoroughbred', sex: 'gelding' as const, stall: 'standard' as const, feedPackage: 'basic' as const, waterPlan: 'every_3h' as const, insurance: { opted: false } }],
})

describe('thông báo theo sự việc của đơn', () => {
  it('khách gửi đơn: Manager nhận. Manager giao việc: Specialist và Coordinator được giao nhận nhiệm vụ', async () => {
    const o = await customerBookingsApi.create(CUSTOMER.name, draft())
    expect((await forOrder('manager', undefined, o.id)).map(n => n.title)).toEqual([expect.stringMatching(/Đơn mới/)])
    expect(await forOrder('specialist', SP.name, o.id)).toHaveLength(0)
    await bookingsApi.activate(o.id, 'Quản lý', SP, CO)
    expect((await forOrder('specialist', SP.name, o.id))[0].title).toMatch(/Nhiệm vụ.*thẩm định/)
    expect((await forOrder('coordinator', CO.name, o.id))[0].title).toMatch(/Nhiệm vụ.*xe và lộ trình/)
    expect(await forOrder('specialist', 'Người khác', o.id)).toHaveLength(0) // chỉ đúng người được giao
  })
  it('Specialist yêu cầu bổ sung: chỉ khách nhận, Manager không nhận; khách gửi lại thì Specialist nhận', async () => {
    const o = await customerBookingsApi.create(CUSTOMER.name, draft())
    await bookingsApi.activate(o.id, 'Quản lý', SP, CO)
    const managerBefore = (await forOrder('manager', undefined, o.id)).length
    await bookingsApi.requestResubmission(o.id, SP.name, 'Phiếu xét nghiệm bị mờ', [{ horseId: 'H-001', doc: 'lab' }])
    const mine = await forOrder('customer', CUSTOMER.name, o.id)
    expect(mine[0].title).toMatch(/bổ sung/)
    expect(mine[0].text).toMatch(/Phiếu xét nghiệm bị mờ/)
    expect((await forOrder('manager', undefined, o.id)).length).toBe(managerBefore)
    await customerBookingsApi.resubmit(CUSTOMER.name, o.id)
    expect((await forOrder('specialist', SP.name, o.id))[0].title).toMatch(/bổ sung/)
  })
  it('khách đặt cọc: Manager, Specialist, Coordinator nhận "khách đã cọc"; Driver và Escort của xe nhận chuyến mới', async () => {
    const id = 'ORD-2026-0105' // awaiting_payment, xe VH-009 (Trần Quốc Bảo, Huỳnh Thị Mai), Specialist Nguyễn Thị Thu
    await customerBookingsApi.payDeposit(CUSTOMER.name, id)
    expect((await forOrder('manager', undefined, id))[0].title).toMatch(/đã đặt cọc/)
    expect((await forOrder('specialist', 'Nguyễn Thị Thu', id))[0].title).toMatch(/đã đặt cọc/)
    expect((await forOrder('coordinator', 'Trần Minh', id))[0].title).toMatch(/đã đặt cọc/)
    expect((await forOrder('driver', 'Trần Quốc Bảo', id))[0].title).toMatch(/Chuyến mới/)
    expect((await forOrder('escort', 'Huỳnh Thị Mai', id))[0].title).toMatch(/Chuyến mới/)
    expect(await forOrder('driver', 'Nguyễn Văn Hùng', id)).toHaveLength(0)
  })
  it('Manager gửi báo giá: khách nhận', async () => {
    const id = 'ORD-2026-0104' // pending_commercial
    const [crew, all, b] = await Promise.all([crewApi.list(), bookingsApi.list(), bookingsApi.get(id)])
    const busy = busyResources(all, b!.departAt, id)
    const free = (role: string) => crew.filter(c => c.role === role && !busy.crew.has(c.id))
    await bookingsApi.assignCrew(id, 'Quản lý', b!.trips!.map((t, i) => ({ tripId: t.tripId, driverId: free('driver')[i].id, escortId: free('escort')[i].id })))
    await bookingsApi.sendQuote(id, 'Quản lý', [])
    expect((await forOrder('customer', CUSTOMER.name, id))[0].title).toMatch(/Báo giá/)
  })
  it('Specialist hoàn tất giấy tờ: khách và Manager nhận.', async () => {
    const id = 'ORD-2026-0111' // clearance_in_progress, mọi hạng mục đã xong; Specialist Nguyễn Thị Thu
    await bookingsApi.completeClearance(id, 'Nguyễn Thị Thu')
    expect((await forOrder('manager', undefined, id))[0].title).toMatch(/Giấy tờ đã xong/)
  })
  it('khách trả 70%: Manager và Coordinator nhận', async () => {
    const id = 'ORD-2026-0115'
    await customerBookingsApi.payBalance(CUSTOMER.name, id)
    expect((await forOrder('manager', undefined, id))[0].title).toMatch(/70%/)
    expect((await forOrder('coordinator', 'Trần Minh', id))[0].title).toMatch(/70%/)
  })
  it('khách hủy đơn: Manager, người được giao và tài xế, hộ tống của xe nhận', async () => {
    const id = 'ORD-2026-0112' // clearance_done, xe VH-013 (Trịnh Văn Long, Lê Thị C)
    await customerBookingsApi.cancel(CUSTOMER.name, id, 'Đổi kế hoạch')
    expect((await forOrder('manager', undefined, id))[0].title).toMatch(/hủy/)
    expect((await forOrder('driver', 'Trịnh Văn Long', id))[0].title).toMatch(/hủy/)
    expect((await forOrder('escort', 'Lê Thị C', id))[0].title).toMatch(/hủy/)
  })
  it('Manager hủy đơn: khách nhận thông báo hoàn tiền', async () => {
    const id = 'ORD-2026-0120'
    await bookingsApi.managerCancel(id, 'Quản lý', 'Hết xe')
    expect((await forOrder('customer', CUSTOMER.name, id))[0].text).toMatch(/Hoàn/)
  })
  it('xe bắt đầu chạy và giao xong: khách, Manager, Coordinator nhận', async () => {
    const id = 'ORD-2026-0116'
    const t = 'TRP-0116-1'
    const b = (await bookingsApi.get(id))!
    await bookingsApi.arriveAtPickup(id, t, 'Nguyễn Văn Hùng', 'a.jpg')
    for (const h of b.horses) await bookingsApi.scanChip(id, t, 'Võ Thị Lan', h.microchip)
    await bookingsApi.collectOriginals(id, t, 'Nguyễn Văn Hùng', ['Hộ chiếu ngựa bản gốc (FEI / National Passport)', 'Sổ tiêm phòng', 'Phiếu xét nghiệm EIA/EVA, bản gốc kèm 02 bản sao công chứng'])
    await bookingsApi.uploadHandover(id, t, 'Nguyễn Văn Hùng', 'bb.jpg')
    await bookingsApi.startJourney(id, t, 'Nguyễn Văn Hùng')
    expect((await forOrder('customer', CUSTOMER.name, id))[0].title).toMatch(/bắt đầu hành trình/)
    expect((await forOrder('manager', undefined, id))[0].title).toMatch(/bắt đầu hành trình/)
    expect((await forOrder('coordinator', 'Trần Minh', id))[0].title).toMatch(/bắt đầu hành trình/)
  })
})

describe('đọc và đánh dấu đã đọc', () => {
  it('đếm chưa đọc, đánh dấu từng tin và tất cả', async () => {
    const before = (await noticesApi.list('manager')).filter(n => !n.read).length
    expect(before).toBeGreaterThan(1)
    const first = (await noticesApi.list('manager'))[0]
    await noticesApi.markRead(first.id)
    expect((await noticesApi.list('manager')).filter(n => !n.read).length).toBe(before - 1)
    await noticesApi.markAllRead('manager')
    expect((await noticesApi.list('manager')).filter(n => !n.read)).toHaveLength(0)
  })
  it('mới nhất lên đầu; tin của vai trò khác không lẫn vào', async () => {
    const mine = await noticesApi.list('customer', CUSTOMER.name)
    expect(mine.every(n => n.role === 'customer')).toBe(true)
    expect(mine.map(n => n.at)).toEqual([...mine.map(n => n.at)].sort((a, b) => b - a))
  })
  it('thông báo không ghi gì vào nhật ký đơn', async () => {
    const b = (await bookingsApi.get('ORD-2026-0104'))!
    expect(b.history.every(h => !/thông báo/i.test(h.text))).toBe(true)
  })
  it('khách từ chối báo giá: Manager, Specialist, Coordinator nhận', async () => {
    const id = 'ORD-2026-0104' // awaiting_payment sau khi Manager gửi báo giá
    await customerBookingsApi.rejectQuote(CUSTOMER.name, id, 'Giá cao')
    expect((await forOrder('manager', undefined, id))[0].title).toMatch(/từ chối báo giá/)
  })
})
