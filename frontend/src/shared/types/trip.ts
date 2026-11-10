export interface Leg {
  no: number
  from: string
  to: string
  vehicleId: string
  driverId: string
  escortId: string
}

export interface OpsTrip {
  id: string
  orderId: string
  feasible?: boolean // kết quả khảo sát (chưa có = chờ khảo sát)
  assessNote: string
  assessedAt?: number
  legs: Leg[]
  activity: { time: number; text: string }[] // nhật ký hiện trường (ảnh chặng) ở trang Giám sát
}

// Báo cáo chuyến dựng từ dữ liệu thật; trường nào chuyến chưa có thì null
export interface ReportLeg {
  title: string
  kind: 'ground' | 'gate'
  meta: string
  route: string
  plannedStart: number | null // mốc thời gian ms
  plannedEnd: number | null
  actualStart: number | null
  actualEnd: number | null
}

export interface TripReport {
  id: string // mã đơn
  tripId: string | null
  customer: string | null
  route: string
  type: string
  horses: string
  escort: string | null
  completedAt: number | null // giờ giao ngựa thực tế
  plannedAt: number | null // giờ giao dự kiến
  otd: 'on_time' | 'early' | 'late' | null
  otdMinutes: number | null
  incidentCost: number
  tollCost?: number // phí cầu đường theo hóa đơn tài xế ghi (nhà xe chịu)
  tolls?: { id: string; amount: number; station: string; photo: string; at: number; by: string }[]
  incidentNote: string
  totalTime: string | null
  legs: ReportLeg[]
  health: [at: number, event: string, ok: boolean][]
  events: [at: number, event: string, ok: boolean][]
  rating: number | null // khách chưa chấm thì null
  feedback: string | null
}
