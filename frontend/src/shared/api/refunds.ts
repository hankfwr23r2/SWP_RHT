import apiClient, { type ApiResponse } from './client'
import type { BookingPayment } from './payments'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type Refund = Schemas['RefundResponseDto']
export type RefundStatus = NonNullable<Refund['status']>

export const refundsApi = {
  list: async (status: RefundStatus) => (await apiClient.get<ApiResponse<Refund[]>>('/refunds', { params: { status } })).data.data,
  markRefunded: async (id: number, transferReference: string, receipt?: File) => {
    const body = new FormData()
    body.append('request', new Blob([JSON.stringify({ transferReference })], { type: 'application/json' }))
    if (receipt) body.append('receipt', receipt)
    return (await apiClient.post<ApiResponse<BookingPayment>>(`/refunds/${id}/mark-refunded`, body,
      { headers: { 'Content-Type': 'multipart/form-data' } })).data.data
  },
}
