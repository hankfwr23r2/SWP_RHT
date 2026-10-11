// Đơn đặt chuyến theo quy trình mới (Flow 1). Khớp docs/PRD.md mục 2, 13. Các luồng 2–6 sẽ thêm trạng thái và trường vào đây.
import type { BookingStatus, FeedPackageId, WaterPlanId, ClearanceDocType, ExpenseCategory, HorseDocType, IncidentAction, IncidentKind, IncidentStatus, Payer, WelfareCondition } from '../config/booking-rules'
import type { CountryCode, GeoPoint } from '../config/network'

export type Sex = 'stallion' | 'mare' | 'gelding'
export const SEX_LABEL: Record<Sex, string> = { stallion: 'Đực', mare: 'Cái', gelding: 'Thiến' }

// ===== Hồ sơ ngựa =====
export interface HorseDoc { fileName: string; uploadedAt: number; expiresAt?: number }
export interface HorseProfile {
  id: string
  owner: string
  name: string
  microchip: string // khóa định danh, không sửa sau khi lưu
  breed: string
  sex: Sex
  color: string
  birthYear: number
  marks: string
  docs: Partial<Record<HorseDocType, HorseDoc>>
  completedTrips: number
  createdAt: number
}

// ===== Đơn =====
export type TransportType = 'domestic' | 'international'
export interface PlaceRef { id: string; name: string; country: CountryCode }
export interface Party { name: string; phone: string; idNumber: string; address: string }
export type StallType = 'standard' | 'single'

export interface BookingHorse {
  horseId: string
  name: string
  microchip: string
  breed: string
  sex: Sex
  stall: StallType
  feedPackage: FeedPackageId // gói thức ăn
  waterPlan: WaterPlanId // cữ nước
  insurance: { opted: boolean }
}

export interface StaffRef { id: string; name: string }

// Manager trả đơn về Kiểm dịch viên (duyệt lại hồ sơ ngựa) hoặc Điều phối viên (làm lại xe và lộ trình)
export interface SentBack { to: 'specialist' | 'coordinator'; reason: string; at: number; by: string }
export interface MedicalReview {
  status: 'pending' | 'approved' | 'resubmit'
  at?: number
  by?: string
  resubmit?: { reason: string; items: { horseId: string; doc: HorseDocType }[]; at: number }
}

export interface QuoteLine { label: string; detail: string; amount: number }
export interface Adjustment { label: string; amount: number } // dương: phụ phí; âm: chiết khấu
export interface Quote {
  lines: QuoteLine[]
  adjustments: Adjustment[]
  subtotal: number
  total: number
  deposit: number // 30%
  balance: number // 70% còn lại, trả ngày D
  sentAt: number
  expiresAt: number
  sentBy: string
}

// ===== Giấy tờ pháp lý do Specialist làm (Flow 2) =====
export type ClearanceStatus = 'todo' | 'done' // Chưa nộp / Đã nộp (nộp cho cơ quan chức năng)
export interface ClearanceItem { type: ClearanceDocType; status: ClearanceStatus; note: string; photos: string[]; updatedAt?: number; by?: string }
export interface Clearance {
  items: ClearanceItem[]
  horsesCleared: string[] // horseId đã có giấy thông quan (quốc tế)
  acceptedAt?: number // Specialist tiếp nhận Vận đơn
  acceptedBy?: string
  doneAt?: number
  doneBy?: string
}

// ===== Lộ trình chi tiết và Trip Manifest (Flow 3) =====
export interface RouteLeg { no: number; from: string; to: string; departAt: number; arriveAt: number }
// Trạm nghỉ (checkpoint dọc tuyến): ngựa dừng tối thiểu 30 phút, Escort ghi nhật ký an sinh
export interface RestStop { afterLeg: number; name: string; minutes: number }
export interface RoutePlan {
  legs: RouteLeg[]
  rests: RestStop[]
  borderEta?: number // quốc tế: giờ tới cửa khẩu
  completedAt?: number
  by?: string
}

// ===== Hành trình thực tế (Flow 4) =====
export type CheckpointType = 'pickup' | 'rest' | 'border' | 'customs' | 'delivery'
export interface Checkpoint {
  id: string // pickup, rest-1, border, customs, delivery
  type: CheckpointType
  label: string
  place: string
  plannedAt: number
  arrivedAt?: number // Driver check-in tại mốc (kèm ảnh chụp trực tiếp)
  leftAt?: number // customs: Driver bấm tiếp tục hành trình, rời cửa khẩu
  doneAt?: number // mốc hoàn tất: xuất phát (pickup), tiếp tục hành trình (rest), thông quan, giao xong
  photo?: string
  by?: string
  chips?: string[] // pickup: microchip đã quét
  originals?: string[] // pickup: bản gốc đã thu
  handoverPhoto?: string // pickup, delivery: ảnh biên bản có chữ ký hai bên
  stampPhotos?: string[] // customs: ảnh mộc đỏ Health Cert, ATA Carnet
  returnedOriginals?: boolean // delivery: đã trả hồ sơ gốc
}
export interface WelfareLog {
  id: string
  checkpointId: string
  at: number
  by: string
  condition: WelfareCondition
  waterLiters: number
  hay: boolean
  photo: string
  note: string
}
export interface TripRun { checkpoints: Checkpoint[]; welfare: WelfareLog[]; startedAt?: number; deliveredAt?: number }

// ===== Mỗi xe của đơn là một chuyến (PRD mục 1.4, 10.2) =====
export interface DriverPack { items: string[]; at: number; by: string } // Coordinator nhập cho Driver mang theo
export interface VehicleTrip {
  tripId: string // TRP-NNNN-1, TRP-NNNN-2...
  vehicleId: string
  driverId: string
  escortId: string
  horseIds: string[]
  acks: { driver?: number; escort?: number } // nhận Lệnh điều xe trên app
  driverPack?: DriverPack
  departedAt?: number // Driver bấm bắt đầu đến điểm đón
  run?: TripRun
}
export interface Waybill { no: string; issuedAt: number }
export interface PlanConfirmed { at: number; by: string; note: string }

// Nhà xe từ chối đơn: Manager (lúc tiếp nhận) hoặc Coordinator (không duyệt phương án xe, lộ trình)
export interface Rejection { at: number; by: string; role: 'manager' | 'coordinator'; reason: string }

export interface Cancellation { at: number; reason: string; by: 'customer' | 'manager'; refund: number } // refund: số tiền trả lại khách

export interface Payment { paidAt: number; amount: number; reference: string }

// ===== Sự cố (Flow 5) và quyết toán (Flow 6) =====
export interface IncidentExpense { id: string; category: ExpenseCategory; label: string; photo: string; amount: number; payer: Payer; at: number; by: string }
// Một đoạn đường vẽ trên bản đồ (đã lấy theo đường bộ): các điểm, quãng đường, thời gian lái
export interface PlanLine { path: [lat: number, lng: number][]; km: number; hours: number }
export interface IncidentPlan {
  action: IncidentAction
  note: string
  newEta: number
  at: number
  by: string
  station?: string // trạm nghỉ đưa ngựa tới (sức khỏe ngựa, xe gặp sự cố)
  restMinutes?: number // thời gian nghỉ ngựa tại trạm
  rescue?: { name: string; phone: string; lat: number; lng: number } // điểm cứu hộ được gọi (xe gặp sự cố)
  rescueLine?: PlanLine // đường cứu hộ chạy tới chỗ xe
  toStation?: PlanLine // đường đưa ngựa từ chỗ xe tới trạm nghỉ
  detour?: PlanLine // lộ trình mới đi tiếp (giao thông tắc nghẽn)
}
export interface Incident {
  id: string // INC-NNNN-i
  tripId: string
  kind: IncidentKind
  reportedBy: string
  reportedAt: number
  location: GeoPoint // vị trí xe lúc báo sự cố (bản thử: mô phỏng theo tiến độ hành trình; có app thật thì lấy GPS)
  photo: string
  note: string
  status: IncidentStatus
  plan?: IncidentPlan
  rejection?: { reason: string; at: number; by: string }
  approval?: { at: number; by: string }
  fitConfirmedAt?: number // Escort xác nhận ngựa đủ sức đi tiếp (sự cố sức khỏe)
  resolvedAt?: number
  expenses: IncidentExpense[]
}
export interface SettlementItem { label: string; amount: number; photo?: string }
export interface Settlement { items: SettlementItem[]; total: number; issuedAt: number; dueAt: number; by: string; paid?: Payment }
export interface Rating { trip: number; driver: number; escort: number; comment: string; at: number }
export interface HistoryEntry { time: number; actor: string; text: string }

export interface Booking {
  id: string // ORD-2026-NNNN
  customer: string
  createdAt: number
  type: TransportType
  origin: PlaceRef
  dest: PlaceRef
  gate?: string // cửa khẩu do Coordinator chọn khi chốt lộ trình (chỉ quốc tế), khóa theo đơn; khách không chọn
  departAt: number
  consignor: Party
  consignee: Party
  horses: BookingHorse[]
  status: BookingStatus

  // ----- nội bộ (khách không thấy) -----
  intake?: { at: number; by: string; specialist: StaffRef; coordinator: StaffRef }
  history: HistoryEntry[]

  // ----- kết quả từng bước -----
  medical?: MedicalReview
  trips?: VehicleTrip[] // Coordinator chọn khi chốt phương án; chưa chốt thì chưa có
  plan?: PlanConfirmed // Coordinator đã xác nhận xe và lộ trình
  sentBack?: SentBack // Manager trả lại để làm lại; xóa khi người đó làm xong
  route?: RoutePlan // một lộ trình dùng chung cho mọi xe
  quote?: Quote
  payment?: Payment // cọc 30%
  waybill?: Waybill
  clearance?: Clearance
  balance?: Payment // 70% ngày D
  incidents?: Incident[]
  expensesSubmittedAt?: number // Driver đã gửi bảng kê chi phí sau giao
  settlement?: Settlement
  rating?: Rating
  cancellation?: Cancellation
  rejection?: Rejection
}
