import apiClient, { type ApiResponse } from './client'

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
  isAvailable: boolean
  busyReason?: string
}

export interface BackendCrewResponse {
  userId: number
  staffCode: string
  name: string
  role: string // DRIVER, ESCORT, COORDINATOR, SPECIALIST
  phone: string
  email: string
  employmentStatus: string
  isAvailable: boolean
  busyReason?: string
}

export const backendFleetApi = {
  // Lấy danh sách đội xe (có thể lọc theo ngày khởi hành và loại chuyến để kiểm tra xung đột)
  getVehicles: async (departAt?: number, tripType?: string): Promise<BackendVehicleResponse[]> => {
    const res = await apiClient.get<ApiResponse<BackendVehicleResponse[]>>('/fleet/vehicles', {
      params: {
        departAt,
        tripType,
      },
    })
    return res.data.data
  },

  // Lấy danh sách nhân sự (Tài xế / Hộ tống / Điều phối / Thú y)
  getCrew: async (departAt?: number, role?: string): Promise<BackendCrewResponse[]> => {
    const res = await apiClient.get<ApiResponse<BackendCrewResponse[]>>('/fleet/crew', {
      params: {
        departAt,
        role,
      },
    })
    return res.data.data
  },
}
