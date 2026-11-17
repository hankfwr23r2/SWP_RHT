import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { AuthProvider } from '../../src/shared/auth/AuthContext'
import { ToastProvider } from '../../src/shared/ui/toast'
import { RatingCard } from '../../src/apps/customer/features/orders/TripFeedback'
import ReportsPage from '../../src/apps/backoffice/features/manager/reports/ReportsPage'
import { AuditModal } from '../../src/apps/backoffice/features/manager/dashboard/AuditModal'
import type { Booking } from '../../src/shared/types/booking'
import '../../src/shared/i18n'
import '../../src/shared/styles/base.css'

const now = Date.now()
const expenseAmount = new URLSearchParams(location.search).has('negative') ? 12_000_000 : 800_000
const booking = {
  id: 'BK-012', customer: 'Customer', status: 'completed', type: 'domestic', createdAt: now, departAt: now,
  origin: { name: 'Hà Nội' }, dest: { name: 'Đà Nẵng' }, horses: [], trips: [], history: [],
  payment: { amount: 3_150_000, paidAt: now }, balance: { amount: 7_350_000, paidAt: now },
  quote: { total: 10_500_000, lines: [{ label: 'Cước vận chuyển', amount: 10_500_000, marginApplied: true }] },
  incidents: [{ id: 'I-1', kind: 'handover_timeout', tripId: 'T', reportedAt: now, reportedBy: 'Driver', status: 'resolved',
    expenses: [{ id: 'stable', category: 'holding_stable', label: 'Chuồng tạm', photo: '/receipt.png', amount: expenseAmount, payer: 'carrier', at: now, by: 'Driver' }] }],
  settlement: { items: [{ label: 'Chuồng tạm', amount: 800_000 }], total: 800_000 },
} as unknown as Booking
const view = new URLSearchParams(location.search).get('view')
const router = createBrowserRouter([{
  path: '/company-cost-test',
  loader: () => ({ all: [booking], errors: [] }),
  element: view === 'reports' ? <ReportsPage /> : view === 'audit'
    ? <AuditModal b={booking} onClose={() => {}} onDone={() => {}} />
    : <RatingCard b={booking} owner="Customer" onDone={() => {}} />,
}])
createRoot(document.getElementById('root')!).render(
  <AuthProvider app="backoffice"><ToastProvider><RouterProvider router={router} /></ToastProvider></AuthProvider>,
)
