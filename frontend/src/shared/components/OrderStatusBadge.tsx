import { useTranslation } from 'react-i18next'
import type { BookingStatus } from '@shared/api/bookingOrders'
import { StatusBadge, type StatusTone } from '@shared/ui/StatusBadge'

// Nơi duy nhất gán sắc thái cho 12 mốc thương mại và mốc đổi lịch của đơn
const TONE: Record<BookingStatus, StatusTone> = {
  PENDING_INTAKE: 'info',
  UNDER_REVIEW: 'info',
  NEEDS_RESUBMISSION: 'danger',
  QUOTING: 'info',
  AWAITING_DEPOSIT: 'warning',
  QUOTE_EXPIRED: 'neutral',
  CONFIRMED: 'success',
  SCHEDULE_CHANGE_REQUIRED: 'warning',
  IN_OPERATION: 'info',
  DELIVERED: 'success',
  COMPLETED: 'success',
  CANCELLED: 'neutral',
  REJECTED: 'danger',
}

export function OrderStatusBadge({ status }: { status?: BookingStatus }) {
  const { t } = useTranslation()
  if (!status) return null
  return <StatusBadge tone={TONE[status] ?? 'neutral'} label={t(`orderStatus.${status}`)} />
}
