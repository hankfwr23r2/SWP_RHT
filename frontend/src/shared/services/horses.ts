// Service Hồ sơ ngựa của khách kết nối Backend Spring Boot (/horses, /horse-documents)
import { backendHorseApi } from '../api/horses'
import type { HorseDocType } from '../config/booking-rules'
import { fromIsoDay } from '../lib/booking'
import type { HorseDoc, HorseProfile } from '../types/booking'
import { seedHorses } from '../fixtures/bookings'
import { createStore } from './store'

const store = createStore<HorseProfile>('horses', seedHorses)

export type NewHorse = Omit<HorseProfile, 'id' | 'owner' | 'completedTrips' | 'createdAt'>
const normalizeChip = (s: string) => s.trim().toUpperCase()

const parseExpiry = (dateStr?: string): number | undefined => {
  if (!dateStr) return undefined
  try {
    return fromIsoDay(dateStr)
  } catch {
    return undefined
  }
}

async function getDocsForHorse(numId: number, cachedDocs?: Partial<Record<HorseDocType, HorseDoc>>): Promise<Partial<Record<HorseDocType, HorseDoc>>> {
  const result: Partial<Record<HorseDocType, HorseDoc>> = { ...(cachedDocs || {}) }
  try {
    const beDocs = await backendHorseApi.getDocuments(numId)
    if (beDocs && beDocs.length > 0) {
      beDocs.forEach(d => {
        const type = d.documentType?.toUpperCase()
        const key: HorseDocType | null =
          type === 'PASSPORT' ? 'passport'
          : (type === 'VACCINATION' || type === 'VACCINE') ? 'vaccine'
          : (type === 'BLOOD_TEST' || type === 'LAB') ? 'lab'
          : null
        if (key) {
          result[key] = {
            fileName: d.documentUrl ? d.documentUrl.split('/').pop() || d.documentUrl : `${key}.pdf`,
            uploadedAt: d.uploadedAt ? new Date(d.uploadedAt).getTime() : Date.now(),
            expiresAt: parseExpiry(d.expiryDate) ?? result[key]?.expiresAt,
          }
        }
      })
    }
  } catch {
    // Giữ nguyên cachedDocs nếu offline hoặc chưa có documents
  }
  return result
}

export const horsesApi = {
  list: async (owner: string): Promise<HorseProfile[]> => {
    if (typeof window !== 'undefined') {
      try {
        const beHorses = await backendHorseApi.list()
        if (beHorses && beHorses.length > 0) {
          const mapped = await Promise.all(beHorses.map(async h => {
            const formattedId = `H-${String(h.id).padStart(3, '0')}`
            const cached = store.all().find(c => c.microchip === h.microchipID || c.id === formattedId)
            const docs = h.id ? await getDocsForHorse(h.id, cached?.docs) : (cached?.docs || {})

            const profile: HorseProfile = {
              id: formattedId,
              owner: cached?.owner || owner,
              name: h.horseName,
              microchip: h.microchipID,
              breed: cached?.breed || 'Thoroughbred',
              sex: (h.gender?.toLowerCase() || cached?.sex || 'gelding') as 'stallion' | 'mare' | 'gelding',
              color: h.color || cached?.color || 'Nâu hạt dẻ',
              birthYear: h.age ? (new Date().getFullYear() - h.age) : (cached?.birthYear || 2020),
              marks: cached?.marks || '',
              docs,
              completedTrips: cached?.completedTrips || 0,
              createdAt: cached?.createdAt || Date.now(),
            }

            // Đồng bộ lại vào store để lưu giữ tài liệu trong phiên
            if (cached) {
              store.update(cached.id, profile)
            } else {
              store.add(profile)
            }

            return profile
          }))

          return mapped.filter(h => !owner || h.owner === owner)
        }
      } catch {
        // Fallback store khi offline
      }
    }
    return structuredClone(store.all().filter(h => !owner || h.owner === owner))
  },

  get: async (owner: string, id: string): Promise<HorseProfile | undefined> => {
    const list = await horsesApi.list(owner)
    return list.find(h => h.id === id)
  },

  // Nhân viên (Specialist) xem hồ sơ ngựa của khách bất kỳ theo mã ngựa trong đơn
  byId: async (id: string): Promise<HorseProfile | undefined> => {
    const numId = Number(id.replace(/\D/g, ''))
    const cached = store.all().find(h => h.id === id || (numId && Number(h.id.replace(/\D/g, '')) === numId))

    if (typeof window !== 'undefined' && numId) {
      try {
        const h = await backendHorseApi.getById(numId)
        if (h) {
          const docs = await getDocsForHorse(numId, cached?.docs)
          return {
            id: `H-${String(h.id).padStart(3, '0')}`,
            owner: cached?.owner || '',
            name: h.horseName,
            microchip: h.microchipID,
            breed: cached?.breed || 'Thoroughbred',
            sex: (h.gender?.toLowerCase() || cached?.sex || 'gelding') as 'stallion' | 'mare' | 'gelding',
            color: h.color || cached?.color || 'Nâu',
            birthYear: h.age ? (new Date().getFullYear() - h.age) : (cached?.birthYear || 2020),
            marks: cached?.marks || '',
            docs,
            completedTrips: cached?.completedTrips || 0,
            createdAt: cached?.createdAt || Date.now(),
          }
        }
      } catch {
        // Fallback
      }
    }
    return structuredClone(cached)
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
  update: async (_owner: string, id: string, patch: Partial<NewHorse>): Promise<HorseProfile> => {
    const current = store.all().find(h => h.id === id || (patch.microchip && normalizeChip(patch.microchip) === h.microchip))
    if (!current) throw new Error('Không tìm thấy ngựa.')
    if (patch.microchip !== undefined && normalizeChip(patch.microchip) !== current.microchip) throw new Error('Không sửa được mã microchip sau khi đã lưu.')
    const { microchip: _chip, ...rest } = patch
    return structuredClone(store.update(current.id, rest))
  },
}
