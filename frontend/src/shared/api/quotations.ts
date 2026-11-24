import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type Quotation = Schemas['QuotationResponseDto']
export type QuotationLine = Schemas['QuotationLineResponseDto']

export const quotationsApi = {
  ofBooking: async (bookingId: number) =>
    (await apiClient.get<ApiResponse<Quotation[]>>(`/bookings/${bookingId}/quotations`)).data.data,
  draft: async (bookingId: number) => (await apiClient.post<ApiResponse<Quotation>>(`/bookings/${bookingId}/quotations`, {})).data.data,
  adjust: async (id: number, adjustment: number, reason: string) =>
    (await apiClient.post<ApiResponse<Quotation>>(`/quotations/${id}/adjust`, { adjustment, reason })).data.data,
  send: async (id: number) => (await apiClient.post<ApiResponse<Quotation>>(`/quotations/${id}/send`, {})).data.data,
  accept: async (id: number) => (await apiClient.post<ApiResponse<Quotation>>(`/quotations/${id}/accept`, {})).data.data,
  reject: async (id: number, reason: string) =>
    (await apiClient.post<ApiResponse<Quotation>>(`/quotations/${id}/reject`, { reason })).data.data,
}
