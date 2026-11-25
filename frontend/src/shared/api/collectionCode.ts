import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

type CollectionCode = components['schemas']['CollectionCodeResponseDto']

export const collectionCodeApi = {
  read: async (bookingId: number) =>
    (await apiClient.get<ApiResponse<CollectionCode>>(`/bookings/${bookingId}/collection-code`)).data.data,
  confirm: async (bookingId: number) =>
    (await apiClient.post<ApiResponse<CollectionCode>>(`/bookings/${bookingId}/confirm-collection`)).data.data,
}
