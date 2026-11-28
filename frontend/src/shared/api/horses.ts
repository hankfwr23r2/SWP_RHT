import apiClient, { type ApiResponse } from './client'
import { formResponseOptions, readFormResponse } from './customerAddresses'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type Horse = Schemas['HorseResponseDto']
export type HorseType = Schemas['HorseTypeResponseDto']
export type HorseCreate = Schemas['HorseCreateRequestDto']
export type HorseUpdate = Schemas['HorseUpdateRequestDto']
export type HorseDocument = Schemas['HorseDocumentResponseDto']

export const customerHorseApi = {
  list: async () => (await apiClient.get<ApiResponse<Horse[]>>('/horses')).data.data,
  getById: async (id: number) => (await apiClient.get<ApiResponse<Horse>>(`/horses/${id}`)).data.data,
  listTypes: async () => (await apiClient.get<ApiResponse<HorseType[]>>('/horse-types')).data.data,
  create: async (body: HorseCreate) =>
    readFormResponse<Horse>(await apiClient.post('/horses', body, formResponseOptions)),
  update: async (id: number, body: HorseUpdate) =>
    readFormResponse<Horse>(await apiClient.put(`/horses/${id}`, body, formResponseOptions)),
  listDocumentTypes: async () =>
    (await apiClient.get<ApiResponse<Schemas['DocumentTypeResponseDto'][]>>('/horses/document-types')).data.data,
  listDocuments: async (id: number) =>
    (await apiClient.get<ApiResponse<HorseDocument[]>>(`/horses/${id}/documents`)).data.data,
  uploadDocument: async (id: number, documentTypeCode: Schemas['HorseDocumentUploadRequestDto']['documentTypeCode'], file: File) => {
    const body = new FormData()
    body.append('documentTypeCode', documentTypeCode)
    body.append('file', file)
    return readFormResponse<HorseDocument>(await apiClient.post(`/horses/${id}/documents`, body,
      { ...formResponseOptions, headers: { 'Content-Type': 'multipart/form-data' } }))
  },
}

export interface BackendHorseDto {
  id?: number
  horseTypeID?: number
  customerID?: number
  horseName: string
  microchipID: string
  gender?: string
  color?: string
  age?: number
  identificationMarks?: string
  completedTrips?: number
  createdAt?: string
  status?: string
  currentLocationName?: string
  currentCountry?: string
  locationUpdatedAt?: number
  locationSource?: string
  transitStatus?: string
}

export interface BackendHorseTypeDto {
  id: number
  typeName: string
  description?: string
  insuredValue?: number
}

export interface BackendHorseJourneyLogDto {
  id: number
  horseId: number
  tripCode?: string
  fromLocation?: string
  toLocation?: string
  departAt?: number
  arrivedAt?: number
  driverName?: string
  proofImageUrl?: string
  notes?: string
  source?: string
  createdAt: number
}

export interface CreateHorseTypePayload {
  typeName: string
  description?: string
  insuredValue: number
}

function legacyHorse(horse: Horse): BackendHorseDto {
  return {
    id: horse.id,
    horseTypeID: horse.horseTypeId,
    horseName: horse.name ?? '',
    microchipID: horse.microchip ?? '',
    gender: horse.sex,
    color: horse.color,
    age: horse.birthYear == null ? undefined : new Date().getFullYear() - horse.birthYear,
    identificationMarks: horse.identificationMarks,
    createdAt: horse.createdAt,
    status: horse.status,
  }
}

type LegacyHorseWrite = BackendHorseDto & { color: string; age: number }

function legacyHorseBody(payload: LegacyHorseWrite): HorseUpdate {
  return {
    name: payload.horseName,
    horseTypeId: payload.horseTypeID!,
    sex: payload.gender?.toUpperCase() as HorseUpdate['sex'],
    color: payload.color,
    birthYear: new Date().getFullYear() - payload.age,
    identificationMarks: payload.identificationMarks,
  }
}

// Legacy consumers retain their existing response fields while using the current horse endpoints.
export const backendHorseApi = {
  listTypes: async (): Promise<BackendHorseTypeDto[]> =>
    (await customerHorseApi.listTypes()).flatMap(type => type.id == null ? [] : [{
      id: type.id, typeName: type.name ?? '', description: type.description,
    }]),

  createType: async (payload: CreateHorseTypePayload): Promise<BackendHorseTypeDto> => {
    const res = await apiClient.post<ApiResponse<BackendHorseTypeDto>>('/horse-types', payload)
    return res.data.data
  },

  updateType: async (id: number, payload: CreateHorseTypePayload): Promise<BackendHorseTypeDto> => {
    const res = await apiClient.put<ApiResponse<BackendHorseTypeDto>>(`/horse-types/${id}`, payload)
    return res.data.data
  },

  deleteType: async (id: number): Promise<void> => {
    await apiClient.delete(`/horse-types/${id}`)
  },

  updateInsuredValue: async (id: number, insuredValue: number): Promise<BackendHorseTypeDto> => {
    const res = await apiClient.put<ApiResponse<BackendHorseTypeDto>>(`/horse-types/${id}/insured-value`, { insuredValue })
    return res.data.data
  },

  list: async (): Promise<BackendHorseDto[]> => (await customerHorseApi.list()).map(legacyHorse),

  getById: async (id: number): Promise<BackendHorseDto> => legacyHorse(await customerHorseApi.getById(id)),

  create: async (payload: LegacyHorseWrite): Promise<BackendHorseDto> =>
    legacyHorse(await customerHorseApi.create({ ...legacyHorseBody(payload), microchip: payload.microchipID })),

  update: async (id: number, payload: LegacyHorseWrite): Promise<BackendHorseDto> =>
    legacyHorse(await customerHorseApi.update(id, legacyHorseBody(payload))),

  // Xóa ngựa
  remove: async (id: number): Promise<void> => {
    await apiClient.delete(`/horses/${id}`)
  },

  // Cập nhật vị trí chuồng hiện tại của ngựa (Dời chuồng ngoài hệ thống / cập nhật thủ công)
  updateLocation: async (id: number, payload: { locationName: string; country?: string; notes?: string }): Promise<BackendHorseDto> => {
    const res = await apiClient.patch<ApiResponse<BackendHorseDto>>(`/horses/${id}/location`, payload)
    return res.data.data
  },

  // Lấy lịch sử hành trình & các chặng di chuyển của ngựa
  getJourneyLogs: async (id: number): Promise<BackendHorseJourneyLogDto[]> => {
    const res = await apiClient.get<ApiResponse<BackendHorseJourneyLogDto[]>>(`/horses/${id}/journey-logs`)
    return res.data.data
  },
}
