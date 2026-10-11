import type { Booking } from '@shared/types/booking'

// Việc Manager phải làm cho đơn: trang xử lý và tên nút
export function managerAction(b: Booking): { to: string; label: string } | undefined {
  if (b.status === 'pending_intake') return { to: '/manager/intake', label: 'Tiếp nhận đơn' }
  if (b.status === 'pending_commercial') return { to: '/manager/approvals', label: 'Duyệt báo giá' }
  if ((b.incidents ?? []).some(i => i.status === 'pending_approval')) return { to: '/manager/incidents', label: 'Duyệt phương án sự cố' }
  if (b.status === 'expenses_submitted') return { to: '/manager/progress', label: 'Đối soát chi phí' }
}
