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

export const bookingOrdersApi = {
  suggestions: async (params: DepartureSuggestionQuery) =>
    (await apiClient.get<ApiResponse<DepartureSuggestion>>('/departures/suggestions', { params })).data.data,
  previewRegulations: async (body: RegulationPreviewRequest) =>
    (await apiClient.post<ApiResponse<RegulationPreview>>('/country-rules/preview', body)).data.data,
  previewPricing: async (body: PricingPreviewRequest) =>
    (await apiClient.post<ApiResponse<PricingPreview>>('/pricing/preview', body)).data.data,
  create: async (body: BookingCreateRequest) =>
    readFormResponse<BookingOrder>(await apiClient.post('/bookings', body, formResponseOptions)),
  myProfile: async () =>
    (await apiClient.get<ApiResponse<CustomerProfile>>('/customers/me')).data.data,
}
