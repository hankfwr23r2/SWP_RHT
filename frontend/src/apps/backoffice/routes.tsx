// Danh sách trang của app nội bộ: URL → trang → vai trò.
import type { AppRoute } from '@shared/routing/types'
import { SitemapPage } from '../sitemap/SitemapPage'
import { ManagerLoginPage, StaffLoginPage } from './features/auth/StaffLoginPage'
import AccountsPage from './features/admin/accounts/AccountsPage'
import IntakePage from './features/manager/intake/IntakePage'
import ApprovalsPage from './features/manager/approvals/ApprovalsPage'
import ProgressPage from './features/manager/progress/ProgressPage'
import DashboardPage from './features/manager/dashboard/DashboardPage'
import TripReportsPage from './features/manager/trip-reports/TripReportsPage'
import ReportsPage from './features/manager/reports/ReportsPage'
import IncidentsPage from './features/manager/incidents/IncidentsPage'
import VerificationListPage from './features/specialist/verification/VerificationListPage'
import VerifyPage from './features/specialist/verification/VerifyPage'
import LegalListPage from './features/specialist/legal/LegalListPage'
import LegalReviewPage from './features/specialist/legal/LegalReviewPage'
import FleetPlanListPage from './features/coordinator/fleet-plan/FleetPlanListPage'
import FleetPlanPage from './features/coordinator/fleet-plan/FleetPlanPage'
import DispatchListPage from './features/coordinator/dispatch/DispatchListPage'
import DispatchPage from './features/coordinator/dispatch/DispatchPage'
import MonitoringPage from './features/coordinator/monitoring/MonitoringPage'
import CoordinatorIncidentsPage from './features/coordinator/incidents/CoordinatorIncidentsPage'
import FleetPage from './features/coordinator/fleet/FleetPage'
import CrewDetailPage from './features/coordinator/crew/CrewDetailPage'
import DriverPage from './features/driver/DriverPage'
import EscortPage from './features/escort/EscortPage'

const A: AppRoute['roles'] = ['admin']
const M: AppRoute['roles'] = ['manager']
const SP: AppRoute['roles'] = ['specialist']
const CO: AppRoute['roles'] = ['coordinator']

export const routes: AppRoute[] = [
  { path: '/login', page: StaffLoginPage, roles: [], title: 'Đăng nhập nội bộ', layout: 'bare' },
  { path: '/manager/login', page: ManagerLoginPage, roles: [], title: 'Đăng nhập Quản lý', layout: 'bare' },

  { path: '/admin/accounts', page: AccountsPage, roles: A, title: 'Tài khoản hệ thống', layout: 'staff' },
  { path: '/admin/accounts/:role', page: AccountsPage, roles: A, title: 'Tài khoản theo loại', example: 'locked', layout: 'staff' },

  { path: '/manager', page: DashboardPage, roles: M, title: 'Bảng điều khiển', layout: 'staff' },
  { path: '/manager/intake', page: IntakePage, roles: M, title: 'Tiếp nhận đơn hàng', layout: 'staff' },
  { path: '/manager/approvals', page: ApprovalsPage, roles: M, title: 'Duyệt báo giá', layout: 'staff' },
  { path: '/manager/progress', page: ProgressPage, roles: M, title: 'Tiến độ đơn', layout: 'staff' },
  { path: '/manager/reports', page: ReportsPage, roles: M, title: 'Báo cáo doanh thu', layout: 'staff' },
  { path: '/manager/trip-reports', page: TripReportsPage, roles: M, title: 'Báo cáo Chuyến đi', layout: 'staff' },
  { path: '/manager/incidents', page: IncidentsPage, roles: M, title: 'Sự cố', layout: 'staff' },

  { path: '/specialist/verification', page: VerificationListPage, roles: SP, title: 'Duyệt hồ sơ ngựa', layout: 'staff' },
  { path: '/specialist/verification/:id', page: VerifyPage, roles: SP, title: 'Duyệt hồ sơ ngựa một đơn', example: 'ORD-2026-0102', layout: 'staff' },
  { path: '/specialist/legal', page: LegalListPage, roles: SP, title: 'Giấy tờ chuyến đi', layout: 'staff' },
  { path: '/specialist/legal/:id', page: LegalReviewPage, roles: SP, title: 'Làm giấy tờ một đơn', example: 'ORD-2026-0109', layout: 'staff' },

  { path: '/coordinator/fleet-plan', page: FleetPlanListPage, roles: CO, title: 'Xe và lộ trình', layout: 'staff' },
  { path: '/coordinator/fleet-plan/:id', page: FleetPlanPage, roles: CO, title: 'Chốt xe và lộ trình một đơn', example: 'ORD-2026-0102', layout: 'staff' },
  { path: '/coordinator/dispatch', page: DispatchListPage, roles: CO, title: 'Giấy cho tài xế', layout: 'staff' },
  { path: '/coordinator/dispatch/:id', page: DispatchPage, roles: CO, title: 'Nhập bộ giấy cho tài xế', example: 'ORD-2026-0111', layout: 'staff' },
  { path: '/coordinator/monitoring', page: MonitoringPage, roles: CO, title: 'Giám sát vận chuyển', layout: 'staff' },
  { path: '/coordinator/incidents', page: CoordinatorIncidentsPage, roles: CO, title: 'Xử lý sự cố', layout: 'staff' },
  { path: '/coordinator/fleet', page: FleetPage, roles: CO, title: 'Quản lý đội xe', layout: 'staff' },
  { path: '/coordinator/staff/:id', page: CrewDetailPage, roles: CO, title: 'Chi tiết nhân sự', example: 'TX-07', layout: 'staff' },

  { path: '/driver', page: DriverPage, roles: ['driver'], title: 'Chuyến của tôi', layout: 'mobile' },
  { path: '/escort', page: EscortPage, roles: ['escort'], title: 'Nhật ký sức khỏe ngựa', layout: 'mobile' },
]

routes.push({ path: '/sitemap', page: () => <SitemapPage app="backoffice" />, roles: [], title: 'Sitemap', layout: 'staff' })
