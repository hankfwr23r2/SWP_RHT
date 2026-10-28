import apiClient, { type ApiResponse } from './client'

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
  bookingId?: number
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

export const backendHorseApi = {
  listTypes: async (): Promise<BackendHorseTypeDto[]> => {
    const res = await apiClient.get<ApiResponse<BackendHorseTypeDto[]>>('/horse-types')
    return res.data.data
  },

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

  // Lấy tất cả ngựa
  list: async (): Promise<BackendHorseDto[]> => {
    const res = await apiClient.get<ApiResponse<BackendHorseDto[]>>('/horses')
    return res.data.data
  },

  // Lấy chi tiết ngựa theo ID
  getById: async (id: number): Promise<BackendHorseDto> => {
    const res = await apiClient.get<ApiResponse<BackendHorseDto>>(`/horses/${id}`)
    return res.data.data
  },

  // Tạo mới ngựa
  create: async (payload: BackendHorseDto): Promise<BackendHorseDto> => {
    const res = await apiClient.post<ApiResponse<BackendHorseDto>>('/horses', payload)
    return res.data.data
  },

  update: async (id: number, payload: BackendHorseDto): Promise<BackendHorseDto> => {
    const res = await apiClient.put<ApiResponse<BackendHorseDto>>(`/horses/${id}`, payload)
    return res.data.data
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
  updateLocation: async (id: number, payload: { locationName: string; country?: string; reason?: string; notes?: string }): Promise<BackendHorseDto> => {
    const res = await apiClient.patch<ApiResponse<BackendHorseDto>>(`/horses/${id}/location`, payload)
    return res.data.data
  },

  // Lấy lịch sử hành trình & các chặng di chuyển của ngựa
  getJourneyLogs: async (id: number): Promise<BackendHorseJourneyLogDto[]> => {
    const res = await apiClient.get<ApiResponse<BackendHorseJourneyLogDto[]>>(`/horses/${id}/journey-logs`)
    return res.data.data
  },
}
