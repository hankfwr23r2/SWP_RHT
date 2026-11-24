import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

export type CustodyRow = components['schemas']['CustodyRowResponseDto']

export const custodyApi = {
  board: async () => (await apiClient.get<ApiResponse<CustodyRow[]>>('/custody-board')).data.data,
}
