import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

export type CustodyRow = components['schemas']['CustodyRowResponseDto']
type ContactRequest = components['schemas']['BalanceHoldContactRequestDto']
type ExtensionRequest = components['schemas']['PickupWaitExtensionRequestDto']
type TransferRequest = components['schemas']['PickupHubTransferRequestDto']
export type BalanceContactTarget = NonNullable<ContactRequest['target']>

export const custodyApi = {
  board: async () => (await apiClient.get<ApiResponse<CustodyRow[]>>('/custody-board')).data.data,
  recordBalanceContact: (id: number, request: ContactRequest) => apiClient.post(`/bookings/${id}/balance-hold-contacts`, request),
  extendPickupWait: (id: number, request: ExtensionRequest) => apiClient.post(`/bookings/${id}/extend-pickup-payment-wait`, request),
  sendPickupToHub: (id: number, request: TransferRequest) => apiClient.post(`/bookings/${id}/send-unclaimed-pickup-to-hub`, request),
}
