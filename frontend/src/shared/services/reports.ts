// Báo cáo chuyến đã hoàn thành. Sau này: GET /api/trip-reports
import { seedTripHistory, type TripReport } from './mock/trip-history'

export const reportsApi = {
  list: async (): Promise<TripReport[]> => seedTripHistory().sort((a, b) => b.completedAt - a.completedAt),
}
export type { TripReport }
