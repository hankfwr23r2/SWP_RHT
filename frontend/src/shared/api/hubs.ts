import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

type Schemas = components['schemas']
type HubHorse = Required<Pick<Schemas['HorseStayResponseDto'], 'id' | 'horseName' | 'bookingCode' | 'stayType' | 'actualStartAt'>>
type HubVehicle = Required<Pick<Schemas['DispatchVehicleResponseDto'], 'id' | 'licensePlate'>> & { tripCode: string | null }
type HubVehicleGroup = { count: number; vehicles: HubVehicle[] }
type HubStaffRole = Required<Pick<Schemas['StaffAccountResponseDto'], 'roleCode'>> & { count: number }
type HubDeparture = Required<Pick<Schemas['DepartureResponse'], 'id' | 'routeCode' | 'status'>> & {
  originName: string
  destinationName: string
  departureAt: string
}
type HubIncident = Required<Pick<Schemas['IncidentResponseDto'], 'id' | 'title' | 'severity' | 'status' | 'reportedAt'>>

export type HubOverview = Required<Pick<Schemas['LocationResponse'], 'id' | 'code' | 'name' | 'countryCode'>> & {
  provinceName: string | null
  countryName: string
  headquarters: boolean
  horseCount: number
  horses: HubHorse[]
  stalls: Required<Pick<Schemas['HubStallInventoryResponseDto'], 'total' | 'available'>>
  fleet: Record<'idle' | 'running' | 'returning' | 'maintenance', HubVehicleGroup>
  staff: HubStaffRole[]
  arrivals: HubDeparture[]
  departures: HubDeparture[]
  openIncidentCount: number
  incidents: HubIncident[]
}

export const hubsApi = {
  overview: async () => (await apiClient.get<ApiResponse<HubOverview[]>>('/hubs/overview')).data.data,
}
