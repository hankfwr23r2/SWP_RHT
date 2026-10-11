// Tài khoản hệ thống mẫu: quản trị viên, quản lý, nhân viên (từ danh bạ), tài xế, hộ tống và khách hàng.
// Số mẫu; khi có backend, tài khoản và mật khẩu (băm) nằm ở server.
import { HOUR, DAY } from '../../config/business-rules'
import type { Role } from '../../types/role'
import { seedCrew } from './fleet'
import { seedStaff } from './staff'

export type AccountStatus = 'active' | 'locked'
export interface AccountLog { time: number; actor: string; text: string }
export interface Account {
  id: string
  name: string
  email: string
  phone: string
  role: Role
  status: AccountStatus
  createdAt: number
  lastLoginAt?: number
  password?: string // chỉ có khi quản trị viên tạo hoặc đặt lại; tài khoản mẫu nhận mọi mật khẩu
  history: AccountLog[]
}

export const ADMIN_EMAIL = 'admin@equine.vn'
const slug = (name: string) => name.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase().replace(/[^a-z0-9]+/g, '.').replace(/^\.|\.$/g, '')

export const seedAccounts = (): Account[] => {
  const now = Date.now()
  const base = (i: number) => now - (400 - i * 7) * DAY
  const rows: Omit<Account, 'id' | 'history' | 'createdAt'>[] = [
    { name: 'Quản trị viên', email: ADMIN_EMAIL, phone: '0900 000 001', role: 'admin', status: 'active', lastLoginAt: now - 1 * HOUR },
    { name: 'Quản lý', email: 'manager@equine.vn', phone: '0900 000 002', role: 'manager', status: 'active', lastLoginAt: now - 3 * HOUR },
    ...seedStaff().map(s => ({
      name: s.name, phone: s.phone, role: (s.role === 'inspector' ? 'specialist' : 'coordinator') as Role, status: 'active' as const,
      email: s.name === 'Phạm Văn Hưng' ? 'specialist@equine.vn' : s.name === 'Trần Minh' ? 'ops@equine.vn' : `${slug(s.name)}@equine.vn`,
      lastLoginAt: now - (s.id.length + s.name.length) * HOUR,
    })),
    ...seedCrew().map(c => ({
      name: c.name, phone: c.phone, role: c.role as Role, status: 'active' as const,
      email: c.name === 'Nguyễn Văn Hùng' ? 'driver@equine.vn' : c.name === 'Võ Thị Lan' ? 'escort@equine.vn' : `${slug(c.name)}@equine.vn`,
      lastLoginAt: now - (c.name.length * 2) * HOUR,
    })),
    { name: 'Trang trại Long Thành', email: 'longthanh.farm@gmail.com', phone: '0901 456 789', role: 'customer', status: 'active', lastLoginAt: now - 5 * HOUR },
    { name: 'CLB Ngựa Phương Nam', email: 'phuongnam.club@gmail.com', phone: '0912 345 678', role: 'customer', status: 'locked', lastLoginAt: now - 6 * DAY },
  ]
  return rows.map((r, i) => {
    const createdAt = base(i)
    const history: AccountLog[] = [{ time: createdAt, actor: r.role === 'customer' ? r.name : 'Quản trị viên', text: r.role === 'customer' ? 'Đăng ký tài khoản' : 'Tạo tài khoản' }]
    if (r.status === 'locked') history.push({ time: now - 5 * DAY, actor: 'Hệ thống', text: 'Khóa tài khoản: quá hạn thanh toán bảng quyết toán (PRD mục 8.2)' })
    return { ...r, id: `TK-${String(i + 1).padStart(3, '0')}`, createdAt, history }
  })
}
