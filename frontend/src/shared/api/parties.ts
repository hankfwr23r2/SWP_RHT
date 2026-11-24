import apiClient, { type ApiResponse } from './client'
import { formResponseOptions, readFormResponse } from './customerAddresses'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type Party = Schemas['PartyResponseDto']
export type PartyCreate = Schemas['PartyCreateRequestDto']
export type PartyUpdate = Schemas['PartyUpdateRequestDto']
export type PartyRole = Schemas['PartyRoleResponseDto']
export type PartyRoleCreate = Schemas['PartyRoleCreateRequestDto']
export type PartyRoleUpdate = Schemas['PartyRoleUpdateRequestDto']

export const partiesApi = {
  list: async () =>
    (await apiClient.get<ApiResponse<Party[]>>('/parties')).data.data,
  create: async (body: PartyCreate) =>
    readFormResponse<Party>(await apiClient.post('/parties', body, formResponseOptions)),
  update: async (id: number, body: PartyUpdate) =>
    readFormResponse<Party>(await apiClient.put(`/parties/${id}`, body, formResponseOptions)),
  addRole: async (id: number, body: PartyRoleCreate) =>
    readFormResponse<PartyRole>(await apiClient.post(`/parties/${id}/roles`, body, formResponseOptions)),
  updateRole: async (id: number, roleType: PartyRoleCreate['roleType'], body: PartyRoleUpdate) =>
    readFormResponse<PartyRole>(await apiClient.put(`/parties/${id}/roles/${roleType}`, body, formResponseOptions)),
}
