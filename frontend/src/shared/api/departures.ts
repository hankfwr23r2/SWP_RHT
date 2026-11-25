import apiClient, { type ApiResponse } from './client'
import { formResponseOptions, readFormResponse } from './customerAddresses'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type Departure = Schemas['DepartureResponse']
export type DepartureDecision = Schemas['DepartureDecisionResponseDto']
export type DepartureCreate = Schemas['DepartureCreateRequest']
export type DepartureDelayRequest = Schemas['DepartureDelayRequestDto']
export type RouteOption = Schemas['RouteResponse']

export const departuresApi = {
  decisions: async (from: string, to: string) =>
    (await apiClient.get<ApiResponse<DepartureDecision[]>>('/departures/decisions', { params: { from, to } })).data.data,
  confirmRun: async (id: number) => (await apiClient.post<ApiResponse<DepartureDecision>>(`/departures/${id}/confirm-run`, {})).data.data,
  delay: async (id: number, body: DepartureDelayRequest) =>
    readFormResponse<Departure>(await apiClient.post(`/departures/${id}/delay`, body, formResponseOptions)),
  cancel: async (id: number, reason: string) => (await apiClient.post<ApiResponse<Departure>>(`/departures/${id}/cancel`, { reason })).data.data,
  create: async (body: DepartureCreate) => readFormResponse<Departure>(await apiClient.post('/departures', body, formResponseOptions)),
  routes: async () => (await apiClient.get<ApiResponse<RouteOption[]>>('/routes')).data.data,
}
