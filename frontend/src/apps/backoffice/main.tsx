import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router'
import '@shared/styles/base.css'
import { AuthProvider } from '@shared/auth/AuthContext'
import { loadBootstrap } from '@shared/config/bootstrap'
import { buildRouter } from '@shared/routing/buildRouter'
import { ToastProvider } from '@shared/ui/toast'
import { MobileStaffLayout, StaffLayout } from './layouts/StaffLayout'
import { routes } from './routes'

const router = buildRouter(routes, { staff: StaffLayout, mobile: MobileStaffLayout }, { app: 'backoffice', basename: '/backoffice', loginPath: '/login' })

// Tải dữ liệu dùng chung ngay khi mở app, không chờ; trang nào cần thì router chờ qua loader
loadBootstrap().catch(() => {})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider app="backoffice">
      <ToastProvider>
        <RouterProvider router={router} />
      </ToastProvider>
    </AuthProvider>
  </StrictMode>,
)
