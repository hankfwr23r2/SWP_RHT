import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { ScheduleChangeCard } from '../../src/apps/customer/features/orders/ScheduleChangeCard'
import { backendBookingApi } from '../../src/shared/api/bookings'
import { toBooking } from '../../src/shared/services/bookingMapper'
import { ToastProvider } from '../../src/shared/ui/toast'
import '../../src/shared/i18n'
import '../../src/shared/styles/base.css'

const initial = toBooking({
  id: 'BK-3', numericId: 3, type: 'domestic', customer: 'Customer', customerName: 'Customer',
  status: 'schedule_change_required', createdAt: 1000, departAt: Date.parse('2026-11-30T00:00:00+07:00'),
  originName: 'Hà Nội', destName: 'Đà Nẵng', totalHorses: 0, horses: [], consignor: { name: 'Sender', phone: '' },
  consignee: { name: 'Recipient', phone: '' }, scheduleChange: { flaggedAt: 1000, previousStatus: 'pending_intake' },
})

function Harness() {
  const [booking, setBooking] = useState(initial)
  return <ToastProvider><main style={{ maxWidth: 650, margin: '30px auto', padding: 16 }}>
    {booking.status === 'schedule_change_required'
      ? <ScheduleChangeCard b={booking} onDone={() => { void backendBookingApi.getCustomerBookingById('BK-3').then(data => setBooking(toBooking(data))) }} />
      : <p role="status">{booking.status === 'cancelled' ? 'Đã hủy yêu cầu' : 'Chờ công ty xác nhận lịch mới'}</p>}
  </main></ToastProvider>
}

createRoot(document.getElementById('root')!).render(<Harness />)
