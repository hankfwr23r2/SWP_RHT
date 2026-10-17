import apiClient, { type ApiResponse } from './client'

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

export type VnpayPaymentType = 'DEPOSIT' | 'BALANCE'

export interface VnpayCreateDto {
  paymentUrl: string
  transactionCode: string
  amount: number
  expiresAt: number
}

export interface VnpayResultDto {
  success: boolean
  rspCode: string
  message: string
  bookingId?: number
  bookingCode?: string
  paymentType?: string
  paymentStatus?: string
  amount?: number
  transactionCode?: string
  gatewayTransactionNo?: string
  bankCode?: string
}

// Chỉ giữ các tham số vnp_* mà VNPAY gắn vào URL trang kết quả
export function vnpayParamsFrom(search: string): Record<string, string> {
  const params: Record<string, string> = {}
  new URLSearchParams(search).forEach((value, key) => {
    if (key.startsWith('vnp_')) params[key] = value
  })
  return params
}

export const paymentApi = {
  /**
   * 1. Tạo link thanh toán VNPAY cho cọc 30% / số dư 70% (POST /api/v1/payments/vnpay/create).
   * Số tiền do backend tự tính từ báo giá, client chỉ gửi mã đơn và loại thanh toán.
   */
  createVnpay: async (bookingId: string | number, paymentType: VnpayPaymentType): Promise<VnpayCreateDto> => {
    const res = await apiClient.post<ApiResponse<VnpayCreateDto>>('/payments/vnpay/create', { bookingId: String(bookingId), paymentType })
    return res.data.data
  },

  /**
   * 2. Gửi lại tham số VNPAY trả về trình duyệt để backend kiểm tra chữ ký và ghi nhận (GET /api/v1/payments/vnpay/return)
   */
  verifyVnpayReturn: async (params: Record<string, string>): Promise<VnpayResultDto> => {
    const res = await apiClient.get<ApiResponse<VnpayResultDto>>('/payments/vnpay/return', { params })
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
