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
  latitude?: number | null
  longitude?: number | null
}

export interface BackendLocationResponse {
  id: number
  locationCode?: string
  locationType: string // Warehouse, RidingClub, RaceTrack, Stable, RestStop, BorderGate
  locationName: string
  address: string
  city: string
  province: string
  country: string
  latitude?: number | null
  longitude?: number | null
  status: 'ACTIVE' | 'INACTIVE' | string
  openingMinutes?: number | null
  closingMinutes?: number | null
  contactPhone?: string | null
  destinationCountry?: string | null
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

export interface LocationPayload {
  locationType: string
  locationName: string
  address: string
  city: string
  province: string
  country: string
  latitude: number
  longitude: number
  status: 'ACTIVE' | 'INACTIVE'
  openingMinutes?: number
  closingMinutes?: number
  contactPhone?: string
  destinationCountry?: 'LA' | 'KH'
}

const LOCATIONS_TTL = 3 * 60 * 1000 // 3 phút
let locationsCache: { key: string; time: number; data: BackendLocationResponse[] } | null = null

export const invalidateLocationsCache = () => {
  locationsCache = null
}

export type CountryCodeVnKhLa = 'VN' | 'KH' | 'LA'

export interface RoutePreviewPlace {
  name: string
  lat: number
  lng: number
  country: CountryCodeVnKhLa
}

export interface RoutePreviewRequest {
  pickup: RoutePreviewPlace
  dropoff: RoutePreviewPlace
  stopLocationIds: number[]
  vehicleId: string
  departAt: number
}

export type RoutePreviewEventType = 'DRIVE' | 'REST' | 'ROADSIDE_REST' | 'OVERNIGHT' | 'BORDER_WAIT' | 'BORDER_PROCESSING'

export interface RoutePreviewEvent {
  type: RoutePreviewEventType
  place: string
  startAt: number
  endAt: number
  km: number
  lat: number | null
  lng: number | null
}

export interface RoutePreviewResponse {
  source: string
  km: number
  driveHours: number
  legs: { from: string; to: string; km: number; driveHours: number }[]
  path: [number, number][]
  toll: { amountVnd: number | null; source: string | null }
  schedule: RoutePreviewEvent[]
  arrivalAt: number
  borderArrivalAt: number | null
  crewDays: number
  warnings: string[]
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

  // Lấy toàn bộ danh mục địa điểm để lựa chọn điểm đang hoạt động và hiển thị đơn cũ.
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

  listAdminLocations: async (): Promise<BackendLocationResponse[]> => {
    const res = await apiClient.get<ApiResponse<BackendLocationResponse[]>>('/locations/admin')
    return res.data?.data || []
  },

  createLocation: async (payload: LocationPayload): Promise<BackendLocationResponse> => {
    invalidateLocationsCache()
    const res = await apiClient.post<ApiResponse<BackendLocationResponse>>('/locations', payload)
    return res.data.data
  },

  updateLocation: async (id: number, payload: LocationPayload): Promise<BackendLocationResponse> => {
    invalidateLocationsCache()
    const res = await apiClient.put<ApiResponse<BackendLocationResponse>>(`/locations/${id}`, payload)
    return res.data.data
  },

  changeLocationStatus: async (id: number, status: 'ACTIVE' | 'INACTIVE'): Promise<BackendLocationResponse> => {
    invalidateLocationsCache()
    const res = await apiClient.patch<ApiResponse<BackendLocationResponse>>(`/locations/${id}/status`, { status })
    return res.data.data
  },
  // Xem trước lộ trình xe tải (Valhalla) + lịch trình + phí cầu đường, do backend tính
  routePreview: async (payload: RoutePreviewRequest, signal?: AbortSignal): Promise<RoutePreviewResponse> => {
    const res = await apiClient.post<ApiResponse<RoutePreviewResponse>>('/coordinator/route-preview', payload, { signal })
    return res.data.data
  },
}
