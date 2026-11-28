import apiClient, { type ApiResponse } from './client'
import { formResponseOptions, readFormResponse } from './customerAddresses'
import type { components, paths } from './generated/schema'

type Schemas = components['schemas']
export type Location = Schemas['LocationResponse']
export type LocationRequest = Schemas['LocationRequest']
export type ServiceArea = Schemas['HubServiceAreaResponse']
export type Route = Schemas['RouteResponse']
export type RouteRequest = Schemas['RouteRequest']
export type RouteStopRequest = Schemas['RouteStopRequest']
export type Schedule = Schemas['DepartureScheduleResponse']
export type ScheduleRequest = Schemas['DepartureScheduleRequest']
export type Departure = Schemas['DepartureResponse']

type LocationQuery = paths['/locations']['get']['parameters']['query']
type DepartureQuery = paths['/departures']['get']['parameters']['query']

const get = async <T>(path: string, params?: object) =>
  (await apiClient.get<ApiResponse<T>>(path, { params })).data.data

const save = async <T>(path: string, body: object, id?: number) => readFormResponse<T>(
  id == null ? await apiClient.post(path, body, formResponseOptions)
    : await apiClient.put(`${path}/${id}`, body, formResponseOptions),
)

const change = async (path: string) => readFormResponse(await apiClient.post(path, undefined, formResponseOptions))

export const networkApi = {
  locations: (params?: LocationQuery) => get<Location[]>('/locations', params),
  saveLocation: (body: LocationRequest, id?: number) => save<Location>('/locations', body, id),
  activateLocation: (id: number) => change(`/locations/${id}/activate`),
  deactivateLocation: (id: number) => change(`/locations/${id}/deactivate`),
  areas: () => get<ServiceArea[]>('/hub-service-areas'),
  assignArea: async (province: string, body: Schemas['HubServiceAreaRequest']) =>
    readFormResponse<ServiceArea>(await apiClient.put(`/hub-service-areas/${encodeURIComponent(province)}`, body, formResponseOptions)),
  removeArea: async (province: string) =>
    readFormResponse(await apiClient.delete(`/hub-service-areas/${encodeURIComponent(province)}`, formResponseOptions)),
  routes: () => get<Route[]>('/routes'),
  route: (id: number) => get<Route>(`/routes/${id}`),
  saveRoute: (body: RouteRequest, id?: number) => save<Route>('/routes', body, id),
  activateRoute: (id: number) => change(`/routes/${id}/activate`),
  deactivateRoute: (id: number) => change(`/routes/${id}/deactivate`),
  schedules: (routeId?: number) => get<Schedule[]>('/departure-schedules', { routeId }),
  saveSchedule: (body: ScheduleRequest, id?: number) => save<Schedule>('/departure-schedules', body, id),
  pauseSchedule: (id: number) => change(`/departure-schedules/${id}/pause`),
  resumeSchedule: (id: number) => change(`/departure-schedules/${id}/resume`),
  endSchedule: (id: number) => change(`/departure-schedules/${id}/end`),
  departures: (params?: DepartureQuery) => get<Departure[]>('/departures', params),
}
