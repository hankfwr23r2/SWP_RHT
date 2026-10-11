// Quy tắc của luồng đặt đơn, duyệt báo giá và đặt cọc (Flow 1). Khớp docs/PRD.md mục 2, 10, 11, 12.
// Các đơn giá ở cuối file là số mẫu để chạy thử: PRD chưa có biểu giá chính thức. Thay bằng bảng giá thật khi có.
import { BIG_TRUCK_FACTOR } from './public-pricing'

// ===== Cọc, báo giá =====
export const DEPOSIT_RATE = 0.3 // cọc 30% để nhận Vận đơn; 70% còn lại trả ngày D (PRD mục 2.6, 11.1)
export const QUOTE_VALID_HOURS = 48 // hạn giữ báo giá; quá hạn thì đơn hết hiệu lực
export const DOCS_CUTOFF_HOUR = 18 // 18:00 ngày D-1: mốc cảnh báo nội bộ giấy tờ (PRD mục 3.4)

// ===== Hạng xe (PRD mục 10) =====
export type VehicleClass = 'light' | 'medium' | 'heavy'
export const VEHICLE_CLASS: Record<VehicleClass, { label: string; kind: string; stalls: string; maxStalls: number }> = {
  light: { label: 'Light', kind: 'Xe tải nhẹ / Van', stalls: '2 ngăn', maxStalls: 2 },
  medium: { label: 'Medium', kind: 'Xe tải trung', stalls: '4 đến 6 ngăn', maxStalls: 6 },
  heavy: { label: 'Heavy', kind: 'Xe tải nặng', stalls: '9 ngăn', maxStalls: 9 },
}

// ===== Hồ sơ ngựa (PRD mục 2.1, 12) =====
export type HorseDocType = 'passport' | 'vaccine' | 'lab'
export const HORSE_DOC: Record<HorseDocType, { label: string; short: string; hint: string; hasExpiry: boolean }> = {
  passport: { label: 'Hộ chiếu ngựa (FEI / National Passport)', short: 'Hộ chiếu', hint: 'Có sơ đồ nhận dạng và số microchip trùng chip trên thân ngựa.', hasExpiry: false },
  vaccine: { label: 'Sổ tiêm phòng', short: 'Sổ tiêm', hint: 'Cúm ngựa còn hạn trong 6 đến 12 tháng, Uốn ván còn hiệu lực.', hasExpiry: true },
  lab: { label: 'Xét nghiệm EIA / Coggins', short: 'Xét nghiệm', hint: 'Kết quả âm tính trong vòng 6 đến 12 tháng.', hasExpiry: true },
}
export const HORSE_BREEDS = ['Thoroughbred', 'Arabian', 'Quarter Horse', 'Warmblood', 'Appaloosa', 'Khác']
export const HORSE_DOC_TYPES = Object.keys(HORSE_DOC) as HorseDocType[]

// ===== Chăm sóc trên xe (PRD mục 2.4) =====

// ===== Trạng thái đơn (PRD mục 13). Khách thấy nhãn tiếng Việt; nội bộ thấy thêm mã gốc. =====
export type BookingStatus =
  | 'pending_intake' | 'under_review' | 'pending_commercial' | 'awaiting_payment' | 'quote_expired' // Flow 1
  | 'waybill_issued' | 'clearance_in_progress' | 'clearance_done' // Flow 2
  | 'ready_for_pickup' | 'en_route_to_pickup' // Flow 3
  | 'in_transit' | 'incident_reported' | 'pending_emergency_approval' | 'emergency_plan_active' // Flow 5
  | 'delivered_pending_settlement' // Flow 4
  | 'expenses_submitted' | 'settlement_issued' | 'payment_overdue' | 'completed' // Flow 6
  | 'cancelled' // khách từ chối báo giá (PRD mục 2.6)
  | 'rejected' // nhà xe từ chối đơn: Manager lúc tiếp nhận hoặc Coordinator không duyệt lộ trình (PRD mục 2.3, 2.4)
export type Tone = 'info' | 'warning' | 'success' | 'danger' | 'muted' | 'orange'
export const BOOKING_STATUS: Record<BookingStatus, { code: string; label: string; customerLabel: string; tone: Tone }> = {
  pending_intake: { code: 'Pending Manager Intake', label: 'Chờ quản lý tiếp nhận', customerLabel: 'Đã gửi, chờ tiếp nhận', tone: 'info' },
  under_review: { code: 'Under Internal Review', label: 'Đang thẩm định nội bộ', customerLabel: 'Đang thẩm định', tone: 'info' },
  pending_commercial: { code: 'Pending Final Commercial Approval', label: 'Chờ quản lý duyệt báo giá', customerLabel: 'Đang lập báo giá', tone: 'orange' },
  awaiting_payment: { code: 'Awaiting Payment', label: 'Chờ khách đặt cọc', customerLabel: 'Chờ đặt cọc', tone: 'warning' },
  quote_expired: { code: 'Quote Expired', label: 'Báo giá hết hạn', customerLabel: 'Báo giá hết hạn', tone: 'muted' },
  waybill_issued: { code: 'Waybill Issued', label: 'Đã có Vận đơn, chờ kiểm dịch viên tiếp nhận', customerLabel: 'Đã có vận đơn', tone: 'success' },
  clearance_in_progress: { code: 'Clearance In Progress', label: 'Kiểm dịch viên đang làm thủ tục giấy tờ', customerLabel: 'Đang làm thủ tục giấy tờ', tone: 'info' },
  clearance_done: { code: 'Clearance Done', label: 'Giấy tờ xong, chờ tài xế và hộ tống nhận lệnh', customerLabel: 'Giấy tờ đã xong', tone: 'success' },
  ready_for_pickup: { code: 'Ready for Pickup', label: 'Sẵn sàng đón ngựa', customerLabel: 'Sẵn sàng đón ngựa', tone: 'success' },
  en_route_to_pickup: { code: 'En Route to Pickup', label: 'Xe đang đến điểm đón', customerLabel: 'Xe đang đến điểm đón', tone: 'info' },
  in_transit: { code: 'In Transit', label: 'Đang vận chuyển', customerLabel: 'Đang vận chuyển', tone: 'info' },
  incident_reported: { code: 'Incident Reported - Action Required', label: 'Có sự cố, chờ điều phối lập phương án', customerLabel: 'Xe gặp sự cố, đang xử lý', tone: 'danger' },
  pending_emergency_approval: { code: 'Pending Emergency Approval', label: 'Chờ quản lý duyệt phương án khẩn cấp', customerLabel: 'Xe gặp sự cố, đang xử lý', tone: 'danger' },
  emergency_plan_active: { code: 'Emergency Plan Active', label: 'Đang thực hiện phương án khẩn cấp', customerLabel: 'Đang xử lý sự cố, ngựa được chăm sóc', tone: 'warning' },
  delivered_pending_settlement: { code: 'Delivered - Pending Settlement', label: 'Đã giao, chờ tài xế gửi chi phí', customerLabel: 'Đã giao ngựa', tone: 'success' },
  expenses_submitted: { code: 'Expenses Submitted - Pending Audit', label: 'Chờ quản lý đối soát chi phí', customerLabel: 'Đã giao, đang quyết toán', tone: 'orange' },
  settlement_issued: { code: 'Settlement Issued - Awaiting Final Payment', label: 'Đã phát hành quyết toán, chờ khách trả', customerLabel: 'Chờ thanh toán quyết toán', tone: 'warning' },
  payment_overdue: { code: 'Payment Overdue', label: 'Khách quá hạn thanh toán quyết toán', customerLabel: 'Quá hạn thanh toán', tone: 'danger' },
  completed: { code: 'Order Completed', label: 'Đã hoàn tất', customerLabel: 'Đã hoàn tất', tone: 'success' },
  cancelled: { code: 'Cancelled', label: 'Khách từ chối báo giá', customerLabel: 'Đã hủy', tone: 'muted' },
  rejected: { code: 'Order Rejected', label: 'Nhà xe đã từ chối đơn', customerLabel: 'Đơn bị từ chối', tone: 'danger' },
}

// Nhãn ngắn của trạng thái cho tab và bảng của nhân viên (nhãn đầy đủ nằm ở tooltip)
export const STATUS_SHORT: Record<BookingStatus, string> = {
  pending_intake: 'Chờ tiếp nhận', under_review: 'Thẩm định', pending_commercial: 'Chờ duyệt giá', awaiting_payment: 'Chờ đặt cọc', quote_expired: 'Hết hạn',
  waybill_issued: 'Có vận đơn', clearance_in_progress: 'Làm giấy tờ', clearance_done: 'Giấy tờ xong', ready_for_pickup: 'Sẵn sàng đón', en_route_to_pickup: 'Đến điểm đón',
  in_transit: 'Vận chuyển', incident_reported: 'Báo sự cố', pending_emergency_approval: 'Chờ duyệt khẩn cấp', emergency_plan_active: 'Xử lý sự cố',
  delivered_pending_settlement: 'Đã giao', expenses_submitted: 'Chờ đối soát', settlement_issued: 'Chờ khách trả', payment_overdue: 'Quá hạn trả', completed: 'Hoàn tất',
  cancelled: 'Đã hủy', rejected: 'Bị từ chối',
}

// Thứ tự trạng thái theo luồng (đầu → cuối); đơn đã đóng (hết hạn, hủy) xếp cuối. Dùng để sắp danh sách đơn.
const CLOSED: BookingStatus[] = ['quote_expired', 'cancelled', 'rejected']
const MAIN_FLOW = (Object.keys(BOOKING_STATUS) as BookingStatus[]).filter(s => !CLOSED.includes(s))
export const statusRank = (s: BookingStatus): number => (CLOSED.includes(s) ? MAIN_FLOW.length + CLOSED.indexOf(s) : MAIN_FLOW.indexOf(s))

// Các bước khách thấy trên thanh tiến độ của đơn (các luồng sau sẽ thêm bước vào cuối)
export const BOOKING_STEPS = ['Gửi đơn', 'Thẩm định', 'Báo giá', 'Đặt cọc', 'Giấy tờ', 'Sẵn sàng', 'Vận chuyển', 'Quyết toán']
export const stepOf = (s: BookingStatus): number => ({
  pending_intake: 0, under_review: 1, pending_commercial: 2, awaiting_payment: 3, quote_expired: 3,
  waybill_issued: 4, clearance_in_progress: 4,
  clearance_done: 5, ready_for_pickup: 5, en_route_to_pickup: 5,
  in_transit: 6, incident_reported: 6, pending_emergency_approval: 6, emergency_plan_active: 6,
  delivered_pending_settlement: 7, expenses_submitted: 7, settlement_issued: 7, payment_overdue: 7,
  completed: BOOKING_STEPS.length, cancelled: 0, rejected: 1,
}[s])

// Tra cứu công khai ở trang chủ chỉ cho thấy 5 bước gọn
export const PUBLIC_STEPS = ['Gửi đơn', 'Thẩm định', 'Đặt cọc', 'Chuẩn bị chuyến', 'Vận chuyển']
export const publicStepOf = (s: BookingStatus): number => (s === 'pending_intake' || s === 'cancelled' || s === 'rejected' ? 0 : s === 'under_review' || s === 'pending_commercial' ? 1 : s === 'awaiting_payment' || s === 'quote_expired' ? 2 : ['in_transit', 'incident_reported', 'pending_emergency_approval', 'emergency_plan_active', 'delivered_pending_settlement', 'expenses_submitted', 'settlement_issued', 'payment_overdue', 'completed'].includes(s) ? 4 : 3)

// ===== Sự cố và chi phí (Flow 5, PRD mục 6, 11.5) =====
export type IncidentKind = 'horse_health' | 'vehicle_breakdown' | 'traffic_jam'
export const INCIDENT_KIND: Record<IncidentKind, { label: string; icon: string; hint: string }> = {
  horse_health: { label: 'Sức khỏe ngựa', icon: 'fa-horse-head', hint: 'Đau bụng, sốt, mất nước, chấn thương' },
  vehicle_breakdown: { label: 'Xe gặp sự cố', icon: 'fa-screwdriver-wrench', hint: 'Hỏng điều hòa thùng, nổ lốp, sự cố động cơ, tai nạn' },
  traffic_jam: { label: 'Giao thông tắc nghẽn', icon: 'fa-traffic-light', hint: 'Kẹt xe, tắc đường, đường bị chặn' },
}
// Phương án xử lý (lập trên bản đồ): mỗi nhóm sự cố có đúng một cách xử lý
export type IncidentAction = 'to_station' | 'rescue_and_station' | 'reroute'
export const INCIDENT_ACTION: Record<IncidentAction, string> = {
  to_station: 'Lập lại lộ trình đưa ngựa đến trạm nghỉ gần nhất',
  rescue_and_station: 'Gọi cứu hộ sửa xe gần chỗ xe gặp nạn, đưa ngựa đến trạm nghỉ gần nhất',
  reroute: 'Đổi sang lộ trình khác để tránh tắc nghẽn',
}
export type ExpenseCategory = 'vet_fee' | 'medicine' | 'holding_stable' | 'rescue' | 'repair' | 'other'
export const EXPENSE_CATEGORY: Record<ExpenseCategory, string> = {
  vet_fee: 'Viện phí thú y', medicine: 'Thuốc cấp cứu', holding_stable: 'Phí trạm nghỉ, chuồng đệm', rescue: 'Xe cứu hộ', repair: 'Sửa chữa xe', other: 'Khoản khác',
}
export type IncidentStatus = 'reported' | 'pending_approval' | 'active' | 'resolved'
export type Payer = 'customer' | 'carrier'
export const SETTLEMENT_GRACE_HOURS = 24 // hạn trả bảng quyết toán; quá hạn là Payment Overdue (PRD mục 8.2)

// ===== Giấy tờ pháp lý do Specialist làm (Flow 2, PRD mục 3.3, 12) =====
export type ClearanceDocType = 'health_cert' | 'poa' | 'customs_declaration' | 'import_permit' | 'quarantine_cert' | 'ata_carnet' | 'commercial_invoice'
// base: có sẵn ở mọi đơn thuộc tuyến tương ứng; không phải base thì Specialist thêm khi cần
export const CLEARANCE_DOC: Record<ClearanceDocType, { label: string; short: string; hint: string; international: boolean; base: boolean }> = {
  health_cert: { label: 'Giấy chứng nhận kiểm dịch động vật vận chuyển', short: 'Giấy kiểm dịch', hint: 'Mộc đỏ của cơ quan thú y có thẩm quyền.', international: false, base: true },
  poa: { label: 'Giấy ủy quyền áp tải', short: 'Giấy ủy quyền áp tải', hint: 'Song ngữ, ghi đúng tài xế và hộ tống của từng xe.', international: false, base: true },
  customs_declaration: { label: 'Tờ khai hải quan điện tử', short: 'Tờ khai hải quan', hint: 'Biển số xe và cửa khẩu phải khớp lộ trình.', international: true, base: true },
  import_permit: { label: 'Giấy phép nhập khẩu', short: 'Giấy phép nhập khẩu', hint: 'Do cơ quan thú y nước nhập khẩu phê duyệt.', international: true, base: true },
  quarantine_cert: { label: 'Giấy chứng nhận cách ly kiểm dịch trước xuất phát', short: 'Giấy cách ly', hint: 'Chỉ khi nước đến yêu cầu.', international: true, base: false },
  ata_carnet: { label: 'Sổ ATA Carnet', short: 'ATA Carnet', hint: 'Chỉ khi đi thi đấu, triển lãm (tạm nhập, tái xuất).', international: true, base: false },
  commercial_invoice: { label: 'Hóa đơn thương mại', short: 'Hóa đơn thương mại', hint: 'Chỉ khi người gửi bán ngựa cho người nhận.', international: true, base: false },
}

// ===== Đơn giá mẫu (PRD chưa có biểu giá chính thức) =====
export const CLASS_FACTOR: Record<VehicleClass, number> = { light: 1, medium: BIG_TRUCK_FACTOR, heavy: 1.9 }
export const CREW_FEE_PER_DAY = 1_800_000 // 01 Driver + 01 Escort, mỗi ngày
export const SINGLE_STALL_FEE = 1_500_000 // khoang đơn mở rộng, mỗi ngựa
// Dinh dưỡng và cữ nước khách chọn riêng cho từng ngựa (PRD mục 2.2). Gói cơ bản và cữ 3 giờ đã gồm trong cước; phí mỗi ngựa là số mẫu.
export const FEED_PACKAGE = {
  basic: { label: 'Gói cơ bản', fee: 0, items: ['Cỏ khô thường'] },
  advanced: { label: 'Gói nâng cao', fee: 300_000, items: ['Cỏ khô Timothy cao cấp', 'Yến mạch'] },
  sport: { label: 'Gói thể thao', fee: 500_000, items: ['Cỏ khô Timothy cao cấp', 'Yến mạch', 'Thức ăn bổ sung vitamin, khoáng'] },
} as const satisfies Record<string, { label: string; fee: number; items: readonly string[] }>
export type FeedPackageId = keyof typeof FEED_PACKAGE
export const FEED_PACKAGE_IDS = Object.keys(FEED_PACKAGE) as FeedPackageId[]
export const WATER_PLAN = {
  every_3h: { label: 'Mỗi 3 giờ', fee: 0, hint: 'Cấp nước tại mỗi trạm dừng.' },
  every_2h: { label: 'Mỗi 2 giờ', fee: 50_000, hint: 'Thêm một cữ giữa các chặng dài.' },
  every_1h: { label: 'Mỗi giờ', fee: 100_000, hint: 'Dành cho ngựa hay mất nước, ngày nóng.' },
} as const satisfies Record<string, { label: string; fee: number; hint: string }>
export type WaterPlanId = keyof typeof WATER_PLAN
export const WATER_PLAN_IDS = Object.keys(WATER_PLAN) as WaterPlanId[]
export const CLEARANCE_FEE = { domestic: 0, international: 300_000 } // phí thủ tục kiểm dịch & hải quan, cố định (PRD mục 11.2)
export const FUEL_BOT_PER_KM = 9_000 // nhiên liệu + BOT ước tính mỗi km (số mẫu)
export const FUEL_BUFFER_RATE = 0.05 // dự phòng trên nhiên liệu và BOT
export const MARGIN_RATE = 0.05 // biên lợi nhuận, gộp vào đơn giá, không hiện thành dòng riêng
export const DEMURRAGE_PER_HOUR = 400_000 // phí lưu xe chờ, chỉ khi lỗi phía khách (PRD mục 11.3: 300.000 – 500.000 đ/giờ)
export const INSURANCE_RATE_BOOKING = 0.02 // trên giá trị bảo hiểm của giống ngựa
// Giá trị bảo hiểm theo giống (VNĐ). Khách không tự khai; số mẫu, chờ bảng giá thật của nhà bảo hiểm.
export const BREED_INSURED_VALUE: Record<string, number> = {
  Thoroughbred: 1_000_000_000, Arabian: 800_000_000, 'Quarter Horse': 600_000_000, Warmblood: 900_000_000, Appaloosa: 500_000_000, Khác: 400_000_000,
}

// ===== Lộ trình: trạm nghỉ và Lệnh điều xe (Flow 1, 3, PRD mục 4.2) =====
export const MAX_CONTINUOUS_HOURS = 4 // ngựa không đi liên tục quá 3–4 giờ
export const TARGET_LEG_HOURS = 3.5
export const MIN_REST_MINUTES = 30
export const BORDER_WINDOW = { open: 7 * 60 + 30, close: 16 * 60 + 30 } // ETA cửa khẩu nên rơi vào 07:30–16:30

// ===== Hành trình và nhật ký (Flow 4, PRD mục 5) =====
export const DELAY_ALERT_MINUTES = 30 // trễ mốc 30–45 phút thì cờ vàng Delayed Check-in
export type WelfareCondition = 'normal' | 'stress' | 'sweating'
export const WELFARE_CONDITION: Record<WelfareCondition, { label: string; tone: Tone }> = {
  normal: { label: 'Bình thường', tone: 'success' },
  stress: { label: 'Căng thẳng (Stress)', tone: 'warning' },
  sweating: { label: 'Đổ mồ hôi nhiều', tone: 'warning' },
}

