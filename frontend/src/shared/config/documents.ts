// Giấy tờ và phương án xử lý hồ sơ. Khớp docs/PRD.md mục 5–6.

export type DocKey = 'passport' | 'vaccine' | 'lab' | 'import_permit' | 'ownership'

// Giấy khách nộp khi đặt đơn
export const DOC_LABEL: Record<DocKey, string> = {
  passport: 'Hộ chiếu ngựa / Microchip',
  vaccine: 'Giấy chứng nhận tiêm phòng',
  lab: 'Kết quả xét nghiệm EIA & cúm ngựa',
  import_permit: 'Giấy phép nhập khẩu của nước đến',
  ownership: 'Giấy tờ chứng minh sở hữu',
}
export const DOCS_DOMESTIC: DocKey[] = ['passport', 'vaccine', 'ownership']
export const DOCS_CROSS_BORDER: DocKey[] = ['passport', 'vaccine', 'lab', 'import_permit', 'ownership']
export const FILE_PREFIX: Record<DocKey, string> = {
  passport: 'Ho_chieu', vaccine: 'Tiem_phong', lab: 'Xet_nghiem', import_permit: 'Giay_phep_NK', ownership: 'So_huu',
}
export const requiredDocs = (border: boolean) => (border ? DOCS_CROSS_BORDER : DOCS_DOMESTIC)

// Giấy do cơ quan chức năng cấp, kiểm dịch viên xin sau khi khách thanh toán
export type ProcedureKey = 'quarantine_domestic' | 'quarantine_border' | 'customs'
export const PROCEDURES: Record<ProcedureKey, { label: string; numberLabel: string; agencyHint: string; hasValidity: boolean }> = {
  quarantine_domestic: { label: 'Giấy chứng nhận kiểm dịch động vật vận chuyển ra khỏi tỉnh', numberLabel: 'Số giấy chứng nhận', agencyHint: 'Ví dụ: Chi cục Chăn nuôi và Thú y Hà Nội', hasValidity: true },
  quarantine_border: { label: 'Giấy chứng nhận kiểm dịch động vật xuất/nhập khẩu', numberLabel: 'Số giấy chứng nhận', agencyHint: 'Ví dụ: Cơ quan Thú y vùng VI', hasValidity: true },
  customs: { label: 'Tờ khai hải quan (điện tử)', numberLabel: 'Số tờ khai', agencyHint: 'Ví dụ: Chi cục Hải quan cửa khẩu Mộc Bài', hasValidity: false },
}
export const proceduresFor = (border: boolean): ProcedureKey[] => (border ? ['quarantine_border', 'customs'] : ['quarantine_domestic'])

// Giấy bàn giao cho khách khi giao ngựa (trang Nghiệm thu)
export const HANDOVER_DOCS_DOMESTIC = ['Hộ chiếu ngựa (bản gốc)', 'Giấy chứng nhận kiểm dịch vận chuyển nội địa']
export const HANDOVER_DOCS_CROSS_BORDER = ['Hộ chiếu ngựa (bản gốc)', 'Giấy chứng nhận kiểm dịch xuất / nhập khẩu', 'Tờ khai hải quan cửa khẩu']

// Phương án cho khách khi hồ sơ có vấn đề không khắc phục được
export type OptionKey = 'remove_horse' | 'replace_horse' | 'postpone' | 'recheck' | 'cancel'
export const OPTIONS: Record<OptionKey, { code: string; label: string }> = {
  remove_horse: { code: 'A', label: 'Bỏ ngựa có vấn đề, chở các con còn lại' },
  replace_horse: { code: 'B', label: 'Thay bằng ngựa khác' },
  postpone: { code: 'C', label: 'Dời ngày khởi hành để điều trị và xét nghiệm lại' },
  recheck: { code: 'D', label: 'Kiểm tra lại (kiểm dịch viên khác)' },
  cancel: { code: 'E', label: 'Hủy đơn miễn phí' },
}
// Cách gọi phương án phía khách (Đơn của tôi)
export const CUSTOMER_OPTION_LABEL: Record<OptionKey, string> = {
  remove_horse: 'Bỏ ngựa có vấn đề, chở các con còn lại',
  replace_horse: 'Thay bằng ngựa khác',
  postpone: 'Dời ngày khởi hành để điều trị và xét nghiệm lại',
  recheck: 'Yêu cầu kiểm tra lại hồ sơ',
  cancel: 'Hủy đơn',
}

// Loại vấn đề kiểm dịch viên báo cáo
export const ISSUE_POSITIVE = 'Xét nghiệm dương tính bệnh truyền nhiễm'
export const INSPECTION_ISSUE_TYPES = [ISSUE_POSITIVE, 'Đã yêu cầu bổ sung nhưng giấy tờ vẫn không đạt', 'Khác']
export const PAPERS_REPORT_TYPES = [
  'Khách chưa gửi bản gốc đúng hạn',
  'Bản gốc không khớp bản scan đã xác minh',
  'Cơ quan chức năng không cấp hoặc chậm cấp giấy',
  'Giấy được cấp hết hiệu lực trước ngày giao',
  'Khác',
]

// Nghiệm thu
export const ACCEPTANCE_ISSUE_TYPES = ['Ngựa bị thương / trầy xước', 'Chỉ số sức khỏe bất thường', 'Thiếu hoặc sai giấy tờ', 'Giao nhầm ngựa', 'Khác']
// Ngưỡng tham khảo cho ngựa trưởng thành khi nghỉ
export const NORMAL_RANGE = { temp: [37.5, 38.5], heart: [28, 44] } as const
