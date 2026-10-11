// Lịch sử chuyến đã hoàn thành (trang Báo cáo chuyến đi, bảng "Hiệu suất chuyến đi gần đây" ở Bảng điều khiển).
// Gốc: Manager/manager_trip_reports.html (17 dòng, 1 báo cáo chi tiết viết cứng) + manager_dashboard.html.
// Đồng bộ: khách lấy từ bộ khách chung, tuyến trong 3 nước; mỗi báo cáo có chặng, nhật ký sức khỏe, sự kiện, đánh giá.
import { atTime } from '../../lib/dates'

export interface ReportLeg { title: string; kind: 'ground' | 'gate'; meta: string; route: string; planned: [string, string]; actual: [string, string] }
export interface TripReport {
  id: string // mã đơn
  tripId: string
  customer: string
  route: string
  type: string
  horses: string
  escort: string
  completedAt: number
  plannedAt: number
  otd: 'on_time' | 'early' | 'late'
  otdMinutes: number
  incidentCost: number
  incidentNote: string
  totalTime: string
  legs: ReportLeg[]
  health: [time: string, event: string, ok: boolean][]
  events: [time: string, event: string, ok: boolean][]
  rating: number
  feedback: string
}

// Tuyến mẫu: chặng dự kiến / thực tế (giờ)
const ROUTES: Record<string, { route: string; type: string; total: string; legs: ReportLeg[] }> = {
  hn_vte: { route: 'Hà Nội → Viêng Chăn (LA)', type: 'Đường bộ Xuyên biên giới', total: '32 Giờ 15 Phút', legs: [
    { title: 'Chặng 1: Hà Nội ➔ Vinh', kind: 'ground', meta: 'Xe chuyên dụng: 29H-12345', route: 'Trường đua Thiên Mã (Hà Nội) ➔ Trạm nghỉ Vinh (Nghệ An)', planned: ['08:00', '14:30'], actual: ['08:00', '14:30'] },
    { title: 'Chặng 2: Vinh ➔ Cửa khẩu Cầu Treo', kind: 'ground', meta: 'Xe chuyên dụng: 29H-12345', route: 'Trạm nghỉ Vinh ➔ Cửa khẩu Cầu Treo (Hà Tĩnh), sáng ngày 2', planned: ['06:00', '08:00'], actual: ['06:00', '07:45'] },
    { title: 'Chặng 3: Thông quan Cầu Treo ➔ Nam Phao', kind: 'gate', meta: 'Hải quan & kiểm dịch thú y', route: 'Cửa khẩu Cầu Treo (VN) ➔ Cửa khẩu Nam Phao (LA)', planned: ['08:00', '10:00'], actual: ['08:00', '10:30'] },
    { title: 'Chặng 4: Nam Phao ➔ Viêng Chăn', kind: 'ground', meta: 'Xe chuyên dụng: 29H-12345', route: 'Cửa khẩu Nam Phao ➔ Vientiane Turf Club (Viêng Chăn, LA)', planned: ['10:00', '16:15'], actual: ['10:30', '16:15'] },
  ] },
  dn_pnh: { route: 'Đồng Nai → Phnom Penh (KH)', type: 'Đường bộ Xuyên biên giới', total: '7 Giờ 20 Phút', legs: [
    { title: 'Chặng 1: Đồng Nai ➔ Cửa khẩu Mộc Bài', kind: 'ground', meta: 'Xe chuyên dụng: 51C-123.45', route: 'Trang trại Long Thành ➔ Cửa khẩu Mộc Bài (Tây Ninh)', planned: ['06:00', '09:00'], actual: ['06:00', '08:50'] },
    { title: 'Chặng 2: Thông quan Mộc Bài ➔ Bavet', kind: 'gate', meta: 'Hải quan & kiểm dịch thú y', route: 'Cửa khẩu Mộc Bài (VN) ➔ Cửa khẩu Bavet (KH)', planned: ['09:00', '10:30'], actual: ['08:50', '10:20'] },
    { title: 'Chặng 3: Bavet ➔ Phnom Penh', kind: 'ground', meta: 'Xe chuyên dụng: 51C-123.45', route: 'Cửa khẩu Bavet ➔ Trường đua Phnom Penh Royal Turf', planned: ['10:30', '13:30'], actual: ['10:20', '13:20'] },
  ] },
  hcm_dng: { route: 'TP.HCM → Đà Nẵng', type: 'Đường bộ Nội địa', total: '26 Giờ 40 Phút', legs: [
    { title: 'Chặng 1: TP.HCM ➔ Tuy Hòa', kind: 'ground', meta: 'Xe chuyên dụng: 51C-888.99', route: 'Trường đua Phú Thọ (TP.HCM) ➔ Trạm nghỉ Tuy Hòa (Phú Yên)', planned: ['06:00', '17:00'], actual: ['06:00', '17:00'] },
    { title: 'Chặng 2: Tuy Hòa ➔ Đà Nẵng', kind: 'ground', meta: 'Xe chuyên dụng: 51C-888.99', route: 'Trạm nghỉ Tuy Hòa ➔ Trường đua Sông Hàn (Đà Nẵng), ngày 2', planned: ['06:00', '14:00'], actual: ['06:00', '14:40'] },
  ] },
  ct_pnh: { route: 'Cần Thơ → Phnom Penh (KH)', type: 'Đường bộ Xuyên biên giới', total: '6 Giờ 05 Phút', legs: [
    { title: 'Chặng 1: Cần Thơ ➔ Cửa khẩu Tịnh Biên', kind: 'ground', meta: 'Xe chuyên dụng: 65C-101.22', route: 'Trang trại Mekong ➔ Cửa khẩu Tịnh Biên (An Giang)', planned: ['07:00', '09:30'], actual: ['07:00', '09:20'] },
    { title: 'Chặng 2: Thông quan Tịnh Biên ➔ Phnom Den', kind: 'gate', meta: 'Hải quan & kiểm dịch thú y', route: 'Cửa khẩu Tịnh Biên (VN) ➔ Cửa khẩu Phnom Den (KH)', planned: ['09:30', '11:00'], actual: ['09:20', '10:45'] },
    { title: 'Chặng 3: Phnom Den ➔ Phnom Penh', kind: 'ground', meta: 'Xe chuyên dụng: 65C-101.22', route: 'Cửa khẩu Phnom Den ➔ Trường đua Phnom Penh Royal Turf', planned: ['11:00', '13:15'], actual: ['10:45', '13:05'] },
  ] },
  bd_hcm: { route: 'Bình Dương → TP.HCM', type: 'Đường bộ Nội địa', total: '1 Giờ 30 Phút', legs: [
    { title: 'Chặng 1: Bình Dương ➔ TP.HCM', kind: 'ground', meta: 'Xe chuyên dụng: 61C-345.67', route: 'Trường đua Đại Nam ➔ Trường đua Phú Thọ', planned: ['08:00', '09:30'], actual: ['08:00', '09:30'] },
  ] },
}

const HEALTH_OK: TripReport['health'] = [
  ['08:00 (Điểm Khởi Hành)', 'Nhịp tim: 38 bpm | Nhiệt độ: 37.5°C', true],
  ['12:30 (Đang di chuyển)', 'Nhịp tim: 42 bpm | Nhiệt độ: 37.8°C', true],
  ['16:45 (Đã đến nơi)', 'Nhịp tim: 40 bpm | Nhiệt độ: 37.6°C', true],
]
const FEEDBACK = 'Dịch vụ xuất sắc. Đội ngũ chăm sóc cực kỳ chuyên nghiệp và ngựa đến nơi trong tình trạng hoàn hảo. Chắc chắn chúng tôi sẽ tiếp tục sử dụng dịch vụ cho các giải đua sắp tới.'

// [mã đơn, mã chuyến, khách, số ngày trước, tuyến, ngựa, NV chăm sóc, OTD, phút lệch, chi phí sự cố, ghi chú sự cố, đánh giá]
const ROWS: [string, string, string, number, keyof typeof ROUTES, string, string, TripReport['otd'], number, number, string, number][] = [
  ['EQ-2026-9830', 'TR-9102', 'Trường đua Thiên Mã', 11, 'hn_vte', '1 x Ngựa Thuần chủng (Ngựa đực)', 'Đỗ Văn Nam', 'late', 30, 0, '', 5],
  ['EQ-2026-9828', 'TR-9101', 'CLB Ngựa Phú Thọ', 14, 'hcm_dng', '3 x Thoroughbred', 'Võ Thị Lan', 'on_time', 0, 0, '', 5],
  ['EQ-2026-9822', 'TR-9100', 'Trang trại Long Thành', 21, 'dn_pnh', '1 x Thoroughbred', 'Võ Thị Lan', 'early', 10, 0, '', 5],
  ['EQ-2026-9815', 'TR-9099', 'Mekong Stud', 29, 'ct_pnh', '1 x Thoroughbred', 'Huỳnh Thị Mai', 'early', 10, 0, '', 4],
  ['EQ-2026-9810', 'TR-9098', 'Đại Nam Racing', 37, 'bd_hcm', '2 x Thoroughbred', 'Huỳnh Thị Mai', 'on_time', 0, 0, '', 5],
  ['EQ-2026-9805', 'TR-9097', 'Vientiane Turf Club', 42, 'hn_vte', '2 x Thoroughbred', 'Đỗ Văn Nam', 'late', 180, 1_500_000, 'Tắc đường', 4],
  ['EQ-2026-9800', 'TR-9096', 'CLB Ngựa Hà Thành', 47, 'hcm_dng', '2 x Arabian', 'Võ Thị Lan', 'on_time', 0, 0, '', 5],
  ['EQ-2026-9795', 'TR-9095', 'Trang trại Tây Ninh Stud', 52, 'dn_pnh', '2 x Thoroughbred', 'Huỳnh Thị Mai', 'on_time', 0, 0, '', 5],
  ['EQ-2026-9790', 'TR-9094', 'Royal Cambodia Stables', 56, 'dn_pnh', '1 x Thoroughbred', 'Võ Thị Lan', 'early', 15, 0, '', 5],
  ['EQ-2026-9785', 'TR-9093', 'Hoàng Gia Stud', 63, 'hn_vte', '1 x Arabian', 'Đỗ Văn Nam', 'on_time', 0, 800_000, 'Thay lốp dự phòng', 4],
  ['EQ-2026-9780', 'TR-9092', 'Trang trại Ba Vì', 68, 'bd_hcm', '3 x Thoroughbred', 'Huỳnh Thị Mai', 'on_time', 0, 0, '', 5],
  ['EQ-2026-9775', 'TR-9091', 'Angkor Equestrian', 73, 'dn_pnh', '2 x Arabian', 'Võ Thị Lan', 'late', 45, 0, '', 4],
  ['EQ-2026-9770', 'TR-9090', 'Trang trại Sông Hàn', 78, 'hcm_dng', '1 x Arabian', 'Võ Thị Lan', 'on_time', 0, 0, '', 5],
  ['EQ-2026-9765', 'TR-9089', 'Savan Horse Club', 83, 'hn_vte', '2 x Thoroughbred', 'Đỗ Văn Nam', 'on_time', 0, 0, '', 5],
  ['EQ-2026-9760', 'TR-9088', 'Luang Prabang Racing', 87, 'hn_vte', '1 x Arabian', 'Đỗ Văn Nam', 'early', 20, 0, '', 5],
  ['EQ-2026-9755', 'TR-9087', 'CLB Ngựa Sài Gòn', 93, 'hcm_dng', '2 x Thoroughbred', 'Võ Thị Lan', 'on_time', 0, 0, '', 5],
  ['EQ-2026-9750', 'TR-9086', 'CLB Ngựa Đức Hòa', 98, 'ct_pnh', '1 x Thoroughbred', 'Huỳnh Thị Mai', 'on_time', 0, 0, '', 5],
]

export const seedTripHistory = (): TripReport[] => ROWS.map(([id, tripId, customer, daysAgo, key, horses, escort, otd, mins, cost, costNote, rating]) => {
  const r = ROUTES[key]
  const completedAt = atTime(-daysAgo, r.legs[r.legs.length - 1].actual[1])
  const shift = (otd === 'late' ? 1 : otd === 'early' ? -1 : 0) * mins * 60_000
  return {
    id, tripId, customer, route: r.route, type: r.type, horses, escort, completedAt, plannedAt: completedAt - shift,
    otd, otdMinutes: mins, incidentCost: cost, incidentNote: costNote, totalTime: r.total, legs: r.legs,
    health: HEALTH_OK,
    events: [
      ['09:15', otd === 'late' ? 'Đường đèo xóc. Ngựa hơi lo lắng. Đã giảm tốc và áp dụng quy trình làm dịu.' : 'Dừng nghỉ theo kế hoạch, ngựa ổn định.', otd !== 'late'],
      ['14:00', 'Hoàn tất quy trình cho ăn và uống nước thành công.', true],
    ],
    rating, feedback: FEEDBACK,
  }
})
