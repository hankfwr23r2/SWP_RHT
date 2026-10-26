import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router'
import '@shared/styles/base.css'
import { AuthProvider } from '@shared/auth/AuthContext'
import { BusinessConfigGate } from '@shared/config/BusinessConfigGate'
import { LocationCatalogGate } from '@shared/config/LocationCatalogGate'
import { DocumentRequirementsGate } from '@shared/config/DocumentRequirementsGate'
import { buildRouter } from '@shared/routing/buildRouter'
import { ToastProvider } from '@shared/ui/toast'
import { CustomerLayout } from './layouts/CustomerLayout'
import { PublicLayout } from './layouts/SiteChrome'
import { routes } from './routes'

const router = buildRouter(routes, { public: PublicLayout, customer: CustomerLayout }, { app: 'customer', loginPath: '/login' })

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider app="customer">
      <ToastProvider>
        <BusinessConfigGate>
          <LocationCatalogGate>
            <DocumentRequirementsGate>
              <RouterProvider router={router} />
            </DocumentRequirementsGate>
          </LocationCatalogGate>
        </BusinessConfigGate>
      </ToastProvider>
    </AuthProvider>
  </StrictMode>,
)
