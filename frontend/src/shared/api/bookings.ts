import apiClient, { type ApiResponse } from './client'

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
    const res = await apiClient.get<ApiResponse<BackendBookingResponse>>(`/customer/bookings/${id}`)
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

  // 8. Manager duyệt và phát hành báo giá 48h (POST /api/v1/manager/bookings/{id}/send-quote)
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

  // 11. Customer từ chối báo giá (POST /api/v1/customer/bookings/{id}/reject-quote)
  rejectQuote: async (id: number | string, payload?: { reason?: string }): Promise<BackendBookingResponse> => {
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>(`/customer/bookings/${id}/reject-quote`, payload ?? {})
    return res.data.data
  },
}
