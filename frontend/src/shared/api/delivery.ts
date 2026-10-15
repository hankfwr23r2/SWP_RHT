import apiClient, { type ApiResponse } from './client'
import type { BackendBookingResponse } from './bookings'

export interface DeliveryArrivePayload {
  photoUrl?: string
  note?: string
}

export interface HandoverCompletePayload {
  recipientName: string
  recipientPhone: string
  recipientSignature: string
  handoverPhotoUrl: string
  originalDocsHandedOver: boolean
  note?: string
}

export interface DeliveryTimeoutPayload {
  timeoutReason: string // UNREACHABLE, DESTINATION_UNREADY, CONSIGNEE_REJECTED
  selectedAction: string // STABLE_NEARBY, RETURN_TO_HQ
  partnerName?: string
  partnerPhone?: string
  estimatedCost?: number
  note: string
}

export interface ReturnToHqPayload {
  vehicleId: number
  driverUsername: string
  proofImageUrl?: string
  vehicleCondition?: string // GOOD, NEEDS_MAINTENANCE
  note?: string
}

export const backendDeliveryApi = {
  // 1. Kíp xe check-in đến điểm giao đích & kích hoạt đếm ngược 2h -> POST /api/v1/delivery/{id}/arrive
  arrive: async (id: number | string, payload?: DeliveryArrivePayload): Promise<BackendBookingResponse> => {
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>(`/delivery/${id}/arrive`, payload ?? {})
    return res.data.data
  },

  // 2. Ký Biên bản Bàn giao & Nghiệm thu -> POST /api/v1/delivery/{id}/complete
  completeHandover: async (id: number | string, payload: HandoverCompletePayload): Promise<BackendBookingResponse> => {
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>(`/delivery/${id}/complete`, payload)
    return res.data.data
  },

  // 3. Xử lý ngoại lệ quá 2h không nhận -> POST /api/v1/delivery/{id}/timeout
  reportTimeout: async (id: number | string, payload: DeliveryTimeoutPayload): Promise<BackendBookingResponse> => {
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>(`/delivery/${id}/timeout`, payload)
    return res.data.data
  },

  // 4. Kíp xe xác nhận đưa xe về Trụ sở chính Việt Nam -> Xe chuyển sang AVAILABLE -> POST /api/v1/delivery/vehicles/return-to-hq
  returnToHq: async (payload: ReturnToHqPayload): Promise<string> => {
    const res = await apiClient.post<ApiResponse<string>>('/delivery/vehicles/return-to-hq', payload)
    return res.data.data
  },
}
