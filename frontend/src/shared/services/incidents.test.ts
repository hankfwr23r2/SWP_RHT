// Flow 5 (sự cố khẩn cấp) và Flow 6 (quyết toán, đóng đơn): máy trạng thái và các cổng chặn.
import { afterEach, describe, expect, it, vi } from 'vitest'
import { bookingsApi, customerBookingsApi } from './bookings'
import { horsesApi } from './horses'
import { CUSTOMER } from './mock/orders'

const ID = 'ORD-2026-0117' // in_transit, 1 xe, 2 ngựa, nội địa
const T = 'TRP-0117-1'
const D = 'Nguyễn Văn Hùng'
const E = 'Võ Thị Lan'
const CO = 'Trần Minh'
const MG = 'Quản lý'
const fail = async (p: Promise<unknown>) => { try { await p } catch (e) { return (e as Error).message } return '' }
const incidentOf = async (id = ID) => (await bookingsApi.get(id))!.incidents!.at(-1)!
const eta = () => Date.now() + 3 * 3600_000

const toStation = { station: 'Trạm nghỉ Long Thành', restMinutes: 60 }
const line = { path: [[10.9, 106.8], [10.78, 107.0]] as [number, number][], km: 30, hours: 0.6 }

describe('sự cố khẩn cấp (Flow 5)', () => {
  it('báo SOS: cần ảnh; xe chưa chạy thì không báo được; mỗi xe một sự cố mở', async () => {
    expect(await fail(bookingsApi.reportIncident(ID, T, D, 'horse_health', '', 'Ngựa đau bụng'))).toMatch(/ảnh/)
    expect(await fail(bookingsApi.reportIncident('ORD-2026-0116', 'TRP-0116-1', D, 'horse_health', 'a.jpg', ''))).toMatch(/chưa/)
    const b = await bookingsApi.reportIncident(ID, T, E, 'horse_health', 'sos.jpg', 'Ngựa đau bụng, đổ mồ hôi')
    expect(b.status).toBe('incident_reported')
    expect(b.incidents![0]).toMatchObject({ tripId: T, kind: 'horse_health', status: 'reported', expenses: [] })
    expect(b.incidents![0].location.lat).toBeGreaterThan(10) // vị trí xe lúc báo, để vẽ trên bản đồ
    expect(await fail(bookingsApi.reportIncident(ID, T, D, 'vehicle_breakdown', 'b.jpg', ''))).toMatch(/đang có sự cố/)
  })
  it('xe đang có sự cố thì không check-in hay đi tiếp được', async () => {
    expect(await fail(bookingsApi.arriveCheckpoint(ID, T, D, 'x.jpg'))).toMatch(/sự cố/)
    expect(await fail(bookingsApi.continueJourney(ID, T, D))).toMatch(/sự cố/)
  })
  it('Coordinator lập phương án: đúng nhóm sự cố, có ETA mới; xong thì chờ Manager duyệt', async () => {
    const inc = await incidentOf()
    expect(await fail(bookingsApi.planIncident(ID, inc.id, CO, { action: 'rescue_and_station', note: '', newEta: eta(), ...toStation }))).toMatch(/không phù hợp/)
    expect(await fail(bookingsApi.planIncident(ID, inc.id, CO, { action: 'to_station', note: '', newEta: Date.now() - 1000, ...toStation }))).toMatch(/ETA/)
    expect(await fail(bookingsApi.planIncident(ID, inc.id, CO, { action: 'to_station', note: '', newEta: eta(), station: 'Trạm không có trong danh mục', restMinutes: 60 }))).toMatch(/danh mục/)
    expect(await fail(bookingsApi.planIncident(ID, inc.id, CO, { action: 'to_station', note: '', newEta: eta(), station: toStation.station, restMinutes: 10 }))).toMatch(/tối thiểu/)
    const b = await bookingsApi.planIncident(ID, inc.id, CO, { action: 'to_station', note: 'Đưa ngựa về trạm nghỉ gần nhất', newEta: eta(), toStation: line, ...toStation })
    expect(b.status).toBe('pending_emergency_approval')
    expect(b.incidents![0].status).toBe('pending_approval')
    expect(b.incidents![0].plan).toMatchObject({ action: 'to_station', station: 'Trạm nghỉ Long Thành', restMinutes: 60 })
  })
  it('Manager trả về cần lý do; Coordinator lập lại thì xóa lý do cũ', async () => {
    const inc = await incidentOf()
    expect(await fail(bookingsApi.rejectIncident(ID, inc.id, MG, ' '))).toMatch(/lý do/)
    const back = await bookingsApi.rejectIncident(ID, inc.id, MG, 'Phương án chưa hợp lý')
    expect(back.status).toBe('incident_reported')
    expect(back.incidents![0].rejection!.reason).toBe('Phương án chưa hợp lý')
    const again = await bookingsApi.planIncident(ID, inc.id, CO, { action: 'to_station', note: '', newEta: eta(), ...toStation })
    expect(again.incidents![0].rejection).toBeUndefined()
  })
  it('Manager duyệt phương án: phương án có hiệu lực', async () => {
    const inc = await incidentOf()
    const b = await bookingsApi.approveIncident(ID, inc.id, MG)
    expect(b.status).toBe('emergency_plan_active')
    expect(b.incidents![0].approval).toMatchObject({ by: MG })
  })
  it('chi phí: ảnh chứng từ trước, rồi mới có số tiền; bên chịu theo chính sách', async () => {
    const inc = await incidentOf()
    expect(await fail(bookingsApi.addIncidentExpense(ID, inc.id, D, { category: 'vet_fee', label: 'Khám', photo: '', amount: 900_000 }))).toMatch(/ảnh/)
    expect(await fail(bookingsApi.addIncidentExpense(ID, inc.id, D, { category: 'vet_fee', label: 'Khám', photo: 'hd.jpg', amount: 0 }))).toMatch(/số tiền/)
    await bookingsApi.addIncidentExpense(ID, inc.id, D, { category: 'vet_fee', label: 'Khám và truyền dịch', photo: 'hd.jpg', amount: 900_000 })
    const b = await bookingsApi.addIncidentExpense(ID, inc.id, D, { category: 'rescue', label: 'Kéo xe', photo: 'hd2.jpg', amount: 400_000 })
    expect(b.incidents![0].expenses.map(e => e.payer)).toEqual(['customer', 'carrier'])
  })
  it('sự cố sức khỏe: Escort xác nhận ngựa đủ sức rồi Driver mới tiếp tục được; xong thì đơn về đang vận chuyển', async () => {
    const inc = await incidentOf()
    expect(await fail(bookingsApi.resumeJourney(ID, inc.id, D))).toMatch(/đủ sức/)
    await bookingsApi.confirmFit(ID, inc.id, E)
    const b = await bookingsApi.resumeJourney(ID, inc.id, D)
    expect(b.incidents![0].status).toBe('resolved')
    expect(b.status).toBe('in_transit')
    expect(await fail(bookingsApi.addIncidentExpense(ID, inc.id, D, { category: 'other', label: 'x', photo: 'a.jpg', amount: 1 }))).toMatch(/đã xử lý xong/)
  })
  it('xe gặp sự cố: phải chọn cứu hộ và trạm nghỉ; ngựa tới trạm nên cũng cần xác nhận đủ sức mới đi tiếp', async () => {
    const b = await bookingsApi.reportIncident(ID, T, D, 'vehicle_breakdown', 'lop.jpg', 'Nổ lốp')
    const inc = b.incidents!.at(-1)!
    const rescue = { name: 'Cứu hộ Long Thành', phone: '0901 100 001', lat: 10.8, lng: 106.98 }
    expect(await fail(bookingsApi.planIncident(ID, inc.id, CO, { action: 'rescue_and_station', note: '', newEta: eta(), ...toStation }))).toMatch(/cứu hộ/)
    const planned = await bookingsApi.planIncident(ID, inc.id, CO, { action: 'rescue_and_station', note: '', newEta: eta(), ...toStation, rescue, rescueLine: line, toStation: line })
    expect(planned.incidents!.at(-1)!.plan).toMatchObject({ action: 'rescue_and_station', rescue: { name: 'Cứu hộ Long Thành' }, station: 'Trạm nghỉ Long Thành' })
    await bookingsApi.approveIncident(ID, inc.id, MG)
    expect(await fail(bookingsApi.resumeJourney(ID, inc.id, D))).toMatch(/đủ sức/)
    await bookingsApi.confirmFit(ID, inc.id, E)
    expect((await bookingsApi.resumeJourney(ID, inc.id, D)).status).toBe('in_transit')
  })
  it('giao thông tắc nghẽn: chọn lộ trình mới, không cần xác nhận đủ sức, nhà xe chịu chi phí', async () => {
    const b = await bookingsApi.reportIncident(ID, T, D, 'traffic_jam', 'kẹt.jpg', 'Kẹt xe dài')
    const inc = b.incidents!.at(-1)!
    expect(await fail(bookingsApi.planIncident(ID, inc.id, CO, { action: 'reroute', note: '', newEta: eta() }))).toMatch(/lộ trình mới/)
    expect(await fail(bookingsApi.planIncident(ID, inc.id, CO, { action: 'to_station', note: '', newEta: eta(), ...toStation }))).toMatch(/không phù hợp/)
    await bookingsApi.planIncident(ID, inc.id, CO, { action: 'reroute', note: 'Đi đường tránh', newEta: eta(), detour: line })
    const approved = await bookingsApi.approveIncident(ID, inc.id, MG)
    expect(approved.incidents!.at(-1)!.plan!.detour).toMatchObject({ km: 30 })
    expect(await fail(bookingsApi.confirmFit(ID, inc.id, E))).toMatch(/không cần/)
    expect((await bookingsApi.resumeJourney(ID, inc.id, D)).status).toBe('in_transit')
  })
})

describe('quyết toán và đóng đơn (Flow 6)', () => {
  afterEach(() => { vi.useRealTimers() })

  it('đơn không có chi phí: tài xế gửi bảng kê thì hệ thống phát hành quyết toán 0 đồng; khách chấm điểm là đóng đơn', async () => {
    const id = 'ORD-2026-0119'
    expect(await fail(bookingsApi.submitExpenses('ORD-2026-0116', D))).toMatch(/chưa giao/)
    const b = await bookingsApi.submitExpenses(id, 'Phạm Văn D')
    expect(b.status).toBe('settlement_issued')
    expect(b.settlement!.total).toBe(0)
    expect(await fail(customerBookingsApi.settle(CUSTOMER.name, id, { trip: 0, driver: 5, escort: 5, comment: '' }))).toMatch(/1 đến 5/)
    const done = await customerBookingsApi.settle(CUSTOMER.name, id, { trip: 5, driver: 4, escort: 5, comment: 'Tốt' })
    expect(done.status).toBe('completed')
    expect(done.rating!.driver).toBe(4)
  })
  it('đơn có chi phí: Manager đối soát, chỉ khoản khách chịu vào bảng quyết toán, hạn trả 24 giờ', async () => {
    const id = 'ORD-2026-0123'
    expect((await bookingsApi.get(id))!.status).toBe('expenses_submitted')
    expect(await fail(customerBookingsApi.settle(CUSTOMER.name, id, { trip: 5, driver: 5, escort: 5, comment: '' }))).toMatch(/chưa phát hành/)
    const exps = (await bookingsApi.get(id))!.incidents![0].expenses
    const medicine = exps.find(e => e.category === 'medicine')!
    const b = await bookingsApi.issueSettlement(id, MG, { [medicine.id]: 'carrier' })
    expect(b.status).toBe('settlement_issued')
    const customerPays = exps.filter(e => e.id !== medicine.id && e.payer === 'customer').reduce((n, e) => n + e.amount, 0)
    expect(b.settlement!.total).toBe(customerPays)
    expect(b.settlement!.dueAt - b.settlement!.issuedAt).toBe(24 * 3600_000)
  })
  it('quá 24 giờ chưa trả: Payment Overdue, khóa đặt đơn mới; trả rồi thì đóng đơn và cộng số chuyến cho ngựa', async () => {
    const id = 'ORD-2026-0123'
    const before = (await bookingsApi.get(id))!
    const horseId = before.horses[0].horseId
    const trips = (await horsesApi.get(CUSTOMER.name, horseId))!.completedTrips
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(before.settlement!.issuedAt + 25 * 3600_000)
    expect((await bookingsApi.get(id))!.status).toBe('payment_overdue')
    expect(await fail(customerBookingsApi.create(CUSTOMER.name, { type: 'domestic' } as never))).toMatch(/quá hạn/)
    const done = await customerBookingsApi.settle(CUSTOMER.name, id, { trip: 5, driver: 5, escort: 5, comment: '' })
    expect(done.status).toBe('completed')
    expect(done.settlement!.paid).toBeTruthy()
    expect((await horsesApi.get(CUSTOMER.name, horseId))!.completedTrips).toBe(trips + 1)
    expect(await fail(customerBookingsApi.settle(CUSTOMER.name, id, { trip: 5, driver: 5, escort: 5, comment: '' }))).toMatch(/chưa phát hành|đã đóng/)
  })
  it('khách khác không thanh toán được đơn của người khác', async () => {
    expect(await fail(customerBookingsApi.settle('Người lạ', 'ORD-2026-0123', { trip: 5, driver: 5, escort: 5, comment: '' }))).toMatch(/quyền/)
  })
})
