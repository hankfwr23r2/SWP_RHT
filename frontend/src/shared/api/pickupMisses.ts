import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type PickupMiss = Schemas['PickupMissResponseDto']
export type PickupChoice = Schemas['PickupMissChoiceRequestDto']
export type PickupLiability = Schemas['PickupMissLiabilityRequestDto']['liability']

export const pickupMissesApi = {
  list: async (bookingId: number) =>
    (await apiClient.get<ApiResponse<PickupMiss[]>>(`/bookings/${bookingId}/pickup-misses`)).data.data,
  choose: async (horseId: number, request: PickupChoice) =>
    (await apiClient.post<ApiResponse<PickupMiss>>(`/booking-horses/${horseId}/pickup-choices`, request)).data.data,
  confirmLiability: async (horseId: number, liability: PickupLiability) =>
    (await apiClient.post<ApiResponse<PickupMiss>>(`/booking-horses/${horseId}/confirm-pickup-liability`, { liability })).data.data,
}
