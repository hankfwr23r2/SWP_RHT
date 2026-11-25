import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type BookingPayment = Schemas['PaymentResponseDto']
export type PaymentResult = Schemas['PaymentResultResponseDto']

export const bookingPaymentsApi = {
  ofBooking: async (bookingId: number) =>
    (await apiClient.get<ApiResponse<BookingPayment[]>>(`/bookings/${bookingId}/payments`)).data.data,
  // Số tiền do backend lấy từ khoản thu, client chỉ gửi mã khoản thu
  checkout: async (paymentId: number) =>
    (await apiClient.post<ApiResponse<Schemas['PaymentCheckoutResponseDto']>>(`/payments/${paymentId}/checkout`, {})).data.data,
  dispute: async (paymentId: number, reason: string) =>
    (await apiClient.post<ApiResponse<BookingPayment>>(`/extra-charges/${paymentId}/dispute`, { reason })).data.data,
  decideDispute: async (paymentId: number, decision: 'ACCEPTED' | 'REJECTED', response: string) =>
    (await apiClient.post<ApiResponse<BookingPayment>>(`/extra-charges/${paymentId}/dispute-decision`, { decision, response })).data.data,
  extend: async (paymentId: number, days: number) =>
    (await apiClient.post<ApiResponse<BookingPayment>>(`/extra-charges/${paymentId}/extend`, { days })).data.data,
  writeOff: async (paymentId: number, reason: string, decision: File) => {
    const body = new FormData()
    body.append('request', new Blob([JSON.stringify({ reason })], { type: 'application/json' }))
    body.append('decision', decision)
    return (await apiClient.post<ApiResponse<BookingPayment>>(`/extra-charges/${paymentId}/write-off`, body)).data.data
  },
  reopen: async (paymentId: number) =>
    (await apiClient.post<ApiResponse<BookingPayment>>(`/extra-charges/${paymentId}/reopen`)).data.data,
  releaseBlock: async (paymentId: number) =>
    (await apiClient.post<ApiResponse<BookingPayment>>(`/extra-charges/${paymentId}/release-block`)).data.data,
  verifyReturn: async (params: Record<string, string>) =>
    (await apiClient.get<ApiResponse<PaymentResult>>('/payments/vnpay/return', { params })).data.data,
}

export interface PaymentDto {
  id: number
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
    const returnUrl = typeof window !== 'undefined' && window.location?.origin
      ? `${window.location.origin}/payment/vnpay-return`
      : undefined
    const res = await apiClient.post<ApiResponse<VnpayCreateDto>>('/payments/vnpay/create', { bookingId: String(bookingId), paymentType, returnUrl })
    return res.data.data
  },

  /**
   * 3. Khách hủy đơn sau cọc, trước khi trả 70%: mất cọc (POST /api/v1/payments/cancel)
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
