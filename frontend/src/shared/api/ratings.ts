import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type Rating = Schemas['RatingResponseDto']

export const ratingsApi = {
  rate: async (bookingId: number, body: Schemas['RatingRequestDto']) =>
    (await apiClient.post<ApiResponse<Rating>>(`/bookings/${bookingId}/ratings`, body)).data.data,
}
