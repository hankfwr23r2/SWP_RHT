import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'
import LegalReviewPage from '../../src/apps/backoffice/features/specialist/legal/LegalReviewPage'
import { AuthProvider } from '../../src/shared/auth/AuthContext'
import { ToastProvider } from '../../src/shared/ui/toast'
import { toBooking } from '../../src/shared/services/bookingMapper'
import '../../src/shared/i18n'
import '../../src/shared/styles/base.css'

sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'specialist', name: 'specialist01', username: 'specialist01@example.test' }))
const router = createBrowserRouter([
  { path: '/legal-review-test', loader: async () => {
    const response = await fetch('/api/v1/specialist/bookings/BK-701')
    return { b: toBooking((await response.json()).data), errors: [] }
  }, element: <AuthProvider app="backoffice"><ToastProvider><LegalReviewPage /></ToastProvider></AuthProvider> },
  { path: '/specialist/legal', element: <p>Legal queue</p> },
])
createRoot(document.getElementById('root')!).render(<RouterProvider router={router} />)
