import type { StaffRole } from '@shared/types/role'

// Menu theo vai trò. Các mục của Flow 1 (tiếp nhận, duyệt báo giá, duyệt hồ sơ ngựa, phương án xe) theo quy trình mới.
export const STAFF_MENUS: Record<StaffRole, [path: string, label: string, opt?: { group?: string; icon?: string }][]> = {
  // Quản trị viên chỉ có một mục; lọc và sort được thực hiện inline trên trang
  admin: [
    ['/admin/routes', 'Tuyến đường cố định', { icon: 'fa-road' }],
    ['/admin/accounts', 'Tất cả tài khoản', { icon: 'fa-users-gear' }],
  ],
  manager: [
    ['/manager', 'Tổng quan', { group: 'Việc cần làm', icon: 'fa-chart-pie' }],
    ['/manager/intake', 'Tiếp nhận', { icon: 'fa-inbox' }],
    ['/manager/approvals', 'Duyệt giá', { icon: 'fa-file-signature' }],
    ['/manager/incidents', 'Sự cố', { icon: 'fa-triangle-exclamation' }],
    ['/manager/reports', 'Doanh thu', { group: 'Báo cáo', icon: 'fa-chart-line' }],
    ['/manager/trip-reports', 'Chuyến đi', { icon: 'fa-clipboard-check' }],
    ['/manager/cancelled-orders', 'Đơn đã hủy', { icon: 'fa-ban' }],
  ],
  specialist: [
    ['/specialist/verification', 'Duyệt hồ sơ ngựa', { icon: 'fa-file-medical' }],
    ['/specialist/legal', 'Giấy tờ chuyến đi', { icon: 'fa-file-signature' }],
  ],
  coordinator: [
    ['/coordinator/fleet-plan', 'Xe và lộ trình', { icon: 'fa-route' }],
    ['/coordinator/monitoring', 'Giám sát', { icon: 'fa-satellite-dish' }],
    ['/coordinator/incidents', 'Sự cố', { icon: 'fa-triangle-exclamation' }],
    ['/coordinator/fleet', 'Đội xe', { icon: 'fa-truck' }],
  ],
  driver: [['/driver', 'Chuyến của tôi']],
  escort: [['/escort', 'Nhật ký sức khỏe']],
}

export const HOME_OF: Record<StaffRole, string> = {
  admin: '/admin/accounts',
  manager: '/manager',
  specialist: '/specialist/verification',
  coordinator: '/coordinator/fleet-plan',
  driver: '/driver',
  escort: '/escort',
}
