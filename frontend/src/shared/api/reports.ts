// Báo cáo vận hành của quản lý (B6). from, to: ISO, lượt xe có giờ bắt đầu kế hoạch trong [from, to)
import apiClient, { type ApiResponse } from './client'
import type { TripReturnProgress } from './dispatch'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type TripReportRow = Schemas['TripReportRowResponseDto'] & TripReturnProgress
export type TripReport = Schemas['TripReportResponseDto']
export type OperationsSummary = Schemas['OperationsSummaryResponseDto']
export type RevenueReport = Schemas['RevenueReportResponseDto']

const get = async <T>(url: string, params?: Record<string, string>) => (await apiClient.get<ApiResponse<T>>(url, { params })).data.data

export const reportsApi = {
  trips: (from: string, to: string) => get<TripReportRow[]>('/reports/trips', { from, to }),
  trip: (id: number) => get<TripReport>(`/reports/trips/${id}`),
  summary: (from: string, to: string) => get<OperationsSummary>('/reports/operations', { from, to }),
  // Doanh thu: đơn theo ngày trả cọc đầu tiên trong [from, to)
  revenue: (from: string, to: string) => get<RevenueReport>('/reports/revenue', { from, to }),
}
