import apiClient, { type ApiResponse } from './client'
import type { components, operations } from './generated/schema'

type Schemas = components['schemas']
export type DepartureSuggestionQuery = operations['suggestions']['parameters']['query']
export type DepartureSuggestion = Schemas['DepartureSuggestionResponse']
export type RegulationPreviewRequest = Schemas['RegulationPreviewRequestDto']
export type RegulationPreview = Schemas['RegulationPreviewResponseDto']
export type PricingPreviewRequest = Schemas['PricingPreviewRequestDto']
export type PricingPreview = Schemas['PricingPreviewResponseDto']
export type BookingCreateRequest = Schemas['BookingCreateRequestDto']
export type BookingOrder = Schemas['BookingResponseDto']

export const bookingOrdersApi = {
  suggestions: async (params: DepartureSuggestionQuery) =>
    (await apiClient.get<ApiResponse<DepartureSuggestion>>('/departures/suggestions', { params })).data.data,
  previewRegulations: async (body: RegulationPreviewRequest) =>
    (await apiClient.post<ApiResponse<RegulationPreview>>('/country-rules/preview', body)).data.data,
  previewPricing: async (body: PricingPreviewRequest) =>
    (await apiClient.post<ApiResponse<PricingPreview>>('/pricing/preview', body)).data.data,
  create: async (body: BookingCreateRequest) =>
    (await apiClient.post<ApiResponse<BookingOrder>>('/bookings', body)).data.data,
}
