// Bộ đơn mẫu CHUẨN của toàn hệ thống (gốc: CUS/don_cua_toi.js). Mọi vai trò đọc cùng bộ này.
// Ngày giờ tính tương đối so với lúc mở trang để các trạng thái hạn chót luôn hiện đúng.
import { APPRAISAL_WORKING_DAYS, HOUR } from '../../config/business-rules'
import { atTime, daysFromToday, workdaysAgo } from '../../lib/dates'
import { formatDate } from '../../lib/format'
import type { Horse, Order } from '../../types/order'

export const CUSTOMER = { name: 'Trang trại Long Thành', phoneLast4: '3456', email: 'longthanh.farm@gmail.com' }

export const HORSES: Record<string, Horse> = {
  stormRunner: { name: 'Storm Runner', breed: 'Thoroughbred', sex: 'Thiến', chip: 'VN-985211' },
  bachPhong: { name: 'Bạch Phong', breed: 'Arabian', sex: 'Cái', chip: 'VN-985212' },
  kimLan: { name: 'Kim Lân', breed: 'Thoroughbred', sex: 'Đực', chip: 'VN-985213' },
  hacPhong: { name: 'Hắc Phong', breed: 'Thoroughbred', sex: 'Đực', chip: 'VN-985214' },
}
const { stormRunner, bachPhong, kimLan, hacPhong } = HORSES

const LONG_THANH = 'Trang trại Long Thành (Đồng Nai, VN)'
const base = { customer: CUSTOMER.name, customerEmail: CUSTOMER.email, phoneLast4: CUSTOMER.phoneLast4, from: LONG_THANH }

export function seedOrders(): Order[] {
  const now = Date.now()
  return [
    {
      ...base, id: 'EQ-2026-1064', submittedAt: daysFromToday(-2, 9), departAt: daysFromToday(15),
      to: 'Trường đua Đại Nam (Bình Dương, VN)', routeShort: 'Đồng Nai → Bình Dương', border: null, distance: '65 km', duration: '~2 giờ',
      horses: [hacPhong], vehicle: 'Xe chuyên dụng 2 ngăn', stops: ['Trang trại Long Thành — nhận ngựa', 'Trường đua Đại Nam — giao ngựa'],
      services: [
        ['Vận chuyển đường bộ', 'Xe chuyên dụng 2 ngăn · khoang tiêu chuẩn · 65 km', 4_200_000],
        ['Kiểm dịch vận chuyển nội địa', 'Cấp giấy chứng nhận kiểm dịch cho 1 ngựa', 900_000],
        ['Chăm sóc dọc đường', 'NV chăm sóc đi kèm · nước điện giải', 400_000],
        ['Bảo hiểm vận chuyển', 'Gói cơ bản', 500_000],
      ],
      status: 'awaiting_payment', approvedAt: now - 43 * HOUR, inspector: 'Phạm Văn Hưng', coordinator: 'Trần Minh',
      review: { inspectNote: 'Giấy tờ hợp lệ.', vehicle: '60C-222.10 (xe chuyên dụng 2 ngăn)', driver: 'Phan Thanh Hải', grooms: 'Võ Thị Lan' },
    },
    {
      ...base, id: 'EQ-2026-1042', submittedAt: daysFromToday(-2, 9), departAt: daysFromToday(12),
      to: 'Trường đua Phnom Penh Royal Turf (Phnom Penh, KH)', routeShort: 'Đồng Nai → Phnom Penh (KH)', border: 'Mộc Bài – Bavet', distance: '290 km', duration: '~7 giờ',
      horses: [stormRunner, bachPhong], vehicle: 'Xe chuyên dụng 4 ngăn',
      stops: ['Trang trại Long Thành (Đồng Nai) — nhận ngựa', 'Cửa khẩu Mộc Bài (Tây Ninh) — thông quan', 'Cửa khẩu Bavet (Svay Rieng) — kiểm tra thú y', 'Trường đua Phnom Penh Royal Turf — giao ngựa'],
      services: [
        ['Vận chuyển đường bộ', 'Xe chuyên dụng 4 ngăn · khoang tiêu chuẩn · 290 km', 17_000_000],
        ['Kiểm dịch & thủ tục xuất cảnh', 'Trọn gói: xét nghiệm, chứng nhận, thông quan Mộc Bài – Bavet', 6_500_000],
        ['Chăm sóc dọc đường', 'NV chăm sóc đi kèm · cỏ khô Timothy · nước điện giải', 1_500_000],
        ['Bảo hiểm vận chuyển', 'Gói cơ bản', 1_500_000],
      ],
      // Trang Phê duyệt: chờ Manager duyệt. Duyệt xong khách mới thấy yêu cầu thanh toán.
      status: 'processing', stage: 'approval', inspector: 'Phạm Văn Hưng', coordinator: 'Trần Minh',
      review: { inspectNote: `Giấy tờ đầy đủ, tiêm phòng còn hạn đến 12/2026. Sẽ kiểm tra sức khỏe tại chỗ ngày ${formatDate(daysFromToday(12))}.`, vehicle: '51C-123.45 (xe chuyên dụng 4 ngăn)', driver: 'Nguyễn Văn Hùng', grooms: 'Võ Thị Lan' },
    },
    {
      ...base, id: 'EQ-2026-1063', submittedAt: daysFromToday(0, 9), departAt: daysFromToday(18),
      to: 'Trường đua Phú Thọ (TP.HCM, VN)', routeShort: 'Đồng Nai → TP.HCM', border: null, distance: '60 km', duration: '~2 giờ',
      horses: [stormRunner, kimLan],
      services: [
        ['Vận chuyển đường bộ', 'Xe chuyên dụng 2 ngăn · khoang tiêu chuẩn · 60 km', 5_000_000],
        ['Kiểm dịch vận chuyển nội địa', 'Cấp giấy chứng nhận kiểm dịch cho 2 ngựa', 1_500_000],
        ['Chăm sóc dọc đường', 'NV chăm sóc đi kèm · nước điện giải', 600_000],
        ['Bảo hiểm vận chuyển', 'Gói cơ bản', 900_000],
      ],
      status: 'processing', stage: 'intake', customerNote: '', hold: '1 xe chuyên dụng 2 ngăn',
    },
    {
      ...base, id: 'EQ-2026-1055', submittedAt: workdaysAgo(APPRAISAL_WORKING_DAYS + 1), departAt: daysFromToday(12),
      to: 'Vientiane Turf Club (Viêng Chăn, LA)', routeShort: 'Đồng Nai → Viêng Chăn (LA)', border: 'Lao Bảo – Densavanh', distance: '1,250 km', duration: '3 ngày',
      horses: [kimLan],
      services: [
        ['Vận chuyển đường bộ', 'Xe chuyên dụng 2 ngăn · khoang tiêu chuẩn · 1,250 km', 32_000_000],
        ['Kiểm dịch & thủ tục xuất cảnh', 'Trọn gói: xét nghiệm, chứng nhận, thông quan Lao Bảo – Densavanh', 4_800_000],
        ['Chăm sóc dọc đường', 'NV chăm sóc đi kèm · cỏ khô Timothy · nước điện giải', 2_400_000],
        ['Bảo hiểm vận chuyển', 'Gói cơ bản', 1_500_000],
      ],
      status: 'processing', stage: 'inspecting', intakeAt: workdaysAgo(APPRAISAL_WORKING_DAYS + 1, 11), inspector: 'Nguyễn Thị Thu', coordinator: 'Trần Minh', hold: '1 xe chuyên dụng 2 ngăn',
    },
    {
      ...base, id: 'EQ-2026-1028', submittedAt: daysFromToday(-15, 9), departAt: daysFromToday(0),
      to: 'Trường đua Phnom Penh Royal Turf (Phnom Penh, KH)', routeShort: 'Đồng Nai → Phnom Penh (KH)', border: 'Mộc Bài – Bavet', distance: '290 km', duration: '~7 giờ',
      horses: [kimLan], vehicle: 'Xe chuyên dụng 2 ngăn',
      stops: ['Trang trại Long Thành — nhận ngựa', 'Cửa khẩu Mộc Bài — thông quan', 'Cửa khẩu Bavet — kiểm tra thú y', 'Trường đua Phnom Penh Royal Turf — giao ngựa'],
      services: [
        ['Vận chuyển đường bộ', 'Xe chuyên dụng 2 ngăn · khoang tiêu chuẩn · 290 km', 11_000_000],
        ['Kiểm dịch & thủ tục xuất cảnh', 'Trọn gói: xét nghiệm, chứng nhận, thông quan Mộc Bài – Bavet', 3_200_000],
        ['Chăm sóc dọc đường', 'NV chăm sóc đi kèm · cỏ khô Timothy · nước điện giải', 800_000],
        ['Bảo hiểm vận chuyển', 'Gói cơ bản', 1_000_000],
      ],
      status: 'in_transit', approvedAt: daysFromToday(-8, 10), paidAt: daysFromToday(-7, 15), inspector: 'Phạm Văn Hưng', coordinator: 'Trần Minh',
      review: { inspectNote: 'Giấy tờ hợp lệ.', vehicle: '51C-123.45 (xe chuyên dụng 2 ngăn)', driver: 'Nguyễn Văn Hùng', grooms: 'Võ Thị Lan' },
      papers: {
        originals: { 'Kim Lân': { passport: daysFromToday(-5, 10), vaccine: daysFromToday(-5, 10), lab: daysFromToday(-5, 10), import_permit: daysFromToday(-4, 14), ownership: daysFromToday(-5, 10) } },
        procedures: {
          quarantine_border: { number: 'KD-XK-2026/0391', agency: 'Cơ quan Thú y vùng VI', issuedAt: daysFromToday(-3), validUntil: daysFromToday(7), file: 'GCN_kiem_dich_XK_EQ-2026-1028.pdf' },
          customs: { number: '305112398410', agency: 'Chi cục Hải quan cửa khẩu Mộc Bài', issuedAt: daysFromToday(-2), file: 'To_khai_HQ_EQ-2026-1028.pdf' },
        },
        handedAt: daysFromToday(-2, 11),
      },
      trip: {
        plate: '51C-123.45', eta: now + 3 * HOUR, updatedAt: now - 10 * 60000,
        contacts: [['Tài xế', 'Nguyễn Văn Hùng', '0908 111 222'], ['NV chăm sóc', 'Võ Thị Lan', '0908 333 444']],
        checkpoints: [
          { label: 'Kiểm tra sức khỏe tại chỗ — Đạt', place: 'Trang trại Long Thành', time: now - 6 * HOUR, state: 'done' },
          { label: 'Nhận ngựa lên xe, khởi hành', place: 'Trang trại Long Thành', time: now - 5.5 * HOUR, state: 'done' },
          { label: 'Dừng nghỉ, cho ngựa uống nước', place: 'Trạm dừng Trảng Bàng (Tây Ninh)', time: now - 3 * HOUR, state: 'done' },
          { label: 'Thông quan xuất cảnh', place: 'Cửa khẩu Mộc Bài (VN)', time: now - 1.5 * HOUR, state: 'done' },
          { label: 'Kiểm tra thú y nhập cảnh', place: 'Cửa khẩu Bavet (KH)', time: now - 0.5 * HOUR, state: 'current' },
          { label: 'Giao ngựa', place: 'Trường đua Phnom Penh Royal Turf', time: now + 3 * HOUR, state: 'next' },
        ],
        health: [
          { time: now - 40 * 60000, temp: '37.8°C', heart: '36 bpm', note: 'Ăn uống bình thường, đứng vững trong khoang', horse: 'Kim Lân', status: 'Bình thường', by: 'Võ Thị Lan' },
          { time: now - 3 * HOUR, temp: '37.6°C', heart: '38 bpm', note: 'Đã uống 8 lít nước tại trạm dừng', horse: 'Kim Lân', status: 'Bình thường', by: 'Võ Thị Lan' },
          { time: now - 6 * HOUR, temp: '37.5°C', heart: '34 bpm', note: 'Kiểm tra trước khi lên xe: đạt', horse: 'Kim Lân', status: 'Bình thường', by: 'Võ Thị Lan' },
        ],
      },
    },
    {
      ...base, id: 'EQ-2026-1019', submittedAt: daysFromToday(-16, 9), departAt: daysFromToday(0),
      to: 'Trường đua Đại Nam (Bình Dương, VN)', routeShort: 'Đồng Nai → Bình Dương', border: null, distance: '65 km', duration: '~2 giờ',
      horses: [stormRunner], vehicle: 'Xe chuyên dụng 2 ngăn', stops: ['Trang trại Long Thành — nhận ngựa', 'Trường đua Đại Nam — giao ngựa'],
      services: [
        ['Vận chuyển đường bộ', 'Xe chuyên dụng 2 ngăn · khoang tiêu chuẩn · 65 km', 3_900_000],
        ['Kiểm dịch vận chuyển nội địa', 'Cấp giấy chứng nhận kiểm dịch cho 1 ngựa', 900_000],
        ['Chăm sóc dọc đường', 'NV chăm sóc đi kèm · nước điện giải', 400_000],
        ['Bảo hiểm vận chuyển', 'Gói cơ bản', 400_000],
      ],
      status: 'delivered', approvedAt: daysFromToday(-8, 10), paidAt: now - 6 * 24 * HOUR, deliveredAt: now - 3 * HOUR,
      handover: {
        vehicle: 'Xe chuyên dụng 2 ngăn · 51C-123.45', driver: 'Nguyễn Văn Hùng', groom: 'Võ Thị Lan', inspector: 'Phạm Văn Hưng',
        pickup: { time: now - 6 * HOUR, temp: 37.6, heart: 34, eat: 'Bình thường', body: 'Không chấn thương' },
        delivery: { time: now - 3 * HOUR, temp: 37.9, heart: 38, eat: 'Bình thường', body: 'Không chấn thương' },
      },
    },
    {
      ...base, id: 'EQ-2026-1030', submittedAt: daysFromToday(-12, 9), departAt: daysFromToday(6),
      to: 'Trường đua Sông Hàn (Đà Nẵng, VN)', routeShort: 'Đồng Nai → Đà Nẵng', border: null, distance: '930 km', duration: '2 ngày',
      horses: [bachPhong], vehicle: 'Xe chuyên dụng 2 ngăn',
      stops: ['Trang trại Long Thành — nhận ngựa', 'Trạm nghỉ Tuy Hòa (Phú Yên) — nghỉ đêm', 'Trường đua Sông Hàn (Đà Nẵng) — giao ngựa'],
      services: [
        ['Vận chuyển đường bộ', 'Xe chuyên dụng 2 ngăn · khoang tiêu chuẩn · 930 km', 21_000_000],
        ['Kiểm dịch vận chuyển nội địa', 'Cấp giấy chứng nhận kiểm dịch cho 1 ngựa', 900_000],
        ['Chăm sóc dọc đường', 'NV chăm sóc đi kèm · cỏ khô Timothy · nước điện giải', 2_400_000],
        ['Bảo hiểm vận chuyển', 'Gói cơ bản', 1_200_000],
      ],
      status: 'paid', approvedAt: now - 9 * 24 * HOUR, paidAt: now - 8 * 24 * HOUR, inspector: 'Nguyễn Thị Thu', coordinator: 'Trần Minh',
      review: { inspectNote: 'Vận chuyển nội địa, giấy tờ đầy đủ.', vehicle: '51C-123.45 (xe chuyên dụng 2 ngăn)', driver: 'Nguyễn Văn Hùng', grooms: 'Võ Thị Lan' },
      papers: { originals: { 'Bạch Phong': { passport: daysFromToday(-2, 10), vaccine: daysFromToday(-2, 10), ownership: null } }, procedures: {} },
    },
    {
      ...base, id: 'EQ-2026-1021', submittedAt: daysFromToday(-22, 9), departAt: daysFromToday(-6),
      to: 'Trường đua Phú Thọ (TP.HCM, VN)', routeShort: 'Đồng Nai → TP.HCM', border: null, distance: '60 km', duration: '~2 giờ',
      horses: [stormRunner], vehicle: 'Xe chuyên dụng 2 ngăn', stops: ['Trang trại Long Thành — nhận ngựa', 'Trường đua Phú Thọ — giao ngựa'],
      services: [
        ['Vận chuyển đường bộ', 'Xe chuyên dụng 2 ngăn · khoang tiêu chuẩn · 60 km', 3_800_000],
        ['Kiểm dịch vận chuyển nội địa', 'Cấp giấy chứng nhận kiểm dịch cho 1 ngựa', 900_000],
        ['Bảo hiểm vận chuyển', 'Gói cơ bản', 400_000],
      ],
      status: 'completed', approvedAt: now - 20 * 24 * HOUR, paidAt: now - 19 * 24 * HOUR,
      deliveredAt: now - 6 * 24 * HOUR - 3 * HOUR, acceptedAt: now - 6 * 24 * HOUR - 2 * HOUR, acceptedBy: 'customer',
      handover: {
        vehicle: 'Xe chuyên dụng 2 ngăn · 51C-123.45', driver: 'Nguyễn Văn Hùng', groom: 'Đỗ Văn Nam', inspector: 'Nguyễn Thị Thu',
        pickup: { time: now - 6 * 24 * HOUR - 5 * HOUR, temp: 37.5, heart: 32, eat: 'Bình thường', body: 'Không chấn thương' },
        delivery: { time: now - 6 * 24 * HOUR - 3 * HOUR, temp: 37.7, heart: 36, eat: 'Bình thường', body: 'Không chấn thương' },
      },
    },
    {
      ...base, id: 'EQ-2026-1049', submittedAt: daysFromToday(-1, 9), departAt: daysFromToday(12),
      to: 'Trường đua Phnom Penh Royal Turf (Phnom Penh, KH)', routeShort: 'Đồng Nai → Phnom Penh (KH)', border: 'Mộc Bài – Bavet', distance: '290 km', duration: '~7 giờ',
      horses: [stormRunner, bachPhong],
      services: [
        ['Vận chuyển đường bộ', 'Xe chuyên dụng 4 ngăn · khoang tiêu chuẩn · 290 km', 17_000_000],
        ['Kiểm dịch & thủ tục xuất cảnh', 'Trọn gói: xét nghiệm, chứng nhận, thông quan Mộc Bài – Bavet', 6_500_000],
        ['Chăm sóc dọc đường', 'NV chăm sóc đi kèm · cỏ khô Timothy · nước điện giải', 1_500_000],
        ['Bảo hiểm vận chuyển', 'Gói cơ bản', 1_500_000],
      ],
      // Trang Tiếp nhận: đơn mới, hệ thống cảnh báo nghi trùng. Manager từ chối thì khách thấy "Bị từ chối ở bước Tiếp nhận".
      status: 'processing', stage: 'intake', customerNote: '', hold: '1 xe chuyên dụng 4 ngăn',
      warning: `Có thể trùng đơn EQ-2026-1042: cùng khách hàng, cùng 2 ngựa (chip VN-985211, VN-985212), cùng ngày khởi hành ${formatDate(daysFromToday(12))}.`,
    },
    {
      ...base, id: 'EQ-2026-1074', submittedAt: daysFromToday(-3, 9), departAt: daysFromToday(14),
      to: 'Trường đua Angkor (Siem Reap, KH)', routeShort: 'Đồng Nai → Siem Reap (KH)', border: 'Mộc Bài – Bavet', distance: '560 km', duration: '2 ngày',
      horses: [hacPhong, kimLan],
      services: [
        ['Vận chuyển đường bộ', 'Xe chuyên dụng 2 ngăn · khoang tiêu chuẩn · 560 km', 18_000_000],
        ['Kiểm dịch & thủ tục xuất cảnh', 'Trọn gói cho 2 ngựa: xét nghiệm, chứng nhận, thông quan Mộc Bài – Bavet', 7_600_000],
        ['Chăm sóc dọc đường', 'NV chăm sóc đi kèm · cỏ khô Timothy · nước điện giải', 2_000_000],
        ['Bảo hiểm vận chuyển', 'Gói cơ bản', 1_600_000],
      ],
      status: 'choose_option', stage: 'inspecting', intakeAt: daysFromToday(-3, 11), inspector: 'Nguyễn Thị Thu', coordinator: 'Phạm Tâm', hold: '1 xe chuyên dụng 2 ngăn',
      offer: {
        issue: 'Kết quả xét nghiệm của ngựa Hắc Phong dương tính bệnh thiếu máu truyền nhiễm (EIA). Đây là bệnh không chữa được nên ngựa không thể vận chuyển.',
        affected: ['Hắc Phong'],
        options: ['remove_horse', 'replace_horse', 'recheck', 'cancel'],
        custom: [{
          code: 'F', label: 'Bỏ Hắc Phong, chuyển Kim Lân sang xe 1 ngăn',
          detail: 'Bỏ Hắc Phong khỏi đơn và chở Kim Lân bằng xe chuyên dụng 1 ngăn. Xe 1 ngăn chỉ còn trống từ 2 ngày sau ngày khởi hành hiện tại nên ngày khởi hành dời thêm 2 ngày. Giá mới 17,300,000 ₫. Bạn không cần cung cấp thêm giấy tờ.',
        }],
        sentAt: now - 6 * HOUR,
        requoteServices: [
          ['Vận chuyển đường bộ', 'Xe chuyên dụng 2 ngăn · khoang tiêu chuẩn · 560 km', 18_000_000],
          ['Kiểm dịch & thủ tục xuất cảnh', 'Trọn gói cho 1 ngựa: xét nghiệm, chứng nhận, thông quan Mộc Bài – Bavet', 3_800_000],
          ['Chăm sóc dọc đường', 'NV chăm sóc đi kèm · cỏ khô Timothy · nước điện giải', 1_000_000],
          ['Bảo hiểm vận chuyển', 'Gói cơ bản', 800_000],
        ],
      },
    },
    {
      ...base, id: 'EQ-2026-1025', submittedAt: daysFromToday(-19, 9), departAt: daysFromToday(-4),
      to: 'Trường đua Angkor (Siem Reap, KH)', routeShort: 'Đồng Nai → Siem Reap (KH)', border: 'Mộc Bài – Bavet', distance: '560 km', duration: '2 ngày',
      horses: [kimLan], vehicle: 'Xe chuyên dụng 2 ngăn',
      stops: ['Trang trại Long Thành — nhận ngựa', 'Cửa khẩu Mộc Bài — thông quan', 'Trường đua Angkor — giao ngựa'],
      services: [
        ['Vận chuyển đường bộ', 'Xe chuyên dụng 2 ngăn · khoang tiêu chuẩn · 560 km', 15_000_000],
        ['Kiểm dịch & thủ tục xuất cảnh', 'Trọn gói: xét nghiệm, chứng nhận, thông quan Mộc Bài – Bavet', 4_800_000],
        ['Bảo hiểm vận chuyển', 'Gói cơ bản', 1_000_000],
      ],
      status: 'awaiting_payment', approvedAt: now - 10 * 24 * HOUR, inspector: 'Phạm Văn Hưng', coordinator: 'Phạm Tâm',
      review: { inspectNote: 'Giấy tờ hợp lệ.', vehicle: '60C-222.10 (xe chuyên dụng 2 ngăn)', driver: 'Phan Thanh Hải', grooms: 'Võ Thị Lan' },
    },
    {
      // Trang Phê duyệt: đã thanh toán, khách chưa gửi đủ bản gốc → kiểm dịch viên báo cáo Manager (gốc: manager_phe_duyet.js)
      ...base, id: 'EQ-2026-1052', submittedAt: atTime(-18, '09:00'), departAt: daysFromToday(1),
      to: 'Trường đua Angkor (Siem Reap, KH)', routeShort: 'Đồng Nai → Siem Reap (KH)', border: 'Mộc Bài – Bavet', distance: '560 km', duration: '2 ngày',
      horses: [kimLan], vehicle: 'Xe chuyên dụng 2 ngăn',
      stops: ['Trang trại Long Thành — nhận ngựa', 'Cửa khẩu Mộc Bài (Tây Ninh) — thông quan', 'Cửa khẩu Bavet (Svay Rieng) — kiểm tra thú y', 'Trường đua Angkor — giao ngựa'],
      services: [
        ['Vận chuyển đường bộ', 'Xe chuyên dụng 2 ngăn · khoang tiêu chuẩn · 560 km', 15_000_000],
        ['Kiểm dịch & thủ tục xuất cảnh', 'Trọn gói: xét nghiệm, chứng nhận, thông quan Mộc Bài – Bavet', 3_800_000],
        ['Chăm sóc dọc đường', 'NV chăm sóc đi kèm · cỏ khô Timothy · nước điện giải', 1_000_000],
        ['Bảo hiểm vận chuyển', 'Gói cơ bản', 800_000],
      ],
      status: 'paid', approvedAt: atTime(-11, '10:00'), paidAt: atTime(-10, '16:00'), inspector: 'Phạm Văn Hưng', coordinator: 'Trần Minh',
      review: { inspectNote: 'Giấy tờ hợp lệ.', vehicle: '60C-222.10 (xe chuyên dụng 2 ngăn)', driver: 'Phan Thanh Hải', grooms: 'Võ Thị Lan' },
      papers: {
        originals: { 'Kim Lân': { passport: atTime(-7, '15:00'), vaccine: atTime(-7, '15:00'), lab: atTime(-7, '15:00'), import_permit: null, ownership: null } },
        procedures: { quarantine_border: { number: 'KD-XK-2026/0409', agency: 'Cơ quan Thú y vùng VI', issuedAt: daysFromToday(-3), validUntil: daysFromToday(11), file: 'GCN_kiem_dich_XK_EQ-2026-1052.pdf' } },
      },
      papersReport: {
        at: atTime(-2, '08:00'), type: 'Khách chưa gửi bản gốc đúng hạn',
        items: ['Kim Lân · Giấy phép nhập khẩu của nước đến', 'Kim Lân · Giấy tờ chứng minh sở hữu', 'Tờ khai hải quan (điện tử)'],
        note: 'Đã gọi khách 2 lần, khách hẹn gửi nhưng chưa nhận được. Chưa khai hải quan được vì thiếu bản gốc giấy phép nhập khẩu.',
      },
    },
  ]
}
