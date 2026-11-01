import { createRoot } from 'react-dom/client'
import { createBrowserRouter, Link, RouterProvider } from 'react-router'
import { InsurancePanel } from '../../src/apps/backoffice/shared/InsurancePanel'
import type { Booking } from '../../src/shared/types/booking'
import '../../src/shared/styles/base.css'
const params = new URLSearchParams(location.search)
const policy = params.has('policy')
const legacy = params.has('legacy')
const empty = params.has('empty')
const booking = {
  id: 'BK-1', status: policy ? 'clearance_in_progress' : 'under_review', departAt: Date.parse('2027-06-01T00:00:00Z'),
  horses: [{ horseId: 'H1', name: 'Storm', microchip: 'MC1', insurance: { option: 'assistance', ...(legacy ? { documentNames: null, policyDocuments: null, documents: empty ? null : ['https://example.test/quote.pdf'] } : {}), ...(policy ? { provider: 'Insurer', premium: 1234567, reviewed: true, documents: ['https://example.test/quote.pdf'] } : {}) } }],
} as Booking
createRoot(document.getElementById('root')!).render(<RouterProvider router={createBrowserRouter([
  { path: '/insurance-test', element: <><Link to="/elsewhere">Rời trang</Link><InsurancePanel b={booking} onDone={() => {}} /></> },
  { path: '/elsewhere', element: <p>Trang khác</p> },
])} />)
