// Harness chỉ dùng cho E2E: dựng màn hình việc của Tài xế thật (DriverJob) với một đơn quản lý đã hủy
// khi 2 con ngựa đã lên xe (trạng thái "Chờ trả ngựa"). Không thêm route test nào vào app thật.
import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { DriverJob } from '../../src/apps/backoffice/features/driver/DriverJob'
import type { BackendBookingResponse } from '../../src/shared/api/bookings'
import { AuthProvider } from '../../src/shared/auth/AuthContext'
import { SESSION_KEY } from '../../src/shared/auth/session'
import { toBooking } from '../../src/shared/services/bookingMapper'
import type { Booking } from '../../src/shared/types/booking'
import '../../src/shared/styles/base.css'

sessionStorage.setItem(`${SESSION_KEY}_backoffice`, JSON.stringify({ role: 'driver', name: 'Tài xế Test', username: 'taixe01', staffCode: 'DR-01' }))

const ORIGINALS = [
  'Hộ chiếu ngựa bản gốc (FEI / National Passport)',
  'Sổ tiêm phòng',
  'Phiếu xét nghiệm EIA/EVA, bản gốc kèm 02 bản sao công chứng',
]

const now = Date.now()
const initial = toBooking({
  id: 'BK-RET1', type: 'domestic', customer: 'Khach Test', customerName: 'Khach Test',
  status: 'returning_horses', createdAt: now - 86_400_000, departAt: now,
  originName: 'Kho Đồng Nai', originCountry: 'VN', destName: 'Hà Nội', destCountry: 'VN',
  consignor: { name: 'Nguyễn Văn Giao', phone: '0901234567' }, consignee: { name: 'Người Nhận', phone: '0900000001' },
  totalHorses: 2,
  horses: [
    { horseId: 'H-1', name: 'Gió Mekong', microchip: 'VN-800221', status: 'ELIGIBLE' },
    { horseId: 'H-2', name: 'Sao Mai', microchip: 'VN-800222', status: 'ELIGIBLE' },
  ],
  trips: [{
    tripId: 'TRP-1', vehicleId: 'VH-001', driverId: 'DR-01', escortId: 'ES-01', horseIds: ['H-1', 'H-2'], departedAt: now - 3_600_000,
    run: { checkpoints: [{
      id: 'pickup', type: 'pickup', label: 'Điểm đón', place: 'Kho Đồng Nai', plannedAt: now - 3_600_000, arrivedAt: now - 1_800_000,
      photo: 'https://example.com/diem-don.jpg', chips: ['VN-800221', 'VN-800222'], originals: ORIGINALS,
      handoverPhoto: 'https://example.com/bien-ban-nhan.jpg',
    }], welfare: [] },
  }],
  payment: { paidAt: now - 86_400_000, amount: 3_000_000, cancelled: true, cancelReason: 'Khách không thanh toán 70%', refundAmount: 0, penaltyAmount: 3_000_000 },
  history: [],
} as unknown as BackendBookingResponse)

function Harness() {
  const [b, setB] = useState<Booking>(initial)
  const [message, setMessage] = useState<{ text: string; error: boolean }>()
  // Giống DriverPage: chạy thao tác, báo kết quả, lấy đơn mới backend trả về
  const run = async (fn: () => Promise<unknown>, msg: string) => {
    try {
      const result = await fn()
      if (result && typeof result === 'object' && 'status' in result) setB(result as Booking)
      setMessage({ text: msg, error: false })
    } catch (e) {
      setMessage({ text: e instanceof Error ? e.message : 'Không thực hiện được', error: true })
    }
  }
  return (
    <AuthProvider app="backoffice">
      <main style={{ maxWidth: 480, margin: '0 auto', padding: 16 }}>
        <p data-testid="booking-status">{b.status}</p>
        {message && <p role={message.error ? 'alert' : 'status'}>{message.text}</p>}
        <DriverJob b={b} t={b.trips![0]} run={run} />
      </main>
    </AuthProvider>
  )
}
createRoot(document.getElementById('root')!).render(<Harness />)
