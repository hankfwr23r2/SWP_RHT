// Danh sách trang của app khách: URL → trang → vai trò. Rỗng roles = công khai.
import { lazy } from 'react'
import type { AppRoute } from '@shared/routing/types'
import LoginPage from './features/auth/LoginPage'
import HomePage from './features/home/HomePage'
import { horseListLoader, horsesLoader } from './features/horses/loaders'
import { orderDetailLoader, ordersLoader } from './features/orders/loaders'
import { portalLoader } from './features/portal/portalLoader'

// Trang chủ và đăng nhập tải ngay; các trang còn lại chỉ tải khi mở tới (lazy), để lần mở web đầu tiên
// không phải tải cả thư viện bản đồ (~1,7 MB) mà trang chủ không dùng.
// Trang có loader khai báo lazyPage: router tải file trang song song với dữ liệu rồi mới vẽ trang.
const SitemapPage = lazy(() => import('../sitemap/SitemapPage').then(m => ({ default: m.SitemapPage })))
const CustomerForgotPasswordPage = lazy(() => import('@shared/auth/ForgotPasswordPage').then(m => ({ default: m.CustomerForgotPasswordPage })))
const RegisterPage = lazy(() => import('./features/auth/RegisterPage'))
const Step1RoutePage = lazy(() => import('./features/booking/Step1RoutePage'))
const PolicyPage = lazy(() => import('./features/policies/PolicyPage'))
const VnpayReturnPage = lazy(() => import('./features/payment/VnpayReturnPage'))
const ProfilePage = lazy(() => import('./features/profile/ProfilePage'))

const C = ['customer'] as AppRoute['roles']

export const routes: AppRoute[] = [
  { path: '/', page: HomePage, roles: [], title: 'Trang chủ', layout: 'public' },
  { path: '/login', page: LoginPage, roles: [], title: 'Đăng nhập', layout: 'bare' },
  { path: '/forgot-password', page: CustomerForgotPasswordPage, roles: [], title: 'Quên mật khẩu', layout: 'bare' },
  { path: '/register', page: RegisterPage, roles: [], title: 'Đăng ký', layout: 'bare' },
  { path: '/portal', lazyPage: () => import('./features/portal/PortalPage'), loader: portalLoader, roles: C, title: 'Trang chủ', layout: 'customer' },
  { path: '/profile', page: ProfilePage, roles: C, title: 'Hồ sơ cá nhân', layout: 'customer' },
  { path: '/horses', lazyPage: () => import('./features/horses/HorsesPage'), loader: horsesLoader, roles: C, title: 'Hồ sơ ngựa', layout: 'customer' },
  { path: '/booking/route', page: Step1RoutePage, roles: C, title: 'Đặt chuyến · Chuyến đi', layout: 'customer' },
  { path: '/booking/horses', lazyPage: () => import('./features/booking/Step2HorsesPage'), loader: horsesLoader, roles: C, title: 'Đặt chuyến · Chọn ngựa', layout: 'customer' },
  { path: '/booking/services', lazyPage: () => import('./features/booking/Step3ServicesPage'), loader: horseListLoader, roles: C, title: 'Đặt chuyến · Dịch vụ & bảo hiểm', layout: 'customer' },
  { path: '/booking/review', lazyPage: () => import('./features/booking/Step4ReviewPage'), loader: horseListLoader, roles: C, title: 'Đặt chuyến · Xác nhận & gửi', layout: 'customer' },
  { path: '/orders', lazyPage: () => import('./features/orders/OrdersPage'), loader: ordersLoader, roles: C, title: 'Đơn của tôi', layout: 'customer' },
  { path: '/history', lazyPage: () => import('./features/orders/HistoryPage'), loader: ordersLoader, roles: C, title: 'Lịch sử đơn', layout: 'customer' },
  { path: '/terms', page: PolicyPage, roles: [], title: 'Điều khoản và chính sách', layout: 'public' },
  { path: '/orders/:id', lazyPage: () => import('./features/orders/OrderDetailPage'), loader: orderDetailLoader, roles: C, title: 'Chi tiết đơn', layout: 'customer' },
  { path: '/payment/vnpay-return', page: VnpayReturnPage, roles: [], title: 'Kết quả thanh toán', layout: 'public' },
]

routes.push({ path: '/sitemap', page: () => <SitemapPage app="customer" />, roles: [], title: 'Sitemap', layout: 'public' })
