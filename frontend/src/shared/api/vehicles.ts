import apiClient, { type ApiResponse } from './client'
import { formResponseOptions, readFormResponse } from './customerAddresses'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type Vehicle = Schemas['VehicleResponseDto']
export type VehicleClass = Schemas['VehicleClassResponseDto']
export type VehicleCreate = Schemas['VehicleCreateRequestDto']
export type VehicleUpdate = Schemas['VehicleUpdateRequestDto']
export type VehicleStatusChange = Schemas['VehicleStatusChangeRequestDto']
export type CatalogLocation = Schemas['LocationResponse']

export const vehiclesApi = {
  list: async () =>
    (await apiClient.get<ApiResponse<Vehicle[]>>('/vehicles')).data.data,
  classes: async () =>
    (await apiClient.get<ApiResponse<VehicleClass[]>>('/vehicle-classes')).data.data,
  create: async (body: VehicleCreate) =>
    readFormResponse<Vehicle>(await apiClient.post('/vehicles', body, formResponseOptions)),
  update: async (id: number, body: VehicleUpdate) =>
    readFormResponse<Vehicle>(await apiClient.put(`/vehicles/${id}`, body, formResponseOptions)),
  startMaintenance: async (id: number, body: VehicleStatusChange) =>
    readFormResponse<Vehicle>(await apiClient.post(`/vehicles/${id}/start-maintenance`, body, formResponseOptions)),
  reactivate: async (id: number, body: VehicleStatusChange) =>
    readFormResponse<Vehicle>(await apiClient.post(`/vehicles/${id}/reactivate`, body, formResponseOptions)),
  retire: async (id: number, body: VehicleStatusChange) =>
    readFormResponse<Vehicle>(await apiClient.post(`/vehicles/${id}/retire`, body, formResponseOptions)),
}

export const catalogLocationsApi = {
  list: async (type: NonNullable<CatalogLocation['locationType']>) =>
    (await apiClient.get<ApiResponse<CatalogLocation[]>>('/locations', { params: { type } })).data.data,
}
