// Service đội xe và người đi theo chuyến, gọi Backend Spring Boot (/fleet/vehicles, /fleet/crew). Không giữ dữ liệu ở máy khách.
import { backendFleetApi, type BackendVehicleResponse } from '../api/fleet'
import type { CrewMember, Vehicle } from '../types/fleet'

const normalizeStatus = (st?: string): string => {
  const s = (st || 'AVAILABLE').toUpperCase().replace(/[\s_-]/g, '')
  if (s === 'BUSY' || s === 'INUSE' || s === 'ONTRIP') return 'BUSY'
  if (s === 'MAINTENANCE' || s === 'UNAVAILABLE') return 'MAINTENANCE'
  if (s === 'AVAILABLE' || s === 'ACTIVE' || s === '') return 'AVAILABLE'
  return st || 'AVAILABLE'
}

const mapVehicle = (v: BackendVehicleResponse): Vehicle => ({
  id: v.code || `VH-${String(v.id).padStart(3, '0')}`,
  name: v.name || 'Xe chuyên dụng',
  type: v.type || 'Xe chuyên dụng',
  capacity: v.capacity || 2,
  plate: v.plate || '',
  status: normalizeStatus(v.status),
})

const toVehiclePayload = (v: Omit<Vehicle, 'id'>): Parameters<typeof backendFleetApi.createVehicle>[0] => ({
  type: v.type,
  capacity: v.capacity,
  plate: v.plate,
  status: v.status,
})

export const vehiclesApi = {
  list: async (departAt?: number, tripType?: string): Promise<Vehicle[]> =>
    (await backendFleetApi.getVehicles(departAt, tripType)).map(mapVehicle),
  create: async (data: Omit<Vehicle, 'id'>) => mapVehicle(await backendFleetApi.createVehicle(toVehiclePayload(data))),
  update: async (id: string, patch: Partial<Vehicle>) => {
    const current = (await vehiclesApi.list()).find(v => v.id === id)
    if (!current) throw new Error('Không tìm thấy xe.')
    const { id: _id, ...payload } = { ...current, ...patch }
    return mapVehicle(await backendFleetApi.updateVehicle(id, toVehiclePayload(payload)))
  },
  remove: async (id: string) => backendFleetApi.removeVehicle(id),
}

export const crewApi = {
  list: async (departAt?: number, role?: string): Promise<CrewMember[]> =>
    (await backendFleetApi.getCrew(departAt, role)).flatMap(c => {
      const r = c.role.toLowerCase()
      if (r !== 'driver' && r !== 'escort') return []
      return [{ id: c.staffCode || `${r === 'driver' ? 'TX' : 'NV'}-${c.userId}`, name: c.name, role: r as 'driver' | 'escort', phone: c.phone || '' }]
    }),
  // Nhân sự do quản trị viên tạo tài khoản (xem accounts.ts), không tạo ở đây
  create: async (_data: Pick<CrewMember, 'name' | 'phone' | 'role'>): Promise<CrewMember> => {
    throw new Error('Tạo nhân sự phải thực hiện qua API quản lý tài khoản.')
  },
}

export type { CrewMember, Vehicle }
