// Danh sách trang của app khách: URL → trang → vai trò. Rỗng roles = công khai.
import { lazy } from 'react'
import type { AppRoute } from '@shared/routing/types'
import i18n, { i18nReady } from '@shared/i18n'
import LoginPage from './features/auth/LoginPage'
import HomePage from './features/home/HomePage'
import { horseProfilesLoader } from './features/horses/loaders'
import { orderDetailLoader, ordersLoader } from './features/orders/loaders'
import { portalLoader } from './features/portal/portalLoader'

// Trang chủ và đăng nhập tải ngay; các trang còn lại chỉ tải khi mở tới (lazy), để lần mở web đầu tiên
// không phải tải cả thư viện bản đồ (~1,7 MB) mà trang chủ không dùng.
// Trang có loader khai báo lazyPage: router tải file trang song song với dữ liệu rồi mới vẽ trang.
const SitemapPage = lazy(() => import('../sitemap/SitemapPage').then(m => ({ default: m.SitemapPage })))
const CustomerForgotPasswordPage = lazy(() => import('@shared/auth/ForgotPasswordPage').then(m => ({ default: m.CustomerForgotPasswordPage })))
const RegisterPage = lazy(() => import('./features/auth/RegisterPage'))
const VerifyEmailPage = lazy(() => import('./features/auth/VerifyEmailPage'))
const PolicyPage = lazy(() => import('./features/policies/PolicyPage'))
const VnpayReturnPage = lazy(() => import('./features/payment/VnpayReturnPage'))
const ProfilePage = lazy(() => import('./features/profile/ProfilePage'))

const C = ['customer'] as AppRoute['roles']

// Bốn bước đặt đơn dùng chung dữ liệu; chờ bộ chữ để tiêu đề trang đúng ngôn ngữ
const bookingStep = (step: 'route' | 'horses' | 'services' | 'review', load: NonNullable<AppRoute['lazyPage']>): AppRoute => ({
  path: `/booking/${step}`, roles: C, layout: 'customer',
  loader: () => import('./features/booking/loaders').then(m => m.bookingLoader()),
  lazyPage: () => Promise.all([load(), i18nReady]).then(([page]) => page),
  get title() { return i18n.t(`bookingForm.steps.${step}`) },
})

export const routes: AppRoute[] = [
  { path: '/', page: HomePage, roles: [], title: 'Trang chủ', layout: 'public', catalog: false },
  { path: '/login', page: LoginPage, roles: [], title: 'Đăng nhập', layout: 'bare', catalog: false },
  { path: '/forgot-password', page: CustomerForgotPasswordPage, roles: [], title: 'Quên mật khẩu', layout: 'bare', catalog: false },
  { path: '/register', page: RegisterPage, roles: [], title: 'Đăng ký', layout: 'bare', catalog: false },
  { path: '/verify-email', page: VerifyEmailPage, roles: [], title: 'Xác thực email', layout: 'bare', catalog: false },
  { path: '/portal', lazyPage: () => import('./features/portal/PortalPage'), loader: portalLoader, roles: C, title: 'Trang chủ', layout: 'customer' },
  { path: '/profile', page: ProfilePage, roles: C, title: 'Hồ sơ cá nhân', layout: 'customer' },
  {
    path: '/horses', roles: C, layout: 'customer', loader: horseProfilesLoader,
    lazyPage: () => Promise.all([import('./features/horses/HorsesPage'), i18nReady]).then(([page]) => page),
    get title() { return i18n.t('horses.title') },
  },
  {
    path: '/addresses', roles: C, layout: 'customer',
    loader: () => import('./features/addresses/loaders').then(m => m.addressesLoader()),
    lazyPage: () => Promise.all([import('./features/addresses/AddressBookPage'), i18nReady]).then(([page]) => page),
    get title() { return i18n.t('addresses.title') },
  },
  bookingStep('route', () => import('./features/booking/Step1RoutePage')),
  bookingStep('horses', () => import('./features/booking/Step2HorsesPage')),
  bookingStep('services', () => import('./features/booking/Step3ServicesPage')),
  bookingStep('review', () => import('./features/booking/Step4ReviewPage')),
  { path: '/orders', lazyPage: () => import('./features/orders/OrdersPage'), loader: ordersLoader, roles: C, title: 'Đơn của tôi', layout: 'customer' },
  { path: '/history', lazyPage: () => import('./features/orders/HistoryPage'), loader: ordersLoader, roles: C, title: 'Lịch sử đơn', layout: 'customer' },
  { path: '/terms', page: PolicyPage, roles: [], title: 'Điều khoản và chính sách', layout: 'public', catalog: false },
  { path: '/orders/:id', lazyPage: () => import('./features/orders/OrderDetailPage'), loader: orderDetailLoader, roles: C, title: 'Chi tiết đơn', layout: 'customer' },
  { path: '/payment/vnpay-return', page: VnpayReturnPage, roles: [], title: 'Kết quả thanh toán', layout: 'public' },
]

routes.push({ path: '/sitemap', page: () => <SitemapPage app="customer" />, roles: [], title: 'Sitemap', layout: 'public', catalog: false })
