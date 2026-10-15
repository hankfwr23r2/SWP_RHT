import apiClient, { type ApiResponse } from './client'

export interface HorseHealthLogDto {
  id?: number
  horseId: number
  bookingId?: number
  escortStaffId?: number
  healthStatus: string
  temperature?: number
  weight?: number
  symptoms?: string
  logTime?: string
}

export interface CreateHealthLogPayload {
  horseId: number
  bookingId?: number
  escortStaffId?: number
  healthStatus: string
  temperature?: number
  weight?: number
  symptoms?: string
}

export const healthLogsApi = {
  // 1. Ghi nhận nhật ký an sinh / sức khỏe ngựa (POST /api/v1/horse-health-logs)
  create: async (payload: CreateHealthLogPayload): Promise<HorseHealthLogDto> => {
    const res = await apiClient.post<ApiResponse<HorseHealthLogDto>>('/horse-health-logs', payload)
    return res.data.data
  },

  // 2. Lấy danh sách nhật ký an sinh theo đơn hàng (GET /api/v1/horse-health-logs/booking/{bookingId})
  getByBooking: async (bookingId: number): Promise<HorseHealthLogDto[]> => {
    const res = await apiClient.get<ApiResponse<HorseHealthLogDto[]>>(`/horse-health-logs/booking/${bookingId}`)
    return res.data.data
  },

  // 3. Lấy lịch sử an sinh của một cá thể ngựa (GET /api/v1/horse-health-logs/horse/{horseId})
  getByHorse: async (horseId: number): Promise<HorseHealthLogDto[]> => {
    const res = await apiClient.get<ApiResponse<HorseHealthLogDto[]>>(`/horse-health-logs/horse/${horseId}`)
    return res.data.data
  },
}
