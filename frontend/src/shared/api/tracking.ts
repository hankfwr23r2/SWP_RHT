// Theo dõi hành trình từng con ngựa (B6)
import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type BookingJourney = Schemas['BookingJourneyResponseDto']
export type HorseJourney = Schemas['HorseJourneyResponseDto']
export type JourneyLeg = Schemas['JourneyLegDto']
export type JourneyStay = Schemas['JourneyStayDto']
export type JourneyHandover = Schemas['JourneyHandoverDto']
export type PublicTracking = Schemas['PublicTrackingResponseDto']
export type PublicHorse = Schemas['PublicHorseDto']

export const trackingApi = {
  // Nhân viên xem mọi đơn, khách chỉ xem đơn của mình
  bookingJourney: async (bookingId: number) => (await apiClient.get<ApiResponse<BookingJourney>>(`/bookings/${bookingId}/journey`)).data.data,
  publicTrack: async (codes: string[], phoneLast4: string) =>
    (await apiClient.post<ApiResponse<PublicTracking[]>>('/public/tracking', { codes, phoneLast4 })).data.data ?? [],
}
