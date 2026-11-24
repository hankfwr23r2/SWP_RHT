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
export type BookingCreateRequest = Schemas['BookingCreateRequestDto']
export type CustomerProfile = Schemas['CustomerProfileResponseDto']
export type BookingOrder = Schemas['BookingResponseDto']
export type BookingStatus = NonNullable<BookingOrder['status']>
export type BookingSummary = Schemas['BookingSummaryResponseDto']
export type BookingDetail = Schemas['BookingDetailResponseDto']
export type BookingCancellation = Schemas['BookingCancellationResponseDto']
export type InsuranceRevision = Schemas['PersonalInsuranceRevisionRequestDto']

export const bookingOrdersApi = {
  suggestions: async (params: DepartureSuggestionQuery) =>
    (await apiClient.get<ApiResponse<DepartureSuggestion>>('/departures/suggestions', { params })).data.data,
  previewRegulations: async (body: RegulationPreviewRequest) =>
    (await apiClient.post<ApiResponse<RegulationPreview>>('/country-rules/preview', body)).data.data,
  previewPricing: async (body: PricingPreviewRequest) =>
    (await apiClient.post<ApiResponse<PricingPreview>>('/pricing/preview', body)).data.data,
  create: async (body: BookingCreateRequest) =>
    readFormResponse<BookingOrder>(await apiClient.post('/bookings', body, formResponseOptions)),
  list: async () => (await apiClient.get<ApiResponse<BookingSummary[]>>('/bookings')).data.data,
  detail: async (id: number) => (await apiClient.get<ApiResponse<BookingDetail>>(`/bookings/${id}`)).data.data,
  cancellationPreview: async (id: number) =>
    (await apiClient.get<ApiResponse<BookingCancellation>>(`/bookings/${id}/cancellation-preview`)).data.data,
  cancel: async (id: number, reason: string) =>
    (await apiClient.post<ApiResponse<BookingCancellation>>(`/bookings/${id}/cancel`, { reason })).data.data,
  reschedule: async (id: number, body: Schemas['BookingRescheduleRequestDto']) =>
    (await apiClient.post<ApiResponse<BookingOrder>>(`/bookings/${id}/reschedule`, body)).data.data,
  resubmit: async (id: number) =>
    (await apiClient.post<ApiResponse<BookingOrder>>(`/bookings/${id}/resubmit`, {})).data.data,
  reviseInsurance: async (id: number, insuranceId: number, body: InsuranceRevision) => {
    await apiClient.post(`/bookings/${id}/insurances/${insuranceId}/revise`, body)
  },
  myProfile: async () =>
    (await apiClient.get<ApiResponse<CustomerProfile>>('/customers/me')).data.data,
}
