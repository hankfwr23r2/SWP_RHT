import apiClient, { type ApiResponse } from './client'

export type SystemConfigGroup = 'WELFARE' | 'PAYMENT' | 'BORDER' | 'OPERATIONS' | 'JOURNEY' | 'PRICING'

export interface SystemConfigItem {
  configKey: string
  configName: string
  configValue: string
  unit: string
  configGroup: SystemConfigGroup
  description: string
  updatedBy?: string
  updatedAt?: string
}

export const systemConfigApi = {
  listAdminConfigs: async (): Promise<SystemConfigItem[]> => {
    const response = await apiClient.get<ApiResponse<SystemConfigItem[]>>('/admin/system-configs')
    return response.data.data
  },

  updateConfig: async (key: string, configValue: string): Promise<SystemConfigItem> => {
    const response = await apiClient.put<ApiResponse<SystemConfigItem>>(
      `/admin/system-configs/${encodeURIComponent(key)}`,
      { configValue },
    )
    return response.data.data
  },
}
