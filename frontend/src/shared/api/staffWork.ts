import apiClient, { type ApiResponse } from './client'

import type { components } from './generated/schema'

export type StaffWorkSummary = components['schemas']['StaffWorkSummaryResponseDto']

export const fetchStaffWork = async () =>
  (await apiClient.get<ApiResponse<StaffWorkSummary>>('/staff/work-summary')).data.data
