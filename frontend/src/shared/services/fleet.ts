// Service đội xe và người đi theo chuyến kết nối Backend Spring Boot (/fleet/vehicles, /fleet/crew)
import { backendFleetApi } from '../api/fleet'
import { seedCrew, seedVehicles, type CrewMember, type Vehicle } from './mock/fleet'
import { createStore } from './store'

const vehicles = createStore<Vehicle>('vehicles', seedVehicles)
const crew = createStore<CrewMember>('crew', seedCrew)

export const vehiclesApi = {
  list: async (departAt?: number, tripType?: string): Promise<Vehicle[]> => {
    if (typeof window !== 'undefined') {
      try {
        const beVehicles = await backendFleetApi.getVehicles(departAt, tripType)
        if (beVehicles && beVehicles.length > 0) {
          return beVehicles.map(v => ({
            id: v.code || `VH-${String(v.id).padStart(3, '0')}`,
            name: v.name || 'Xe chuyên dụng',
            type: v.type || 'Xe chuyên dụng',
            capacity: v.capacity || 2,
            plate: v.plate,
          }))
        }
      } catch {
        // Fallback store khi offline
      }
    }
    return structuredClone(vehicles.all())
  },
  create: async (data: Omit<Vehicle, 'id'>) => {
    const next = Math.max(0, ...vehicles.all().map(v => Number(v.id.slice(3)))) + 1
    return structuredClone(vehicles.add({ id: `VH-${String(next).padStart(3, '0')}`, ...data }))
  },
  update: async (id: string, patch: Partial<Vehicle>) => structuredClone(vehicles.update(id, patch)),
  remove: async (id: string) => vehicles.remove(id),
}

export const crewApi = {
  list: async (departAt?: number, role?: string): Promise<CrewMember[]> => {
    if (typeof window !== 'undefined') {
      try {
        const beCrew = await backendFleetApi.getCrew(departAt, role)
        if (beCrew && beCrew.length > 0) {
          return beCrew.map(c => ({
            id: c.staffCode || (c.role?.toLowerCase() === 'driver' ? `TX-${c.userId}` : `NV-${c.userId}`),
            name: c.name,
            role: (c.role?.toLowerCase() === 'driver' ? 'driver' : 'escort') as 'driver' | 'escort',
            phone: c.phone || '',
          }))
        }
      } catch {
        // Fallback store khi offline
      }
    }
    return structuredClone(crew.all())
  },
  // Thêm tài xế / hộ tống mới (do quản trị viên tạo tài khoản): mã TX-xx hoặc NV-xx
  create: async (data: Pick<CrewMember, 'name' | 'phone' | 'role'>) => {
    const prefix = data.role === 'driver' ? 'TX' : 'NV'
    const next = Math.max(0, ...crew.all().filter(c => c.id.startsWith(prefix)).map(c => Number(c.id.slice(3)))) + 1
    return structuredClone(crew.add({ id: `${prefix}-${String(next).padStart(2, '0')}`, ...data }))
  },
}

export type { CrewMember, Vehicle }
