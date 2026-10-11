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
  delivered_pending_settlement: 'fa-flag-checkered',
  expenses_submitted: 'fa-receipt',
  settlement_issued: 'fa-file-invoice-dollar',
  payment_overdue: 'fa-circle-exclamation',
  completed: 'fa-circle-check',
  cancelled: 'fa-ban',
  rejected: 'fa-circle-xmark',
}
const CLASS: Record<Tone, string> = { info: 'badge-info', warning: 'badge-warning', success: 'badge-success', danger: 'badge-danger', muted: 'badge-muted', orange: 'badge-orange' }

// Nhãn trạng thái đơn. Khách thấy nhãn dễ hiểu; nội bộ thấy nhãn nghiệp vụ, mã gốc theo PRD nằm ở tooltip.
export function BookingStatusBadge({ status, audience = 'customer', text }: { status: BookingStatus; audience?: 'customer' | 'staff'; text?: string }) {
  const s = BOOKING_STATUS[status]
  return (
    <span className={`badge ${CLASS[s.tone]}`} title={text ? `${s.label} (${s.code})` : s.code}>
      <i className={`fa-solid ${ICON[status]}`} aria-hidden="true" /> {text ?? (audience === 'customer' ? s.customerLabel : s.label)}
    </span>
  )
}
