// Máy trạng thái của luồng đặt đơn: giấy tờ do Specialist làm, nhiều xe, thanh toán hai đợt, và hành trình từng xe.
import { beforeAll, describe, expect, it } from 'vitest'
import { buildRoutePlan, busyResources, manifestDocuments, routeKm, vehicleDocsOk } from '../lib/booking'
import { COUNTRY_LOCATIONS } from '../config/network'
import { bookingsApi, customerBookingsApi } from './bookings'
import { crewApi, vehiclesApi } from './fleet'
import type { Booking } from '../types/booking'
import { CUSTOMER } from './mock/orders'

const ID = 'ORD-2026-0116' // en_route_to_pickup, 1 xe, 2 ngựa, nội địa, đã trả 70%
const T = 'TRP-0116-1'
const D = 'Nguyễn Văn Hùng'
const E = 'Võ Thị Lan'
const SP = 'Phạm Văn Hưng'
// Điều phối viên tự chọn xe, tài xế, hộ tống đang rảnh vào ngày đi (không còn gán tự động); cả đơn đi một xe
const freeTrip = async (b: { id: string; departAt: number; type: string; horses: { horseId: string }[] }) => {
  const [vehicles, crew, all] = await Promise.all([vehiclesApi.list(), crewApi.list(), bookingsApi.list()])
  const busy = busyResources(all, b.departAt, b.id)
  const v = vehicles.filter(x => vehicleDocsOk(x, b.type === 'international') && !busy.vehicles.has(x.id) && x.capacity >= b.horses.length).sort((a, z) => a.capacity - z.capacity)[0]
  const d = crew.find(c => c.role === 'driver' && !busy.crew.has(c.id))!
  const e = crew.find(c => c.role === 'escort' && !busy.crew.has(c.id))!
  return { vehicleId: v.id, driverId: d.id, escortId: e.id, horseIds: b.horses.map(h => h.horseId) }
}
const fail = async (p: Promise<unknown>) => { try { await p } catch (e) { return (e as Error).message } return '' }

describe('hành trình một xe (Flow 4)', () => {
  beforeAll(async () => { expect((await bookingsApi.get(ID))!.status).toBe('en_route_to_pickup') })

  it('không bắt đầu hành trình khi chưa check-in, chưa quét chip, chưa đủ bản gốc, chưa có biên bản', async () => {
    expect(await fail(bookingsApi.startJourney(ID, T, D))).toMatch(/xác nhận có mặt/)
    await bookingsApi.arriveAtPickup(ID, T, D, 'capture_pickup.jpg')
    expect(await fail(bookingsApi.startJourney(ID, T, D))).toMatch(/microchip/)
  })
  it('chip lạ bị từ chối, chip đúng được ghi nhận, quét đủ hết ngựa mới qua', async () => {
    expect(await fail(bookingsApi.scanChip(ID, T, E, 'VN-000000'))).toMatch(/không khớp/)
    const b = (await bookingsApi.get(ID))!
    for (const h of b.horses) await bookingsApi.scanChip(ID, T, E, h.microchip.toLowerCase())
    expect(await fail(bookingsApi.startJourney(ID, T, D))).toMatch(/chứng từ gốc/)
    const originals = manifestDocuments(b).originals
    await bookingsApi.collectOriginals(ID, T, D, originals.slice(0, 1))
    expect(await fail(bookingsApi.startJourney(ID, T, D))).toMatch(/chứng từ gốc/)
    await bookingsApi.collectOriginals(ID, T, D, originals)
    expect(await fail(bookingsApi.startJourney(ID, T, D))).toMatch(/biên bản/)
    await bookingsApi.uploadHandover(ID, T, D, 'bien_ban.jpg')
    const started = await bookingsApi.startJourney(ID, T, D)
    expect(started.status).toBe('in_transit')
    expect(started.trips![0].run!.startedAt).toBeTruthy()
  })
  it('qua trạm nghỉ: phải check-in có ảnh và có nhật ký an sinh mới được tiếp tục', async () => {
    const b = (await bookingsApi.get(ID))!
    const cp = b.trips![0].run!.checkpoints.find(c => !c.doneAt)!
    if (cp.type === 'rest') {
      expect(await fail(bookingsApi.continueJourney(ID, T, D))).toMatch(/Chưa ở trạm nghỉ/)
      expect(await fail(bookingsApi.arriveCheckpoint(ID, T, D, ''))).toMatch(/ảnh/)
      await bookingsApi.arriveCheckpoint(ID, T, D, 'capture_rest.jpg')
      expect(await fail(bookingsApi.continueJourney(ID, T, D))).toMatch(/nhật ký an sinh/)
      await bookingsApi.submitWelfare(ID, T, E, { condition: 'normal', waterLiters: 8, hay: true, photo: 'ngua.jpg', note: '' })
      await bookingsApi.continueJourney(ID, T, D)
    }
  })
  it('giao ngựa: cần Escort kiểm tra lần cuối và ảnh biên bản có chữ ký', async () => {
    expect(await fail(bookingsApi.completeDelivery(ID, T, D))).toMatch(/Chưa tới điểm giao/)
    await bookingsApi.arriveCheckpoint(ID, T, D, 'capture_delivery.jpg')
    expect(await fail(bookingsApi.completeDelivery(ID, T, D))).toMatch(/kiểm tra thể trạng/)
    await bookingsApi.submitWelfare(ID, T, E, { condition: 'normal', waterLiters: 2, hay: false, photo: 'ngua_cuoi.jpg', note: 'Ngựa khỏe' })
    expect(await fail(bookingsApi.completeDelivery(ID, T, D))).toMatch(/Biên bản/)
    await bookingsApi.uploadHandover(ID, T, D, 'bien_ban_ban_giao.jpg')
    const done = await bookingsApi.completeDelivery(ID, T, D)
    expect(done.status).toBe('delivered_pending_settlement')
    expect(done.trips![0].run!.deliveredAt).toBeTruthy()
    expect(done.trips![0].run!.checkpoints.every(c => c.doneAt)).toBe(true)
  })
})

describe('thanh toán hai đợt', () => {
  it('không bắt đầu hành trình khi khách chưa trả 70%', async () => {
    const id = 'ORD-2026-0120'
    const t = 'TRP-0120-1'
    await bookingsApi.arriveAtPickup(id, t, D, 'a.jpg')
    expect(await fail(bookingsApi.startJourney(id, t, D))).toMatch(/70%/)
  })
  it('trả 70% chỉ khi xe sẵn sàng hoặc đang đến điểm đón; không trả hai lần', async () => {
    const early = 'ORD-2026-0112' // clearance_done, chưa tới ready_for_pickup
    expect(await fail(customerBookingsApi.payBalance(CUSTOMER.name, early))).toMatch(/Chưa đến bước/)
    const id = 'ORD-2026-0115' // ready_for_pickup, chưa trả
    const paid = await customerBookingsApi.payBalance(CUSTOMER.name, id)
    expect(paid.balance!.amount).toBe(paid.quote!.balance)
    expect(await fail(customerBookingsApi.payBalance(CUSTOMER.name, id))).toMatch(/đã thanh toán/)
  })
})

describe('Lệnh điều xe (Flow 3)', () => {
  it('chỉ Ready for Pickup khi giấy xong và cả Driver lẫn Escort đã nhận lệnh', async () => {
    const id = 'ORD-2026-0112'
    const t = 'TRP-0112-1'
    expect((await bookingsApi.get(id))!.status).toBe('clearance_done')
    const a = await bookingsApi.acknowledgeTrip(id, t, 'driver', D)
    expect(a.status).toBe('clearance_done')
    expect(await fail(bookingsApi.departToPickup(id, t, D))).toMatch(/chưa sẵn sàng/)
    expect((await bookingsApi.acknowledgeTrip(id, t, 'escort', E)).status).toBe('ready_for_pickup')
    expect((await bookingsApi.departToPickup(id, t, D)).status).toBe('en_route_to_pickup')
  })
  it('giấy tờ chưa xong thì xe chưa đi đón dù đã nhận lệnh', async () => {
    const id = 'ORD-2026-0111' // clearance_in_progress
    await bookingsApi.acknowledgeTrip(id, 'TRP-0111-1', 'driver', 'Lê Văn Tài')
    await bookingsApi.acknowledgeTrip(id, 'TRP-0111-1', 'escort', 'Đỗ Thị Hạnh')
    expect((await bookingsApi.get(id))!.status).toBe('clearance_in_progress')
    expect(await fail(bookingsApi.departToPickup(id, 'TRP-0111-1', 'Lê Văn Tài'))).toMatch(/giấy tờ chưa hoàn tất/)
  })
})

describe('giấy tờ do Specialist làm (Flow 2)', () => {
  it('chỉ tiếp nhận Vận đơn khi waybill_issued; không hoàn tất khi còn hạng mục chưa xong hoặc thiếu cờ thông quan', async () => {
    const id = 'ORD-2026-0106' // intl, waybill_issued
    expect(await fail(bookingsApi.acceptWaybill('ORD-2026-0113', SP))).toMatch(/chưa có Vận đơn hoặc đã được tiếp nhận/)
    expect((await bookingsApi.acceptWaybill(id, SP)).status).toBe('clearance_in_progress')
    expect(await fail(bookingsApi.completeClearance(id, SP))).toMatch(/hạng mục/)
    const b = (await bookingsApi.get(id))!
    for (const i of b.clearance!.items) await bookingsApi.updateClearanceItem(id, SP, i.type, { photos: ['a.jpg'] })
    expect(await fail(bookingsApi.completeClearance(id, SP))).toMatch(/thông quan/)
    for (const h of b.horses) await bookingsApi.markHorseCleared(id, SP, h.horseId, true)
    expect((await bookingsApi.completeClearance(id, SP)).status).toBe('clearance_done')
  })
  it('nội địa không ghi nhận thông quan, không thêm giấy hải quan', async () => {
    const id = 'ORD-2026-0110'
    const b = (await bookingsApi.get(id))!
    expect(await fail(bookingsApi.markHorseCleared(id, SP, b.horses[0].horseId, true))).toMatch(/quốc tế/)
    expect(await fail(bookingsApi.addClearanceItem(id, SP, 'ata_carnet'))).toMatch(/quốc tế/)
  })
  it('cập nhật hạng mục lưu ghi chú, ảnh và đưa đơn sang đang làm', async () => {
    const id = 'ORD-2026-0110'
    const poa = (b: Booking) => b.clearance!.items.find(i => i.type === 'poa')!
    expect(poa((await bookingsApi.get(id))!).status).toBe('todo')
    const noted = await bookingsApi.updateClearanceItem(id, SP, 'poa', { note: 'Đang soạn song ngữ' })
    expect(poa(noted)).toMatchObject({ status: 'todo', note: 'Đang soạn song ngữ' }) // chỉ ghi chú thì chưa nộp
    const b = await bookingsApi.updateClearanceItem(id, SP, 'poa', { photos: ['poa_scan.jpg'] })
    expect(poa(b).status).toBe('done') // tải ảnh lên thì tự chuyển Đã nộp
    expect(poa(await bookingsApi.updateClearanceItem(id, SP, 'poa', { photos: [] })).status).toBe('todo') // xóa hết ảnh thì về Chưa nộp
  })
})

describe('nhiều xe', () => {
  const id = 'ORD-2026-0122' // 2 xe: xe 1 đang chạy, xe 2 đang đến điểm đón; đã trả 70%
  it('chip của ngựa xe khác không quét được trên xe này', async () => {
    const b = (await bookingsApi.get(id))!
    await bookingsApi.arriveAtPickup(id, b.trips![1].tripId, 'Lê Văn C', 'a.jpg')
    const other = b.horses.find(h => b.trips![0].horseIds.includes(h.horseId))!
    expect(await fail(bookingsApi.scanChip(id, b.trips![1].tripId, 'Lê Thị C', other.microchip))).toMatch(/không khớp/)
  })
  it('đơn đã in_transit khi xe đầu chạy; xe thứ hai bắt đầu hành trình không làm đơn lùi trạng thái', async () => {
    const b = (await bookingsApi.get(id))!
    expect(b.trips).toHaveLength(2)
    expect(b.status).toBe('in_transit')
    const t2 = b.trips![1]
    const drv = 'Lê Văn C'
    const esc = 'Lê Thị C'
    for (const h of b.horses.filter(x => t2.horseIds.includes(x.horseId))) await bookingsApi.scanChip(id, t2.tripId, esc, h.microchip)
    await bookingsApi.collectOriginals(id, t2.tripId, drv, manifestDocuments(b).originals)
    await bookingsApi.uploadHandover(id, t2.tripId, drv, 'bb.jpg')
    const started = await bookingsApi.startJourney(id, t2.tripId, drv)
    expect(started.trips![1].run!.startedAt).toBeTruthy()
    expect(started.status).toBe('in_transit')
  })
})

describe('tiếp nhận của Manager', () => {
  const draft = () => ({
    type: 'domestic' as const, origin: { id: 'KHO-DN', name: 'Kho Đồng Nai', country: 'VN' as const }, dest: { id: 'CLB-SG', name: 'CLB', country: 'VN' as const },
    departAt: Date.now() + 40 * 86_400_000, consignor: { name: 'a', phone: '0901000001', idNumber: '1', address: 'x' }, consignee: { name: 'b', phone: '0901000002', idNumber: '2', address: 'y' },
    horses: [{ horseId: 'H-001', name: 'Storm Runner', microchip: 'VN-985211', breed: 'Thoroughbred', sex: 'gelding' as const, stall: 'standard' as const, feedPackage: 'basic' as const, waterPlan: 'every_3h' as const, insurance: { opted: false } }],
  })
  const sp = { id: 'KD-01', name: SP }
  const co = { id: 'DP-01', name: 'Trần Minh' }
  it('tiếp nhận chuyển sang thẩm định, giao người phụ trách và chưa có xe nào (Điều phối viên chọn sau)', async () => {
    const created = await customerBookingsApi.create(CUSTOMER.name, draft())
    const b = await bookingsApi.activate(created.id, 'Quản lý', sp, co)
    expect(b.status).toBe('under_review')
    expect(b.intake).toMatchObject({ specialist: sp, coordinator: co })
    expect(b.trips).toBeUndefined()
    expect(b.clearance!.items.length).toBeGreaterThan(0)
  })
  it('tiếp nhận không phụ thuộc đội xe: dù mọi xe thiếu giấy vẫn tiếp nhận được', async () => {
    const created = await customerBookingsApi.create(CUSTOMER.name, draft())
    const saved = await vehiclesApi.list()
    for (const v of saved) await vehiclesApi.update(v.id, { inspectionNo: '' })
    const b = await bookingsApi.activate(created.id, 'Quản lý', sp, co)
    for (const v of saved) await vehiclesApi.update(v.id, { inspectionNo: v.inspectionNo })
    expect(b.status).toBe('under_review')
    expect(b.trips).toBeUndefined()
  })
})

describe('sửa sau review', () => {
  it('đơn nhiều xe: xe B vẫn nhận lệnh và nhận bộ giấy sau khi xe A đã xuất phát', async () => {
    const id = 'ORD-2026-0121' // 2 xe, intl, waybill_issued
    const SPN = 'Phạm Văn Hưng'
    await bookingsApi.acceptWaybill(id, SPN)
    const b0 = (await bookingsApi.get(id))!
    for (const i of b0.clearance!.items) await bookingsApi.updateClearanceItem(id, SPN, i.type, { photos: ['a.jpg'] })
    for (const h of b0.horses) await bookingsApi.markHorseCleared(id, SPN, h.horseId, true)
    await bookingsApi.completeClearance(id, SPN)
    const [a, bTrip] = b0.trips!
    await bookingsApi.acknowledgeTrip(id, a.tripId, 'driver', 'x')
    await bookingsApi.acknowledgeTrip(id, a.tripId, 'escort', 'x')
    expect((await bookingsApi.departToPickup(id, a.tripId, 'x')).status).toBe('en_route_to_pickup')
    expect((await bookingsApi.acknowledgeTrip(id, bTrip.tripId, 'driver', 'y')).trips![1].acks.driver).toBeTruthy()
    expect((await bookingsApi.acknowledgeTrip(id, bTrip.tripId, 'escort', 'y')).trips![1].acks.escort).toBeTruthy()
    expect((await bookingsApi.setDriverPack(id, bTrip.tripId, 'Trần Minh', ['Vận đơn'])).trips![1].driverPack?.items).toEqual(['Vận đơn'])
    expect((await bookingsApi.departToPickup(id, bTrip.tripId, 'y')).trips![1].departedAt).toBeTruthy()
    expect(await fail(bookingsApi.acknowledgeTrip(id, a.tripId, 'driver', 'x'))).toMatch(/đã xuất phát/)
  })
  it('Coordinator chốt xe mà chưa có tài xế, hộ tống; Manager chọn sau rồi mới gửi được báo giá', async () => {
    const created = await customerBookingsApi.create(CUSTOMER.name, { ...(() => { const d = { type: 'domestic' as const, origin: { id: 'KHO-DN', name: 'Kho Đồng Nai', country: 'VN' as const }, dest: { id: 'CLB-SG', name: 'CLB', country: 'VN' as const }, departAt: Date.now() + 70 * 86_400_000, consignor: { name: 'a', phone: '1', idNumber: '1', address: 'x' }, consignee: { name: 'b', phone: '1', idNumber: '1', address: 'x' }, horses: [{ horseId: 'E1', name: 'Ngựa 1', microchip: 'VN-9000', breed: 'Thoroughbred', sex: 'gelding' as const, stall: 'standard' as const, feedPackage: 'basic' as const, waterPlan: 'every_3h' as const, insurance: { opted: false } }] }; return d })() })
    const b = await bookingsApi.activate(created.id, 'Quản lý', { id: 'KD-01', name: SP }, { id: 'DP-01', name: 'Trần Minh' })
    await bookingsApi.approveMedical(b.id, SP)
    const t = await freeTrip(b)
    const route = buildRoutePlan(b, Date.now() + 70 * 86_400_000)
    const planned = await bookingsApi.confirmPlan(b.id, 'Trần Minh', { trips: [{ vehicleId: t.vehicleId, horseIds: t.horseIds }], route, note: '' })
    expect(planned.status).toBe('pending_commercial')
    expect(planned.trips![0]).toMatchObject({ driverId: '', escortId: '' })
    expect(await fail(bookingsApi.sendQuote(b.id, 'Quản lý', []))).toMatch(/Chọn tài xế và hộ tống/)
    const tripId = planned.trips![0].tripId
    const taken = (await bookingsApi.get('ORD-2026-0116'))!.trips![0] // đã giao việc cho đơn khác (ngày đi xa vẫn khóa)
    expect(await fail(bookingsApi.assignCrew(b.id, 'Quản lý', [{ tripId, driverId: taken.driverId, escortId: t.escortId }]))).toMatch(/đã được giữ cho đơn khác/)
    expect(await fail(bookingsApi.assignCrew(b.id, 'Quản lý', [{ tripId, driverId: t.driverId, escortId: taken.escortId }]))).toMatch(/đã được giữ cho đơn khác/)
    expect(await fail(bookingsApi.assignCrew(b.id, 'Quản lý', [{ tripId, driverId: t.driverId, escortId: '' }]))).toMatch(/một tài xế và một/)
    expect((await bookingsApi.assignCrew(b.id, 'Quản lý', [{ tripId, driverId: t.driverId, escortId: t.escortId }])).trips![0]).toMatchObject({ driverId: t.driverId, escortId: t.escortId })
    expect((await bookingsApi.sendQuote(b.id, 'Quản lý', [])).status).toBe('awaiting_payment')
  })
  it('Manager trả đơn về Kiểm dịch viên hoặc Điều phối viên làm lại, bắt buộc ghi lý do', async () => {
    const departAt = Date.now() + 75 * 86_400_000
    const created = await customerBookingsApi.create(CUSTOMER.name, { type: 'domestic' as const, origin: { id: 'KHO-DN', name: 'Kho Đồng Nai', country: 'VN' as const }, dest: { id: 'CLB-SG', name: 'CLB', country: 'VN' as const }, departAt, consignor: { name: 'a', phone: '1', idNumber: '1', address: 'x' }, consignee: { name: 'b', phone: '1', idNumber: '1', address: 'x' }, horses: [{ horseId: 'E1', name: 'Ngựa 1', microchip: 'VN-9001', breed: 'Thoroughbred', sex: 'gelding' as const, stall: 'standard' as const, feedPackage: 'basic' as const, waterPlan: 'every_3h' as const, insurance: { opted: false } }] })
    const b = await bookingsApi.activate(created.id, 'Quản lý', { id: 'KD-01', name: SP }, { id: 'DP-01', name: 'Trần Minh' })
    await bookingsApi.approveMedical(b.id, SP)
    const t0 = await freeTrip(b)
    const route = buildRoutePlan(b, departAt)
    const plan = () => bookingsApi.confirmPlan(b.id, 'Trần Minh', { trips: [{ vehicleId: t0.vehicleId, horseIds: t0.horseIds }], route, note: '' })
    expect((await plan()).status).toBe('pending_commercial')
    expect(await fail(bookingsApi.sendBack(b.id, 'Quản lý', 'specialist', ' '))).toMatch(/lý do/)
    const toSp = await bookingsApi.sendBack(b.id, 'Quản lý', 'specialist', 'Hộ chiếu mờ, kiểm tra lại')
    expect(toSp).toMatchObject({ status: 'under_review', medical: { status: 'pending' }, sentBack: { to: 'specialist', reason: 'Hộ chiếu mờ, kiểm tra lại' } })
    expect(toSp.trips).toHaveLength(1) // xe và lộ trình của Điều phối viên giữ nguyên
    const redone = await bookingsApi.approveMedical(b.id, SP)
    expect(redone.status).toBe('pending_commercial')
    expect(redone.sentBack).toBeUndefined()
    const toCo = await bookingsApi.sendBack(b.id, 'Quản lý', 'coordinator', 'Chọn lại xe lớn hơn')
    expect(toCo).toMatchObject({ status: 'under_review', sentBack: { to: 'coordinator' } })
    expect(toCo.trips).toBeUndefined()
    expect(toCo.plan).toBeUndefined()
    expect(toCo.medical?.status).toBe('approved') // hồ sơ ngựa đã duyệt thì không phải duyệt lại
    expect(await fail(bookingsApi.sendBack(b.id, 'Quản lý', 'coordinator', 'x'))).toMatch(/chờ duyệt báo giá/)
    const again = await plan()
    expect(again.status).toBe('pending_commercial')
    expect(again.sentBack).toBeUndefined()
  })
  it('khách không hủy được đơn đang thẩm định để báo giá', async () => {
    expect(await fail(customerBookingsApi.cancel(CUSTOMER.name, 'ORD-2026-0102', 'Đổi ý'))).toMatch(/chỉ hủy được khi đã có báo giá/)
  })
  it('khách đang chờ đặt cọc không hủy được, chỉ từ chối báo giá', async () => {
    expect(await fail(customerBookingsApi.cancel(CUSTOMER.name, 'ORD-2026-0105', 'Đổi ý'))).toMatch(/Từ chối báo giá/)
  })
  it('đơn khách từ chối báo giá thì mọi thao tác theo chuyến bị chặn', async () => {
    const id = 'ORD-2026-0105' // awaiting_payment, đã có xe
    const t = (await bookingsApi.get(id))!.trips![0].tripId
    await customerBookingsApi.rejectQuote(CUSTOMER.name, id, 'Khách đổi ý')
    expect((await bookingsApi.get(id))!.status).toBe('cancelled')
    expect(await fail(bookingsApi.arriveAtPickup(id, t, D, 'a.jpg'))).toMatch(/không còn ở bước/)
    expect(await fail(bookingsApi.scanChip(id, t, E, 'VN-985212'))).toMatch(/không còn ở bước/)
    expect(await fail(bookingsApi.startJourney(id, t, D))).toMatch(/không còn ở bước/)
  })
  it('Coordinator không xác nhận được phương án có xe không chở ngựa nào', async () => {
    const id = 'ORD-2026-0102'
    const b = (await bookingsApi.get(id))!
    const route = buildRoutePlan(b, Date.now() + 40 * 86_400_000)
    route.rests.forEach((r, i) => { r.name = `Trạm ${i + 1}` })
    const t0 = await freeTrip(b)
    const empty = { vehicleId: 'VH-010', driverId: 'TX-10', escortId: 'NV-05', horseIds: [] as string[] }
    expect(await fail(bookingsApi.confirmPlan(id, 'Trần Minh', { trips: [t0, empty], route, note: '' }))).toMatch(/ít nhất một ngựa/)
    expect((await bookingsApi.get(id))!.plan).toBeUndefined()
  })
})

describe('sửa các lỗi nhỏ sau review', () => {
  it('khách không thấy ghi chú nội bộ của Coordinator (plan)', async () => {
    const v = (await customerBookingsApi.get(CUSTOMER.name, 'ORD-2026-0105'))!
    expect('plan' in v).toBe(false)
    expect('intake' in v).toBe(false)
  })
  it('báo giá hết hạn thì khách không còn thấy xe, tài xế, lộ trình', async () => {
    const id = 'ORD-2026-0107' // quote_expired
    const v = (await customerBookingsApi.get(CUSTOMER.name, id))!
    expect(v.trips).toBeUndefined()
    expect(v.route).toBeUndefined()
    expect(await customerBookingsApi.team(CUSTOMER.name, id)).toEqual([])
  })
  it('khách hủy sau cọc: mất cọc, số dư 70% đã trả được hoàn đủ', async () => {
    const id = 'ORD-2026-0115' // ready_for_pickup, đã trả 70% ở test trước
    const b = (await bookingsApi.get(id))!
    const out = await customerBookingsApi.cancel(CUSTOMER.name, id, 'Ngựa ốm')
    expect(out.status).toBe('cancelled')
    expect(out.cancellation).toMatchObject({ by: 'customer', refund: b.balance!.amount })
  })
  it('Manager hủy đơn đã cọc: hoàn đủ cọc, chỉ khi xe chưa nhận ngựa', async () => {
    const id = 'ORD-2026-0120' // en_route_to_pickup
    const b = (await bookingsApi.get(id))!
    expect(await fail(bookingsApi.managerCancel(id, 'Quản lý', ' '))).toMatch(/lý do/)
    const out = await bookingsApi.managerCancel(id, 'Quản lý', 'Nhà xe hết xe')
    expect(out.status).toBe('cancelled')
    expect(out.cancellation).toMatchObject({ by: 'manager', refund: b.payment!.amount + (b.balance?.amount ?? 0) })
    expect(await fail(bookingsApi.managerCancel('ORD-2026-0105', 'Quản lý', 'x'))).toMatch(/đã cọc/)
  })
  it('chốt phương án: xe thiếu giấy đăng kiểm bị từ chối; tuyến quốc tế còn cần giấy phép liên vận', async () => {
    const created = await customerBookingsApi.create(CUSTOMER.name, { ...(() => { const d = { type: 'domestic' as const, origin: { id: 'KHO-DN', name: 'Kho Đồng Nai', country: 'VN' as const }, dest: { id: 'CLB-SG', name: 'CLB', country: 'VN' as const }, departAt: Date.now() + 40 * 86_400_000, consignor: { name: 'a', phone: '0901000011', idNumber: '1', address: 'x' }, consignee: { name: 'b', phone: '0901000012', idNumber: '2', address: 'y' }, horses: [{ horseId: 'H-001', name: 'S', microchip: 'VN-985211', breed: 'Thoroughbred', sex: 'gelding' as const, stall: 'standard' as const, feedPackage: 'basic' as const, waterPlan: 'every_3h' as const, insurance: { opted: false } }] }; return d })() })
    const b = await bookingsApi.activate(created.id, 'Quản lý', { id: 'KD-01', name: SP }, { id: 'DP-01', name: 'Trần Minh' })
    const route = buildRoutePlan(b, Date.now() + 40 * 86_400_000)
    const t = await freeTrip(b)
    await vehiclesApi.update(t.vehicleId, { inspectionNo: '' })
    expect(await fail(bookingsApi.confirmPlan(b.id, 'Trần Minh', { trips: [t], route, note: '' }))).toMatch(/đăng kiểm/)
    await vehiclesApi.update(t.vehicleId, { inspectionNo: 'KD-2026-RESTORED' })
  })
})

describe('cửa khẩu do Coordinator chốt', () => {
  const loc = (id: string, c: 'VN' | 'KH' | 'LA') => ({ id, name: COUNTRY_LOCATIONS[c].find(l => l.id === id)!.name, country: c })
  const input = () => ({
    type: 'international' as const, origin: loc('KHO-DN', 'VN'), dest: loc('KHO-PNH', 'KH'),
    departAt: Date.now() + 40 * 86_400_000, consignor: { name: 'a', phone: '0901000003', idNumber: '1', address: 'x' }, consignee: { name: 'b', phone: '0901000004', idNumber: '2', address: 'y' },
    horses: [{ horseId: 'H-001', name: 'Storm Runner', microchip: 'VN-985211', breed: 'Thoroughbred', sex: 'gelding' as const, stall: 'standard' as const, feedPackage: 'basic' as const, waterPlan: 'every_3h' as const, insurance: { opted: false } }],
  })
  const plan = async (gate?: string) => {
    const created = await customerBookingsApi.create(CUSTOMER.name, input())
    expect(created.gate).toBeUndefined() // khách không chọn cửa khẩu
    const b = await bookingsApi.activate(created.id, 'Quản lý', { id: 'KD-01', name: SP }, { id: 'DP-01', name: 'Trần Minh' })
    const route = buildRoutePlan({ ...b, gate }, Date.now() + 40 * 86_400_000)
    const t = await freeTrip(b)
    return { id: b.id, call: (g?: string) => bookingsApi.confirmPlan(b.id, 'Trần Minh', { trips: [t], route, gate: g, note: '' }) }
  }
  it('tuyến quốc tế phải có cửa khẩu đúng nước đến mới chốt được', async () => {
    const p = await plan('Mộc Bài – Bavet')
    expect(await fail(p.call(undefined))).toMatch(/cửa khẩu/)
    expect(await fail(p.call('Lao Bảo – Densavanh'))).toMatch(/cửa khẩu/)
    expect((await bookingsApi.get(p.id))!.plan).toBeUndefined()
  })
  it('chốt xong thì đơn có cửa khẩu đã khóa và báo giá tính theo cửa khẩu đó', async () => {
    const p = await plan('Tịnh Biên – Phnom Den')
    const b = await p.call('Tịnh Biên – Phnom Den')
    expect(b.gate).toBe('Tịnh Biên – Phnom Den')
    const { km } = await bookingsApi.quoteDraft(p.id)
    expect(km).toBe(routeKm(b.origin, b.dest, 'Tịnh Biên – Phnom Den'))
    expect(km).not.toBe(routeKm(b.origin, b.dest, 'Mộc Bài – Bavet'))
  })
  it('tuyến nội địa không cần và không nhận cửa khẩu', async () => {
    const id = 'ORD-2026-0102'
    const b = (await bookingsApi.get(id))!
    const route = buildRoutePlan(b, Date.now() + 40 * 86_400_000)
    const t = await freeTrip(b)
    expect((await bookingsApi.confirmPlan(id, 'Trần Minh', { trips: [t], route, gate: 'Mộc Bài – Bavet', note: '' })).gate).toBeUndefined()
  })
})

describe('từ chối đơn (Flow 1)', () => {
  it('Manager chỉ từ chối được đơn chờ tiếp nhận và phải ghi lý do; khách thấy lý do', async () => {
    expect(await fail(bookingsApi.rejectOrder('ORD-2026-0101', 'Quản lý', 'manager', '  '))).toMatch(/lý do/)
    expect(await fail(bookingsApi.rejectOrder('ORD-2026-0102', 'Quản lý', 'manager', 'Không nhận'))).toMatch(/chờ tiếp nhận/)
    const b = await bookingsApi.rejectOrder('ORD-2026-0101', 'Quản lý', 'manager', 'Tuyến chưa khai thác')
    expect(b.status).toBe('rejected')
    const view = await customerBookingsApi.get(CUSTOMER.name, 'ORD-2026-0101')
    expect(view!.rejection).toMatchObject({ role: 'manager', reason: 'Tuyến chưa khai thác' })
  })
  it('Coordinator không duyệt xe và lộ trình: đơn bị từ chối, không tiếp nhận lại được', async () => {
    expect(await fail(bookingsApi.rejectOrder('ORD-2026-0101', 'Điều phối', 'coordinator', 'x'))).toMatch(/đang thẩm định/)
    const b = await bookingsApi.rejectOrder('ORD-2026-0102', 'Điều phối', 'coordinator', 'Không đủ xe ngày D')
    expect(b.status).toBe('rejected')
    expect(await fail(bookingsApi.rejectOrder('ORD-2026-0102', 'Điều phối', 'coordinator', 'x'))).toMatch(/đang thẩm định/)
  })
})
