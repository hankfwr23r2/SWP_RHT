import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router'
import 'virtual:icons-customer.css'
import '@fontsource-variable/roboto/wght.css'
import '@shared/styles/base.css'
import { i18nReady } from '@shared/i18n'
import { AuthProvider } from '@shared/auth/AuthContext'
import { CustomerLanguageSync } from '@shared/auth/CustomerLanguageSync'
import { buildRouter } from '@shared/routing/buildRouter'
import { ToastProvider } from '@shared/ui/toast'
import { CustomerLayout } from './layouts/CustomerLayout'
import { PublicLayout } from './layouts/SiteChrome'
import { routes } from './routes'

const router = buildRouter(routes, { public: PublicLayout, customer: CustomerLayout }, { app: 'customer', loginPath: '/login' })

await i18nReady

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider app="customer">
      <ToastProvider>
        <CustomerLanguageSync />
        <RouterProvider router={router} />
      </ToastProvider>
    </AuthProvider>
  </StrictMode>,
)
