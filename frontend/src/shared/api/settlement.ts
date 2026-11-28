import apiClient, { type ApiResponse } from './client'
import type { BookingPayment } from './payments'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type Settlement = Schemas['SettlementResponseDto']
export type CostItem = Schemas['CostItemResponseDto']

export const settlementApi = {
  get: async (bookingId: number) => (await apiClient.get<ApiResponse<Settlement>>(`/bookings/${bookingId}/settlement`)).data.data,
  approveCost: async (costItemId: number) => (await apiClient.post<ApiResponse<CostItem>>(`/cost-items/${costItemId}/approve`, {})).data.data,
  extraCharge: async (bookingId: number, costItemIds: number[]) =>
    (await apiClient.post<ApiResponse<BookingPayment>>(`/bookings/${bookingId}/settlement/extra-charges`, { costItemIds })).data.data,
  complete: async (bookingId: number) =>
    (await apiClient.post<ApiResponse<Settlement>>(`/bookings/${bookingId}/settlement/complete`, {})).data.data,
}
