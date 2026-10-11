// Sự cố trên đường. Gốc: Fleet And Route/ops_data.js (sự cố do điều phối ghi nhận, đề xuất cách xử lý)
// + Manager/manager_duyet_su_co.html (chi phí, bên chịu phí, bảo hiểm; Manager duyệt hoặc từ chối).
// Chuyến TR-9021 → đơn EQ-2026-1080, TR-9042 → đơn EQ-2026-1079, TR-9028 → đơn EQ-2026-1028.
import { atTime } from '../../lib/dates'

export type IncidentStatus = 'open' | 'proposed' | 'approved' | 'rejected' // open: chờ điều phối đề xuất · proposed: chờ Manager
export interface Incident {
  id: string
  tripId: string
  orderId: string
  leg: string
  time: number
  severity: 'emergency' | 'medium' | 'low'
  type: string
  desc: string
  status: IncidentStatus
  proposal: string
  report?: string // báo cáo chi tiết của điều phối viên
  cost?: number // chi phí phát sinh dự kiến (VND)
  bearer?: 'company' | 'customer'
  claim?: string // khả năng claim bảo hiểm
  attachments?: string[]
  directive?: string // chỉ đạo của Manager khi duyệt
  rejectReason?: string
}

export const seedIncidents = (): Incident[] => [
  { id: 'INC-001', tripId: 'TR-9021', orderId: 'EQ-2026-1080', leg: '1/2', time: atTime(0, '10:30'), severity: 'emergency', type: 'Y tế ngựa', desc: 'Nhịp tim bất thường, ngựa bỏ ăn trên cabin', status: 'open', proposal: '' },
  { id: 'INC-002', tripId: 'TR-9042', orderId: 'EQ-2026-1079', leg: '1/1', time: atTime(-1, '14:15'), severity: 'medium', type: 'Hỏng xe', desc: 'Xe 30A-55678 nổ lốp tại KM15 cao tốc', status: 'proposed',
    proposal: 'Gọi cứu hộ thay lốp tại chỗ, ngựa ở lại khoang có điều hòa trong lúc chờ.',
    report: 'Xe 30A-55678 bị nổ lốp tại KM15 cao tốc. Không có thương vong, nhiệt độ cabin ổn định dưới 22°C. Đề xuất gọi cứu hộ khẩn cấp.',
    cost: 1_500_000, bearer: 'company', claim: 'Có thể Claim (BH phương tiện)', attachments: ['Hóa đơn', 'Hiện trường'] },
  { id: 'INC-003', tripId: 'TR-9021', orderId: 'EQ-2026-1080', leg: '2/2', time: atTime(0, '08:00'), severity: 'low', type: 'Giao thông', desc: 'Tắc nghẽn đường tới Viêng Chăn, trễ 30p', status: 'open', proposal: '' },
  { id: 'INC-004', tripId: 'TR-9042', orderId: 'EQ-2026-1079', leg: '1/1', time: atTime(0, '09:00'), severity: 'medium', type: 'Y tế ngựa', desc: 'Ngựa stress nhiệt, cần dừng nghỉ thêm 2 giờ', status: 'proposed',
    proposal: 'Dừng trạm nghỉ Tuy Hòa 2 giờ, bổ sung nước điện giải',
    report: 'Ngựa Tuyết Sơn có dấu hiệu stress nhiệt (thân nhiệt 38,7°C). Đề xuất dừng tại trạm Tuy Hòa 2 giờ để hạ nhiệt và bổ sung điện giải.',
    cost: 800_000, bearer: 'customer', claim: 'Không áp dụng', attachments: ['Nhật ký sức khỏe'] },
  { id: 'INC-005', tripId: 'TR-9042', orderId: 'EQ-2026-1079', leg: '1/1', time: atTime(0, '11:20'), severity: 'low', type: 'Giao thông', desc: 'Mưa lớn đoạn Quy Nhơn, giảm tốc độ', status: 'open', proposal: '' },
  { id: 'INC-006', tripId: 'TR-9028', orderId: 'EQ-2026-1028', leg: '1/2', time: atTime(0, '07:30'), severity: 'medium', type: 'Giao thông', desc: 'Kẹt xe trước cửa khẩu Mộc Bài, trễ lịch 1 giờ', status: 'approved',
    proposal: 'Báo trước cho hải quan Bavet, giữ nguyên lịch giao ngựa buổi chiều.', report: 'Hàng dài xe tải chờ làm thủ tục tại Mộc Bài.', cost: 0, bearer: 'company', claim: 'Không áp dụng', attachments: ['Hiện trường'],
    directive: 'Đồng ý. Cập nhật giờ dự kiến giao cho khách.' },
  { id: 'INC-007', tripId: 'TR-9042', orderId: 'EQ-2026-1079', leg: '1/1', time: atTime(-1, '16:20'), severity: 'low', type: 'Thiết bị', desc: 'GPS mất tín hiệu 2 tiếng', status: 'rejected',
    proposal: 'Thuê thiết bị GPS dự phòng giá 2.000.000 VND.', report: 'Thiết bị định vị mất tín hiệu đoạn đèo Cả.', cost: 2_000_000, bearer: 'company', claim: 'Không áp dụng', attachments: [],
    rejectReason: 'Dùng định vị điện thoại của tài xế, không thuê thiết bị ngoài.' },
]
