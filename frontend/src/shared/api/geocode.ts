import apiClient, { type ApiResponse } from './client'

export interface GeocodeResult {
  label: string
  latitude: number
  longitude: number
}

export const searchLocations = async (q: string, countryCode: string) =>
  (await apiClient.get<ApiResponse<GeocodeResult[]>>('/geocode/search', { params: { q, countryCode } })).data.data
