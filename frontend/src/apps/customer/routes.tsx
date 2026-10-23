// Danh sách trang của app khách: URL → trang → vai trò. Rỗng roles = công khai.
import { lazy } from 'react'
import type { AppRoute } from '@shared/routing/types'
import LoginPage from './features/auth/LoginPage'
import HomePage from './features/home/HomePage'

// Trang chủ và đăng nhập tải ngay; các trang còn lại chỉ tải khi mở tới (lazy), để lần mở web đầu tiên
// không phải tải cả thư viện bản đồ (~1,7 MB) mà trang chủ không dùng.
const SitemapPage = lazy(() => import('../sitemap/SitemapPage').then(m => ({ default: m.SitemapPage })))
const CustomerForgotPasswordPage = lazy(() => import('@shared/auth/ForgotPasswordPage').then(m => ({ default: m.CustomerForgotPasswordPage })))
const RegisterPage = lazy(() => import('./features/auth/RegisterPage'))
const PortalPage = lazy(() => import('./features/portal/PortalPage'))
const HorsesPage = lazy(() => import('./features/horses/HorsesPage'))
const Step1RoutePage = lazy(() => import('./features/booking/Step1RoutePage'))
const Step2HorsesPage = lazy(() => import('./features/booking/Step2HorsesPage'))
const Step3ServicesPage = lazy(() => import('./features/booking/Step3ServicesPage'))
const Step4ReviewPage = lazy(() => import('./features/booking/Step4ReviewPage'))
const OrdersPage = lazy(() => import('./features/orders/OrdersPage'))
const OrderDetailPage = lazy(() => import('./features/orders/OrderDetailPage'))
const HistoryPage = lazy(() => import('./features/orders/HistoryPage'))
const PolicyPage = lazy(() => import('./features/policies/PolicyPage'))
const VnpayReturnPage = lazy(() => import('./features/payment/VnpayReturnPage'))
const ProfilePage = lazy(() => import('./features/profile/ProfilePage'))

const C = ['customer'] as AppRoute['roles']

export const routes: AppRoute[] = [
  { path: '/', page: HomePage, roles: [], title: 'Trang chủ', layout: 'public' },
  { path: '/login', page: LoginPage, roles: [], title: 'Đăng nhập', layout: 'bare' },
  { path: '/forgot-password', page: CustomerForgotPasswordPage, roles: [], title: 'Quên mật khẩu', layout: 'bare' },
  { path: '/register', page: RegisterPage, roles: [], title: 'Đăng ký', layout: 'bare' },
  { path: '/portal', page: PortalPage, roles: C, title: 'Trang chủ', layout: 'customer' },
  { path: '/profile', page: ProfilePage, roles: C, title: 'Hồ sơ cá nhân', layout: 'customer' },
  { path: '/horses', page: HorsesPage, roles: C, title: 'Hồ sơ ngựa', layout: 'customer' },
  { path: '/booking/route', page: Step1RoutePage, roles: C, title: 'Đặt chuyến · Chuyến đi', layout: 'customer' },
  { path: '/booking/horses', page: Step2HorsesPage, roles: C, title: 'Đặt chuyến · Chọn ngựa', layout: 'customer' },
  { path: '/booking/services', page: Step3ServicesPage, roles: C, title: 'Đặt chuyến · Dịch vụ & bảo hiểm', layout: 'customer' },
  { path: '/booking/review', page: Step4ReviewPage, roles: C, title: 'Đặt chuyến · Xác nhận & gửi', layout: 'customer' },
  { path: '/orders', page: OrdersPage, roles: C, title: 'Đơn của tôi', layout: 'customer' },
  { path: '/history', page: HistoryPage, roles: C, title: 'Lịch sử đơn', layout: 'customer' },
  { path: '/terms', page: PolicyPage, roles: [], title: 'Điều khoản và chính sách', layout: 'public' },
  { path: '/orders/:id', page: OrderDetailPage, roles: C, title: 'Chi tiết đơn', layout: 'customer' },
  { path: '/payment/vnpay-return', page: VnpayReturnPage, roles: [], title: 'Kết quả thanh toán', layout: 'public' },
]

routes.push({ path: '/sitemap', page: () => <SitemapPage app="customer" />, roles: [], title: 'Sitemap', layout: 'public' })
