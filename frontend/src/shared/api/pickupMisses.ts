import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type PickupMiss = Schemas['PickupMissResponseDto']
export type PickupChoice = Schemas['PickupMissChoiceRequestDto']
export type PickupLiability = Schemas['PickupMissLiabilityRequestDto']['liability']
export type PickupAddOn = Schemas['PickupAddOnResponseDto']

export const pickupMissesApi = {
  list: async (bookingId: number) =>
    (await apiClient.get<ApiResponse<PickupMiss[]>>(`/bookings/${bookingId}/pickup-misses`)).data.data,
  choose: async (horseId: number, request: PickupChoice) =>
    (await apiClient.post<ApiResponse<PickupMiss>>(`/booking-horses/${horseId}/pickup-choices`, request)).data.data,
  confirmLiability: async (horseId: number, liability: PickupLiability) =>
    (await apiClient.post<ApiResponse<PickupMiss>>(`/booking-horses/${horseId}/confirm-pickup-liability`, { liability })).data.data,
  addOns: async (horseId: number) =>
    (await apiClient.get<ApiResponse<PickupAddOn[]>>(`/booking-horses/${horseId}/pickup-add-ons`)).data.data,
  completeAddOn: async (horseId: number, request: Schemas['CompletePickupAddOnRequestDto']) =>
    (await apiClient.post<ApiResponse<PickupAddOn>>(`/booking-horses/${horseId}/complete-pickup-add-on`, request)).data.data,
}
