// Đơn hàng. Trường lấy từ dữ liệu mẫu của CUS/don_cua_toi.js; khớp docs/PRD.md mục 8–9.
import type { DocKey, OptionKey, ProcedureKey } from '../config/documents'

export type OrderStatus =
  | 'processing' // chờ thẩm định
  | 'choose_option' // cần khách chọn phương án
  | 'rechecking' // đang kiểm tra lại (phương án D)
  | 'awaiting_payment'
  | 'paid'
  | 'in_transit'
  | 'delivered' // đã giao, chờ nghiệm thu
  | 'disputed' // khách báo vấn đề khi nghiệm thu
  | 'completed'
  | 'rejected'
  | 'cancelled'

export interface Horse { name: string; breed: string; sex: string; chip?: string }
export const horseLabel = (h: Horse) => `${h.name} (${h.breed}, ${h.sex})`

// [tên dịch vụ, lựa chọn, thành tiền]
export type ServiceLine = [name: string, detail: string, amount: number]

export interface Offer {
  issue: string
  affected: string[] // tên ngựa bị ảnh hưởng
  options: OptionKey[]
  custom?: { code: string; label: string; detail: string }[] // Manager tự thêm
  sentAt: number
  requoteServices: ServiceLine[] // giá mới cho phương án A
}

export interface Papers {
  originals: Record<string, Partial<Record<DocKey, number | null>>> // tên ngựa → giấy → lúc nhận bản gốc
  procedures: Partial<Record<ProcedureKey, { number: string; agency: string; issuedAt: number; validUntil?: number; file: string }>>
  handedAt?: number
}

export interface Checkpoint { label: string; place: string; time: number; state: 'done' | 'current' | 'next' }
// Nhật ký sức khỏe. horse/status/other/by/photo: báo cáo của hộ tống (trang Nhật ký sức khỏe ngựa; hộ tống không đo nhịp tim → heart = '—')
export interface HealthLog { time: number; temp: string; heart: string; note: string; horse?: string; status?: string; other?: string; by?: string; photo?: string }

export interface Trip {
  plate: string
  eta: number
  updatedAt: number
  contacts: [role: string, name: string, phone: string][]
  checkpoints: Checkpoint[]
  health: HealthLog[]
}

export interface Vitals { time: number; temp: number; heart: number; eat: string; body: string }
export interface Handover {
  vehicle: string
  driver: string
  groom: string // nhân viên chăm sóc
  inspector: string // kiểm dịch viên kiểm tra lúc nhận
  pickup: Vitals
  delivery: Vitals
}

// ===== Trường nội bộ (chỉ app nội bộ dùng; khách không thấy) =====
// Bước xử lý nội bộ khi đơn đang "Chờ thẩm định" phía khách
export type Stage = 'intake' | 'inspecting' | 'routing' | 'approval'

// Báo cáo vấn đề của kiểm dịch viên
export interface InspectionReport {
  inspector: string
  horses: string[]
  type: string
  disease?: string
  curable?: boolean | null
  note: string
  evidence?: string[]
}

// Việc chuyển lên Manager ở trang Tiếp nhận
export interface ManagerPending {
  kind: 'issue' | 'recheck' | 'expired' // issue: kiểm dịch báo vấn đề · recheck: khách chọn D · expired: khách không chọn trong 48 giờ
  at: number
  report: InspectionReport
  customerReason?: string
  customerFiles?: string[]
}

// Kết quả thẩm định trình Manager duyệt (trang Phê duyệt)
export interface Review { inspectNote: string; vehicle: string; driver: string; grooms: string }

// Theo dõi hạn xử lý của người phụ trách (trang Nhân sự & Điều chuyển)
export interface TaskTrack {
  step: 'inspector' | 'coordinator'
  assigneeId: string
  assignedAt: number
  pausedSince?: number // đang chờ khách bổ sung: đồng hồ dừng
  pausedWorkingDays: number
  history: { time: number; fromId: string; toId: string; reason: string; auto: boolean }[]
  specialDeadline?: number // Manager gia hạn đặc biệt
  reschedule?: { newDate: number; sentAt: number; note: string } // đã đề nghị khách dời ngày, chờ khách phản hồi
}

// Kết quả xác minh từng giấy của kiểm dịch viên (trang Xác minh hồ sơ). Giấy chưa có trong docs = chưa xác minh.
export interface DocDecision { decision: 'valid' | 'invalid'; reason: string }
export interface Verification {
  docs: Record<string, Partial<Record<DocKey, DocDecision>>> // tên ngựa → giấy → kết luận
  result?: 'passed' | 'reported'
  closedAt?: number
  requestedAt?: number // lúc gửi yêu cầu khách bổ sung
  requestOriginal?: boolean // yêu cầu nộp bản gốc để đối chiếu
  customerMessage?: string
}

// Điều phối đánh giá tuyến không khả thi → trả Manager (trang Phê duyệt)
export interface Infeasible { note: string; at: number; by: string }

// Kiểm dịch viên báo cáo khi chuẩn bị giấy tờ chuyến đi
export interface PapersReport { at: number; type: string; items: string[]; note: string }

export interface Order {
  id: string
  customer: string
  customerEmail: string
  phoneLast4: string
  submittedAt: number
  departAt: number
  from: string
  to: string
  routeShort: string
  border: string | null // cửa khẩu, null = nội địa
  distance: string
  duration: string
  horses: Horse[]
  vehicle?: string
  stops?: string[]
  services: ServiceLine[]
  status: OrderStatus
  approvedAt?: number
  paidAt?: number
  deliveredAt?: number
  rejectedStep?: 0 | 1 | 2 // 0 = Tiếp nhận, 1 = Kiểm dịch, 2 = Phê duyệt (khách thấy 1 và 2 là "Thẩm định hồ sơ")
  rejectedAt?: number
  cancelledAt?: number
  reason?: string
  note?: string // ghi chú hiện cho khách sau khi chọn phương án
  recheckAt?: number
  offer?: Offer
  choice?: { key: string; at: number; note?: string }
  papers?: Papers
  trip?: Trip
  handover?: Handover // thông tin bàn giao khi giao ngựa
  acceptedAt?: number
  acceptedBy?: 'customer' | 'auto'
  issue?: { time: number; type: string; note: string; files: string[] } // khách báo vấn đề khi nghiệm thu

  // ----- nội bộ -----
  stage?: Stage
  inspector?: string
  coordinator?: string
  intakeAt?: number // lúc Manager tiếp nhận đơn
  customerNote?: string
  hold?: string // chỗ xe giữ tạm khi đặt đơn
  warning?: string // cảnh báo hệ thống (vd. nghi trùng đơn)
  waitingCustomer?: boolean // kiểm dịch đang chờ khách bổ sung giấy tờ
  pending?: ManagerPending
  rejectType?: string
  report?: InspectionReport // báo cáo kiểm dịch của đơn đã từ chối ở bước thẩm định
  rechecked?: boolean // đã dùng quyền kiểm tra lại (phương án D)
  review?: Review
  task?: TaskTrack
  papersReport?: PapersReport
  verification?: Verification
  recheckRequest?: { customerReason: string; customerFiles: string[] } // khách chọn phương án D: lý do gửi kèm
  infeasible?: Infeasible
}

export const orderTotal = (o: Pick<Order, 'services'>) => o.services.reduce((t, s) => t + s[2], 0)
