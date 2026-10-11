// Đơn của các khách khác (gốc: Manager/manager_tiep_nhan.js, manager_phe_duyet.js, manager_phan_cong.js).
// Gộp đơn trùng giữa các trang; đánh số lại mã bị trùng mà khác đơn:
//   tiếp nhận 1052 → 1076 · tiếp nhận 1050 → 1077 · nhân sự 1058 → 1078.
// Ngày tính tương đối theo ngày soạn dữ liệu cũ (24/09/2026 = hôm nay).
import { DAY, HOUR } from '../../config/business-rules'
import { atTime, daysFromToday, workdaysAgo } from '../../lib/dates'
import { DOCS_CROSS_BORDER, DOCS_DOMESTIC, type DocKey } from '../../config/documents'
import type { DocDecision, Horse, Order, ServiceLine } from '../../types/order'

const H = (name: string, breed: string, sex: string, chip: string): Horse => ({ name, breed, sex, chip })
// Giá ở trang Tiếp nhận chỉ có [hạng mục, số tiền]
const items = (list: [string, number][]): ServiceLine[] => list.map(([name, amount]) => [name, '', amount])
const std = (vehicle: string, km: string, quarantine: [string, string, number], care: [string, number], insurance: number): ServiceLine[] => [
  ['Vận chuyển đường bộ', `Xe chuyên dụng ${vehicle} · khoang tiêu chuẩn · ${km}`, 0],
  [quarantine[0], quarantine[1], quarantine[2]],
  ['Chăm sóc dọc đường', care[0], care[1]],
  ['Bảo hiểm vận chuyển', 'Gói cơ bản', insurance],
]
const withFreight = (lines: ServiceLine[], freight: number): ServiceLine[] => [[lines[0][0], lines[0][1], freight], ...lines.slice(1)]
const CARE_FULL = 'NV chăm sóc đi kèm · cỏ khô Timothy · nước điện giải'
const ok = (customer: string) => ({ customer, customerEmail: '', phoneLast4: '0000' })

// Horses
const baDen = H('Bà Đen', 'Thoroughbred', 'Cái', 'VN-610118')
const vamCo = H('Vàm Cỏ', 'Thoroughbred', 'Đực', 'VN-610125')
const thangLong = H('Thăng Long', 'Thoroughbred', 'Đực', 'VN-118830')
const hoGuom = H('Hồ Gươm', 'Arabian', 'Cái', 'VN-118847')
const longBien = H('Long Biên', 'Anglo-Arab', 'Thiến', 'VN-118852')
const hoaTien = H('Hỏa Tiễn', 'Thoroughbred', 'Đực', 'VN-420017')
const nganHa = H('Ngân Hà', 'Thoroughbred', 'Cái', 'VN-420023')
const xichTho = H('Xích Thố', 'Thoroughbred', 'Đực', 'VN-503311')
const oVan = H('Ô Vân', 'Thoroughbred', 'Cái', 'VN-503328')
const tuyetSon = H('Tuyết Sơn', 'Arabian', 'Cái', 'VN-503340')
const loiDien = H('Lôi Điện', 'Anglo-Arab', 'Thiến', 'VN-503356')
const kimO = H('Kim Ô', 'Anglo-Arab', 'Thiến', 'VN-771089')
const cuuLong = H('Cửu Long', 'Thoroughbred', 'Đực', 'VN-812004')
const ngocHoang = H('Ngọc Hoàng', 'Arabian', 'Đực', 'VN-902214')
const tanVien = H('Tản Viên', 'Thoroughbred', 'Đực', 'VN-330145')
const sonTinh = H('Sơn Tinh', 'Thoroughbred', 'Thiến', 'VN-330152')
const myNuong = H('Mỵ Nương', 'Arabian', 'Cái', 'VN-330168')
const mekongWind = H('Mekong Wind', 'Arabian', 'Cái', 'LA-118903')

const valid: DocDecision = { decision: 'valid', reason: '' }
// Đã nhận bản gốc mọi giấy khách nộp
const received = (keys: DocKey[], at: number) => Object.fromEntries(keys.map(k => [k, at]))

export function seedOtherOrders(): Order[] {
  const now = Date.now()
  return [
    // ===== Trang Tiếp nhận: đơn mới =====
    {
      ...ok('Trang trại Tây Ninh Stud'), id: 'EQ-2026-1076', submittedAt: atTime(0, '08:15'), departAt: daysFromToday(14),
      from: 'Trang trại Tây Ninh Stud (Tây Ninh, VN)', to: 'Trường đua Phnom Penh Royal Turf (Phnom Penh, KH)',
      routeShort: 'Tây Ninh → Phnom Penh (KH)', border: 'Mộc Bài – Bavet', distance: '180 km', duration: '~5 giờ', horses: [baDen, vamCo],
      customerNote: 'Ngựa Vàm Cỏ nhạy cảm tiếng ồn, đề nghị xếp ngăn cuối xe.', hold: '1 xe chuyên dụng 2 ngăn',
      services: items([['Cước vận chuyển đường bộ', 12_500_000], ['Phí kiểm dịch & thủ tục xuất cảnh', 6_500_000], ['Chăm sóc ngựa dọc đường', 1_200_000], ['Bảo hiểm vận chuyển', 1_300_000]]),
      status: 'processing', stage: 'intake',
    },
    {
      ...ok('CLB Ngựa Hà Thành'), id: 'EQ-2026-1051', submittedAt: atTime(0, '07:40'), departAt: daysFromToday(16),
      from: 'Trường đua Thiên Mã (Sóc Sơn, Hà Nội, VN)', to: 'Trường đua Sông Hàn (Đà Nẵng, VN)',
      routeShort: 'Hà Nội → Đà Nẵng', border: null, distance: '770 km', duration: '2 ngày', horses: [thangLong, hoGuom, longBien],
      customerNote: '', hold: '1 xe chuyên dụng 4 ngăn',
      services: items([['Cước vận chuyển đường bộ', 27_000_000], ['Phí kiểm dịch nội địa', 2_500_000], ['Chăm sóc ngựa dọc đường', 3_800_000], ['Bảo hiểm vận chuyển', 2_700_000]]),
      status: 'processing', stage: 'intake',
    },
    {
      ...ok('Luang Prabang Racing'), id: 'EQ-2026-1077', submittedAt: atTime(-1, '21:05'), departAt: daysFromToday(18),
      from: 'Trường đua Luang Prabang (Luang Prabang, LA)', to: 'Trường đua Thiên Mã (Sóc Sơn, Hà Nội, VN)',
      routeShort: 'Luang Prabang (LA) → Hà Nội', border: 'Sop Hun – Tây Trang', distance: '620 km', duration: '2 ngày',
      horses: [H('Mount Phousi', 'Arabian', 'Đực', 'LA-305512')],
      customerNote: 'Đường đèo nhiều, đề nghị có điểm nghỉ cho ngựa giữa chặng.', hold: '1 xe chuyên dụng 2 ngăn',
      services: items([['Cước vận chuyển đường bộ', 19_000_000], ['Phí kiểm dịch & thủ tục nhập cảnh', 5_500_000], ['Chăm sóc ngựa dọc đường', 2_000_000], ['Bảo hiểm vận chuyển', 1_500_000]]),
      status: 'processing', stage: 'intake',
    },
    {
      ...ok('Đại Nam Racing'), id: 'EQ-2026-1048', submittedAt: atTime(-1, '10:20'), departAt: daysFromToday(11),
      from: 'Trường đua Đại Nam (Bình Dương, VN)', to: 'Trang trại Mekong (Cần Thơ, VN)',
      routeShort: 'Bình Dương → Cần Thơ', border: null, distance: '190 km', duration: '~4.5 giờ', horses: [hoaTien, nganHa],
      customerNote: '', hold: '1 xe chuyên dụng 2 ngăn',
      services: items([['Cước vận chuyển đường bộ', 8_000_000], ['Phí kiểm dịch nội địa', 1_500_000], ['Chăm sóc ngựa dọc đường', 700_000], ['Bảo hiểm vận chuyển', 800_000]]),
      status: 'processing', stage: 'intake',
    },

    // ===== Đang kiểm dịch / lập lộ trình =====
    {
      ...ok('Royal Cambodia Stables'), id: 'EQ-2026-1047', submittedAt: atTime(-2, '09:10'), departAt: daysFromToday(10),
      from: 'Trường đua Phnom Penh Royal Turf (Phnom Penh, KH)', to: 'Trường đua Phú Thọ (TP.HCM, VN)',
      routeShort: 'Phnom Penh (KH) → TP.HCM', border: 'Bavet – Mộc Bài', distance: '230 km', duration: '~6 giờ',
      horses: [H('Tonle Sap', 'Thoroughbred', 'Đực', 'KH-220417')], customerNote: '', hold: '1 xe chuyên dụng 2 ngăn',
      services: items([['Cước vận chuyển đường bộ', 9_500_000], ['Phí kiểm dịch & thủ tục nhập cảnh', 6_000_000], ['Chăm sóc ngựa dọc đường', 800_000], ['Bảo hiểm vận chuyển', 900_000]]),
      status: 'choose_option', stage: 'inspecting', intakeAt: atTime(-2, '10:05'), inspector: 'Phạm Văn Hưng', coordinator: 'Lê Quang',
      offer: { issue: 'Hồ sơ của ngựa Tonle Sap có vấn đề không khắc phục được bằng bổ sung giấy tờ (xem báo cáo kiểm dịch).', affected: ['Tonle Sap'], options: ['replace_horse', 'recheck', 'cancel'], sentAt: now - 5 * HOUR, requoteServices: [] },
      // Thu trễ → đã tự chuyển sang Hưng; Hưng lại trễ → không còn ai chưa từng phụ trách → Manager xử lý
      task: { step: 'inspector', assigneeId: 'KD-01', assignedAt: workdaysAgo(3), pausedWorkingDays: 0, history: [{ time: workdaysAgo(3), fromId: 'KD-02', toId: 'KD-01', reason: 'Trễ hạn', auto: true }] },
    },
    {
      ...ok('Savan Horse Club'), id: 'EQ-2026-1046', submittedAt: atTime(-3, '15:45'), departAt: daysFromToday(9),
      from: 'Trường đua Savannakhet (Savannakhet, LA)', to: 'Trường đua Sông Hàn (Đà Nẵng, VN)',
      routeShort: 'Savannakhet (LA) → Đà Nẵng', border: 'Densavanh – Lao Bảo', distance: '500 km', duration: '~11 giờ',
      horses: [H('Mekong Wind', 'Arabian', 'Cái', 'LA-118903'), H('Sepon', 'Thoroughbred', 'Đực', 'LA-118917')], customerNote: '', hold: '1 xe chuyên dụng 2 ngăn',
      services: items([['Cước vận chuyển đường bộ', 16_000_000], ['Phí kiểm dịch & thủ tục nhập cảnh', 8_500_000], ['Chăm sóc ngựa dọc đường', 2_200_000], ['Bảo hiểm vận chuyển', 1_800_000]]),
      status: 'processing', stage: 'inspecting', intakeAt: atTime(-3, '17:20'), inspector: 'Nguyễn Thị Thu', coordinator: 'Trần Minh', waitingCustomer: true,
      task: { step: 'inspector', assigneeId: 'KD-02', assignedAt: workdaysAgo(1), pausedSince: workdaysAgo(1, 14), pausedWorkingDays: 0, history: [] },
    },
    {
      ...ok('Trang trại Tây Ninh Stud'), id: 'EQ-2026-1060', submittedAt: atTime(-1, '14:00'), departAt: daysFromToday(8),
      from: 'Trang trại Tây Ninh Stud (Tây Ninh, VN)', to: 'Trường đua Phnom Penh Royal Turf (Phnom Penh, KH)',
      routeShort: 'Tây Ninh → Phnom Penh (KH)', border: 'Mộc Bài – Bavet', distance: '180 km', duration: '~5 giờ', horses: [baDen], hold: '1 xe chuyên dụng 2 ngăn',
      services: items([['Cước vận chuyển đường bộ', 11_000_000], ['Phí kiểm dịch & thủ tục xuất cảnh', 3_500_000], ['Chăm sóc ngựa dọc đường', 600_000], ['Bảo hiểm vận chuyển', 700_000]]),
      status: 'processing', stage: 'inspecting', intakeAt: workdaysAgo(0), inspector: 'Phạm Văn Hưng', coordinator: 'Phạm Tâm',
      task: { step: 'inspector', assigneeId: 'KD-01', assignedAt: workdaysAgo(0), pausedWorkingDays: 0, history: [] },
    },
    {
      ...ok('Hoàng Gia Stud'), id: 'EQ-2026-1056', submittedAt: atTime(-3, '09:00'), departAt: daysFromToday(15),
      from: 'Trường đua Thiên Mã (Sóc Sơn, Hà Nội, VN)', to: 'Trường đua Luang Prabang (Luang Prabang, LA)',
      routeShort: 'Hà Nội → Luang Prabang (LA)', border: 'Tây Trang – Sop Hun', distance: '620 km', duration: '2 ngày', horses: [ngocHoang], hold: '1 xe chuyên dụng 2 ngăn',
      services: withFreight(std('2 ngăn', '620 km', ['Kiểm dịch & thủ tục xuất cảnh', 'Trọn gói: xét nghiệm, chứng nhận, thông quan Tây Trang – Sop Hun', 4_200_000], [CARE_FULL, 1_800_000], 1_100_000), 19_000_000),
      status: 'processing', stage: 'inspecting', intakeAt: workdaysAgo(2), inspector: 'Nguyễn Thị Thu', coordinator: 'Phạm Tâm', waitingCustomer: true,
      task: { step: 'inspector', assigneeId: 'KD-02', assignedAt: workdaysAgo(2), pausedSince: workdaysAgo(1, 14), pausedWorkingDays: 0, history: [] },
    },
    {
      ...ok('CLB Ngựa Phú Thọ'), id: 'EQ-2026-1045', submittedAt: atTime(-4, '11:00'), departAt: daysFromToday(8),
      from: 'Trường đua Phú Thọ (TP.HCM, VN)', to: 'Trường đua Angkor (Siem Reap, KH)',
      routeShort: 'TP.HCM → Siem Reap (KH)', border: 'Mộc Bài – Bavet', distance: '550 km', duration: '2 ngày', horses: [xichTho, oVan, tuyetSon],
      customerNote: '', hold: '1 xe chuyên dụng 4 ngăn',
      services: items([['Cước vận chuyển đường bộ', 28_000_000], ['Phí kiểm dịch & thủ tục xuất cảnh', 9_500_000], ['Chăm sóc ngựa dọc đường', 3_500_000], ['Bảo hiểm vận chuyển', 3_000_000]]),
      status: 'processing', stage: 'routing', intakeAt: atTime(-4, '13:30'), inspector: 'Phạm Văn Hưng', coordinator: 'Phạm Tâm',
      task: { step: 'coordinator', assigneeId: 'DP-03', assignedAt: workdaysAgo(0), pausedWorkingDays: 0, history: [] },
    },
    {
      ...ok('Trường đua Thiên Mã'), id: 'EQ-2026-1044', submittedAt: atTime(-5, '14:25'), departAt: daysFromToday(7),
      from: 'Trường đua Thiên Mã (Sóc Sơn, Hà Nội, VN)', to: 'Vientiane Turf Club (Viêng Chăn, LA)',
      routeShort: 'Hà Nội → Viêng Chăn (LA)', border: 'Cầu Treo – Nam Phao', distance: '730 km', duration: '2 ngày', horses: [kimO],
      customerNote: '', hold: '1 xe chuyên dụng 2 ngăn',
      services: items([['Cước vận chuyển đường bộ', 21_000_000], ['Phí kiểm dịch & thủ tục xuất cảnh', 4_500_000], ['Chăm sóc ngựa dọc đường', 1_800_000], ['Bảo hiểm vận chuyển', 1_200_000]]),
      status: 'processing', stage: 'routing', intakeAt: atTime(-5, '16:00'), inspector: 'Nguyễn Thị Thu', coordinator: 'Lê Quang',
      // Lê Quang đang nghỉ → hệ thống sẽ tự chuyển khi mở trang Nhân sự
      task: { step: 'coordinator', assigneeId: 'DP-02', assignedAt: workdaysAgo(1), pausedWorkingDays: 0, history: [] },
    },
    {
      ...ok('CLB Ngựa Hà Thành'), id: 'EQ-2026-1061', submittedAt: atTime(-4, '09:30'), departAt: daysFromToday(13),
      from: 'Trường đua Thiên Mã (Sóc Sơn, Hà Nội, VN)', to: 'Trường đua Sông Hàn (Đà Nẵng, VN)',
      routeShort: 'Hà Nội → Đà Nẵng', border: null, distance: '770 km', duration: '2 ngày', horses: [longBien], hold: '1 xe chuyên dụng 2 ngăn',
      services: items([['Cước vận chuyển đường bộ', 16_000_000], ['Phí kiểm dịch nội địa', 900_000], ['Chăm sóc ngựa dọc đường', 1_300_000], ['Bảo hiểm vận chuyển', 900_000]]),
      status: 'processing', stage: 'routing', intakeAt: workdaysAgo(3), inspector: 'Phạm Văn Hưng', coordinator: 'Phạm Tâm',
      task: { step: 'coordinator', assigneeId: 'DP-03', assignedAt: workdaysAgo(1), pausedWorkingDays: 0, history: [] },
    },
    {
      ...ok('Angkor Equestrian'), id: 'EQ-2026-1059', submittedAt: atTime(-5, '10:00'), departAt: daysFromToday(9),
      from: 'Trường đua Angkor (Siem Reap, KH)', to: 'Trường đua Phú Thọ (TP.HCM, VN)',
      routeShort: 'Siem Reap (KH) → TP.HCM', border: 'Bavet – Mộc Bài', distance: '550 km', duration: '2 ngày', horses: [H('Angkor Wat', 'Arabian', 'Đực', 'KH-118231')], hold: '1 xe chuyên dụng 2 ngăn',
      services: items([['Cước vận chuyển đường bộ', 15_000_000], ['Phí kiểm dịch & thủ tục nhập cảnh', 4_000_000], ['Chăm sóc ngựa dọc đường', 1_200_000], ['Bảo hiểm vận chuyển', 1_000_000]]),
      status: 'processing', stage: 'routing', intakeAt: workdaysAgo(4), inspector: 'Nguyễn Thị Thu', coordinator: 'Phạm Tâm',
      // Minh trễ hôm qua → đã tự chuyển sang Tâm
      task: { step: 'coordinator', assigneeId: 'DP-03', assignedAt: workdaysAgo(1, 10), pausedWorkingDays: 0, history: [{ time: workdaysAgo(1, 10), fromId: 'DP-01', toId: 'DP-03', reason: 'Trễ hạn', auto: true }] },
    },
    {
      ...ok('Đại Nam Racing'), id: 'EQ-2026-1078', submittedAt: atTime(-4, '15:00'), departAt: daysFromToday(12),
      from: 'Trường đua Đại Nam (Bình Dương, VN)', to: 'Trang trại Mekong (Cần Thơ, VN)',
      routeShort: 'Bình Dương → Cần Thơ', border: null, distance: '190 km', duration: '~4.5 giờ', horses: [hoaTien], hold: '1 xe chuyên dụng 2 ngăn',
      services: items([['Cước vận chuyển đường bộ', 6_000_000], ['Phí kiểm dịch nội địa', 900_000], ['Chăm sóc ngựa dọc đường', 400_000], ['Bảo hiểm vận chuyển', 500_000]]),
      status: 'processing', stage: 'routing', intakeAt: workdaysAgo(3), inspector: 'Phạm Văn Hưng', coordinator: 'Trần Minh',
      task: { step: 'coordinator', assigneeId: 'DP-01', assignedAt: workdaysAgo(1), pausedWorkingDays: 0, history: [] },
    },
    {
      ...ok('Mekong Stud'), id: 'EQ-2026-1057', submittedAt: atTime(-3, '11:00'), departAt: daysFromToday(13),
      from: 'Trang trại Mekong (Cần Thơ, VN)', to: 'Trường đua Phnom Penh Royal Turf (Phnom Penh, KH)',
      routeShort: 'Cần Thơ → Phnom Penh (KH)', border: 'Tịnh Biên – Phnom Den', distance: '250 km', duration: '~6 giờ', horses: [H('Hậu Giang', 'Thoroughbred', 'Cái', 'VN-812011')], hold: '1 xe chuyên dụng 2 ngăn',
      services: items([['Cước vận chuyển đường bộ', 12_000_000], ['Phí kiểm dịch & thủ tục xuất cảnh', 4_500_000], ['Chăm sóc ngựa dọc đường', 1_300_000], ['Bảo hiểm vận chuyển', 1_200_000]]),
      status: 'processing', stage: 'routing', intakeAt: workdaysAgo(2), inspector: 'Nguyễn Thị Thu', coordinator: 'Lê Quang',
      task: { step: 'coordinator', assigneeId: 'DP-02', assignedAt: workdaysAgo(0), pausedWorkingDays: 0, history: [] },
    },

    // ===== Chuyển lên Manager =====
    {
      // Kiểm dịch báo cáo vấn đề không khắc phục được → Manager chọn phương án
      ...ok('Mekong Stud'), id: 'EQ-2026-1072', submittedAt: atTime(-2, '15:10'), departAt: daysFromToday(13),
      from: 'Trang trại Mekong (Cần Thơ, VN)', to: 'Trường đua Phnom Penh Royal Turf (Phnom Penh, KH)',
      routeShort: 'Cần Thơ → Phnom Penh (KH)', border: 'Tịnh Biên – Phnom Den', distance: '250 km', duration: '~6 giờ', horses: [cuuLong], customerNote: '', hold: '1 xe chuyên dụng 2 ngăn',
      services: items([['Cước vận chuyển đường bộ', 12_000_000], ['Phí kiểm dịch & thủ tục xuất cảnh', 4_500_000], ['Chăm sóc ngựa dọc đường', 1_300_000], ['Bảo hiểm vận chuyển', 1_200_000]]),
      status: 'processing', stage: 'inspecting', intakeAt: atTime(-2, '16:00'), inspector: 'Nguyễn Thị Thu', coordinator: 'Phạm Tâm',
      pending: { kind: 'issue', at: now - 2 * HOUR, report: { inspector: 'Nguyễn Thị Thu', horses: ['Cửu Long'], type: 'Xét nghiệm dương tính bệnh truyền nhiễm', disease: 'Cúm ngựa', curable: true, note: 'Cửu Long dương tính cúm ngựa. Cần điều trị khoảng 2–3 tuần rồi xét nghiệm lại.', evidence: ['Cửu Long · Kết quả xét nghiệm EIA & cúm ngựa'] } },
    },
    {
      // Khách chọn phương án D (kiểm tra lại) → Manager giao kiểm dịch viên khác
      ...ok('Trường đua Thiên Mã'), id: 'EQ-2026-1070', submittedAt: atTime(-3, '10:30'), departAt: daysFromToday(15),
      from: 'Trường đua Thiên Mã (Sóc Sơn, Hà Nội, VN)', to: 'Vientiane Turf Club (Viêng Chăn, LA)',
      routeShort: 'Hà Nội → Viêng Chăn (LA)', border: 'Cầu Treo – Nam Phao', distance: '730 km', duration: '2 ngày', horses: [H('Phi Vân', 'Anglo-Arab', 'Cái', 'VN-771120')], customerNote: '', hold: '1 xe chuyên dụng 2 ngăn',
      services: items([['Cước vận chuyển đường bộ', 21_000_000], ['Phí kiểm dịch & thủ tục xuất cảnh', 4_500_000], ['Chăm sóc ngựa dọc đường', 1_800_000], ['Bảo hiểm vận chuyển', 1_200_000]]),
      status: 'rechecking', recheckAt: now - 1 * HOUR, stage: 'inspecting', intakeAt: atTime(-3, '11:00'), inspector: 'Nguyễn Thị Thu', coordinator: 'Trần Minh',
      pending: {
        kind: 'recheck', at: now - 1 * HOUR,
        report: { inspector: 'Nguyễn Thị Thu', horses: ['Phi Vân'], type: 'Xét nghiệm dương tính bệnh truyền nhiễm', disease: 'EIA', curable: false, note: 'Phiếu xét nghiệm của Phi Vân ghi EIA dương tính.', evidence: ['Phi Vân · Kết quả xét nghiệm EIA & cúm ngựa'] },
        customerReason: 'Phiếu xét nghiệm cũ bị nhầm mẫu. Trại đã xét nghiệm lại tại phòng xét nghiệm khác, kết quả âm tính.', customerFiles: ['Xet_nghiem_lai_Phi_Van.pdf'],
      },
    },
    {
      // Khách không chọn phương án trong 48 giờ → Manager quyết định từ chối
      ...ok('CLB Ngựa Hà Thành'), id: 'EQ-2026-1075', submittedAt: atTime(-5, '08:20'), departAt: daysFromToday(12),
      from: 'Trường đua Thiên Mã (Sóc Sơn, Hà Nội, VN)', to: 'Trường đua Sông Hàn (Đà Nẵng, VN)',
      routeShort: 'Hà Nội → Đà Nẵng', border: null, distance: '770 km', duration: '2 ngày', horses: [thangLong, hoGuom], customerNote: '', hold: '1 xe chuyên dụng 2 ngăn',
      services: items([['Cước vận chuyển đường bộ', 18_000_000], ['Phí kiểm dịch nội địa', 1_800_000], ['Chăm sóc ngựa dọc đường', 2_600_000], ['Bảo hiểm vận chuyển', 1_800_000]]),
      status: 'choose_option', stage: 'inspecting', intakeAt: atTime(-5, '09:00'), inspector: 'Phạm Văn Hưng', coordinator: 'Phạm Tâm',
      offer: { issue: 'Đã yêu cầu bổ sung 2 lần, giấy tiêm phòng của Thăng Long vẫn thiếu mũi cúm ngựa bắt buộc.', affected: ['Thăng Long'], options: ['remove_horse', 'replace_horse', 'recheck', 'cancel'], sentAt: now - 50 * HOUR, requoteServices: items([['Cước vận chuyển đường bộ', 18_000_000], ['Phí kiểm dịch nội địa', 900_000], ['Chăm sóc ngựa dọc đường', 1_300_000], ['Bảo hiểm vận chuyển', 900_000]]) },
      pending: { kind: 'expired', at: now - 50 * HOUR + 48 * HOUR, report: { inspector: 'Phạm Văn Hưng', horses: ['Thăng Long'], type: 'Đã yêu cầu bổ sung nhưng giấy tờ vẫn không đạt', disease: '', curable: null, note: 'Đã yêu cầu bổ sung 2 lần, giấy tiêm phòng của Thăng Long vẫn thiếu mũi cúm ngựa bắt buộc.', evidence: ['Thăng Long · Giấy chứng nhận tiêm phòng'] } },
    },

    // ===== Đã từ chối =====
    {
      ...ok('HKD Du lịch Ngựa Bình Định'), id: 'EQ-2026-1043', submittedAt: atTime(-6, '08:50'), departAt: daysFromToday(6),
      from: 'Quy Nhơn (Bình Định, VN)', to: 'Trường đua Sông Hàn (Đà Nẵng, VN)',
      routeShort: 'Quy Nhơn → Đà Nẵng', border: null, distance: '320 km', duration: '~7 giờ',
      horses: [H('Ngựa kéo số 1', 'Ngựa nội (kéo xe)', 'Đực', 'VN-077001'), H('Ngựa kéo số 2', 'Ngựa nội (kéo xe)', 'Cái', 'VN-077002')], customerNote: '', hold: '1 xe chuyên dụng 2 ngăn',
      services: items([['Cước vận chuyển đường bộ', 11_000_000], ['Phí kiểm dịch nội địa', 1_500_000], ['Chăm sóc ngựa dọc đường', 900_000], ['Bảo hiểm vận chuyển', 800_000]]),
      status: 'rejected', rejectedStep: 0, rejectedAt: now - 6 * DAY, rejectType: 'Ngựa không thuộc diện vận chuyển',
      reason: 'Hệ thống chỉ nhận vận chuyển ngựa đua. 2 ngựa khai báo là ngựa kéo xe du lịch.',
    },
    {
      ...ok('HKD Du lịch Ngựa Quy Nhơn'), id: 'EQ-2026-1069', submittedAt: atTime(-7, '09:40'), departAt: daysFromToday(8),
      from: 'Quy Nhơn (Bình Định, VN)', to: 'Trường đua Sông Hàn (Đà Nẵng, VN)',
      routeShort: 'Quy Nhơn → Đà Nẵng', border: null, distance: '320 km', duration: '~7 giờ',
      horses: [H('Ngựa kéo số 3', 'Thoroughbred (khách khai)', 'Đực', 'VN-077003')], customerNote: '', hold: '1 xe chuyên dụng 2 ngăn',
      services: items([['Cước vận chuyển đường bộ', 6_500_000], ['Phí kiểm dịch nội địa', 900_000], ['Chăm sóc ngựa dọc đường', 500_000], ['Bảo hiểm vận chuyển', 500_000]]),
      status: 'rejected', rejectedStep: 1, inspector: 'Phạm Văn Hưng', rejectedAt: now - 3 * DAY,
      rejectType: 'Khách không chọn phương án trong 48 giờ', reason: 'Đã gửi phương án thay ngựa / kiểm tra lại / hủy đơn, khách không phản hồi.',
      report: { inspector: 'Phạm Văn Hưng', horses: ['Ngựa kéo số 3'], type: 'Đã yêu cầu bổ sung nhưng giấy tờ vẫn không đạt', note: 'Khách không cung cấp được giấy tờ chứng minh là ngựa đua; hộ chiếu ghi ngựa kéo xe du lịch.', evidence: ['Ngựa kéo số 3 · Hộ chiếu ngựa / Microchip'] },
    },

    // ===== Trang Phê duyệt: chờ duyệt =====
    {
      ...ok('Trường đua Thiên Mã'), id: 'EQ-2026-1041', submittedAt: atTime(-3, '09:00'), departAt: daysFromToday(14),
      from: 'Trường đua Thiên Mã (Sóc Sơn, Hà Nội, VN)', to: 'Vientiane Turf Club (Viêng Chăn, LA)',
      routeShort: 'Hà Nội → Viêng Chăn (LA)', border: 'Cầu Treo – Nam Phao', distance: '730 km', duration: '2 ngày',
      horses: [H('Hắc Long', 'Thoroughbred', 'Đực', 'VN-771020'), H('Thiên Lý', 'Thoroughbred', 'Cái', 'VN-771034'), kimO],
      stops: ['Trường đua Thiên Mã (Hà Nội) — nhận ngựa', 'Trạm nghỉ Vinh (Nghệ An) — nghỉ đêm', 'Cửa khẩu Cầu Treo (Hà Tĩnh) — thông quan', 'Cửa khẩu Nam Phao (Bolikhamxay) — kiểm tra thú y', 'Vientiane Turf Club — giao ngựa'],
      services: withFreight(std('4 ngăn', '730 km', ['Kiểm dịch & thủ tục xuất cảnh', 'Trọn gói: xét nghiệm, chứng nhận, thông quan Cầu Treo – Nam Phao', 9_500_000], [CARE_FULL, 3_500_000], 3_000_000), 30_000_000),
      status: 'processing', stage: 'approval', inspector: 'Nguyễn Thị Thu', coordinator: 'Lê Quang', vehicle: 'Xe chuyên dụng 4 ngăn',
      review: { inspectNote: 'Đã đối chiếu microchip 3/3 con. Giấy phép nhập khẩu phía Lào hợp lệ.', vehicle: '29H-456.78 (xe chuyên dụng 4 ngăn)', driver: 'Trần Quốc Bảo', grooms: 'Đỗ Văn Nam' },
    },
    {
      ...ok('Trang trại Sông Hàn'), id: 'EQ-2026-1040', submittedAt: atTime(-4, '09:00'), departAt: daysFromToday(11),
      from: 'Trường đua Sông Hàn (Đà Nẵng, VN)', to: 'Trường đua Savannakhet (Savannakhet, LA)',
      routeShort: 'Đà Nẵng → Savannakhet (LA)', border: 'Lao Bảo – Densavanh', distance: '500 km', duration: '~11 giờ', horses: [H('Phong Vân', 'Arabian', 'Đực', 'VN-640552')],
      stops: ['Trường đua Sông Hàn (Đà Nẵng) — nhận ngựa', 'Cửa khẩu Lao Bảo (Quảng Trị) — thông quan', 'Cửa khẩu Densavanh (Savannakhet) — kiểm tra thú y', 'Trường đua Savannakhet — giao ngựa'],
      services: withFreight(std('2 ngăn', '500 km', ['Kiểm dịch & thủ tục xuất cảnh', 'Trọn gói: xét nghiệm, chứng nhận, thông quan Lao Bảo – Densavanh', 4_800_000], [CARE_FULL, 1_800_000], 1_200_000), 14_000_000),
      status: 'processing', stage: 'approval', inspector: 'Phạm Văn Hưng', coordinator: 'Phạm Tâm', vehicle: 'Xe chuyên dụng 2 ngăn',
      review: { inspectNote: 'Giấy tờ hợp lệ.', vehicle: '43C-222.11 (xe chuyên dụng 2 ngăn)', driver: 'Lê Minh Tuấn', grooms: 'Huỳnh Thị Mai' },
    },
    {
      ...ok('CLB Ngựa Phú Thọ'), id: 'EQ-2026-1039', submittedAt: atTime(-5, '09:00'), departAt: daysFromToday(13),
      from: 'Trường đua Phú Thọ (TP.HCM, VN)', to: 'Trường đua Sông Hàn (Đà Nẵng, VN)',
      routeShort: 'TP.HCM → Đà Nẵng', border: null, distance: '960 km', duration: '2 ngày', horses: [xichTho, oVan, tuyetSon, loiDien],
      stops: ['Trường đua Phú Thọ (TP.HCM) — nhận ngựa', 'Trạm nghỉ Tuy Hòa (Phú Yên) — nghỉ đêm', 'Trường đua Sông Hàn (Đà Nẵng) — giao ngựa'],
      services: withFreight(std('4 ngăn', '960 km', ['Kiểm dịch vận chuyển nội địa', 'Cấp giấy chứng nhận kiểm dịch cho 4 ngựa', 3_000_000], [CARE_FULL, 5_000_000], 3_000_000), 33_000_000),
      status: 'processing', stage: 'approval', inspector: 'Nguyễn Thị Thu', coordinator: 'Trần Minh', vehicle: 'Xe chuyên dụng 4 ngăn',
      review: { inspectNote: 'Vận chuyển nội địa, giấy tờ đầy đủ.', vehicle: '51C-888.99 (xe chuyên dụng 4 ngăn)', driver: 'Phạm Đức Anh', grooms: 'Võ Thị Lan, Đỗ Văn Nam' },
    },
    {
      ...ok('Đại Nam Racing'), id: 'EQ-2026-1038', submittedAt: atTime(-6, '09:00'), departAt: daysFromToday(9),
      from: 'Trường đua Đại Nam (Bình Dương, VN)', to: 'Trường đua Phú Thọ (TP.HCM, VN)',
      routeShort: 'Bình Dương → TP.HCM', border: null, distance: '40 km', duration: '~1.5 giờ', horses: [hoaTien, nganHa],
      stops: ['Trường đua Đại Nam (Bình Dương) — nhận ngựa', 'Trường đua Phú Thọ (TP.HCM) — giao ngựa'],
      services: withFreight(std('2 ngăn', '40 km', ['Kiểm dịch vận chuyển nội địa', 'Cấp giấy chứng nhận kiểm dịch cho 2 ngựa', 1_000_000], [CARE_FULL, 300_000], 400_000), 3_500_000),
      status: 'processing', stage: 'approval', inspector: 'Phạm Văn Hưng', coordinator: 'Phạm Tâm', vehicle: 'Xe chuyên dụng 2 ngăn',
      review: { inspectNote: 'Giấy tờ hợp lệ.', vehicle: '61C-345.67 (xe chuyên dụng 2 ngăn)', driver: 'Võ Thanh Sơn', grooms: 'Huỳnh Thị Mai' },
    },
    {
      ...ok('Angkor Equestrian'), id: 'EQ-2026-1037', submittedAt: atTime(-6, '14:00'), departAt: daysFromToday(15),
      from: 'Trường đua Angkor (Siem Reap, KH)', to: 'Trường đua Phú Thọ (TP.HCM, VN)',
      routeShort: 'Siem Reap (KH) → TP.HCM', border: 'Bavet – Mộc Bài', distance: '550 km', duration: '2 ngày',
      horses: [H('Apsara', 'Arabian', 'Cái', 'KH-118204'), H('Bayon', 'Thoroughbred', 'Đực', 'KH-118219')],
      stops: ['Trường đua Angkor (Siem Reap) — nhận ngựa', 'Cửa khẩu Bavet (Svay Rieng) — thông quan', 'Khu cách ly Mộc Bài (Tây Ninh) — cách ly 2 ngày', 'Trường đua Phú Thọ (TP.HCM) — giao ngựa'],
      services: withFreight(std('4 ngăn', '550 km', ['Kiểm dịch & thủ tục nhập cảnh', 'Trọn gói: xét nghiệm, chứng nhận, thông quan Bavet – Mộc Bài', 8_000_000], [CARE_FULL, 2_500_000], 2_000_000), 18_000_000),
      status: 'processing', stage: 'approval', inspector: 'Nguyễn Thị Thu', coordinator: 'Lê Quang', vehicle: 'Xe chuyên dụng 4 ngăn',
      review: { inspectNote: 'Ngựa nhập khẩu vào Việt Nam phải cách ly 2 ngày tại cửa khẩu Mộc Bài theo yêu cầu thú y. Đã cộng chi phí cách ly.', vehicle: '51C-123.45 (xe chuyên dụng 4 ngăn)', driver: 'Nguyễn Văn Hùng', grooms: 'Đỗ Văn Nam' },
    },
    {
      ...ok('Trang trại Ba Vì'), id: 'EQ-2026-1036', submittedAt: atTime(-8, '09:00'), departAt: daysFromToday(8),
      from: 'Trang trại Ba Vì (Hà Nội, VN)', to: 'Trường đua Thiên Mã (Sóc Sơn, Hà Nội, VN)',
      routeShort: 'Ba Vì → Sóc Sơn (Hà Nội)', border: null, distance: '80 km', duration: '~2 giờ',
      horses: [tanVien, sonTinh, myNuong],
      stops: ['Trang trại Ba Vì — nhận ngựa', 'Trường đua Thiên Mã (Sóc Sơn) — giao ngựa'],
      services: withFreight(std('4 ngăn', '80 km', ['Kiểm dịch vận chuyển nội địa', 'Cấp giấy chứng nhận kiểm dịch cho 3 ngựa', 1_200_000], [CARE_FULL, 600_000], 700_000), 5_000_000),
      status: 'processing', stage: 'approval', inspector: 'Phạm Văn Hưng', coordinator: 'Trần Minh', vehicle: 'Xe chuyên dụng 4 ngăn',
      review: { inspectNote: 'Giấy tờ hợp lệ.', vehicle: '29H-456.78 (xe chuyên dụng 4 ngăn)', driver: 'Trần Quốc Bảo', grooms: 'Đỗ Văn Nam' },
    },

    // ===== Trang Phê duyệt: đã duyệt, chờ thanh toán =====
    {
      ...ok('Vientiane Turf Club'), id: 'EQ-2026-1035', submittedAt: atTime(-10, '09:00'), departAt: daysFromToday(6),
      from: 'Vientiane Turf Club (Viêng Chăn, LA)', to: 'Trường đua Thiên Mã (Sóc Sơn, Hà Nội, VN)',
      routeShort: 'Viêng Chăn (LA) → Hà Nội', border: 'Nam Phao – Cầu Treo', distance: '730 km', duration: '2 ngày',
      horses: [H('Mekong Star', 'Thoroughbred', 'Đực', 'LA-207731'), H('Champa', 'Arabian', 'Cái', 'LA-207748')],
      stops: ['Vientiane Turf Club — nhận ngựa', 'Cửa khẩu Nam Phao (Bolikhamxay) — thông quan', 'Cửa khẩu Cầu Treo (Hà Tĩnh) — kiểm tra thú y', 'Trạm nghỉ Vinh (Nghệ An) — nghỉ đêm', 'Trường đua Thiên Mã (Hà Nội) — giao ngựa'],
      services: withFreight(std('4 ngăn', '730 km', ['Kiểm dịch & thủ tục nhập cảnh', 'Trọn gói: xét nghiệm, chứng nhận, thông quan Nam Phao – Cầu Treo', 8_000_000], [CARE_FULL, 3_500_000], 2_500_000), 26_000_000),
      status: 'awaiting_payment', approvedAt: atTime(-1, '10:30'), inspector: 'Nguyễn Thị Thu', coordinator: 'Lê Quang', vehicle: 'Xe chuyên dụng 4 ngăn',
      review: { inspectNote: 'Giấy tờ hợp lệ.', vehicle: '29H-456.78 (xe chuyên dụng 4 ngăn)', driver: 'Trần Quốc Bảo', grooms: 'Đỗ Văn Nam' },
    },
    {
      ...ok('Mekong Stud'), id: 'EQ-2026-1034', submittedAt: atTime(-12, '09:00'), departAt: daysFromToday(4),
      from: 'Trang trại Mekong (Cần Thơ, VN)', to: 'Trường đua Phnom Penh Royal Turf (Phnom Penh, KH)',
      routeShort: 'Cần Thơ → Phnom Penh (KH)', border: 'Tịnh Biên – Phnom Den', distance: '250 km', duration: '~6 giờ', horses: [cuuLong],
      stops: ['Trang trại Mekong (Cần Thơ) — nhận ngựa', 'Cửa khẩu Tịnh Biên (An Giang) — thông quan', 'Cửa khẩu Phnom Den (Takeo) — kiểm tra thú y', 'Trường đua Phnom Penh Royal Turf — giao ngựa'],
      services: withFreight(std('2 ngăn', '250 km', ['Kiểm dịch & thủ tục xuất cảnh', 'Trọn gói: xét nghiệm, chứng nhận, thông quan Tịnh Biên – Phnom Den', 4_500_000], [CARE_FULL, 1_300_000], 1_200_000), 12_000_000),
      status: 'awaiting_payment', approvedAt: atTime(0, '08:00'), inspector: 'Phạm Văn Hưng', coordinator: 'Phạm Tâm', vehicle: 'Xe chuyên dụng 2 ngăn',
      review: { inspectNote: 'Giấy tờ hợp lệ.', vehicle: '65C-101.22 (xe chuyên dụng 2 ngăn)', driver: 'Đặng Hoài Phúc', grooms: 'Huỳnh Thị Mai' },
    },

    // ===== Trang Phê duyệt: từ chối ở bước duyệt =====
    {
      ...ok('CLB Ngựa Sài Gòn'), id: 'EQ-2026-1033', submittedAt: atTime(-14, '09:00'), departAt: daysFromToday(1),
      from: 'Trường đua Phú Thọ (TP.HCM, VN)', to: 'Trường đua Savannakhet (Savannakhet, LA)',
      routeShort: 'TP.HCM → Savannakhet (LA)', border: 'Lao Bảo – Densavanh', distance: '1,100 km', duration: '3 ngày',
      horses: [H('Sài Gòn Express', 'Thoroughbred', 'Đực', 'VN-290071'), H('Bến Thành', 'Arabian', 'Cái', 'VN-290085')],
      stops: ['Trường đua Phú Thọ (TP.HCM) — nhận ngựa', 'Trạm nghỉ Quy Nhơn (Bình Định) — nghỉ đêm', 'Cửa khẩu Lao Bảo (Quảng Trị) — thông quan', 'Cửa khẩu Densavanh (Savannakhet) — kiểm tra thú y', 'Trường đua Savannakhet — giao ngựa'],
      services: withFreight(std('4 ngăn', '1,100 km', ['Kiểm dịch & thủ tục xuất cảnh', 'Trọn gói: xét nghiệm, chứng nhận, thông quan Lao Bảo – Densavanh', 9_000_000], [CARE_FULL, 4_000_000], 3_000_000), 36_000_000),
      status: 'rejected', rejectedStep: 2, rejectedAt: atTime(-1, '09:00'), inspector: 'Nguyễn Thị Thu', coordinator: 'Trần Minh', vehicle: 'Xe chuyên dụng 4 ngăn',
      rejectType: 'Tuyến đường hoặc cửa khẩu không khả thi',
      reason: 'Cửa khẩu Densavanh tạm dừng thông quan động vật sống do dịch cúm ngựa tại Savannakhet.',
      review: { inspectNote: 'Giấy tờ hợp lệ.', vehicle: '51C-888.99 (xe chuyên dụng 4 ngăn)', driver: 'Phạm Đức Anh', grooms: 'Võ Thị Lan' },
    },

    // ===== Đang vận chuyển (gốc: Fleet And Route/ops_data.js — chuyến TR-9042, TR-9021; đánh số đơn mới vì mã cũ trùng) =====
    {
      ...ok('CLB Ngựa Phú Thọ'), id: 'EQ-2026-1079', submittedAt: atTime(-20, '09:00'), departAt: daysFromToday(-1),
      from: 'Trường đua Phú Thọ (TP.HCM, VN)', to: 'Trường đua Sông Hàn (Đà Nẵng, VN)',
      routeShort: 'TP.HCM → Đà Nẵng', border: null, distance: '960 km', duration: '2 ngày', horses: [xichTho, oVan, tuyetSon, loiDien],
      stops: ['Trường đua Phú Thọ (TP.HCM) — nhận ngựa', 'Trạm nghỉ Tuy Hòa (Phú Yên) — nghỉ đêm', 'Trường đua Sông Hàn (Đà Nẵng) — giao ngựa'],
      services: withFreight(std('4 ngăn', '960 km', ['Kiểm dịch vận chuyển nội địa', 'Cấp giấy chứng nhận kiểm dịch cho 4 ngựa', 3_000_000], [CARE_FULL, 5_000_000], 3_000_000), 33_000_000),
      status: 'in_transit', approvedAt: atTime(-10, '09:00'), paidAt: atTime(-9, '10:00'), inspector: 'Nguyễn Thị Thu', coordinator: 'Trần Minh', vehicle: 'Xe chuyên dụng 4 ngăn',
      review: { inspectNote: 'Vận chuyển nội địa, giấy tờ đầy đủ.', vehicle: '30A-55678 (xe chuyên dụng 4 ngăn)', driver: 'Lê Văn C', grooms: 'Võ Thị Lan' },

      trip: {
        plate: '30A-55678', eta: atTime(0, '17:00'), updatedAt: atTime(0, '07:10'),
        contacts: [['Tài xế', 'Lê Văn C', '0901 555 666'], ['NV chăm sóc', 'Võ Thị Lan', '0908 333 444']],
        checkpoints: [
          { label: 'Nhận ngựa lên xe, khởi hành', place: 'Trường đua Phú Thọ (TP.HCM)', time: atTime(-1, '05:30'), state: 'done' },
          { label: 'Nghỉ đêm', place: 'Trạm nghỉ Tuy Hòa (Phú Yên)', time: atTime(-1, '18:40'), state: 'done' },
          { label: 'Rời trạm nghỉ, đi tiếp', place: 'Trạm nghỉ Tuy Hòa (Phú Yên)', time: atTime(0, '07:10'), state: 'current' },
          { label: 'Giao ngựa', place: 'Trường đua Sông Hàn (Đà Nẵng)', time: atTime(0, '17:00'), state: 'next' },
        ],
        health: [
          { time: atTime(0, '06:30'), temp: '38.7°C', heart: '46 bpm', note: 'Tuyết Sơn có dấu hiệu stress nhiệt, đã cho uống điện giải', horse: 'Tuyết Sơn', status: 'Mệt nhẹ', by: 'Võ Thị Lan' },
          { time: atTime(-1, '19:00'), temp: '37.8°C', heart: '38 bpm', note: 'Cả 4 con ăn uống bình thường tại trạm nghỉ', status: 'Bình thường', by: 'Võ Thị Lan' },
          { time: atTime(-1, '05:00'), temp: '37.6°C', heart: '36 bpm', note: 'Kiểm tra trước khi lên xe: đạt', status: 'Bình thường', by: 'Võ Thị Lan' },
        ],
      },
    },
    {
      ...ok('CLB Ngựa Đức Hòa'), id: 'EQ-2026-1080', submittedAt: atTime(-18, '09:00'), departAt: daysFromToday(0),
      from: 'Trang trại CLB Đức Hòa (Hà Nội, VN)', to: 'Vientiane Turf Club (Viêng Chăn, LA)',
      routeShort: 'Hà Nội → Viêng Chăn (LA)', border: 'Cầu Treo – Nam Phao', distance: '730 km', duration: '2 ngày',
      horses: [H('Đức Hòa 1', 'Thoroughbred', 'Đực', 'VN-440101'), H('Đức Hòa 2', 'Arabian', 'Cái', 'VN-440102')],
      stops: ['Hà Nội (Trang trại CLB) — nhận ngựa', 'Cửa khẩu Cầu Treo (Hà Tĩnh) — thông quan', 'Cửa khẩu Nam Phao (Bolikhamxay) — kiểm tra thú y', 'Vientiane Turf Club — giao ngựa'],
      services: withFreight(std('2 ngăn', '730 km', ['Kiểm dịch & thủ tục xuất cảnh', 'Trọn gói: xét nghiệm, chứng nhận, thông quan Cầu Treo – Nam Phao', 7_600_000], [CARE_FULL, 2_600_000], 1_800_000), 21_000_000),
      status: 'in_transit', approvedAt: atTime(-8, '09:00'), paidAt: atTime(-7, '10:00'), inspector: 'Phạm Văn Hưng', coordinator: 'Phạm Tâm', vehicle: 'Xe chuyên dụng 2 ngăn',
      review: { inspectNote: 'Giấy tờ hợp lệ.', vehicle: '29H-12345 (xe chuyên dụng 2 ngăn)', driver: 'Nguyễn Văn A', grooms: 'Lê Thị C' },

      trip: {
        plate: '29H-12345', eta: atTime(1, '16:00'), updatedAt: atTime(0, '06:00'),
        contacts: [['Tài xế', 'Nguyễn Văn A', '0901 111 222'], ['NV chăm sóc', 'Lê Thị C', '0902 111 222']],
        checkpoints: [
          { label: 'Nhận ngựa lên xe, khởi hành', place: 'Hà Nội (Trang trại CLB)', time: atTime(0, '06:00'), state: 'done' },
          { label: 'Thông quan', place: 'Cửa khẩu Cầu Treo (Hà Tĩnh)', time: atTime(0, '15:00'), state: 'current' },
          { label: 'Kiểm tra thú y', place: 'Cửa khẩu Nam Phao (Bolikhamxay)', time: atTime(1, '08:00'), state: 'next' },
          { label: 'Giao ngựa', place: 'Vientiane Turf Club', time: atTime(1, '16:00'), state: 'next' },
        ],
        health: [
          { time: atTime(0, '05:30'), temp: '37.7°C', heart: '37 bpm', note: 'Kiểm tra trước khi lên xe: đạt', status: 'Bình thường', by: 'Lê Thị C' },
        ],
      },
    },

    // ===== Trang Phê duyệt: đã thanh toán, kiểm dịch viên chuẩn bị giấy tờ =====
    {
      ...ok('Trang trại Tây Ninh Stud'), id: 'EQ-2026-1058', submittedAt: atTime(-14, '09:00'), departAt: daysFromToday(6),
      from: 'Trang trại Tây Ninh Stud (Tây Ninh, VN)', to: 'Trường đua Phnom Penh Royal Turf (Phnom Penh, KH)',
      routeShort: 'Tây Ninh → Phnom Penh (KH)', border: 'Mộc Bài – Bavet', distance: '230 km', duration: '~6 giờ', horses: [baDen, vamCo],
      stops: ['Trang trại Tây Ninh Stud — nhận ngựa', 'Cửa khẩu Mộc Bài (Tây Ninh) — thông quan', 'Cửa khẩu Bavet (Svay Rieng) — kiểm tra thú y', 'Trường đua Phnom Penh Royal Turf — giao ngựa'],
      services: [
        ['Vận chuyển đường bộ', 'Xe chuyên dụng 2 ngăn · khoang tiêu chuẩn · 230 km', 12_500_000],
        ['Kiểm dịch & thủ tục xuất cảnh', 'Trọn gói cho 2 ngựa: xét nghiệm, chứng nhận, thông quan Mộc Bài – Bavet', 7_000_000],
        ['Chăm sóc dọc đường', 'NV chăm sóc đi kèm · nước điện giải', 1_600_000],
        ['Bảo hiểm vận chuyển', 'Gói cơ bản', 1_600_000],
      ],
      status: 'paid', approvedAt: atTime(-5, '09:00'), paidAt: atTime(-4, '10:00'), inspector: 'Phạm Văn Hưng', coordinator: 'Phạm Tâm', vehicle: 'Xe chuyên dụng 2 ngăn',
      review: { inspectNote: 'Giấy tờ hợp lệ.', vehicle: '70C-045.18 (xe chuyên dụng 2 ngăn)', driver: 'Lê Văn Tài', grooms: 'Huỳnh Thị Mai' },
      papers: {
        originals: {
          'Bà Đen': { passport: atTime(-3, '10:00'), vaccine: atTime(-3, '10:00'), lab: atTime(-3, '10:00'), import_permit: atTime(-3, '10:00'), ownership: atTime(-3, '10:00') },
          'Vàm Cỏ': { passport: atTime(-3, '10:00'), vaccine: atTime(-3, '10:00'), lab: null, import_permit: null, ownership: null },
        },
        procedures: { quarantine_border: { number: 'KD-XK-2026/0412', agency: 'Cơ quan Thú y vùng VI', issuedAt: daysFromToday(-1), validUntil: daysFromToday(12), file: 'GCN_kiem_dich_XK_EQ-2026-1058.pdf' } },
      },
    },
    {
      ...ok('Hoàng Gia Stud'), id: 'EQ-2026-1050', submittedAt: atTime(-19, '09:00'), departAt: daysFromToday(1),
      from: 'Trường đua Thiên Mã (Sóc Sơn, Hà Nội, VN)', to: 'Trường đua Luang Prabang (Luang Prabang, LA)',
      routeShort: 'Hà Nội → Luang Prabang (LA)', border: 'Tây Trang – Sop Hun', distance: '620 km', duration: '2 ngày', horses: [ngocHoang],
      stops: ['Trường đua Thiên Mã (Sóc Sơn) — nhận ngựa', 'Trạm nghỉ Điện Biên — nghỉ đêm', 'Cửa khẩu Tây Trang (Điện Biên) — thông quan', 'Cửa khẩu Sop Hun (Phongsaly) — kiểm tra thú y', 'Trường đua Luang Prabang — giao ngựa'],
      services: withFreight(std('2 ngăn', '620 km', ['Kiểm dịch & thủ tục xuất cảnh', 'Trọn gói: xét nghiệm, chứng nhận, thông quan Tây Trang – Sop Hun', 4_200_000], [CARE_FULL, 1_800_000], 1_100_000), 19_000_000),
      status: 'paid', approvedAt: atTime(-10, '09:00'), paidAt: atTime(-9, '14:00'), inspector: 'Phạm Văn Hưng', coordinator: 'Phạm Tâm', vehicle: 'Xe chuyên dụng 2 ngăn',
      review: { inspectNote: 'Giấy tờ hợp lệ.', vehicle: '29C-310.77 (xe chuyên dụng 2 ngăn)', driver: 'Trịnh Văn Long', grooms: 'Đỗ Thị Hạnh' },
      papers: {
        originals: { 'Ngọc Hoàng': { passport: atTime(-6, '09:30'), vaccine: atTime(-6, '09:30'), lab: atTime(-6, '09:30'), import_permit: atTime(-6, '09:30'), ownership: atTime(-6, '09:30') } },
        procedures: {
          quarantine_border: { number: 'KD-XK-2026/0397', agency: 'Cơ quan Thú y vùng I', issuedAt: daysFromToday(-3), validUntil: daysFromToday(9), file: 'GCN_kiem_dich_XK_EQ-2026-1050.pdf' },
          customs: { number: '305112402267', agency: 'Chi cục Hải quan cửa khẩu Tây Trang', issuedAt: daysFromToday(-2), file: 'To_khai_HQ_EQ-2026-1050.pdf' },
        },
        handedAt: atTime(-1, '10:00'),
      },
    },
    // ===== Kiểm dịch viên Phạm Văn Hưng: xác minh hồ sơ (gốc: Specialist/kiem_dich.js) =====
    {
      ...ok('Trang trại Tây Ninh Stud'), id: 'EQ-2026-1065', submittedAt: atTime(-1, '10:00'), departAt: daysFromToday(12),
      from: 'Trang trại Tây Ninh Stud (Tây Ninh, VN)', to: 'Trường đua Phnom Penh Royal Turf (Phnom Penh, KH)',
      routeShort: 'Tây Ninh → Phnom Penh (KH)', border: 'Mộc Bài – Bavet', distance: '230 km', duration: '~6 giờ', horses: [baDen, vamCo],
      customerNote: 'Ngựa Vàm Cỏ nhạy cảm tiếng ồn.', hold: '1 xe chuyên dụng 2 ngăn',
      services: withFreight(std('2 ngăn', '230 km', ['Kiểm dịch & thủ tục xuất cảnh', 'Trọn gói cho 2 ngựa: xét nghiệm, chứng nhận, thông quan Mộc Bài – Bavet', 7_000_000], [CARE_FULL, 1_600_000], 1_600_000), 12_500_000),
      status: 'processing', stage: 'inspecting', intakeAt: workdaysAgo(0, 9), inspector: 'Phạm Văn Hưng', coordinator: 'Phạm Tâm',
      task: { step: 'inspector', assigneeId: 'KD-01', assignedAt: workdaysAgo(0, 9), pausedWorkingDays: 0, history: [] },
    },
    {
      ...ok('CLB Ngựa Phú Thọ'), id: 'EQ-2026-1066', submittedAt: atTime(-2, '11:00'), departAt: daysFromToday(9),
      from: 'Trường đua Phú Thọ (TP.HCM, VN)', to: 'Trường đua Angkor (Siem Reap, KH)',
      routeShort: 'TP.HCM → Siem Reap (KH)', border: 'Mộc Bài – Bavet', distance: '550 km', duration: '2 ngày', horses: [xichTho], hold: '1 xe chuyên dụng 2 ngăn',
      services: withFreight(std('2 ngăn', '550 km', ['Kiểm dịch & thủ tục xuất cảnh', 'Trọn gói: xét nghiệm, chứng nhận, thông quan Mộc Bài – Bavet', 4_800_000], [CARE_FULL, 1_200_000], 1_000_000), 15_000_000),
      status: 'processing', stage: 'inspecting', intakeAt: workdaysAgo(1, 14), inspector: 'Phạm Văn Hưng', coordinator: 'Trần Minh',
      task: { step: 'inspector', assigneeId: 'KD-01', assignedAt: workdaysAgo(1, 14), pausedWorkingDays: 0, history: [] },
    },
    {
      ...ok('Trang trại Ba Vì'), id: 'EQ-2026-1067', submittedAt: atTime(-2, '08:30'), departAt: daysFromToday(11),
      from: 'Trang trại Ba Vì (Hà Nội, VN)', to: 'Trường đua Thiên Mã (Sóc Sơn, Hà Nội, VN)',
      routeShort: 'Ba Vì → Sóc Sơn (Hà Nội)', border: null, distance: '80 km', duration: '~2 giờ', horses: [tanVien, sonTinh, myNuong], hold: '1 xe chuyên dụng 4 ngăn',
      services: withFreight(std('4 ngăn', '80 km', ['Kiểm dịch vận chuyển nội địa', 'Cấp giấy chứng nhận kiểm dịch cho 3 ngựa', 1_200_000], [CARE_FULL, 600_000], 700_000), 5_000_000),
      status: 'processing', stage: 'inspecting', intakeAt: workdaysAgo(1, 10), inspector: 'Phạm Văn Hưng', coordinator: 'Trần Minh',
      task: { step: 'inspector', assigneeId: 'KD-01', assignedAt: workdaysAgo(1, 10), pausedWorkingDays: 0, history: [] },
      verification: { docs: {
        'Tản Viên': { passport: valid, vaccine: valid, ownership: valid },
        'Sơn Tinh': { passport: { decision: 'invalid', reason: 'Microchip trên hộ chiếu là VN-330125, không trùng microchip khai báo VN-330152' } },
      } },
    },
    {
      ...ok('Savan Horse Club'), id: 'EQ-2026-1068', submittedAt: atTime(-4, '09:00'), departAt: daysFromToday(14),
      from: 'Trường đua Savannakhet (Savannakhet, LA)', to: 'Trường đua Sông Hàn (Đà Nẵng, VN)',
      routeShort: 'Savannakhet (LA) → Đà Nẵng', border: 'Densavanh – Lao Bảo', distance: '500 km', duration: '~11 giờ', horses: [mekongWind], hold: '1 xe chuyên dụng 2 ngăn',
      services: withFreight(std('2 ngăn', '500 km', ['Kiểm dịch & thủ tục nhập cảnh', 'Trọn gói: xét nghiệm, chứng nhận, thông quan Densavanh – Lao Bảo', 4_800_000], [CARE_FULL, 1_800_000], 1_200_000), 14_000_000),
      status: 'processing', stage: 'inspecting', intakeAt: workdaysAgo(2, 9), inspector: 'Phạm Văn Hưng', coordinator: 'Phạm Tâm', waitingCustomer: true,
      task: { step: 'inspector', assigneeId: 'KD-01', assignedAt: workdaysAgo(2, 9), pausedSince: workdaysAgo(1, 15), pausedWorkingDays: 0, history: [] },
      verification: {
        docs: { 'Mekong Wind': { passport: valid, vaccine: { decision: 'invalid', reason: 'Ảnh chụp mờ, không đọc được ngày tiêm' }, lab: valid, import_permit: valid, ownership: valid } },
        requestedAt: workdaysAgo(1, 15), customerMessage: 'Vui lòng chụp lại rõ trang có dấu và ngày tiêm.',
      },
    },
    {
      // Hồ sơ hợp lệ, đã chuyển Điều phối → trang Đánh giá khả thi
      ...ok('Đại Nam Racing'), id: 'EQ-2026-1062', submittedAt: atTime(-5, '10:00'), departAt: daysFromToday(10),
      from: 'Trường đua Đại Nam (Bình Dương, VN)', to: 'Trang trại Mekong (Cần Thơ, VN)',
      routeShort: 'Bình Dương → Cần Thơ', border: null, distance: '190 km', duration: '~4.5 giờ', horses: [hoaTien], hold: '1 xe chuyên dụng 2 ngăn',
      services: withFreight(std('2 ngăn', '190 km', ['Kiểm dịch vận chuyển nội địa', 'Cấp giấy chứng nhận kiểm dịch cho 1 ngựa', 900_000], [CARE_FULL, 400_000], 500_000), 6_000_000),
      status: 'processing', stage: 'routing', intakeAt: workdaysAgo(3, 9), inspector: 'Phạm Văn Hưng', coordinator: 'Trần Minh',
      verification: { docs: { 'Hỏa Tiễn': { passport: valid, vaccine: valid, ownership: valid } }, result: 'passed', closedAt: workdaysAgo(1, 16) },
      task: { step: 'coordinator', assigneeId: 'DP-01', assignedAt: workdaysAgo(1, 16), pausedWorkingDays: 0, history: [] },
    },
    {
      // Khách chọn kiểm tra lại (phương án D), Manager giao cho Hưng xác minh lại từ đầu
      ...ok('Hoàng Gia Stud'), id: 'EQ-2026-1073', submittedAt: atTime(-6, '09:00'), departAt: daysFromToday(13),
      from: 'Trường đua Thiên Mã (Sóc Sơn, Hà Nội, VN)', to: 'Trường đua Luang Prabang (Luang Prabang, LA)',
      routeShort: 'Hà Nội → Luang Prabang (LA)', border: 'Tây Trang – Sop Hun', distance: '620 km', duration: '2 ngày', horses: [ngocHoang], hold: '1 xe chuyên dụng 2 ngăn',
      services: withFreight(std('2 ngăn', '620 km', ['Kiểm dịch & thủ tục xuất cảnh', 'Trọn gói: xét nghiệm, chứng nhận, thông quan Tây Trang – Sop Hun', 4_200_000], [CARE_FULL, 1_800_000], 1_100_000), 19_000_000),
      status: 'rechecking', recheckAt: atTime(0, '09:30'), stage: 'inspecting', intakeAt: workdaysAgo(4, 9), inspector: 'Phạm Văn Hưng', coordinator: 'Trần Minh', rechecked: true,
      task: { step: 'inspector', assigneeId: 'KD-01', assignedAt: workdaysAgo(0, 10), pausedWorkingDays: 0, history: [] },
      report: { inspector: 'Nguyễn Thị Thu', horses: ['Ngọc Hoàng'], type: 'Xét nghiệm dương tính bệnh truyền nhiễm', disease: 'EIA', curable: false, note: 'Phiếu xét nghiệm ghi EIA dương tính.', evidence: ['Ngọc Hoàng · Kết quả xét nghiệm EIA & cúm ngựa'] },
      recheckRequest: { customerReason: 'Phiếu xét nghiệm cũ bị nhầm mẫu. Trại đã xét nghiệm lại tại phòng xét nghiệm khác, kết quả âm tính (đính kèm).', customerFiles: ['Xet_nghiem_lai_Ngoc_Hoang.pdf'] },
    },

    // ===== Kiểm dịch viên Phạm Văn Hưng: chuẩn bị giấy tờ chuyến đi (gốc: Specialist/thu_tuc.js; đánh số lại mã trùng: 1060 → 1081, 1057 → 1082, 1055 → 1083) =====
    {
      ...ok('Trang trại Ba Vì'), id: 'EQ-2026-1081', submittedAt: atTime(-12, '09:00'), departAt: daysFromToday(5),
      from: 'Trang trại Ba Vì (Hà Nội, VN)', to: 'Trường đua Sông Hàn (Đà Nẵng, VN)',
      routeShort: 'Ba Vì → Đà Nẵng', border: null, distance: '800 km', duration: '~16 giờ', horses: [tanVien],
      stops: ['Trang trại Ba Vì — nhận ngựa', 'Trạm nghỉ Vinh (Nghệ An) — dừng nghỉ', 'Trường đua Sông Hàn (Đà Nẵng) — giao ngựa'],
      services: withFreight(std('2 ngăn', '800 km', ['Kiểm dịch vận chuyển nội địa', 'Cấp giấy chứng nhận kiểm dịch cho 1 ngựa', 900_000], [CARE_FULL, 1_300_000], 900_000), 17_000_000),
      status: 'paid', approvedAt: atTime(-3, '09:00'), paidAt: atTime(-2, '15:00'), inspector: 'Phạm Văn Hưng', coordinator: 'Trần Minh', vehicle: 'Xe chuyên dụng 2 ngăn',
      review: { inspectNote: 'Giấy tờ hợp lệ.', vehicle: '60C-222.10 (xe chuyên dụng 2 ngăn)', driver: 'Phan Thanh Hải', grooms: 'Huỳnh Thị Mai' },
      papers: { originals: { 'Tản Viên': received(DOCS_DOMESTIC, atTime(-1, '09:00')) }, procedures: {} },
    },
    {
      ...ok('Savan Horse Club'), id: 'EQ-2026-1082', submittedAt: atTime(-15, '09:00'), departAt: daysFromToday(7),
      from: 'Trường đua Savannakhet (Savannakhet, LA)', to: 'Trường đua Sông Hàn (Đà Nẵng, VN)',
      routeShort: 'Savannakhet (LA) → Đà Nẵng', border: 'Densavanh – Lao Bảo', distance: '500 km', duration: '2 ngày', horses: [mekongWind],
      stops: ['Trường đua Savannakhet — nhận ngựa', 'Cửa khẩu Densavanh (Savannakhet) — thông quan', 'Cửa khẩu Lao Bảo (Quảng Trị) — kiểm tra thú y', 'Trường đua Sông Hàn (Đà Nẵng) — giao ngựa'],
      services: withFreight(std('2 ngăn', '500 km', ['Kiểm dịch & thủ tục nhập cảnh', 'Trọn gói: xét nghiệm, chứng nhận, thông quan Densavanh – Lao Bảo', 4_800_000], [CARE_FULL, 1_800_000], 1_200_000), 14_000_000),
      status: 'paid', approvedAt: atTime(-6, '09:00'), paidAt: atTime(-5, '09:00'), inspector: 'Phạm Văn Hưng', coordinator: 'Phạm Tâm', vehicle: 'Xe chuyên dụng 2 ngăn',
      review: { inspectNote: 'Giấy tờ hợp lệ.', vehicle: '43C-222.11 (xe chuyên dụng 2 ngăn)', driver: 'Lê Minh Tuấn', grooms: 'Huỳnh Thị Mai' },
      papers: {
        originals: { 'Mekong Wind': received(DOCS_CROSS_BORDER, atTime(-4, '09:00')) },
        procedures: {
          quarantine_border: { number: 'KD-NK-2026/0388', agency: 'Cơ quan Thú y vùng III', issuedAt: daysFromToday(-1), validUntil: daysFromToday(8), file: 'GCN_kiem_dich_NK_EQ-2026-1082.pdf' },
          customs: { number: '305112448720', agency: 'Chi cục Hải quan cửa khẩu Lao Bảo', issuedAt: daysFromToday(-1), file: 'To_khai_HQ_EQ-2026-1082.pdf' },
        },
      },
    },
    {
      ...ok('CLB Ngựa Phú Thọ'), id: 'EQ-2026-1083', submittedAt: atTime(-16, '09:00'), departAt: daysFromToday(4),
      from: 'Trường đua Phú Thọ (TP.HCM, VN)', to: 'Trường đua Angkor (Siem Reap, KH)',
      routeShort: 'TP.HCM → Siem Reap (KH)', border: 'Mộc Bài – Bavet', distance: '550 km', duration: '~12 giờ', horses: [xichTho],
      stops: ['Trường đua Phú Thọ (TP.HCM) — nhận ngựa', 'Cửa khẩu Mộc Bài (Tây Ninh) — thông quan', 'Cửa khẩu Bavet (Svay Rieng) — kiểm tra thú y', 'Trường đua Angkor — giao ngựa'],
      services: withFreight(std('2 ngăn', '550 km', ['Kiểm dịch & thủ tục xuất cảnh', 'Trọn gói: xét nghiệm, chứng nhận, thông quan Mộc Bài – Bavet', 4_800_000], [CARE_FULL, 1_200_000], 1_000_000), 15_000_000),
      status: 'paid', approvedAt: atTime(-7, '09:00'), paidAt: atTime(-6, '11:00'), inspector: 'Phạm Văn Hưng', coordinator: 'Trần Minh', vehicle: 'Xe chuyên dụng 2 ngăn',
      review: { inspectNote: 'Giấy tờ hợp lệ.', vehicle: '61C-345.67 (xe chuyên dụng 2 ngăn)', driver: 'Võ Thanh Sơn', grooms: 'Đỗ Văn Nam' },
      papers: {
        originals: { 'Xích Thố': received(DOCS_CROSS_BORDER, atTime(-5, '09:00')) },
        procedures: {
          quarantine_border: { number: 'KD-XK-2026/0405', agency: 'Cơ quan Thú y vùng VI', issuedAt: daysFromToday(-2), validUntil: daysFromToday(10), file: 'GCN_kiem_dich_XK_EQ-2026-1083.pdf' },
          customs: { number: '305112431150', agency: 'Chi cục Hải quan cửa khẩu Mộc Bài', issuedAt: daysFromToday(-1), file: 'To_khai_HQ_EQ-2026-1083.pdf' },
        },
      },
    },
    {
      ...ok('Đại Nam Racing'), id: 'EQ-2026-1054', submittedAt: atTime(-17, '09:00'), departAt: daysFromToday(1),
      from: 'Trường đua Đại Nam (Bình Dương, VN)', to: 'Trang trại Mekong (Cần Thơ, VN)',
      routeShort: 'Bình Dương → Cần Thơ', border: null, distance: '190 km', duration: '~4.5 giờ', horses: [hoaTien],
      stops: ['Trường đua Đại Nam (Bình Dương) — nhận ngựa', 'Trang trại Mekong (Cần Thơ) — giao ngựa'],
      services: withFreight(std('2 ngăn', '190 km', ['Kiểm dịch vận chuyển nội địa', 'Cấp giấy chứng nhận kiểm dịch cho 1 ngựa', 900_000], [CARE_FULL, 400_000], 500_000), 6_000_000),
      status: 'paid', approvedAt: atTime(-8, '09:00'), paidAt: atTime(-7, '09:00'), inspector: 'Phạm Văn Hưng', coordinator: 'Trần Minh', vehicle: 'Xe chuyên dụng 2 ngăn',
      review: { inspectNote: 'Giấy tờ hợp lệ.', vehicle: '51C-98765 (xe thùng tiêu chuẩn)', driver: 'Trần Văn B', grooms: 'Lê Thị C' },
      papers: { originals: { 'Hỏa Tiễn': received(DOCS_DOMESTIC, atTime(-6, '09:00')) }, procedures: {} },
    },
  ]
}
