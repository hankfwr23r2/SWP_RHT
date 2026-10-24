import apiClient, { type ApiResponse } from './client'

export interface BackendRouteResponse {
  id: number
  routeName: string
  description?: string
  estimatedTime?: number // giờ
  distance?: number // km
  status: 'ACTIVE' | 'INACTIVE' | string
  originId?: number
  originName?: string
  destinationId?: number
  destinationName?: string
}

export interface BackendLocationRouteResponse {
  id?: { routeID: number; locationID: number }
  routeID: number
  locationID: number
  stopOrder: number
  arrivalTime?: string
  departureTime?: string
  locationName?: string
  locationType?: string
  address?: string
  province?: string
  country?: string
  latitude?: number
  longitude?: number
}

export interface BackendLocationResponse {
  id: number
  locationType: string // Warehouse, RidingClub, RaceTrack, Stable, RestStop, BorderGate
  locationName: string
  address: string
  city: string
  province: string
  country: string
}

export interface CreateRoutePayload {
  routeName: string
  description?: string
  estimatedTime?: number
  distance?: number
  status?: string
  stops?: Array<{
    locationId: number
    stopOrder?: number
    latitude?: number
    longitude?: number
  }>
}

export interface CreateRestStopPayload {
  locationName: string
  address: string
  city: string
  province: string
  country: string
}

const LOCATIONS_TTL = 3 * 60 * 1000 // 3 phút
let locationsCache: { key: string; time: number; data: BackendLocationResponse[] } | null = null

export const invalidateLocationsCache = () => {
  locationsCache = null
}

export const routesApi = {
  // Lấy danh sách tất cả các tuyến cố định
  listRoutes: async (): Promise<BackendRouteResponse[]> => {
    const res = await apiClient.get<ApiResponse<BackendRouteResponse[]>>('/routes')
    return res.data?.data || []
  },

  // Chi tiết 1 tuyến
  getRoute: async (id: number): Promise<BackendRouteResponse> => {
    const res = await apiClient.get<ApiResponse<BackendRouteResponse>>(`/routes/${id}`)
    return res.data.data
  },

  // Lấy các điểm dừng của tuyến theo thứ tự
  getRouteStops: async (routeId: number): Promise<BackendLocationRouteResponse[]> => {
    const res = await apiClient.get<ApiResponse<BackendLocationRouteResponse[]>>(`/routes/${routeId}/stops`)
    return res.data?.data || []
  },

  // Admin tạo tuyến mới
  createRoute: async (payload: CreateRoutePayload): Promise<BackendRouteResponse> => {
    const res = await apiClient.post<ApiResponse<BackendRouteResponse>>('/routes', payload)
    return res.data.data
  },

  // Admin cập nhật tuyến
  updateRoute: async (id: number, payload: CreateRoutePayload): Promise<BackendRouteResponse> => {
    const res = await apiClient.put<ApiResponse<BackendRouteResponse>>(`/routes/${id}`, payload)
    return res.data.data
  },

  // Admin ngừng kích hoạt tuyến
  deleteRoute: async (id: number): Promise<void> => {
    await apiClient.delete<ApiResponse<void>>(`/routes/${id}`)
  },

  // Lấy danh mục các Hub, trạm nghỉ, cửa khẩu, nút giao (có TTL cache)
  listLocations: async (params?: { type?: string; country?: string }): Promise<BackendLocationResponse[]> => {
    const key = `${params?.type ?? ''}_${params?.country ?? ''}`
    const now = Date.now()
    if (locationsCache && locationsCache.key === key && now - locationsCache.time < LOCATIONS_TTL) {
      return locationsCache.data
    }
    const res = await apiClient.get<ApiResponse<BackendLocationResponse[]>>('/locations', { params })
    const data = res.data?.data || []
    locationsCache = { key, time: now, data }
    return data
  },

  createRestStop: async (payload: CreateRestStopPayload): Promise<BackendLocationResponse> => {
    invalidateLocationsCache()
    const res = await apiClient.post<ApiResponse<BackendLocationResponse>>('/locations/rest-stops', payload)
    return res.data.data
  },

  updateRestStop: async (id: number, payload: CreateRestStopPayload): Promise<BackendLocationResponse> => {
    invalidateLocationsCache()
    const res = await apiClient.put<ApiResponse<BackendLocationResponse>>(`/locations/rest-stops/${id}`, payload)
    return res.data.data
  },

  deleteRestStop: async (id: number): Promise<void> => {
    invalidateLocationsCache()
    await apiClient.delete<ApiResponse<void>>(`/locations/rest-stops/${id}`)
  },
}
