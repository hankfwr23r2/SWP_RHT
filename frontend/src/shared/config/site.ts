// Dữ liệu khởi động của trang (không phải quy tắc nghiệp vụ).

// Hotline và địa chỉ dự phòng, hiện trong lúc chờ API /contact trả giá trị quản trị viên đặt (useContact).
export const HOTLINE = '1900 6868'
export const OFFICE_ADDRESS = 'Văn phòng Vận chuyển Ngựa, 120 Xô Viết Nghệ Tĩnh, TP.HCM'

// Điều khoản và chính sách: mỗi văn bản là một tệp ảnh hoặc PDF, xem ở trang /terms.
// Thay tệp trong public/policies rồi đổi đường dẫn ở đây (đuôi .pdf thì hiện bằng khung PDF, còn lại hiện như ảnh).
export const POLICY_DOCS = {
  transport: { title: 'Điều khoản vận chuyển', file: '/policies/dieu-khoan-van-chuyen.pdf' },
  incident_cost: { title: 'Chính sách chi phí khi có sự cố', file: '/policies/chinh-sach-chi-phi-su-co.pdf' },
  liability: { title: 'Điều khoản Trách nhiệm Hạn chế', file: '/policies/dieu-khoan-trach-nhiem-han-che.pdf' },
} as const
export type PolicyDocId = keyof typeof POLICY_DOCS
