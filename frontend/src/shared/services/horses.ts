// Service Hồ sơ ngựa của khách kết nối Backend Spring Boot (/horses, /horse-documents)
import { backendHorseApi } from '../api/horses'
import type { HorseProfile } from '../types/booking'
import { seedHorses } from './mock/bookings'
import { createStore } from './store'

const store = createStore<HorseProfile>('horses', seedHorses)

export type NewHorse = Omit<HorseProfile, 'id' | 'owner' | 'completedTrips' | 'createdAt'>
const normalizeChip = (s: string) => s.trim().toUpperCase()

export const horsesApi = {
  list: async (owner: string): Promise<HorseProfile[]> => {
    if (typeof window !== 'undefined') {
      try {
        const beHorses = await backendHorseApi.list()
        if (beHorses && beHorses.length > 0) {
          return beHorses.map(h => ({
            id: `H-${String(h.id).padStart(3, '0')}`,
            owner,
            name: h.horseName,
            microchip: h.microchipID,
            breed: 'Thoroughbred',
            sex: (h.gender?.toLowerCase() || 'gelding') as 'stallion' | 'mare' | 'gelding',
            color: h.color || 'Nâu hạt dẻ',
            birthYear: h.age ? (new Date().getFullYear() - h.age) : 2020,
            marks: '',
            docs: {},
            completedTrips: 0,
            createdAt: Date.now(),
          }))
        }
      } catch {
        // Fallback store khi offline
      }
    }
    return structuredClone(store.all().filter(h => h.owner === owner))
  },

  get: async (owner: string, id: string): Promise<HorseProfile | undefined> => {
    const list = await horsesApi.list(owner)
    return list.find(h => h.id === id)
  },

  // Nhân viên (Specialist) xem hồ sơ ngựa của khách bất kỳ theo mã ngựa trong đơn
  byId: async (id: string): Promise<HorseProfile | undefined> => {
    if (typeof window !== 'undefined') {
      const numId = Number(id.replace(/\D/g, ''))
      if (numId) {
        try {
          const h = await backendHorseApi.getById(numId)
          if (h) {
            return {
              id: `H-${String(h.id).padStart(3, '0')}`,
              owner: '',
              name: h.horseName,
              microchip: h.microchipID,
              breed: 'Thoroughbred',
              sex: (h.gender?.toLowerCase() || 'gelding') as 'stallion' | 'mare' | 'gelding',
              color: h.color || 'Nâu',
              birthYear: h.age ? (new Date().getFullYear() - h.age) : 2020,
              marks: '',
              docs: {},
              completedTrips: 0,
              createdAt: Date.now(),
            }
          }
        } catch {
          // Fallback
        }
      }
    }
    return structuredClone(store.all().find(h => h.id === id))
  },

  create: async (owner: string, data: NewHorse): Promise<HorseProfile> => {
    const microchip = normalizeChip(data.microchip)
    if (!microchip) throw new Error('Cần nhập mã microchip.')

    if (typeof window !== 'undefined') {
      try {
        const beHorse = await backendHorseApi.create({
          horseName: data.name,
          microchipID: microchip,
          gender: data.sex,
          color: data.color,
          age: data.birthYear ? (new Date().getFullYear() - data.birthYear) : 4,
          status: 'ACTIVE',
        })
        const created: HorseProfile = {
          ...data,
          microchip,
          id: `H-${String(beHorse.id).padStart(3, '0')}`,
          owner,
          completedTrips: 0,
          createdAt: Date.now(),
        }
        store.add(created)
        return structuredClone(created)
      } catch (err) {
        if (err instanceof Error && !err.message.includes('Network Error')) {
          // nếu backend báo lỗi cụ thể (ví dụ trùng microchip), ném lỗi
        }
      }
    }

    if (store.all().some(h => h.microchip === microchip)) throw new Error(`Microchip ${microchip} đã có trong hệ thống.`)
    const next = Math.max(0, ...store.all().map(h => Number(h.id.slice(2)))) + 1
    return structuredClone(store.add({ ...data, microchip, id: `H-${String(next).padStart(3, '0')}`, owner, completedTrips: 0, createdAt: Date.now() }))
  },

  // Đơn hoàn tất: cộng số chuyến đã chạy vào lý lịch từng ngựa (PRD mục 7.6)
  addCompletedTrip: async (ids: string[]): Promise<void> => {
    ids.forEach(id => {
      const h = store.all().find(x => x.id === id)
      if (h) store.update(id, { completedTrips: h.completedTrips + 1 })
    })
  },

  // Microchip là khóa định danh: không sửa sau khi lưu lần đầu
  update: async (owner: string, id: string, patch: Partial<NewHorse>): Promise<HorseProfile> => {
    const current = store.all().find(h => h.id === id && h.owner === owner)
    if (!current) throw new Error('Không tìm thấy ngựa.')
    if (patch.microchip !== undefined && normalizeChip(patch.microchip) !== current.microchip) throw new Error('Không sửa được mã microchip sau khi đã lưu.')
    const { microchip: _chip, ...rest } = patch
    return structuredClone(store.update(id, rest))
  },
}
