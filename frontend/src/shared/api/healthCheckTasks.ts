import apiClient, { type ApiResponse } from './client'
import { formResponseOptions, readFormResponse } from './customerAddresses'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type HealthCheckTask = Schemas['HealthCheckTaskResponseDto']
export type HealthCheckTaskDraft = Schemas['HealthCheckTaskCreateRequestDto']
export type HealthCheckTarget = Pick<HealthCheckTaskDraft, 'bookingHorseId' | 'vehicleTripId' | 'horseStayId'>
type Assignee = Schemas['HealthCheckAssigneeResponseDto']

const resource = '/health-check-tasks'
export const healthCheckTasksApi = {
  assignees: async (target: HealthCheckTarget) =>
    (await apiClient.get<ApiResponse<Assignee[]>>(`${resource}/assignees`, { params: target })).data.data,
  ofHorse: async (bookingHorseId: number) =>
    (await apiClient.get<ApiResponse<HealthCheckTask[]>>(resource, { params: { bookingHorseId } })).data.data,
  mine: async () => (await apiClient.get<ApiResponse<HealthCheckTask[]>>(`${resource}/mine`)).data.data,
  assign: async (draft: HealthCheckTaskDraft) =>
    readFormResponse<HealthCheckTask>(await apiClient.post(resource, draft, formResponseOptions)),
  accept: (id: number) => apiClient.post(`${resource}/${id}/accept`),
  cancel: async (id: number, reason: string) =>
    readFormResponse<HealthCheckTask>(await apiClient.post(`${resource}/${id}/cancel`, { reason }, formResponseOptions)),
}
