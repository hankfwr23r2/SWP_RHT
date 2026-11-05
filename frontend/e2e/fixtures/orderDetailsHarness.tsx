import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { OrderDetailModal } from '../../src/apps/backoffice/features/manager/dashboard/OrderDetailModal'
import type { BackendBookingResponse } from '../../src/shared/api/bookings'
import { toBooking } from '../../src/shared/services/bookingMapper'
import { RouteError } from '../../src/shared/routing/RouteError'
import { InsuranceDocuments } from '../../src/shared/ui/InsuranceDocuments'
import '../../src/shared/styles/base.css'

const params = new URLSearchParams(location.search)
const booking = toBooking({
  id: 'BK-003', numericId: 3, type: 'domestic', customer: 'Customer', customerName: 'Customer',
  status: 'pending_intake', createdAt: 1000, departAt: Date.parse('2026-12-01'),
  originName: 'Hà Nội', originCountry: 'VN', destName: 'Đà Nẵng', destCountry: 'VN',
  consignor: { name: 'Sender', phone: '0900000000' }, consignee: { name: 'Recipient', phone: '0900000001' },
  totalHorses: 1, horses: [{ horseId: 'H-008', name: 'Tí Tèo', breed: 'Arabian',
    insurance: { option: params.get('option') ?? 'assistance', documents: null, documentNames: null, policyDocuments: null } }],
  history: [{ time: 1000, actor: 'Customer', text: 'Gửi yêu cầu đặt đơn' }],
} as unknown as BackendBookingResponse)

function DirectDocuments() {
  const [urls, setUrls] = useState<string[] | null>(null)
  return <InsuranceDocuments label="Chứng từ kiểm tra" urls={urls} names={null} onChange={setUrls} />
}

const router = createBrowserRouter([{
  path: '/order-details-test', errorElement: <RouteError />,
  element: params.has('direct') ? <DirectDocuments /> : <OrderDetailModal b={booking} onClose={() => {}} onDone={() => {}} />,
}])
createRoot(document.getElementById('root')!).render(<RouterProvider router={router} />)
