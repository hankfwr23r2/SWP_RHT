import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

export type Contact = components['schemas']['ContactResponseDto']

export const contactApi = {
  get: async () => (await apiClient.get<ApiResponse<Contact>>('/public/contact')).data.data,
}
