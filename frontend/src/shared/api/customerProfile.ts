import apiClient, { type ApiResponse } from './client'

export interface CustomerProfileDto {
  userId: number
  username: string
  email: string
  fullName: string
  phone: string | null
  address: string | null
  totalHorses: number
  totalBookings: number
}

export interface UpdateCustomerProfilePayload {
  fullName: string
  phone?: string
  address?: string
}

export interface ChangePasswordPayload {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

export const customerProfileApi = {
  getProfile: async (): Promise<CustomerProfileDto> => {
    const res = await apiClient.get<ApiResponse<CustomerProfileDto>>('/customer/profile')
    return res.data.data
  },

  updateProfile: async (payload: UpdateCustomerProfilePayload): Promise<CustomerProfileDto> => {
    const res = await apiClient.put<ApiResponse<CustomerProfileDto>>('/customer/profile', payload)
    return res.data.data
  },

  changePassword: async (payload: ChangePasswordPayload): Promise<void> => {
    await apiClient.post<ApiResponse<void>>('/auth/change-password', payload)
  },
}
