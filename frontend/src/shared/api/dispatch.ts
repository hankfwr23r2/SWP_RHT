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
export type DispatchVehicle = Schemas['DispatchVehicleResponseDto']
export type Departure = Schemas['DepartureResponse']
export type NetworkLocation = Schemas['LocationResponse']
export type HorseStay = Schemas['HorseStayResponseDto']
export type VehicleTripCreate = Schemas['VehicleTripCreateRequestDto']
export type TripStopCreate = Schemas['TripStopRequestDto']
export type CrewRole = NonNullable<Schemas['CrewAssignmentRequestDto']['crewRole']>
export type PlannedTrip = Schemas['PlannedTripResponseDto']
export type PlannedTripCreate = Schemas['PlannedTripRequestDto']
export type LegType = NonNullable<HorseLeg['legType']>
export type TripType = NonNullable<VehicleTripCreate['tripType']>
export type AgentLegCreate = Schemas['AgentLegRequestDto']
export type Partner = Schemas['PartyResponseDto']
export type StallPlan = Schemas['StallPlanResponseDto']
export type TripGroup = Schemas['TripGroupResponseDto']
export type StallAssignment = Schemas['StallAssignmentDto']

const get = async <T>(url: string, params?: Record<string, string>) => (await apiClient.get<ApiResponse<T>>(url, { params })).data.data
const post = async <T>(url: string, body?: unknown) => (await apiClient.post<ApiResponse<T>>(url, body)).data.data
const postEvidence = async <T>(url: string, body: FormData) =>
  (await apiClient.post<ApiResponse<T>>(url, body, { headers: { 'Content-Type': 'multipart/form-data' } })).data.data

export type HubHandoverInput = Schemas['HubHandoverRequestDto']
function hubHandover(stayId: number, endpoint: 'receipts' | 'deliveries', request: HubHandoverInput, photo: File, record: File) {
  const body = new FormData()
  body.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }))
  body.append('photo', photo)
  body.append('record', record)
  return postEvidence<HorseStay>(`/horse-stays/${stayId}/${endpoint}`, body)
}

export const dispatchApi = {
  trips: (from: string, to: string) => get<VehicleTrip[]>('/vehicle-trips', { from, to }),
  trip: (id: number) => get<VehicleTrip>(`/vehicle-trips/${id}`),
  createTrip: (body: VehicleTripCreate) => post<VehicleTrip>('/vehicle-trips', body),
  planTrip: (body: PlannedTripCreate) => post<PlannedTrip>('/dispatch/trip-plans', body),
  createPlannedTrip: (body: PlannedTripCreate) => post<VehicleTrip>('/vehicle-trips/planned', body),
  cancelTrip: (id: number, reason: string) => post<VehicleTrip>(`/vehicle-trips/${id}/cancel`, { reason }),
  assignCrew: (tripId: number, staffId: number, crewRole: CrewRole) => post<VehicleTrip>(`/vehicle-trips/${tripId}/crew-assignments`, { staffId, crewRole }),
  releaseCrew: (assignmentId: number) => post<VehicleTrip>(`/crew-assignments/${assignmentId}/release`),
  vehicles: () => get<DispatchVehicle[]>('/dispatch/vehicles'),
  crewCandidates: (role: CrewRole, tripId: number) => get<CrewCandidate[]>('/dispatch/crew-candidates', { role, tripId: String(tripId) }),
  bookings: (from: string, to: string) => get<DispatchBooking[]>('/dispatch/bookings', { from, to }),
  horsesOnTrip: (tripId: number) => get<TripHorse[]>(`/vehicle-trips/${tripId}/horse-legs`),
  assignHorse: (tripId: number, bookingHorseId: number, boardTripStopId: number, alightTripStopId: number) =>
    post<TripHorse[]>(`/vehicle-trips/${tripId}/horse-legs`, { bookingHorseId, boardTripStopId, alightTripStopId }),
  cancelLeg: (legId: number) => post<TripHorse[]>(`/horse-legs/${legId}/cancel`),
  departures: (from: string, to: string) => get<Departure[]>('/departures', { from, to }),
  locations: () => get<NetworkLocation[]>('/locations', { status: 'ACTIVE' }),
  myAssignments: () => get<MyCrewAssignment[]>('/crew-assignments/mine'),
  horseStays: (locationId: number) => get<HorseStay[]>('/horse-stays', { locationId: String(locationId) }),
  groupingSuggestions: (date: string, hubId: number, tripType: 'PICKUP' | 'DELIVERY') =>
    get<TripGroup[]>('/dispatch/grouping-suggestions', { date, hubId: String(hubId), tripType }),
  stallPlan: (tripId: number) => get<StallPlan>(`/vehicle-trips/${tripId}/stall-plan`),
  suggestStallPlan: (tripId: number) => post<StallPlan>(`/vehicle-trips/${tripId}/stall-plan/suggest`),
  applyStallPlan: (tripId: number, horses: StallAssignment[]) => post<StallPlan>(`/vehicle-trips/${tripId}/stall-plan/apply`, { horses }),
  hubAppointments: (locationId: number) => get<HorseStay[]>('/horse-stays/appointments', { locationId: String(locationId) }),
  appointAtHub: (bookingHorseId: number, plannedStartAt: string) => post<HorseStay>(`/booking-horses/${bookingHorseId}/hub-appointments`, { plannedStartAt }),
  hubHandoverPapers: (stayId: number, receiving: boolean) => get<Schemas['HandoverPaperResponseDto'][]>(
    `/horse-stays/${stayId}/handover-papers?receiving=${receiving}`),
  receiveAtHub: (stayId: number, request: HubHandoverInput, photo: File, record: File) => hubHandover(stayId, 'receipts', request, photo, record),
  refuseAtHub: (stayId: number, reason: string) => post<null>(`/horse-stays/${stayId}/refusals`, { reason }),
  setCollectionTime: (stayId: number, collectAt: string) => post<HorseStay>(`/horse-stays/${stayId}/collection-time`, { collectAt }),
  stableAtHub: (stayId: number, note?: string) => post<HorseStay>(`/horse-stays/${stayId}/stabling`, { note }),
  deliverAtHub: (stayId: number, request: HubHandoverInput, photo: File, record: File) => hubHandover(stayId, 'deliveries', request, photo, record),
  parties: () => get<Partner[]>('/parties'),
  createAgentLeg: (bookingHorseId: number, body: AgentLegCreate) => post<HorseLeg>(`/booking-horses/${bookingHorseId}/agent-legs`, body),
  cancelAgentLeg: (legId: number) => post<null>(`/agent-legs/${legId}/cancel`),
  startAgentLeg: (legId: number) => post<HorseLeg>(`/agent-legs/${legId}/start`),
  completeAgentLeg: (legId: number, proof: File) => {
    const body = new FormData()
    body.append('proof', proof)
    return postEvidence<HorseLeg>(`/agent-legs/${legId}/complete`, body)
  },
  acknowledge: (assignmentId: number) => post<MyCrewAssignment>(`/crew-assignments/${assignmentId}/acknowledge`),
}
