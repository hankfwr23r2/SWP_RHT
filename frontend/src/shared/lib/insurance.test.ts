import { describe, it, expect } from 'vitest'
import { quoteLines } from './booking'
import { toBooking } from '../services/bookingMapper'
import type { Booking } from '../types/booking'
import type { BackendBookingResponse } from '../api/bookings'

describe('bảo hiểm bên thứ ba', () => {
  const base = { type: 'domestic', origin: { id: 'CLB-SG', country: 'VN', name: 'TP.HCM' }, dest: { id: 'HN', country: 'VN', name: 'Hà Nội' }, horses: [] } as unknown as Booking
  const horse = (insurance: Booking['horses'][number]['insurance']) => ({ horseId: 'H1', name: 'Storm', breed: 'Thoroughbred', stall: 'standard', insurance }) as Booking['horses'][number]
  it('không tự tính phí theo giống hoặc coi yêu cầu hỗ trợ là miễn phí', () => {
    const lines = quoteLines({ ...base, horses: [horse({ opted: true, option: 'assistance' })] }, [{ capacity: 2 }]).lines
    expect(lines.some(l => l.label.includes('bảo hiểm'))).toBe(false)
  })
  it('lấy đúng phí chuyên viên đã xác nhận, chỉ cộng cho yêu cầu hỗ trợ', () => {
    const lines = quoteLines({ ...base, horses: [horse({ opted: true, option: 'assistance', provider: 'Provider', premium: 1234567, reviewed: true }), horse({ opted: false, option: 'personal', premium: 999 }), horse({ opted: false, option: 'none' })] }, [{ capacity: 6 }]).lines
    expect(lines.filter(l => l.label.includes('bảo hiểm'))).toEqual([expect.objectContaining({ label: 'Thu hộ bảo hiểm bên thứ ba', amount: 1234567 })])
  })
  it('giữ nguyên thông tin bảo hiểm và chứng từ từ API', () => {
    const insurance = { option: 'assistance' as const, provider: 'Provider', premium: 1234567, reviewed: true, documents: ['https://example.com/quote.pdf'] }
    const b = toBooking({ id: 'BK-1', originName: 'TP.HCM', destName: 'Hà Nội', horses: [{ name: 'Storm', insurance }], createdAt: 1, departAt: 2 } as BackendBookingResponse)
    expect(b.horses[0].insurance).toMatchObject(insurance)
  })
})
