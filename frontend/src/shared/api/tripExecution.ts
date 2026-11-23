// Tổ xe thực hiện lượt xe theo điểm dừng (B2). Mọi thao tác trả về lượt xe nhìn từ tổ xe.
import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type CrewTrip = Schemas['CrewTripResponseDto']
export type Handover = Schemas['HandoverResponseDto']
export type HandoverType = NonNullable<Schemas['HandoverCreateRequestDto']['handoverType']>

const get = async <T>(url: string) => (await apiClient.get<ApiResponse<T>>(url)).data.data
const post = async <T>(url: string, body?: unknown) => (await apiClient.post<ApiResponse<T>>(url, body)).data.data

export const tripExecutionApi = {
  trip: (tripId: number) => get<CrewTrip>(`/crew/vehicle-trips/${tripId}`),
  start: (tripId: number) => post<CrewTrip>(`/vehicle-trips/${tripId}/start`),
  complete: (tripId: number) => post<CrewTrip>(`/vehicle-trips/${tripId}/complete`),
  arrive: (stopId: number) => post<CrewTrip>(`/trip-stops/${stopId}/arrive`),
  depart: (stopId: number) => post<CrewTrip>(`/trip-stops/${stopId}/depart`),
  openHandover: (stopId: number, handoverType: HandoverType) => post<CrewTrip>(`/trip-stops/${stopId}/handovers`, { handoverType }),
  scan: (handoverId: number, horseLegId: number, microchip: string) => post<CrewTrip>(`/handovers/${handoverId}/horses`, { horseLegId, microchip }),
  completeHandover: (handoverId: number) => post<CrewTrip>(`/handovers/${handoverId}/complete`),
}
