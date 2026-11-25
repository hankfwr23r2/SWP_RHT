import apiClient, { type ApiResponse } from './client'
import type { BookingCancellation, BookingDetail, BookingOrder, BookingStatus } from './bookingOrders'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type ManagerOrderRow = Schemas['BookingSummaryResponseDto']
export type DocumentDelay = Schemas['ReportDocumentDelayRequestDto']
export const SUMMARY_PAGE_SIZE = 50

export const managerOrdersApi = {
  summaries: async (statuses: BookingStatus[], page: number) =>
    (await apiClient.get<ApiResponse<ManagerOrderRow[]>>('/bookings/summaries', { params: { statuses: statuses.join(','), page } })).data.data,
  detail: async (id: number) => (await apiClient.get<ApiResponse<BookingDetail>>(`/bookings/${id}/review-detail`)).data.data,
  missingDocuments: async () => (await apiClient.get<ApiResponse<BookingOrder[]>>('/bookings/missing-documents')).data.data,
  intake: async (id: number, specialistId: number) => (await apiClient.post<ApiResponse<BookingOrder>>(`/bookings/${id}/intake`, { specialistId })).data.data,
  reject: async (id: number, reason: string) =>
    (await apiClient.post<ApiResponse<BookingOrder>>(`/bookings/${id}/reject`, { reason })).data.data,
  reportDocumentDelay: async (id: number, body: DocumentDelay) =>
    (await apiClient.post<ApiResponse<BookingOrder>>(`/bookings/${id}/report-document-delay`, body)).data.data,
  cancellationPreview: async (id: number, byCompany: boolean) =>
    (await apiClient.get<ApiResponse<BookingCancellation>>(`/bookings/${id}/manager-cancellation-preview`, { params: { byCompany } })).data.data,
  cancelForCompany: async (id: number, reason: string) =>
    (await apiClient.post<ApiResponse<BookingCancellation>>(`/bookings/${id}/cancel-for-company`, { reason })).data.data,
  // Khoản thu hộ không hoàn nên không chọn dòng nào
  cancelForCustomer: async (id: number, reason: string) =>
    (await apiClient.post<ApiResponse<BookingCancellation>>(`/bookings/${id}/cancel-for-customer`, { reason, unspentQuotationLineIds: [] })).data.data,
}
