import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

export type TransportRules = components['schemas']['TransportRulesResponseDto']

export const transportRulesApi = {
  get: async () => (await apiClient.get<ApiResponse<TransportRules>>('/public/transport-rules')).data.data,
}
