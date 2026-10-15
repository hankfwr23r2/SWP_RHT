export type IncidentStatus = 'open' | 'proposed' | 'approved' | 'rejected'

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
