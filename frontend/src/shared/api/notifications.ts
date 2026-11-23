// Hộp thư thông báo của người đang đăng nhập (B6)
import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

export type Notice = components['schemas']['NotificationResponseDto']

export const notificationsApi = {
  mine: async () => (await apiClient.get<ApiResponse<Notice[]>>('/notifications/mine')).data.data ?? [],
  markRead: async (id: number) => { await apiClient.post(`/notifications/${id}/read`) },
  markAllRead: async () => { await apiClient.post('/notifications/read-all') },
  remove: async (id: number) => { await apiClient.delete(`/notifications/${id}`) },
  removeAll: async () => { await apiClient.delete('/notifications/mine') },
}
