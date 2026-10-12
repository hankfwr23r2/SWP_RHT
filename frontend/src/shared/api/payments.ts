import apiClient, { type ApiResponse } from './client'

export interface ProcessPaymentPayload {
  bookingId: string | number
  amount?: number
  paymentType?: 'DEPOSIT' | 'BALANCE' | 'PENALTY' | 'REFUND' | 'SETTLEMENT'
  paymentMethod?: 'BANK_TRANSFER' | 'VNPAY' | 'CARD' | 'CASH'
  transactionCode?: string
  note?: string
}

export interface PaymentDto {
  id: number
  bookingId: number
  bookingCode: string
  waybillNo?: string
  amount: number
  paymentDate: string
  paymentType: string
  paymentMethod: string
  transactionCode: string
  status: string
  note?: string
}

export interface PaymentSummaryDto {
  bookingId: number
  bookingCode: string
  waybillNo?: string
  bookingStatus: string
  totalAmount: number
  depositAmount: number
  balanceAmount: number
  totalPaid: number
  remainingBalance: number
  depositPaid: boolean
  balancePaid: boolean
  fullyPaid: boolean
  payments: PaymentDto[]
}

export interface CancelWithRefundDto {
  bookingId: number
  bookingCode: string
  bookingStatus: string
  depositPaid: number
  daysToDeparture: number
  refundPercentage: number
  refundAmount: number
  penaltyAmount: number
  message: string
  refundPayment?: PaymentDto
}

export const paymentApi = {
  /**
   * 1. Thanh toán cọc 30% để nhận Vận đơn (POST /api/v1/payments/deposit)
   */
  payDeposit: async (payload: ProcessPaymentPayload): Promise<PaymentDto> => {
    const res = await apiClient.post<ApiResponse<PaymentDto>>('/payments/deposit', payload)
    return res.data.data
  },

  /**
   * 2. Thanh toán 70% còn lại trước giờ khởi hành ngày D (POST /api/v1/payments/balance)
   */
  payBalance: async (payload: ProcessPaymentPayload): Promise<PaymentDto> => {
    const res = await apiClient.post<ApiResponse<PaymentDto>>('/payments/balance', payload)
    return res.data.data
  },

  /**
   * 3. Hủy đơn kèm chính sách hoàn cọc / phạt cọc (POST /api/v1/payments/cancel)
   */
  cancelWithRefund: async (bookingId: string | number, reason?: string): Promise<CancelWithRefundDto> => {
    const res = await apiClient.post<ApiResponse<CancelWithRefundDto>>('/payments/cancel', { reason }, {
      params: { bookingId },
    })
    return res.data.data
  },

  /**
   * 4. Xem tóm tắt tài chính đơn hàng (GET /api/v1/payments/booking/{bookingId}/summary)
   */
  getSummary: async (bookingId: string | number): Promise<PaymentSummaryDto> => {
    const res = await apiClient.get<ApiResponse<PaymentSummaryDto>>(`/payments/booking/${bookingId}/summary`)
    return res.data.data
  },

  /**
   * 5. Xem lịch sử các đợt thanh toán của đơn (GET /api/v1/payments/booking/{bookingId})
   */
  getByBooking: async (bookingId: string | number): Promise<PaymentDto[]> => {
    const res = await apiClient.get<ApiResponse<PaymentDto[]>>(`/payments/booking/${bookingId}`)
    return res.data.data
  },

  /**
   * 6. Xem chi tiết 1 giao dịch thanh toán (GET /api/v1/payments/{id})
   */
  getDetail: async (id: number): Promise<PaymentDto> => {
    const res = await apiClient.get<ApiResponse<PaymentDto>>(`/payments/${id}`)
    return res.data.data
  },
}
