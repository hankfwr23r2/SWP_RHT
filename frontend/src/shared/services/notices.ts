// Thông báo đẩy xuống từng vai trò (chuông trên thanh menu). Chỉ báo tin, không ghi gì vào nhật ký đơn.
// Người nhận: theo vai trò (Manager: mọi Manager) hoặc theo tên người (Specialist, Coordinator, Driver, Escort, khách).
// Tên hàm theo REST để sau này thay bằng fetch('/api/notices').
import type { Role } from '../types/role'
import { createStore } from './store'

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

const store = createStore<Notice>('notices', () => [])
let seq = 0

// Chỉ dùng trong services khi sự việc xảy ra
export function pushNotice(to: { role: Role; name?: string }, n: { title: string; text: string; link?: string; bookingId?: string }) {
  store.add({ id: `N-${Date.now()}-${++seq}`, role: to.role, name: to.name, ...n, at: Date.now(), read: false })
}

const seqOf = (n: Notice) => Number(n.id.split('-')[2])
const mine = (n: Notice, role: Role, name?: string) => n.role === role && (!n.name || n.name === name)

export const noticesApi = {
  list: async (role: Role, name?: string): Promise<Notice[]> =>
    structuredClone(store.all().filter(n => mine(n, role, name))).sort((a, b) => b.at - a.at || seqOf(b) - seqOf(a)),
  markRead: async (id: string) => { store.update(id, { read: true }) },
  markAllRead: async (role: Role, name?: string) => { store.all().filter(n => mine(n, role, name) && !n.read).forEach(n => store.update(n.id, { read: true })) },
}
