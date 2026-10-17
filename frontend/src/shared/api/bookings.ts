import apiClient, { type ApiResponse } from './client'
import type { BackendIncidentResponse } from './incidents'

export interface BookingPartyDto {
  name: string
  phone: string
}

export interface BookingHorseDto {
  horseId?: string
  name: string
  microchip?: string
  breed?: string
  sex?: string
  stall?: string
  feedPackage?: string
  waterPlan?: string
  insuranceOpted?: boolean
}

export interface CreateBookingPayload {
  type: string
  originName: string
  originCountry?: string
  destName: string
  destCountry?: string
  departAt: number
  consignor: BookingPartyDto
  consignee: BookingPartyDto
  horses: BookingHorseDto[]
}

export interface BackendBookingResponse {
  id: string
  numericId: number
  type: string
  customer: string
  customerName: string
  status: string
  createdAt: number
  departAt: number
  originName: string
  originCountry?: string
  destName: string
  destCountry?: string
  consignor: BookingPartyDto
  consignee: BookingPartyDto
  totalHorses: number
  horses: BookingHorseDto[]
  waybillNo?: string
  // Kết quả từng bước: backend lưu dạng JSON theo đúng hình dạng của frontend
  trips?: Array<{ tripId: string; vehicleId: string; driverId?: string; escortId?: string; horseIds: string[] } & Record<string, unknown>>
  route?: unknown
  gate?: string
  plan?: unknown
  quote?: unknown
  payment?: Record<string, unknown>
  medical?: unknown
  clearance?: unknown
  settlement?: unknown
  rating?: unknown
  intake?: unknown
  rejection?: { at: number; by: string; role: 'manager' | 'coordinator'; reason: string }
  incidents?: BackendIncidentResponse[]
  history?: Array<{ time: number; actor: string; text: string }>
}

export function toNumericBookingId(identifier: number | string): number {
  if (typeof identifier === 'number') {
    if (Number.isSafeInteger(identifier) && identifier > 0) return identifier
    throw new Error('Mã đơn hàng không hợp lệ.')
  }

  const value = identifier.trim()
  const match = /^(?:BK-)?(\d+)$/i.exec(value)
  const numericId = match ? Number(match[1]) : Number.NaN
  if (!Number.isSafeInteger(numericId) || numericId <= 0) {
    throw new Error(`Mã đơn hàng không hợp lệ: ${identifier}`)
  }
  return numericId
}

export const backendBookingApi = {
  // 1. Khách hàng tạo đơn vận chuyển mới (POST /api/v1/customer/bookings)
  create: async (payload: CreateBookingPayload): Promise<BackendBookingResponse> => {
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>('/customer/bookings', payload)
    return res.data.data
  },

  // 2. Khách hàng xem danh sách đơn của mình (GET /api/v1/customer/bookings)
  getCustomerBookings: async (): Promise<BackendBookingResponse[]> => {
    const res = await apiClient.get<ApiResponse<BackendBookingResponse[]>>('/customer/bookings')
    return res.data.data
  },

  // 3. Khách hàng xem chi tiết đơn (GET /api/v1/customer/bookings/{id})
  getCustomerBookingById: async (id: number | string): Promise<BackendBookingResponse> => {
    const numericId = toNumericBookingId(id)
    const res = await apiClient.get<ApiResponse<BackendBookingResponse>>(`/customer/bookings/${numericId}`)
    return res.data.data
  },

  getAssignedBookings: async (): Promise<BackendBookingResponse[]> => {
    const res = await apiClient.get<ApiResponse<BackendBookingResponse[]>>('/bookings/assigned')
    return res.data.data
  },

  resubmitCustomerBooking: async (id: number | string): Promise<BackendBookingResponse> => {
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>(`/customer/bookings/${id}/resubmit`)
    return res.data.data
  },

  // 4. Manager lấy danh sách đơn chờ tiếp nhận (GET /api/v1/manager/bookings/intake)
  getIntakeBookings: async (status = 'pending_intake'): Promise<BackendBookingResponse[]> => {
    const res = await apiClient.get<ApiResponse<BackendBookingResponse[]>>('/manager/bookings/intake', {
      params: { status },
    })
    return res.data.data
  },

  // 5. Manager lấy danh sách tất cả đơn (GET /api/v1/manager/bookings)
  getAllBookings: async (): Promise<BackendBookingResponse[]> => {
    const res = await apiClient.get<ApiResponse<BackendBookingResponse[]>>('/manager/bookings')
    return res.data.data
  },

  // Coordinator xem danh sách đơn được phân công qua endpoint có quyền phù hợp.
  getCoordinatorBookings: async (): Promise<BackendBookingResponse[]> => {
    const res = await apiClient.get<ApiResponse<BackendBookingResponse[]>>('/coordinator/bookings')
    return res.data.data
  },

  // 6. Coordinator chốt xe & lộ trình (POST /api/v1/coordinator/bookings/{id}/fleet-plan)
  confirmFleetPlan: async (id: number | string, payload: { by?: string; trips: Array<{ vehicleId: string; horseIds: string[]; driverId?: string; escortId?: string; tripId?: string }>; route?: any; gate?: string; note?: string }): Promise<BackendBookingResponse> => {
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>(`/coordinator/bookings/${id}/fleet-plan`, payload)
    return res.data.data
  },

  // 7. Manager phân công kíp xe (POST /api/v1/manager/bookings/{id}/assign-crew)
  assignCrew: async (id: number | string, payload: { by?: string; picks: Array<{ tripId: string; driverId: string; escortId: string }> }): Promise<BackendBookingResponse> => {
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>(`/manager/bookings/${id}/assign-crew`, payload)
    return res.data.data
  },

  // 8. Manager duyệt và phát hành báo giá 24h (POST /api/v1/manager/bookings/{id}/send-quote)
  sendQuote: async (id: number | string, payload: { by?: string; adjustments: Array<{ label: string; amount: number }>; lines?: any[]; subtotal?: number; total?: number; deposit?: number; balance?: number }): Promise<BackendBookingResponse> => {
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>(`/manager/bookings/${id}/send-quote`, payload)
    return res.data.data
  },

  // 9. Manager trả đơn về Specialist / Coordinator (POST /api/v1/manager/bookings/{id}/send-back)
  sendBack: async (id: number | string, payload: { by?: string; to: 'specialist' | 'coordinator'; reason: string }): Promise<BackendBookingResponse> => {
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>(`/manager/bookings/${id}/send-back`, payload)
    return res.data.data
  },

  // 10. Customer thanh toán đặt cọc 30% (POST /api/v1/customer/bookings/{id}/pay-deposit)
  payDeposit: async (id: number | string, payload?: { paymentMethod?: string; amount?: number; transactionCode?: string }): Promise<BackendBookingResponse> => {
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>(`/customer/bookings/${id}/pay-deposit`, payload ?? {})
    return res.data.data
  },

  // 12. Manager kích hoạt thẩm định song song (POST /api/v1/manager/bookings/{id}/activate)
  activate: async (id: number | string, payload: { by?: string; specialist?: any; coordinator?: any }): Promise<BackendBookingResponse> => {
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>(`/manager/bookings/${id}/activate`, payload)
    return res.data.data
  },

  // 13. Manager/Coordinator từ chối đơn hàng (POST /api/v1/manager/bookings/{id}/reject hoặc /coordinator/bookings/{id}/reject)
  reject: async (id: number | string, payload: { by?: string; role?: 'manager' | 'coordinator'; reason?: string }): Promise<BackendBookingResponse> => {
    const endpoint = payload.role === 'coordinator' ? `/coordinator/bookings/${id}/reject` : `/manager/bookings/${id}/reject`
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>(endpoint, payload)
    return res.data.data
  },

  // Khách từ chối báo giá khi đơn đang chờ đặt cọc (POST /api/v1/customer/bookings/{id}/reject-quote)
  rejectQuote: async (id: number | string, payload: { reason?: string }): Promise<BackendBookingResponse> => {
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>(`/customer/bookings/${id}/reject-quote`, payload)
    return res.data.data
  },

  // 14. Customer gửi lại hồ sơ ngựa sau khi được yêu cầu bổ sung (POST /api/v1/customer/bookings/{id}/resubmit)
  resubmitDocuments: async (id: number | string): Promise<BackendBookingResponse> => {
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>(`/customer/bookings/${id}/resubmit`)
    return res.data.data
  },

  // 15. Customer thanh toán 70% còn lại trước giờ khởi hành (ngày D - Flow 4)
  payBalance: async (id: number | string, payload?: { paymentMethod?: string; amount?: number; transactionCode?: string }): Promise<BackendBookingResponse> => {
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>(`/customer/bookings/${id}/pay-balance`, payload ?? {})
    return res.data.data
  },

  // 16. Kíp xe (Driver / Escort) lấy danh sách đơn vận chuyển (GET /api/v1/field/bookings)
  getFieldBookings: async (): Promise<BackendBookingResponse[]> => {
    const res = await apiClient.get<ApiResponse<BackendBookingResponse[]>>('/field/bookings')
    return res.data.data
  },

  // 17. Kíp xe cập nhật tiến độ chuyến xe (POST /api/v1/field/bookings/{id}/trip-update)
  updateTripProgress: async (id: number | string, payload: { tripId: string; action: string; actor?: string; payload?: Record<string, unknown> }): Promise<BackendBookingResponse> => {
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>(`/field/bookings/${id}/trip-update`, payload)
    return res.data.data
  },

  // 18. Driver/Escort lấy CHÍNH XÁC chuyến được phân công cho mình (GET /api/v1/field/bookings/mine)
  myFieldBookings: async (): Promise<BackendBookingResponse[]> => {
    const res = await apiClient.get<ApiResponse<BackendBookingResponse[]>>('/field/bookings/mine')
    return res.data.data
  },
}
