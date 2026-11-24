// Số liệu nhanh và việc cần làm ở trang chủ khách, đọc từ danh sách đơn
import type { BookingOrder } from '@shared/api/bookingOrders'
import { groupOf, nextStepOf, type OrderGroup } from '@shared/components/orderSteps'

export function portalSummary(orders: BookingOrder[]) {
  const inGroup = (group: OrderGroup) => orders.filter(order => groupOf(order) === group)
  const action = inGroup('action')
  return {
    action: action.length,
    processing: inGroup('processing').length,
    moving: inGroup('moving').length,
    // Đơn mới nhất lên đầu
    todo: action.sort((a, b) => (b.id ?? 0) - (a.id ?? 0)).map(order => ({ order, next: nextStepOf(order) })),
  }
}
