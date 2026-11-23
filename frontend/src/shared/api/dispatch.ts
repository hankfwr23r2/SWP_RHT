// Điều độ (B1): lượt xe, điểm dừng, tổ lái, chặng của ngựa. Kiểu lấy từ OpenAPI sinh tự động.
import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type VehicleTrip = Schemas['VehicleTripResponseDto']
export type TripStop = Schemas['TripStopResponseDto']
export type CrewAssignment = Schemas['CrewAssignmentResponseDto']
export type DispatchBooking = Schemas['DispatchBookingResponseDto']
export type DispatchHorse = Schemas['HorseDto']
export type HorseLeg = Schemas['HorseLegResponseDto']
export type TripHorse = Schemas['TripHorseResponseDto']
export type MyCrewAssignment = Schemas['MyCrewAssignmentResponseDto']
export type CrewCandidate = Schemas['CrewCandidateResponseDto']
export type DispatchVehicle = Schemas['VehicleResponseDto']
export type Departure = Schemas['DepartureResponse']
export type NetworkLocation = Schemas['LocationResponse']
export type VehicleTripCreate = Schemas['VehicleTripCreateRequestDto']
export type TripStopCreate = Schemas['TripStopRequestDto']
export type CrewRole = NonNullable<Schemas['CrewAssignmentRequestDto']['crewRole']>
export type TripType = NonNullable<VehicleTripCreate['tripType']>

const get = async <T>(url: string, params?: Record<string, string>) => (await apiClient.get<ApiResponse<T>>(url, { params })).data.data
const post = async <T>(url: string, body?: unknown) => (await apiClient.post<ApiResponse<T>>(url, body)).data.data

export const dispatchApi = {
  trips: (from: string, to: string) => get<VehicleTrip[]>('/vehicle-trips', { from, to }),
  trip: (id: number) => get<VehicleTrip>(`/vehicle-trips/${id}`),
  createTrip: (body: VehicleTripCreate) => post<VehicleTrip>('/vehicle-trips', body),
  cancelTrip: (id: number, reason: string) => post<VehicleTrip>(`/vehicle-trips/${id}/cancel`, { reason }),
  assignCrew: (tripId: number, staffId: number, crewRole: CrewRole) => post<VehicleTrip>(`/vehicle-trips/${tripId}/crew-assignments`, { staffId, crewRole }),
  releaseCrew: (assignmentId: number) => post<VehicleTrip>(`/crew-assignments/${assignmentId}/release`),
  vehicles: () => get<DispatchVehicle[]>('/dispatch/vehicles'),
  crewCandidates: (role: CrewRole) => get<CrewCandidate[]>('/dispatch/crew-candidates', { role }),
  bookings: (from: string, to: string) => get<DispatchBooking[]>('/dispatch/bookings', { from, to }),
  horsesOnTrip: (tripId: number) => get<TripHorse[]>(`/vehicle-trips/${tripId}/horse-legs`),
  assignHorse: (tripId: number, bookingHorseId: number, boardTripStopId: number, alightTripStopId: number) =>
    post<TripHorse[]>(`/vehicle-trips/${tripId}/horse-legs`, { bookingHorseId, boardTripStopId, alightTripStopId }),
  cancelLeg: (legId: number) => post<TripHorse[]>(`/horse-legs/${legId}/cancel`),
  departures: (from: string, to: string) => get<Departure[]>('/departures', { from, to }),
  locations: () => get<NetworkLocation[]>('/locations', { status: 'ACTIVE' }),
  myAssignments: () => get<MyCrewAssignment[]>('/crew-assignments/mine'),
  acknowledge: (assignmentId: number) => post<MyCrewAssignment>(`/crew-assignments/${assignmentId}/acknowledge`),
}
