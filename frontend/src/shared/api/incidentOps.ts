// Sự cố, phương án và chi phí (B4). Kiểu lấy từ OpenAPI sinh tự động.
import apiClient, { type ApiResponse } from './client'
import { formResponseOptions, readFormResponse } from './customerAddresses'
import type { components } from './generated/schema'

type Schemas = components['schemas']
// Bổ sung trường của PR A và biểu diễn mốc nghỉ có thể là null.
export type IncidentSeverity = 'LOW' | 'MEDIUM' | 'HIGH'
export type Incident = Omit<Schemas['IncidentResponseDto'], 'restStartedAt' | 'fitEligibleAt'> & {
  severity?: IncidentSeverity
  blocksTrip?: boolean
  restStartedAt?: string | null
  fitEligibleAt?: string | null
}
export type HandoverCauseChange = Schemas['HandoverCauseRequestDto']
export type IncidentPlan = Schemas['PlanDto']
export type IncidentReport = Schemas['IncidentReportRequestDto'] & { severity?: IncidentSeverity }
export type IncidentPlanCreate = Schemas['IncidentPlanRequestDto']
export type CostItem = Schemas['CostItemResponseDto']
export type CostItemCreate = Schemas['CostItemRequestDto']
export type Party = Schemas['PartyResponseDto']
export type IncidentType = NonNullable<IncidentReport['incidentType']>
export type CauseType = NonNullable<IncidentReport['causeType']>
export type ResolutionType = NonNullable<IncidentPlanCreate['resolutionType']>
export type CostCategory = NonNullable<CostItemCreate['category']>
export type Evidence = Schemas['EvidenceResponseDto']

const get = async <T>(url: string) => (await apiClient.get<ApiResponse<T>>(url)).data.data
const post = async <T>(url: string, body?: unknown) => (await apiClient.post<ApiResponse<T>>(url, body)).data.data
const upload = async (url: string, file: File, params?: Record<string, number | string>) => {
  const body = new FormData()
  body.append('file', file)
  return (await apiClient.post<ApiResponse<Evidence[]>>(url, body, { params, headers: { 'Content-Type': 'multipart/form-data' } })).data.data
}

export const incidentsApi = {
  open: () => get<Incident[]>('/incidents'),
  incident: (id: number) => get<Incident>(`/incidents/${id}`),
  severityDefaults: () => get<Partial<Record<IncidentType, IncidentSeverity>>>('/incidents/severity-defaults'),
  changeSeverity: async (id: number, severity: IncidentSeverity) =>
    readFormResponse<void>(await apiClient.patch(`/incidents/${id}/severity`, { severity }, formResponseOptions)),
  unlockTrip: async (id: number, reason: string) =>
    readFormResponse<void>(await apiClient.post(`/incidents/${id}/unlock-trip`, { reason }, formResponseOptions)),
  amendHandoverCause: (id: number, body: HandoverCauseChange) => post<Incident>(`/incidents/${id}/handover-cause`, body),
  ofTrip: (tripId: number) => get<Incident[]>(`/vehicle-trips/${tripId}/incidents`),
  report: async (body: IncidentReport) =>
    readFormResponse<Incident>(await apiClient.post('/incidents', body, formResponseOptions)),
  propose: (incidentId: number, body: IncidentPlanCreate) => post<Incident>(`/incidents/${incidentId}/plans`, body),
  approve: (planId: number, confirmedCauseType?: CauseType) => post<Incident>(`/incident-plans/${planId}/approve`, { confirmedCauseType }),
  reject: (planId: number, reason: string) => post<Incident>(`/incident-plans/${planId}/reject`, { reason }),
  startRest: (incidentId: number) => post<Incident>(`/incidents/${incidentId}/rest-start`),
  confirmFit: (incidentId: number) => post<Incident>(`/incidents/${incidentId}/fit-confirmation`),
  resolve: (incidentId: number) => post<Incident>(`/incidents/${incidentId}/resolve`),
  close: (incidentId: number) => post<Incident>(`/incidents/${incidentId}/close`),
  costs: (incidentId: number) => get<CostItem[]>(`/incidents/${incidentId}/cost-items`),
  recordCost: (body: CostItemCreate) => post<CostItem>('/cost-items', body),
  approveCost: (costId: number, emergencyOverrideReason?: string) => post<CostItem>(`/cost-items/${costId}/approve`, { emergencyOverrideReason }),
  customerCosts: (bookingId: number) => get<CostItem[]>(`/bookings/${bookingId}/customer-cost-items`),
  customerPlans: (bookingId: number) => get<IncidentPlan[]>(`/bookings/${bookingId}/customer-incident-plans`),
  acceptPlan: (planId: number) => post<void>(`/incident-plans/${planId}/accept`),
  respondCost: (costId: number, agree: boolean) => post<CostItem>(`/cost-items/${costId}/${agree ? 'accept' : 'decline'}`),
  phoneConsent: (costId: number, note: string) => post<CostItem>(`/cost-items/${costId}/phone-consent`, { note }),
  markRecovered: (costId: number) => post<CostItem>(`/cost-items/${costId}/mark-recovered`),
  parties: () => get<Party[]>('/parties'),
  // Thực hiện phương án đã duyệt
  swapVehicle: (incidentId: number, vehicleId: number) => post<Incident>(`/incidents/${incidentId}/vehicle-swap`, { vehicleId }),
  returnToHub: (incidentId: number) => post<Incident>(`/incidents/${incidentId}/return-to-hub`),
  recordVetResult: async (incidentId: number, note: string, incidentPlanId?: number) =>
    readFormResponse<Incident>(await apiClient.post(`/incidents/${incidentId}/vet-result`, { note, incidentPlanId }, formResponseOptions)),
  cancelLegs: (incidentId: number, incidentPlanId: number) => post<Incident>(`/incidents/${incidentId}/cancel-legs`, { incidentPlanId }),
  // Chứng từ: biên lai, hóa đơn kèm chi phí; ảnh hiện trường kèm sự cố (có tọa độ nếu máy cho lấy vị trí)
  receipts: (costId: number) => get<Evidence[]>(`/cost-items/${costId}/receipts`),
  attachReceipt: (costId: number, file: File) => upload(`/cost-items/${costId}/receipts`, file),
  photos: (incidentId: number) => get<Evidence[]>(`/incidents/${incidentId}/photos`),
  attachPhoto: (incidentId: number, file: File, at?: { latitude: number; longitude: number }, documentType?: 'INCIDENT_PHOTO' | 'VET_REPORT' | 'VEHICLE_SWAP_RECORD' | 'FIT_CHECK_PHOTO') =>
    upload(`/incidents/${incidentId}/photos`, file, { ...at, ...(documentType ? { documentType } : {}) }),
}
