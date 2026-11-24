import apiClient, { type ApiResponse } from './client'
import type { components, paths } from './generated/schema'

type Schemas = components['schemas']
export type AuditLog = Schemas['SystemLogResponseDto']
export type AuditLogPage = Schemas['SystemLogPageResponseDto']

export const auditLogsApi = {
  list: async (params: NonNullable<paths['/admin/system-logs']['get']['parameters']['query']>) =>
    (await apiClient.get<ApiResponse<AuditLogPage>>('/admin/system-logs', { params })).data.data,
  options: async () =>
    (await apiClient.get<ApiResponse<NonNullable<Schemas['ApiResponseMapStringListString']['data']>>>('/admin/system-logs/options')).data.data,
  detail: async (id: string) =>
    (await apiClient.get<ApiResponse<AuditLog>>(`/admin/system-logs/${encodeURIComponent(id)}`)).data.data,
}
