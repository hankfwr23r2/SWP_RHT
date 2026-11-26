import apiClient, { type ApiResponse } from './client'
import { formResponseOptions, readFormResponse } from './customerAddresses'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type DepartureSuggestionQuery = Schemas['DepartureSuggestionQuery']
export type DepartureSuggestion = Schemas['DepartureSuggestionResponse']
export type RegulationPreviewRequest = Schemas['RegulationPreviewRequestDto']
export type RegulationPreview = Schemas['RegulationPreviewResponseDto']
export type PricingPreviewRequest = Schemas['PricingPreviewRequestDto']
export type PricingPreview = Schemas['PricingPreviewResponseDto']
export type TransferOptionsRequest = Schemas['TransferOptionsRequestDto']
export type TransferOptions = Schemas['TransferOptionsResponseDto']
export type BookingCreateRequest = Schemas['BookingCreateRequestDto']
export type CustomerProfile = Schemas['CustomerProfileResponseDto']
export type CustomerProfileUpdate = Schemas['CustomerProfileUpdateRequestDto']
export type BookingOrder = Schemas['BookingResponseDto']
export type BookingStatus = NonNullable<BookingOrder['status']>
export type BookingSummary = Schemas['BookingSummaryResponseDto']
export type BookingDetail = Schemas['BookingDetailResponseDto']
export type BookingCancellation = Schemas['BookingCancellationResponseDto']
export type DeliveryChangeSelection = Schemas['DeliveryChangeRequestDto']
export type DeliveryChangePreview = Schemas['DeliveryChangeResponseDto']
export type BookingDelay = Schemas['BookingDelayResponseDto']
export type BookingReschedule = Schemas['BookingRescheduleRequestDto']
export type BookingMergeSuggestion = BookingReschedule & Required<Pick<Schemas['DepartureResponse'], 'departureDate'>>
export type InsuranceRevision = Schemas['PersonalInsuranceRevisionRequestDto']

const multipartOptions = { ...formResponseOptions, headers: { 'Content-Type': 'multipart/form-data' } }

function multipartRequest(request: BookingCreateRequest | InsuranceRevision) {
  const body = new FormData()
  body.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }))
  return body
}

export const bookingOrdersApi = {
  suggestions: async (params: DepartureSuggestionQuery) =>
    (await apiClient.get<ApiResponse<DepartureSuggestion>>('/departures/suggestions', { params })).data.data,
  previewRegulations: async (body: RegulationPreviewRequest) =>
    (await apiClient.post<ApiResponse<RegulationPreview>>('/country-rules/preview', body)).data.data,
  previewPricing: async (body: PricingPreviewRequest) =>
    (await apiClient.post<ApiResponse<PricingPreview>>('/pricing/preview', body)).data.data,
  transferOptions: async (body: TransferOptionsRequest) =>
    (await apiClient.post<ApiResponse<TransferOptions>>('/pricing/transfer-options', body)).data.data,
  create: async (request: BookingCreateRequest, insuranceFiles: File[] = []) => {
    if (!insuranceFiles.length) return readFormResponse<BookingOrder>(await apiClient.post('/bookings', request, formResponseOptions))
    const body = multipartRequest(request)
    for (const file of insuranceFiles) body.append('insuranceFiles', file)
    return readFormResponse<BookingOrder>(await apiClient.post('/bookings', body, multipartOptions))
  },
  list: async () => (await apiClient.get<ApiResponse<BookingSummary[]>>('/bookings')).data.data,
  detail: async (id: number) => (await apiClient.get<ApiResponse<BookingDetail>>(`/bookings/${id}`)).data.data,
  previewDeliveryChange: async (id: number, body: DeliveryChangeSelection) =>
    readFormResponse<DeliveryChangePreview>(await apiClient.post(`/bookings/${id}/delivery-change/preview`, body, formResponseOptions)),
  confirmDeliveryChange: async (id: number, body: Schemas['DeliveryChangeConfirmRequestDto']) =>
    readFormResponse<DeliveryChangePreview>(await apiClient.post(`/bookings/${id}/delivery-change`, body, formResponseOptions)),
  cancellationPreview: async (id: number) =>
    (await apiClient.get<ApiResponse<BookingCancellation>>(`/bookings/${id}/cancellation-preview`)).data.data,
  delay: async (id: number) =>
    (await apiClient.get<ApiResponse<BookingDelay[]>>(`/bookings/${id}/delay`)).data.data,
  delayRefundPreview: async (id: number) =>
    (await apiClient.get<ApiResponse<BookingCancellation>>(`/bookings/${id}/delay/refund-preview`)).data.data,
  delayRefund: async (id: number, reason: string) =>
    (await apiClient.post<ApiResponse<BookingCancellation>>(`/bookings/${id}/delay/refund`, { reason })).data.data,
  delayReschedule: async (id: number, body: BookingReschedule) =>
    (await apiClient.post<ApiResponse<BookingOrder>>(`/bookings/${id}/delay/reschedule`, body)).data.data,
  cancel: async (id: number, reason: string) =>
    (await apiClient.post<ApiResponse<BookingCancellation>>(`/bookings/${id}/cancel`, { reason })).data.data,
  mergeSuggestion: async (id: number) =>
    (await apiClient.get<ApiResponse<BookingMergeSuggestion | null>>(`/bookings/${id}/reschedule-suggestion`)).data.data,
  reschedule: async (id: number, body: Schemas['BookingRescheduleRequestDto']) =>
    (await apiClient.post<ApiResponse<BookingOrder>>(`/bookings/${id}/reschedule`, body)).data.data,
  resubmit: async (id: number) =>
    (await apiClient.post<ApiResponse<BookingOrder>>(`/bookings/${id}/resubmit`, {})).data.data,
  reviseInsurance: async (id: number, insuranceId: number, request: InsuranceRevision, file: File) => {
    const body = multipartRequest(request)
    body.append('file', file)
    readFormResponse(await apiClient.post(`/bookings/${id}/insurances/${insuranceId}/revise`, body, multipartOptions))
  },
  myProfile: async () =>
    (await apiClient.get<ApiResponse<CustomerProfile>>('/customers/me')).data.data,
  updateMyProfile: async (body: CustomerProfileUpdate) =>
    readFormResponse<CustomerProfile>(await apiClient.put('/customers/me', body, formResponseOptions)),
}
