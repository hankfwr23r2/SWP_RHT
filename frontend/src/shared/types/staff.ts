export interface StaffMember {
  id: string
  name: string
  phone: string
  role: 'inspector' | 'coordinator'
  status: 'working' | 'off'
  offTo?: number
  offReason?: string
  kpi: { late: number; transferredOut: number } // số liệu trong tháng
}
