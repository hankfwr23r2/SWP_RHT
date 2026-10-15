// Báo cáo chuyến đã hoàn thành. Sau này: GET /api/trip-reports
import type { TripReport } from '../types/trip'

export const reportsApi = {
  list: async (): Promise<TripReport[]> => [],
}
export type { TripReport }
