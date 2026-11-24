// Danh sách trang của app nội bộ: URL → trang → vai trò.
import { lazy } from 'react'
import type { PageLoader } from '@shared/routing/pageData'
import type { AppRoute } from '@shared/routing/types'
import { ManagerLoginPage, StaffLoginPage } from './features/auth/StaffLoginPage'
import type * as Loaders from './shared/loaders'
// Loader nằm ngoài gói tải đầu: chỉ tải khi mở trang có dữ liệu
const load = <K extends keyof typeof Loaders>(name: K): PageLoader => async ctx =>
  (await import('./shared/loaders'))[name](ctx)

// Trang đăng nhập tải ngay; các trang còn lại chỉ tải khi mở tới (lazy), mỗi vai trò chỉ tải phần mình dùng.
// Trang có loader khai báo lazyPage: router tải file trang song song với dữ liệu rồi mới vẽ trang.
const SitemapPage = lazy(() => import('../sitemap/SitemapPage').then(m => ({ default: m.SitemapPage })))
const SystemLogsPage = lazy(() => import('./features/admin/system-logs/SystemLogsPage'))
const FleetPage = lazy(() => import('./features/coordinator/fleet/FleetPage'))
const AdminFleetPage = lazy(() => import('./features/admin/fleet/AdminFleetPage'))
const DriverPage = lazy(() => import('./features/driver/DriverPage'))
const EscortPage = lazy(() => import('./features/escort/EscortPage'))
const StaffProfilePage = lazy(() => import('./features/profile/StaffProfilePage'))
const StaffForgotPasswordPage = lazy(() => import('@shared/auth/ForgotPasswordPage').then(m => ({ default: m.StaffForgotPasswordPage })))

const A: AppRoute['roles'] = ['admin']
const M: AppRoute['roles'] = ['manager']
const SP: AppRoute['roles'] = ['specialist']
const CO: AppRoute['roles'] = ['coordinator']

export const routes: AppRoute[] = [
  { path: '/forgot-password', page: StaffForgotPasswordPage, roles: [], title: 'Khôi phục mật khẩu', layout: 'bare', catalog: false },
  { path: '/profile', page: StaffProfilePage, roles: ['admin', 'manager', 'specialist', 'coordinator', 'driver', 'escort'], title: 'Hồ sơ nhân viên', layout: 'staff' },
  { path: '/login', page: StaffLoginPage, roles: [], title: 'Đăng nhập nội bộ', layout: 'bare', catalog: false },
  { path: '/manager/login', page: ManagerLoginPage, roles: [], title: 'Đăng nhập quản lý', layout: 'bare', catalog: false },

  { path: '/admin/pricing', lazyPage: () => import('./features/admin/pricing/AdminPricingPage'), loader: load('adminPricingLoader'), roles: A, title: 'Biểu giá & Công thức', layout: 'staff' },
  { path: '/admin/horse-types', lazyPage: () => import('./features/admin/horse-types/AdminHorseTypesPage'), loader: load('adminHorseTypesLoader'), roles: A, title: 'Danh mục giống ngựa', layout: 'staff' },
  { path: '/admin/fleet', page: AdminFleetPage, roles: A, title: 'Đội xe', layout: 'staff' },
  { path: '/admin/routes', lazyPage: () => import('./features/admin/routes/AdminRoutesPage'), loader: load('adminRoutesLoader'), roles: A, title: 'Tuyến đường cố định', layout: 'staff' },
  { path: '/admin/services', lazyPage: () => import('./features/admin/services/AdminServicesPage'), loader: load('adminServicesLoader'), roles: A, title: 'Dịch vụ & Biểu phí', layout: 'staff' },
  { path: '/admin/rules', lazyPage: () => import('./features/admin/rules/AdminRulesPage'), loader: load('systemConfigLoader'), roles: A, title: 'Quy định & Tham số', layout: 'staff' },
  { path: '/admin/document-requirements', lazyPage: () => import('./features/admin/documents/AdminDocumentRequirementsPage'), loader: load('documentRequirementsLoader'), roles: A, title: 'Giấy tờ bắt buộc', layout: 'staff' },
  { path: '/admin/system-logs', page: SystemLogsPage, roles: A, title: 'Nhật ký hệ thống', layout: 'staff', catalog: false },
  { path: '/admin/ui-library', lazyPage: () => import('./features/admin/ui-library/UiLibraryPage'), roles: A, title: 'Thư viện giao diện', layout: 'staff', catalog: false },
  { path: '/admin/accounts', lazyPage: () => import('./features/admin/accounts/AccountsPage'), loader: load('accountsLoader'), roles: A, title: 'Tất cả tài khoản', layout: 'staff' },
  { path: '/admin/accounts/:role', lazyPage: () => import('./features/admin/accounts/AccountsPage'), loader: load('accountsLoader'), roles: A, title: 'Tài khoản theo loại', layout: 'staff' },

  { path: '/manager', lazyPage: () => import('./features/manager/dashboard/DashboardPage'), loader: load('bookingListLoader'), roles: M, title: 'Bảng điều khiển', layout: 'staff' },
  {
    path: '/manager/orders', roles: M, layout: 'staff', catalog: false,
    lazyPage: () => import('./features/manager/orders/ManagerOrdersPage'), title: 'Đơn hàng',
  },
  {
    path: '/manager/orders/:id', roles: M, layout: 'staff', catalog: false,
    loader: context => import('./features/manager/orders/loaders').then(m => m.orderLoader(context)),
    lazyPage: () => import('./features/manager/orders/ManagerOrderPage'), title: 'Chi tiết đơn',
  },
  {
    path: '/manager/departures', roles: M, layout: 'staff', catalog: false,
    loader: () => import('./features/manager/departures/loaders').then(m => m.departuresLoader()),
    lazyPage: () => import('./features/manager/departures/DeparturesPage'), title: 'Chuyến cần quyết định',
  },
  {
    path: '/manager/refunds', roles: M, layout: 'staff', catalog: false,
    lazyPage: () => import('./features/manager/refunds/RefundsPage'), title: 'Hoàn tiền',
  },
  { path: '/manager/reports', lazyPage: () => import('./features/manager/reports/ReportsPage'), loader: load('bookingListLoader'), roles: M, title: 'Báo cáo doanh thu', layout: 'staff' },
  { path: '/manager/trip-reports', lazyPage: () => import('./features/manager/trip-reports/TripReportsPage'), roles: M, title: 'Báo cáo vận hành', layout: 'staff', catalog: false },
  { path: '/manager/incidents', lazyPage: () => import('./features/incidents/IncidentsBoardPage'), roles: M, title: 'Sự cố', layout: 'staff', catalog: false },

  { path: '/specialist/verification', lazyPage: () => import('./features/specialist/verification/VerificationListPage'), loader: load('bookingListLoader'), roles: SP, title: 'Duyệt hồ sơ ngựa', layout: 'staff' },
  { path: '/specialist/verification/:id', lazyPage: () => import('./features/specialist/verification/VerifyPage'), loader: load('verifyLoader'), roles: SP, title: 'Duyệt hồ sơ ngựa một đơn', layout: 'staff' },
  { path: '/specialist/insurance', lazyPage: () => import('./features/specialist/insurance/InsuranceListPage'), loader: load('insuranceListLoader'), roles: SP, title: 'Bảo hiểm', layout: 'staff' },
  { path: '/specialist/insurance/:id', lazyPage: () => import('./features/specialist/insurance/InsuranceDetailPage'), loader: load('bookingLoader'), roles: SP, title: 'Bảo hiểm theo đơn', layout: 'staff' },
  { path: '/specialist/legal', lazyPage: () => import('./features/specialist/legal/LegalListPage'), loader: load('bookingListLoader'), roles: SP, title: 'Giấy tờ chuyến đi', layout: 'staff' },
  { path: '/specialist/legal/:id', lazyPage: () => import('./features/specialist/legal/LegalReviewPage'), loader: load('bookingLoader'), roles: SP, title: 'Làm giấy tờ một đơn', layout: 'staff' },

  { path: '/coordinator/dispatch', lazyPage: () => import('./features/coordinator/dispatch/DispatchPage'), roles: CO, title: 'Điều độ', layout: 'staff', catalog: false },
  { path: '/coordinator/fleet-plan', lazyPage: () => import('./features/coordinator/fleet-plan/FleetPlanListPage'), loader: load('bookingListLoader'), roles: CO, title: 'Xe và lộ trình', layout: 'staff' },
  { path: '/coordinator/fleet-plan/:id', lazyPage: () => import('./features/coordinator/fleet-plan/FleetPlanPage'), loader: load('fleetPlanLoader'), roles: CO, title: 'Chốt xe và lộ trình một đơn', layout: 'staff' },
  { path: '/coordinator/monitoring', lazyPage: () => import('./features/coordinator/monitoring/MonitoringPage'), loader: load('monitoringLoader'), roles: CO, title: 'Giám sát vận chuyển', layout: 'staff' },
  { path: '/coordinator/incidents', lazyPage: () => import('./features/incidents/IncidentsBoardPage'), roles: CO, title: 'Xử lý sự cố', layout: 'staff', catalog: false },
  { path: '/coordinator/fleet', page: FleetPage, roles: CO, title: 'Đội xe', layout: 'staff' },

  { path: '/my-trips/:id', lazyPage: () => import('./features/crew/TripRunPage'), roles: ['driver', 'escort'], title: 'Thực hiện lượt xe', layout: 'mobile', catalog: false },
  { path: '/my-trips', lazyPage: () => import('./features/crew/MyTripsPage'), roles: ['driver', 'escort'], title: 'Lượt xe của tôi', layout: 'mobile', catalog: false },
  { path: '/driver', page: DriverPage, roles: ['driver'], title: 'Chuyến của tôi', layout: 'mobile' },
  { path: '/escort', page: EscortPage, roles: ['escort'], title: 'Nhật ký sức khỏe ngựa', layout: 'mobile' },
]

routes.push({ path: '/sitemap', page: () => <SitemapPage app="backoffice" />, roles: [], title: 'Sitemap', layout: 'staff', catalog: false })
