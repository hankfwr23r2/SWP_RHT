// Nhân sự nội bộ dùng chung mọi trang (gốc: manager_phan_cong.js, manager_tiep_nhan.js, kiem_dich.js, ops_data.js).
import { atHour, shiftWorkingDays } from '../../lib/dates'
import type { StaffRole } from '../../types/role'

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

// Kiểm dịch viên (vai trò đăng nhập: specialist) và điều phối viên (coordinator)
export const seedStaff = (): StaffMember[] => [
  { id: 'KD-01', name: 'Phạm Văn Hưng', phone: '0901 234 567', role: 'inspector', status: 'working', kpi: { late: 2, transferredOut: 1 } },
  { id: 'KD-02', name: 'Nguyễn Thị Thu', phone: '0902 345 678', role: 'inspector', status: 'working', kpi: { late: 1, transferredOut: 1 } },
  { id: 'DP-01', name: 'Trần Minh', phone: '0903 456 789', role: 'coordinator', status: 'working', kpi: { late: 3, transferredOut: 1 } },
  { id: 'DP-02', name: 'Lê Quang', phone: '0904 567 890', role: 'coordinator', status: 'off', offTo: atHour(shiftWorkingDays(Date.now(), 2), 23), offReason: 'Ốm', kpi: { late: 0, transferredOut: 0 } },
  { id: 'DP-03', name: 'Phạm Tâm', phone: '0905 678 901', role: 'coordinator', status: 'working', kpi: { late: 1, transferredOut: 0 } },
]

// Tài khoản đăng nhập giả lập: email chứa từ khóa vai trò (giữ đúng cách của Staffs/staff_login.js)
export const LOGIN_KEYWORDS: [keyword: string, role: StaffRole, name: string][] = [
  ['admin', 'admin', 'Quản trị viên'],
  ['manager', 'manager', 'Quản lý'],
  ['driver', 'driver', 'Nguyễn Văn Hùng'],
  ['escort', 'escort', 'Võ Thị Lan'],
  ['specialist', 'specialist', 'Phạm Văn Hưng'],
  ['ops', 'coordinator', 'Trần Minh'],
  ['fleet', 'coordinator', 'Trần Minh'],
  ['route', 'coordinator', 'Trần Minh'],
  ['coordinator', 'coordinator', 'Trần Minh'],
]
