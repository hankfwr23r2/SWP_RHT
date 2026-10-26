import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router'
import '@shared/styles/base.css'
import { AuthProvider } from '@shared/auth/AuthContext'
import { loadBootstrap } from '@shared/config/bootstrap'
import { buildRouter } from '@shared/routing/buildRouter'
import { ToastProvider } from '@shared/ui/toast'
import { CustomerLayout } from './layouts/CustomerLayout'
import { PublicLayout } from './layouts/SiteChrome'
import { routes } from './routes'

const router = buildRouter(routes, { public: PublicLayout, customer: CustomerLayout }, { app: 'customer', loginPath: '/login' })

// Tải dữ liệu dùng chung ngay khi mở app, không chờ; trang nào cần thì router chờ qua loader
loadBootstrap().catch(() => {})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider app="customer">
      <ToastProvider>
        <RouterProvider router={router} />
      </ToastProvider>
    </AuthProvider>
  </StrictMode>,
)
