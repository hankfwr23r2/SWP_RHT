import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

export type CustodyRow = components['schemas']['CustodyRowResponseDto']
export type UnclaimedHorseResolution = components['schemas']['UnclaimedHorseResolutionRequestDto']
type ContactRequest = components['schemas']['BalanceHoldContactRequestDto']
type ExtensionRequest = components['schemas']['PickupWaitExtensionRequestDto']
type TransferRequest = components['schemas']['PickupHubTransferRequestDto']
export type BalanceContactTarget = NonNullable<ContactRequest['target']>

export const custodyApi = {
  board: async () => (await apiClient.get<ApiResponse<CustodyRow[]>>('/custody-board')).data.data,
  resolveUnclaimed: (stayId: number, request: UnclaimedHorseResolution, proof: File) => {
    const body = new FormData()
    body.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }))
    body.append('proof', proof)
    return apiClient.post(`/horse-stays/${stayId}/resolve-unclaimed`, body)
  },
  recordBalanceContact: (id: number, request: ContactRequest) => apiClient.post(`/bookings/${id}/balance-hold-contacts`, request),
  extendPickupWait: (id: number, request: ExtensionRequest) => apiClient.post(`/bookings/${id}/extend-pickup-payment-wait`, request),
  sendPickupToHub: (id: number, request: TransferRequest) => apiClient.post(`/bookings/${id}/send-unclaimed-pickup-to-hub`, request),
}
