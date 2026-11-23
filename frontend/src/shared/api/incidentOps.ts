// Sự cố, phương án và chi phí (B4). Kiểu lấy từ OpenAPI sinh tự động.
import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type Incident = Schemas['IncidentResponseDto']
export type IncidentPlan = Schemas['PlanDto']
export type IncidentReport = Schemas['IncidentReportRequestDto']
export type IncidentPlanCreate = Schemas['IncidentPlanRequestDto']
export type CostItem = Schemas['CostItemResponseDto']
export type CostItemCreate = Schemas['CostItemRequestDto']
export type Party = Schemas['PartyResponseDto']
export type IncidentType = NonNullable<IncidentReport['incidentType']>
export type CauseType = NonNullable<IncidentReport['causeType']>
export type ResolutionType = NonNullable<IncidentPlanCreate['resolutionType']>
export type CostCategory = NonNullable<CostItemCreate['category']>

const get = async <T>(url: string) => (await apiClient.get<ApiResponse<T>>(url)).data.data
const post = async <T>(url: string, body?: unknown) => (await apiClient.post<ApiResponse<T>>(url, body)).data.data

export const incidentsApi = {
  open: () => get<Incident[]>('/incidents'),
  incident: (id: number) => get<Incident>(`/incidents/${id}`),
  ofTrip: (tripId: number) => get<Incident[]>(`/vehicle-trips/${tripId}/incidents`),
  report: (body: IncidentReport) => post<Incident>('/incidents', body),
  propose: (incidentId: number, body: IncidentPlanCreate) => post<Incident>(`/incidents/${incidentId}/plans`, body),
  approve: (planId: number) => post<Incident>(`/incident-plans/${planId}/approve`),
  reject: (planId: number, reason: string) => post<Incident>(`/incident-plans/${planId}/reject`, { reason }),
  confirmFit: (incidentId: number) => post<Incident>(`/incidents/${incidentId}/fit-confirmation`),
  resolve: (incidentId: number) => post<Incident>(`/incidents/${incidentId}/resolve`),
  close: (incidentId: number) => post<Incident>(`/incidents/${incidentId}/close`),
  costs: (incidentId: number) => get<CostItem[]>(`/incidents/${incidentId}/cost-items`),
  recordCost: (body: CostItemCreate) => post<CostItem>('/cost-items', body),
  approveCost: (costId: number) => post<CostItem>(`/cost-items/${costId}/approve`),
  parties: () => get<Party[]>('/parties'),
}
