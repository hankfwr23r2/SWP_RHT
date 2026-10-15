import type { Role } from './role'

export type AccountStatus = 'active' | 'locked'

export interface AccountLog {
  time: number
  actor: string
  text: string
}

export interface Account {
  id: string
  name: string
  email: string
  phone: string
  role: Role
  status: AccountStatus
  createdAt: number
  lastLoginAt?: number
  password?: string // chỉ có khi quản trị viên tạo hoặc đặt lại
  history: AccountLog[]
}

export const ADMIN_EMAIL = 'admin@equine.vn'
