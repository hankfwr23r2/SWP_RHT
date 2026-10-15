// Service nhân sự nội bộ (kiểm dịch viên, điều phối viên). Sau này: GET/PATCH /api/staff
import type { Order } from '../types/order'
import { backendFleetApi } from '../api/fleet'
import { seedStaff } from '../fixtures/staff'
import type { StaffMember } from '../types/staff'
import { createStore } from './store'

const testStore = createStore<StaffMember>('staff', () => import.meta.env?.MODE === 'test' ? seedStaff() : [])
const isTestEnv = import.meta.env?.MODE === 'test'

export const staffApi = {
  list: async (): Promise<StaffMember[]> => isTestEnv ? structuredClone(testStore.all()) : (await backendFleetApi.getCrew()).flatMap(member => {
    const role = member.role.toUpperCase()
    if (role !== 'SPECIALIST' && role !== 'INSPECTOR' && role !== 'COORDINATOR') return []
    return [{
      id: member.staffCode || String(member.userId),
      name: member.name,
      phone: member.phone || '',
      role: role === 'COORDINATOR' ? 'coordinator' : 'inspector',
      status: member.employmentStatus.toUpperCase() === 'ACTIVE' ? 'working' : 'off',
    }]
  }),
  update: async (id: string, patch: Partial<StaffMember>) => {
    if (isTestEnv) return structuredClone(testStore.update(id, patch))
    throw new Error('Cập nhật nhân sự phải thực hiện qua API quản lý tài khoản.')
  },
  create: async (data: Pick<StaffMember, 'name' | 'phone' | 'role'>) => {
    if (isTestEnv) {
      const prefix = data.role === 'inspector' ? 'KD' : 'DP'
      const next = Math.max(0, ...testStore.all().filter(s => s.id.startsWith(prefix)).map(s => Number(s.id.slice(3)))) + 1
      return structuredClone(testStore.add({ id: `${prefix}-${String(next).padStart(2, '0')}`, ...data, status: 'working', kpi: { late: 0, transferredOut: 0 } }))
    }
    throw new Error('Tạo nhân sự phải thực hiện qua API quản lý tài khoản.')
  },
}

// Số đơn đang xử lý của một người (dùng để gợi ý người ít việc nhất):
// kiểm dịch viên: đơn đang ở bước kiểm dịch · điều phối viên: đơn đang ở bước kiểm dịch hoặc lập lộ trình
const ACTIVE: Order['status'][] = ['processing', 'choose_option', 'rechecking']
export const loadOf = (name: string, role: StaffMember['role'], orders: Order[]) =>
  orders.filter(o => ACTIVE.includes(o.status) && (role === 'inspector'
    ? o.stage === 'inspecting' && o.inspector === name
    : (o.stage === 'inspecting' || o.stage === 'routing') && o.coordinator === name)).length

// Người đang làm việc ít việc nhất (gợi ý phân công)
export function suggestStaff(staff: StaffMember[], role: StaffMember['role'], orders: Order[], exclude: string[] = []) {
  return staff
    .filter(s => s.role === role && s.status === 'working' && !exclude.includes(s.name))
    .map(s => ({ ...s, load: loadOf(s.name, role, orders) }))
    .sort((a, b) => a.load - b.load || a.id.localeCompare(b.id))
}
