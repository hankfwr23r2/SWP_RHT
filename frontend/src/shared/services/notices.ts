// Thông báo đẩy xuống từng vai trò (chuông trên thanh menu). Backend chưa có API thông báo nên chuông luôn trống.
import type { Role } from '../types/role'

export interface Notice {
  id: string
  role: Role
  name?: string // bỏ trống = mọi người thuộc vai trò (Manager)
  title: string
  text: string
  link?: string // đường dẫn trong app của người nhận
  bookingId?: string
  at: number
  read: boolean
}

export const noticesApi = {
  list: async (_role: Role, _name?: string): Promise<Notice[]> => [],
  markRead: async (_id: string): Promise<void> => {},
  markAllRead: async (_role: Role, _name?: string): Promise<void> => {},
}
