import apiClient, { type ApiResponse } from './client'
import type { BackendBookingResponse } from './bookings'

export interface ExpenseItemPayload {
  id: string
  category: string
  description: string
  photo: string
  amount: number
  payer: 'customer' | 'carrier'
}

export interface SubmitExpensesPayload {
  tripId?: string
  driverName?: string
  expenses?: ExpenseItemPayload[]
}

export interface IssueSettlementPayload {
  managerName?: string
  items?: ExpenseItemPayload[]
  adjustment?: number
  note?: string
  payers?: Record<string, 'customer' | 'carrier'>
}

export interface PaySettlementPayload {
  paymentMethod?: string
  transactionCode?: string
  rating?: {
    stars: number
    comment?: string
    trip?: number
    driver?: number
    escort?: number
  }
}

export interface SettlementDetailResponse {
  id: string
  bookingId: string
  totalDepositPaid: number
  totalBalancePaid: number
  actualTotal: number
  balanceDue: number
  paid: boolean
  dueAt: number
  expenses: Array<{
    id: string
    category: string
    label: string
    photo: string
    amount: number
    payer: 'customer' | 'company'
  }>
  rating?: {
    stars?: number
    comment?: string
    trip?: number
    driver?: number
    escort?: number
  }
}

export const backendSettlementApi = {
  // 1. Kê khai chi phí phát sinh thực tế kèm ảnh hóa đơn -> POST /api/v1/settlements/{id}/submit-expenses
  submitExpenses: async (id: number | string, payload: SubmitExpensesPayload): Promise<BackendBookingResponse> => {
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>(`/settlements/${id}/submit-expenses`, payload)
    return res.data.data
  },

  // 2. Manager đối soát và phát hành Bảng quyết toán phát sinh -> POST /api/v1/settlements/{id}/issue
  issue: async (id: number | string, payload: IssueSettlementPayload): Promise<SettlementDetailResponse> => {
    const res = await apiClient.post<ApiResponse<SettlementDetailResponse>>(`/settlements/${id}/issue`, payload)
    return res.data.data
  },

  // 3. Khách hàng thanh toán phát sinh và đánh giá 1-5 sao -> POST /api/v1/settlements/{id}/pay-and-complete
  payAndComplete: async (id: number | string, payload: PaySettlementPayload): Promise<BackendBookingResponse> => {
    const res = await apiClient.post<ApiResponse<BackendBookingResponse>>(`/settlements/${id}/pay-and-complete`, payload)
    return res.data.data
  },

  // 4. Xem chi tiết Bảng quyết toán -> GET /api/v1/settlements/{id}
  getDetail: async (id: number | string): Promise<SettlementDetailResponse> => {
    const res = await apiClient.get<ApiResponse<SettlementDetailResponse>>(`/settlements/${id}`)
    return res.data.data
  },
}
