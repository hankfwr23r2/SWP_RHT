// Báo cáo vận hành của quản lý (B6). from, to: ISO, lượt xe có giờ bắt đầu kế hoạch trong [from, to)
import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type TripReportRow = Schemas['TripReportRowResponseDto']
export type TripReport = Schemas['TripReportResponseDto']
export type OperationsSummary = Schemas['OperationsSummaryResponseDto']

const get = async <T>(url: string, params?: Record<string, string>) => (await apiClient.get<ApiResponse<T>>(url, { params })).data.data

export const reportsApi = {
  trips: (from: string, to: string) => get<TripReportRow[]>('/reports/trips', { from, to }),
  trip: (id: number) => get<TripReport>(`/reports/trips/${id}`),
  summary: (from: string, to: string) => get<OperationsSummary>('/reports/operations', { from, to }),
}
