import apiClient, { type ApiResponse } from './client'

export type DocumentGroup = 'HORSE' | 'CLEARANCE'
export type DocumentType =
  | 'passport' | 'vaccine' | 'lab'
  | 'health_cert' | 'poa' | 'customs_declaration' | 'import_permit'
  | 'quarantine_cert' | 'ata_carnet' | 'commercial_invoice'
export type DocumentTripType = 'ALL' | 'DOMESTIC' | 'INTERNATIONAL'
export type DocumentCountry = 'ALL' | 'VN' | 'LA' | 'KH'
export type DocumentRequirementStatus = 'ACTIVE' | 'INACTIVE'

export interface DocumentRequirement {
  id: number
  documentGroup: DocumentGroup
  documentType: DocumentType
  documentName: string
  description?: string
  tripType: DocumentTripType
  country: DocumentCountry
  required: boolean
  status: DocumentRequirementStatus
  updatedBy?: string
  updatedAt?: string
}

export type DocumentRequirementPayload = Omit<DocumentRequirement, 'id' | 'updatedBy' | 'updatedAt'>

export const documentRequirementsApi = {
  listPublic: async (): Promise<DocumentRequirement[]> => {
    const response = await apiClient.get<ApiResponse<DocumentRequirement[]>>('/public/document-requirements')
    return response.data.data
  },
  listAdmin: async (): Promise<DocumentRequirement[]> => {
    const response = await apiClient.get<ApiResponse<DocumentRequirement[]>>('/admin/document-requirements')
    return response.data.data
  },
  create: async (payload: DocumentRequirementPayload): Promise<DocumentRequirement> => {
    const response = await apiClient.post<ApiResponse<DocumentRequirement>>('/admin/document-requirements', payload)
    return response.data.data
  },
  update: async (id: number, payload: DocumentRequirementPayload): Promise<DocumentRequirement> => {
    const response = await apiClient.put<ApiResponse<DocumentRequirement>>(`/admin/document-requirements/${id}`, payload)
    return response.data.data
  },
  changeStatus: async (id: number, status: DocumentRequirementStatus): Promise<DocumentRequirement> => {
    const response = await apiClient.patch<ApiResponse<DocumentRequirement>>(
      `/admin/document-requirements/${id}/status`, { status },
    )
    return response.data.data
  },
}
