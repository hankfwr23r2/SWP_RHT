import apiClient, { type ApiResponse } from './client'
import { formResponseOptions, readFormResponse } from './customerAddresses'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type Departure = Schemas['DepartureResponse']
export type DepartureDetail = Schemas['DepartureDetailResponseDto']
export type DepartureDecision = Schemas['DepartureDecisionResponseDto'] & { canMerge?: boolean }
export type DepartureCreate = Schemas['DepartureCreateRequest']
export type DepartureDelayRequest = Schemas['DepartureDelayRequestDto']
export type DepartureMergeRequest = Pick<Schemas['DepartureCancelRequest'], 'reason'> & { targetDepartureId: number }
export type RouteOption = Schemas['RouteResponse']

export const departuresApi = {
  decisions: async (from: string, to: string) =>
    (await apiClient.get<ApiResponse<DepartureDecision[]>>('/departures/decisions', { params: { from, to } })).data.data,
  detail: async (id: number) =>
    (await apiClient.get<ApiResponse<DepartureDetail>>(`/departures/${id}`)).data.data,
  removeVehicle: async (id: number, allotmentId: number, reason: string) =>
    readFormResponse<DepartureDetail>(await apiClient.post(`/departures/${id}/vehicles/${allotmentId}/remove`, { reason }, formResponseOptions)),
  confirmRun: async (id: number) =>
    readFormResponse<DepartureDecision>(await apiClient.post(`/departures/${id}/confirm-run`, {}, formResponseOptions)),
  mergeTargets: async (id: number) =>
    (await apiClient.get<ApiResponse<Departure[]>>(`/departures/${id}/merge-targets`)).data.data,
  merge: async (id: number, body: DepartureMergeRequest) =>
    readFormResponse<Departure>(await apiClient.post(`/departures/${id}/merge`, body, formResponseOptions)),
  delay: async (id: number, body: DepartureDelayRequest) =>
    readFormResponse<Departure>(await apiClient.post(`/departures/${id}/delay`, body, formResponseOptions)),
  cancel: async (id: number, reason: string) => (await apiClient.post<ApiResponse<Departure>>(`/departures/${id}/cancel`, { reason })).data.data,
  create: async (body: DepartureCreate) => readFormResponse<Departure>(await apiClient.post('/departures', body, formResponseOptions)),
  routes: async () => (await apiClient.get<ApiResponse<RouteOption[]>>('/routes')).data.data,
}
