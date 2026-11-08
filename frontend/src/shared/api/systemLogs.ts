import apiClient, { type ApiResponse } from './client'

export interface SystemLogItem {
  id: number
  actionType: string
  actorId: number | null
  actorName: string | null
  actorEmail: string | null
  actorRole: string | null
  targetTable: string
  targetKey: string | null
  targetLabel: string | null
  description: string | null
  oldData: string | null
  newData: string | null
  legacy: boolean
  createdAt: string | null
}

export interface SystemLogPage {
  content: SystemLogItem[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export const systemLogsApi = {
  async list(params: Record<string, string | number>, signal?: AbortSignal) {
    const response = await apiClient.get<ApiResponse<SystemLogPage>>('/admin/system-logs', { params, signal })
    return response.data.data
  },
  async detail(id: string, signal?: AbortSignal) {
    const response = await apiClient.get<ApiResponse<SystemLogItem>>(`/admin/system-logs/${encodeURIComponent(id)}`, { signal })
    return response.data.data
  },
  async options(signal?: AbortSignal) {
    const response = await apiClient.get<ApiResponse<Record<string, string[]>>>('/admin/system-logs/options', { signal })
    return response.data.data
  },
}
