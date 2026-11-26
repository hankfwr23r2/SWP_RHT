import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

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

type NetworkLocation = components['schemas']['LocationResponse']
type NetworkRoute = components['schemas']['RouteResponse']
type NetworkRouteStop = components['schemas']['RouteStopResponse']

const locationLabels: Partial<Record<NonNullable<NetworkLocation['locationType']>, string>> = {
  BORDER_GATE: 'BorderGate',
  REST_STOP: 'RestStop',
  RACETRACK: 'RaceTrack',
  STABLE: 'Stable',
}

function locationTypeFromApi(type?: NetworkLocation['locationType']): string {
  return type ? locationLabels[type] ?? type : ''
}

function locationTypeToApi(type?: string): string | undefined {
  return Object.entries(locationLabels).find(([, label]) => label === type)?.[0] ?? type
}

function minutesFromTime(time?: string): number | undefined {
  if (!time) return undefined
  const [hours, minutes, seconds = 0] = time.split(':').map(Number)
  return hours * 60 + minutes + seconds / 60
}

function timeFromMinutes(minutes?: number): string | undefined {
  if (minutes === undefined || !Number.isFinite(minutes) || minutes < 0 || minutes >= 1440) {
    return undefined
  }
  const seconds = Math.floor(minutes * 60)
  return [Math.floor(seconds / 3600), Math.floor(seconds / 60) % 60, seconds % 60]
    .map(value => String(value).padStart(2, '0')).join(':')
}

function locationFromApi(location: NetworkLocation): BackendLocationResponse {
  return {
    id: location.id ?? 0,
    locationCode: location.code,
    locationType: locationTypeFromApi(location.locationType),
    locationName: location.name ?? '',
    address: location.address ?? '',
    city: '',
    province: location.provinceCode ?? '',
    country: location.countryCode ?? '',
    latitude: location.latitude,
    longitude: location.longitude,
    status: location.status ?? '',
    openingMinutes: minutesFromTime(location.openingTime),
    closingMinutes: minutesFromTime(location.closingTime),
    contactPhone: location.contactPhone,
  }
}

function locationToApi(payload: LocationPayload) {
  const { locationName, locationType, country, province, openingMinutes, closingMinutes, ...location } = payload
  return {
    ...location,
    name: locationName,
    locationType: locationTypeToApi(locationType),
    countryCode: country,
    provinceCode: province,
    openingTime: timeFromMinutes(openingMinutes),
    closingTime: timeFromMinutes(closingMinutes),
  }
}

function routeFromApi(route: NetworkRoute): BackendRouteResponse {
  return {
    id: route.id ?? 0,
    routeName: route.name ?? '',
    distance: route.distanceKm,
    estimatedTime: route.estimatedDurationMinutes === undefined ? undefined : route.estimatedDurationMinutes / 60,
    status: route.status ?? '',
    originId: route.originLocationId,
    originName: route.originName,
    destinationId: route.destinationLocationId,
    destinationName: route.destinationName,
  }
}

function routeToApi(payload: CreateRoutePayload) {
  const { routeName, distance, estimatedTime, stops, ...route } = payload
  return {
    ...route,
    name: routeName,
    distanceKm: distance,
    estimatedDurationMinutes: estimatedTime === undefined ? undefined : Math.round(estimatedTime * 60),
    stops: stops?.map(({ stopOrder, ...stop }) => ({ ...stop, sequenceNumber: stopOrder })),
  }
}

function stopFromApi(routeId: number, stop: NetworkRouteStop): BackendLocationRouteResponse {
  return {
    routeID: routeId,
    locationID: stop.locationId ?? 0,
    stopOrder: stop.sequenceNumber ?? 0,
    locationName: stop.locationName,
    locationType: locationTypeFromApi(stop.locationType),
  }
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

export interface RoadRouteRequest {
  points: { lat: number; lng: number }[]
  alternatives: boolean
}

// routes[0]: đường chính; các phần tử sau: đường tránh
export interface RoadRouteResponse {
  routes: { km: number; driveHours: number; path: [number, number][] }[]
  warnings: string[]
}

export interface RoutePreviewResponse {
  source: string
  km: number
  driveHours: number
  legs: { from: string; to: string; km: number; driveHours: number }[]
  path: [number, number][]
  schedule: RoutePreviewEvent[]
  arrivalAt: number
  borderArrivalAt: number | null
  crewDays: number
  warnings: string[]
}

export const routesApi = {
  // Lấy danh sách tất cả các tuyến cố định
  listRoutes: async (): Promise<BackendRouteResponse[]> => {
    const res = await apiClient.get<ApiResponse<NetworkRoute[]>>('/routes')
    return (res.data?.data ?? []).map(routeFromApi)
  },

  // Chi tiết 1 tuyến
  getRoute: async (id: number): Promise<BackendRouteResponse> => {
    const res = await apiClient.get<ApiResponse<NetworkRoute>>(`/routes/${id}`)
    return routeFromApi(res.data.data)
  },

  // Lấy các điểm dừng của tuyến theo thứ tự
  getRouteStops: async (routeId: number): Promise<BackendLocationRouteResponse[]> => {
    const res = await apiClient.get<ApiResponse<NetworkRoute>>(`/routes/${routeId}`)
    return (res.data?.data?.stops ?? []).map(stop => stopFromApi(routeId, stop))
  },

  // Admin tạo tuyến mới
  createRoute: async (payload: CreateRoutePayload): Promise<BackendRouteResponse> => {
    const res = await apiClient.post<ApiResponse<NetworkRoute>>('/routes', routeToApi(payload))
    return routeFromApi(res.data.data)
  },

  // Admin cập nhật tuyến
  updateRoute: async (id: number, payload: CreateRoutePayload): Promise<BackendRouteResponse> => {
    const res = await apiClient.put<ApiResponse<NetworkRoute>>(`/routes/${id}`, routeToApi(payload))
    return routeFromApi(res.data.data)
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
    const res = await apiClient.get<ApiResponse<NetworkLocation[]>>('/locations', {
      params: { ...params, type: locationTypeToApi(params?.type) },
    })
    const data = (res.data?.data ?? []).map(locationFromApi)
    locationsCache = { key, time: now, data }
    return data
  },

  listAdminLocations: async (): Promise<BackendLocationResponse[]> => {
    const res = await apiClient.get<ApiResponse<NetworkLocation[]>>('/locations')
    return (res.data?.data ?? []).map(locationFromApi)
  },

  createLocation: async (payload: LocationPayload): Promise<BackendLocationResponse> => {
    invalidateLocationsCache()
    const res = await apiClient.post<ApiResponse<NetworkLocation>>('/locations', locationToApi(payload))
    return locationFromApi(res.data.data)
  },

  updateLocation: async (id: number, payload: LocationPayload): Promise<BackendLocationResponse> => {
    invalidateLocationsCache()
    const res = await apiClient.put<ApiResponse<NetworkLocation>>(`/locations/${id}`, locationToApi(payload))
    return locationFromApi(res.data.data)
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
  // Đường xe tải qua một dãy điểm cho phương án sự cố; alternatives: thêm đường tránh kẹt xe
  roadRoute: async (payload: RoadRouteRequest, signal?: AbortSignal): Promise<RoadRouteResponse> => {
    const res = await apiClient.post<ApiResponse<RoadRouteResponse>>('/coordinator/road-route', payload, { signal })
    return res.data.data
  },
}
