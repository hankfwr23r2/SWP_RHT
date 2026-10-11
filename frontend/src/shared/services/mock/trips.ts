// Chuyến xe nội bộ của Điều phối (mã TR-xxxx, nối sang đơn qua orderId). Gốc: Fleet And Route/ops_data.js (trips).
// Khách, tuyến, số ngựa, ngày đi lấy từ đơn; chuyến chỉ giữ phần của Điều phối: kết quả khảo sát và các chặng.
// Nối mã chuyến cũ sang đơn cùng khách/tuyến trong bộ đơn chung:
//   TR-9021 → EQ-2026-1080 · TR-9042 → EQ-2026-1079 · TR-9028 → EQ-2026-1028 · TR-9035 → EQ-2026-1045
//   TR-9036 → EQ-2026-1078 · TR-9050 → EQ-2026-1044 · TR-9051 → EQ-2026-1057 · TR-9018 → EQ-2026-1036
// Các đơn khác đã qua bước lập lộ trình được tạo chuyến TR-91xx, chặng suy từ điểm dừng, xe và hộ tống theo kết quả thẩm định (review).
import { atTime } from '../../lib/dates'
import { legsFromStops } from '../../lib/trip'
import type { Order } from '../../types/order'
import { seedCrew, seedDriverOf, seedVehicles } from './fleet'
import { seedOrders } from './orders'
import { seedOtherOrders } from './orders-others'

export interface Leg { no: number; from: string; to: string; vehicleId: string; driverId: string; escortId: string }
export interface OpsTrip {
  id: string
  orderId: string
  feasible?: boolean // kết quả khảo sát (chưa có = chờ khảo sát)
  assessNote: string
  assessedAt?: number
  legs: Leg[]
  activity: { time: number; text: string }[] // nhật ký hiện trường (ảnh chặng) ở trang Giám sát
}

const leg = (no: number, from: string, to: string, vehicleId = '', driverId = '', escortId = ''): Leg => ({ no, from, to, vehicleId, driverId, escortId })

// Chặng của một đơn: xe lấy theo biển số trong review, tài xế theo xe, hộ tống theo tên trong review
function legsOf(o: Order): Leg[] {
  const vehicles = seedVehicles()
  const crew = seedCrew()
  const v = o.review && vehicles.find(x => o.review!.vehicle.startsWith(x.plate))
  const escort = o.review && crew.find(c => c.role === 'escort' && o.review!.grooms.startsWith(c.name))
  return legsFromStops(o.stops, o.from, o.to).map(([from, to], i) => leg(i + 1, from, to, v?.id ?? '', v ? seedDriverOf(v.id) : '', escort?.id ?? ''))
}

// Đơn đã qua bước lập lộ trình mà chưa có chuyến ghi sẵn
const ROUTED: Order['status'][] = ['awaiting_payment', 'paid', 'in_transit', 'delivered', 'disputed', 'completed']
const hasTrip = (o: Order) => !!o.coordinator && (o.stage === 'routing' || o.stage === 'approval' || ROUTED.includes(o.status))

export function seedTrips(): OpsTrip[] {
  const orders = [...seedOrders(), ...seedOtherOrders()]
  const order = (id: string) => orders.find(o => o.id === id)!
  const trips: OpsTrip[] = [
    { id: 'TR-9021', orderId: 'EQ-2026-1080', feasible: true, assessNote: 'Tuyến Cầu Treo – Nam Phao, đi 2 ngày.', legs: [
      leg(1, 'Hà Nội (Trang trại CLB)', 'Cửa khẩu Cầu Treo', 'VH-001', 'TX-01', 'NV-01'),
      leg(2, 'Cửa khẩu Nam Phao', 'Vientiane Turf Club', 'VH-001', 'TX-01', 'NV-01'),
    ], activity: [] },
    { id: 'TR-9042', orderId: 'EQ-2026-1079', feasible: true, assessNote: 'Nghỉ đêm tại trạm Tuy Hòa.', legs: [
      leg(1, 'Trường đua Phú Thọ (TP.HCM)', 'Trạm nghỉ Tuy Hòa (Phú Yên)', 'VH-003', 'TX-03', 'NV-02'),
      leg(2, 'Trạm nghỉ Tuy Hòa (Phú Yên)', 'Trường đua Sông Hàn (Đà Nẵng)', 'VH-003', 'TX-03', 'NV-02'),
    ], activity: [{ time: atTime(0, '07:15'), text: 'TR-9042 — Đã tải ảnh chặng: rời trạm nghỉ Tuy Hòa' }] },
    { id: 'TR-9028', orderId: 'EQ-2026-1028', feasible: true, assessNote: 'Tuyến Mộc Bài – Bavet, đi trong ngày.', legs: legsOf(order('EQ-2026-1028')).map(l => ({ ...l, vehicleId: 'VH-007', driverId: 'TX-07', escortId: 'NV-02' })), activity: [] },
    { id: 'TR-9035', orderId: 'EQ-2026-1045', assessNote: '', legs: legsOf(order('EQ-2026-1045')), activity: [] },
    { id: 'TR-9036', orderId: 'EQ-2026-1078', assessNote: '', legs: legsOf(order('EQ-2026-1078')), activity: [] },
    { id: 'TR-9050', orderId: 'EQ-2026-1044', feasible: true, assessNote: 'Tuyến Cầu Treo – Nam Phao, đi 2 ngày.', assessedAt: atTime(-1, '15:00'), legs: [
      leg(1, 'Trường đua Thiên Mã (Hà Nội)', 'Trạm nghỉ Vinh (Nghệ An)'),
      leg(2, 'Trạm nghỉ Vinh (Nghệ An)', 'Vientiane Turf Club'),
    ], activity: [] },
    { id: 'TR-9051', orderId: 'EQ-2026-1057', feasible: true, assessNote: 'Tuyến Tịnh Biên – Phnom Den, đi trong ngày.', assessedAt: atTime(0, '09:00'), legs: [
      leg(1, 'Trang trại Mekong (Cần Thơ)', 'Cửa khẩu Tịnh Biên (An Giang)'),
      leg(2, 'Cửa khẩu Phnom Den (Takeo)', 'Trường đua Phnom Penh Royal Turf'),
    ], activity: [] },
    { id: 'TR-9018', orderId: 'EQ-2026-1036', feasible: true, assessNote: 'Tuyến nội địa ngắn.', legs: legsOf(order('EQ-2026-1036')), activity: [] },
  ]
  let next = 9101
  orders.filter(o => hasTrip(o) && !trips.some(t => t.orderId === o.id)).forEach(o => {
    const routed = o.stage !== 'routing'
    trips.push({ id: `TR-${next++}`, orderId: o.id, feasible: routed || undefined, assessNote: '', legs: legsOf(o), activity: [] })
  })
  return trips
}
