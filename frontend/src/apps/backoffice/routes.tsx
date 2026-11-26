// Danh sách trang của app nội bộ: URL → trang → vai trò.
import { lazy } from 'react'
import i18n, { i18nReady } from '@shared/i18n'
import type { PageLoader } from '@shared/routing/pageData'
import type { AppRoute } from '@shared/routing/types'
import { dispatchTexts, managerTexts, reviewTexts } from '@shared/i18n/staffTexts'
import { ManagerLoginPage, StaffLoginPage } from './features/auth/StaffLoginPage'
import type * as NetworkLoaders from './features/admin/network/loaders'
// Trang đăng nhập tải ngay; các trang còn lại chỉ tải khi mở tới (lazy), mỗi vai trò chỉ tải phần mình dùng.
// Trang có loader khai báo lazyPage: router tải file trang song song với dữ liệu rồi mới vẽ trang.
const SitemapPage = lazy(() => import('../sitemap/SitemapPage').then(m => ({ default: m.SitemapPage })))
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
  staffCatalogPage('/admin/network/locations', 'locations', () => import('./features/admin/network/LocationsPage'), loadNetwork('locationsLoader')),
  staffCatalogPage('/admin/network/areas', 'areas', () => import('./features/admin/network/AreasPage'), loadNetwork('areasLoader')),
  staffCatalogPage('/admin/network/routes', 'routes', () => import('./features/admin/network/RoutesPage'), loadNetwork('routesLoader')),
  staffCatalogPage('/admin/network/routes/new', 'addRoute', () => import('./features/admin/network/RouteEditorPage'), loadNetwork('routeEditorLoader')),
  staffCatalogPage('/admin/network/routes/:id', 'viewRoute', () => import('./features/admin/network/RouteEditorPage'), loadNetwork('routeEditorLoader')),
  staffCatalogPage('/admin/network/routes/:id/edit', 'editRoute', () => import('./features/admin/network/RouteEditorPage'), loadNetwork('routeEditorLoader')),
  staffCatalogPage('/admin/network/schedules', 'schedules', () => import('./features/admin/network/SchedulesPage'), loadNetwork('schedulesLoader')),
  staffCatalogPage('/admin/network/departures', 'departures', () => import('./features/admin/network/DeparturesPage'), loadNetwork('departuresLoader')),
  { path: '/admin/horse-types', lazyPage: () => import('./features/admin/horse-types/AdminHorseTypesPage'), loader: () => import('./features/admin/horse-types/loaders').then(m => m.loader()), roles: A, get title() { return i18n.t('staffCatalog.horseTypes.title') }, layout: 'staff' },
  { path: '/admin/vehicles', lazyPage: () => import('./features/admin/vehicles/VehiclesPage'), loader: () => import('./features/admin/vehicles/loaders').then(m => m.loader()), roles: A, get title() { return i18n.t('staffCatalog.vehicles.title') }, layout: 'staff' },
  { path: '/admin/settings', lazyPage: () => import('./features/admin/settings/SettingsPage'), loader: () => import('./features/admin/settings/loaders').then(m => m.loader()), roles: A, get title() { return i18n.t('staffCatalog.settings.title') }, layout: 'staff' },
  { path: '/admin/regulations', lazyPage: () => import('./features/admin/regulations/RegulationsPage'), loader: () => import('./features/admin/regulations/loaders').then(m => m.loader()), roles: A, get title() { return i18n.t('staffCatalog.regulations.title') }, layout: 'staff' },
  { path: '/admin/system-logs', lazyPage: () => import('./features/admin/system-logs/SystemLogsPage'), loader: () => import('./features/admin/system-logs/loaders').then(m => m.loader()), roles: A, get title() { return i18n.t('systemLogs.title') }, layout: 'staff' },
  { path: '/admin/ui-library', lazyPage: () => import('./features/admin/ui-library/UiLibraryPage'), roles: A, title: 'Thư viện giao diện', layout: 'staff', catalog: false },
  { path: '/admin/accounts', lazyPage: () => import('./features/admin/accounts/AccountsPage'), loader: () => import('./features/admin/accounts/loaders').then(m => m.loader()), roles: A, get title() { return i18n.t('staffCatalog.accounts.title') }, layout: 'staff' },
  { path: '/admin/parties', lazyPage: () => import('./features/admin/parties/PartiesPage'), loader: () => import('./features/admin/parties/loaders').then(m => m.loader()), roles: ['admin', 'coordinator'], get title() { return i18n.t('staffCatalog.parties.title') }, layout: 'staff' },

  { path: '/manager', lazyPage: () => import('./features/manager/overview/OverviewPage'), roles: M, title: 'Tổng quan', layout: 'staff' },
  {
    path: '/manager/hubs', roles: M, layout: 'staff', catalog: false,
    lazyPage: () => Promise.all([import('./features/manager/hubs/ManagerHubsPage'), managerTexts(), dispatchTexts()]).then(([page]) => page),
    get title() { return i18n.t('staffMenus./manager/hubs') },
  },
  {
    path: '/manager/assignments', roles: ['manager'], layout: 'staff', catalog: false,
    lazyPage: () => Promise.all([import('./features/manager/assignments/AssignmentsPage'), managerTexts()]).then(([page]) => page),
    get title() { return i18n.t('mgr.assignments.title') },
  },
  {
    path: '/manager/orders', roles: M, layout: 'staff', catalog: false,
    lazyPage: () => Promise.all([import('./features/manager/orders/ManagerOrdersPage'), managerTexts()]).then(([page]) => page), title: 'Đơn hàng',
  },
  {
    path: '/manager/orders/:id', roles: M, layout: 'staff', catalog: false,
    loader: context => import('./features/manager/orders/loaders').then(m => m.orderLoader(context)),
    lazyPage: () => Promise.all([import('./features/manager/orders/ManagerOrderPage'), managerTexts()]).then(([page]) => page), title: 'Chi tiết đơn',
  },
  {
    path: '/manager/departures', roles: M, layout: 'staff', catalog: false,
    loader: () => import('./features/manager/departures/loaders').then(m => m.departuresLoader()),
    lazyPage: () => Promise.all([import('./features/manager/departures/DeparturesPage'), managerTexts()]).then(([page]) => page), title: 'Chuyến cần quyết định',
  },
  {
    path: '/manager/refunds', roles: M, layout: 'staff', catalog: false,
    lazyPage: () => Promise.all([import('./features/manager/refunds/RefundsPage'), managerTexts()]).then(([page]) => page), title: 'Hoàn tiền',
  },
  { path: '/manager/reports', lazyPage: () => import('./features/manager/reports/ReportsPage'), roles: M, title: 'Báo cáo doanh thu', layout: 'staff' },
  { path: '/manager/trip-reports', lazyPage: () => import('./features/manager/trip-reports/TripReportsPage'), roles: M, title: 'Báo cáo vận hành', layout: 'staff', catalog: false },
  { path: '/manager/incidents', lazyPage: () => import('./features/incidents/IncidentsBoardPage'), roles: M, title: 'Sự cố', layout: 'staff', catalog: false },

  {
    path: '/specialist/review', roles: SP, layout: 'staff',
    lazyPage: () => Promise.all([import('./features/specialist/review/ReviewListPage'), reviewTexts()]).then(([page]) => page),
    loader: () => import('./features/specialist/review/loaders').then(m => m.reviewListLoader()),
    title: 'Thẩm định đơn',
  },
  {
    path: '/specialist/review/:id', roles: SP, layout: 'staff',
    lazyPage: () => Promise.all([import('./features/specialist/review/ReviewDetailPage'), reviewTexts()]).then(([page]) => page),
    loader: context => import('./features/specialist/review/loaders').then(m => m.reviewDetailLoader(context)),
    title: 'Chi tiết thẩm định',
  },
  {
    path: '/specialist/missing-documents', roles: SP, layout: 'staff',
    lazyPage: () => Promise.all([import('./features/specialist/review/MissingDocumentsPage'), reviewTexts()]).then(([page]) => page),
    loader: () => import('./features/specialist/review/loaders').then(m => m.missingDocumentsLoader()),
    title: 'Đơn thiếu giấy',
  },

  { path: '/coordinator/dispatch', lazyPage: () => Promise.all([import('./features/coordinator/dispatch/DispatchPage'), dispatchTexts()]).then(([page]) => page), roles: CO, title: 'Điều độ', layout: 'staff', catalog: false },
  { path: '/coordinator/my-trips', lazyPage: () => import('./features/coordinator/my-trips/CoordinatorMyTripsPage'), roles: CO, title: 'Lượt của tôi', layout: 'staff', catalog: false },
  { path: '/coordinator/plans', lazyPage: () => Promise.all([import('./features/coordinator/plans/PlansPage'), dispatchTexts()]).then(([page]) => page), roles: CO, title: 'Kế hoạch đơn', layout: 'staff', catalog: false },
  { path: '/coordinator/tracking', lazyPage: () => Promise.all([import('./features/coordinator/tracking/TrackingPage'), dispatchTexts()]).then(([page]) => page), roles: CO, title: 'Theo dõi', layout: 'staff', catalog: false },
  { path: '/coordinator/hub', lazyPage: () => Promise.all([import('./features/coordinator/hub/HubPage'), dispatchTexts()]).then(([page]) => page), roles: CO, title: 'Hub', layout: 'staff', catalog: false },
  { path: '/coordinator/incidents', lazyPage: () => import('./features/incidents/IncidentsBoardPage'), roles: CO, title: 'Xử lý sự cố', layout: 'staff', catalog: false },

  { path: '/my-trips/:id', lazyPage: () => import('./features/crew/TripRunPage'), roles: ['driver', 'escort'], title: 'Thực hiện lượt xe', layout: 'mobile', catalog: false },
  { path: '/my-trips', lazyPage: () => import('./features/crew/MyTripsPage'), roles: ['driver', 'escort'], title: 'Lượt xe của tôi', layout: 'mobile', catalog: false },
]

routes.push({ path: '/sitemap', page: () => <SitemapPage app="backoffice" />, roles: [], title: 'Sitemap', layout: 'staff', catalog: false })
