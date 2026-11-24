import type { StaffRole } from '@shared/types/role'

// Menu theo vai trò. Các mục của Flow 1 (tiếp nhận, duyệt báo giá, duyệt hồ sơ ngựa, phương án xe) theo quy trình mới.
export const STAFF_MENUS: Record<StaffRole, [path: string, label: string, opt?: { group?: string; icon?: string }][]> = {
  admin: [
    ['/admin/horse-types', 'Danh mục loại ngựa', { icon: 'fa-horse-head' }],
    ['/admin/vehicles', 'Đội xe', { icon: 'fa-truck' }],
    ['/admin/settings', 'Tham số hệ thống', { icon: 'fa-scale-balanced' }],
    ['/admin/regulations', 'Quy định và giấy tờ', { icon: 'fa-file-circle-check' }],
    ['/admin/accounts', 'Tài khoản nhân viên', { icon: 'fa-users-gear' }],
    ['/admin/parties', 'Đối tác', { icon: 'fa-address-book' }],
    ['/admin/network/locations', 'Địa điểm', { icon: 'fa-location-dot' }],
    ['/admin/network/areas', 'Vùng phục vụ', { icon: 'fa-diagram-project' }],
    ['/admin/network/routes', 'Tuyến', { icon: 'fa-road' }],
    ['/admin/network/schedules', 'Lịch chạy', { icon: 'fa-calendar-days' }],
    ['/admin/pricing', 'Bảng giá', { icon: 'fa-calculator' }],
    ['/admin/system-logs', 'Nhật ký hệ thống', { icon: 'fa-clock-rotate-left' }],
    ['/admin/ui-library', 'Thư viện giao diện', { icon: 'fa-swatchbook' }],
  ],
  manager: [
    ['/manager', 'Tổng quan', { group: 'Việc cần làm', icon: 'fa-chart-pie' }],
    ['/manager/orders', 'Đơn hàng', { icon: 'fa-inbox' }],
    ['/manager/departures', 'Chuyến cần quyết định', { icon: 'fa-truck' }],
    ['/manager/refunds', 'Hoàn tiền', { icon: 'fa-rotate-left' }],
    ['/manager/incidents', 'Sự cố', { icon: 'fa-triangle-exclamation' }],
    ['/manager/reports', 'Doanh thu', { group: 'Báo cáo', icon: 'fa-chart-line' }],
    ['/manager/trip-reports', 'Chuyến đi', { icon: 'fa-clipboard-check' }],
  ],
  specialist: [
    ['/specialist/review', 'Thẩm định đơn', { icon: 'fa-file-circle-check' }],
    ['/specialist/missing-documents', 'Đơn thiếu giấy', { icon: 'fa-file-medical' }],
  ],
  coordinator: [
    ['/coordinator/dispatch', 'Điều độ', { icon: 'fa-diagram-project' }],
    ['/coordinator/fleet-plan', 'Xe và lộ trình', { icon: 'fa-route' }],
    ['/coordinator/monitoring', 'Giám sát', { icon: 'fa-satellite-dish' }],
    ['/coordinator/incidents', 'Sự cố', { icon: 'fa-triangle-exclamation' }],
    ['/coordinator/fleet', 'Đội xe', { icon: 'fa-truck' }],
  ],
  driver: [['/driver', 'Chuyến của tôi'], ['/my-trips', 'Lượt xe của tôi']],
  escort: [['/escort', 'Nhật ký sức khỏe'], ['/my-trips', 'Lượt xe của tôi']],
}

export const HOME_OF: Record<StaffRole, string> = {
  admin: '/admin/accounts',
  manager: '/manager',
  specialist: '/specialist/review',
  coordinator: '/coordinator/fleet-plan',
  driver: '/driver',
  escort: '/escort',
}
