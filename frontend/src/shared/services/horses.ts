// Service Hồ sơ ngựa của khách. Sau này: GET/POST/PATCH /api/horses
import type { HorseProfile } from '../types/booking'
import { seedHorses } from './mock/bookings'
import { createStore } from './store'

const store = createStore<HorseProfile>('horses', seedHorses)

export type NewHorse = Omit<HorseProfile, 'id' | 'owner' | 'completedTrips' | 'createdAt'>
const normalizeChip = (s: string) => s.trim().toUpperCase()

export const horsesApi = {
  list: async (owner: string): Promise<HorseProfile[]> => structuredClone(store.all().filter(h => h.owner === owner)),
  get: async (owner: string, id: string): Promise<HorseProfile | undefined> => structuredClone(store.all().find(h => h.id === id && h.owner === owner)),
  // Nhân viên (Specialist) xem hồ sơ ngựa của khách bất kỳ theo mã ngựa trong đơn
  byId: async (id: string): Promise<HorseProfile | undefined> => structuredClone(store.all().find(h => h.id === id)),
  create: async (owner: string, data: NewHorse): Promise<HorseProfile> => {
    const microchip = normalizeChip(data.microchip)
    if (!microchip) throw new Error('Cần nhập mã microchip.')
    if (store.all().some(h => h.microchip === microchip)) throw new Error(`Microchip ${microchip} đã có trong hệ thống.`)
    const next = Math.max(0, ...store.all().map(h => Number(h.id.slice(2)))) + 1
    return structuredClone(store.add({ ...data, microchip, id: `H-${String(next).padStart(3, '0')}`, owner, completedTrips: 0, createdAt: Date.now() }))
  },
  // Đơn hoàn tất: cộng số chuyến đã chạy vào lý lịch từng ngựa (PRD mục 7.6)
  addCompletedTrip: async (ids: string[]): Promise<void> => { ids.forEach(id => { const h = store.all().find(x => x.id === id); if (h) store.update(id, { completedTrips: h.completedTrips + 1 }) }) },
  // Microchip là khóa định danh: không sửa sau khi lưu lần đầu
  update: async (owner: string, id: string, patch: Partial<NewHorse>): Promise<HorseProfile> => {
    const current = store.all().find(h => h.id === id && h.owner === owner)
    if (!current) throw new Error('Không tìm thấy ngựa.')
    if (patch.microchip !== undefined && normalizeChip(patch.microchip) !== current.microchip) throw new Error('Không sửa được mã microchip sau khi đã lưu.')
    const { microchip: _chip, ...rest } = patch
    return structuredClone(store.update(id, rest))
  },
}
