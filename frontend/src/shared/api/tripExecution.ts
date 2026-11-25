// Tổ xe thực hiện lượt xe theo điểm dừng (B2). Mọi thao tác trả về lượt xe nhìn từ tổ xe.
import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type DeliveryRecipient = Schemas['DeliveryRecipientRequestDto']
export type CrewTrip = Schemas['CrewTripResponseDto']
export type Handover = Schemas['HandoverResponseDto']
export type HandoverType = NonNullable<Schemas['HandoverCreateRequestDto']['handoverType']>
export type HealthLog = Schemas['HealthLogResponseDto']
export type HealthLogCreate = Schemas['HealthLogRequestDto']
export type HealthStatus = NonNullable<HealthLogCreate['healthStatus']>
export type IntakeLevel = NonNullable<HealthLogCreate['waterIntake']>
export type HandoverPaper = Schemas['HandoverPaperResponseDto']
export type StablePartner = Schemas['StablePartnerResponseDto']

const get = async <T>(url: string) => (await apiClient.get<ApiResponse<T>>(url)).data.data
const post = async <T>(url: string, body?: unknown) => (await apiClient.post<ApiResponse<T>>(url, body)).data.data

export type PickupMissReport = Schemas['HandoverRefusalRequestDto']

export const tripExecutionApi = {
  trip: (tripId: number) => get<CrewTrip>(`/crew/vehicle-trips/${tripId}`),
  start: (tripId: number) => post<CrewTrip>(`/vehicle-trips/${tripId}/start`),
  complete: (tripId: number) => post<CrewTrip>(`/vehicle-trips/${tripId}/complete`),
  arrive: (stopId: number) => post<CrewTrip>(`/trip-stops/${stopId}/arrive`),
  depart: (stopId: number, earlyReason?: string) => post<CrewTrip>(`/trip-stops/${stopId}/depart`, earlyReason ? { earlyReason } : undefined),
  openHandover: (stopId: number, handoverType: HandoverType, partyId?: number) =>
    post<CrewTrip>(`/trip-stops/${stopId}/handovers`, partyId ? { handoverType, partyId } : { handoverType }),
  stablePartners: () => get<StablePartner[]>('/stable-partners'),
  scan: (handoverId: number, horseLegId: number, microchip: string, conditionNote?: string) => post<CrewTrip>(`/handovers/${handoverId}/horses`, { horseLegId, microchip, conditionNote }),
  refuse: async (handoverId: number, request: PickupMissReport, photo: File) => {
    const body = new FormData()
    body.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }))
    body.append('photo', photo)
    return (await apiClient.post<ApiResponse<CrewTrip>>(`/handovers/${handoverId}/refusals`, body, { headers: { 'Content-Type': 'multipart/form-data' } })).data.data
  },
  uploadHorsePhoto: async (handoverId: number, horseLegId: number, file: File) => {
    const body = new FormData()
    body.append('file', file)
    return (await apiClient.post<ApiResponse<CrewTrip>>(`/handovers/${handoverId}/horses/${horseLegId}/photos`, body, { headers: { 'Content-Type': 'multipart/form-data' } })).data.data
  },
  uploadRecord: async (handoverId: number, file: File) => {
    const body = new FormData()
    body.append('file', file)
    return (await apiClient.post<ApiResponse<CrewTrip>>(`/handovers/${handoverId}/record`, body, { headers: { 'Content-Type': 'multipart/form-data' } })).data.data
  },
  papers: (handoverId: number) => get<HandoverPaper[]>(`/handovers/${handoverId}/papers`),
  markPaper: (handoverId: number, documentId: number, originalMissing = false) => post<HandoverPaper[]>(`/handovers/${handoverId}/papers`, { documentId, originalMissing }),
  completeHandover: (handoverId: number, recipients?: Record<string, DeliveryRecipient>) =>
    post<CrewTrip>(`/handovers/${handoverId}/complete`, recipients ? { recipients } : undefined),
  startWaiting: (handoverId: number) => post<CrewTrip>(`/handovers/${handoverId}/start-waiting`),
  failDelivery: (handoverId: number) => post<CrewTrip>(`/handovers/${handoverId}/fail`),
  recordHealth: (body: HealthLogCreate) => post<HealthLog>('/health-logs', body),
  healthLogs: (bookingHorseId: number) => get<HealthLog[]>(`/booking-horses/${bookingHorseId}/health-logs`),
}
