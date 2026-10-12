import apiClient, { type ApiResponse } from './client'
import type { BackendBookingResponse } from './bookings'

export interface ApproveMedicalPayload {
  specialistName?: string
  conclusion?: string
  note?: string
}

export interface ResubmitItemPayload {
  horseId: string
  doc: string
  reason?: string
}

export interface RequestResubmissionPayload {
  specialistName?: string
  reason: string
  items: ResubmitItemPayload[]
}

export interface UpdateClearanceItemPayload {
  type: string
  note?: string
  photos?: string[]
  status?: string
}

export interface AddClearanceItemPayload {
  type: string
  label?: string
  note?: string
}

export interface MarkHorseClearedPayload {
  horseId: string
  cleared: boolean
}

export const specialistApi = {
  // Flow 1: Duyệt hồ sơ ngựa
  getVerificationBookings: async (): Promise<BackendBookingResponse[]> => {
    const res = await apiClient.get<ApiResponse<BackendBookingResponse[]>>('/specialist/bookings/verification')
    return res.data.data
  },

  getBookingDetail: async (id: string | number): Promise<BackendBookingResponse> => {
    const res = await apiClient.get<ApiResponse<BackendBookingResponse>>(`/specialist/bookings/${id}`)
    return res.data.data
  },

  approveMedical: async (id: string | number, payload: ApproveMedicalPayload = {}): Promise<BackendBookingResponse> => {
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>(`/specialist/bookings/${id}/verify-horse`, payload)
    return res.data.data
  },

  requestResubmission: async (id: string | number, payload: RequestResubmissionPayload): Promise<BackendBookingResponse> => {
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>(`/specialist/bookings/${id}/request-resubmission`, payload)
    return res.data.data
  },

  // Flow 2: Làm giấy tờ kiểm dịch & hải quan
  getClearanceBookings: async (): Promise<BackendBookingResponse[]> => {
    const res = await apiClient.get<ApiResponse<BackendBookingResponse[]>>('/specialist/bookings/clearance')
    return res.data.data
  },

  acceptWaybill: async (id: string | number): Promise<BackendBookingResponse> => {
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>(`/specialist/bookings/${id}/accept-waybill`)
    return res.data.data
  },

  updateClearanceItem: async (id: string | number, payload: UpdateClearanceItemPayload): Promise<BackendBookingResponse> => {
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>(`/specialist/bookings/${id}/clearance-items`, payload)
    return res.data.data
  },

  addClearanceItem: async (id: string | number, payload: AddClearanceItemPayload): Promise<BackendBookingResponse> => {
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>(`/specialist/bookings/${id}/add-clearance-item`, payload)
    return res.data.data
  },

  markHorseCleared: async (id: string | number, horseId: string, cleared: boolean): Promise<BackendBookingResponse> => {
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>(`/specialist/bookings/${id}/clearance-horse`, { horseId, cleared })
    return res.data.data
  },

  completeClearance: async (id: string | number): Promise<BackendBookingResponse> => {
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>(`/specialist/bookings/${id}/complete-clearance`)
    return res.data.data
  },
}
