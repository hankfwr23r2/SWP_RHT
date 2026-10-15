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

export interface ReportLeg {
  title: string
  kind: 'ground' | 'gate'
  meta: string
  route: string
  planned: [string, string]
  actual: [string, string]
}

export interface TripReport {
  id: string // mã đơn
  tripId: string
  customer: string
  route: string
  type: string
  horses: string
  escort: string
  completedAt: number
  plannedAt: number
  otd: 'on_time' | 'early' | 'late'
  otdMinutes: number
  incidentCost: number
  incidentNote: string
  totalTime: string
  legs: ReportLeg[]
  health: [time: string, event: string, ok: boolean][]
  events: [time: string, event: string, ok: boolean][]
  rating: number
  feedback: string
}
