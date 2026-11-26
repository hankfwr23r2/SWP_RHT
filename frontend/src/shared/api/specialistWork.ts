import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

export type SpecialistWork = components['schemas']['SpecialistWorkResponseDto']
type WorkSummary = components['schemas']['SpecialistWorkSummaryResponseDto']

export const specialistWorkApi = {
  queue: async (all: boolean) =>
    (await apiClient.get<ApiResponse<SpecialistWork[]>>('/specialist/work-queue', { params: { all } })).data.data,
  summary: async () =>
    (await apiClient.get<ApiResponse<WorkSummary>>('/specialist/work-summary')).data.data,
}
