// Quy tắc nghiệp vụ dùng chung. Khớp docs/PRD.md. Quy tắc riêng của luồng đặt đơn (Flow 1): xem booking-rules.ts.
// Giá trị chuyển nguyên từ code HTML cũ (don_cua_toi.js, manager_*.js, kiem_dich.js, thu_tuc.js, acceptance.js).

export const HOUR = 60 * 60 * 1000
export const DAY = 24 * HOUR

// ===== Lịch làm việc =====
// Thứ Hai – thứ Sáu, 08:00 – 17:00, trừ ngày lễ.
// Ngày lễ âm lịch (Tết Nguyên đán, Giỗ Tổ) và ngày nghỉ liền kề Quốc khánh thay đổi theo năm:
// cập nhật danh sách này theo thông báo chính thức hằng năm.
export const WORK_END_HOUR = 17
export const HOLIDAYS = ['2026-01-01', '2026-04-30', '2026-05-01', '2026-09-02', '2027-01-01']
export const WEEKDAYS = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7']

// ===== Đặt đơn =====
export const MIN_LEAD_DAYS = 30 // PRD mục 1.4: đặt trước ngày khởi hành tối thiểu 30 ngày
export const MAX_HORSES = 9 // 1 đơn = 1 xe; xe Heavy lớn nhất có 9 ngăn

// ===== Thẩm định (cam kết với khách) =====
// Hạn = mốc SỚM HƠN của (1) 17:00 ngày làm việc thứ APPRAISAL_WORKING_DAYS sau ngày gửi đơn
// (kiểm dịch 2 ngày + lập lộ trình 2 ngày + duyệt 1 ngày) và (2) 17:00 của (khởi hành − APPRAISAL_CAP_DAYS).
export const APPRAISAL_WORKING_DAYS = 5
export const APPRAISAL_CAP_DAYS = 4
// Quá hạn thẩm định → "Ưu tiên": cam kết mới = 17:00 ngày làm việc kế tiếp sau hạn cũ.
export const PRIORITY_WORKING_DAYS = 1

// ===== Hạn xử lý nội bộ theo vai trò =====
// Hạn = mốc SỚM HƠN của (1) 17:00 ngày làm việc thứ N, đếm từ ngày làm việc kế tiếp sau ngày nhận việc
// và (2) 17:00 của (khởi hành − CAP_DAYS). Đồng hồ tạm dừng khi chờ khách bổ sung.
export const SLA_WORKING_DAYS = { inspector: 2, coordinator: 2 } as const
export const CAP_DAYS = { inspector: 7, coordinator: 5 } as const

// ===== Phương án cho khách khi hồ sơ có vấn đề =====
export const CHOICE_HOURS = 48

// ===== Thanh toán =====
// Hạn = mốc SỚM HƠN của (duyệt + PAYMENT_HOURS) và 17:00 của (khởi hành − PAYMENT_CAP_DAYS).
export const PAYMENT_HOURS = 48
export const PAYMENT_CAP_DAYS = 2
export const URGENT_HOURS = 12 // còn ít hơn số giờ này thì cảnh báo đỏ

// ===== Giấy tờ chuyến đi (đơn đã thanh toán) =====
export const ORIGINALS_DUE_DAYS = 3 // khách gửi bản gốc trước 17:00 ngày D − 3
export const ORIGINALS_DUE_HOUR = 17
export const HANDOVER_DUE_DAYS = 2 // kiểm dịch viên bàn giao điều phối trước 12:00 ngày D − 2
export const HANDOVER_DUE_HOUR = 12

// ===== Nghiệm thu =====
export const ACCEPTANCE_HOURS = 24
export const ISSUE_RESPONSE_HOURS = 4

// ===== Liên hệ, thanh toán =====
export const HOTLINE = '1900 6868'
export const OFFICE_ADDRESS = 'Văn phòng Vận chuyển Ngựa, 120 Xô Viết Nghệ Tĩnh, TP.HCM'
export const BANK = { name: 'Vietcombank', account: '0071 000 123 456', owner: 'CONG TY TNHH EQUINEZ LOGISTICS' }

// ===== Điều khoản và chính sách (PRD mục 11.5): mỗi văn bản là một tệp ảnh hoặc PDF, xem ở trang /terms =====
// Thay tệp trong public/policies rồi đổi đường dẫn ở đây (đuôi .pdf thì hiện bằng khung PDF, còn lại hiện như ảnh).
export const POLICY_DOCS = {
  transport: { title: 'Điều khoản vận chuyển', file: '/policies/dieu-khoan-van-chuyen.svg' },
  incident_cost: { title: 'Chính sách chi phí khi có sự cố', file: '/policies/chinh-sach-chi-phi-su-co.svg' },
  liability: { title: 'Điều khoản Trách nhiệm Hạn chế', file: '/policies/dieu-khoan-trach-nhiem-han-che.svg' },
} as const
export type PolicyDocId = keyof typeof POLICY_DOCS
