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

export const adminServicesApi = {
  // Lấy danh sách dịch vụ
  listServices: async (): Promise<BackendServiceItem[]> => {
    const res = await apiClient.get<ApiResponse<BackendServiceItem[]>>('/admin/services')
    return res.data?.data || []
  },

  // Lấy danh sách danh mục phân loại
  listCategories: async (): Promise<BackendCategoryItem[]> => {
    const res = await apiClient.get<ApiResponse<BackendCategoryItem[]>>('/admin/services/categories')
    return res.data?.data || []
  },

  // Lấy chi tiết 1 dịch vụ
  getService: async (id: number): Promise<BackendServiceItem> => {
    const res = await apiClient.get<ApiResponse<BackendServiceItem>>(`/admin/services/${id}`)
    return res.data.data
  },

  // Tạo mới dịch vụ
  createService: async (payload: CreateServicePayload): Promise<BackendServiceItem> => {
    const res = await apiClient.post<ApiResponse<BackendServiceItem>>('/admin/services', payload)
    return res.data.data
  },

  // Cập nhật dịch vụ
  updateService: async (id: number, payload: CreateServicePayload): Promise<BackendServiceItem> => {
    const res = await apiClient.put<ApiResponse<BackendServiceItem>>(`/admin/services/${id}`, payload)
    return res.data.data
  },

  // Bật/tắt trạng thái
  toggleStatus: async (id: number): Promise<void> => {
    await apiClient.patch(`/admin/services/${id}/toggle-status`)
  },
}
