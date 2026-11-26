import apiClient, { type ApiResponse } from './client'
import { formResponseOptions, readFormResponse } from './customerAddresses'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type DocumentType = Schemas['DocumentTypeResponseDto']
export type DocumentTypeCreate = Schemas['DocumentTypeCreateRequestDto']
export type DocumentTypeUpdate = Schemas['DocumentTypeUpdateRequestDto']
export type CountryRule = Schemas['CountryRuleResponseDto']
export type CountryRuleCreate = Schemas['CountryRuleCreateRequestDto']
export type CountryRuleUpdate = Schemas['CountryRuleUpdateRequestDto']
export type DocumentRequirement = Schemas['DocumentRequirementResponseDto']
export type DocumentRequirementCreate = Schemas['DocumentRequirementCreateRequestDto']
export type DocumentRequirementUpdate = Schemas['DocumentRequirementUpdateRequestDto']

export const regulationsApi = {
  types: async () =>
    (await apiClient.get<ApiResponse<DocumentType[]>>('/document-types')).data.data,
  createType: async (body: DocumentTypeCreate) =>
    readFormResponse<DocumentType>(await apiClient.post('/document-types', body, formResponseOptions)),
  updateType: async (id: number, body: DocumentTypeUpdate) =>
    readFormResponse<DocumentType>(await apiClient.put(`/document-types/${id}`, body, formResponseOptions)),
  rules: async () =>
    (await apiClient.get<ApiResponse<CountryRule[]>>('/country-rules')).data.data,
  createRule: async (body: CountryRuleCreate) =>
    readFormResponse<CountryRule>(await apiClient.post('/country-rules', body, formResponseOptions)),
  updateRule: async (id: number, body: CountryRuleUpdate) =>
    readFormResponse<CountryRule>(await apiClient.put(`/country-rules/${id}`, body, formResponseOptions)),
  requirements: async (ruleId: number) =>
    (await apiClient.get<ApiResponse<DocumentRequirement[]>>(`/country-rules/${ruleId}/document-requirements`)).data.data,
  createRequirement: async (ruleId: number, body: DocumentRequirementCreate) =>
    readFormResponse<DocumentRequirement>(await apiClient.post(`/country-rules/${ruleId}/document-requirements`, body, formResponseOptions)),
  updateRequirement: async (ruleId: number, id: number, body: DocumentRequirementUpdate) =>
    readFormResponse<DocumentRequirement>(await apiClient.put(`/country-rules/${ruleId}/document-requirements/${id}`, body, formResponseOptions)),
}
