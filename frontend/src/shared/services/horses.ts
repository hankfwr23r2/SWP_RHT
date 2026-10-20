// Service Hồ sơ ngựa của khách, gọi Backend Spring Boot (/horses, /horse-documents). Không giữ dữ liệu ở máy khách.
import { backendHorseApi, HORSE_DOC_BACKEND_TYPE, type BackendHorseDto, type BackendHorseDocResponse } from '../api/horses'
import type { HorseDocType } from '../config/booking-rules'
import type { HorseDoc, HorseProfile } from '../types/booking'

export type NewHorse = Omit<HorseProfile, 'id' | 'owner' | 'completedTrips' | 'createdAt'>
const normalizeChip = (s: string) => s.trim().toUpperCase()
const horseCode = (id?: number) => `H-${String(id ?? 0).padStart(3, '0')}`
const numericId = (id: string) => Number(id.replace(/\D/g, ''))
const thisYear = () => new Date().getFullYear()

const DOC_KEY: Record<string, HorseDocType> = { PASSPORT: 'passport', VACCINATION: 'vaccine', VACCINE: 'vaccine', BLOOD_TEST: 'lab', LAB: 'lab' }

function docsOf(list: BackendHorseDocResponse[]): Partial<Record<HorseDocType, HorseDoc>> {
  const out: Partial<Record<HorseDocType, HorseDoc>> = {}
  for (const d of list) {
    const key = DOC_KEY[d.documentType?.toUpperCase()]
    if (!key) continue
    const url = d.documentUrl ?? d.fileUrl
    out[key] = {
      documentId: d.id,
      fileName: url ? url.split('/').pop() || url : `${key}.pdf`,
      fileUrl: url,
      uploadedAt: new Date(d.uploadedAt ?? d.uploadDate ?? Date.now()).getTime(),
    }
  }
  return out
}

function profileOf(h: BackendHorseDto, owner: string, docs: Partial<Record<HorseDocType, HorseDoc>>): HorseProfile {
  return {
    id: horseCode(h.id), owner, name: h.horseName, microchip: h.microchipID,
    breed: h.breed ?? '', sex: (h.gender?.toLowerCase() ?? '') as HorseProfile['sex'], color: h.color ?? '',
    birthYear: h.age !== undefined ? thisYear() - h.age : 0, marks: h.identificationMarks ?? '',
    docs, completedTrips: h.completedTrips ?? 0, createdAt: h.createdAt ? new Date(h.createdAt).getTime() : 0,
    currentLocationName: h.currentLocationName,
    currentCountry: h.currentCountry,
    locationUpdatedAt: h.locationUpdatedAt,
    locationSource: h.locationSource as HorseProfile['locationSource'],
    transitStatus: h.transitStatus as HorseProfile['transitStatus'],
  }
}

const withDocs = async (h: BackendHorseDto, owner: string) =>
  profileOf(h, owner, h.id ? docsOf(await backendHorseApi.getDocuments(h.id)) : {})

const payloadOf = (data: Pick<NewHorse, 'name' | 'breed' | 'sex' | 'color' | 'birthYear' | 'marks'>, microchip: string): BackendHorseDto => ({
  horseName: data.name, microchipID: microchip, breed: data.breed, gender: data.sex, color: data.color,
  age: thisYear() - data.birthYear, identificationMarks: data.marks, status: 'ACTIVE',
})

export const horsesApi = {
  list: async (owner: string): Promise<HorseProfile[]> =>
    Promise.all((await backendHorseApi.list()).map(h => withDocs(h, owner))),

  get: async (owner: string, id: string): Promise<HorseProfile | undefined> =>
    (await horsesApi.list(owner)).find(h => h.id === id),

  // Nhân viên (Specialist) xem hồ sơ ngựa của khách bất kỳ theo mã ngựa trong đơn
  byId: async (id: string): Promise<HorseProfile | undefined> => {
    const n = numericId(id)
    if (!n) return undefined
    return withDocs(await backendHorseApi.getById(n), '')
  },

  create: async (owner: string, data: NewHorse): Promise<HorseProfile> => {
    const microchip = normalizeChip(data.microchip)
    if (!microchip) throw new Error('Cần nhập mã microchip.')
    return profileOf(await backendHorseApi.create(payloadOf(data, microchip)), owner, {})
  },

  remove: async (id: string): Promise<void> => {
    const n = numericId(id)
    if (!n) throw new Error('Mã hồ sơ ngựa không hợp lệ.')
    await backendHorseApi.remove(n)
  },

  uploadDocument: async (horseId: string, type: HorseDocType, file: File) => {
    const id = numericId(horseId)
    if (!id) throw new Error('Mã hồ sơ ngựa không hợp lệ.')
    return backendHorseApi.uploadDocument(id, HORSE_DOC_BACKEND_TYPE[type], file)
  },

  // Microchip là khóa định danh: không sửa sau khi lưu lần đầu
  update: async (owner: string, id: string, patch: Partial<NewHorse>): Promise<HorseProfile> => {
    const n = numericId(id)
    const current = n ? await horsesApi.byId(id) : undefined
    if (!current) throw new Error('Không tìm thấy ngựa.')
    if (patch.microchip !== undefined && normalizeChip(patch.microchip) !== current.microchip) throw new Error('Không sửa được mã microchip sau khi đã lưu.')
    const merged = { name: patch.name ?? current.name, breed: patch.breed ?? current.breed, sex: patch.sex ?? current.sex, color: patch.color ?? current.color, birthYear: patch.birthYear ?? current.birthYear, marks: patch.marks ?? current.marks }
    const updated = await backendHorseApi.update(n, { id: n, ...payloadOf(merged, current.microchip) })
    return profileOf(updated, owner, current.docs)
  },

  // Cập nhật vị trí chuồng ngựa hiện tại (Dời chuồng ngoài hệ thống / cập nhật thủ công)
  updateLocation: async (id: string, payload: { locationName: string; country?: string; reason?: string; notes?: string }): Promise<HorseProfile> => {
    const n = numericId(id)
    if (!n) throw new Error('Mã hồ sơ ngựa không hợp lệ.')
    const updated = await backendHorseApi.updateLocation(n, payload)
    const docs = docsOf(await backendHorseApi.getDocuments(n))
    return profileOf(updated, '', docs)
  },

  // Lấy toàn bộ nhật ký hành trình của cá thể ngựa
  getJourneyLogs: async (id: string) => {
    const n = numericId(id)
    if (!n) return []
    return backendHorseApi.getJourneyLogs(n)
  },
}
