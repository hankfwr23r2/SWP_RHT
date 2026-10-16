// Báo cáo chuyến đã hoàn thành. Gọi GET /api/v1/trip-reports
import apiClient, { type ApiResponse } from '../api/client'
import type { TripReport } from '../types/trip'

export const reportsApi = {
  list: async (): Promise<TripReport[]> => {
    try {
      const res = await apiClient.get<ApiResponse<TripReport[]>>('/trip-reports')
      return res.data.data ?? []
    } catch {
      return []
    }
  },
}
export type { TripReport }
