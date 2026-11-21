import { createRoot } from 'react-dom/client'
import { createBrowserRouter, Link, RouterProvider } from 'react-router'
import { InsurancePanel } from '../../src/apps/backoffice/shared/InsurancePanel'
import { TripSummary } from '../../src/apps/backoffice/shared/BookingParts'
import type { Booking } from '../../src/shared/types/booking'
import '../../src/shared/styles/base.css'
const params = new URLSearchParams(location.search)
const policy = params.has('policy')
const personal = params.has('personal')
const waiting = params.has('waiting')
const booking = {
  id: 'BK-1', status: policy ? 'clearance_in_progress' : 'under_review', departAt: Date.parse('2027-06-01T00:00:00Z'),
  horses: [{ horseId: 'H1', name: 'Storm', microchip: 'MC1', insurance: { option: 'assistance', ...(waiting ? { revisionReason: 'Bên bảo hiểm từ chối, cần khách chọn lại', revisionRequestedAt: Date.parse('2027-05-01T02:00:00Z') } : {}), ...(policy ? { provider: 'Insurer', premium: 1234567, reviewed: true, documents: ['https://example.test/quote.pdf'] } : {}) } }],
} as Booking
const personalBooking = {
  id: 'BK-2', status: 'under_review', type: 'international', departAt: Date.parse('2027-06-01T00:00:00Z'),
  customer: 'khietnguyenngoc.ttma@gmail.com',
  origin: { id: 'A', country: 'VN', name: 'Trại Ngựa Thạch Thất, Phường Thủ Đức, Thành phố Hồ Chí Minh' },
  dest: { id: 'B', country: 'KH', name: 'Battambang' },
  consignor: { name: 'Nguyễn Ngọc Khiết', phone: '+84833942069' }, consignee: { name: 'cha bum kun', phone: '+85512345678' },
  horses: [
    { horseId: 'H1', name: 'Gió Bấc', microchip: 'MC1', insurance: { option: 'assistance', provider: 'Insurer', premium: 1000000, reviewed: true } },
    { horseId: 'H2', name: 'Sao Băng', microchip: 'MC2', insurance: { option: 'personal', provider: 'Bảo Việt', policyNumber: 'P-1', validFrom: '2027-01-01', validUntil: '2027-12-31', documents: ['https://example.test/hop-dong.pdf'], documentNames: { 'https://example.test/hop-dong.pdf': 'Hop-dong.pdf' } } },
    { horseId: 'H3', name: 'Bạch Vân', microchip: 'MC3', insurance: { option: 'none' } },
  ],
} as unknown as Booking
createRoot(document.getElementById('root')!).render(<RouterProvider router={createBrowserRouter([
  { path: '/insurance-test', element: personal
    ? <div style={{ maxWidth: 1200 }}><div className="card"><TripSummary b={personalBooking} /></div><InsurancePanel b={personalBooking} onDone={() => {}} /></div>
    : <><Link to="/elsewhere">Rời trang</Link><InsurancePanel b={booking} onDone={() => {}} /></> },
  { path: '/elsewhere', element: <p>Trang khác</p> },
])} />)
