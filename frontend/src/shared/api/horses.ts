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
  expiryDate?: string
  uploadDate?: string
  uploadedAt?: string
}

export const HORSE_DOC_BACKEND_TYPE = {
  passport: 'PASSPORT',
  vaccine: 'VACCINATION',
  lab: 'BLOOD_TEST',
} as const

export const backendHorseApi = {
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

  // Upload tài liệu ngựa
  uploadDocument: async (horseId: number, documentType: string, file: File, expiryDate?: string): Promise<BackendHorseDocResponse> => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('horseId', String(horseId))
    formData.append('documentType', documentType)
    if (expiryDate) formData.append('expiryDate', expiryDate)

    const res = await apiClient.post<ApiResponse<BackendHorseDocResponse>>('/horse-documents/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return res.data.data
  },

  updateDocumentExpiry: async (docId: number, expiryDate: string): Promise<BackendHorseDocResponse> => {
    const res = await apiClient.put<ApiResponse<BackendHorseDocResponse>>(`/horse-documents/${docId}/expiry`, { expiryDate })
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
}
