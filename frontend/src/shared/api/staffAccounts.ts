import apiClient, { type ApiResponse } from './client'
import { formResponseOptions, readFormResponse } from './customerAddresses'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type StaffAccount = Schemas['StaffAccountResponseDto']
export type StaffAccountCreate = Schemas['StaffAccountCreateRequestDto']
export type StaffAccountUpdate = Schemas['StaffAccountUpdateRequestDto']

export const staffAccountsApi = {
  list: async () =>
    (await apiClient.get<ApiResponse<StaffAccount[]>>('/staff-accounts')).data.data,
  create: async (body: StaffAccountCreate) =>
    readFormResponse<StaffAccount>(await apiClient.post('/staff-accounts', body, formResponseOptions)),
  lock: async (id: number, reason: string) =>
    readFormResponse<StaffAccount>(await apiClient.post(`/staff-accounts/${id}/lock`, { reason }, formResponseOptions)),
  unlock: async (id: number, reason: string) =>
    readFormResponse<StaffAccount>(await apiClient.post(`/staff-accounts/${id}/unlock`, { reason }, formResponseOptions)),
  update: async (id: number, body: StaffAccountUpdate) =>
    readFormResponse<StaffAccount>(await apiClient.put(`/staff-accounts/${id}`, body, formResponseOptions)),
}
