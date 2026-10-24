import apiClient, { type ApiResponse } from './client'

export interface BackendServiceItem {
  id: number
  serviceName: string
  categoryId: number
  categoryName: string
  price: number
  status: 'ACTIVE' | 'INACTIVE' | string
}

export interface BackendCategoryItem {
  id: number
  categoryName: string
  description?: string
}

export interface CreateServicePayload {
  serviceName: string
  categoryId: number
  price: number
  status?: string
}

const SERVICES_TTL = 3 * 60 * 1000 // 3 phút
let servicesCache: { time: number; data: BackendServiceItem[] } | null = null
let categoriesCache: { time: number; data: BackendCategoryItem[] } | null = null

export const invalidateServicesCache = () => {
  servicesCache = null
  categoriesCache = null
}

export const adminServicesApi = {
  // Lấy danh sách dịch vụ (có TTL cache)
  listServices: async (): Promise<BackendServiceItem[]> => {
    const now = Date.now()
    if (servicesCache && now - servicesCache.time < SERVICES_TTL) {
      return servicesCache.data
    }
    const res = await apiClient.get<ApiResponse<BackendServiceItem[]>>('/admin/services')
    const data = res.data?.data || []
    servicesCache = { time: now, data }
    return data
  },

  // Lấy danh sách danh mục phân loại (có TTL cache)
  listCategories: async (): Promise<BackendCategoryItem[]> => {
    const now = Date.now()
    if (categoriesCache && now - categoriesCache.time < SERVICES_TTL) {
      return categoriesCache.data
    }
    const res = await apiClient.get<ApiResponse<BackendCategoryItem[]>>('/admin/services/categories')
    const data = res.data?.data || []
    categoriesCache = { time: now, data }
    return data
  },

  // Lấy chi tiết 1 dịch vụ
  getService: async (id: number): Promise<BackendServiceItem> => {
    const res = await apiClient.get<ApiResponse<BackendServiceItem>>(`/admin/services/${id}`)
    return res.data.data
  },

  // Tạo mới dịch vụ
  createService: async (payload: CreateServicePayload): Promise<BackendServiceItem> => {
    invalidateServicesCache()
    const res = await apiClient.post<ApiResponse<BackendServiceItem>>('/admin/services', payload)
    return res.data.data
  },

  // Cập nhật dịch vụ
  updateService: async (id: number, payload: CreateServicePayload): Promise<BackendServiceItem> => {
    invalidateServicesCache()
    const res = await apiClient.put<ApiResponse<BackendServiceItem>>(`/admin/services/${id}`, payload)
    return res.data.data
  },

  // Bật/tắt trạng thái
  toggleStatus: async (id: number): Promise<void> => {
    invalidateServicesCache()
    await apiClient.patch(`/admin/services/${id}/toggle-status`)
  },
}
