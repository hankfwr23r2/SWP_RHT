import { describe, expect, it } from 'vitest'
import type { CustomerBookingView } from '@shared/services/bookings'
import { nextStep } from './nextStep'

const deadline = Date.parse('2026-11-07T03:00:00Z')
const booking: CustomerBookingView = {
  id: 'BK-007', customer: 'khach01', createdAt: deadline - 3600_000, type: 'domestic',
  origin: { id: 'a', name: 'Điểm đón', country: 'VN' }, dest: { id: 'b', name: 'Điểm giao', country: 'VN' },
  departAt: deadline + 86400_000,
  consignor: { name: 'Gửi', phone: '', idNumber: '', address: '' },
  consignee: { name: 'Nhận', phone: '', idNumber: '', address: '' }, horses: [], status: 'awaiting_payment',
  quote: { lines: [], adjustments: [], subtotal: 10_000_000, total: 10_000_000, deposit: 3_000_000,
    balance: 7_000_000, sentAt: deadline - 3600_000, expiresAt: deadline, sentBy: 'manager' },
  depositConfirmationUntil: deadline + 300_000,
}

describe('đặt cọc sát hạn', () => {
  it('offers a schedule choice rather than a deposit for an overdue request', () => {
    expect(nextStep({ ...booking, status: 'schedule_change_required' }, deadline))
      .toMatchObject({ actionNeeded: true, title: 'Cần xác nhận lại lịch', cta: 'Chọn phương án' })
  })
  it('còn hạn vẫn yêu cầu đặt cọc', () => {
    expect(nextStep(booking, deadline - 1000)).toMatchObject({ actionNeeded: true, cta: 'Đặt cọc 30%' })
  })

  it('trong khoảng chờ chỉ báo xác nhận, không mời khách trả tiền lại', () => {
    const step = nextStep(booking, deadline)
    expect(step.actionNeeded).toBe(false)
    expect(step.cta).toBeUndefined()
    expect(step.title).toBe('Đang xác nhận thanh toán đặt cọc')
    expect(step.text).toContain('không cần thanh toán lại')
  })

  it('hết khoảng chờ không còn hứa tiếp tục giữ chỗ', () => {
    const step = nextStep(booking, deadline + 300_000)
    expect(step.actionNeeded).toBe(false)
    expect(step.title).toBe('Đã hết hạn đặt cọc')
    expect(step.text).toContain('đối soát')
  })

  it('không có giao dịch chờ thì không hiển thị giữ thêm chỗ', () => {
    expect(nextStep({ ...booking, depositConfirmationUntil: undefined }, deadline).title).toBe('Đã hết hạn đặt cọc')
  })
})
