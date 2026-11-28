import apiClient, { type ApiResponse } from './client'
import { formResponseOptions, readFormResponse } from './customerAddresses'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type HubStall = Schemas['HubStallResponseDto']
export type HubStallInventory = Schemas['HubStallInventoryResponseDto']
export type HubStallCreate = Schemas['HubStallCreateRequestDto']
type HubStallChange = Schemas['HubStallChangeRequestDto']

const path = (hubId: number) => `/locations/${hubId}/hub-stalls`

export const hubStallsApi = {
  inventory: async (hubId: number) =>
    (await apiClient.get<ApiResponse<HubStallInventory>>(path(hubId))).data.data,
  create: async (hubId: number, body: HubStallCreate) =>
    readFormResponse<HubStall>(await apiClient.post(path(hubId), body, formResponseOptions)),
  deactivate: async (hubId: number, stallId: number) =>
    readFormResponse<HubStall>(await apiClient.post(`${path(hubId)}/${stallId}/deactivate`, undefined, formResponseOptions)),
  move: async (stayId: number, body: HubStallChange) =>
    (await apiClient.post<ApiResponse<HubStall>>(`/horse-stays/${stayId}/change-stall`, body)).data.data,
}
