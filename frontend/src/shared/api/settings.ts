import apiClient, { type ApiResponse } from './client'
import { formResponseOptions, readFormResponse } from './customerAddresses'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type SystemSetting = Schemas['SystemConfigResponseDto']
export type SystemSettingUpdate = Schemas['SystemConfigUpdateRequestDto']

export const settingsApi = {
  list: async () =>
    (await apiClient.get<ApiResponse<SystemSetting[]>>('/system-configs')).data.data,
  update: async (key: string, body: SystemSettingUpdate) =>
    readFormResponse<SystemSetting>(await apiClient.put(`/system-configs/${encodeURIComponent(key)}`, body, formResponseOptions)),
}
