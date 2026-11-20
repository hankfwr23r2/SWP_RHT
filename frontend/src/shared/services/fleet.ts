// Service đội xe và người đi theo chuyến, gọi Backend Spring Boot (/fleet/vehicles, /fleet/crew). Không giữ dữ liệu ở máy khách.
import { backendFleetApi, type BackendVehicleResponse } from '../api/fleet'
import type { Booking } from '../types/booking'
import type { CrewMember, Vehicle } from '../types/fleet'

// Đơn đang xếp xe hoặc kíp: backend tính rảnh/bận theo ngày đi, loại chuyến và bỏ qua chính đơn này
type ForBooking = Pick<Booking, 'id' | 'departAt' | 'type'>

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
  available: v.available,
  busyReason: v.busyReason || undefined,
  busyBookingCode: v.busyBookingCode || undefined,
  busyUntil: v.busyUntil ?? undefined,
  onTrip: !!v.onTrip,
  onTripBookingCode: v.onTripBookingCode || undefined,
  returnEta: v.returnEta ?? undefined,
  upcoming: v.upcoming ?? [],
})

const toVehiclePayload = (v: Omit<Vehicle, 'id'>): Parameters<typeof backendFleetApi.createVehicle>[0] => ({
  type: v.type,
  capacity: v.capacity,
  plate: v.plate,
  status: v.status,
})

export const vehiclesApi = {
  list: async (departAt?: number, tripType?: string, bookingId?: string): Promise<Vehicle[]> =>
    (await backendFleetApi.getVehicles(departAt, tripType, bookingId)).map(mapVehicle),
  forBooking: (b: ForBooking): Promise<Vehicle[]> => vehiclesApi.list(b.departAt, b.type, b.id),
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
  list: async (departAt?: number, role?: string, tripType?: string, bookingId?: string): Promise<CrewMember[]> =>
    (await backendFleetApi.getCrew(departAt, role, tripType, bookingId)).flatMap(c => {
      const r = c.role.toLowerCase()
      if (r !== 'driver' && r !== 'escort') return []
      return [{
        id: c.staffCode || `${r === 'driver' ? 'TX' : 'NV'}-${c.userId}`, name: c.name, role: r as 'driver' | 'escort', phone: c.phone || '',
        available: c.available, busyReason: c.busyReason || undefined, busyBookingCode: c.busyBookingCode || undefined, busyUntil: c.busyUntil ?? undefined,
      }]
    }),
  forBooking: (b: ForBooking): Promise<CrewMember[]> => crewApi.list(b.departAt, undefined, b.type, b.id),
  // Nhân sự do quản trị viên tạo tài khoản (xem accounts.ts), không tạo ở đây
  create: async (_data: Pick<CrewMember, 'name' | 'phone' | 'role'>): Promise<CrewMember> => {
    throw new Error('Tạo nhân sự phải thực hiện qua API quản lý tài khoản.')
  },
}

export type { CrewMember, Vehicle }
