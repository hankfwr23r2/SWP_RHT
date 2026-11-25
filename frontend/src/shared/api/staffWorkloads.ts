import apiClient, { type ApiResponse } from './client'
import type { BookingOrder } from './bookingOrders'
import type { components } from './generated/schema'

export type StaffWorkload = components['schemas']['StaffWorkloadResponseDto']

export const staffWorkloadsApi = {
  list: async (role: 'SPECIALIST' | 'COORDINATOR' = 'SPECIALIST') =>
    (await apiClient.get<ApiResponse<StaffWorkload[]>>('/staff-workloads', { params: { role } })).data.data,
  bookings: async (staffId: number) => (await apiClient.get<ApiResponse<BookingOrder[]>>('/staff-workloads/' + staffId + '/bookings')).data.data,
  reassign: async (id: number, request: components['schemas']['BookingReassignmentRequestDto']) =>
    (await apiClient.post<ApiResponse<BookingOrder>>('/bookings/' + id + '/reassign-specialist', request)).data.data,
}
