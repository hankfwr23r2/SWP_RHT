import apiClient, { type ApiResponse } from './client'
import type { Role } from '../types/role'

export interface CreateStaffPayload {
  name: string
  email: string
  phone?: string
  role: Role
}

export interface AccountLogItem {
  id: number
  actionType: string
  actionByName: string
  actionByEmail: string
  targetUserId: number
  oldData?: string
  newData?: string
  description: string
  createdAt: string
}

export interface AccountApiItem {
  id: string
  userId: number
  name: string
  email: string
  phone?: string
  role: Role
  status: 'active' | 'locked'
  staffCode?: string
  hireDate?: string
  tempPassword?: string
  history?: AccountLogItem[]
}

export const adminAccountsApi = {
  // 1. Lấy danh sách tài khoản từ Backend (GET /api/v1/admin/accounts)
  list: async (): Promise<AccountApiItem[]> => {
    const res = await apiClient.get<ApiResponse<AccountApiItem[]>>('/admin/accounts')
    return res.data.data
  },

  // 2. Tạo tài khoản nhân viên mới (POST /api/v1/admin/accounts)
  create: async (payload: CreateStaffPayload): Promise<AccountApiItem> => {
    const res = await apiClient.post<ApiResponse<AccountApiItem>>('/admin/accounts', payload)
    return res.data.data
  },

  // 3. Khóa hoặc Mở khóa tài khoản (PATCH /api/v1/admin/accounts/{userId}/status)
  toggleLock: async (userId: number, locked: boolean, reason?: string): Promise<void> => {
    await apiClient.patch(`/admin/accounts/${userId}/status`, { locked, reason })
  },

  // 4. Đặt lại mật khẩu tạm (POST /api/v1/admin/accounts/{userId}/reset-password)
  resetPassword: async (userId: number): Promise<string> => {
    const res = await apiClient.post<ApiResponse<{ tempPassword: string }>>(`/admin/accounts/${userId}/reset-password`)
    return res.data.data.tempPassword
  },

  // 5. Lấy lịch sử audit log của 1 tài khoản (GET /api/v1/admin/accounts/{userId}/logs)
  getLogs: async (userId: number): Promise<AccountLogItem[]> => {
    const res = await apiClient.get<ApiResponse<AccountLogItem[]>>(`/admin/accounts/${userId}/logs`)
    return res.data.data
  },

  // 6. Lấy toàn bộ lịch sử audit log hệ thống (GET /api/v1/admin/accounts/logs)
  getAllLogs: async (): Promise<AccountLogItem[]> => {
    const res = await apiClient.get<ApiResponse<AccountLogItem[]>>('/admin/accounts/logs')
    return res.data.data
  },
}

export default adminAccountsApi
