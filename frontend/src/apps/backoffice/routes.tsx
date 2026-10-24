// Danh sách trang của app nội bộ: URL → trang → vai trò.
import { lazy } from 'react'
import type { AppRoute } from '@shared/routing/types'
import { ManagerLoginPage, StaffLoginPage } from './features/auth/StaffLoginPage'
import { accountsLoader, adminRoutesLoader, adminServicesLoader, bookingListLoader, bookingLoader, fleetPlanLoader, monitoringLoader, tripReportsLoader, verifyLoader } from './shared/loaders'
// Trang đăng nhập tải ngay; các trang còn lại chỉ tải khi mở tới (lazy), mỗi vai trò chỉ tải phần mình dùng.
// Trang có loader khai báo lazyPage: router tải file trang song song với dữ liệu rồi mới vẽ trang.
const SitemapPage = lazy(() => import('../sitemap/SitemapPage').then(m => ({ default: m.SitemapPage })))
const FleetPage = lazy(() => import('./features/coordinator/fleet/FleetPage'))
const DriverPage = lazy(() => import('./features/driver/DriverPage'))
const EscortPage = lazy(() => import('./features/escort/EscortPage'))
const StaffProfilePage = lazy(() => import('./features/profile/StaffProfilePage'))
const StaffForgotPasswordPage = lazy(() => import('@shared/auth/ForgotPasswordPage').then(m => ({ default: m.StaffForgotPasswordPage })))

const A: AppRoute['roles'] = ['admin']
const M: AppRoute['roles'] = ['manager']
const SP: AppRoute['roles'] = ['specialist']
const CO: AppRoute['roles'] = ['coordinator']

export const routes: AppRoute[] = [
  { path: '/forgot-password', page: StaffForgotPasswordPage, roles: [], title: 'Khôi phục mật khẩu', layout: 'bare' },
  { path: '/profile', page: StaffProfilePage, roles: ['admin', 'manager', 'specialist', 'coordinator', 'driver', 'escort'], title: 'Hồ sơ nhân viên', layout: 'staff' },
  { path: '/login', page: StaffLoginPage, roles: [], title: 'Đăng nhập nội bộ', layout: 'bare' },
  { path: '/manager/login', page: ManagerLoginPage, roles: [], title: 'Đăng nhập Quản lý', layout: 'bare' },

  { path: '/admin/routes', lazyPage: () => import('./features/admin/routes/AdminRoutesPage'), loader: adminRoutesLoader, roles: A, title: 'Tuyến đường cố định', layout: 'staff' },
  { path: '/admin/services', lazyPage: () => import('./features/admin/services/AdminServicesPage'), loader: adminServicesLoader, roles: A, title: 'Dịch vụ & Biểu phí', layout: 'staff' },
  { path: '/admin/accounts', lazyPage: () => import('./features/admin/accounts/AccountsPage'), loader: accountsLoader, roles: A, title: 'Tất cả tài khoản', layout: 'staff' },
  { path: '/admin/accounts/:role', lazyPage: () => import('./features/admin/accounts/AccountsPage'), loader: accountsLoader, roles: A, title: 'Tài khoản theo loại', layout: 'staff' },

  { path: '/manager', lazyPage: () => import('./features/manager/dashboard/DashboardPage'), loader: bookingListLoader, roles: M, title: 'Bảng điều khiển', layout: 'staff' },
  { path: '/manager/intake', lazyPage: () => import('./features/manager/intake/IntakePage'), loader: bookingListLoader, roles: M, title: 'Tiếp nhận đơn hàng', layout: 'staff' },
  { path: '/manager/approvals', lazyPage: () => import('./features/manager/approvals/ApprovalsPage'), loader: bookingListLoader, roles: M, title: 'Duyệt báo giá', layout: 'staff' },
  { path: '/manager/reports', lazyPage: () => import('./features/manager/reports/ReportsPage'), loader: bookingListLoader, roles: M, title: 'Báo cáo doanh thu', layout: 'staff' },
  { path: '/manager/trip-reports', lazyPage: () => import('./features/manager/trip-reports/TripReportsPage'), loader: tripReportsLoader, roles: M, title: 'Báo cáo Chuyến đi', layout: 'staff' },
  { path: '/manager/cancelled-orders', lazyPage: () => import('./features/manager/cancelled-orders/CancelledOrdersPage'), loader: bookingListLoader, roles: M, title: 'Đơn đã hủy', layout: 'staff' },
  { path: '/manager/incidents', lazyPage: () => import('./features/manager/incidents/IncidentsPage'), loader: bookingListLoader, roles: M, title: 'Sự cố', layout: 'staff' },

  { path: '/specialist/verification', lazyPage: () => import('./features/specialist/verification/VerificationListPage'), loader: bookingListLoader, roles: SP, title: 'Duyệt hồ sơ ngựa', layout: 'staff' },
  { path: '/specialist/verification/:id', lazyPage: () => import('./features/specialist/verification/VerifyPage'), loader: verifyLoader, roles: SP, title: 'Duyệt hồ sơ ngựa một đơn', layout: 'staff' },
  { path: '/specialist/legal', lazyPage: () => import('./features/specialist/legal/LegalListPage'), loader: bookingListLoader, roles: SP, title: 'Giấy tờ chuyến đi', layout: 'staff' },
  { path: '/specialist/legal/:id', lazyPage: () => import('./features/specialist/legal/LegalReviewPage'), loader: bookingLoader, roles: SP, title: 'Làm giấy tờ một đơn', layout: 'staff' },

  { path: '/coordinator/fleet-plan', lazyPage: () => import('./features/coordinator/fleet-plan/FleetPlanListPage'), loader: bookingListLoader, roles: CO, title: 'Xe và lộ trình', layout: 'staff' },
  { path: '/coordinator/fleet-plan/:id', lazyPage: () => import('./features/coordinator/fleet-plan/FleetPlanPage'), loader: fleetPlanLoader, roles: CO, title: 'Chốt xe và lộ trình một đơn', layout: 'staff' },
  { path: '/coordinator/monitoring', lazyPage: () => import('./features/coordinator/monitoring/MonitoringPage'), loader: monitoringLoader, roles: CO, title: 'Giám sát vận chuyển', layout: 'staff' },
  { path: '/coordinator/incidents', lazyPage: () => import('./features/coordinator/incidents/CoordinatorIncidentsPage'), loader: bookingListLoader, roles: CO, title: 'Xử lý sự cố', layout: 'staff' },
  { path: '/coordinator/fleet', page: FleetPage, roles: CO, title: 'Quản lý đội xe', layout: 'staff' },

  { path: '/driver', page: DriverPage, roles: ['driver'], title: 'Chuyến của tôi', layout: 'mobile' },
  { path: '/escort', page: EscortPage, roles: ['escort'], title: 'Nhật ký sức khỏe ngựa', layout: 'mobile' },
]

routes.push({ path: '/sitemap', page: () => <SitemapPage app="backoffice" />, roles: [], title: 'Sitemap', layout: 'staff' })
