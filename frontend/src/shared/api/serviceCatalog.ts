import apiClient, { type ApiResponse } from './client'

export type BookingServiceCategory = 'stall' | 'feed' | 'water' | 'insurance'
export type BookingRequiredServiceCategory = Exclude<BookingServiceCategory, 'water'>
export type ServiceBillingUnit = 'PER_TRIP' | 'PER_HORSE' | 'PER_HORSE_DAY'

export const SERVICE_BILLING_UNIT_LABEL: Record<ServiceBillingUnit, string> = {
  PER_TRIP: 'mỗi chuyến',
  PER_HORSE: 'mỗi ngựa',
  PER_HORSE_DAY: 'mỗi ngựa × ngày',
}

export interface ServiceCatalogItem {
  id: number
  serviceCode: string
  serviceName: string
  categoryId: number
  categoryName: string
  price: number
  billingUnit: ServiceBillingUnit
  description?: string
  status: 'ACTIVE' | 'INACTIVE' | string
}

export const serviceCatalogApi = {
  listActive: async (): Promise<ServiceCatalogItem[]> => {
    const res = await apiClient.get<ApiResponse<ServiceCatalogItem[]>>('/public/services')
    return res.data.data
  },
}
