import { BOOKING_STATUS, type BookingStatus, type Tone } from '../config/booking-rules'

const ICON: Record<BookingStatus, string> = {
  pending_intake: 'fa-inbox',
  under_review: 'fa-magnifying-glass',
  pending_commercial: 'fa-file-invoice-dollar',
  awaiting_payment: 'fa-credit-card',
  quote_expired: 'fa-hourglass-end',
  waybill_issued: 'fa-file-contract',
  clearance_in_progress: 'fa-file-signature',
  clearance_done: 'fa-stamp',
  ready_for_pickup: 'fa-circle-check',
  en_route_to_pickup: 'fa-truck-moving',
  in_transit: 'fa-truck-fast',
  incident_reported: 'fa-triangle-exclamation',
  pending_emergency_approval: 'fa-hourglass-half',
  emergency_plan_active: 'fa-kit-medical',
  arrived: 'fa-location-dot',
  handover_timeout: 'fa-clock',
  returning_to_hq: 'fa-rotate-left',
  delivered_pending_settlement: 'fa-flag-checkered',
  expenses_submitted: 'fa-receipt',
  settlement_issued: 'fa-file-invoice-dollar',
  payment_overdue: 'fa-circle-exclamation',
  completed: 'fa-circle-check',
  cancelled: 'fa-ban',
  rejected: 'fa-circle-xmark',
}
const CLASS: Record<Tone, string> = { info: 'badge-info', warning: 'badge-warning', success: 'badge-success', danger: 'badge-danger', muted: 'badge-muted', orange: 'badge-orange' }

type Labels = { statusLabel?: string; customerStatusLabel?: string }

// Nhãn trạng thái đơn. Khách thấy nhãn dễ hiểu; nội bộ thấy nhãn nghiệp vụ, mã gốc theo PRD nằm ở tooltip.
// Có `labels` (đơn nhận từ backend) thì hiện nhãn backend trả; bảng ở frontend chỉ dùng khi thiếu.
export function BookingStatusBadge({ status, audience = 'customer', text, labels }: { status: BookingStatus; audience?: 'customer' | 'staff'; text?: string; labels?: Labels }) {
  // Trạng thái backend mới thêm mà frontend chưa biết: hiện nguyên mã thay vì làm trắng cả trang
  const s = BOOKING_STATUS[status] ?? { code: status, label: status, customerLabel: status, tone: 'muted' as Tone }
  return (
    <span className={`badge ${CLASS[s.tone]}`} title={text ? `${s.label} (${s.code})` : s.code}>
      <i className={`fa-solid ${ICON[status] ?? 'fa-circle-info'}`} aria-hidden="true" /> {text ?? (audience === 'customer' ? labels?.customerStatusLabel ?? s.customerLabel : labels?.statusLabel ?? s.label)}
    </span>
  )
}
