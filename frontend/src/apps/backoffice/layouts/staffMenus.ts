import type { StaffRole } from '@shared/types/role'

// Menu theo vai trò. Các mục của Flow 1 (tiếp nhận, duyệt báo giá, duyệt hồ sơ ngựa, phương án xe) theo quy trình mới.
export const STAFF_MENUS: Record<StaffRole, [path: string, label: string, opt?: { group?: string; icon?: string }][]> = {
  // Danh mục tài khoản theo vai trò; số lượng hiện cạnh từng mục
  admin: [
    ['/admin/accounts', 'Tất cả tài khoản', { icon: 'fa-users-gear' }],
    ['/admin/accounts/admin', 'Quản trị viên', { icon: 'fa-user-shield' }],
    ['/admin/accounts/manager', 'Quản lý', { icon: 'fa-user-tie' }],
    ['/admin/accounts/specialist', 'Kiểm dịch viên', { icon: 'fa-user-doctor' }],
    ['/admin/accounts/coordinator', 'Điều phối viên', { icon: 'fa-route' }],
    ['/admin/accounts/driver', 'Tài xế', { icon: 'fa-id-card' }],
    ['/admin/accounts/escort', 'Hộ tống', { icon: 'fa-horse-head' }],
    ['/admin/accounts/customer', 'Khách hàng', { icon: 'fa-user' }],
    ['/admin/accounts/locked', 'Đã khóa', { icon: 'fa-lock' }],
  ],
  manager: [
    ['/manager', 'Tổng quan', { group: 'Việc cần làm', icon: 'fa-chart-pie' }],
    ['/manager/intake', 'Tiếp nhận', { icon: 'fa-inbox' }],
    ['/manager/approvals', 'Duyệt giá', { icon: 'fa-file-signature' }],
    ['/manager/incidents', 'Sự cố', { icon: 'fa-triangle-exclamation' }],
    ['/manager/progress', 'Tiến độ đơn', { group: 'Theo dõi', icon: 'fa-list-check' }],
    ['/manager/reports', 'Doanh thu', { group: 'Báo cáo', icon: 'fa-chart-line' }],
    ['/manager/trip-reports', 'Chuyến đi', { icon: 'fa-route' }],
  ],
  specialist: [
    ['/specialist/verification', 'Duyệt hồ sơ ngựa', { icon: 'fa-file-medical' }],
    ['/specialist/legal', 'Giấy tờ chuyến đi', { icon: 'fa-file-signature' }],
  ],
  coordinator: [
    ['/coordinator/fleet-plan', 'Xe và lộ trình', { icon: 'fa-route' }],
    ['/coordinator/dispatch', 'Giấy cho tài xế', { icon: 'fa-folder-open' }],
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
