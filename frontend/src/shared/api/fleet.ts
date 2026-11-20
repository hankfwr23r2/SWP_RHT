import apiClient, { type ApiResponse } from './client'
import type { VehicleSchedule } from '../types/fleet'

export interface BackendVehicleResponse {
  id: number
  code: string
  name: string
  type: string
  plate: string
  capacity: number
  status: string
  homeDepot?: string
  currentLocation?: string
  available: boolean // Lombok boolean isAvailable -> JSON "available"
  busyReason?: string
  busyBookingCode?: string
  busyUntil?: number
  onTrip?: boolean
  onTripBookingCode?: string
  returnEta?: number
  upcoming?: VehicleSchedule[]
}

export interface VehiclePayload {
  type: string
  capacity: number
  plate: string
  status?: string
}

export interface BackendCrewResponse {
  userId: number
  staffCode: string
  name: string
  role: string // DRIVER, ESCORT, COORDINATOR, SPECIALIST
  phone: string
  email: string
  employmentStatus: string
  available: boolean // Lombok boolean isAvailable -> JSON "available"
  busyReason?: string
  busyBookingCode?: string
  busyUntil?: number
}

export const backendFleetApi = {
  // Lấy danh sách đội xe. Kèm ngày đi (và mã đơn đang xếp) thì backend tính xe nào chọn được cho đơn đó
  getVehicles: async (departAt?: number, tripType?: string, bookingId?: string): Promise<BackendVehicleResponse[]> => {
    const res = await apiClient.get<ApiResponse<BackendVehicleResponse[]>>('/fleet/vehicles', {
      params: {
        departAt,
        tripType,
        bookingId,
      },
    })
    return res.data.data
  },

  // Lấy danh sách nhân sự (Tài xế / Hộ tống / Điều phối / Thú y)
  getCrew: async (departAt?: number, role?: string, tripType?: string, bookingId?: string): Promise<BackendCrewResponse[]> => {
    const res = await apiClient.get<ApiResponse<BackendCrewResponse[]>>('/fleet/crew', {
      params: {
        departAt,
        role,
        tripType,
        bookingId,
      },
    })
    return res.data.data
  },

  createVehicle: async (payload: VehiclePayload): Promise<BackendVehicleResponse> => {
    const res = await apiClient.post<ApiResponse<BackendVehicleResponse>>('/fleet/vehicles', payload)
    return res.data.data
  },

  updateVehicle: async (id: string, payload: VehiclePayload): Promise<BackendVehicleResponse> => {
    const res = await apiClient.put<ApiResponse<BackendVehicleResponse>>(`/fleet/vehicles/${encodeURIComponent(id)}`, payload)
    return res.data.data
  },

  removeVehicle: async (id: string): Promise<void> => {
    await apiClient.delete(`/fleet/vehicles/${encodeURIComponent(id)}`)
  },
}
