import apiClient, { type ApiResponse } from './client'
import { documentUploadError } from '../lib/documentUpload'
import { formResponseOptions, readFormResponse } from './customerAddresses'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type Horse = Schemas['HorseResponseDto']
export type HorseType = Schemas['HorseTypeResponseDto']
export type HorseCreate = Schemas['HorseCreateRequestDto']
export type HorseUpdate = Schemas['HorseUpdateRequestDto']

export const customerHorseApi = {
  list: async () => (await apiClient.get<ApiResponse<Horse[]>>('/horses')).data.data,
  getById: async (id: number) => (await apiClient.get<ApiResponse<Horse>>(`/horses/${id}`)).data.data,
  listTypes: async () => (await apiClient.get<ApiResponse<HorseType[]>>('/horse-types')).data.data,
  create: async (body: HorseCreate) =>
    readFormResponse<Horse>(await apiClient.post('/horses', body, formResponseOptions)),
  update: async (id: number, body: HorseUpdate) =>
    readFormResponse<Horse>(await apiClient.put(`/horses/${id}`, body, formResponseOptions)),
}

export interface BackendHorseDto {
  id?: number
  horseTypeID?: number
  customerID?: number
  horseName: string
  microchipID: string
  gender?: string
  breed?: string
  color?: string
  age?: number
  weight?: number
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

export interface BackendHorseDocResponse {
  id: number
  horseId: number
  horseName: string
  documentType: string
  fileUrl?: string
  documentUrl?: string
  status: string
  rejectionReason?: string
  reviewerName?: string
  reviewedAt?: string
  uploadDate?: string
  uploadedAt?: string
}

export const HORSE_DOC_BACKEND_TYPE = {
  passport: 'PASSPORT',
  vaccine: 'VACCINATION',
  lab: 'BLOOD_TEST',
} as const

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
    breed: horse.breed,
    color: horse.color,
    age: horse.birthYear == null ? undefined : new Date().getFullYear() - horse.birthYear,
    weight: horse.weightKg,
    identificationMarks: horse.identificationMarks,
    createdAt: horse.createdAt,
    status: horse.status,
  }
}

function legacyHorseBody(payload: BackendHorseDto): HorseUpdate {
  return {
    name: payload.horseName,
    horseTypeId: payload.horseTypeID!,
    sex: payload.gender?.toUpperCase() as HorseUpdate['sex'],
    breed: payload.breed,
    color: payload.color,
    birthYear: payload.age == null ? undefined : new Date().getFullYear() - payload.age,
    weightKg: payload.weight,
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

  create: async (payload: BackendHorseDto): Promise<BackendHorseDto> =>
    legacyHorse(await customerHorseApi.create({ ...legacyHorseBody(payload), microchip: payload.microchipID })),

  update: async (id: number, payload: BackendHorseDto): Promise<BackendHorseDto> => {
    const current = await customerHorseApi.getById(id)
    return legacyHorse(await customerHorseApi.update(id, {
      ...legacyHorseBody(payload),
      weightKg: payload.weight ?? current.weightKg,
      passportNumber: current.passportNumber,
      sire: current.sire,
      dam: current.dam,
      declaredValue: current.declaredValue,
      declaredCurrencyCode: current.declaredCurrencyCode,
    }))
  },

  // Xóa ngựa
  remove: async (id: number): Promise<void> => {
    await apiClient.delete(`/horses/${id}`)
  },

  // Lấy danh sách tài liệu của một ngựa
  getDocuments: async (horseId: number): Promise<BackendHorseDocResponse[]> => {
    const res = await apiClient.get<ApiResponse<BackendHorseDocResponse[]>>(`/horse-documents/horse/${horseId}`)
    return res.data.data
  },

  // Khách lấy tài liệu của mọi ngựa của mình
  getMyDocuments: async (): Promise<BackendHorseDocResponse[]> => {
    const res = await apiClient.get<ApiResponse<BackendHorseDocResponse[]>>('/horse-documents/mine')
    return res.data.data
  },

  // Upload tài liệu ngựa
  uploadDocument: async (horseId: number, documentType: string, file: File): Promise<BackendHorseDocResponse> => {
    const message = documentUploadError(file)
    if (message) throw new Error(message)
    const formData = new FormData()
    formData.append('file', file)
    formData.append('horseId', String(horseId))
    formData.append('documentType', documentType)

    const res = await apiClient.post<ApiResponse<BackendHorseDocResponse>>('/horse-documents/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return res.data.data
  },

  // Specialist duyệt tài liệu ngựa
  reviewDocument: async (docId: number, approved: boolean, note: string, reviewerName: string): Promise<BackendHorseDocResponse> => {
    const res = await apiClient.put<ApiResponse<BackendHorseDocResponse>>(`/horse-documents/${docId}/review`, {
      approved,
      note,
      reviewerName,
    })
    return res.data.data
  },

  // Xóa một giấy tờ ngựa theo ID tài liệu
  deleteDocument: async (documentId: number): Promise<void> => {
    await apiClient.delete(`/horse-documents/${documentId}`)
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
