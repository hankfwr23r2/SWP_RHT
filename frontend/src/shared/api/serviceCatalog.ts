import apiClient, { type ApiResponse } from './client'

export type BookingServiceCategory = 'stall' | 'feed' | 'water' | 'insurance'

export interface ServiceCatalogItem {
  id: number
  serviceName: string
  categoryId: number
  categoryName: string
  price: number
  status: 'ACTIVE' | 'INACTIVE' | string
}

export const serviceCatalogApi = {
  listActive: async (): Promise<ServiceCatalogItem[]> => {
    const res = await apiClient.get<ApiResponse<ServiceCatalogItem[]>>('/public/services')
    return res.data.data
  },
}
