// Service đội xe và người đi theo chuyến kết nối Backend Spring Boot (/fleet/vehicles, /fleet/crew)
import { backendFleetApi } from '../api/fleet'
import { seedCrew, seedVehicles } from '../fixtures/fleet'
import type { CrewMember, Vehicle } from '../types/fleet'
import { createStore } from './store'

const vehicles = createStore<Vehicle>('vehicles', () => import.meta.env?.MODE === 'test' ? seedVehicles() : [])
const crew = createStore<CrewMember>('crew', () => import.meta.env?.MODE === 'test' ? seedCrew() : [])
const isTestEnv = import.meta.env?.MODE === 'test'

export const vehiclesApi = {
  list: async (departAt?: number, tripType?: string): Promise<Vehicle[]> => {
    if (!isTestEnv) {
      const beVehicles = await backendFleetApi.getVehicles(departAt, tripType)
      return beVehicles.map(v => ({
            id: v.code || `VH-${String(v.id).padStart(3, '0')}`,
            name: v.name || 'Xe chuyên dụng',
            type: v.type || 'Xe chuyên dụng',
            capacity: v.capacity || 2,
            plate: v.plate,
          }))
    }
    return structuredClone(vehicles.all())
  },
  create: async (data: Omit<Vehicle, 'id'>) => {
    if (!isTestEnv) throw new Error('API backend chưa hỗ trợ tạo xe.')
    const next = Math.max(0, ...vehicles.all().map(v => Number(v.id.slice(3)))) + 1
    return structuredClone(vehicles.add({ id: `VH-${String(next).padStart(3, '0')}`, ...data }))
  },
  update: async (id: string, patch: Partial<Vehicle>) => {
    if (!isTestEnv) throw new Error('API backend chưa hỗ trợ cập nhật xe.')
    return structuredClone(vehicles.update(id, patch))
  },
  remove: async (id: string) => {
    if (!isTestEnv) throw new Error('API backend chưa hỗ trợ xóa xe.')
    vehicles.remove(id)
  },
}

export const crewApi = {
  list: async (departAt?: number, role?: string): Promise<CrewMember[]> => {
    if (!isTestEnv) {
      const beCrew = await backendFleetApi.getCrew(departAt, role)
      return beCrew.flatMap(c => {
        const role = c.role.toLowerCase()
        if (role !== 'driver' && role !== 'escort') return []
        return [{
            id: c.staffCode || (c.role?.toLowerCase() === 'driver' ? `TX-${c.userId}` : `NV-${c.userId}`),
            name: c.name,
            role: role as 'driver' | 'escort',
            phone: c.phone || '',
          }]
      })
    }
    return structuredClone(crew.all())
  },
  // Thêm tài xế / hộ tống mới (do quản trị viên tạo tài khoản): mã TX-xx hoặc NV-xx
  create: async (data: Pick<CrewMember, 'name' | 'phone' | 'role'>) => {
    if (!isTestEnv) throw new Error('Tạo nhân sự phải thực hiện qua API quản lý tài khoản.')
    const prefix = data.role === 'driver' ? 'TX' : 'NV'
    const next = Math.max(0, ...crew.all().filter(c => c.id.startsWith(prefix)).map(c => Number(c.id.slice(3)))) + 1
    return structuredClone(crew.add({ id: `${prefix}-${String(next).padStart(2, '0')}`, ...data }))
  },
}

export type { CrewMember, Vehicle }
