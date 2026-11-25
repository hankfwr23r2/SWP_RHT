import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type CoordinatorTrip = Schemas['CoordinatorTripResponseDto']
export type CoordinatorTripHorse = Schemas['CoordinatorTripHorseResponseDto']

export const coordinatorAssignmentsApi = {
  assign: async (resource: 'departures' | 'vehicle-trips', id: number, request: Schemas['CoordinatorAssignmentRequestDto']) =>
    (await apiClient.post<ApiResponse<void>>('/' + resource + '/' + id + '/assign-coordinator', request)).data,
  takeOver: async (id: number, request: Schemas['CoordinatorTakeOverRequestDto']) =>
    (await apiClient.post<ApiResponse<void>>('/vehicle-trips/' + id + '/take-over', request)).data,
  mine: async () => (await apiClient.get<ApiResponse<CoordinatorTrip[]>>('/coordinator/vehicle-trips')).data.data,
  horses: async (id: number) =>
    (await apiClient.get<ApiResponse<CoordinatorTripHorse[]>>('/coordinator/vehicle-trips/' + id + '/horses')).data.data,
}
