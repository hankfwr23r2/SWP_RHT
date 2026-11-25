import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

type Schemas = components['schemas']

export const stablingApi = {
  clocks: async (bookingId: number) => (await apiClient.get<ApiResponse<Schemas['StablingClockResponseDto'][]>>(
    `/bookings/${bookingId}/stabling-clocks`,
  )).data.data,
  ready: (stayId: number) => apiClient.post(`/horse-stays/${stayId}/ready`),
  recordReady: (stayId: number, body: Schemas['StablingReadyRequestDto']) =>
    apiClient.post(`/horse-stays/${stayId}/record-ready`, body),
  laterSlot: (stayId: number, body: Schemas['StablingLaterSlotRequestDto']) =>
    apiClient.post(`/horse-stays/${stayId}/choose-later-slot`, body),
  collect: (stayId: number, body: Schemas['StablingCollectionRequestDto']) =>
    apiClient.post(`/horse-stays/${stayId}/report-collection`, body),
}
