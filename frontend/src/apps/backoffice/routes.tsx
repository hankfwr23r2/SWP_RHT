// Danh sách trang của app nội bộ: URL → trang → vai trò.
import { lazy } from 'react'
import i18n, { i18nReady } from '@shared/i18n'
import type { PageLoader } from '@shared/routing/pageData'
import type { AppRoute } from '@shared/routing/types'
import { ManagerLoginPage, StaffLoginPage } from './features/auth/StaffLoginPage'
import type * as Loaders from './shared/loaders'
import type * as NetworkLoaders from './features/admin/network/loaders'
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

const loadNetwork = <K extends keyof typeof NetworkLoaders>(name: K): PageLoader => async ctx =>
  (await import('./features/admin/network/loaders'))[name](ctx)

const staffCatalogPage = (path: string, title: string, page: NonNullable<AppRoute['lazyPage']>, loader: PageLoader, roles = A): AppRoute => ({
  path, roles, layout: 'staff', loader,
  lazyPage: () => Promise.all([page(), i18nReady]).then(([component]) => component),
  get title() { return i18n.t('staffNetwork.' + title) },
})

export const routes: AppRoute[] = [
  { path: '/forgot-password', page: StaffForgotPasswordPage, roles: [], title: 'Khôi phục mật khẩu', layout: 'bare', catalog: false },
  { path: '/profile', page: StaffProfilePage, roles: ['admin', 'manager', 'specialist', 'coordinator', 'driver', 'escort'], title: 'Hồ sơ nhân viên', layout: 'staff' },
  { path: '/login', page: StaffLoginPage, roles: [], title: 'Đăng nhập nội bộ', layout: 'bare', catalog: false },
  { path: '/manager/login', page: ManagerLoginPage, roles: [], title: 'Đăng nhập quản lý', layout: 'bare', catalog: false },

  staffCatalogPage('/admin/pricing', 'pricing', () => import('./features/admin/pricing/AdminPricingPage'),
    () => import('./features/admin/pricing/loaders').then(m => m.pricingLoader()), ['admin', 'manager']),
  staffCatalogPage('/admin/pricing/:id', 'viewPriceList', () => import('./features/admin/pricing/PriceListDetailPage'),
    ctx => import('./features/admin/pricing/loaders').then(m => m.pricingDetailLoader(ctx)), ['admin', 'manager']),
  { path: '/admin/horse-types', lazyPage: () => import('./features/admin/horse-types/AdminHorseTypesPage'), loader: load('adminHorseTypesLoader'), roles: A, title: 'Danh mục giống ngựa', layout: 'staff' },
  { path: '/admin/fleet', page: AdminFleetPage, roles: A, title: 'Đội xe', layout: 'staff' },
  staffCatalogPage('/admin/network/locations', 'locations', () => import('./features/admin/network/LocationsPage'), loadNetwork('locationsLoader')),
  staffCatalogPage('/admin/network/areas', 'areas', () => import('./features/admin/network/AreasPage'), loadNetwork('areasLoader')),
  staffCatalogPage('/admin/network/routes', 'routes', () => import('./features/admin/network/RoutesPage'), loadNetwork('routesLoader')),
  staffCatalogPage('/admin/network/routes/new', 'addRoute', () => import('./features/admin/network/RouteEditorPage'), loadNetwork('routeEditorLoader')),
  staffCatalogPage('/admin/network/routes/:id', 'viewRoute', () => import('./features/admin/network/RouteEditorPage'), loadNetwork('routeEditorLoader')),
  staffCatalogPage('/admin/network/routes/:id/edit', 'editRoute', () => import('./features/admin/network/RouteEditorPage'), loadNetwork('routeEditorLoader')),
  staffCatalogPage('/admin/network/schedules', 'schedules', () => import('./features/admin/network/SchedulesPage'), loadNetwork('schedulesLoader')),
  staffCatalogPage('/admin/network/departures', 'departures', () => import('./features/admin/network/DeparturesPage'), loadNetwork('departuresLoader')),
  { path: '/admin/rules', lazyPage: () => import('./features/admin/rules/AdminRulesPage'), loader: load('systemConfigLoader'), roles: A, title: 'Quy định & Tham số', layout: 'staff' },
  { path: '/admin/document-requirements', lazyPage: () => import('./features/admin/documents/AdminDocumentRequirementsPage'), loader: load('documentRequirementsLoader'), roles: A, title: 'Giấy tờ bắt buộc', layout: 'staff' },
  { path: '/admin/system-logs', page: SystemLogsPage, roles: A, title: 'Nhật ký hệ thống', layout: 'staff', catalog: false },
  { path: '/admin/ui-library', lazyPage: () => import('./features/admin/ui-library/UiLibraryPage'), roles: A, title: 'Thư viện giao diện', layout: 'staff', catalog: false },
  { path: '/admin/accounts', lazyPage: () => import('./features/admin/accounts/AccountsPage'), loader: load('accountsLoader'), roles: A, title: 'Tất cả tài khoản', layout: 'staff' },
  { path: '/admin/accounts/:role', lazyPage: () => import('./features/admin/accounts/AccountsPage'), loader: load('accountsLoader'), roles: A, title: 'Tài khoản theo loại', layout: 'staff' },

  { path: '/manager', lazyPage: () => import('./features/manager/dashboard/DashboardPage'), loader: load('bookingListLoader'), roles: M, title: 'Bảng điều khiển', layout: 'staff' },
  { path: '/manager/intake', lazyPage: () => import('./features/manager/intake/IntakePage'), loader: load('bookingListLoader'), roles: M, title: 'Tiếp nhận đơn hàng', layout: 'staff' },
  { path: '/manager/approvals', lazyPage: () => import('./features/manager/approvals/ApprovalsPage'), loader: load('bookingListLoader'), roles: M, title: 'Duyệt báo giá', layout: 'staff' },
  { path: '/manager/reports', lazyPage: () => import('./features/manager/reports/ReportsPage'), loader: load('bookingListLoader'), roles: M, title: 'Báo cáo doanh thu', layout: 'staff' },
  { path: '/manager/trip-reports', lazyPage: () => import('./features/manager/trip-reports/TripReportsPage'), roles: M, title: 'Báo cáo vận hành', layout: 'staff', catalog: false },
  { path: '/manager/cancelled-orders', lazyPage: () => import('./features/manager/cancelled-orders/CancelledOrdersPage'), loader: load('bookingListLoader'), roles: M, title: 'Đơn đã hủy', layout: 'staff' },
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
