// Thông báo đẩy xuống từng vai trò (chuông trên thanh menu). Kết nối GET/PATCH /api/v1/notifications
import apiClient, { type ApiResponse } from '../api/client'
import type { Role } from '../types/role'

export interface Notice {
  id: string
  role: Role
  name?: string // bỏ trống = mọi người thuộc vai trò (Manager)
  title: string
  text: string
  link?: string // đường dẫn trong app của người nhận
  bookingId?: string
  at: number
  read: boolean
}

export const noticesApi = {
  list: async (role: Role, name?: string): Promise<Notice[]> => {
    try {
      const res = await apiClient.get<ApiResponse<Notice[]>>('/notifications', {
        params: { role, name },
      })
      return res.data.data ?? []
    } catch {
      return []
    }
  },
  markRead: async (id: string): Promise<void> => {
    try {
      await apiClient.patch(`/notifications/${id}/read`)
    } catch {
      // ignore
    }
  },
  markAllRead: async (role: Role, name?: string): Promise<void> => {
    try {
      await apiClient.patch('/notifications/read-all', null, {
        params: { role, name },
      })
    } catch {
      // ignore
    }
  },
  delete: async (id: string, role: Role): Promise<void> => {
    await apiClient.delete(`/notifications/${id}`, { params: { role } })
  },
  deleteAll: async (role: Role): Promise<void> => {
    await apiClient.delete('/notifications', { params: { role } })
  },
}
