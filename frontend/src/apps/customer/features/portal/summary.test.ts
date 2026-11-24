import { describe, expect, it } from 'vitest'
import type { BookingOrder } from '@shared/api/bookingOrders'
import { portalSummary } from './summary'

const orders: BookingOrder[] = [
  { id: 1, status: 'AWAITING_DEPOSIT' }, { id: 2, status: 'QUOTING' }, { id: 3, status: 'IN_OPERATION' },
  { id: 4, status: 'NEEDS_RESUBMISSION' }, { id: 5, status: 'COMPLETED' }, { id: 6, status: 'CONFIRMED' }, { id: 7, status: 'CANCELLED' },
]

describe('trang chủ khách', () => {
  it('đếm đơn theo nhóm, đơn đã kết thúc không tính', () => {
    expect(portalSummary(orders)).toMatchObject({ action: 2, processing: 2, moving: 1 })
  })
  it('việc cần làm: đơn mới nhất lên đầu, kèm bước tiếp theo', () => {
    expect(portalSummary(orders).todo.map(({ order, next }) => [order.id, next.key])).toEqual([[4, 'resubmit'], [1, 'quote']])
  })
  it('chưa có đơn thì mọi số bằng 0', () => {
    expect(portalSummary([])).toEqual({ action: 0, processing: 0, moving: 0, todo: [] })
  })
})
