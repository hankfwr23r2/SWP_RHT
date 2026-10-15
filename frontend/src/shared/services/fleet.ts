// Service đội xe và người đi theo chuyến. Sau này: GET/POST/PATCH/DELETE /api/vehicles, GET /api/crew
import { seedCrew, seedVehicles, type CrewMember, type Vehicle } from './mock/fleet'
import { createStore } from './store'

const vehicles = createStore<Vehicle>('vehicles', seedVehicles)
const crew = createStore<CrewMember>('crew', seedCrew)

export const vehiclesApi = {
  list: async (): Promise<Vehicle[]> => structuredClone(vehicles.all()),
  create: async (data: Omit<Vehicle, 'id'>) => {
    const next = Math.max(0, ...vehicles.all().map(v => Number(v.id.slice(3)))) + 1
    return structuredClone(vehicles.add({ id: `VH-${String(next).padStart(3, '0')}`, ...data }))
  },
  update: async (id: string, patch: Partial<Vehicle>) => structuredClone(vehicles.update(id, patch)),
  remove: async (id: string) => vehicles.remove(id),
}

export const crewApi = {
  list: async (): Promise<CrewMember[]> => structuredClone(crew.all()),
  // Thêm tài xế / hộ tống mới (do quản trị viên tạo tài khoản): mã TX-xx hoặc NV-xx
  create: async (data: Pick<CrewMember, 'name' | 'phone' | 'role'>) => {
    const prefix = data.role === 'driver' ? 'TX' : 'NV'
    const next = Math.max(0, ...crew.all().filter(c => c.id.startsWith(prefix)).map(c => Number(c.id.slice(3)))) + 1
    return structuredClone(crew.add({ id: `${prefix}-${String(next).padStart(2, '0')}`, ...data }))
  },
}

export type { CrewMember, Vehicle }
