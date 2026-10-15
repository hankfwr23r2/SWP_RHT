import apiClient, { type ApiResponse } from './client'

export interface BackendIncidentResponse {
  id: number
  formattedId: string // INC-001
  bookingId: number
  bookingCode: string // BK-003
  reporter: string
  incidentName: string
  incidentType: string
  description: string
  status: string // open, proposed, active, resolved, rejected
  resolutionType?: string
  partnerName?: string
  partnerService?: string
  partnerPhone?: string
  estimatedCost?: number
  actualCost?: number
  costBearer?: string
  invoiceUrl?: string
  directive?: string
  createdAt?: number
  resolvedAt?: number
}

export interface ReportIncidentPayload {
  bookingId: number
  reporter: string
  incidentName: string
  incidentType: string
  description: string
  photoUrl?: string
}

export interface ProposeExternalPlanPayload {
  partnerName: string
  partnerService: string
  partnerPhone?: string
  estimatedCost: number
  directive?: string
}

export interface ApproveIncidentPlanPayload {
  directive?: string
  costBearer?: string
  approvedAmount?: number
}

export interface ResolveIncidentPayload {
  actualCost: number
  invoiceUrl: string
  note?: string
}

export const backendIncidentApi = {
  // 1. Kíp xe báo sự cố khẩn cấp (SOS) -> POST /api/v1/incidents/report
  report: async (payload: ReportIncidentPayload): Promise<BackendIncidentResponse> => {
    const res = await apiClient.post<ApiResponse<BackendIncidentResponse>>('/incidents/report', payload)
    return res.data.data
  },

  // 2. Coordinator đề xuất gọi đội ngoài -> POST /api/v1/incidents/{id}/propose-external
  proposeExternal: async (id: number, payload: ProposeExternalPlanPayload): Promise<BackendIncidentResponse> => {
    const res = await apiClient.post<ApiResponse<BackendIncidentResponse>>(`/incidents/${id}/propose-external`, payload)
    return res.data.data
  },

  // 3. Manager phê duyệt phương án & bảo lãnh chi phí -> POST /api/v1/incidents/{id}/approve-plan
  approvePlan: async (id: number, payload: ApproveIncidentPlanPayload): Promise<BackendIncidentResponse> => {
    const res = await apiClient.post<ApiResponse<BackendIncidentResponse>>(`/incidents/${id}/approve-plan`, payload)
    return res.data.data
  },

  // 4. Nghiệm thu hiện trường, lưu hóa đơn và tiếp tục hành trình -> POST /api/v1/incidents/{id}/complete-resolution
  completeResolution: async (id: number, payload: ResolveIncidentPayload): Promise<BackendIncidentResponse> => {
    const res = await apiClient.post<ApiResponse<BackendIncidentResponse>>(`/incidents/${id}/complete-resolution`, payload)
    return res.data.data
  },

  // 5. Lấy danh sách sự cố -> GET /api/v1/incidents
  list: async (status?: string): Promise<BackendIncidentResponse[]> => {
    const res = await apiClient.get<ApiResponse<BackendIncidentResponse[]>>('/incidents', {
      params: { status },
    })
    return res.data.data
  },

  // 6. Lấy sự cố theo đơn hàng -> GET /api/v1/incidents/booking/{bookingId}
  getByBooking: async (bookingId: number): Promise<BackendIncidentResponse[]> => {
    const res = await apiClient.get<ApiResponse<BackendIncidentResponse[]>>(`/incidents/booking/${bookingId}`)
    return res.data.data
  },
}
